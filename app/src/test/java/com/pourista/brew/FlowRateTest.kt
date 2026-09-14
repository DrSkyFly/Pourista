package com.pourista.brew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowRateTest {

    /** An even pour: [gramsPerSecond] g/s in ticks of [tickMs] ms. */
    private fun pour(
        flow: FlowRate,
        gramsPerSecond: Float,
        tickMs: Long,
        forMs: Long,
        fromMs: Long = 0L,
        fromGrams: Float = 0f,
    ): Float {
        var last = 0f
        var at = fromMs
        while (at <= fromMs + forMs) {
            last = flow.onSample(fromGrams + gramsPerSecond * (at - fromMs) / 1000f, null, at)
            at += tickMs
        }
        return last
    }

    @Test
    fun `an even pour gives its own rate`() {
        assertEquals(10f, pour(FlowRate(), gramsPerSecond = 10f, tickMs = 100, forMs = 3_000), 0.01f)
        assertEquals(4.5f, pour(FlowRate(), gramsPerSecond = 4.5f, tickMs = 100, forMs = 3_000), 0.01f)
    }

    @Test
    fun `a drifting tick does not overstate the rate`() {
        // A tick promises 100 ms, but delay gives "no less". The old counting took ten ticks
        // for a second and at a tick of 130 ms overstated the rate by a quarter.
        assertEquals(10f, pour(FlowRate(), gramsPerSecond = 10f, tickMs = 130, forMs = 3_000), 0.01f)
        assertEquals(10f, pour(FlowRate(), gramsPerSecond = 10f, tickMs = 250, forMs = 3_000), 0.01f)
    }

    @Test
    fun `at the start of a pour the rate is not understated`() {
        val flow = FlowRate()
        // While the window is shorter than three tenths of a second there is simply no rate.
        assertEquals(0f, flow.onSample(0f, null, 0), 0.001f)
        assertEquals(0f, flow.onSample(1f, null, 100), 0.001f)
        assertEquals(0f, flow.onSample(2f, null, 200), 0.001f)
        // And as soon as the window has filled — the truth at once, not a third of it.
        assertEquals(10f, flow.onSample(3f, null, 300), 0.01f)
    }

    @Test
    fun `the number from the scale beats our own estimate`() {
        val flow = FlowRate(smoothing = FlowSmoothing.NONE)
        pour(flow, gramsPerSecond = 10f, tickMs = 100, forMs = 1_000)

        // The scale counted it itself — we take its number.
        assertEquals(6.5f, flow.onSample(11f, 6.5f, 1_100), 0.01f)
        // Backwards it goes negative on them, and that does not mean a pour.
        assertEquals(0f, flow.onSample(12f, -3f, 1_200), 0.01f)
        // The scale ceiling on a sharp change of weight is not a flow rate.
        assertEquals(10f, flow.onSample(13f, 99.9f, 1_300), 0.01f)
    }

    @Test
    fun `the number from the scale is smoothed on a par with our own estimate`() {
        val flow = FlowRate(smoothing = FlowSmoothing.NORMAL)
        pour(flow, gramsPerSecond = 10f, tickMs = 100, forMs = 2_000)

        // The scale showed half as much: until 1.9.2 that went to the screen as it came, and
        // the figure jumped with every packet. Now the reading walks towards the new number
        // gradually.
        val first = flow.onSample(20f, 5f, 2_100)
        assertTrue(first > 9f)
        assertTrue(first < 10f)

        // A second later nothing is left in the window of the previous ten.
        var at = 2_200L
        var shown = first
        while (at <= 3_200) {
            shown = flow.onSample(20f, 5f, at)
            at += 100
        }
        assertEquals(5f, shown, 0.01f)
    }

    @Test
    fun `without smoothing the number from the scale goes to the screen as it comes`() {
        val flow = FlowRate(smoothing = FlowSmoothing.NONE)
        pour(flow, gramsPerSecond = 10f, tickMs = 100, forMs = 2_000)
        assertEquals(5f, flow.onSample(20f, 5f, 2_100), 0.01f)
    }

    @Test
    fun `a spike does not drop the reading to zero`() {
        val flow = FlowRate()
        pour(flow, gramsPerSecond = 10f, tickMs = 100, forMs = 2_000)

        // The readings came back after a long dip: a hundred grams in one tick.
        assertTrue(flow.onSample(120f, null, 2_100) > 9f)

        // From there they pour as they poured, and a window later the rate is right again.
        assertEquals(
            10f,
            pour(flow, gramsPerSecond = 10f, tickMs = 100, forMs = 1_000, fromMs = 2_200, fromGrams = 121f),
            0.01f,
        )
    }

    @Test
    fun `a break does not give a surge`() {
        val flow = FlowRate()
        val before = pour(flow, gramsPerSecond = 10f, tickMs = 100, forMs = 2_000)

        // The pause was lifted a minute later: old samples have no place in the window.
        assertEquals(before, flow.onSample(20f, null, 62_000), 0.001f)
        assertEquals(before, flow.onSample(20.5f, null, 62_100), 0.001f)

        // We count from the new samples rather than from a weight a minute old.
        assertEquals(5f, flow.onSample(21.5f, null, 62_300), 0.01f)
    }

    @Test
    fun `after a pour the rate falls away to zero`() {
        val flow = FlowRate()
        pour(flow, gramsPerSecond = 10f, tickMs = 100, forMs = 2_000)

        // The kettle is closed: the weight stands. The tail of the pour stays in the smoothing
        // window, so the reading does not equal zero straight away.
        assertEquals(0f, pour(flow, gramsPerSecond = 0f, tickMs = 100, forMs = 2_500, fromMs = 2_100, fromGrams = 20f), 0.01f)
    }

    @Test
    fun `a reset forgets the previous brew`() {
        val flow = FlowRate()
        pour(flow, gramsPerSecond = 10f, tickMs = 100, forMs = 2_000)
        flow.reset()
        assertEquals(0f, flow.onSample(0f, null, 2_100), 0.001f)
    }
}
