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
    fun `the water is rounded to five grams and keeps the ratio`() {
        val scaled = hoffmann().scaledToDose(15.4f)

        assertEquals(15.4f, scaled.doseGrams, 0.001f)
        // 15,4 × 16,667 = 256,7 → 255
        assertEquals(255f, scaled.waterGrams, 0.001f)
        assertEquals(255f, scaled.finalTargetGrams, 0.001f)
        assertTrue(
            "the ratio must not drift further than the rounding",
            kotlin.math.abs(scaled.ratio - hoffmann().ratio) < 0.2f,
        )
    }

    @Test
    fun `every target is a multiple of five and none decreases`() {
        val scaled = hoffmann().scaledToDose(21.3f)

        var previous = 0f
        scaled.steps.forEach { step ->
            val target = step.targetWaterGrams
            assertEquals(
                "the target $target is not a multiple of five",
                0f,
                target % 5f,
                0.001f,
            )
            assertTrue("the targets must only grow: $previous to $target", target >= previous)
            previous = target
        }
    }

    @Test
    fun `pauses hold the weight of the previous pour`() {
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
    fun `the step length and the flow rate do not change on a recalculation`() {
        val original = hoffmann()
        val scaled = original.scaledToDose(20f)

        original.steps.zip(scaled.steps).forEach { (before, after) ->
            assertEquals(
                "the step length is set by the recipe and does not depend on the dose",
                before.durationSec,
                after.durationSec,
            )
            assertEquals(
                "the flow rate is a property of the technique, not of the dose",
                before.pourFlowRate,
                after.pourFlowRate,
                0.001f,
            )
        }
    }

    @Test
    fun `the pour time inside a step grows together with the dose`() {
        val bloomFor = { dose: Float ->
            val recipe = hoffmann().scaledToDose(dose)
            recipe.steps[0].pourSeconds(recipe.steps[0].targetWaterGrams)
        }

        val small = bloomFor(15f)
        val large = bloomFor(20f)

        // 50 g at 5 g/s is ten seconds; for a larger dose there is more water, the pour is longer,
        // and the step itself stays forty-five seconds.
        assertEquals(10f, small, 0.5f)
        assertTrue("for 20 g the pour should be longer: $small to $large", large > small)
        assertTrue("the pour is obliged to fit into the step", large <= 45f)
    }

    @Test
    fun `without a dose the recipe is left alone`() {
        val recipe = hoffmann()
        assertSame(recipe, recipe.scaledToDose(0f))
        assertSame(recipe, recipe.scaledToDose(-1f))
    }

    @Test
    fun `recalculating from the original again gives the same result`() {
        val once = hoffmann().scaledToDose(18f)
        val twice = hoffmann().scaledToDose(18f)

        assertEquals(once.waterGrams, twice.waterGrams, 0.001f)
        assertEquals(
            once.steps.map { it.targetWaterGrams },
            twice.steps.map { it.targetWaterGrams },
        )
    }
}
