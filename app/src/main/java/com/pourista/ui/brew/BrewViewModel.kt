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

    /** A brew has just gone into the history — the screen has something to say. */
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        viewModelScope.launch {
            if (container.brewEngine.state.value.recipe != null) return@launch
            val saved = container.settings.current()
            // We do not look for a generator build in the database — it is not there. It is
            // assembled again from the same dials: the numbers come out the same.
            if (saved.lastRecipeFortySix) {
                container.brewEngine.selectRecipe(container.fortySixRecipe(saved.fortySix))
            } else {
                saved.lastRecipeId?.let { id ->
                    container.recipes.recipeById(id)?.let(container.brewEngine::selectRecipe)
                }
            }
        }
        // The brew is saved by the container: the finish also happens automatically, when the
        // screen is already gone. What is left to the screen is to show that the record happened.
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

    /** The recording is over and the recipe draft is ready — the screen should open the editor. */
    val draftReady: StateFlow<Boolean> = container.draftReady

    fun clearDraftReady() = container.clearDraftReady()

    /**
     * Stops the brew. It goes into the history by itself — the same way as when the finish is
     * detected by the cup being lifted off the scale.
     */
    fun finish() = container.brewEngine.finish()

    fun selectRecipe(recipe: Recipe?) = selectRecipe(recipe, fromGenerator = false)

    private fun selectRecipe(recipe: Recipe?, fromGenerator: Boolean) {
        // A finished brew closes by itself when a new recipe is picked: there is no point keeping
        // traces of the previous cup on the screen.
        if (brew.value.phase == BrewPhase.FINISHED) {
            container.brewEngine.reset()
        }
        container.brewEngine.selectRecipe(recipe)
        viewModelScope.launch {
            // A generator build has a zero id: there is nothing to remember it by and nothing to
            // mark as used.
            val id = recipe?.id?.takeIf { it > 0 }
            container.settings.setLastRecipe(id = id, fortySix = fromGenerator)
            id?.let { container.recipes.markUsed(it) }
        }
    }

    /** Save the generator dials under a name. If the name is taken, the preset is replaced. */
    fun saveFortySixPreset(name: String, params: FortySixParams, lockRatio: Boolean) {
        viewModelScope.launch {
            container.settings.saveFortySixPreset(FortySixPreset(name, params, lockRatio))
        }
    }

    fun deleteFortySixPreset(name: String) {
        viewModelScope.launch { container.settings.deleteFortySixPreset(name) }
    }

    /**
     * Rebuilds the 4:6 recipe from the new settings and takes it into work at once: the generator
     * is opened when one is about to brew, not for later.
     *
     * The build does not go into the recipe list. The dials are remembered — that is enough to
     * repeat it — and one's own sets are kept in the generator as presets.
     */
    fun generateFortySix(params: FortySixParams, lockRatio: Boolean) {
        selectRecipe(container.fortySixRecipe(params), fromGenerator = true)
        viewModelScope.launch {
            container.settings.setFortySix(params)
            container.settings.setFortySixLockRatio(lockRatio)
        }
    }

    /**
     * Arm or disarm auto-start for the current brew. It does not touch the recipe setting: that
     * only decides whether to arm by itself after the dose is recorded.
     */
    fun toggleAutoStart() {
        container.brewEngine.setAutoStartArmed(!brew.value.autoStartArmed)
    }

    /**
     * Keep the water as the recipe wrote it. The setting is global and remembered: this is a
     * habit rather than a decision for one cup.
     */
    fun toggleKeepRecipeWater() {
        viewModelScope.launch {
            container.settings.setKeepRecipeWater(!settings.value.keepRecipeWater)
        }
    }

    /**
     * The cooldown timer: how long to wait and whether to wind it by itself when the brew is over.
     * The setting is global — the habit of drinking without scalding does not change from cup to
     * cup.
     */
    fun setCooldownSeconds(seconds: Int) {
        viewModelScope.launch { container.settings.setCooldownSeconds(seconds) }
    }

    fun setCooldownAutoStart(enabled: Boolean) {
        viewModelScope.launch { container.settings.setCooldownAutoStart(enabled) }
    }

    /** Wind the timer right now, for the time set in the sheet. */
    fun startCooldown() = container.startCooldown(settings.value.cooldownSeconds)

    fun stopCooldown() = container.cooldown.stop()

    /** The grind conversion remembers both grinders and the setting: the sheet closes, and nobody
     *  wants to look their own model up in the list again. */
    fun rememberGrindPair(fromId: String, toId: String, setting: String) {
        viewModelScope.launch {
            container.settings.setGrindPair(fromId, toId, setting)
        }
    }

    fun clearSaved() {
        _saved.value = false
    }
}
