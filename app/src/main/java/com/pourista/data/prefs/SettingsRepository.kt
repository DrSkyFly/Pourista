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
import com.pourista.brew.DEFAULT_COOLDOWN_SECONDS
import com.pourista.brew.DEFAULT_PACE_TOLERANCE
import com.pourista.brew.FlowSmoothing
import com.pourista.data.presets.FortySixParams
import com.pourista.data.presets.FortySixStrength
import com.pourista.data.presets.FortySixTaste
import com.pourista.ui.theme.AppPalette
import com.pourista.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saved settings of the 4:6 generator: dose, ratio and both dials under a name of their
 * own. A preset has no timings — in the 4:6 method they never change.
 */
data class FortySixPreset(
    val name: String,
    val params: FortySixParams,
    val lockRatio: Boolean,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val palette: AppPalette = AppPalette.DYNAMIC,
    val keepScreenOn: Boolean = true,
    val soundCues: Boolean = true,
    val hapticCues: Boolean = true,
    val countdownCue: Boolean = true,
    /** How many grams before the step target to give a cue. */
    val nearTargetGrams: Float = DEFAULT_NEAR_TARGET_GRAMS,
    /** Allowed drift of the flow rate from the recipe, as a share of the target. */
    val paceTolerance: Float = DEFAULT_PACE_TOLERANCE,
    /** How much to smooth the flow rate shown on screen. */
    val flowSmoothing: FlowSmoothing = FlowSmoothing.NORMAL,
    /**
     * End the brew by itself once the cone or the cup is lifted off the scale. Whoever
     * finds it in the way turns it off and presses "Finish" by hand.
     */
    val autoFinish: Boolean = true,
    /** The question about the scale has been answered — we do not ask twice. */
    val scaleAsked: Boolean = false,
    /**
     * Whether we work with a scale at all. Off, and the app becomes a timer: it does not
     * look for a scale, does not ask for Bluetooth and does not show the icon.
     */
    val useScale: Boolean = true,
    val autoConnectOnLaunch: Boolean = true,
    val stopTimerOnDisconnect: Boolean = true,
    val keepScaleInGrams: Boolean = true,
    val lastRecipeId: Long? = null,
    /**
     * The last thing picked was not a recipe from the list but a 4:6 generator build. The
     * database has none, and on the next start it is built again from [fortySix].
     */
    val lastRecipeFortySix: Boolean = false,
    val presetsVersion: Int = 0,
    /** The language the texts of the built-in recipes lie in inside the database. */
    val presetsLocale: String = "",
    /**
     * Do not recalculate the water for the actual dose: sometimes more coffee is ground on
     * purpose, for a denser cup, while the water volume stays the same.
     */
    val keepRecipeWater: Boolean = false,
    /** Built-in recipes the user has deleted: we do not seed them back. */
    val deletedPresets: Set<String> = emptySet(),
    /** The last settings of the 4:6 generator — to brew the same way again. */
    val fortySix: FortySixParams = FortySixParams(),
    /** The ratio in the 4:6 generator is pinned: the water is dialled, the dose is counted. */
    val fortySixLockRatio: Boolean = false,
    /** Saved generator settings, by name. */
    val fortySixPresets: List<FortySixPreset> = emptyList(),
    /** The version "What is new" has already been shown for. */
    val whatsNewSeenVersion: Int = 0,
    /** The grinder the grind was converted from last time. */
    val grindFromId: String = "",
    /** Your own grinder: the one we convert to. */
    val grindToId: String = "",
    /** The last setting entered into the grind conversion. */
    val grindSetting: String = "",
    /** How long the coffee cools after a brew, in seconds. */
    val cooldownSeconds: Int = DEFAULT_COOLDOWN_SECONDS,
    /** Wind the cooldown timer by itself as soon as the brew is over. */
    val cooldownAutoStart: Boolean = false,
) {
    /**
     * Time to ask about the scale: there has been no answer yet, and the app has never been
     * started. Those who updated are not asked — everything is already set up for them.
     */
    val needScaleQuestion: Boolean get() = !scaleAsked && whatsNewSeenVersion == 0
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val palette = stringPreferencesKey("palette")
        /** The old shape of the setting: before the palettes the wallpaper was a checkbox. */
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val soundCues = booleanPreferencesKey("sound_cues")
        val hapticCues = booleanPreferencesKey("haptic_cues")
        val countdownCue = booleanPreferencesKey("countdown_cue")
        val nearTargetGrams = floatPreferencesKey("near_target_grams")
        val paceTolerance = floatPreferencesKey("pace_tolerance")
        val flowSmoothing = stringPreferencesKey("flow_smoothing")
        val autoFinish = booleanPreferencesKey("auto_finish")
        val scaleAsked = booleanPreferencesKey("scale_asked")
        val useScale = booleanPreferencesKey("use_scale")
        val autoConnect = booleanPreferencesKey("auto_connect")
        val stopTimerOnDisconnect = booleanPreferencesKey("stop_timer_on_disconnect")
        val keepScaleInGrams = booleanPreferencesKey("keep_scale_in_grams")
        val lastRecipeId = longPreferencesKey("last_recipe_id")
        val lastRecipeFortySix = booleanPreferencesKey("last_recipe_forty_six")
        val presetsVersion = intPreferencesKey("presets_version")
        val presetsLocale = stringPreferencesKey("presets_locale")
        val keepRecipeWater = booleanPreferencesKey("keep_recipe_water")
        val deletedPresets = stringSetPreferencesKey("deleted_presets")
        val fortySixDose = floatPreferencesKey("forty_six_dose")
        val fortySixRatio = floatPreferencesKey("forty_six_ratio")
        val fortySixTaste = stringPreferencesKey("forty_six_taste")
        val fortySixStrength = stringPreferencesKey("forty_six_strength")
        val fortySixLockRatio = booleanPreferencesKey("forty_six_lock_ratio")
        val fortySixPresets = stringPreferencesKey("forty_six_presets")
        val whatsNewSeen = intPreferencesKey("whats_new_seen")
        val grindFrom = stringPreferencesKey("grind_from")
        val grindTo = stringPreferencesKey("grind_to")
        val grindSetting = stringPreferencesKey("grind_setting")
        val cooldownSeconds = intPreferencesKey("cooldown_seconds")
        val cooldownAutoStart = booleanPreferencesKey("cooldown_auto_start")
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
            flowSmoothing = prefs[Keys.flowSmoothing]?.let { value ->
                runCatching { FlowSmoothing.valueOf(value) }.getOrNull()
            } ?: FlowSmoothing.NORMAL,
            autoFinish = prefs[Keys.autoFinish] ?: true,
            scaleAsked = prefs[Keys.scaleAsked] ?: false,
            useScale = prefs[Keys.useScale] ?: true,
            autoConnectOnLaunch = prefs[Keys.autoConnect] ?: true,
            stopTimerOnDisconnect = prefs[Keys.stopTimerOnDisconnect] ?: true,
            keepScaleInGrams = prefs[Keys.keepScaleInGrams] ?: true,
            lastRecipeId = prefs[Keys.lastRecipeId]?.takeIf { it > 0 },
            lastRecipeFortySix = prefs[Keys.lastRecipeFortySix] ?: false,
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
            fortySixLockRatio = prefs[Keys.fortySixLockRatio] ?: false,
            fortySixPresets = decodePresets(prefs[Keys.fortySixPresets]),
            whatsNewSeenVersion = prefs[Keys.whatsNewSeen] ?: 0,
            grindFromId = prefs[Keys.grindFrom] ?: "",
            grindToId = prefs[Keys.grindTo] ?: "",
            grindSetting = prefs[Keys.grindSetting] ?: "",
            cooldownSeconds = prefs[Keys.cooldownSeconds] ?: DEFAULT_COOLDOWN_SECONDS,
            cooldownAutoStart = prefs[Keys.cooldownAutoStart] ?: false,
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

    suspend fun setFlowSmoothing(smoothing: FlowSmoothing) =
        edit { it[Keys.flowSmoothing] = smoothing.name }

    suspend fun setAutoFinish(enabled: Boolean) = edit { it[Keys.autoFinish] = enabled }

    suspend fun setUseScale(enabled: Boolean) = edit { it[Keys.useScale] = enabled }

    suspend fun setScaleAsked() = edit { it[Keys.scaleAsked] = true }

    suspend fun setAutoConnect(enabled: Boolean) = edit { it[Keys.autoConnect] = enabled }

    suspend fun setStopTimerOnDisconnect(enabled: Boolean) =
        edit { it[Keys.stopTimerOnDisconnect] = enabled }

    suspend fun setKeepScaleInGrams(enabled: Boolean) = edit { it[Keys.keepScaleInGrams] = enabled }

    /**
     * What was picked last time. A 4:6 generator build does not lie in the database and has
     * no id of its own — it is marked by a flag and built again at startup.
     */
    suspend fun setLastRecipe(id: Long?, fortySix: Boolean = false) = edit { prefs ->
        if (id == null) prefs.remove(Keys.lastRecipeId) else prefs[Keys.lastRecipeId] = id
        prefs[Keys.lastRecipeFortySix] = fortySix
    }

    suspend fun setPresetsVersion(version: Int) = edit { it[Keys.presetsVersion] = version }

    suspend fun setPresetsLocale(tag: String) = edit { it[Keys.presetsLocale] = tag }

    suspend fun setKeepRecipeWater(enabled: Boolean) = edit { it[Keys.keepRecipeWater] = enabled }

    /** Marks a built-in recipe as deleted, so it does not come back when the set is updated. */
    suspend fun addDeletedPreset(name: String) = edit { prefs ->
        prefs[Keys.deletedPresets] = (prefs[Keys.deletedPresets] ?: emptySet()) + name
    }

    /** Remembers the dials of the 4:6 generator: the timings are wired into it and do not change. */
    suspend fun setFortySix(params: FortySixParams) = edit { prefs ->
        prefs[Keys.fortySixDose] = params.doseGrams
        prefs[Keys.fortySixRatio] = params.ratio
        prefs[Keys.fortySixTaste] = params.taste.name
        prefs[Keys.fortySixStrength] = params.strength.name
    }

    suspend fun setFortySixLockRatio(locked: Boolean) =
        edit { it[Keys.fortySixLockRatio] = locked }

    suspend fun setGrindPair(fromId: String, toId: String, setting: String) = edit { prefs ->
        prefs[Keys.grindFrom] = fromId
        prefs[Keys.grindTo] = toId
        prefs[Keys.grindSetting] = setting
    }

    suspend fun setCooldownSeconds(seconds: Int) =
        edit { it[Keys.cooldownSeconds] = seconds }

    suspend fun setCooldownAutoStart(enabled: Boolean) =
        edit { it[Keys.cooldownAutoStart] = enabled }

    suspend fun setWhatsNewSeenVersion(versionCode: Int) =
        edit { it[Keys.whatsNewSeen] = versionCode }

    /**
     * Save the generator settings under a name. The name is the key: saving over an existing
     * one replaces it rather than breeding a second preset with the same title.
     */
    suspend fun saveFortySixPreset(preset: FortySixPreset) = edit { prefs ->
        val kept = decodePresets(prefs[Keys.fortySixPresets])
            .filterNot { it.name.equals(preset.name, ignoreCase = true) }
        prefs[Keys.fortySixPresets] = encodePresets(kept + preset)
    }

    suspend fun deleteFortySixPreset(name: String) = edit { prefs ->
        val kept = decodePresets(prefs[Keys.fortySixPresets])
            .filterNot { it.name.equals(name, ignoreCase = true) }
        prefs[Keys.fortySixPresets] = encodePresets(kept)
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { preferences -> block(preferences) }
    }

    /**
     * The presets lie in a single JSON string: there are few of them, they are always read
     * as a list and never looked up one by one — no reason to start a table for that.
     */
    private fun encodePresets(presets: List<FortySixPreset>): String {
        val array = JSONArray()
        presets.sortedBy { it.name.lowercase() }.forEach { preset ->
            array.put(
                JSONObject()
                    .put("name", preset.name)
                    .put("dose", preset.params.doseGrams)
                    .put("ratio", preset.params.ratio)
                    .put("taste", preset.params.taste.name)
                    .put("strength", preset.params.strength.name)
                    .put("temp", preset.params.waterTempC)
                    .put("lock", preset.lockRatio)
            )
        }
        return array.toString()
    }

    private fun decodePresets(text: String?): List<FortySixPreset> {
        if (text.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        val defaults = FortySixParams()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            FortySixPreset(
                name = name,
                params = FortySixParams(
                    doseGrams = item.optDouble("dose", defaults.doseGrams.toDouble()).toFloat(),
                    ratio = item.optDouble("ratio", defaults.ratio.toDouble()).toFloat(),
                    taste = runCatching { FortySixTaste.valueOf(item.optString("taste")) }
                        .getOrDefault(defaults.taste),
                    strength = runCatching { FortySixStrength.valueOf(item.optString("strength")) }
                        .getOrDefault(defaults.strength),
                    waterTempC = item.optInt("temp", defaults.waterTempC),
                ),
                lockRatio = item.optBoolean("lock", false),
            )
        }
    }
}
