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
import com.pourista.data.presets.FortySixParams
import com.pourista.data.presets.FortySixStrength
import com.pourista.data.presets.FortySixTaste
import com.pourista.ui.theme.AppPalette
import com.pourista.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val palette: AppPalette = AppPalette.DYNAMIC,
    val keepScreenOn: Boolean = true,
    val soundCues: Boolean = true,
    val hapticCues: Boolean = true,
    val countdownCue: Boolean = true,
    /** За сколько граммов до цели шага подать сигнал. */
    val nearTargetGrams: Float = DEFAULT_NEAR_TARGET_GRAMS,
    /** Допустимое расхождение скорости пролива с рецептом, доля от целевой. */
    val paceTolerance: Float = DEFAULT_PACE_TOLERANCE,
    /**
     * Заканчивать заваривание самому, когда с весов сняли воронку или чашку.
     * Кому мешает — выключает и жмёт «Финиш» руками.
     */
    val autoFinish: Boolean = true,
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
    /** Последние настройки генератора 4:6 — чтобы заварить так же. */
    val fortySix: FortySixParams = FortySixParams(),
    /** Рецепт, в который пишет генератор 4:6: он один и переписывается. */
    val fortySixRecipeId: Long? = null,
    /** Версия, для которой уже показали «Что нового». */
    val whatsNewSeenVersion: Int = 0,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val palette = stringPreferencesKey("palette")
        /** Прежний вид настройки: до появления палитр обои включались флажком. */
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val soundCues = booleanPreferencesKey("sound_cues")
        val hapticCues = booleanPreferencesKey("haptic_cues")
        val countdownCue = booleanPreferencesKey("countdown_cue")
        val nearTargetGrams = floatPreferencesKey("near_target_grams")
        val paceTolerance = floatPreferencesKey("pace_tolerance")
        val autoFinish = booleanPreferencesKey("auto_finish")
        val autoConnect = booleanPreferencesKey("auto_connect")
        val stopTimerOnDisconnect = booleanPreferencesKey("stop_timer_on_disconnect")
        val keepScaleInGrams = booleanPreferencesKey("keep_scale_in_grams")
        val lastRecipeId = longPreferencesKey("last_recipe_id")
        val presetsVersion = intPreferencesKey("presets_version")
        val presetsLocale = stringPreferencesKey("presets_locale")
        val keepRecipeWater = booleanPreferencesKey("keep_recipe_water")
        val deletedPresets = stringSetPreferencesKey("deleted_presets")
        val fortySixDose = floatPreferencesKey("forty_six_dose")
        val fortySixRatio = floatPreferencesKey("forty_six_ratio")
        val fortySixTaste = stringPreferencesKey("forty_six_taste")
        val fortySixStrength = stringPreferencesKey("forty_six_strength")
        val fortySixRecipeId = longPreferencesKey("forty_six_recipe_id")
        val whatsNewSeen = intPreferencesKey("whats_new_seen")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.themeMode]?.let { value ->
                runCatching { ThemeMode.valueOf(value) }.getOrNull()
            } ?: ThemeMode.SYSTEM,
            palette = prefs[Keys.palette]?.let { value ->
                runCatching { AppPalette.valueOf(value) }.getOrNull()
            } ?: if (prefs[Keys.dynamicColor] == false) AppPalette.COPPER else AppPalette.DYNAMIC,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
            soundCues = prefs[Keys.soundCues] ?: true,
            hapticCues = prefs[Keys.hapticCues] ?: true,
            countdownCue = prefs[Keys.countdownCue] ?: true,
            nearTargetGrams = prefs[Keys.nearTargetGrams] ?: DEFAULT_NEAR_TARGET_GRAMS,
            paceTolerance = prefs[Keys.paceTolerance] ?: DEFAULT_PACE_TOLERANCE,
            autoFinish = prefs[Keys.autoFinish] ?: true,
            autoConnectOnLaunch = prefs[Keys.autoConnect] ?: true,
            stopTimerOnDisconnect = prefs[Keys.stopTimerOnDisconnect] ?: true,
            keepScaleInGrams = prefs[Keys.keepScaleInGrams] ?: true,
            lastRecipeId = prefs[Keys.lastRecipeId]?.takeIf { it > 0 },
            presetsVersion = prefs[Keys.presetsVersion] ?: 0,
            presetsLocale = prefs[Keys.presetsLocale] ?: "",
            keepRecipeWater = prefs[Keys.keepRecipeWater] ?: false,
            deletedPresets = prefs[Keys.deletedPresets] ?: emptySet(),
            fortySix = FortySixParams(
                doseGrams = prefs[Keys.fortySixDose] ?: FortySixParams().doseGrams,
                ratio = prefs[Keys.fortySixRatio] ?: FortySixParams().ratio,
                taste = prefs[Keys.fortySixTaste]?.let { value ->
                    runCatching { FortySixTaste.valueOf(value) }.getOrNull()
                } ?: FortySixParams().taste,
                strength = prefs[Keys.fortySixStrength]?.let { value ->
                    runCatching { FortySixStrength.valueOf(value) }.getOrNull()
                } ?: FortySixParams().strength,
            ),
            fortySixRecipeId = prefs[Keys.fortySixRecipeId]?.takeIf { it > 0 },
            whatsNewSeenVersion = prefs[Keys.whatsNewSeen] ?: 0,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.themeMode] = mode.name }

    suspend fun setPalette(palette: AppPalette) = edit { it[Keys.palette] = palette.name }

    suspend fun setKeepScreenOn(enabled: Boolean) = edit { it[Keys.keepScreenOn] = enabled }

    suspend fun setSoundCues(enabled: Boolean) = edit { it[Keys.soundCues] = enabled }

    suspend fun setHapticCues(enabled: Boolean) = edit { it[Keys.hapticCues] = enabled }

    suspend fun setCountdownCue(enabled: Boolean) = edit { it[Keys.countdownCue] = enabled }

    suspend fun setNearTargetGrams(grams: Float) = edit { it[Keys.nearTargetGrams] = grams }

    suspend fun setPaceTolerance(share: Float) = edit { it[Keys.paceTolerance] = share }

    suspend fun setAutoFinish(enabled: Boolean) = edit { it[Keys.autoFinish] = enabled }

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

    /** Запоминает ручки генератора 4:6: тайминги в него зашиты и не меняются. */
    suspend fun setFortySix(params: FortySixParams) = edit { prefs ->
        prefs[Keys.fortySixDose] = params.doseGrams
        prefs[Keys.fortySixRatio] = params.ratio
        prefs[Keys.fortySixTaste] = params.taste.name
        prefs[Keys.fortySixStrength] = params.strength.name
    }

    suspend fun setWhatsNewSeenVersion(versionCode: Int) =
        edit { it[Keys.whatsNewSeen] = versionCode }

    suspend fun setFortySixRecipeId(id: Long?) = edit { prefs ->
        if (id == null) prefs.remove(Keys.fortySixRecipeId) else prefs[Keys.fortySixRecipeId] = id
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { preferences -> block(preferences) }
    }
}
