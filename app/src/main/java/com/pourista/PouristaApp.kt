package com.pourista

import android.app.Application
import android.content.Context
import android.net.Uri
import android.content.res.Configuration
import android.util.Log
import com.pourista.brew.BrewEngine
import com.pourista.brew.BrewEvent
import com.pourista.brew.BrewPhase
import com.pourista.brew.BrewState
import com.pourista.brew.CooldownTimer
import com.pourista.data.db.AppDatabase
import com.pourista.data.io.RecipeJson
import com.pourista.data.model.BrewNotes
import com.pourista.data.model.Recipe
import com.pourista.data.prefs.AppSettings
import com.pourista.data.prefs.SettingsRepository
import com.pourista.data.presets.BuiltInRecipes
import com.pourista.data.presets.FortySixGenerator
import com.pourista.data.presets.FortySixParams
import com.pourista.data.repo.BrewRepository
import com.pourista.core.AppLocale
import com.pourista.data.repo.RecipeRepository
import com.pourista.scale.ScaleRepository
import com.pourista.ui.brew.BrewCuePlayer
import com.pourista.ui.labelRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PouristaApp : Application() {

    lateinit var container: AppContainer
        private set

    /** The app language is needed here already: the presets are read from the app context. */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * The app language is changed in the system settings, without restarting the process. The texts
     * of the built-in recipes lie in the database, so they have to be laid down again — they will not
     * translate themselves.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        container.syncPresets()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as PouristaApp).container

/**
 * Assembling the dependencies by hand: there are few of them, while the scale and the course of a
 * brew have to outlive a change of screens, so they live at application level.
 */
class AppContainer(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Needed by the screens for access to files: importing and exporting recipes. */
    val appContext: Context get() = context

    /**
     * The context strings are read from. The language could have been changed after the start, while
     * the application context on older Android keeps the previous locale — so we take it anew on
     * every request rather than once.
     */
    private val localized: Context get() = AppLocale.wrap(context)

    private val database = AppDatabase.build(context)

    val settings = SettingsRepository(context)
    val recipes = RecipeRepository(database.recipeDao())
    val brews = BrewRepository(database.brewDao())
    val scale = ScaleRepository(context)
    val brewEngine = BrewEngine(scale, scope)

    /**
     * The cooldown timer. It lives here for the same reason as the course of a brew: it is wound
     * before stepping away from the phone, and switching screens or closing the brew screen must not
     * knock the count off.
     */
    val cooldown = CooldownTimer(scope)

    /**
     * A recipe assembled in the recording mode and not yet saved. It lies here rather than in the
     * database: until the person presses "Save" in the editor, there must be no record.
     */
    var recipeDraft: Recipe? = null

    val settingsState = settings.settings
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private val _brewSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** A brew has gone into the history — the screen has something to say. */
    val brewSaved: SharedFlow<Unit> = _brewSaved.asSharedFlow()

    private val _draftReady = MutableStateFlow(false)

    /** A recorded pour is ready to become a recipe — the screen should open the editor. */
    val draftReady: StateFlow<Boolean> = _draftReady.asStateFlow()

    fun clearDraftReady() {
        _draftReady.value = false
    }

    private val _openedRecipes = MutableStateFlow<Int?>(null)

    /**
     * How many recipes arrived from an outside file; zero means the file did not fit. The event lives
     * until navigation picks it up: a file is opened on a cold start too, when there is no screen yet.
     */
    val openedRecipes: StateFlow<Int?> = _openedRecipes.asStateFlow()

    fun clearOpenedRecipes() {
        _openedRecipes.value = null
    }

    /**
     * A recipe file opened outside the app: from a messenger, from mail or from a file manager.
     *
     * The first recipe from the file becomes the current one at once: a file is opened to brew by it,
     * not to put it in the list and look for it again.
     */
    fun openRecipeFile(uri: Uri) {
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().decodeToString() }
                }.getOrNull()
            }
            val parsed = text
                ?.let { runCatching { RecipeJson.decode(it) }.getOrNull() }
                .orEmpty()
            if (parsed.isEmpty()) {
                _openedRecipes.value = 0
                return@launch
            }
            val ids = recipes.importAll(parsed)
            ids.firstOrNull()
                ?.let { id -> recipes.recipeById(id) }
                ?.let { recipe ->
                    brewEngine.reset()
                    brewEngine.selectRecipe(recipe)
                    settings.setLastRecipe(recipe.id)
                    recipes.markUsed(recipe.id)
                }
            _openedRecipes.value = ids.size
        }
    }

    /**
     * The cue for the end of the cooldown. The on-screen player will not do for this: the ring happens
     * minutes after the brew, when the screen could have closed. It is created together with the timer
     * rather than at the moment of the ring: SoundPool does not read files instantly, and one created
     * on the ring would have managed to keep quiet.
     */
    @Volatile
    private var cooldownCues: BrewCuePlayer? = null

    /**
     * Wind the cooldown timer. The player is created under a lock: the timer is wound from a
     * background coroutine, and two simultaneous starts would leave an extra SoundPool behind for the
     * rest of the life of the process.
     */
    fun startCooldown(seconds: Int) {
        synchronized(this) {
            if (cooldownCues == null) cooldownCues = BrewCuePlayer(context)
        }
        cooldown.start(seconds)
    }

    init {
        syncPresets()
        applyUnitSetting()
        applyCueSettings()
        pipeWeightToEngine()
        pauseTimerOnDisconnect()
        keepSelectedRecipeFresh()
        saveFinishedBrews()
        startCooldownAfterBrew()
        ringCooldown()
        autoConnect()
    }

    /**
     * The brew is over — we wind the cooldown, if that is how it is set. Recording a recipe and
     * misses of the button do not count: there is no cup in them, and nothing to cool.
     */
    private fun startCooldownAfterBrew() {
        scope.launch {
            brewEngine.events.filterIsInstance<BrewEvent.Finished>().collect {
                val state = brewEngine.state.value
                if (!settingsState.value.cooldownAutoStart) return@collect
                if (state.recording || state.elapsedMs < MIN_SAVED_MS) return@collect
                startCooldown(settingsState.value.cooldownSeconds)
            }
        }
    }

    /** Time is up: the same ring as at the finish of a brew. */
    private fun ringCooldown() {
        scope.launch {
            cooldown.rings.collect {
                val current = settingsState.value
                cooldownCues?.finished(current.soundCues, current.hapticCues)
            }
        }
    }

    /**
     * A finished brew goes into the history here rather than on the screen: the finish is sometimes
     * automatic, when the cup has been taken off the scale, and the brew screen may be closed by then.
     */
    private fun saveFinishedBrews() {
        scope.launch {
            brewEngine.events.filterIsInstance<BrewEvent.Finished>().collect {
                val state = brewEngine.state.value
                if (worthKeeping(state)) {
                    saveBrew(state)
                    _brewSaved.emit(Unit)
                }
                if (state.recording) prepareRecordedDraft()
            }
        }
    }

    /**
     * An accidental touch of "Start" and "Finish" right after must not clutter the history, so
     * everything shorter than [MIN_SAVED_MS] is quietly skipped.
     */
    private fun worthKeeping(state: BrewState): Boolean {
        if (state.elapsedMs < MIN_SAVED_MS) return false
        return state.weightSeries.isNotEmpty() || state.weightGrams > 0f
    }

    private suspend fun saveBrew(state: BrewState) {
        val recipe = state.recipe
        brews.saveBrew(
            brewedAt = System.currentTimeMillis(),
            doseGrams = state.doseGrams,
            weightGrams = state.weightGrams,
            elapsedMs = state.elapsedMs,
            weightSeries = state.weightSeries,
            flowSeries = state.flowSeries,
            flowRateAvg = state.flowRateAvg,
            recipeId = recipe?.id?.takeIf { it > 0 },
            recipeName = recipe?.name,
            notes = BrewNotes(
                bean = recipe?.beanName,
                roaster = recipe?.roaster,
                grinder = recipe?.grinderName,
                grindSetting = recipe?.grindSetting,
                filterName = recipe?.filterName,
                brewer = recipe?.brewer,
                waterTemp = recipe?.waterTempC?.toString(),
            ),
        )
    }

    /**
     * Assembles a recipe by the 4:6 method and puts it into the database.
     *
     * The build does not go into the recipe list and has no id of its own: the generator is a
     * workbench rather than a way to start a recipe. Brewing by it goes like brewing by any other
     * recipe; to repeat it, open the generator again — the dials in it stayed as they were — and
     * whoever needs sets of their own has presets there.
     */
    fun fortySixRecipe(params: FortySixParams): Recipe = FortySixGenerator.recipe(
        params = params,
        name = localized.getString(R.string.four_six_recipe_name),
        grindSetting = localized.getString(R.string.grind_coarse),
        notes = localized.getString(
            R.string.four_six_notes,
            localized.getString(params.taste.labelRes()),
            localized.getString(params.strength.labelRes()),
        ),
    )

    /** We turn a recorded pour into a recipe draft — the editor picks it up. */
    private fun prepareRecordedDraft() {
        val stamp = SimpleDateFormat("d MMMM, HH:mm", Locale.getDefault()).format(Date())
        val draft = brewEngine.buildRecordedRecipe(
            name = "${localized.getString(R.string.recipe_recorded_name)} $stamp",
            brewer = "",
        )
        brewEngine.cancelRecording()
        if (draft != null) {
            recipeDraft = draft
            _draftReady.value = true
        }
    }

    /**
     * We keep the chosen recipe up to date: an edit in the editor or a switch of auto-start next to
     * the "Start" button has to be seen by the brew screen at once, not after the recipe is picked
     * again.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun keepSelectedRecipeFresh() {
        scope.launch {
            brewEngine.state
                .map { it.recipe?.id }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    // A generator build has no id: there is nothing to watch for in the database, and
                    // replacing it with null is even less of an option.
                    if (id == null || id == 0L) emptyFlow() else recipes.observeRecipe(id)
                }
                .collect { fresh -> brewEngine.selectRecipe(fresh) }
        }
    }

    /**
     * Brings the built-in recipes in line with the app: reseeds them when the set has been updated,
     * and translates the texts when the language has changed. One's own recipes, edited copies of
     * built-in ones and favourites are left alone.
     *
     * It is called at startup and on a configuration change, so it is obliged to be cheap and
     * repeatable: if nothing has changed, there is no work at all.
     */
    fun syncPresets() {
        scope.launch {
            val current = settings.current()
            val locale = currentLocaleTag()

            if (current.presetsVersion < BuiltInRecipes.VERSION) {
                val removed = recipes.deleteUntouchedBuiltIns()
                // The first run gets the current set alone. After that a reseed also brings back the
                // recipes we no longer offer to newcomers: whoever had them keeps them.
                val firstRun = current.presetsVersion == 0
                // Built-in recipes deleted by the user are not brought back: if there is no aeropress
                // in the house, it must not rise again with an update.
                BuiltInRecipes.all(localized, includeRetired = !firstRun)
                    .filterNot { it.name in current.deletedPresets }
                    .forEach { recipes.save(it) }
                settings.setPresetsVersion(BuiltInRecipes.VERSION)
                settings.setPresetsLocale(locale)
                Log.d(TAG, "Presets updated: $removed removed, added anew")
                return@launch
            }

            if (current.presetsLocale == locale) return@launch
            // A reseed cannot fix the language: it would wipe the order and the favourites. We change
            // the texts alone, and only in recipes nobody has touched by hand.
            BuiltInRecipes.all(localized).forEach { recipes.relocalizeBuiltIn(it) }
            settings.setPresetsLocale(locale)
            Log.d(TAG, "The texts of the built-in recipes are translated into $locale")
        }
    }

    /** The language the app currently gives out the resource strings in. */
    private fun currentLocaleTag(): String =
        localized.resources.configuration.locales[0].toLanguageTag()

    /** The thresholds and the cue modes live in the settings, the engine gets them from here. */
    private fun applyCueSettings() {
        scope.launch {
            settings.settings
                .map { it.nearTargetGrams }
                .distinctUntilChanged()
                .collect { brewEngine.nearTargetGrams = it }
        }
        scope.launch {
            settings.settings
                .map { it.paceTolerance }
                .distinctUntilChanged()
                .collect { brewEngine.paceTolerance = it }
        }
        scope.launch {
            settings.settings
                .map { it.flowSmoothing }
                .distinctUntilChanged()
                .collect { brewEngine.flowSmoothing = it }
        }
        scope.launch {
            settings.settings
                .map { it.autoFinish }
                .distinctUntilChanged()
                .collect { brewEngine.autoFinish = it }
        }
        scope.launch {
            settings.settings
                .map { it.keepRecipeWater }
                .distinctUntilChanged()
                .collect { brewEngine.setKeepRecipeWater(it) }
        }
    }

    /** The scale can show ounces, the app cannot: we keep it in grams. */
    private fun applyUnitSetting() {
        scope.launch {
            settings.settings
                .map { it.keepScaleInGrams }
                .distinctUntilChanged()
                .collect { scale.keepGrams(it) }
        }
    }

    /** The link to the scale is gone — the weight no longer changes, and the timer would lie about the pour. */
    private fun pauseTimerOnDisconnect() {
        scope.launch {
            scale.state
                .map { it.isConnected }
                .distinctUntilChanged()
                .collect { connected ->
                    if (!connected &&
                        settingsState.value.stopTimerOnDisconnect &&
                        brewEngine.state.value.phase == BrewPhase.RUNNING
                    ) {
                        brewEngine.pause()
                    }
                }
        }
    }

    private fun autoConnect() {
        scope.launch {
            val current = settings.current()
            // Until the scale question has been asked we do not go on the air: there are no
            // permissions anyway, and the search would clutter the log.
            if (current.needScaleQuestion) return@launch
            if (current.useScale && current.autoConnectOnLaunch) scale.startScan()
        }
    }

    /**
     * The weight the gain for auto-start is counted from. We keep the minimum since the arming: that
     * way auto-start fires the same both from a zeroed scale and when something is already standing on
     * it and the person simply ticked the box before pouring.
     */
    @Volatile
    private var autoStartBaselineGrams = Float.MAX_VALUE

    /**
     * The weight from the scale goes to the engine; auto-start of the timer lives here too — it has to
     * work even if the brew screen is not open right now.
     */
    private fun pipeWeightToEngine() {
        scope.launch {
            scale.state
                .map { it.weightGrams }
                .distinctUntilChanged()
                .collect { grams ->
                    brewEngine.onWeightChanged(grams)
                    val brew = brewEngine.state.value

                    if (!brew.autoStartArmed || brew.phase != BrewPhase.IDLE) {
                        autoStartBaselineGrams = Float.MAX_VALUE
                        return@collect
                    }

                    if (grams < autoStartBaselineGrams) autoStartBaselineGrams = grams
                    if (grams - autoStartBaselineGrams >= AUTO_START_DELTA_GRAMS) {
                        Log.d(
                            TAG,
                            "Auto-start: the weight grew from %.1f to %.1f g".format(
                                autoStartBaselineGrams, grams
                            ),
                        )
                        autoStartBaselineGrams = Float.MAX_VALUE
                        brewEngine.start()
                    }
                }
        }

    }


    private companion object {
        const val TAG = "BrewAutomation"

        /** How much the weight must grow after arming for this to be water rather than a shiver of the scale. */
        const val AUTO_START_DELTA_GRAMS = 2f

        /** Shorter than this is not a brew but a miss of the button. */
        const val MIN_SAVED_MS = 5_000L
    }
}
