package com.pourista.brew

import com.pourista.data.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A weight series from the history — one point a second. We check that it gives the same recipe as
 * would have been recorded live.
 */
class RecipeFromHistoryTest {

    /** Builds a per-second series: pour, pause, pour, pause. */
    private fun series(vararg parts: Pair<Float, Int>): List<Float> {
        val points = mutableListOf<Float>()
        var weight = 0f
        parts.forEach { (grams, seconds) ->
            val perSecond = grams / seconds
            repeat(seconds) {
                weight += perSecond
                points += weight
            }
        }
        return points
    }

    @Test
    fun `a recipe with pours and pauses is assembled from a recording`() {
        // 50 g in 10 s, a 35 s pause, 100 g in 20 s, a 25 s pause.
        val weights = series(50f to 10, 0f to 35, 100f to 20, 0f to 25)

        val recipe = RecipeFromHistory.build(
            weightSeries = weights,
            name = "Recording",
            brewer = "Hario V60-02",
            doseGrams = 15f,
            waterTempC = 94,
            elapsedMs = weights.size * 1_000L,
        )!!

        // Two pours and the drawdown.
        assertEquals(3, recipe.steps.size)
        assertEquals("the first pour is the bloom", StepKind.BLOOM, recipe.steps.first().kind)
        assertEquals("the last step is the drawdown", StepKind.DRAWDOWN, recipe.steps.last().kind)
        // A step lasts from its own pour to the start of the next, not to the end of the pour.
        assertEquals(listOf(0, 45), recipe.steps.take(2).map { it.startSec })
        assertEquals(45, recipe.steps.first().durationSec)
        assertEquals(listOf(50f, 150f, 150f), recipe.steps.map { it.targetWaterGrams })
        assertEquals(150f, recipe.waterGrams, 0.01f)
        assertEquals(15f, recipe.doseGrams, 0.01f)
        assertEquals("Hario V60-02", recipe.brewer)
        assertTrue("the last step carries through to the end of the recording", recipe.totalSec >= 85)
    }

    @Test
    fun `a brew without a scale gives no recipe`() {
        assertNull(
            RecipeFromHistory.build(
                weightSeries = List(120) { 0f },
                name = "Recording",
                brewer = "",
                doseGrams = 15f,
                waterTempC = 94,
                elapsedMs = 120_000L,
            )
        )
    }

    @Test
    fun `an empty recording gives no recipe`() {
        assertNull(
            RecipeFromHistory.build(
                weightSeries = emptyList(),
                name = "Recording",
                brewer = "",
                doseGrams = 15f,
                waterTempC = 94,
                elapsedMs = 0L,
            )
        )
    }
}
