package com.pourista.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeScalingTest {

    private fun hoffmann() = Recipe(
        name = "V60",
        brewer = "Hario V60-02",
        doseGrams = 15f,
        waterGrams = 250f,
        waterTempC = 95,
        steps = listOf(
            RecipeStep(
                kind = StepKind.POUR,
                startSec = 0,
                durationSec = 45,
                targetWaterGrams = 50f,
                pourFlowRate = 5f,
            ),
            RecipeStep(kind = StepKind.WAIT, startSec = 45, durationSec = 0, targetWaterGrams = 50f),
            RecipeStep(kind = StepKind.POUR, startSec = 45, durationSec = 30, targetWaterGrams = 150f),
            RecipeStep(kind = StepKind.POUR, startSec = 75, durationSec = 30, targetWaterGrams = 250f),
            RecipeStep(
                kind = StepKind.DRAWDOWN,
                startSec = 105,
                durationSec = 95,
                targetWaterGrams = 250f,
            ),
        ),
    )

    @Test
    fun `вода округляется до пяти граммов и сохраняет пропорцию`() {
        val scaled = hoffmann().scaledToDose(15.4f)

        assertEquals(15.4f, scaled.doseGrams, 0.001f)
        // 15,4 × 16,667 = 256,7 → 255
        assertEquals(255f, scaled.waterGrams, 0.001f)
        assertEquals(255f, scaled.finalTargetGrams, 0.001f)
        assertTrue(
            "пропорция не должна уехать дальше округления",
            kotlin.math.abs(scaled.ratio - hoffmann().ratio) < 0.2f,
        )
    }

    @Test
    fun `все цели кратны пяти и не убывают`() {
        val scaled = hoffmann().scaledToDose(21.3f)

        var previous = 0f
        scaled.steps.forEach { step ->
            val target = step.targetWaterGrams
            assertEquals(
                "цель $target не кратна пяти",
                0f,
                target % 5f,
                0.001f,
            )
            assertTrue("цели должны только расти: $previous → $target", target >= previous)
            previous = target
        }
    }

    @Test
    fun `паузы держат вес предыдущего пролива`() {
        val scaled = hoffmann().scaledToDose(20f)

        val bloom = scaled.steps[0].targetWaterGrams
        assertEquals(bloom, scaled.steps[1].targetWaterGrams, 0.001f)
        assertEquals(
            scaled.steps[3].targetWaterGrams,
            scaled.steps[4].targetWaterGrams,
            0.001f,
        )
    }

    @Test
    fun `длительность шага и скорость влива при пересчёте не меняются`() {
        val original = hoffmann()
        val scaled = original.scaledToDose(20f)

        original.steps.zip(scaled.steps).forEach { (before, after) ->
            assertEquals(
                "длительность шага задаётся рецептом и не зависит от дозы",
                before.durationSec,
                after.durationSec,
            )
            assertEquals(
                "скорость влива — свойство техники, а не дозы",
                before.pourFlowRate,
                after.pourFlowRate,
                0.001f,
            )
        }
    }

    @Test
    fun `время влива внутри шага растёт вместе с дозой`() {
        val bloomFor = { dose: Float ->
            val recipe = hoffmann().scaledToDose(dose)
            recipe.steps[0].pourSeconds(recipe.steps[0].targetWaterGrams)
        }

        val small = bloomFor(15f)
        val large = bloomFor(20f)

        // 50 г при 5 г/с — десять секунд; для большей дозы воды больше, влив дольше,
        // а сам шаг остаётся сорокапятисекундным.
        assertEquals(10f, small, 0.5f)
        assertTrue("для 20 г влив должен быть длиннее: $small → $large", large > small)
        assertTrue("влив обязан помещаться в шаг", large <= 45f)
    }

    @Test
    fun `без дозы рецепт не трогаем`() {
        val recipe = hoffmann()
        assertSame(recipe, recipe.scaledToDose(0f))
        assertSame(recipe, recipe.scaledToDose(-1f))
    }

    @Test
    fun `повторный пересчёт от исходника даёт тот же результат`() {
        val once = hoffmann().scaledToDose(18f)
        val twice = hoffmann().scaledToDose(18f)

        assertEquals(once.waterGrams, twice.waterGrams, 0.001f)
        assertEquals(
            once.steps.map { it.targetWaterGrams },
            twice.steps.map { it.targetWaterGrams },
        )
    }
}
