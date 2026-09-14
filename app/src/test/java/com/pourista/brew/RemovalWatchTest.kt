package com.pourista.brew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemovalWatchTest {

    /** Pour 250 g in the first seconds: after that the watch can be armed. */
    private fun RemovalWatch.pourUpTo(grams: Float, untilMs: Long = 0L): Long {
        var now = 0L
        var weight = 0f
        while (weight < grams) {
            weight += 10f
            now += 1_000L
            assertFalse(onSample(weight, now))
        }
        return maxOf(now, untilMs)
    }

    @Test
    fun `before the last pour a fall of the weight means nothing`() {
        val watch = RemovalWatch()
        var now = watch.pourUpTo(250f)

        // The cup was taken off, but the watch is not armed: the recipe still asks for water.
        repeat(10) {
            now += 1_000L
            assertFalse(watch.onSample(-300f, now))
        }
    }

    @Test
    fun `a lifted cup ends the brew three seconds later`() {
        val watch = RemovalWatch()
        var now = watch.pourUpTo(250f)
        watch.arm(250f)

        now += 1_000L
        assertFalse(watch.onSample(250f, now))

        // The cone was lifted: the weight fell by more than half.
        val droppedAt = now + 100L
        assertFalse(watch.onSample(60f, droppedAt))
        // Three seconds is not enough: that is how long a cone is swirled in the air.
        assertFalse(watch.onSample(60f, droppedAt + 3_000L))
        assertFalse(watch.onSample(60f, droppedAt + 4_900L))
        assertTrue(watch.onSample(60f, droppedAt + 5_000L))

        // The finish is placed at the moment of the fall, and the weight for the history is the
        // last normal one: what was taken off was an already finished cup.
        assertEquals(droppedAt, watch.droppedAtMs)
        assertEquals(250f, watch.weightBeforeDrop, 0.01f)
    }

    @Test
    fun `a cup lifted off whole sends the scale negative and counts as a finish too`() {
        val watch = RemovalWatch()
        var now = watch.pourUpTo(250f)
        watch.arm(250f)

        now += 500L
        assertFalse(watch.onSample(-420f, now))
        // A minus is believed sooner: that only happens when everything is taken off at once.
        assertFalse(watch.onSample(-420f, now + 2_900L))
        assertTrue(watch.onSample(-420f, now + 3_000L))
    }

    @Test
    fun `a short jerk of the scale does not end the brew`() {
        val watch = RemovalWatch()
        var now = watch.pourUpTo(250f)
        watch.arm(250f)

        now += 500L
        assertFalse(watch.onSample(10f, now))
        assertFalse(watch.onSample(10f, now + 1_500L))
        // The cup was put back — the count starts over.
        assertFalse(watch.onSample(248f, now + 2_000L))
        assertFalse(watch.onSample(10f, now + 2_500L))
        assertFalse(watch.onSample(10f, now + 6_000L))
        assertTrue(watch.onSample(10f, now + 7_500L))
    }

    @Test
    fun `on a large volume a lifted cone weighs less than half`() {
        val watch = RemovalWatch()
        var now = watch.pourUpTo(600f)
        watch.arm(600f)

        // A cone with soaked coffee is about a hundred grams out of six hundred: the weight will
        // never fall by half here, and the brew is over all the same.
        now += 1_000L
        val droppedAt = now
        assertFalse(watch.onSample(490f, droppedAt))
        assertTrue(watch.onSample(490f, droppedAt + 5_000L))
        assertEquals(600f, watch.weightBeforeDrop, 0.01f)
    }

    @Test
    fun `wobbling the cone on the scale does not count as a lift-off`() {
        val watch = RemovalWatch()
        var now = watch.pourUpTo(250f)
        watch.arm(250f)

        // Twenty grams back and forth is ordinary noise during a swirl.
        repeat(10) {
            now += 1_000L
            assertFalse(watch.onSample(if (it % 2 == 0) 232f else 250f, now))
        }
    }

    @Test
    fun `on a really light weight the watch keeps quiet`() {
        val watch = RemovalWatch()
        // Fifteen grams is still a dose rather than a brew: a couple of grams of scale noise must
        // not count as a lifted cup.
        assertFalse(watch.onSample(15f, 1_000L))
        watch.arm(15f)
        assertFalse(watch.onSample(1f, 2_000L))
        assertFalse(watch.onSample(1f, 10_000L))
    }

    @Test
    fun `a sunken weight is visible before the wait runs out`() {
        val watch = RemovalWatch()
        val now = watch.pourUpTo(600f)
        watch.arm(600f)
        assertFalse(watch.dropPending)

        // The cone was lifted a second ago: the wait is still running, but the fact of the fall is
        // already known — "Finish" by the button has to count the same way.
        assertFalse(watch.onSample(400f, now + 1_000L))
        assertTrue(watch.dropPending)
        assertEquals(600f, watch.weightBeforeDrop, 0.01f)
    }

    @Test
    fun `a fall before arming is remembered and counted out afterwards`() {
        val watch = RemovalWatch()
        val now = watch.pourUpTo(600f)

        // The cone was lifted before the recipe recognised the pour as finished.
        assertFalse(watch.onSample(400f, now + 1_000L))
        assertTrue("the fall is visible without arming too", watch.dropPending)
        assertEquals("the weight before the fall is remembered", 600f, watch.weightBeforeDrop, 0.01f)

        // Armed later — the count runs from the fall itself rather than from this moment.
        watch.arm(400f)
        assertEquals(600f, watch.weightBeforeDrop, 0.01f)
        assertTrue(watch.onSample(400f, now + 6_000L))
    }

    @Test
    fun `intermediate lift-off readings do not understate the weight in the history`() {
        val watch = RemovalWatch()
        var now = watch.pourUpTo(430f)
        watch.arm(430f)

        // After the kettle the weight settles slightly below the maximum and stays there.
        now += 1_000L
        assertFalse(watch.onSample(429.7f, now))

        // The scale does not give a lift-off in one jump: while the cone is being raised two or
        // three intermediate readings arrive. The first of them is still above the threshold, and it
        // used to be exactly the one that went into the history instead of what was poured.
        assertFalse(watch.onSample(403f, now + 100L))
        assertFalse(watch.onSample(180f, now + 200L))
        assertTrue(watch.onSample(150f, now + 5_200L))

        assertEquals(430f, watch.weightBeforeDrop, 0.01f)
    }

    @Test
    fun `a dip that holds does become the weight before the fall after all`() {
        val watch = RemovalWatch()
        var now = watch.pourUpTo(430f)
        watch.arm(430f)
        now += 1_000L
        assertFalse(watch.onSample(429.7f, now))

        // Not every fall inside the threshold is a lift-off: the cup could have been moved, and the
        // new weight holds one level. Fifteen seconds is no longer ripple, nor a descent on the way
        // down.
        repeat(15) {
            now += 1_000L
            assertFalse(watch.onSample(403f, now))
        }
        assertEquals(403f, watch.weightBeforeDrop, 0.01f)
    }

    @Test
    fun `after a long drawdown a lift-off does not understate the weight in the history`() {
        val watch = RemovalWatch()
        var now = 0L

        // The pour. The maximum is set on the stream: falling water adds a couple of grams, and
        // after the kettle the weight settles slightly lower. It will not come back to the maximum
        // itself — the whole drawdown the readings run within the tolerance.
        for (grams in listOf(45f, 100f, 148f, 200f, 251.4f)) {
            now += 30_000L
            assertFalse(watch.onSample(grams, now))
        }
        now += 1_000L
        assertFalse(watch.onSample(251.2f, now))
        watch.arm(251.2f)

        // One noisy packet below the tolerance and a minute of drawdown.
        now += 3_000L
        assertFalse(watch.onSample(249.9f, now))
        repeat(60) {
            now += 1_000L
            assertFalse(watch.onSample(251.0f, now))
        }

        // The cone was lifted. The first reading on the way down is still above the threshold — and
        // it used to be exactly the one that went into the history instead of what was poured.
        now += 100L
        assertFalse(watch.onSample(230f, now))
        assertEquals(251.4f, watch.weightBeforeDrop, 0.01f)

        val droppedAt = now + 100L
        assertFalse(watch.onSample(5f, droppedAt))
        assertTrue(watch.onSample(5f, droppedAt + 5_000L))
        assertEquals(251.4f, watch.weightBeforeDrop, 0.01f)
    }

    @Test
    fun `a jump upwards does not raise the lift-off threshold`() {
        val watch = RemovalWatch()
        var now = watch.pourUpTo(300f)
        watch.arm(300f)

        // The lid was pressed: one reading of 410.8 g with three hundred poured.
        now += 100L
        assertFalse(watch.onSample(410.8f, now))
        assertEquals("the threshold is counted from what is poured", 264f, watch.cutoffGrams, 0.1f)

        // The lid was let go. A real three hundred grams is not a fallen weight, and there is
        // nothing to close the brew on.
        repeat(30) {
            now += 200L
            assertFalse(watch.onSample(300f, now))
        }
        assertEquals(300f, watch.weightBeforeDrop, 0.01f)
    }

    @Test
    fun `a reset takes the watch off`() {
        val watch = RemovalWatch()
        val now = watch.pourUpTo(250f)
        watch.arm(250f)
        watch.reset()

        assertFalse(watch.armed)
        assertFalse(watch.onSample(-300f, now + 10_000L))
    }
}
