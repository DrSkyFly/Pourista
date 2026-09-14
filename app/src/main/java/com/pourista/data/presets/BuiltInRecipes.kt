package com.pourista.data.presets

import android.content.Context
import com.pourista.R
import com.pourista.data.model.Recipe
import com.pourista.data.model.RecipeStep
import com.pourista.data.model.StepKind

/**
 * The recipes the app is filled with on the first run. After that they are ordinary
 * records in the database: they can be copied and edited to fit your own cone.
 */
object BuiltInRecipes {

    /**
     * The version of the set. When it grows the built-in recipes are reseeded, while the
     * ones the user changed or marked as favourites are left untouched.
     */
    const val VERSION = 10

    /**
     * The set to seed. [includeRetired] covers recipes we no longer offer to newcomers but
     * leave to those who already have them: a person may have got used to them, and an
     * update is no reason to rearrange their shelf.
     */
    fun all(context: Context, includeRetired: Boolean = true): List<Recipe> {
        val medium = context.getString(R.string.grind_medium)
        val coarse = context.getString(R.string.grind_coarse)
        val fine = context.getString(R.string.grind_fine)
        // The order of the list is set here: the V60 cones first, then the other methods.
        // Every recipe gets a place of its own in the sorting.
        return listOf(
            hoffmann(context, medium),
            // 4:6 left the set: the generator counts the same method, and more precisely.
            kasuya(context, coarse).takeIf { includeRetired },
            rao(context, medium),
            espresso(context, fine),
            aeropress(context, fine),
            kalita(context, medium),
            chemex(context, coarse),
        ).filterNotNull().mapIndexed { index, recipe -> recipe.copy(sortOrder = (index + 1) * 10) }
    }

    private fun recipe(
        name: String,
        brewer: String,
        dose: Float,
        water: Float,
        temp: Int,
        grind: String,
        notes: String,
        steps: List<RecipeStep>,
        autoStart: Boolean = true,
        aeropressMode: Boolean = false,
    ) = Recipe(
        name = name,
        brewer = brewer,
        doseGrams = dose,
        waterGrams = water,
        waterTempC = temp,
        grindSetting = grind,
        notes = notes,
        isBuiltIn = true,
        autoStart = autoStart,
        aeropressMode = aeropressMode,
        steps = steps,
    )

    private fun step(
        kind: StepKind,
        start: Int,
        duration: Int,
        target: Float,
        flow: Float = 0f,
    ) = RecipeStep(
        kind = kind,
        startSec = start,
        durationSec = duration,
        targetWaterGrams = target,
        pourFlowRate = flow,
    )

    private fun hoffmann(
        context: Context,
        grind: String,
    ) = recipe(
        name = "V60 · James Hoffmann",
        brewer = "Hario V60-02",
        dose = 15f,
        water = 250f,
        temp = 95,
        grind = grind,
        notes = context.getString(R.string.preset_notes_hoffmann),
        steps = listOf(
            step(StepKind.BLOOM, 0, 45, 50f, flow = 5f),
            step(StepKind.POUR, 45, 30, 150f, flow = 4.5f),
            step(StepKind.POUR, 75, 30, 250f, flow = 4.5f),
            step(StepKind.SWIRL, 105, 10, 250f),
            step(StepKind.DRAWDOWN, 115, 95, 250f),
        ),
    )

    private fun kasuya(
        context: Context,
        grind: String,
    ) = recipe(
        name = "V60 4:6 · Tetsu Kasuya",
        brewer = "Hario V60-02",
        dose = 20f,
        water = 300f,
        temp = 92,
        grind = grind,
        notes = context.getString(R.string.preset_notes_kasuya),
        steps = listOf(
            step(StepKind.BLOOM, 0, 45, 60f, flow = 6f),
            step(StepKind.POUR, 45, 45, 120f, flow = 6f),
            step(StepKind.POUR, 90, 40, 180f, flow = 6f),
            step(StepKind.POUR, 130, 40, 240f, flow = 6f),
            step(StepKind.POUR, 170, 10, 300f, flow = 6f),
            step(StepKind.DRAWDOWN, 180, 30, 300f),
        ),
    )

    private fun rao(
        context: Context,
        grind: String,
    ) = recipe(
        name = "V60 · Scott Rao",
        brewer = "Hario V60-02",
        dose = 22f,
        water = 360f,
        temp = 96,
        grind = grind,
        notes = context.getString(R.string.preset_notes_rao),
        steps = listOf(
            step(StepKind.BLOOM, 0, 15, 66f, flow = 5.5f),
            step(StepKind.STIR, 15, 30, 66f),
            step(StepKind.POUR, 45, 60, 360f, flow = 5f),
            step(StepKind.SWIRL, 105, 10, 360f),
            step(StepKind.DRAWDOWN, 115, 95, 360f),
        ),
    )

    private fun kalita(
        context: Context,
        grind: String,
    ) = recipe(
        name = "Kalita Wave 155",
        brewer = "Kalita Wave 155",
        dose = 20f,
        water = 320f,
        temp = 93,
        grind = grind,
        notes = context.getString(R.string.preset_notes_kalita),
        steps = listOf(
            step(StepKind.BLOOM, 0, 45, 60f, flow = 5f),
            step(StepKind.POUR, 45, 45, 140f, flow = 5f),
            step(StepKind.POUR, 90, 45, 220f, flow = 5f),
            step(StepKind.POUR, 135, 15, 320f, flow = 5f),
            step(StepKind.DRAWDOWN, 150, 75, 320f),
        ),
    )

    private fun chemex(
        context: Context,
        grind: String,
    ) = recipe(
        name = "Chemex",
        brewer = "Chemex 6 cup",
        dose = 30f,
        water = 500f,
        temp = 94,
        grind = grind,
        notes = context.getString(R.string.preset_notes_chemex),
        steps = listOf(
            step(StepKind.BLOOM, 0, 45, 90f, flow = 5f),
            step(StepKind.POUR, 45, 60, 250f, flow = 5.5f),
            step(StepKind.POUR, 105, 55, 375f, flow = 5f),
            step(StepKind.POUR, 160, 30, 500f, flow = 5f),
            step(StepKind.DRAWDOWN, 190, 110, 500f),
        ),
    )

    /**
     * The only recipe with a manual start: on espresso the weight only starts growing after
     * the first drops, and the timer has to be started together with the pump.
     */
    private fun espresso(context: Context, grind: String) = recipe(
        name = "Espresso 1:2",
        brewer = "Espresso",
        dose = 18f,
        water = 36f,
        temp = 93,
        grind = grind,
        notes = context.getString(R.string.preset_notes_espresso),
        autoStart = false,
        steps = listOf(
            // One continuous pour from pressing the pump: 36 g by 0:28.
            step(StepKind.POUR, 0, 28, 36f, flow = 1.3f),
        ),
    )

    private fun aeropress(context: Context, grind: String) = recipe(
        name = "AeroPress",
        brewer = "AeroPress",
        dose = 15f,
        water = 220f,
        temp = 85,
        grind = grind,
        notes = context.getString(R.string.preset_notes_aeropress),
        // The press drops the weight: it must not be smoothed, nor used to catch the end.
        aeropressMode = true,
        steps = listOf(
            step(StepKind.POUR, 0, 20, 220f, flow = 11f),
            step(StepKind.STIR, 20, 70, 220f),
            step(StepKind.PRESS, 90, 45, 220f),
        ),
    )
}
