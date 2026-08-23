package com.pourista.data.model

import com.pourista.data.db.RecipeEntity
import com.pourista.data.db.RecipeStepEntity
import com.pourista.data.db.RecipeWithSteps

enum class StepKind {
    /**
     * Первый влив: смачиваем кофе и даём ему раскрыться. Отдельный вид, а не
     * просто пролив с названием, потому что место у блуминга всегда первое,
     * и рецепт должен знать, есть он в нём или нет.
     */
    BLOOM,

    /** Наливаем воду до накопительной цели шага. */
    POUR,

    /** Ждём: настаивание, пауза между проливами. */
    WAIT,

    /** Круговое движение воронкой. */
    SWIRL,

    /** Размешивание. */
    STIR,

    /** Пролив закончен, ждём, пока вода уйдёт. */
    DRAWDOWN,

    /** Отжим поршня в аэропрессе. */
    PRESS;

    /** Шаг, во время которого пользователь льёт воду. */
    val isPour: Boolean get() = this == POUR || this == BLOOM

    /** Шаги с закреплённым местом: блуминг только первый, слив только последний. */
    val isPinned: Boolean get() = this == BLOOM || this == DRAWDOWN

    companion object {
        fun fromKey(key: String): StepKind =
            entries.firstOrNull { it.name == key } ?: WAIT

        /** Виды, которые можно выбрать у обычного шага. */
        val selectable: List<StepKind> get() = entries.filterNot { it.isPinned }
    }
}

data class RecipeStep(
    val id: Long = 0,
    val kind: StepKind,
    val title: String? = null,
    val startSec: Int,
    val durationSec: Int,
    /** Сколько всего воды должно быть на весах к концу шага. */
    val targetWaterGrams: Float,
    /** Скорость влива, г/с. Ноль — не задана, берётся типовая. */
    val pourFlowRate: Float = 0f,
    val note: String? = null,
) {
    val endSec: Int get() = startSec + durationSec

    /** Сколько секунд занимает сам влив при заданной скорости. */
    fun pourSeconds(deltaGrams: Float): Float {
        if (deltaGrams <= 0f) return 0f
        val rate = pourFlowRate.takeIf { it > 0f } ?: DEFAULT_POUR_FLOW_RATE
        return (deltaGrams / rate).coerceAtLeast(MIN_POUR_SECONDS)
    }
}

/** Свои рецепты идут после встроенных. */
const val USER_RECIPE_SORT_ORDER = 1000

/** Типовая скорость влива, когда рецепт её не задаёт. */
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
    val beanName: String? = null,
    val roaster: String? = null,
    val notes: String? = null,
    val isBuiltIn: Boolean = false,
    val isFavorite: Boolean = false,
    /** Взводить автостарт сразу после записи дозы. */
    val autoStart: Boolean = true,
    /**
     * Режим аэропресса. Отжим роняет вес: воду продавливают в чашку, а поршень
     * давит на весы неравномерно. Обычно такое сглаживают и считают концом
     * заваривания, здесь — наоборот: вес пишется как есть, автофиниш молчит,
     * и на графике видно, когда начался отжим.
     */
    val aeropressMode: Boolean = false,
    /** Порядок в списке рецептов. */
    val sortOrder: Int = USER_RECIPE_SORT_ORDER,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val lastUsedAt: Long? = null,
    val steps: List<RecipeStep> = emptyList(),
) {
    val ratio: Float get() = if (doseGrams > 0f) waterGrams / doseGrams else 0f

    val totalSec: Int get() = steps.maxOfOrNull { it.endSec } ?: 0

    /** Вес воды к концу последнего пролива. */
    val finalTargetGrams: Float
        get() = steps.filter { it.kind.isPour }.maxOfOrNull { it.targetWaterGrams } ?: waterGrams

    val pourCount: Int get() = steps.count { it.kind.isPour }
}

/**
 * Пересчёт рецепта под фактически насыпанную дозу с сохранением пропорции.
 *
 * Намолоть ровно 15,0 г получается редко, а пропорция важнее круглых чисел.
 * Объём воды округляется до [WATER_ROUNDING_GRAMS]: цели вроде «до 64,3 г»
 * читать во время пролива невозможно. Возвращает копию — рецепт в базе не меняется.
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
            // Округление каждой цели по отдельности не должно ломать порядок:
            // шаг не может требовать меньше, чем уже налито.
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
