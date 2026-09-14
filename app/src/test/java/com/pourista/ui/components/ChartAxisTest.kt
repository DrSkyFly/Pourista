package com.pourista.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The tick size has to be a familiar one: the weight in fifties, the flow rate in 2.5 g/s.
 */
class ChartAxisTest {

    private fun step(axis: Pair<Float, Int>) = axis.first / axis.second

    @Test
    fun `the weight on an ordinary cup goes in fifties`() {
        val axis = chartAxis(rawMax = 250f)

        assertEquals(50f, step(axis), 0.01f)
        assertEquals(300f, axis.first, 0.01f)
        assertEquals(6, axis.second)
    }

    @Test
    fun `on a large volume the step grows so the lines do not merge`() {
        val axis = chartAxis(rawMax = 600f)

        assertEquals(100f, step(axis), 0.01f)
        assertEquals(700f, axis.first, 0.01f)
    }

    @Test
    fun `there are no quarter-hundreds among the ticks`() {
        listOf(80f, 150f, 250f, 360f, 600f, 1000f).forEach { max ->
            val step = step(chartAxis(rawMax = max))
            assertEquals("the step for $max should be a multiple of 50", 0f, step % 50f, 0.01f)
        }
    }

    @Test
    fun `the flow rate is laid out in twos and a half`() {
        val axis = chartAxis(rawMax = 10f, steps = FLOW_AXIS_STEPS)

        assertEquals(2.5f, step(axis), 0.01f)
        assertEquals(12.5f, axis.first, 0.01f)
    }

    @Test
    fun `a spike of the flow rate does not turn the grid into a picket fence`() {
        val axis = chartAxis(rawMax = 90f, steps = FLOW_AXIS_STEPS)

        assert(axis.second <= 7) { "the ticks came out at ${axis.second}" }
    }

    @Test
    fun `an empty series does not break the axis`() {
        val axis = chartAxis(rawMax = 0f)

        assertEquals(50f, axis.first, 0.01f)
        assertEquals(1, axis.second)
    }
}
