package com.pourista.data.io

import com.pourista.data.model.BrewNotes
import com.pourista.data.model.BrewRecord
import com.pourista.data.model.Recipe
import com.pourista.data.model.USER_RECIPE_SORT_ORDER
import org.json.JSONArray
import org.json.JSONObject

/**
 * Резервная копия: рецепты и вся история завариваний одним файлом.
 *
 * Отдельно от [RecipeJson] и с другим форматом внутри: тот файл пишут и правят
 * руками, поэтому в нём нет ни избранного, ни порядка, ни времён. Здесь всё
 * наоборот — файл делает и читает только приложение, и потерять при переезде
 * не должно ничего.
 *
 * Настройки в копию не входят: там половина полей — счётчики самой установки
 * (какой набор пресетов посеян, что уже показали, какой рецепт открывали
 * последним), и переносить их на другой телефон вредно.
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

    /** Разбор строгий: чужой файл лучше отвергнуть, чем частично применить. */
    fun decode(text: String): Backup {
        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: throw IllegalArgumentException("Ожидался объект JSON")
        val format = root.optString("format")
        if (format != FORMAT) throw IllegalArgumentException("Это не резервная копия")
        if (root.optInt("version", 0) > VERSION) {
            throw IllegalArgumentException("Копия от более новой версии приложения")
        }
        return Backup(
            recipes = root.optJSONArray("recipes").objects().map { decodeRecipe(it) },
            brews = root.optJSONArray("brews").objects().mapNotNull { decodeBrew(it) },
        )
    }

    /** Рецепт как в обменном формате плюс то, что там намеренно опущено. */
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
     * Заваривание. Ряды весов и потока — те же строки с «;», что и в базе:
     * разбирать их ради файла и собирать обратно смысла нет.
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
            item.putOpt("brewer", notes.brewer)
            item.putOpt("temp", notes.waterTemp)
            item.putOpt("note", notes.extra)
            json.put("notes", item)
        }
        return json
    }

    /** Заваривание без времени опознать нечем — такую запись пропускаем. */
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
