package com.pourista.data.presets

import com.pourista.data.model.Recipe
import com.pourista.data.model.RecipeStep
import com.pourista.data.model.StepKind
import kotlin.math.roundToInt

/**
 * Баланс вкуса: им распоряжаются первые 40 % воды, разделённые на два пролива.
 *
 * Правило Тэцу Кацуи: первый пролив меньше второго — чашка слаще, первый
 * больше второго — ярче кислотность. Доля здесь — часть этих 40 %,
 * приходящаяся на первый пролив; шаг в 9 % взят из оригинального приложения.
 */
enum class FortySixTaste(val firstPourShare: Float) {
    SWEET(0.32f),
    SWEET_NORMAL(0.41f),
    NORMAL(0.50f),
    NORMAL_ACID(0.59f),
    ACID(0.68f),
}

/**
 * Крепость: оставшиеся 60 % воды. Чем на большее число проливов они разбиты,
 * тем плотнее чашка — один пролив даёт лёгкую, пять самую насыщенную.
 */
enum class FortySixStrength(val pours: Int) {
    LOWER(1),
    LOW(2),
    NORMAL(3),
    HIGH(4),
    HIGHER(5),
}

/** Настройки метода 4:6. Значения по умолчанию — рецепт Кацуи как он есть. */
data class FortySixParams(
    val doseGrams: Float = FortySixGenerator.DEFAULT_DOSE_GRAMS,
    /** Воды на грамм кофе: 1:[ratio]. */
    val ratio: Float = FortySixGenerator.DEFAULT_RATIO,
    val taste: FortySixTaste = FortySixTaste.NORMAL,
    val strength: FortySixStrength = FortySixStrength.NORMAL,
    val waterTempC: Int = FortySixGenerator.DEFAULT_TEMP_C,
)

/** Один пролив плана: когда начинать, сколько долить и сколько станет всего. */
data class FortySixPour(
    val startSec: Int,
    val durationSec: Int,
    val addGrams: Float,
    val totalGrams: Float,
)

/**
 * Метод 4:6 Тэцу Кацуи, собранный в рецепт.
 *
 * Две ручки. Первые 40 % воды делятся на два пролива и задают баланс
 * кислотности и сладости. Оставшиеся 60 % делятся на один—пять проливов и
 * задают крепость; они равномерно раскладываются по времени, которое осталось
 * до конца заваривания, поэтому чем их больше, тем чаще они идут.
 *
 * Расписание жёсткое, как в оригинале: первый пролив в 0:00, второй в 0:45,
 * третий в 1:30, конец в 3:30.
 *
 * Класс ничего не знает ни об Android, ни о базе: на входе числа, на выходе
 * шаги или готовый рецепт. Названия и заметки приходят снаружи — они
 * переводятся вместе с приложением.
 */
object FortySixGenerator {

    /** Сколько всего воды: доза, умноженная на пропорцию. */
    fun waterGrams(params: FortySixParams): Float =
        round(params.doseGrams.coerceAtLeast(0f) * params.ratio)

    /**
     * План проливов. Накопительные цели считаются от точных долей и
     * округляются до грамма: лить «до 137,5 г» по показаниям весов невозможно.
     */
    fun pours(params: FortySixParams): List<FortySixPour> {
        val total = waterGrams(params)
        if (total <= 0f) return emptyList()

        val firstPart = total * FIRST_PART_SHARE
        val strengthPours = params.strength.pours
        val strengthStep = (TOTAL_SEC - STRENGTH_START_SEC) / strengthPours

        val plan = mutableListOf<Pair<Int, Float>>()
        plan += FIRST_POUR_SEC to firstPart * params.taste.firstPourShare
        plan += SECOND_POUR_SEC to firstPart
        for (index in 1..strengthPours) {
            plan += (STRENGTH_START_SEC + (index - 1) * strengthStep) to
                firstPart + (total - firstPart) * index / strengthPours
        }

        var poured = 0f
        return plan.mapIndexed { index, (startSec, target) ->
            // Порядок целей округление ломать не должно: пролив не может
            // требовать меньше, чем уже налито.
            val cumulative = maxOf(round(target), poured + MIN_POUR_GRAMS)
            val nextStart = plan.getOrNull(index + 1)?.first ?: TOTAL_SEC
            val pour = FortySixPour(
                startSec = startSec,
                durationSec = nextStart - startSec,
                addGrams = cumulative - poured,
                totalGrams = cumulative,
            )
            poured = cumulative
            pour
        }
    }

    /** Те же проливы, но шагами рецепта: первый — блуминг, в конце слив. */
    fun steps(params: FortySixParams): List<RecipeStep> {
        val pours = pours(params)
        if (pours.isEmpty()) return emptyList()

        val steps = pours.mapIndexed { index, pour ->
            RecipeStep(
                kind = if (index == 0) StepKind.BLOOM else StepKind.POUR,
                startSec = pour.startSec,
                durationSec = pour.durationSec,
                targetWaterGrams = pour.totalGrams,
                pourFlowRate = DEFAULT_FLOW_RATE,
            )
        }
        val last = pours.last()
        // Слива в оригинале нет: там заваривание кончается на 3:30. Нам он
        // нужен — по нему приложение ждёт, пока вода уйдёт, и ловит снятую
        // воронку. Расписание проливов от этого не меняется.
        return steps + RecipeStep(
            kind = StepKind.DRAWDOWN,
            startSec = last.startSec + last.durationSec,
            durationSec = DRAWDOWN_SEC,
            targetWaterGrams = last.totalGrams,
        )
    }

    /**
     * Готовый рецепт. Всё, что переводится — название, заметки, помол, — берётся
     * снаружи: генератор не должен зависеть от языка приложения.
     */
    fun recipe(
        params: FortySixParams,
        name: String,
        brewer: String = DEFAULT_BREWER,
        grindSetting: String? = null,
        notes: String? = null,
    ): Recipe = Recipe(
        name = name,
        brewer = brewer,
        doseGrams = params.doseGrams,
        waterGrams = waterGrams(params),
        waterTempC = params.waterTempC,
        grindSetting = grindSetting,
        notes = notes,
        steps = steps(params),
    )

    /** Доля воды на первую пару проливов — те самые «4» из названия метода. */
    private const val FIRST_PART_SHARE = 0.4f
    private const val MIN_POUR_GRAMS = 1f

    private const val FIRST_POUR_SEC = 0
    private const val SECOND_POUR_SEC = 45

    /** Проливы на крепость начинаются здесь и делят поровну остаток времени. */
    private const val STRENGTH_START_SEC = 90
    private const val TOTAL_SEC = 210
    private const val DRAWDOWN_SEC = 30

    const val DEFAULT_DOSE_GRAMS = 15f
    /** 15 г кофе и ровно 250 г воды — стартовый рецепт оригинала. */
    const val DEFAULT_RATIO = 250f / 15f
    const val DEFAULT_TEMP_C = 93
    const val DEFAULT_FLOW_RATE = 6f
    const val DEFAULT_BREWER = "Hario V60-02"

    private fun round(value: Float): Float = value.roundToInt().toFloat()
}
