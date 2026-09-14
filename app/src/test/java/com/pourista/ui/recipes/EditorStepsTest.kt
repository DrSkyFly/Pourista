package com.pourista.ui.recipes

import com.pourista.data.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bloom and the drawdown are fixed at the ends of a recipe: there is nothing to pour before the
 * first and nowhere to pour after the last. We check that the order holds through any edit.
 */
class EditorStepsTest {

    private fun step(key: Long, kind: StepKind) = EditableStep(key = key, kind = kind)

    private val full = listOf(
        step(1, StepKind.BLOOM),
        step(2, StepKind.POUR),
        step(3, StepKind.POUR),
        step(4, StepKind.DRAWDOWN),
    )

    @Test
    fun `a new step goes in before the drawdown`() {
        val result = full.withRegularStep(step(9, StepKind.POUR))

        assertEquals(
            listOf(StepKind.BLOOM, StepKind.POUR, StepKind.POUR, StepKind.POUR, StepKind.DRAWDOWN),
            result.map { it.kind },
        )
        assertEquals("the new step is second to last", 9L, result[result.lastIndex - 1].key)
    }

    @Test
    fun `without a drawdown a new step simply goes at the end`() {
        val result = full.dropLast(1).withRegularStep(step(9, StepKind.POUR))

        assertEquals(9L, result.last().key)
    }

    @Test
    fun `the bloom goes in first and only once`() {
        val withoutBloom = full.drop(1)

        val added = withoutBloom.withBloom(step(9, StepKind.BLOOM))
        assertEquals(StepKind.BLOOM, added.first().kind)
        assertEquals(9L, added.first().key)

        assertEquals("a second bloom is not added", added, added.withBloom(step(10, StepKind.BLOOM)))
    }

    @Test
    fun `the drawdown goes in last and only once`() {
        val withoutDrawdown = full.dropLast(1)

        val added = withoutDrawdown.withDrawdown(step(9, StepKind.DRAWDOWN))
        assertEquals(StepKind.DRAWDOWN, added.last().kind)

        assertEquals(added, added.withDrawdown(step(10, StepKind.DRAWDOWN)))
    }

    @Test
    fun `fixed steps do not move`() {
        assertFalse(full.canMove(1, 1))
        assertFalse(full.canMove(4, -1))
        assertEquals(full, full.moved(1, 1))
    }

    @Test
    fun `an ordinary step does not go past the bloom or the drawdown`() {
        assertFalse("above the bloom is not allowed", full.canMove(2, -1))
        assertFalse("below the drawdown is not allowed", full.canMove(3, 1))
        assertTrue(full.canMove(2, 1))

        val moved = full.moved(2, 1)
        assertEquals(listOf(1L, 3L, 2L, 4L), moved.map { it.key })
    }

    @Test
    fun `the pour time is counted from the rate, and an empty field does not break the step`() {
        val step = EditableStep(key = 1, kind = StepKind.POUR, water = "50", flow = "5")
        assertEquals(10, step.pourSeconds)

        val empty = step.copy(water = "", flow = "")
        assertEquals(0f, empty.deltaGrams, 0.001f)
        assertEquals(0, empty.pourSeconds)
    }

    private val pour = EditableStep(key = 1, kind = StepKind.POUR, water = "50", flow = "5")
        .syncPourSeconds()

    @Test
    fun `the rate and the pour time recalculate each other`() {
        assertEquals("10", pour.pourSec)

        val byTime = pour.withPourSeconds("20")
        assertEquals("2.5", byTime.flow)
        assertEquals("20", byTime.pourSec)

        val byFlow = byTime.withFlow("10")
        assertEquals("5", byFlow.pourSec)
    }

    @Test
    fun `the volume changes whatever was not set by hand`() {
        // The rate was set by hand — it stays, and the time is recalculated.
        val keepsFlow = pour.withFlow("5").withWater("100")
        assertEquals("5", keepsFlow.flow)
        assertEquals("20", keepsFlow.pourSec)

        // The time was set by hand — it stays, and the rate adjusts.
        val keepsTime = pour.withPourSeconds("10").withWater("100")
        assertEquals("10", keepsTime.pourSec)
        assertEquals("10", keepsTime.flow)
    }

    @Test
    fun `the pour is trimmed to the length of the step`() {
        // 50 g at 5 g/s is 10 seconds, and the step has become six seconds long.
        val short = pour.copy(duration = "6").pourFittedToDuration()

        assertEquals("6", short.pourSec)
        assertEquals("50 g in 6 seconds", "8.3", short.flow)
        assertFalse(short.pourTooLong)
    }

    @Test
    fun `a pour shorter than the step is left alone`() {
        val roomy = pour.copy(duration = "30")

        assertEquals(roomy, roomy.pourFittedToDuration())
    }

    @Test
    fun `a half-typed length does not recalculate the rate`() {
        // The field was cleared to be typed anew: that is not yet "zero seconds".
        val typing = pour.copy(duration = "")

        assertEquals(typing, typing.pourFittedToDuration())
    }

    @Test
    fun `after the trim the volume changes the rate rather than the time`() {
        val short = pour.copy(duration = "6").pourFittedToDuration()

        // The pour already takes the whole step, and the volume added has to fit into the same six
        // seconds.
        val more = short.withWater("60")
        assertEquals("6", more.pourSec)
        assertEquals("10", more.flow)
    }

    @Test
    fun `a half-typed number does not wipe the neighbouring field`() {
        val cleared = pour.withFlow("")

        assertEquals("", cleared.flow)
        assertEquals("the pour time stayed the same", "10", cleared.pourSec)
    }
}
