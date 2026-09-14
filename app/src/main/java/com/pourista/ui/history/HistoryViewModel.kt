package com.pourista.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pourista.AppContainer
import com.pourista.brew.RecipeFromHistory
import com.pourista.data.model.BrewNotes
import com.pourista.data.model.BrewRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A record may have no temperature — we take the typical one for a pour-over. */
private const val DEFAULT_WATER_TEMP_C = 94

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * Brews with the recipe name for today. The record holds the name as it was at the time of the
     * brew: the recipe could have been renamed since, and a person will look for it in the history by
     * its present name. The name from the record stays as a fallback — for recipes that are gone.
     */
    val brews: StateFlow<List<BrewRecord>> = _query
        .flatMapLatest { query ->
            combine(
                container.brews.observeBrews(query),
                container.recipes.observeRecipes(),
            ) { records, recipes ->
                val names = recipes.associate { it.id to it.name }
                records.map { record ->
                    val current = record.recipeId?.let { names[it] }?.takeIf { it.isNotBlank() }
                    if (current == null) record else record.copy(recipeName = current)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    /**
     * A recipe from the recorded pour — the same breakdown as in the brew card. The editor picks
     * the finished draft up.
     */
    fun buildRecipe(record: BrewRecord, name: String): Boolean {
        val recipe = RecipeFromHistory.build(
            weightSeries = record.weightSeries,
            name = name,
            brewer = record.notes.brewer.orEmpty(),
            doseGrams = record.doseGrams,
            waterTempC = record.notes.waterTemp?.toIntOrNull() ?: DEFAULT_WATER_TEMP_C,
            elapsedMs = record.elapsedMs,
        ) ?: return false
        container.recipeDraft = recipe
        return true
    }

    fun delete(record: BrewRecord) {
        viewModelScope.launch { container.brews.deleteBrew(record.id) }
    }

    /**
     * Bring back a deleted one. The record is put down again, together with the charts and the
     * notes; its id will be different, but that is invisible to the person.
     */
    fun restore(record: BrewRecord) {
        viewModelScope.launch { container.brews.restoreAll(listOf(record)) }
    }
}

class BrewDetailViewModel(
    private val container: AppContainer,
    private val brewId: Long,
) : ViewModel() {

    val brew: StateFlow<BrewRecord?> = combine(
        container.brews.observeBrew(brewId),
        container.recipes.observeRecipes(),
    ) { record, recipes ->
        val current = record?.recipeId
            ?.let { id -> recipes.firstOrNull { it.id == id }?.name }
            ?.takeIf { it.isNotBlank() }
        if (record == null || current == null) record else record.copy(recipeName = current)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Assembles a recipe from the recorded pour and leaves it as a draft: the editor opens it from
     * there. Returns false when no pours can be seen in the record — brewed without a scale, nothing
     * to take apart.
     */
    fun buildRecipe(name: String): Boolean {
        val record = brew.value ?: return false
        val recipe = RecipeFromHistory.build(
            weightSeries = record.weightSeries,
            name = name,
            brewer = record.notes.brewer.orEmpty(),
            doseGrams = record.doseGrams,
            waterTempC = record.notes.waterTemp?.toIntOrNull() ?: DEFAULT_WATER_TEMP_C,
            elapsedMs = record.elapsedMs,
        ) ?: return false
        container.recipeDraft = recipe
        return true
    }

    fun saveNotes(notes: BrewNotes) {
        viewModelScope.launch { container.brews.updateNotes(brewId, notes) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            container.brews.deleteBrew(brewId)
            onDeleted()
        }
    }
}
