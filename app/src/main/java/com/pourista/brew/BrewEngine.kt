package com.pourista.brew

import android.os.SystemClock
import kotlin.math.ceil
import com.pourista.data.model.Recipe
import com.pourista.data.model.RecipeStep
import com.pourista.data.model.StepKind
import com.pourista.data.model.scaledToDose
import com.pourista.scale.ScaleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** За сколько граммов до цели предупреждать по умолчанию. */
const val DEFAULT_NEAR_TARGET_GRAMS = 5f

/**
 * Насколько скорость может разойтись с рецептом, прежде чем приложение скажет
 * лить быстрее или медленнее. Доля от целевой скорости.
 */
const val DEFAULT_PACE_TOLERANCE = 0.1f

enum class BrewPhase { IDLE, RUNNING, PAUSED, FINISHED }

/** Насколько пролив попадает в график рецепта. */
enum class Pace { ON_TRACK, TOO_FAST, TOO_SLOW }

/** Что делают прямо сейчас внутри шага. */
enum class StepPhase { POURING, WAITING }

/** Как лить следующий пролив по сравнению с только что законченным. */
enum class NextPourHint { SAME, FASTER, SLOWER }

/**
 * Сравнивает фактическую скорость только что законченного влива с той, что
 * просит следующий. Разницу меньше [tolerance] не упоминаем: подсказка «чуть
 * быстрее» на пять процентов только отвлекает.
 */
internal fun compareNextPour(
    lastFlowRate: Float,
    nextFlowRate: Float?,
    tolerance: Float = DEFAULT_PACE_TOLERANCE,
): NextPourHint? {
    if (lastFlowRate <= 0f || nextFlowRate == null || nextFlowRate <= 0f) return null
    val ratio = nextFlowRate / lastFlowRate
    return when {
        ratio > 1f + tolerance -> NextPourHint.FASTER
        ratio < 1f - tolerance -> NextPourHint.SLOWER
        else -> NextPourHint.SAME
    }
}

data class Guidance(
    val stepIndex: Int,
    val stepCount: Int,
    val step: RecipeStep,
    val nextStep: RecipeStep?,
    /** Сколько воды потребует следующий шаг сверх уже налитого. */
    val nextStepDeltaGrams: Float?,
    /** Сколько воды доливают на текущем шаге. */
    val stepDeltaGrams: Float,
    /** Как лить следующий пролив относительно только что законченного. */
    val nextPourHint: NextPourHint?,
    /** Целевая скорость следующего пролива, г/с. */
    val nextPourFlowRate: Float?,
    /** С какой скоростью человек фактически лил прошлый влив, г/с. */
    val lastPourFlowRate: Float?,
    /** Доля пройденного времени шага, 0..1. */
    val stepProgress: Float,
    val secondsLeftInStep: Int,
    /** Сколько воды должно быть на весах прямо сейчас. */
    val targetNowGrams: Float,
    /** Цель к концу текущего шага. */
    val targetEndGrams: Float,
    /** Сколько ещё долить до конца шага. */
    val remainingGrams: Float,
    val pace: Pace,
    /** Влив идёт или шаг уже выстаивается. */
    val stepPhase: StepPhase,
    /** Скорость влива, заданная рецептом, г/с. */
    val targetFlowRate: Float,
    /** Где на кольце шага стоит отметка окончания влива, 0..1. */
    val pourEndFraction: Float,
    val totalProgress: Float,
    val secondsToNextPour: Int?,
)

sealed interface BrewEvent {
    data class StepChanged(val index: Int, val step: RecipeStep) : BrewEvent
    data class Countdown(val secondsLeft: Int) : BrewEvent

    /** До цели шага осталось совсем немного — пора готовиться закрывать чайник. */
    data class NearTarget(val remainingGrams: Float) : BrewEvent

    /**
     * План рецепта отыгран: время последнего шага, обычно слива, вышло.
     * Заваривание при этом ещё идёт — воронку снимают руками.
     */
    data object PlanFinished : BrewEvent
    data object Finished : BrewEvent
}

data class BrewState(
    val phase: BrewPhase = BrewPhase.IDLE,
    val elapsedMs: Long = 0L,
    val doseGrams: Float = 0f,
    val weightGrams: Float = 0f,
    val flowRate: Float = 0f,
    val flowRateAvg: Float = 0f,
    val weightSeries: List<Float> = emptyList(),
    val flowSeries: List<Float> = emptyList(),
    val recipe: Recipe? = null,
    /** Цели рецепта пересчитаны под фактическую дозу. */
    val recipeScaled: Boolean = false,
    val guidance: Guidance? = null,
    /**
     * Автостарт взведён прямо сейчас: следующая же вода на весах запустит таймер.
     * Состояние на одно заваривание — по умолчанию выключено, взводится галкой
     * у кнопки «Старт» или само после записи дозы, если так велит рецепт.
     */
    val autoStartArmed: Boolean = false,
    /** Идёт запись рецепта с реального пролива. */
    val recording: Boolean = false,
    /** Сколько проливов уже распознано во время записи. */
    val recordedPours: Int = 0,
) {
    val elapsedSec: Int get() = (elapsedMs / 1000).toInt()
    val isRunning: Boolean get() = phase == BrewPhase.RUNNING
    val hasData: Boolean get() = weightSeries.isNotEmpty() || elapsedMs > 0
    val ratio: Float get() = if (doseGrams > 0f) weightGrams / doseGrams else 0f
}

/**
 * Ход заваривания: таймер, скорость пролива и подсказки по рецепту.
 *
 * Живёт на уровне приложения, поэтому переход на другой экран и поворот
 * не сбрасывают начатое заваривание.
 */
class BrewEngine(
    private val scale: ScaleRepository,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(BrewState())
    val state: StateFlow<BrewState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<BrewEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<BrewEvent> = _events.asSharedFlow()

    private var tickerJob: Job? = null

    /** Отсчёт ведём от монотонных часов, чтобы таймер не плыл. */
    private var startedAtElapsedRealtime = 0L
    private var accumulatedMs = 0L

    /** Показания веса с частотой тика, для расчёта скорости пролива. */
    private val weightSamples = ArrayDeque<Float>()
    private val flowSamples = ArrayDeque<Float>()
    private val flowForAverage = mutableListOf<Float>()

    private var lastChartSecond = -1
    private var lastStepIndex = -1
    private var lastCountdownSecond = -1

    /** Шаг, влив которого уже завершён — по достижении цели или по остановке веса. */
    private var pourDoneStepIndex = -1
    private var nearTargetStepIndex = -1

    /** Сигнал о конце плана даём один раз за заваривание. */
    private var planFinishedEmitted = false
    private var steadyWeight = 0f
    private var steadySinceMs = 0L

    /** Рецепт как он сохранён; в состоянии может лежать пересчитанная под дозу копия. */
    private var baseRecipe: Recipe? = null

    /** За сколько граммов до цели предупреждать. Задаётся в настройках. */
    @Volatile
    var nearTargetGrams: Float = DEFAULT_NEAR_TARGET_GRAMS

    /** Допустимое расхождение скорости с рецептом. Задаётся в настройках. */
    @Volatile
    var paceTolerance: Float = DEFAULT_PACE_TOLERANCE

    /**
     * Оставлять объём воды как в рецепте, не подгоняя под фактическую дозу.
     * Настройка глобальная: её ставят осознанно, когда кофе сыплют больше
     * специально, ради более плотной чашки.
     */
    private var keepRecipeWater: Boolean = false

    fun setKeepRecipeWater(enabled: Boolean) {
        if (keepRecipeWater == enabled) return
        keepRecipeWater = enabled
        _state.update { current -> current.withRecipeForDose(baseRecipe, current.doseGrams) }
    }

    /**
     * На сколько секунд рецепт «съехал» вперёд относительно секундомера. Растёт,
     * когда влив закончен раньше времени и ждать до конца шага незачем: слив
     * начинается сразу. Само заваривание при этом идёт по реальным часам.
     */
    private var timelineShiftSec = 0f

    /** Секунды с начала заваривания на текущем тике — без сдвига рецепта. */
    private var currentElapsedSec = 0f

    /** Фактическая скорость последнего законченного влива, г/с. */
    private var lastPourFlowRate = 0f
    private var pourTrackedStepIndex = -1
    private var pourStartedAtMs = 0L
    private var pourStartWeight = 0f

    private val recorder = PourRecorder()

    /** Сторож конца заваривания: взводится, когда закончен последний влив. */
    private val removal = RemovalWatch()

    /** Вес для расчётов: без просадок от покачивания воронки. */
    private val pouredWeight = MonotonicWeight()

    fun selectRecipe(recipe: Recipe?) {
        baseRecipe = recipe
        _state.update { current -> current.withRecipeForDose(recipe, current.doseGrams) }
    }

    /**
     * Текущий вес становится дозой кофе, весы обнуляются. Если рецепт просит
     * автостарт — здесь же взводим его: доза записана, следующая вода на весах
     * это уже пролив.
     */
    fun captureDose() {
        val dose = _state.value.weightGrams
        if (dose <= 0f) return
        val arm = baseRecipe?.autoStart == true
        _state.update {
            it.copy(doseGrams = dose, autoStartArmed = it.autoStartArmed || arm)
                .withRecipeForDose(baseRecipe, dose)
        }
        scale.tare()
    }

    fun setDose(grams: Float) {
        val dose = grams.coerceAtLeast(0f)
        _state.update { it.copy(doseGrams = dose).withRecipeForDose(baseRecipe, dose) }
    }

    /**
     * Подставляет рецепт, пересчитанный под фактическую дозу. Считаем всегда от
     * сохранённого рецепта, иначе повторная дозировка масштабировала бы уже
     * масштабированное.
     */
    private fun BrewState.withRecipeForDose(base: Recipe?, dose: Float): BrewState {
        if (base == null) return copy(recipe = null, recipeScaled = false, guidance = null)
        val adjusted = if (dose > 0f && !keepRecipeWater) base.scaledToDose(dose) else base
        val scaled = adjusted.waterGrams != base.waterGrams || adjusted.doseGrams != base.doseGrams
        val next = copy(recipe = adjusted, recipeScaled = scaled)
        return next.copy(guidance = guidanceFor(adjusted, next))
    }

    fun tare() = scale.tare()

    /** Включить запись: заваривание начинается с чистого листа. */
    fun startRecording() {
        reset()
        recorder.reset()
        _state.update { it.copy(recording = true, recordedPours = 0) }
    }

    fun cancelRecording() {
        recorder.reset()
        _state.update { it.copy(recording = false, recordedPours = 0) }
    }

    /** Собирает рецепт из записанного пролива. */
    fun buildRecordedRecipe(name: String, brewer: String): Recipe? {
        val state = _state.value
        return recorder.buildRecipe(
            name = name,
            brewer = brewer,
            doseGrams = state.doseGrams,
            totalElapsedMs = state.elapsedMs,
            waterTempC = DEFAULT_WATER_TEMP_C,
        )
    }

    fun setAutoStartArmed(armed: Boolean) {
        _state.update { it.copy(autoStartArmed = armed) }
    }

    fun start() {
        if (_state.value.phase == BrewPhase.RUNNING) return
        if (_state.value.phase == BrewPhase.FINISHED) return
        startedAtElapsedRealtime = SystemClock.elapsedRealtime()
        _state.update { it.copy(phase = BrewPhase.RUNNING, autoStartArmed = false) }
        startTicker()
    }

    fun pause() {
        if (_state.value.phase != BrewPhase.RUNNING) return
        accumulatedMs += SystemClock.elapsedRealtime() - startedAtElapsedRealtime
        tickerJob?.cancel()
        tickerJob = null
        _state.update { it.copy(phase = BrewPhase.PAUSED, elapsedMs = accumulatedMs) }
    }

    fun toggleRunning() {
        when (_state.value.phase) {
            BrewPhase.RUNNING -> pause()
            BrewPhase.IDLE, BrewPhase.PAUSED -> start()
            BrewPhase.FINISHED -> Unit
        }
    }

    /**
     * Финиш по кнопке. Если вес к этому моменту уже просел, закрываем
     * заваривание так же, как автофиниш: воронку сняли раньше, чем нажали,
     * и в историю должен попасть вес до падения.
     */
    fun finish() {
        if (removal.dropPending) finishAfterRemoval() else finishAt(SystemClock.elapsedRealtime())
    }

    /**
     * Заканчивает заваривание временем [atMs] по монотонным часам. Отдельный
     * момент нужен автофинишу: чашку сняли раньше, чем сторож в этом убедился,
     * и лишние секунды в историю попадать не должны.
     */
    private fun finishAt(atMs: Long, transform: (BrewState) -> BrewState = { it }) {
        if (_state.value.phase == BrewPhase.FINISHED) return
        if (_state.value.phase == BrewPhase.RUNNING) {
            accumulatedMs += (atMs - startedAtElapsedRealtime).coerceAtLeast(0L)
        }
        tickerJob?.cancel()
        tickerJob = null
        removal.reset()
        // Правку веса вносим тем же обновлением, что и финиш: иначе показание
        // с весов успело бы лечь между ними.
        _state.update { transform(it).copy(phase = BrewPhase.FINISHED, elapsedMs = accumulatedMs) }
        _events.tryEmit(BrewEvent.Finished)
    }

    fun reset() {
        tickerJob?.cancel()
        tickerJob = null
        accumulatedMs = 0L
        startedAtElapsedRealtime = 0L
        weightSamples.clear()
        flowSamples.clear()
        flowForAverage.clear()
        lastChartSecond = -1
        lastStepIndex = -1
        lastCountdownSecond = -1
        pourDoneStepIndex = -1
        nearTargetStepIndex = -1
        planFinishedEmitted = false
        steadySinceMs = 0L
        timelineShiftSec = 0f
        lastPourFlowRate = 0f
        pourTrackedStepIndex = -1
        pourStartedAtMs = 0L
        removal.reset()
        pouredWeight.reset()
        // Возвращаем исходный рецепт: пересчёт был привязан к дозе прошлой чашки.
        _state.value = BrewState(recipe = baseRecipe)
        scale.tare()
    }

    /** Вес приходит с весов и вне запущенного таймера — для автостарта и дозы. */
    fun onWeightChanged(grams: Float) {
        // После финиша показания замораживаем: на экране и в истории должно
        // остаться то, что налили, а не ноль с весов, с которых сняли чашку.
        if (_state.value.phase == BrewPhase.FINISHED) return
        _state.update { it.copy(weightGrams = grams) }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
    }

    private fun tick() {
        val current = _state.value
        if (current.phase != BrewPhase.RUNNING) return

        val nowMs = SystemClock.elapsedRealtime()
        val elapsed = accumulatedMs + (nowMs - startedAtElapsedRealtime)
        currentElapsedSec = elapsed / 1000f

        // Для скорости, графиков и конца влива берём неубывающий вес: покачивание
        // воронки роняет показания, а их возврат выглядел бы бешеным вливом.
        // В режиме аэропресса — наоборот: отжим роняет вес по делу, и сглаживать
        // его нельзя, иначе на графике не видно, когда начали давить.
        val raw = current.weightGrams.coerceAtLeast(0f)
        val aeropress = current.recipe?.aeropressMode == true
        val weight = if (aeropress) raw else pouredWeight.onSample(raw, nowMs)

        weightSamples.addLast(weight)
        while (weightSamples.size > SAMPLE_WINDOW) weightSamples.removeFirst()

        // Скорость пролива: прирост веса за последнюю секунду, сглаженный по десяти отсчётам.
        val secondAgo = if (weightSamples.size > TICKS_PER_SECOND) {
            weightSamples.elementAt(weightSamples.size - 1 - TICKS_PER_SECOND)
        } else {
            weightSamples.first()
        }
        // Скачок веса на десятки граммов за секунду из чайника не наливают:
        // так выглядит только возврат показаний после долгой просадки. В
        // скорость и в график такое попадать не должно.
        val jump = (weight - secondAgo).coerceAtLeast(0f)
        val instantFlow = if (jump > MAX_PLAUSIBLE_FLOW) 0f else jump
        flowSamples.addLast(instantFlow)
        while (flowSamples.size > TICKS_PER_SECOND) flowSamples.removeFirst()
        val flowRate = flowSamples.average().toFloat()
        if (instantFlow >= MIN_FLOW_FOR_AVERAGE) flowForAverage += instantFlow
        val flowAvg = if (flowForAverage.isEmpty()) 0f else flowForAverage.average().toFloat()

        val second = (elapsed / 1000).toInt()
        val appendChartPoint = second != lastChartSecond
        if (appendChartPoint) lastChartSecond = second

        val next = current.copy(
            elapsedMs = elapsed,
            flowRate = flowRate,
            flowRateAvg = flowAvg,
            weightSeries = if (appendChartPoint) current.weightSeries + weight else current.weightSeries,
            flowSeries = if (appendChartPoint) current.flowSeries + flowRate else current.flowSeries,
        )
        if (next.recording) {
            recorder.onSample(elapsed, weight)
            if (recorder.pourCount != next.recordedPours) {
                _state.value = next.copy(recordedPours = recorder.pourCount)
            }
        }

        val firstPass = next.recipe?.let { guidanceFor(it, next) }
        if (firstPass != null) detectPourFinished(firstPass, weight, nowMs)

        // После определения конца влива подсказку пересобираем: статус шага мог
        // смениться прямо сейчас, и показывать устаревший «влив» нельзя.
        val guidance = next.recipe?.let { guidanceFor(it, next) }
        _state.value = next.copy(guidance = guidance)

        if (guidance != null) emitCues(guidance)

        // Вес берём как есть, без обрезки по нулю: снятая целиком чашка уводит
        // весы в минус, и это самый явный признак, что заваривание закончено.
        if (removal.onSample(current.weightGrams, nowMs)) finishAfterRemoval()
    }

    /**
     * Автофиниш: с весов сняли воронку или чашку. Заваривание закрываем тем
     * временем, когда вес упал, а вес возвращаем последний нормальный — вместе
     * с графиками, куда уже успели попасть секунды падения.
     */
    private fun finishAfterRemoval() {
        val restored = removal.weightBeforeDrop
        val cutoff = removal.cutoffGrams
        finishAt(removal.droppedAtMs) { state ->
            val weights = state.weightSeries.dropLastWhile { it <= cutoff }
            state.copy(
                weightGrams = restored,
                weightSeries = weights,
                flowSeries = state.flowSeries.take(weights.size),
            )
        }
    }

    private fun emitCues(guidance: Guidance) {
        if (guidance.stepIndex != lastStepIndex) {
            lastStepIndex = guidance.stepIndex
            lastCountdownSecond = -1
            _events.tryEmit(BrewEvent.StepChanged(guidance.stepIndex, guidance.step))
        }

        // Отдельный сигнал незадолго до цели: закрывать чайник надо заранее,
        // вода из носика доливается ещё пару секунд.
        if (guidance.stepPhase == StepPhase.POURING &&
            guidance.stepIndex != nearTargetStepIndex &&
            guidance.remainingGrams in 0.1f..nearTargetGrams
        ) {
            nearTargetStepIndex = guidance.stepIndex
            _events.tryEmit(BrewEvent.NearTarget(guidance.remainingGrams))
        }
        // Последний шаг отыгран: рецепт кончился, пора снимать воронку.
        // Финиш при этом не наступает — его даёт снятая чашка или кнопка.
        if (!planFinishedEmitted &&
            guidance.stepIndex == guidance.stepCount - 1 &&
            guidance.secondsLeftInStep <= 0
        ) {
            planFinishedEmitted = true
            _events.tryEmit(BrewEvent.PlanFinished)
        }

        val toNextPour = guidance.secondsToNextPour
        if (toNextPour != null && toNextPour in 1..COUNTDOWN_FROM &&
            toNextPour != lastCountdownSecond
        ) {
            lastCountdownSecond = toNextPour
            _events.tryEmit(BrewEvent.Countdown(toNextPour))
        }
    }

    /** Весы на связи: только тогда вес что-то значит. */
    private val measuring: Boolean get() = scale.state.value.isConnected

    private fun guidanceFor(recipe: Recipe, state: BrewState): Guidance? {
        val steps = recipe.steps
        if (steps.isEmpty()) return null

        val elapsedSec = state.elapsedMs / 1000f + timelineShiftSec
        val index = steps.indexOfLast { elapsedSec >= it.startSec }.coerceAtLeast(0)
        val step = steps[index]
        val next = steps.getOrNull(index + 1)

        val stepElapsed = (elapsedSec - step.startSec).coerceAtLeast(0f)
        val stepProgress = if (step.durationSec > 0) {
            (stepElapsed / step.durationSec).coerceIn(0f, 1f)
        } else {
            1f
        }
        // Округляем вверх: пока идёт последняя секунда шага, на кольце должна
        // гореть единица. Ноль означал бы лишнюю секунду ожидания, которой нет.
        val secondsLeft = ceil(step.endSec - elapsedSec).toInt().coerceAtLeast(0)

        val previousTarget = steps.take(index).maxOfOrNull { it.targetWaterGrams } ?: 0f
        val delta = step.targetWaterGrams - previousTarget

        // Рецепт пишут как «50 г, 45 секунд»: это не значит лить сорок пять секунд.
        // Время влива берётся из заданной скорости, остальное шаг выстаивается.
        val pourSeconds = step.pourSeconds(delta)
        val targetFlowRate = if (pourSeconds > 0f) delta / pourSeconds else 0f
        val pourProgress = if (pourSeconds > 0f) {
            (stepElapsed / pourSeconds).coerceIn(0f, 1f)
        } else {
            1f
        }

        // Влив заканчивается только по факту — по достижении цели или остановке
        // веса. Рекомендованное время лишь рисует отметку на кольце: человек
        // может закрыть чайник и раньше, и позже, и подсказка должна это терпеть.
        //
        // Без весов факта нет, и остаётся время: шаг льётся ровно столько, сколько
        // просит заданная скорость, дальше выстаивается. Иначе заваривание без
        // весов навсегда застревало бы в состоянии «идёт влив».
        val pourDone = delta <= 0f || pourDoneStepIndex == index ||
            (!measuring && stepElapsed >= pourSeconds)
        val stepPhase = if (pourDone) StepPhase.WAITING else StepPhase.POURING
        val targetNow = previousTarget + delta * pourProgress

        val pace = when {
            // Судить о темпе можно только по весам. Без них любая оценка — выдумка:
            // вес стоит на нуле, и человек всё заваривание слышал бы «лейте быстрее».
            !measuring -> Pace.ON_TRACK
            stepPhase == StepPhase.WAITING || targetFlowRate <= 0f -> Pace.ON_TRACK
            // Первые секунды струя только устанавливается, ругаться рано.
            stepElapsed < PACE_GRACE_SECONDS -> Pace.ON_TRACK
            state.flowRate > targetFlowRate * (1f + paceTolerance) -> Pace.TOO_FAST
            state.flowRate < targetFlowRate * (1f - paceTolerance) -> Pace.TOO_SLOW
            else -> Pace.ON_TRACK
        }

        // Следующий долив ищем по воде, а не по типу шага: доливом считается любой
        // шаг, который требует больше, чем уже налито.
        var running = previousTarget
        val nextPour = steps.drop(index).firstOrNull { candidate ->
            val adds = candidate.targetWaterGrams > running
            running = maxOf(running, candidate.targetWaterGrams)
            adds && candidate.startSec > elapsedSec
        }
        val secondsToNextPour = nextPour?.let { ceil(it.startSec - elapsedSec).toInt() }

        // Пока шаг выстаивается, полезнее знать не «руки прочь», а как лить дальше:
        // сравниваем только что показанную скорость с той, что просит следующий пролив.
        val nextPourFlow = nextPour?.let { candidate ->
            val previous = steps.takeWhile { it !== candidate }.maxOfOrNull { it.targetWaterGrams } ?: 0f
            val nextDelta = candidate.targetWaterGrams - previous
            val seconds = candidate.pourSeconds(nextDelta)
            if (seconds > 0f) nextDelta / seconds else null
        }
        val nextHint = if (stepPhase == StepPhase.WAITING) {
            compareNextPour(lastPourFlowRate, nextPourFlow, paceTolerance)
        } else {
            null
        }

        val total = recipe.totalSec.takeIf { it > 0 } ?: 1
        return Guidance(
            stepIndex = index,
            stepCount = steps.size,
            step = step,
            nextStep = next,
            nextStepDeltaGrams = next
                ?.let { it.targetWaterGrams - step.targetWaterGrams }
                ?.takeIf { it > 0f },
            stepDeltaGrams = delta,
            nextPourHint = nextHint,
            nextPourFlowRate = nextPourFlow,
            lastPourFlowRate = lastPourFlowRate.takeIf { it > 0f },
            stepProgress = stepProgress,
            secondsLeftInStep = secondsLeft,
            targetNowGrams = targetNow,
            targetEndGrams = step.targetWaterGrams,
            remainingGrams = (step.targetWaterGrams - state.weightGrams).coerceAtLeast(0f),
            pace = pace,
            stepPhase = stepPhase,
            targetFlowRate = targetFlowRate,
            pourEndFraction = if (step.durationSec > 0 && pourSeconds > 0f) {
                (pourSeconds / step.durationSec).coerceIn(0f, 1f)
            } else {
                0f
            },
            totalProgress = (elapsedSec / total).coerceIn(0f, 1f),
            secondsToNextPour = secondsToNextPour,
        )
    }

    /**
     * Влив считается законченным, когда вес перешагнул цель или почти дошёл до неё
     * и перестал расти. Ждать конца отведённого времени нельзя: человек закрывает
     * чайник раньше, и всё это время подсказка кричала бы «лей быстрее».
     */
    private fun detectPourFinished(guidance: Guidance, weight: Float, nowMs: Long) {
        if (guidance.stepIndex != pourTrackedStepIndex) {
            pourTrackedStepIndex = guidance.stepIndex
            pourStartedAtMs = 0L
            pourStartWeight = 0f
        }
        if (guidance.stepPhase == StepPhase.WAITING) return
        val target = guidance.targetEndGrams

        // Момент, когда человек действительно открыл чайник, а не когда
        // формально начался шаг: с него и считаем фактическую скорость.
        val poured = weight - (target - guidance.stepDeltaGrams)
        if (pourStartedAtMs == 0L && poured >= POUR_TRACK_START_GRAMS) {
            pourStartedAtMs = nowMs
            pourStartWeight = weight
        }

        if (weight >= target) {
            rememberPourFlowRate(weight, nowMs)
            markPourDone(guidance)
            return
        }

        if (kotlin.math.abs(weight - steadyWeight) > STEADY_TOLERANCE_GRAMS) {
            steadyWeight = weight
            steadySinceMs = nowMs
            return
        }
        val closeEnough = target - weight <= NEAR_STOP_GRAMS
        if (closeEnough && steadySinceMs != 0L && nowMs - steadySinceMs >= STEADY_HOLD_MS) {
            rememberPourFlowRate(weight, steadySinceMs)
            markPourDone(guidance)
        }
    }

    /**
     * Влив закончен. Если дальше по рецепту слив — ждать конца шага незачем:
     * вода уже вся в воронке, и уходить она начинает прямо сейчас.
     */
    private fun markPourDone(guidance: Guidance) {
        pourDoneStepIndex = guidance.stepIndex
        // Дальше рецепт воды не требует: с этого момента любое падение веса —
        // это снятая с весов чашка, а не пролив.
        // В режиме аэропресса сторож не взводим: там вес падает от отжима, а не
        // от снятой чашки, и заваривание заканчивают руками.
        if (isLastPour(guidance) && baseRecipe?.aeropressMode != true) {
            removal.arm(_state.value.weightGrams)
        }
        if (guidance.nextStep?.kind != StepKind.DRAWDOWN) return
        val left = guidance.step.endSec - (currentElapsedSec + timelineShiftSec)
        if (left > 0f) timelineShiftSec += left
    }

    /** Последний ли это влив рецепта: дальше воды больше не требуют. */
    private fun isLastPour(guidance: Guidance): Boolean {
        val steps = _state.value.recipe?.steps ?: return false
        return steps.drop(guidance.stepIndex + 1)
            .none { it.targetWaterGrams > guidance.step.targetWaterGrams }
    }

    private fun rememberPourFlowRate(weight: Float, finishedAtMs: Long) {
        if (pourStartedAtMs == 0L) return
        val seconds = (finishedAtMs - pourStartedAtMs) / 1000f
        if (seconds < MIN_MEASURED_POUR_SECONDS) return
        val poured = weight - pourStartWeight
        if (poured <= 0f) return
        lastPourFlowRate = poured / seconds
    }

    private companion object {
        const val TICK_MS = 100L
        const val TICKS_PER_SECOND = 10
        const val SAMPLE_WINDOW = 60
        const val MIN_FLOW_FOR_AVERAGE = 0.1f

        /** Быстрее этого не льют: 25 г/с — это полтора литра в минуту. */
        const val MAX_PLAUSIBLE_FLOW = 25f
        const val COUNTDOWN_FROM = 3

        /** Насколько близко к цели считается «долил» при остановившемся весе. */
        const val NEAR_STOP_GRAMS = 5f
        const val STEADY_TOLERANCE_GRAMS = 0.4f
        const val STEADY_HOLD_MS = 3_000L

        /** Пока струя устанавливается, о скорости не судим. */
        const val PACE_GRACE_SECONDS = 2f

        /** С какого долива начинаем мерить фактическую скорость влива. */
        const val POUR_TRACK_START_GRAMS = 2f
        const val MIN_MEASURED_POUR_SECONDS = 2f

        const val DEFAULT_WATER_TEMP_C = 94
    }
}
