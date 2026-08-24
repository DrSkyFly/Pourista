package com.pourista.brew

import com.pourista.data.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ряд веса из истории — одна точка в секунду. Проверяем, что по нему выходит
 * тот же рецепт, что записался бы вживую.
 */
class RecipeFromHistoryTest {

    /** Собирает посекундный ряд: пролив, пауза, пролив, пауза. */
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
    fun `по записи собирается рецепт с проливами и паузами`() {
        // 50 г за 10 с, пауза 35 с, 100 г за 20 с, пауза 25 с.
        val weights = series(50f to 10, 0f to 35, 100f to 20, 0f to 25)

        val recipe = RecipeFromHistory.build(
            weightSeries = weights,
            name = "Запись",
            brewer = "Hario V60-02",
            doseGrams = 15f,
            waterTempC = 94,
            elapsedMs = weights.size * 1_000L,
        )!!

        // Два пролива и слив.
        assertEquals(3, recipe.steps.size)
        assertEquals("первый влив — блуминг", StepKind.BLOOM, recipe.steps.first().kind)
        assertEquals("последний шаг — слив", StepKind.DRAWDOWN, recipe.steps.last().kind)
        // Шаг длится от своего пролива до начала следующего, а не до конца влива.
        assertEquals(listOf(0, 45), recipe.steps.take(2).map { it.startSec })
        assertEquals(45, recipe.steps.first().durationSec)
        assertEquals(listOf(50f, 150f, 150f), recipe.steps.map { it.targetWaterGrams })
        assertEquals(150f, recipe.waterGrams, 0.01f)
        assertEquals(15f, recipe.doseGrams, 0.01f)
        assertEquals("Hario V60-02", recipe.brewer)
        assertTrue("последний шаг доводит до конца записи", recipe.totalSec >= 85)
    }

    @Test
    fun `заваривание без весов рецепта не даёт`() {
        assertNull(
            RecipeFromHistory.build(
                weightSeries = List(120) { 0f },
                name = "Запись",
                brewer = "",
                doseGrams = 15f,
                waterTempC = 94,
                elapsedMs = 120_000L,
            )
        )
    }

    @Test
    fun `пустая запись рецепта не даёт`() {
        assertNull(
            RecipeFromHistory.build(
                weightSeries = emptyList(),
                name = "Запись",
                brewer = "",
                doseGrams = 15f,
                waterTempC = 94,
                elapsedMs = 0L,
            )
        )
    }
}
