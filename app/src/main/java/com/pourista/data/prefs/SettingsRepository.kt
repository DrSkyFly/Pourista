package com.pourista.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pourista.brew.DEFAULT_NEAR_TARGET_GRAMS
import com.pourista.brew.DEFAULT_PACE_TOLERANCE
import com.pourista.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val keepScreenOn: Boolean = true,
    val soundCues: Boolean = true,
    val hapticCues: Boolean = true,
    val countdownCue: Boolean = true,
    /** За сколько граммов до цели шага подать сигнал. */
    val nearTargetGrams: Float = DEFAULT_NEAR_TARGET_GRAMS,
    /** Допустимое расхождение скорости пролива с рецептом, доля от целевой. */
    val paceTolerance: Float = DEFAULT_PACE_TOLERANCE,
    val autoConnectOnLaunch: Boolean = true,
    val stopTimerOnDisconnect: Boolean = true,
    val keepScaleInGrams: Boolean = true,
    val lastRecipeId: Long? = null,
    val presetsVersion: Int = 0,
    /** Язык, на котором лежат тексты встроенных рецептов в базе. */
    val presetsLocale: String = "",
    /**
     * Не пересчитывать воду под фактическую дозу: иногда кофе сыплют больше
     * специально, чтобы чашка вышла плотнее, а объём воды остаётся прежним.
     */
    val keepRecipeWater: Boolean = false,
    /** Встроенные рецепты, которые пользователь удалил: обратно их не сеем. */
    val deletedPresets: Set<String> = emptySet(),
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val soundCues = booleanPreferencesKey("sound_cues")
        val hapticCues = booleanPreferencesKey("haptic_cues")
        val countdownCue = booleanPreferencesKey("countdown_cue")
        val nearTargetGrams = floatPreferencesKey("near_target_grams")
        val paceTolerance = floatPreferencesKey("pace_tolerance")
        val autoConnect = booleanPreferencesKey("auto_connect")
        val stopTimerOnDisconnect = booleanPreferencesKey("stop_timer_on_disconnect")
        val keepScaleInGrams = booleanPreferencesKey("keep_scale_in_grams")
        val lastRecipeId = longPreferencesKey("last_recipe_id")
        val presetsVersion = intPreferencesKey("presets_version")
        val presetsLocale = stringPreferencesKey("presets_locale")
        val keepRecipeWater = booleanPreferencesKey("keep_recipe_water")
        val deletedPresets = stringSetPreferencesKey("deleted_presets")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.themeMode]?.let { value ->
                runCatching { ThemeMode.valueOf(value) }.getOrNull()
            } ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[Keys.dynamicColor] ?: true,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
            soundCues = prefs[Keys.soundCues] ?: true,
            hapticCues = prefs[Keys.hapticCues] ?: true,
            countdownCue = prefs[Keys.countdownCue] ?: true,
            nearTargetGrams = prefs[Keys.nearTargetGrams] ?: DEFAULT_NEAR_TARGET_GRAMS,
            paceTolerance = prefs[Keys.paceTolerance] ?: DEFAULT_PACE_TOLERANCE,
            autoConnectOnLaunch = prefs[Keys.autoConnect] ?: true,
            stopTimerOnDisconnect = prefs[Keys.stopTimerOnDisconnect] ?: true,
            keepScaleInGrams = prefs[Keys.keepScaleInGrams] ?: true,
            lastRecipeId = prefs[Keys.lastRecipeId]?.takeIf { it > 0 },
            presetsVersion = prefs[Keys.presetsVersion] ?: 0,
            presetsLocale = prefs[Keys.presetsLocale] ?: "",
            keepRecipeWater = prefs[Keys.keepRecipeWater] ?: false,
            deletedPresets = prefs[Keys.deletedPresets] ?: emptySet(),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.themeMode] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.dynamicColor] = enabled }

    suspend fun setKeepScreenOn(enabled: Boolean) = edit { it[Keys.keepScreenOn] = enabled }

    suspend fun setSoundCues(enabled: Boolean) = edit { it[Keys.soundCues] = enabled }

    suspend fun setHapticCues(enabled: Boolean) = edit { it[Keys.hapticCues] = enabled }

    suspend fun setCountdownCue(enabled: Boolean) = edit { it[Keys.countdownCue] = enabled }

    suspend fun setNearTargetGrams(grams: Float) = edit { it[Keys.nearTargetGrams] = grams }

    suspend fun setPaceTolerance(share: Float) = edit { it[Keys.paceTolerance] = share }

    suspend fun setAutoConnect(enabled: Boolean) = edit { it[Keys.autoConnect] = enabled }

    suspend fun setStopTimerOnDisconnect(enabled: Boolean) =
        edit { it[Keys.stopTimerOnDisconnect] = enabled }

    suspend fun setKeepScaleInGrams(enabled: Boolean) = edit { it[Keys.keepScaleInGrams] = enabled }

    suspend fun setLastRecipeId(id: Long?) = edit { prefs ->
        if (id == null) prefs.remove(Keys.lastRecipeId) else prefs[Keys.lastRecipeId] = id
    }

    suspend fun setPresetsVersion(version: Int) = edit { it[Keys.presetsVersion] = version }

    suspend fun setPresetsLocale(tag: String) = edit { it[Keys.presetsLocale] = tag }

    suspend fun setKeepRecipeWater(enabled: Boolean) = edit { it[Keys.keepRecipeWater] = enabled }

    /** Помечает встроенный рецепт удалённым, чтобы он не вернулся при обновлении набора. */
    suspend fun addDeletedPreset(name: String) = edit { prefs ->
        prefs[Keys.deletedPresets] = (prefs[Keys.deletedPresets] ?: emptySet()) + name
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { preferences -> block(preferences) }
    }
}
