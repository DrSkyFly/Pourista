package com.pourista.brew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextPourHintTest {

    @Test
    fun `matching rates do not count as different`() {
        assertEquals(NextPourHint.SAME, compareNextPour(lastFlowRate = 5f, nextFlowRate = 5f))
        // Eight percent is within the default tolerance, nothing worth mentioning.
        assertEquals(NextPourHint.SAME, compareNextPour(lastFlowRate = 5f, nextFlowRate = 5.4f))
        assertEquals(NextPourHint.SAME, compareNextPour(lastFlowRate = 5f, nextFlowRate = 4.6f))
    }

    @Test
    fun `the default tolerance is ten percent`() {
        assertEquals(NextPourHint.FASTER, compareNextPour(lastFlowRate = 5f, nextFlowRate = 5.6f))
        assertEquals(NextPourHint.SLOWER, compareNextPour(lastFlowRate = 5f, nextFlowRate = 4.4f))
    }

    @Test
    fun `the tolerance is set from outside`() {
        // The same case under a wide tolerance is not worth mentioning.
        assertEquals(
            NextPourHint.SAME,
            compareNextPour(lastFlowRate = 5f, nextFlowRate = 5.6f, tolerance = 0.3f),
        )
    }

    @Test
    fun `a noticeable difference turns into a hint`() {
        assertEquals(NextPourHint.FASTER, compareNextPour(lastFlowRate = 4f, nextFlowRate = 6f))
        assertEquals(NextPourHint.SLOWER, compareNextPour(lastFlowRate = 6f, nextFlowRate = 4f))
    }

    @Test
    fun `without a measured rate there is no hint`() {
        assertNull(compareNextPour(lastFlowRate = 0f, nextFlowRate = 5f))
        assertNull(compareNextPour(lastFlowRate = 5f, nextFlowRate = null))
        assertNull(compareNextPour(lastFlowRate = 5f, nextFlowRate = 0f))
    }
}
