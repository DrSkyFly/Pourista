package com.pourista.ui.settings

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pourista.AppContainer
import com.pourista.R
import com.pourista.data.io.BackupJson
import com.pourista.data.prefs.AppSettings
import com.pourista.ui.theme.AppPalette
import com.pourista.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Чем закончились экспорт или восстановление: экран показывает это тостом. */
data class BackupMessage(
    @StringRes val textRes: Int,
    val recipes: Int = 0,
    val brews: Int = 0,
)

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
    fun setAutoFinish(value: Boolean) = update { container.settings.setAutoFinish(value) }

    /** Отказ от весов рвёт и текущую связь: иначе значок останется висеть. */
    fun setUseScale(value: Boolean) = update {
        container.settings.setUseScale(value)
        if (!value) container.scale.disconnect()
    }

    fun setAutoConnect(value: Boolean) = update { container.settings.setAutoConnect(value) }
    fun setStopTimerOnDisconnect(value: Boolean) =
        update { container.settings.setStopTimerOnDisconnect(value) }

    fun setKeepScaleInGrams(value: Boolean) =
        update { container.settings.setKeepScaleInGrams(value) }

    private val _backupMessage = MutableStateFlow<BackupMessage?>(null)
    val backupMessage: StateFlow<BackupMessage?> = _backupMessage.asStateFlow()

    fun clearBackupMessage() {
        _backupMessage.value = null
    }

    /** Файл выбирает система, приложению хватает одного uri на запись. */
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val recipes = container.recipes.exportAll()
            val brews = container.brews.exportAll()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val text = BackupJson.encode(recipes, brews, System.currentTimeMillis())
                    container.appContext.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(text.toByteArray())
                    } ?: error("Не удалось открыть файл")
                }.isSuccess
            }
            _backupMessage.value = if (ok) {
                BackupMessage(R.string.backup_saved, recipes.size, brews.size)
            } else {
                BackupMessage(R.string.backup_save_failed)
            }
        }
    }

    /**
     * Восстановление добавляет, а не заменяет: рецепт с таким же названием и
     * заваривание с тем же временем пропускаются. Так копию можно залить
     * поверх живой базы и не получить каждой чашки по два раза.
     */
    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val backup = withContext(Dispatchers.IO) {
                runCatching {
                    val text = container.appContext.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().decodeToString() }
                        ?: error("Не удалось открыть файл")
                    BackupJson.decode(text)
                }.getOrNull()
            }
            if (backup == null) {
                _backupMessage.value = BackupMessage(R.string.backup_restore_failed)
                return@launch
            }
            _backupMessage.value = BackupMessage(
                textRes = R.string.backup_restored,
                recipes = container.recipes.restoreAll(backup.recipes),
                brews = container.brews.restoreAll(backup.brews),
            )
        }
    }
}
