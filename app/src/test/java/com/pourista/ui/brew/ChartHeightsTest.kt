package com.pourista.ui.brew

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chart heights for different screens: from a tablet to a short phone.
 */
class ChartHeightsTest {

    @Test
    fun `on a tall screen the charts stand at full height`() {
        val (weight, flow) = chartHeights(available = 800.dp)

        assertEquals(140.dp, weight)
        assertEquals(72.dp, flow)
    }

    @Test
    fun `on a short screen the weight is squeezed and the flow rate stays`() {
        val (weight, flow) = chartHeights(available = 560.dp)

        assertEquals("the flow rate is left alone", 72.dp, flow)
        assertTrue("the weight is squeezed", weight < 140.dp)
        assertTrue("but no lower than the flow rate", weight >= flow)
    }

    @Test
    fun `on a really short screen we split evenly`() {
        val (weight, flow) = chartHeights(available = 480.dp)

        assertEquals("we do not go below equality", weight, flow)
        assertTrue("both are squeezed", weight < 140.dp)
    }

    @Test
    fun `the charts do not go below the lower limit`() {
        val (weight, flow) = chartHeights(available = 200.dp)

        assertEquals(64.dp, weight)
        assertEquals(64.dp, flow)
    }
}
