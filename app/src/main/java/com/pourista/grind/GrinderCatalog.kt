package com.pourista.grind

import android.content.Context
import com.pourista.R
import org.json.JSONObject

/**
 * Список кофемолок из res/raw/grinders.json.
 *
 * Файл небольшой и лежит в самом приложении: пересчёт помола работает без
 * сети. Читаем один раз при первом обращении.
 */
object GrinderCatalog {

    @Volatile
    private var cache: List<Grinder>? = null

    fun all(context: Context): List<Grinder> = cache ?: synchronized(this) {
        cache ?: load(context).also { cache = it }
    }

    fun byId(context: Context, id: String?): Grinder? =
        id?.let { key -> all(context).firstOrNull { it.id == key } }

    /** Фирмы по алфавиту. */
    fun brands(context: Context): List<String> =
        all(context).map { it.brand }.distinct().sortedBy { it.lowercase() }

    /** Модели одной фирмы. */
    fun models(context: Context, brand: String): List<Grinder> =
        all(context).filter { it.brand == brand }.sortedBy { it.model.lowercase() }

    /**
     * Поиск модели по тому, как её записали в рецепте. Пишут по-разному —
     * «Timemore C5 ESP», «1Zpresso JX-Pro», просто «C40 MK4», — поэтому
     * сравниваем без пробелов, дефисов и регистра.
     */
    fun find(context: Context, query: String?): Grinder? {
        val needle = simplify(query ?: return null)
        if (needle.length < 3) return null
        val all = all(context)
        all.firstOrNull { simplify(it.name) == needle }?.let { return it }
        // В рецепте написано с лишним: ищем самое длинное название внутри.
        all.filter { needle.contains(simplify(it.name)) }
            .maxByOrNull { simplify(it.name).length }
            ?.let { return it }
        // Написано короче полного имени: берём самое короткое из подходящих,
        // оно же самое точное.
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
