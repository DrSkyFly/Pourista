package com.pourista.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pourista.AppContainer
import com.pourista.data.model.BrewNotes
import com.pourista.data.model.BrewRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

    val brews: StateFlow<List<BrewRecord>> = _query
        .flatMapLatest { container.brews.observeBrews(it) }
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

    val brew: StateFlow<BrewRecord?> = container.brews.observeBrew(brewId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
