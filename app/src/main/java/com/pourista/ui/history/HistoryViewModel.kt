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

/** Температуры в записи может не быть — берём типовую для пуровера. */
private const val DEFAULT_WATER_TEMP_C = 94

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * Заваривания с именем рецепта на сегодня. В записи лежит имя на момент
     * заваривания: рецепт с тех пор могли переименовать, а искать его в истории
     * человек будет по нынешнему названию. Имя из записи остаётся запасным —
     * для рецептов, которых уже нет.
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
     * Рецепт по записанному проливу — тот же разбор, что и в карточке
     * заваривания. Готовый черновик подхватывает редактор.
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
     * Вернуть удалённое. Запись кладётся заново, вместе с графиками и
     * заметками; id у неё будет другой, но человеку он не виден.
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
     * Собирает рецепт по записанному проливу и оставляет его черновиком:
     * дальше его открывает редактор. Возвращает false, когда проливов в записи
     * не видно — заваривали без весов, разбирать нечего.
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
