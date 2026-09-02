package com.pourista.data.model

import com.pourista.data.db.BrewNotesEntity
import com.pourista.data.db.BrewWithNotes

data class BrewNotes(
    val bean: String? = null,
    val roaster: String? = null,
    val grinder: String? = null,
    val grindSetting: String? = null,
    /** Бумага: «Hario», «Cafec Abaca». */
    val filterName: String? = null,
    val brewer: String? = null,
    val waterTemp: String? = null,
    val extra: String? = null,
) {
    val isEmpty: Boolean
        get() = listOf(bean, roaster, grinder, grindSetting, filterName, brewer, waterTemp, extra)
            .all { it.isNullOrBlank() }
}

/**
 * Заваривание из истории. Хранится только измеренное; время и пропорция —
 * это его вид на экране, а не отдельные данные, поэтому считаются при показе.
 */
data class BrewRecord(
    val id: Long,
    val brewedAt: Long,
    val doseGrams: Float,
    val weightGrams: Float,
    val elapsedMs: Long,
    val weightSeries: List<Float>,
    val flowSeries: List<Float>,
    val flowRateAvg: Float,
    val recipeId: Long?,
    val recipeName: String?,
    val notes: BrewNotes,
)

private fun String.toSeries(): List<Float> =
    split(';').mapNotNull { it.trim().toFloatOrNull() }

fun BrewWithNotes.toDomain(): BrewRecord = BrewRecord(
    id = brew.id,
    brewedAt = brew.brewedAt,
    doseGrams = brew.doseGrams,
    weightGrams = brew.weightGrams,
    elapsedMs = brew.elapsedMs,
    weightSeries = brew.weightSeries.toSeries(),
    flowSeries = brew.flowSeries.toSeries(),
    flowRateAvg = brew.flowRateAvg,
    recipeId = brew.recipeId,
    recipeName = brew.recipeName,
    notes = notes?.toDomain() ?: BrewNotes(),
)

fun BrewNotesEntity.toDomain(): BrewNotes = BrewNotes(
    bean = bean,
    roaster = roaster,
    grinder = grinder,
    grindSetting = grindSetting,
    filterName = filterName,
    brewer = brewer,
    waterTemp = waterTemp,
    extra = note,
)
