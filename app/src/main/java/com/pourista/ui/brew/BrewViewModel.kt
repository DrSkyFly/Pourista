package com.pourista.ui.brew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pourista.AppContainer
import com.pourista.brew.BrewEvent
import com.pourista.brew.BrewPhase
import com.pourista.brew.BrewState
import com.pourista.brew.CooldownState
import com.pourista.data.model.Recipe
import com.pourista.data.prefs.AppSettings
import com.pourista.data.prefs.FortySixPreset
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
    val cooldown: StateFlow<CooldownState> = container.cooldown.state
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
            if (container.brewEngine.state.value.recipe != null) return@launch
            val saved = container.settings.current()
            // Сборку генератора в базе не ищем — её там нет. Собираем заново
            // по тем же ручкам: числа выйдут те же.
            if (saved.lastRecipeFortySix) {
                container.brewEngine.selectRecipe(container.fortySixRecipe(saved.fortySix))
            } else {
                saved.lastRecipeId?.let { id ->
                    container.recipes.recipeById(id)?.let(container.brewEngine::selectRecipe)
                }
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

    fun selectRecipe(recipe: Recipe?) = selectRecipe(recipe, fromGenerator = false)

    private fun selectRecipe(recipe: Recipe?, fromGenerator: Boolean) {
        // Законченное заваривание с выбором нового рецепта закрывается само:
        // держать на экране следы прошлой чашки незачем.
        if (brew.value.phase == BrewPhase.FINISHED) {
            container.brewEngine.reset()
        }
        container.brewEngine.selectRecipe(recipe)
        viewModelScope.launch {
            // У сборки генератора id нулевой: ни запоминать её по номеру, ни
            // отмечать использованной нечего.
            val id = recipe?.id?.takeIf { it > 0 }
            container.settings.setLastRecipe(id = id, fortySix = fromGenerator)
            id?.let { container.recipes.markUsed(it) }
        }
    }

    /** Сохранить ручки генератора под именем. Имя занято — пресет заменяется. */
    fun saveFortySixPreset(name: String, params: FortySixParams, lockRatio: Boolean) {
        viewModelScope.launch {
            container.settings.saveFortySixPreset(FortySixPreset(name, params, lockRatio))
        }
    }

    fun deleteFortySixPreset(name: String) {
        viewModelScope.launch { container.settings.deleteFortySixPreset(name) }
    }

    /**
     * Пересобирает рецепт 4:6 по новым настройкам и сразу берёт его в работу:
     * генератор открывают, когда собираются заваривать, а не про запас.
     *
     * В список рецептов сборка не попадает. Ручки запоминаются — этого хватает,
     * чтобы повторить, — а свои наборы у генератора хранятся пресетами.
     */
    fun generateFortySix(params: FortySixParams, lockRatio: Boolean) {
        selectRecipe(container.fortySixRecipe(params), fromGenerator = true)
        viewModelScope.launch {
            container.settings.setFortySix(params)
            container.settings.setFortySixLockRatio(lockRatio)
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

    /**
     * Таймер остывания: сколько ждать и заводить ли его самому по окончании
     * заваривания. Настройка глобальная — привычка пить не обжигаясь
     * не меняется от чашки к чашке.
     */
    fun setCooldownSeconds(seconds: Int) {
        viewModelScope.launch { container.settings.setCooldownSeconds(seconds) }
    }

    fun setCooldownAutoStart(enabled: Boolean) {
        viewModelScope.launch { container.settings.setCooldownAutoStart(enabled) }
    }

    /** Завести таймер прямо сейчас, на выставленное в листе время. */
    fun startCooldown() = container.startCooldown(settings.value.cooldownSeconds)

    fun stopCooldown() = container.cooldown.stop()

    /** Пересчёт помола запоминает обе кофемолки и настройку: окно закрывается,
     *  а искать свою модель в списке заново не хочется. */
    fun rememberGrindPair(fromId: String, toId: String, setting: String) {
        viewModelScope.launch {
            container.settings.setGrindPair(fromId, toId, setting)
        }
    }

    fun clearSaved() {
        _saved.value = false
    }
}
