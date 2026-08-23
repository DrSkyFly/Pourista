package com.pourista.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pourista.AppContainer
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

    fun delete(record: BrewRecord) {
        viewModelScope.launch { container.brews.deleteBrew(record.id) }
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
