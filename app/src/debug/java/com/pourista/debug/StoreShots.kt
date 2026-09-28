package com.pourista.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pourista.BuildConfig
import com.pourista.appContainer
import com.pourista.core.AppLocale
import com.pourista.data.model.BrewNotes
import com.pourista.data.model.Recipe
import com.pourista.data.model.RecipeStep
import com.pourista.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Debug-only remote control for shooting the store screenshots.
 *
 * The listing needs the same seven screens in every language, three of them with a live weight on
 * them. An emulator has no Bluetooth and no scale to pair with, and brewing the same cup fourteen
 * times by hand is not a plan, so the screens are set up from adb instead:
 *
 * ```
 * adb shell am broadcast -a com.pourista.debug.SHOT -n com.pourista/com.pourista.debug.StoreShotsReceiver \
 *   --es cmd lang --es tag de
 * ```
 *
 * Commands: `lang` (tag), `seed`, `brew` (pace, until, recipe), `idle`, `compact` (on), `off`.
 */
class StoreShotsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val cmd = intent.getStringExtra("cmd") ?: return
        Log.i(TAG, "command $cmd")
        when (cmd) {
            // The app language, the same as the picker in the settings sets.
            "lang" -> AppLocale.apply(app, intent.getStringExtra("tag"))
            // A clean phone set up for the shots: no first-run questions, dark theme, a grinder
            // pair remembered for the converter.
            "prep" -> scene { prep(app) }
            // Recipes are seeded by the app itself; this fills the history.
            "seed" -> scene { seedHistory(app) }
            // A brew played out to the given second: the pour follows the recipe plan multiplied
            // by `pace`, so 1.0 keeps the guidance calm and 1.6 makes it ask to slow down.
            "brew" -> scene {
                pour(
                    context = app,
                    paceFactor = intent.getFloatExtra("pace", 1f),
                    untilSec = intent.getIntExtra("until", 60),
                    recipeName = intent.getStringExtra("recipe"),
                    freeze = intent.getBooleanExtra("freeze", true),
                )
            }
            // The tight brew screen is a setting, and the shots are wanted in both sizes.
            "compact" -> scene {
                app.appContainer.settings.setCompactBrew(intent.getBooleanExtra("on", true))
            }
            // The screen before the start: a scale on the line, an empty cone, no timer running.
            "idle" -> scene { idle(app, intent.getStringExtra("recipe")) }
            "off" -> scene {
                app.appContainer.brewEngine.reset()
                app.appContainer.scale.simulate(connected = false)
            }
        }
    }

    /**
     * One scene at a time. A scene left running keeps feeding weights into the next one and pauses
     * its brew when its own tail runs out — two of them at once turn the screen into nonsense.
     */
    private fun scene(block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch { block() }
    }

    /** Everything the first run would otherwise ask about, answered from here. */
    private suspend fun prep(context: Context) {
        val settings = context.appContainer.settings
        settings.setScaleAsked()
        settings.setUseScale(true)
        settings.setThemeMode(ThemeMode.DARK)
        settings.setWhatsNewSeenVersion(BuildConfig.VERSION_CODE)
        settings.setGrindPair(
            fromId = "comandante-c40-mk4",
            toId = "timemore-c5-esp",
            setting = "23",
        )
    }

    /** The brew screen as it looks before the start: the recipe is picked, the dose is recorded. */
    private suspend fun idle(context: Context, recipeName: String?) {
        val container = context.appContainer
        container.brewEngine.reset()
        container.scale.simulate(connected = true, grams = 0f)
        container.brewEngine.selectRecipe(recipe(context, recipeName))
        container.brewEngine.setDose(DOSE_GRAMS)
    }

    /**
     * Plays a brew in real time: the weight follows the recipe plan, and at [untilSec] the shot is
     * due. Everything on the screen — the ring, the target, the flow rate and the verdict on the
     * pace — is counted by the app itself, exactly as it is with a real scale.
     *
     * With [freeze] the timer is paused on that second, which is handy for looking at it but turns
     * the button into "Resume". For a shot the pour has to keep going, so the brew is left running
     * for a while longer and the screen is caught on the way.
     */
    private suspend fun pour(
        context: Context,
        paceFactor: Float,
        untilSec: Int,
        recipeName: String?,
        freeze: Boolean,
    ) {
        val container = context.appContainer
        val engine = container.brewEngine
        idle(context, recipeName)
        val plan = engine.state.value.recipe?.steps.orEmpty()
        if (plan.isEmpty()) return

        container.scale.simulate(connected = true, grams = 0f)
        engine.start()
        val startedAt = System.currentTimeMillis()
        val stopAt = if (freeze) untilSec else untilSec + TAIL_SECONDS
        while (true) {
            val elapsed = (System.currentTimeMillis() - startedAt) / 1000f
            if (elapsed >= stopAt) break
            container.scale.simulate(connected = true, grams = poured(plan, elapsed, paceFactor))
            delay(TICK_MS)
        }
        engine.pause()
    }

    /**
     * How much water is on the scale at [elapsed]. Every pour step is poured at the rate the recipe
     * asks for, multiplied by [paceFactor]; the rest of the step the weight stands.
     */
    private fun poured(plan: List<RecipeStep>, elapsed: Float, paceFactor: Float): Float {
        var previous = 0f
        var weight = 0f
        for (step in plan) {
            val delta = step.targetWaterGrams - previous
            if (delta > 0f && elapsed > step.startSec) {
                val seconds = step.pourSeconds(delta) / paceFactor
                val share = ((elapsed - step.startSec) / seconds).coerceIn(0f, 1f)
                weight = previous + delta * share
            }
            previous = maxOf(previous, step.targetWaterGrams)
            if (elapsed < step.endSec) break
        }
        return weight
    }

    /** The recipe for the shot: by name, or the first one in the list. */
    private suspend fun recipe(context: Context, name: String?): Recipe? {
        val all = context.appContainer.recipes.observeRecipes().first()
        return all.firstOrNull { name != null && it.name.contains(name, ignoreCase = true) }
            ?: all.firstOrNull()
    }

    /**
     * Three brews for the history screen. The curves are counted from the recipe plan, so they bend
     * the way a real pour does rather than like a drawn line.
     */
    private suspend fun seedHistory(context: Context) {
        val container = context.appContainer
        container.brews.exportAll().forEach { container.brews.deleteBrew(it.id) }
        val recipes = context.appContainer.recipes.observeRecipes().first().take(3)
        if (recipes.isEmpty()) return

        val hour = 60 * 60 * 1000L
        val beans = listOf(
            Triple("Colombia Huila", "Tasty Coffee", "23"),
            Triple("Ethiopia Guji", "Torrefacto", "21"),
            Triple("Kenya Nyeri", "Sweet Beans", "24"),
        )
        recipes.forEachIndexed { index, recipe ->
            val plan = recipe.steps
            if (plan.isEmpty()) return@forEachIndexed
            val elapsedMs = (recipe.totalSec + 4 * index) * 1000L
            val seconds = (elapsedMs / 1000).toInt()
            val pace = 1f + 0.03f * (index - 1)
            val weights = (0..seconds).map { poured(plan, it.toFloat(), pace) }
            val flows = weights.zipWithNext { a, b -> (b - a).coerceAtLeast(0f) } + 0f
            val (bean, roaster, grind) = beans[index % beans.size]
            container.brews.saveBrew(
                brewedAt = System.currentTimeMillis() - (index * 27 + 9) * hour,
                doseGrams = recipe.doseGrams,
                weightGrams = weights.last(),
                elapsedMs = elapsedMs,
                weightSeries = weights,
                flowSeries = flows,
                flowRateAvg = flows.filter { it > 0f }.average().toFloat(),
                recipeId = recipe.id,
                recipeName = recipe.name,
                notes = BrewNotes(
                    bean = bean,
                    roaster = roaster,
                    grinder = "Comandante C40 MK4",
                    grindSetting = grind,
                    filterName = "Hario",
                    brewer = recipe.brewer,
                    waterTemp = recipe.waterTempC.toString(),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "StoreShots"
        const val TICK_MS = 100L
        const val DOSE_GRAMS = 15.1f

        /** How long the brew is left running past the shot, so the button still says "Pause". */
        const val TAIL_SECONDS = 25
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** The scene running right now; a new command cancels it. */
        var job: Job? = null
    }
}
