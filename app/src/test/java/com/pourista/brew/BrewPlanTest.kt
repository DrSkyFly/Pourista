package com.pourista.brew

import com.pourista.data.model.Recipe
import com.pourista.data.model.RecipeStep
import com.pourista.data.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BrewPlanTest {

    @Test
    fun `a swirl starts right after the pour and the remainder goes into the pause`() {
        // A 30 s pour, a 5 s swirl, a 10 s pause. Poured fast and finished on the twentieth.
        val plan = listOf(
            step(StepKind.POUR, 0, 30, 300f),
            step(StepKind.SWIRL, 30, 5, 300f),
            step(StepKind.WAIT, 35, 10, 300f),
        )

        val pulled = plan.pulledIn(stepStartSec = 30, shiftSec = 10)

        assertEquals(listOf(0 to 20, 20 to 5, 25 to 20), pulled.times())
        // The recipe ends at the same moment: the time is not lost, it is in the pause.
        assertEquals(45, plan.last().endSec)
        assertEquals(45, pulled.last().endSec)
    }

    @Test
    fun `the recipe has no pause so one appears`() {
        val plan = listOf(
            step(StepKind.POUR, 0, 30, 300f),
            step(StepKind.SWIRL, 30, 5, 300f),
            step(StepKind.POUR, 35, 10, 400f),
        )

        val pulled = plan.pulledIn(stepStartSec = 30, shiftSec = 10)

        assertEquals(listOf(0 to 20, 20 to 5, 25 to 10, 35 to 10), pulled.times())
        assertEquals(StepKind.WAIT, pulled[2].kind)
        // The pause holds the same amount of water as after the swirl.
        assertEquals(300f, pulled[2].targetWaterGrams, 0.01f)
        // The second pour starts exactly when it meant to.
        assertEquals(35, pulled[3].startSec)
    }

    @Test
    fun `no pause is needed before the drawdown`() {
        val plan = listOf(
            step(StepKind.POUR, 75, 30, 250f),
            step(StepKind.SWIRL, 105, 10, 250f),
            step(StepKind.DRAWDOWN, 115, 95, 250f),
        )

        val pulled = plan.pulledIn(stepStartSec = 105, shiftSec = 10)

        assertEquals(listOf(75 to 20, 95 to 10, 105 to 95), pulled.times())
        // No more water is coming and nothing to wait for: the brew ends earlier.
        assertEquals(200, pulled.last().endSec)
    }

    @Test
    fun `a swirl at the end of the recipe simply finishes it earlier`() {
        val plan = listOf(
            step(StepKind.POUR, 0, 30, 300f),
            step(StepKind.SWIRL, 30, 5, 300f),
        )

        val pulled = plan.pulledIn(stepStartSec = 30, shiftSec = 10)

        assertEquals(listOf(0 to 20, 20 to 5), pulled.times())
    }

    @Test
    fun `a stir is pulled in the same way as a swirl`() {
        val plan = listOf(
            step(StepKind.BLOOM, 0, 15, 66f),
            step(StepKind.STIR, 15, 30, 66f),
            step(StepKind.POUR, 45, 60, 360f),
        )

        val pulled = plan.pulledIn(stepStartSec = 15, shiftSec = 3)

        assertEquals(listOf(0 to 12, 12 to 30, 42 to 3, 45 to 60), pulled.times())
    }

    @Test
    fun `only a swirl can be pulled, and only behind a pour`() {
        val plan = listOf(
            step(StepKind.POUR, 0, 30, 300f),
            step(StepKind.WAIT, 30, 10, 300f),
            step(StepKind.SWIRL, 40, 5, 300f),
        )

        // A pause after a pour will wait: its place in the recipe was chosen deliberately.
        assertSame(plan, plan.pulledIn(stepStartSec = 30, shiftSec = 10))
        // The swirl is behind a pause rather than a pour — so the pour ended long ago.
        assertSame(plan, plan.pulledIn(stepStartSec = 40, shiftSec = 5))
        // There is no such step in the recipe.
        assertSame(plan, plan.pulledIn(stepStartSec = 33, shiftSec = 5))
    }

    @Test
    fun `a pull-in does not eat the whole pour`() {
        val plan = listOf(
            step(StepKind.POUR, 0, 30, 300f),
            step(StepKind.SWIRL, 30, 5, 300f),
            step(StepKind.WAIT, 35, 10, 300f),
        )

        assertSame(plan, plan.pulledIn(stepStartSec = 30, shiftSec = 30))
        assertSame(plan, plan.pulledIn(stepStartSec = 30, shiftSec = 40))
        assertSame(plan, plan.pulledIn(stepStartSec = 30, shiftSec = 0))
    }

    @Test
    fun `pull-ins carry over to a recipe recalculated for the dose`() {
        val recipe = recipe(
            step(StepKind.POUR, 0, 30, 300f),
            step(StepKind.SWIRL, 30, 5, 300f),
            step(StepKind.WAIT, 35, 10, 300f),
            step(StepKind.POUR, 45, 20, 500f),
            step(StepKind.SWIRL, 65, 5, 500f),
            step(StepKind.DRAWDOWN, 70, 60, 500f),
        )

        val planned = recipe.withPullIns(linkedMapOf(30 to 10, 65 to 5))

        assertEquals(
            listOf(0 to 20, 20 to 5, 25 to 20, 45 to 15, 60 to 5, 65 to 60),
            planned.steps.times(),
        )
        // A recipe with no pull-ins stays the very same object.
        assertSame(recipe, recipe.withPullIns(emptyMap()))
    }

    private fun List<RecipeStep>.times(): List<Pair<Int, Int>> =
        map { it.startSec to it.durationSec }

    private fun step(kind: StepKind, start: Int, duration: Int, target: Float) = RecipeStep(
        kind = kind,
        startSec = start,
        durationSec = duration,
        targetWaterGrams = target,
    )

    private fun recipe(vararg steps: RecipeStep) = Recipe(
        name = "Test",
        brewer = "V60",
        doseGrams = 18f,
        waterGrams = 300f,
        waterTempC = 94,
        steps = steps.toList(),
    )
}
