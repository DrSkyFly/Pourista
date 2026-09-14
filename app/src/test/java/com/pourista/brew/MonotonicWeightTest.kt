package com.pourista.brew

import org.junit.Assert.assertEquals
import org.junit.Test

class MonotonicWeightTest {

    @Test
    fun `a growing weight passes as it is`() {
        val weight = MonotonicWeight()
        assertEquals(0f, weight.onSample(0f, 100), 0.001f)
        assertEquals(50f, weight.onSample(50f, 5_100), 0.001f)
        assertEquals(120.5f, weight.onSample(120.5f, 12_100), 0.001f)
    }

    @Test
    fun `a press on the cone does not count towards the water`() {
        val weight = MonotonicWeight()
        var now = 0L
        var raw = 0f
        // The pour: a gram per reading, which is ten grams a second.
        repeat(300) {
            now += 100L
            raw += 1f
            assertEquals(raw, weight.onSample(raw, now), 0.001f)
        }

        // The lid was pressed: a hundred and ten grams in a single reading. Nobody pours that
        // much water in a tenth of a second.
        now += 100L
        assertEquals(300f, weight.onSample(410.8f, now), 0.001f)

        // The lid was let go — nothing had changed in the calculations anyway.
        now += 100L
        assertEquals(300f, weight.onSample(300f, now), 0.001f)
        now += 100L
        assertEquals(301f, weight.onSample(301f, now), 0.001f)
    }

    @Test
    fun `a hand on the cone does not live to the end of the wait`() {
        val weight = MonotonicWeight()
        var now = 1_000L
        weight.onSample(300f, now)

        // Six seconds with a hand on the cone: the weight is high, but not a single reading
        // stands still.
        repeat(8) {
            listOf(360f, 402f, 411f, 405f, 398f, 407f, 412f, 399f).forEach { grams ->
                now += 100L
                assertEquals(300f, weight.onSample(grams, now), 0.001f)
            }
        }
    }

    @Test
    fun `a weight that holds up there does become the maximum after all`() {
        val weight = MonotonicWeight()
        weight.onSample(100f, 1_000)

        // The link to the scale was lost, and the reading came back large at once.
        assertEquals(100f, weight.onSample(300f, 1_100), 0.001f)
        assertEquals(100f, weight.onSample(300f, 4_000), 0.001f)
        assertEquals(300f, weight.onSample(300f, 6_100), 0.001f)
    }

    @Test
    fun `wobbling the cone neither drops the weight nor gives a spike on the return`() {
        val weight = MonotonicWeight()
        weight.onSample(150f, 0)

        // Nudged: the readings sank and came back half a second later.
        assertEquals(150f, weight.onSample(120f, 100), 0.001f)
        assertEquals(150f, weight.onSample(135f, 300), 0.001f)
        assertEquals(150f, weight.onSample(149f, 500), 0.001f)
        assertEquals(150f, weight.onSample(150f, 600), 0.001f)

        // A real addition shows at once.
        assertEquals(160f, weight.onSample(160f, 700), 0.001f)
    }

    @Test
    fun `a lasting dip becomes the new baseline`() {
        val weight = MonotonicWeight()
        weight.onSample(200f, 0)

        // The tare was pressed: the weight fell and does not come back.
        assertEquals(200f, weight.onSample(0f, 1_000), 0.001f)
        assertEquals(200f, weight.onSample(0f, 4_500), 0.001f)
        assertEquals(0f, weight.onSample(0f, 6_100), 0.001f)
        assertEquals(1f, weight.onSample(1f, 6_200), 0.001f)
        assertEquals(30f, weight.onSample(30f, 9_000), 0.001f)
    }

    @Test
    fun `a four-second swirl does not count as a dip`() {
        val weight = MonotonicWeight()
        weight.onSample(600f, 0)

        // The cone is swirled for a few seconds: the readings wander but come back.
        assertEquals(600f, weight.onSample(380f, 500), 0.001f)
        assertEquals(600f, weight.onSample(420f, 2_000), 0.001f)
        assertEquals(600f, weight.onSample(390f, 3_500), 0.001f)
        assertEquals(600f, weight.onSample(600f, 4_200), 0.001f)
        // And the next pour is counted from the previous maximum rather than from the sag.
        assertEquals(610f, weight.onSample(610f, 4_500), 0.001f)
    }

    @Test
    fun `a shiver of the scale within a gram does not count as a dip`() {
        val weight = MonotonicWeight()
        weight.onSample(100f, 0)
        assertEquals(100f, weight.onSample(99.4f, 5_000), 0.001f)
        assertEquals(100f, weight.onSample(99.4f, 10_000), 0.001f)
    }

    @Test
    fun `coming back inside the tolerance cancels the dip countdown`() {
        val weight = MonotonicWeight()

        // The maximum is set on the stream: falling water adds a couple of grams, and after the
        // kettle the weight settles slightly lower. It will never come back to the maximum
        // itself — but that is not a dip.
        weight.onSample(251.4f, 0)
        assertEquals(251.4f, weight.onSample(251.2f, 1_000), 0.001f)

        // One noisy packet below the tolerance — the countdown has started.
        assertEquals(251.4f, weight.onSample(249.9f, 4_000), 0.001f)

        // A minute of drawdown within the tolerance. The countdown must come off meanwhile:
        // otherwise it would live to the lifted cone already expired.
        var now = 5_000L
        repeat(60) {
            now += 1_000L
            assertEquals(251.4f, weight.onSample(251.0f, now), 0.001f)
        }

        // The cone was lifted. The first reading on the way down is not the new truth.
        assertEquals(251.4f, weight.onSample(230f, now + 100), 0.001f)
    }

    @Test
    fun `a reading on the way down does not become the new truth`() {
        val weight = MonotonicWeight()
        weight.onSample(251.4f, 0)

        // The cone is being raised: the scale gives the descent in a dozen packets a second.
        // Not one of them holds down there, and none of them can be the truth.
        assertEquals(251.4f, weight.onSample(230f, 60_000), 0.001f)
        assertEquals(251.4f, weight.onSample(180f, 60_100), 0.001f)
        assertEquals(251.4f, weight.onSample(90f, 60_200), 0.001f)
        assertEquals(251.4f, weight.onSample(5f, 60_300), 0.001f)

        // While a lifted cone does hold down there — and that is the new baseline.
        assertEquals(251.4f, weight.onSample(5f, 64_000), 0.001f)
        assertEquals(5f, weight.onSample(5f, 65_400), 0.001f)
    }

    @Test
    fun `a reset brings the count back to zero`() {
        val weight = MonotonicWeight()
        weight.onSample(250f, 0)
        weight.reset()
        assertEquals(5f, weight.onSample(5f, 100), 0.001f)
    }
}
