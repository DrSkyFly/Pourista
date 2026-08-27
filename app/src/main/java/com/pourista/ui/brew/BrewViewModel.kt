package com.pourista.ui.brew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pourista.AppContainer
import com.pourista.brew.BrewEvent
import com.pourista.brew.BrewPhase
import com.pourista.brew.BrewState
import com.pourista.data.model.Recipe
import com.pourista.data.prefs.AppSettings
import com.pourista.data.presets.FortySixParams
import com.pourista.scale.ScaleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BrewViewModel(private val container: AppContainer) : ViewModel() {

    val brew: StateFlow<BrewState> = container.brewEngine.state
    val scale: StateFlow<ScaleState> = container.scale.state
    val settings: StateFlow<AppSettings> = container.settingsState
    val events: SharedFlow<BrewEvent> = container.brewEngine.events

    val recipes: StateFlow<List<Recipe>> = container.recipes.observeRecipes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _saved = MutableStateFlow(false)

    /** Заваривание только что легло в историю — экрану есть о чём сказать. */
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        viewModelScope.launch {
            val lastId = container.settings.current().lastRecipeId
            if (lastId != null && container.brewEngine.state.value.recipe == null) {
                container.recipes.recipeById(lastId)?.let(container.brewEngine::selectRecipe)
            }
        }
        // Сохраняет заваривание контейнер: финиш бывает и автоматическим, когда
        // экрана уже нет. Экрану остаётся показать, что запись случилась.
        viewModelScope.launch {
            container.brewSaved.collect { _saved.value = true }
        }
    }

    fun hasScalePermissions(): Boolean = container.scale.hasPermissions()

    fun connect() = container.scale.startScan()

    fun disconnect() = container.scale.disconnect()

    fun toggleConnection() {
        val state = scale.value
        if (state.isConnected || state.isBusy) container.scale.disconnect() else container.scale.startScan()
    }

    fun toggleTimer() = container.brewEngine.toggleRunning()

    fun tare() = container.brewEngine.tare()

    fun captureDose() = container.brewEngine.captureDose()

    fun setDose(grams: Float) = container.brewEngine.setDose(grams)

    fun reset() = container.brewEngine.reset()

    fun startRecording() = container.brewEngine.startRecording()

    fun cancelRecording() = container.brewEngine.cancelRecording()

    /** Запись закончена, черновик рецепта готов — экран должен открыть редактор. */
    val draftReady: StateFlow<Boolean> = container.draftReady

    fun clearDraftReady() = container.clearDraftReady()

    /**
     * Останавливает заваривание. В историю оно ляжет само — так же, как когда
     * финиш определяется по снятой с весов чашке.
     */
    fun finish() = container.brewEngine.finish()

    fun selectRecipe(recipe: Recipe?) {
        // Законченное заваривание с выбором нового рецепта закрывается само:
        // держать на экране следы прошлой чашки незачем.
        if (brew.value.phase == BrewPhase.FINISHED) {
            container.brewEngine.reset()
        }
        container.brewEngine.selectRecipe(recipe)
        viewModelScope.launch {
            container.settings.setLastRecipeId(recipe?.id)
            recipe?.let { container.recipes.markUsed(it.id) }
        }
    }

    /**
     * Пересобирает рецепт 4:6 по новым настройкам и сразу берёт его в работу:
     * генератор открывают, когда собираются заваривать, а не про запас.
     */
    fun generateFortySix(params: FortySixParams, lockRatio: Boolean) {
        viewModelScope.launch {
            container.settings.setFortySixLockRatio(lockRatio)
            val recipe = container.buildFortySixRecipe(params) ?: return@launch
            selectRecipe(recipe)
        }
    }

    /**
     * Взвести или снять автостарт для текущего заваривания. Настройку рецепта не
     * трогает: она лишь решает, взводиться ли самому после записи дозы.
     */
    fun toggleAutoStart() {
        container.brewEngine.setAutoStartArmed(!brew.value.autoStartArmed)
    }

    /**
     * Оставлять объём воды как в рецепте. Настройка глобальная и запоминается:
     * это привычка, а не решение на одну чашку.
     */
    fun toggleKeepRecipeWater() {
        viewModelScope.launch {
            container.settings.setKeepRecipeWater(!settings.value.keepRecipeWater)
        }
    }

    fun clearSaved() {
        _saved.value = false
    }
}
