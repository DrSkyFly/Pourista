package com.pourista.data.io

import com.pourista.data.model.DEFAULT_POUR_FLOW_RATE
import com.pourista.data.model.Recipe
import com.pourista.data.model.RecipeStep
import com.pourista.data.model.StepKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exchanging recipes through a file.
 *
 * A step is described in the file the way a person reads it: "add this much over that
 * many seconds". The cumulative targets and the absolute time are counted on import — the
 * file has none of them, otherwise a recipe could not be written by hand.
 */
object RecipeJson {

    const val FORMAT = "pourista.recipes"
    const val VERSION = 1

    fun encode(recipes: List<Recipe>): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        val array = JSONArray()
        recipes.forEach { array.put(encodeRecipe(it)) }
        root.put("recipes", array)
        return root.toString(2)
    }

    /** Open to a backup: the same recipe there, plus the housekeeping fields. */
    internal fun encodeRecipe(recipe: Recipe): JSONObject {
        val json = JSONObject()
        json.put("name", recipe.name)
        json.putOpt("brewer", recipe.brewer.takeIf { it.isNotBlank() })
        json.put("dose", recipe.doseGrams.trim())
        json.put("water", recipe.waterGrams.trim())
        json.put("temp", recipe.waterTempC)
        json.putOpt("grinder", recipe.grinderName)
        json.putOpt("grind", recipe.grindSetting)
        json.putOpt("filter", recipe.filterName)
        json.putOpt("bean", recipe.beanName)
        json.putOpt("roaster", recipe.roaster)
        json.putOpt("notes", recipe.notes)
        json.put("autoStart", recipe.autoStart)
        if (recipe.aeropressMode) json.put("aeropressMode", true)

        val steps = JSONArray()
        var previousTarget = 0f
        recipe.steps.forEach { step ->
            val delta = (step.targetWaterGrams - previousTarget).coerceAtLeast(0f)
            previousTarget = maxOf(previousTarget, step.targetWaterGrams)
            val item = JSONObject()
            item.put("kind", step.kind.name)
            item.putOpt("title", step.title)
            item.put("duration", step.durationSec)
            if (delta > 0f) {
                item.put("water", delta.trim())
                item.put("flow", step.pourFlowRate.takeIf { it > 0f }?.trim() ?: DEFAULT_POUR_FLOW_RATE)
            }
            item.putOpt("note", step.note)
            steps.put(item)
        }
        json.put("steps", steps)
        return json
    }

    /**
     * Parses the file. The format is deliberately forgiving: a recipe can be written by a
     * person or by a neural network, and missing optional fields are normal.
     */
    fun decode(text: String): List<Recipe> {
        val root = runCatching { JSONObject(extractObject(text)) }.getOrNull()
            ?: throw IllegalArgumentException("Expected a JSON object")
        val array = root.optJSONArray("recipes")
            ?: throw IllegalArgumentException("No recipes list")
        val result = mutableListOf<Recipe>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            result += decodeRecipe(item)
        }
        if (result.isEmpty()) throw IllegalArgumentException("No recipes in the file")
        return result
    }

    /**
     * Cuts the object itself out of pasted text. A recipe is often copied from a chat with
     * a neural network, and that likes to wrap the answer in ```json and add a couple of
     * words before and after — parsing would trip over them for no reason.
     */
    private fun extractObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end < start) throw IllegalArgumentException("No JSON object in the text")
        return text.substring(start, end + 1)
    }

    internal fun decodeRecipe(json: JSONObject): Recipe {
        val name = json.optString("name").takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("The recipe has no name")
        val steps = decodeSteps(json.optJSONArray("steps"))
        val water = json.optDouble("water", Double.NaN).toFloat()
            .takeIf { !it.isNaN() && it > 0f }
            ?: steps.maxOfOrNull { it.targetWaterGrams }
            ?: 0f
        return Recipe(
            name = name,
            brewer = json.optString("brewer"),
            doseGrams = json.optDouble("dose", 0.0).toFloat(),
            waterGrams = water,
            waterTempC = json.optInt("temp", DEFAULT_TEMP_C),
            grinderName = json.optStringOrNull("grinder"),
            grindSetting = json.optStringOrNull("grind"),
            filterName = json.optStringOrNull("filter"),
            beanName = json.optStringOrNull("bean"),
            roaster = json.optStringOrNull("roaster"),
            notes = json.optStringOrNull("notes"),
            autoStart = json.optBoolean("autoStart", true),
            aeropressMode = json.optBoolean("aeropressMode", false),
            steps = steps,
        )
    }

    private fun decodeSteps(array: JSONArray?): List<RecipeStep> {
        if (array == null) return emptyList()
        val steps = mutableListOf<RecipeStep>()
        var start = 0
        var cumulative = 0f
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val delta = item.optDouble("water", 0.0).toFloat().coerceAtLeast(0f)
            val kind = item.optStringOrNull("kind")
                ?.let { key -> StepKind.entries.firstOrNull { it.name.equals(key, true) } }
                ?: if (delta > 0f) StepKind.POUR else StepKind.WAIT
            val duration = item.optInt("duration", 0).coerceAtLeast(1)
            cumulative += delta
            steps += RecipeStep(
                kind = kind,
                title = item.optStringOrNull("title"),
                startSec = start,
                durationSec = duration,
                targetWaterGrams = cumulative,
                pourFlowRate = if (delta > 0f) {
                    item.optDouble("flow", DEFAULT_POUR_FLOW_RATE.toDouble()).toFloat()
                } else {
                    0f
                },
                note = item.optStringOrNull("note"),
            )
            start += duration
        }
        return steps
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        optString(key).takeIf { it.isNotBlank() }

    /** Numbers in the file should look the way they do in a recipe: 50, not 50.0. */
    private fun Float.trim(): Number =
        if (this % 1f == 0f) toInt() else (kotlin.math.round(this * 10f) / 10f)

    private const val DEFAULT_TEMP_C = 94
}
