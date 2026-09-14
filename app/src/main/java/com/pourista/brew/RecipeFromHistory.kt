package com.pourista.brew

import com.pourista.data.model.Recipe

/**
 * A recipe out of a cup that has already been brewed.
 *
 * The history keeps the weight at one point per second — enough to run the series through
 * the same pour detector that works live and get the same steps. The only difference is the
 * source: there it is scale readings in real time, here a recording, but the rules of the
 * breakdown have to match, otherwise a recipe from the history would not agree with a recipe
 * recorded on the go.
 */
internal object RecipeFromHistory {

    /**
     * Builds a recipe from a weight series out of the history. Returns null when no pours
     * can be seen in the recording — brewed without a scale, for instance.
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

    /** The history chart is written once a second. */
    private const val MS_PER_POINT = 1_000L
    private const val MIN_POINTS = 3
}
