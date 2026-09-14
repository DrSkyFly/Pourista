package com.pourista.data.io

import com.pourista.data.model.BrewNotes
import com.pourista.data.model.BrewRecord
import com.pourista.data.model.Recipe
import com.pourista.data.model.USER_RECIPE_SORT_ORDER
import org.json.JSONArray
import org.json.JSONObject

/**
 * A backup: the recipes and the whole brewing history in one file.
 *
 * Apart from [RecipeJson] and with a different format inside: that file is written and
 * edited by hand, so it has no favourites, no order and no times. Here it is the other way
 * round — the file is made and read by the app alone, and nothing must be lost on the way
 * to another phone.
 *
 * The settings are not part of a backup: half the fields there are counters of the
 * installation itself (which preset set has been seeded, what has already been shown, which
 * recipe was opened last), and carrying them to another phone does harm.
 */
object BackupJson {

    const val FORMAT = "pourista.backup"
    const val VERSION = 1

    fun encode(recipes: List<Recipe>, brews: List<BrewRecord>, now: Long): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("createdAt", now)
        val recipeArray = JSONArray()
        recipes.forEach { recipeArray.put(encodeRecipe(it)) }
        root.put("recipes", recipeArray)
        val brewArray = JSONArray()
        brews.forEach { brewArray.put(encodeBrew(it)) }
        root.put("brews", brewArray)
        return root.toString(2)
    }

    /** Parsing is strict: a foreign file is better rejected than partly applied. */
    fun decode(text: String): Backup {
        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: throw IllegalArgumentException("Expected a JSON object")
        val format = root.optString("format")
        if (format != FORMAT) throw IllegalArgumentException("This is not a backup")
        if (root.optInt("version", 0) > VERSION) {
            throw IllegalArgumentException("A backup from a newer version of the app")
        }
        return Backup(
            recipes = root.optJSONArray("recipes").objects().map { decodeRecipe(it) },
            brews = root.optJSONArray("brews").objects().mapNotNull { decodeBrew(it) },
        )
    }

    /** A recipe as in the exchange format plus what is deliberately left out there. */
    private fun encodeRecipe(recipe: Recipe): JSONObject =
        RecipeJson.encodeRecipe(recipe).apply {
            if (recipe.isFavorite) put("favorite", true)
            if (recipe.isBuiltIn) put("builtIn", true)
            put("order", recipe.sortOrder)
            if (recipe.createdAt > 0) put("createdAt", recipe.createdAt)
            if (recipe.updatedAt > 0) put("updatedAt", recipe.updatedAt)
            recipe.lastUsedAt?.let { put("lastUsedAt", it) }
        }

    private fun decodeRecipe(json: JSONObject): Recipe = RecipeJson.decodeRecipe(json).copy(
        isFavorite = json.optBoolean("favorite", false),
        isBuiltIn = json.optBoolean("builtIn", false),
        sortOrder = json.optInt("order", USER_RECIPE_SORT_ORDER),
        createdAt = json.optLong("createdAt", 0L),
        updatedAt = json.optLong("updatedAt", 0L),
        lastUsedAt = json.optLong("lastUsedAt", 0L).takeIf { it > 0L },
    )

    /**
     * A brew. The weight and flow series are the same ";" strings as in the database:
     * taking them apart for the file and putting them back together makes no sense.
     */
    private fun encodeBrew(brew: BrewRecord): JSONObject {
        val json = JSONObject()
        json.put("at", brew.brewedAt)
        json.put("dose", brew.doseGrams)
        json.put("weight", brew.weightGrams)
        json.put("elapsed", brew.elapsedMs)
        json.put("flowAvg", brew.flowRateAvg)
        json.put("weights", brew.weightSeries.joinToString(";"))
        json.put("flows", brew.flowSeries.joinToString(";"))
        json.putOpt("recipe", brew.recipeName)
        val notes = brew.notes
        if (!notes.isEmpty) {
            val item = JSONObject()
            item.putOpt("bean", notes.bean)
            item.putOpt("roaster", notes.roaster)
            item.putOpt("grinder", notes.grinder)
            item.putOpt("grind", notes.grindSetting)
            item.putOpt("filter", notes.filterName)
            item.putOpt("brewer", notes.brewer)
            item.putOpt("temp", notes.waterTemp)
            item.putOpt("note", notes.extra)
            json.put("notes", item)
        }
        return json
    }

    /** A brew with no time cannot be identified — such a record is skipped. */
    private fun decodeBrew(json: JSONObject): BrewRecord? {
        val at = json.optLong("at", 0L).takeIf { it > 0L } ?: return null
        val notes = json.optJSONObject("notes")
        return BrewRecord(
            id = 0,
            brewedAt = at,
            doseGrams = json.optDouble("dose", 0.0).toFloat(),
            weightGrams = json.optDouble("weight", 0.0).toFloat(),
            elapsedMs = json.optLong("elapsed", 0L),
            weightSeries = json.optString("weights").toSeries(),
            flowSeries = json.optString("flows").toSeries(),
            flowRateAvg = json.optDouble("flowAvg", 0.0).toFloat(),
            recipeId = null,
            recipeName = json.optStringOrNull("recipe"),
            notes = BrewNotes(
                bean = notes?.optStringOrNull("bean"),
                roaster = notes?.optStringOrNull("roaster"),
                grinder = notes?.optStringOrNull("grinder"),
                grindSetting = notes?.optStringOrNull("grind"),
                filterName = notes?.optStringOrNull("filter"),
                brewer = notes?.optStringOrNull("brewer"),
                waterTemp = notes?.optStringOrNull("temp"),
                extra = notes?.optStringOrNull("note"),
            ),
        )
    }

    data class Backup(val recipes: List<Recipe>, val brews: List<BrewRecord>)
}

private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }
}

private fun String.toSeries(): List<Float> =
    split(';').mapNotNull { it.trim().toFloatOrNull() }

private fun JSONObject.optStringOrNull(key: String): String? =
    optString(key).takeIf { it.isNotBlank() }
