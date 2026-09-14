package com.pourista.data.presets

import com.pourista.data.model.Recipe
import com.pourista.data.model.RecipeStep
import com.pourista.data.model.StepKind
import kotlin.math.roundToInt

/**
 * The taste balance: the first 40% of the water rules it, split into two pours.
 *
 * The rule of Tetsu Kasuya: a first pour smaller than the second makes the cup sweeter, a
 * first pour larger than the second brings out the acidity. The share here is the part of
 * those 40% that falls to the first pour; the 9% step comes from the original app.
 */
enum class FortySixTaste(val firstPourShare: Float) {
    SWEET(0.32f),
    SWEET_NORMAL(0.41f),
    NORMAL(0.50f),
    NORMAL_ACID(0.59f),
    ACID(0.68f),
}

/**
 * The strength: the remaining 60% of the water. The more pours it is split into, the denser
 * the cup — one pour gives a light one, five the richest.
 */
enum class FortySixStrength(val pours: Int) {
    LOWER(1),
    LOW(2),
    NORMAL(3),
    HIGH(4),
    HIGHER(5),
}

/** Settings of the 4:6 method. The defaults are the Kasuya recipe as it is. */
data class FortySixParams(
    val doseGrams: Float = FortySixGenerator.DEFAULT_DOSE_GRAMS,
    /** Water per gram of coffee: 1:[ratio]. */
    val ratio: Float = FortySixGenerator.DEFAULT_RATIO,
    val taste: FortySixTaste = FortySixTaste.NORMAL,
    val strength: FortySixStrength = FortySixStrength.NORMAL,
    val waterTempC: Int = FortySixGenerator.DEFAULT_TEMP_C,
)

/** One pour of the plan: when to start, how much to add and how much there will be in total. */
data class FortySixPour(
    val startSec: Int,
    val durationSec: Int,
    val addGrams: Float,
    val totalGrams: Float,
)

/**
 * The 4:6 method of Tetsu Kasuya, assembled into a recipe.
 *
 * Two dials. The first 40% of the water is split into two pours and sets the balance of
 * acidity and sweetness. The remaining 60% is split into one to five pours and sets the
 * strength; they are laid out evenly over the time left until the end of the brew, so the
 * more of them there are, the more often they come.
 *
 * The schedule is rigid, as in the original: the first pour at 0:00, the second at 0:45,
 * the third at 1:30, the end at 3:30.
 *
 * The class knows nothing about Android or the database: numbers in, steps or a finished
 * recipe out. The names and the notes come from outside — they are translated together with
 * the app.
 */
object FortySixGenerator {

    /** How much water in total: the dose multiplied by the ratio. */
    fun waterGrams(params: FortySixParams): Float =
        round(params.doseGrams.coerceAtLeast(0f) * params.ratio)

    /**
     * The plan of pours. The cumulative targets are counted from exact shares and rounded
     * to the gram: pouring "up to 137.5 g" by the scale readings is impossible.
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
            // Rounding must not break the order of the targets: a pour cannot ask for less
            // than is already poured.
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

    /** The same pours, but as recipe steps: the bloom first, the drawdown at the end. */
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
        // The original has no drawdown: the brew there ends at 3:30. We need one — by it the
        // app waits for the water to go through and catches the lifted cone. The schedule of
        // the pours does not change because of it.
        return steps + RecipeStep(
            kind = StepKind.DRAWDOWN,
            startSec = last.startSec + last.durationSec,
            durationSec = DRAWDOWN_SEC,
            targetWaterGrams = last.totalGrams,
        )
    }

    /**
     * The finished recipe. Everything translatable — the name, the notes, the grind — comes
     * from outside: the generator must not depend on the language of the app.
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

    /** The share of water for the first pair of pours — the very "4" of the method name. */
    private const val FIRST_PART_SHARE = 0.4f
    private const val MIN_POUR_GRAMS = 1f

    private const val FIRST_POUR_SEC = 0
    private const val SECOND_POUR_SEC = 45

    /** The strength pours start here and split the rest of the time evenly. */
    private const val STRENGTH_START_SEC = 90
    private const val TOTAL_SEC = 210
    private const val DRAWDOWN_SEC = 30

    const val DEFAULT_DOSE_GRAMS = 15f
    /** 15 g of coffee and exactly 250 g of water — the starting recipe of the original. */
    const val DEFAULT_RATIO = 250f / 15f
    const val DEFAULT_TEMP_C = 93
    const val DEFAULT_FLOW_RATE = 6f
    const val DEFAULT_BREWER = "Hario V60-02"

    private fun round(value: Float): Float = value.roundToInt().toFloat()
}
