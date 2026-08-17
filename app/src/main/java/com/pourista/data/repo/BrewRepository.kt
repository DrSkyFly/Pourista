package com.pourista.data.repo

import com.pourista.data.db.BrewDao
import com.pourista.data.db.BrewEntity
import com.pourista.data.db.BrewNotesEntity
import com.pourista.data.model.BrewNotes
import com.pourista.data.model.BrewRecord
import com.pourista.data.model.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BrewRepository(private val dao: BrewDao) {

    fun observeBrews(query: String): Flow<List<BrewRecord>> {
        val source = if (query.isBlank()) {
            dao.observeBrews()
        } else {
            dao.searchBrews(query.trim().lowercase())
        }
        return source.map { rows -> rows.map { it.toDomain() } }
    }

    fun observeBrew(id: Long): Flow<BrewRecord?> =
        dao.observeBrew(id).map { it?.toDomain() }

    fun observeBeans(): Flow<List<String>> = dao.observeBeans()

    fun observeRoasters(): Flow<List<String>> = dao.observeRoasters()

    fun observeGrinders(): Flow<List<String>> = dao.observeGrinders()

    suspend fun saveBrew(
        brewedAt: Long,
        doseGrams: Float,
        weightGrams: Float,
        elapsedMs: Long,
        weightSeries: List<Float>,
        flowSeries: List<Float>,
        flowRateAvg: Float,
        recipeId: Long?,
        recipeName: String?,
        notes: BrewNotes,
    ): Long {
        val brewId = dao.insertBrew(
            BrewEntity(
                brewedAt = brewedAt,
                doseGrams = doseGrams,
                weightGrams = weightGrams,
                elapsedMs = elapsedMs,
                flowRateAvg = flowRateAvg,
                weightSeries = weightSeries.joinToString(";"),
                flowSeries = flowSeries.joinToString(";"),
                recipeId = recipeId,
                recipeName = recipeName,
            )
        )
        dao.insertNotes(notes.toEntity(brewId = brewId))
        return brewId
    }

    suspend fun updateNotes(brewId: Long, notes: BrewNotes) {
        val existing = dao.notesForBrew(brewId)
        if (existing == null) {
            dao.insertNotes(notes.toEntity(brewId))
        } else {
            dao.updateNotes(notes.toEntity(brewId).copy(id = existing.id))
        }
    }

    suspend fun deleteBrew(id: Long) = dao.deleteBrew(id)
}

private fun BrewNotes.toEntity(brewId: Long) = BrewNotesEntity(
    id = 0,
    brewId = brewId,
    bean = bean,
    roaster = roaster,
    grinder = grinder,
    grindSetting = grindSetting,
    brewer = brewer,
    waterTemp = waterTemp,
    note = extra,
)
