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
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

    /** Язык приложения нужен уже здесь: из контекста приложения читаются пресеты. */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * Язык приложения меняют в системных настройках, не перезапуская процесс.
     * Тексты встроенных рецептов лежат в базе, поэтому их надо переложить
     * заново — сами они не переведутся.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        container.syncPresets()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as PouristaApp).container

/**
 * Ручная сборка зависимостей: их немного, а весы и ход заваривания должны
 * пережить смену экранов, поэтому живут на уровне приложения.
 */
class AppContainer(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Нужен экранам для доступа к файлам: импорт и экспорт рецептов. */
    val appContext: Context get() = context

    /**
     * Контекст для чтения строк. Язык могли сменить уже после запуска, а
     * контекст приложения на старых Android остаётся с прежней локалью —
     * поэтому берём его заново на каждое обращение, а не один раз.
     */
    private val localized: Context get() = AppLocale.wrap(context)

    private val database = AppDatabase.build(context)

    val settings = SettingsRepository(context)
    val recipes = RecipeRepository(database.recipeDao())
    val brews = BrewRepository(database.brewDao())
    val scale = ScaleRepository(context)
    val brewEngine = BrewEngine(scale, scope)

    /**
     * Рецепт, собранный в режиме записи и ещё не сохранённый. Лежит здесь, а не
     * в базе: пока человек не нажал «Сохранить» в редакторе, записи быть не должно.
     */
    var recipeDraft: Recipe? = null

    val settingsState = settings.settings
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private val _brewSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Заваривание легло в историю — экрану есть о чём сказать. */
    val brewSaved: SharedFlow<Unit> = _brewSaved.asSharedFlow()

    private val _draftReady = MutableStateFlow(false)

    /** Записанный пролив готов стать рецептом — экран должен открыть редактор. */
    val draftReady: StateFlow<Boolean> = _draftReady.asStateFlow()

    fun clearDraftReady() {
        _draftReady.value = false
    }

    private val _openedRecipes = MutableStateFlow<Int?>(null)

    /**
     * Сколько рецептов пришло файлом снаружи; ноль — файл не подошёл. Событие
     * живёт до того, как его заберёт навигация: файл открывают и на холодном
     * запуске, когда экрана ещё нет.
     */
    val openedRecipes: StateFlow<Int?> = _openedRecipes.asStateFlow()

    fun clearOpenedRecipes() {
        _openedRecipes.value = null
    }

    /**
     * Файл рецепта, открытый снаружи приложения: из мессенджера, почты или
     * файлового менеджера.
     *
     * Первый рецепт из файла сразу становится текущим: файл открывают, чтобы
     * заварить по нему, а не чтобы положить в список и искать заново.
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
                    settings.setLastRecipeId(recipe.id)
                    recipes.markUsed(recipe.id)
                }
            _openedRecipes.value = ids.size
        }
    }

    init {
        syncPresets()
        applyUnitSetting()
        applyCueSettings()
        pipeWeightToEngine()
        pauseTimerOnDisconnect()
        keepSelectedRecipeFresh()
        saveFinishedBrews()
        autoConnect()
    }

    /**
     * Законченное заваривание кладём в историю здесь, а не на экране: финиш
     * бывает и автоматическим, когда с весов сняли чашку, а экран заваривания
     * к этому моменту может быть уже закрыт.
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
     * Случайное касание «Старт» и тут же «Финиш» историю засорять не должно,
     * поэтому всё короче [MIN_SAVED_MS] молча пропускаем.
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
            recipeId = recipe?.id,
            recipeName = recipe?.name,
            notes = BrewNotes(
                bean = recipe?.beanName,
                roaster = recipe?.roaster,
                grinder = recipe?.grinderName,
                grindSetting = recipe?.grindSetting,
                brewer = recipe?.brewer,
                waterTemp = recipe?.waterTempC?.toString(),
            ),
        )
    }

    /**
     * Собирает рецепт по методу 4:6 и кладёт его в базу.
     *
     * Строка в базе одна и та же: генератор — это верстак, а не фабрика
     * одноразовых рецептов, засоряющих список. Настройки при этом запоминаются,
     * чтобы в следующий раз заварить так же.
     */
    suspend fun buildFortySixRecipe(params: FortySixParams): Recipe? {
        val existing = settings.current().fortySixRecipeId?.let { recipes.recipeById(it) }
        val fresh = FortySixGenerator.recipe(
            params = params,
            name = localized.getString(R.string.four_six_recipe_name),
            grindSetting = localized.getString(R.string.grind_coarse),
            notes = localized.getString(
                R.string.four_six_notes,
                localized.getString(params.taste.labelRes()),
                localized.getString(params.strength.labelRes()),
            ),
        )
        // Избранное, место в списке и дату заведения оставляем прежними: с точки
        // зрения человека рецепт не новый, у него просто поменялись числа.
        val id = if (existing == null) {
            recipes.saveNewOnTop(fresh)
        } else {
            recipes.save(
                fresh.copy(
                    id = existing.id,
                    isFavorite = existing.isFavorite,
                    sortOrder = existing.sortOrder,
                    createdAt = existing.createdAt,
                    lastUsedAt = existing.lastUsedAt,
                )
            )
        }
        settings.setFortySix(params)
        settings.setFortySixRecipeId(id)
        return recipes.recipeById(id)
    }

    /** Записанный пролив превращаем в черновик рецепта — его подхватит редактор. */
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
     * Выбранный рецепт держим в актуальном состоянии: правку в редакторе или
     * переключение автостарта у кнопки «Старт» экран заваривания должен видеть
     * сразу, а не после повторного выбора рецепта.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun keepSelectedRecipeFresh() {
        scope.launch {
            brewEngine.state
                .map { it.recipe?.id }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id == null || id == 0L) flowOf(null) else recipes.observeRecipe(id)
                }
                .collect { fresh -> brewEngine.selectRecipe(fresh) }
        }
    }

    /**
     * Приводит встроенные рецепты в соответствие с приложением: пересеивает,
     * когда обновился набор, и переводит тексты, когда сменился язык. Свои
     * рецепты, поправленные копии встроенных и избранное не трогаются.
     *
     * Вызывается при запуске и при смене конфигурации, поэтому обязана быть
     * дешёвой и повторяемой: если ничего не изменилось, работы никакой.
     */
    fun syncPresets() {
        scope.launch {
            val current = settings.current()
            val locale = currentLocaleTag()

            if (current.presetsVersion < BuiltInRecipes.VERSION) {
                val removed = recipes.deleteUntouchedBuiltIns()
                // Первый запуск — только актуальный набор. Дальше пересев
                // возвращает и рецепты, которые новичкам уже не предлагаем:
                // у кого они стояли, у того и останутся.
                val firstRun = current.presetsVersion == 0
                // Удалённые пользователем встроенные рецепты обратно не возвращаем:
                // если аэропресса в доме нет, он не должен воскресать с обновлением.
                BuiltInRecipes.all(localized, includeRetired = !firstRun)
                    .filterNot { it.name in current.deletedPresets }
                    .forEach { recipes.save(it) }
                settings.setPresetsVersion(BuiltInRecipes.VERSION)
                settings.setPresetsLocale(locale)
                Log.d(TAG, "Пресеты обновлены: удалено $removed, добавлено заново")
                return@launch
            }

            if (current.presetsLocale == locale) return@launch
            // Пересевом язык не поправить: он снёс бы порядок и избранное. Меняем
            // только тексты и только у рецептов, которых не касались руками.
            BuiltInRecipes.all(localized).forEach { recipes.relocalizeBuiltIn(it) }
            settings.setPresetsLocale(locale)
            Log.d(TAG, "Тексты встроенных рецептов переведены на $locale")
        }
    }

    /** Язык, на котором приложение сейчас отдаёт строки из ресурсов. */
    private fun currentLocaleTag(): String =
        localized.resources.configuration.locales[0].toLanguageTag()

    /** Пороги и режимы подсказок живут в настройках, движок получает их отсюда. */
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

    /** Весы умеют показывать унции, приложение — нет: держим их в граммах. */
    private fun applyUnitSetting() {
        scope.launch {
            settings.settings
                .map { it.keepScaleInGrams }
                .distinctUntilChanged()
                .collect { scale.keepGrams(it) }
        }
    }

    /** Связь с весами пропала — вес больше не меняется, и таймер врал бы о проливе. */
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
            // Пока про весы не спросили, в эфир не лезем: разрешений всё
            // равно нет, а поиск засорил бы журнал.
            if (current.needScaleQuestion) return@launch
            if (current.useScale && current.autoConnectOnLaunch) scale.startScan()
        }
    }

    /**
     * Вес, от которого считается прирост для автостарта. Держим минимум с момента
     * взведения: тогда автостарт одинаково срабатывает и с обнулённых весов, и
     * когда на них уже что-то стоит, а человек просто щёлкнул галку перед проливом.
     */
    @Volatile
    private var autoStartBaselineGrams = Float.MAX_VALUE

    /**
     * Вес с весов уходит в движок; здесь же живёт автостарт таймера — он должен
     * работать, даже если экран заваривания сейчас не открыт.
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
                            "Автостарт: вес вырос с %.1f до %.1f г".format(
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

        /** Насколько вес должен вырасти после взведения, чтобы это была вода, а не дрожь весов. */
        const val AUTO_START_DELTA_GRAMS = 2f

        /** Короче этого — не заваривание, а промах по кнопке. */
        const val MIN_SAVED_MS = 5_000L
    }
}
