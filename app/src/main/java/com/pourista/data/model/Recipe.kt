package com.pourista.data.model

import com.pourista.data.db.RecipeEntity
import com.pourista.data.db.RecipeStepEntity
import com.pourista.data.db.RecipeWithSteps

enum class StepKind {
    /**
     * The first pour: the coffee is wetted and allowed to open up. A kind of its own
     * rather than a pour with a name, because the bloom is always first, and the recipe
     * has to know whether it has one.
     */
    BLOOM,

    /** Pour water up to the cumulative target of the step. */
    POUR,

    /** Wait: steeping, a pause between pours. */
    WAIT,

    /** A circular movement of the cone. */
    SWIRL,

    /** Stirring. */
    STIR,

    /** The pouring is over, wait for the water to go through. */
    DRAWDOWN,

    /** Pressing the aeropress plunger. */
    PRESS;

    /** A step during which the person pours water. */
    val isPour: Boolean get() = this == POUR || this == BLOOM

    /**
     * Swirl and stir. They are done right after the pour, while the water still stands
     * above the coffee, not on the second the recipe put them on.
     */
    val isAgitation: Boolean get() = this == SWIRL || this == STIR

    /** Steps with a fixed place: the bloom only first, the drawdown only last. */
    val isPinned: Boolean get() = this == BLOOM || this == DRAWDOWN

    companion object {
        fun fromKey(key: String): StepKind =
            entries.firstOrNull { it.name == key } ?: WAIT

        /** The kinds an ordinary step can be set to. */
        val selectable: List<StepKind> get() = entries.filterNot { it.isPinned }
    }
}

data class RecipeStep(
    val id: Long = 0,
    val kind: StepKind,
    val title: String? = null,
    val startSec: Int,
    val durationSec: Int,
    /** How much water in total should be on the scale by the end of the step. */
    val targetWaterGrams: Float,
    /** Flow rate, g/s. Zero means unset, and the typical one is used. */
    val pourFlowRate: Float = 0f,
    val note: String? = null,
) {
    val endSec: Int get() = startSec + durationSec

    /** How many seconds the pour itself takes at the given rate. */
    fun pourSeconds(deltaGrams: Float): Float {
        if (deltaGrams <= 0f) return 0f
        val rate = pourFlowRate.takeIf { it > 0f } ?: DEFAULT_POUR_FLOW_RATE
        return (deltaGrams / rate).coerceAtLeast(MIN_POUR_SECONDS)
    }
}

/** Own recipes come after the built-in ones. */
const val USER_RECIPE_SORT_ORDER = 1000

/** The typical flow rate for when a recipe does not set one. */
const val DEFAULT_POUR_FLOW_RATE = 5f
private const val MIN_POUR_SECONDS = 2f

data class Recipe(
    val id: Long = 0,
    val name: String,
    val brewer: String,
    val doseGrams: Float,
    val waterGrams: Float,
    val waterTempC: Int,
    val grinderName: String? = null,
    val grindSetting: String? = null,
    /** Paper: "Hario", "Cafec Abaca". */
    val filterName: String? = null,
    val beanName: String? = null,
    val roaster: String? = null,
    val notes: String? = null,
    val isBuiltIn: Boolean = false,
    val isFavorite: Boolean = false,
    /** Arm auto-start right after the dose is recorded. */
    val autoStart: Boolean = true,
    /**
     * Aeropress mode. The press drops the weight: the water is forced into the cup and
     * the plunger leans on the scale unevenly. Normally that is smoothed away and taken
     * for the end of a brew; here it is the other way round — the weight is written as it
     * comes, auto-finish keeps quiet, and the chart shows when the pressing began.
     */
    val aeropressMode: Boolean = false,
    /** Position in the recipe list. */
    val sortOrder: Int = USER_RECIPE_SORT_ORDER,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val lastUsedAt: Long? = null,
    val steps: List<RecipeStep> = emptyList(),
) {
    val ratio: Float get() = if (doseGrams > 0f) waterGrams / doseGrams else 0f

    val totalSec: Int get() = steps.maxOfOrNull { it.endSec } ?: 0

    /** Water weight by the end of the last pour. */
    val finalTargetGrams: Float
        get() = steps.filter { it.kind.isPour }.maxOfOrNull { it.targetWaterGrams } ?: waterGrams

    val pourCount: Int get() = steps.count { it.kind.isPour }
}

/**
 * Recalculating a recipe for the dose actually ground, keeping the ratio.
 *
 * Grinding exactly 15.0 g rarely works out, and the ratio matters more than round
 * numbers. The water volume is rounded to [WATER_ROUNDING_GRAMS]: targets like "up to
 * 64.3 g" cannot be read while pouring. Returns a copy — the recipe in the database
 * stays as it is.
 */
fun Recipe.scaledToDose(actualDoseGrams: Float): Recipe {
    if (actualDoseGrams <= 0f || doseGrams <= 0f) return this
    val finalTarget = finalTargetGrams
    if (finalTarget <= 0f) return this

    val targetWater = roundToStep(actualDoseGrams * ratio)
    if (targetWater <= 0f) return this
    val factor = targetWater / finalTarget

    var previousTarget = 0f
    val scaledSteps = steps.map { step ->
        val target = if (step.kind.isPour) {
            // Rounding every target on its own must not break the order: a step cannot
            // ask for less than is already poured.
            maxOf(roundToStep(step.targetWaterGrams * factor), previousTarget)
        } else {
            previousTarget
        }
        previousTarget = target
        step.copy(targetWaterGrams = target)
    }

    return copy(
        doseGrams = actualDoseGrams,
        waterGrams = targetWater,
        steps = scaledSteps,
    )
}

private const val WATER_ROUNDING_GRAMS = 5f

private fun roundToStep(value: Float): Float =
    kotlin.math.round(value / WATER_ROUNDING_GRAMS) * WATER_ROUNDING_GRAMS

fun RecipeWithSteps.toDomain(): Recipe = Recipe(
    id = recipe.id,
    name = recipe.name,
    brewer = recipe.brewer,
    doseGrams = recipe.doseGrams,
    waterGrams = recipe.waterGrams,
    waterTempC = recipe.waterTempC,
    grinderName = recipe.grinderName,
    grindSetting = recipe.grindSetting,
    filterName = recipe.filterName,
    beanName = recipe.beanName,
    roaster = recipe.roaster,
    notes = recipe.notes,
    isBuiltIn = recipe.isBuiltIn,
    isFavorite = recipe.isFavorite,
    autoStart = recipe.autoStart,
    aeropressMode = recipe.aeropressMode,
    sortOrder = recipe.sortOrder,
    createdAt = recipe.createdAt,
    updatedAt = recipe.updatedAt,
    lastUsedAt = recipe.lastUsedAt,
    steps = steps.sortedBy { it.position }.map { it.toDomain() },
)

fun RecipeStepEntity.toDomain(): RecipeStep = RecipeStep(
    id = id,
    kind = StepKind.fromKey(kind),
    title = title,
    startSec = startSec,
    durationSec = durationSec,
    targetWaterGrams = targetWaterGrams,
    pourFlowRate = pourFlowRate,
    note = note,
)

fun Recipe.toEntity(now: Long): RecipeEntity = RecipeEntity(
    id = id,
    name = name,
    brewer = brewer,
    doseGrams = doseGrams,
    waterGrams = waterGrams,
    waterTempC = waterTempC,
    grinderName = grinderName,
    grindSetting = grindSetting,
    filterName = filterName,
    beanName = beanName,
    roaster = roaster,
    notes = notes,
    isBuiltIn = isBuiltIn,
    isFavorite = isFavorite,
    autoStart = autoStart,
    aeropressMode = aeropressMode,
    sortOrder = sortOrder,
    createdAt = if (createdAt == 0L) now else createdAt,
    updatedAt = now,
    lastUsedAt = lastUsedAt,
)

fun List<RecipeStep>.toEntities(recipeId: Long): List<RecipeStepEntity> =
    mapIndexed { index, step ->
        RecipeStepEntity(
            id = 0,
            recipeId = recipeId,
            position = index,
            kind = step.kind.name,
            title = step.title,
            startSec = step.startSec,
            durationSec = step.durationSec,
            targetWaterGrams = step.targetWaterGrams,
            pourFlowRate = step.pourFlowRate,
            note = step.note,
        )
    }
