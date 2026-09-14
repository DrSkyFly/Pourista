package com.pourista.grind

import android.content.Context
import com.pourista.R
import org.json.JSONObject

/**
 * The list of grinders from res/raw/grinders.json.
 *
 * The file is small and lies inside the app itself: the grind conversion works with no network. We
 * read it once, on the first request.
 */
object GrinderCatalog {

    @Volatile
    private var cache: List<Grinder>? = null

    fun all(context: Context): List<Grinder> = cache ?: synchronized(this) {
        cache ?: load(context).also { cache = it }
    }

    fun byId(context: Context, id: String?): Grinder? =
        id?.let { key -> all(context).firstOrNull { it.id == key } }

    /** The makes in alphabetical order. */
    fun brands(context: Context): List<String> =
        all(context).map { it.brand }.distinct().sortedBy { it.lowercase() }

    /** The models of one make. */
    fun models(context: Context, brand: String): List<Grinder> =
        all(context).filter { it.brand == brand }.sortedBy { it.model.lowercase() }

    /**
     * Looking a model up by the way it was written down in a recipe. People write them differently —
     * "Timemore C5 ESP", "1Zpresso JX-Pro", or plain "C40 MK4" — so we compare without spaces,
     * hyphens and case.
     */
    fun find(context: Context, query: String?): Grinder? {
        val needle = simplify(query ?: return null)
        if (needle.length < 3) return null
        val all = all(context)
        all.firstOrNull { simplify(it.name) == needle }?.let { return it }
        // The recipe has more written down than needed: we look for the longest name inside it.
        all.filter { needle.contains(simplify(it.name)) }
            .maxByOrNull { simplify(it.name).length }
            ?.let { return it }
        // It is written shorter than the full name: we take the shortest of those that fit, which is
        // also the most exact.
        return all.filter { simplify(it.name).contains(needle) }
            .minByOrNull { simplify(it.name).length }
    }

    private fun simplify(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

    private fun load(context: Context): List<Grinder> {
        val text = context.resources.openRawResource(R.raw.grinders)
            .bufferedReader()
            .use { it.readText() }
        val items = JSONObject(text).getJSONArray("g")
        return (0 until items.length()).map { index ->
            val obj = items.getJSONObject(index)
            val radix = obj.getJSONArray("r")
            Grinder(
                id = obj.getString("id"),
                brand = obj.getString("br"),
                model = obj.getString("m"),
                base = obj.optDouble("b", 0.0),
                step = obj.getDouble("s"),
                radix = (0 until radix.length()).map { radix.getInt(it) },
                separator = obj.optString("p", ".").first(),
                minClicks = obj.optInt("lo", 0),
                maxClicks = obj.getInt("max"),
                decimals = obj.optInt("d", 0),
            )
        }
    }
}
