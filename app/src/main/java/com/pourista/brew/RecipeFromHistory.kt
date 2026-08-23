package com.pourista.brew

import com.pourista.data.model.Recipe

/**
 * Рецепт из уже заваренной чашки.
 *
 * История хранит вес по секунде на точку — этого хватает, чтобы прогнать ряд
 * через тот же детектор проливов, что работает вживую, и получить те же шаги.
 * Разница только в источнике: там показания весов в реальном времени, здесь
 * запись, но правила разбора обязаны совпадать, иначе рецепт из истории не
 * сойдётся с рецептом, записанным на ходу.
 */
internal object RecipeFromHistory {

    /**
     * Собирает рецепт по ряду веса из истории. Возвращает null, когда проливов
     * в записи не видно — например, заваривали без весов.
     */
    fun build(
        weightSeries: List<Float>,
        name: String,
        brewer: String,
        doseGrams: Float,
        waterTempC: Int,
        elapsedMs: Long,
    ): Recipe? {
        if (weightSeries.size < MIN_POINTS) return null

        val recorder = PourRecorder()
        weightSeries.forEachIndexed { index, weight ->
            recorder.onSample(index * MS_PER_POINT, weight)
        }

        val total = maxOf(elapsedMs, weightSeries.lastIndex * MS_PER_POINT)
        return recorder.buildRecipe(
            name = name,
            brewer = brewer,
            doseGrams = doseGrams,
            totalElapsedMs = total,
            waterTempC = waterTempC,
        )
    }

    /** График истории пишется раз в секунду. */
    private const val MS_PER_POINT = 1_000L
    private const val MIN_POINTS = 3
}
