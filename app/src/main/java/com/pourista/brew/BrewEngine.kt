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

/** How many grams before the target to warn by default. */
const val DEFAULT_NEAR_TARGET_GRAMS = 5f

/**
 * How far the flow rate may drift from the recipe before the app says to pour
 * faster or slower. A share of the target rate.
 */
const val DEFAULT_PACE_TOLERANCE = 0.1f

enum class BrewPhase { IDLE, RUNNING, PAUSED, FINISHED }

/** How well the pour keeps to the recipe. */
enum class Pace { ON_TRACK, TOO_FAST, TOO_SLOW }

/** What is being done inside the step right now. */
enum class StepPhase { POURING, WAITING }

/** How to pour the next one compared with the pour just finished. */
enum class NextPourHint { SAME, FASTER, SLOWER }

/**
 * Compares the measured rate of the pour just finished with the one the next pour
 * asks for. A difference below [tolerance] goes unmentioned: a "slightly faster"
 * over five percent is nothing but a distraction.
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
    /** How much water the next step will ask for on top of what is already poured. */
    val nextStepDeltaGrams: Float?,
    /** How much water this step adds. */
    val stepDeltaGrams: Float,
    /** How to pour the next one relative to the pour just finished. */
    val nextPourHint: NextPourHint?,
    /** Target rate of the next pour, g/s. */
    val nextPourFlowRate: Float?,
    /** The rate the previous pour was actually poured at, g/s. */
    val lastPourFlowRate: Float?,
    /** Share of the step time already gone, 0..1. */
    val stepProgress: Float,
    val secondsLeftInStep: Int,
    /** How much water should be on the scale right now. */
    val targetNowGrams: Float,
    /** The target for the end of the current step. */
    val targetEndGrams: Float,
    /** How much is left to pour before the step ends. */
    val remainingGrams: Float,
    val pace: Pace,
    /** The pour is running, or the step is already settling. */
    val stepPhase: StepPhase,
    /** Flow rate the recipe asks for, g/s. */
    val targetFlowRate: Float,
    /** Where the end-of-pour mark sits on the step ring, 0..1. */
    val pourEndFraction: Float,
    val totalProgress: Float,
    val secondsToNextPour: Int?,
)

sealed interface BrewEvent {
    data class StepChanged(val index: Int, val step: RecipeStep) : BrewEvent
    data class Countdown(val secondsLeft: Int) : BrewEvent

    /** Very little left to the step target — time to get ready to close the kettle. */
    data class NearTarget(val remainingGrams: Float) : BrewEvent

    /**
     * The recipe plan is played out: the time of the last step, usually the
     * drawdown, is up. The brew is still running — the cone comes off by hand.
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
    /** Recipe targets recalculated for the actual dose. */
    val recipeScaled: Boolean = false,
    val guidance: Guidance? = null,
    /**
     * Auto-start is armed right now: the next water on the scale starts the timer.
     * The state lasts one brew — off by default, armed by the checkbox next to
     * "Start" or by itself after the dose is recorded, if the recipe says so.
     */
    val autoStartArmed: Boolean = false,
    /** A recipe is being recorded from a real pour. */
    val recording: Boolean = false,
    /** How many pours have been recognised during the recording. */
    val recordedPours: Int = 0,
) {
    val elapsedSec: Int get() = (elapsedMs / 1000).toInt()
    val isRunning: Boolean get() = phase == BrewPhase.RUNNING
    val hasData: Boolean get() = weightSeries.isNotEmpty() || elapsedMs > 0
    val ratio: Float get() = if (doseGrams > 0f) weightGrams / doseGrams else 0f
}

/**
 * The course of a brew: timer, flow rate and guidance by the recipe.
 *
 * Lives at application level, so moving to another screen or rotating the phone
 * does not drop a brew already under way.
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

    /** The count runs off the monotonic clock, so the timer does not drift. */
    private var startedAtElapsedRealtime = 0L
    private var accumulatedMs = 0L

    /** Flow rate: the number from the scale if it counts one, otherwise our own estimate. */
    private val flow = FlowRate()
    private val flowForAverage = mutableListOf<Float>()

    private var lastChartSecond = -1
    private var lastStepIndex = -1
    private var lastCountdownSecond = -1

    /** The step whose pour is already done — by reaching the target or by the weight stopping. */
    private var pourDoneStepIndex = -1
    private var nearTargetStepIndex = -1

    /** The end-of-plan signal is given once per brew. */
    private var planFinishedEmitted = false
    private var steadyWeight = 0f
    private var steadySinceMs = 0L

    /** The recipe as saved; the state may hold a copy recalculated for the dose. */
    private var baseRecipe: Recipe? = null

    /** How many grams before the target to warn. Set in the settings. */
    @Volatile
    var nearTargetGrams: Float = DEFAULT_NEAR_TARGET_GRAMS

    /** Allowed drift of the flow rate from the recipe. Set in the settings. */
    @Volatile
    var paceTolerance: Float = DEFAULT_PACE_TOLERANCE

    /** How much to smooth the flow rate on screen. Set in the settings. */
    var flowSmoothing: FlowSmoothing
        get() = flow.smoothing
        set(value) { flow.smoothing = value }

    /**
     * Keep the water as the recipe wrote it, without fitting it to the actual dose.
     * The setting is global: it is turned on deliberately, for when more coffee is
     * ground on purpose, for a denser cup.
     */
    private var keepRecipeWater: Boolean = false

    fun setKeepRecipeWater(enabled: Boolean) {
        if (keepRecipeWater == enabled) return
        keepRecipeWater = enabled
        _state.update { current -> current.withRecipeForDose(baseRecipe, current.doseGrams) }
    }

    /**
     * How many seconds the recipe has "slid" forward relative to the stopwatch.
     * It grows when a pour is finished early and there is nothing to wait for:
     * the drawdown starts at once. The brew itself keeps to the real clock.
     */
    private var timelineShiftSec = 0f

    /** Seconds since the start of the brew on this tick — without the recipe shift. */
    private var currentElapsedSec = 0f

    /**
     * Schedule pull-ins collected during this brew: the second a step stood at in
     * the recipe, and how many seconds earlier it started. Kept apart from the plan,
     * because recalculating for the dose builds the recipe again from the saved one.
     */
    private val pullIns = linkedMapOf<Int, Int>()

    /** Measured rate of the last finished pour, g/s. */
    private var lastPourFlowRate = 0f
    private var pourTrackedStepIndex = -1
    private var pourStartedAtMs = 0L
    private var pourStartWeight = 0f

    private val recorder = PourRecorder()

    /** The end-of-brew watch: armed once the last pour is done. */
    private val removal = RemovalWatch()

    /**
     * Whether to end the brew by the cone being lifted. With auto-finish off the
     * drops are still counted: "Finish" pressed by hand uses them to write the
     * weight from before the cup was taken off into the history.
     */
    @Volatile
    var autoFinish: Boolean = true

    /** Weight for calculations: without the dips from a wobbling cone. */
    private val pouredWeight = MonotonicWeight()

    fun selectRecipe(recipe: Recipe?) {
        baseRecipe = recipe
        _state.update { current -> current.withRecipeForDose(recipe, current.doseGrams) }
    }

    /**
     * The current weight becomes the coffee dose and the scale is zeroed. If the
     * recipe asks for auto-start, it is armed here too: the dose is recorded, and
     * the next water on the scale is already the pour.
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
     * Puts in the recipe recalculated for the actual dose. It is always counted
     * from the saved recipe, otherwise dosing twice would scale what was already
     * scaled.
     */
    private fun BrewState.withRecipeForDose(base: Recipe?, dose: Float): BrewState {
        if (base == null) return copy(recipe = null, recipeScaled = false, guidance = null)
        val adjusted = if (dose > 0f && !keepRecipeWater) base.scaledToDose(dose) else base
        val scaled = adjusted.waterGrams != base.waterGrams || adjusted.doseGrams != base.doseGrams
        // Pulled-in steps carry over to the rebuilt recipe: the dose changes the
        // water, not the schedule.
        val planned = adjusted.withPullIns(pullIns)
        val next = copy(recipe = planned, recipeScaled = scaled)
        return next.copy(guidance = guidanceFor(planned, next))
    }

    fun tare() = scale.tare()

    /** Start recording: the brew begins with a clean sheet. */
    fun startRecording() {
        reset()
        recorder.reset()
        _state.update { it.copy(recording = true, recordedPours = 0) }
    }

    fun cancelRecording() {
        recorder.reset()
        _state.update { it.copy(recording = false, recordedPours = 0) }
    }

    /** Builds a recipe out of the recorded pour. */
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
     * Finish by the button. If the weight has already dropped by then, the brew is
     * closed the same way auto-finish closes it: the cone came off before the press,
     * and the weight from before the drop is what belongs in the history.
     */
    fun finish() {
        if (removal.dropPending) finishAfterRemoval() else finishAt(SystemClock.elapsedRealtime())
    }

    /**
     * Ends the brew at [atMs] by the monotonic clock. Auto-finish needs a moment of
     * its own: the cup was taken off before the watch made sure of it, and the extra
     * seconds have no business in the history.
     */
    private fun finishAt(atMs: Long, transform: (BrewState) -> BrewState = { it }) {
        if (_state.value.phase == BrewPhase.FINISHED) return
        if (_state.value.phase == BrewPhase.RUNNING) {
            accumulatedMs += (atMs - startedAtElapsedRealtime).coerceAtLeast(0L)
        }
        tickerJob?.cancel()
        tickerJob = null
        removal.reset()
        // The weight is corrected by the same update as the finish: otherwise a
        // reading from the scale could land between the two.
        _state.update { transform(it).copy(phase = BrewPhase.FINISHED, elapsedMs = accumulatedMs) }
        _events.tryEmit(BrewEvent.Finished)
    }

    fun reset() {
        tickerJob?.cancel()
        tickerJob = null
        accumulatedMs = 0L
        startedAtElapsedRealtime = 0L
        flow.reset()
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
        pullIns.clear()
        removal.reset()
        pouredWeight.reset()
        // Bring back the original recipe: the scaling was tied to the previous dose.
        _state.value = BrewState(recipe = baseRecipe)
        scale.tare()
    }

    /** The weight arrives from the scale outside a running timer too — for auto-start and the dose. */
    fun onWeightChanged(grams: Float) {
        // After the finish the readings are frozen: the screen and the history must
        // keep what was poured, not the zero of a scale the cup has been taken off.
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

        // For the flow rate, the charts and the end of a pour we take the
        // non-decreasing weight: a wobbling cone drops the reading, and its return
        // would look like a furious pour. In aeropress mode it is the other way
        // round: the plunger drops the weight for a reason, and smoothing it away
        // would hide the moment the pressing started.
        val raw = current.weightGrams.coerceAtLeast(0f)
        val aeropress = current.recipe?.aeropressMode == true
        val weight = if (aeropress) raw else pouredWeight.onSample(raw, nowMs)

        val flowRate = flow.onSample(weight, scale.state.value.flowRate, nowMs)
        if (flowRate >= MIN_FLOW_FOR_AVERAGE) flowForAverage += flowRate
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

        // Once the end of the pour is known the guidance is rebuilt: the step status
        // could have changed just now, and showing a stale "pouring" is wrong. The
        // plan is taken from the state rather than from `next`: the end of the pour
        // may have pulled the swirl in, and the schedule there is a different one.
        val plan = _state.value.recipe
        val guidance = plan?.let { guidanceFor(it, next) }
        _state.value = next.copy(recipe = plan, guidance = guidance)

        if (guidance != null) {
            emitCues(guidance)
            armRemovalWhenWaterDone(guidance, weight)
        }

        // The weight is taken as it is, without clamping at zero: a cup lifted off
        // entirely sends the scale negative, and that is the clearest sign the brew
        // is over. In aeropress mode the watch has nothing to do: the weight there
        // falls from the plunger, not from a lifted cup, and taking the press for
        // the end is wrong — here as well as in the "Finish" button, which looks at
        // the same watch.
        if (aeropress) return
        if (removal.onSample(current.weightGrams, nowMs) && autoFinish) finishAfterRemoval()
    }

    /**
     * Arms the end watch as soon as the recipe stops asking for water.
     *
     * Normally the end-of-pour detector does that, but it is bound to a step: if the
     * water was added after the step had already changed, the pour "never finished",
     * and nobody was waiting for the cone to come off. So the watch is armed by the
     * fact as well — when as much is poured as the recipe asks, or when the plan is
     * played out.
     */
    private fun armRemovalWhenWaterDone(guidance: Guidance, weight: Float) {
        if (removal.armed || baseRecipe?.aeropressMode == true) return
        val recipe = _state.value.recipe ?: return

        val poured = weight >= recipe.finalTargetGrams - NEAR_STOP_GRAMS
        val planOver = guidance.stepIndex == guidance.stepCount - 1 &&
            guidance.secondsLeftInStep <= 0
        if (poured || planOver) removal.arm(weight)
    }

    /**
     * Auto-finish: the cone or the cup has been lifted off the scale. The brew is
     * closed at the time the weight fell, and the weight goes back to the last settled
     * one — together with the charts, where the seconds of the fall already landed.
     */
    private fun finishAfterRemoval() {
        val restored = removal.weightBeforeDrop
        finishAt(removal.droppedAtMs) { state ->
            // The tail of the chart is trimmed by the restored weight rather than by
            // the watch threshold: between a full cone and the fall below the
            // threshold the dip lands on the chart, and the chart would then disagree
            // with the total on the card.
            val weights = state.weightSeries
                .dropLastWhile { it < restored - CHART_TAIL_GRAMS }
                .ifEmpty { state.weightSeries }
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

        // A separate cue shortly before the target: the kettle has to be closed in
        // advance, water keeps coming out of the spout for another couple of seconds.
        if (guidance.stepPhase == StepPhase.POURING &&
            guidance.stepIndex != nearTargetStepIndex &&
            guidance.remainingGrams in 0.1f..nearTargetGrams
        ) {
            nearTargetStepIndex = guidance.stepIndex
            _events.tryEmit(BrewEvent.NearTarget(guidance.remainingGrams))
        }
        // The last step is played out: the recipe is over, time to take the cone off.
        // The finish does not come with it — that is for the lifted cup or the button.
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

    /** The scale is connected: only then does the weight mean anything. */
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
        // Rounded up: while the last second of the step runs, the ring must show a
        // one. A zero would mean an extra second of waiting that is not there.
        val secondsLeft = ceil(step.endSec - elapsedSec).toInt().coerceAtLeast(0)

        val previousTarget = steps.take(index).maxOfOrNull { it.targetWaterGrams } ?: 0f
        val delta = step.targetWaterGrams - previousTarget

        // A recipe is written as "50 g, 45 seconds": that does not mean pouring for
        // forty-five. The pour time comes from the given rate, the rest the step settles.
        val pourSeconds = step.pourSeconds(delta)
        val targetFlowRate = if (pourSeconds > 0f) delta / pourSeconds else 0f
        val pourProgress = if (pourSeconds > 0f) {
            (stepElapsed / pourSeconds).coerceIn(0f, 1f)
        } else {
            1f
        }

        // A pour ends by fact only — by reaching the target or by the weight stopping.
        // The recommended time merely draws a mark on the ring: the kettle may be
        // closed earlier or later, and the guidance has to bear that.
        //
        // Without a scale there is no fact, and time is all that is left: the step
        // pours for exactly as long as the given rate asks, then settles. Otherwise a
        // brew without a scale would be stuck in "pouring" forever.
        val pourDone = delta <= 0f || pourDoneStepIndex == index ||
            (!measuring && stepElapsed >= pourSeconds)
        val stepPhase = if (pourDone) StepPhase.WAITING else StepPhase.POURING
        val targetNow = previousTarget + delta * pourProgress

        val pace = when {
            // The pace can only be judged by the scale. Without it any verdict is a
            // fabrication: the weight sits at zero, and the whole brew would be spent
            // hearing "pour faster".
            !measuring -> Pace.ON_TRACK
            stepPhase == StepPhase.WAITING || targetFlowRate <= 0f -> Pace.ON_TRACK
            // In the first seconds the stream is only settling in, too early to complain.
            stepElapsed < PACE_GRACE_SECONDS -> Pace.ON_TRACK
            state.flowRate > targetFlowRate * (1f + paceTolerance) -> Pace.TOO_FAST
            state.flowRate < targetFlowRate * (1f - paceTolerance) -> Pace.TOO_SLOW
            else -> Pace.ON_TRACK
        }

        // The next pour is looked for by water rather than by step kind: a pour is any
        // step that asks for more than is already on the scale.
        var running = previousTarget
        val nextPour = steps.drop(index).firstOrNull { candidate ->
            val adds = candidate.targetWaterGrams > running
            running = maxOf(running, candidate.targetWaterGrams)
            adds && candidate.startSec > elapsedSec
        }
        val secondsToNextPour = nextPour?.let { ceil(it.startSec - elapsedSec).toInt() }

        // While a step settles, what helps is not "hands off" but how to pour next:
        // compare the rate just shown with the one the next pour asks for.
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
     * A pour counts as finished when the weight has stepped over the target, or has
     * almost reached it and stopped growing. Waiting out the allotted time is not an
     * option: the kettle is closed earlier, and all that time the guidance would be
     * shouting "pour faster".
     */
    private fun detectPourFinished(guidance: Guidance, weight: Float, nowMs: Long) {
        if (guidance.stepIndex != pourTrackedStepIndex) {
            pourTrackedStepIndex = guidance.stepIndex
            pourStartedAtMs = 0L
            pourStartWeight = 0f
        }
        if (guidance.stepPhase == StepPhase.WAITING) return
        val target = guidance.targetEndGrams

        // The moment the kettle was really opened, not the one the step formally
        // started at: the measured rate is counted from there.
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
     * The pour is done. If a drawdown or a swirl comes next in the recipe, there is
     * nothing to wait the step out for: the water is already in the cone.
     */
    private fun markPourDone(guidance: Guidance) {
        pourDoneStepIndex = guidance.stepIndex
        // The recipe asks for no more water: from this moment any fall of the weight
        // is a cup taken off the scale, not a pour.
        // In aeropress mode the watch is not armed: the weight there falls from the
        // plunger, not from a lifted cup, and the brew is ended by hand.
        if (isLastPour(guidance) && baseRecipe?.aeropressMode != true) {
            removal.arm(_state.value.weightGrams)
        }
        val next = guidance.nextStep ?: return
        val nowSec = currentElapsedSec + timelineShiftSec
        if (next.kind == StepKind.DRAWDOWN) {
            // The drawdown starts right now, and the rest of the step is simply not
            // needed: the brew will end earlier.
            val left = guidance.step.endSec - nowSec
            if (left > 0f) timelineShiftSec += left
            return
        }
        if (!next.kind.isAgitation) return
        // The swirl is moved to the end of the pour by whole seconds, and upwards:
        // the plan lives in seconds, and a swirl must not start later than the pour.
        val shift = ceil(next.startSec - nowSec).toInt()
        if (shift <= 0) return
        pullInStep(next.startSec, shift)
    }

    /**
     * Moves a step [shiftSec] seconds back in the schedule of this brew. The recipe
     * in the database is left alone: a pull-in lives exactly one cup.
     */
    private fun pullInStep(stepStartSec: Int, shiftSec: Int) {
        val plan = _state.value.recipe ?: return
        val pulled = plan.withPullIns(mapOf(stepStartSec to shiftSec))
        if (pulled === plan) return
        pullIns[stepStartSec] = shiftSec
        _state.update { it.copy(recipe = pulled) }
    }

    /** Whether this is the last pour of the recipe: no more water is asked for. */
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
        const val MIN_FLOW_FOR_AVERAGE = 0.1f
        const val COUNTDOWN_FROM = 3

        /** How close to the target counts as "poured" once the weight has stopped. */
        const val NEAR_STOP_GRAMS = 5f

        /** The tolerance the chart tail is trimmed to the final weight with. */
        const val CHART_TAIL_GRAMS = 1f
        const val STEADY_TOLERANCE_GRAMS = 0.4f
        const val STEADY_HOLD_MS = 3_000L

        /** While the stream settles in, the rate is not judged. */
        const val PACE_GRACE_SECONDS = 2f

        /** The amount poured from which the measured rate starts being counted. */
        const val POUR_TRACK_START_GRAMS = 2f
        const val MIN_MEASURED_POUR_SECONDS = 2f

        const val DEFAULT_WATER_TEMP_C = 94
    }
}
