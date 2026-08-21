package com.pourista.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pourista.AppContainer
import com.pourista.data.prefs.AppSettings
import com.pourista.ui.theme.AppPalette
import com.pourista.ui.theme.ThemeMode
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settingsState

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun setThemeMode(mode: ThemeMode) = update { container.settings.setThemeMode(mode) }

    fun setPalette(palette: AppPalette) = update { container.settings.setPalette(palette) }


    fun setKeepScreenOn(value: Boolean) = update { container.settings.setKeepScreenOn(value) }
    fun setSoundCues(value: Boolean) = update { container.settings.setSoundCues(value) }
    fun setHapticCues(value: Boolean) = update { container.settings.setHapticCues(value) }
    fun setCountdownCue(value: Boolean) = update { container.settings.setCountdownCue(value) }
    fun setNearTargetGrams(value: Float) = update { container.settings.setNearTargetGrams(value) }

    fun setPaceTolerance(value: Float) = update { container.settings.setPaceTolerance(value) }
    fun setAutoConnect(value: Boolean) = update { container.settings.setAutoConnect(value) }
    fun setStopTimerOnDisconnect(value: Boolean) =
        update { container.settings.setStopTimerOnDisconnect(value) }

    fun setKeepScaleInGrams(value: Boolean) =
        update { container.settings.setKeepScaleInGrams(value) }
}
