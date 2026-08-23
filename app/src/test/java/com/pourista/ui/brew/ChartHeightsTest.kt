package com.pourista.ui.brew

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Высоты графиков под разные экраны: от планшета до короткого телефона.
 */
class ChartHeightsTest {

    @Test
    fun `на высоком экране графики в полный рост`() {
        val (weight, flow) = chartHeights(available = 800.dp)

        assertEquals(140.dp, weight)
        assertEquals(72.dp, flow)
    }

    @Test
    fun `на коротком экране ужимается вес, скорость остаётся`() {
        val (weight, flow) = chartHeights(available = 560.dp)

        assertEquals("скорость не трогаем", 72.dp, flow)
        assertTrue("вес ужат", weight < 140.dp)
        assertTrue("но не ниже скорости", weight >= flow)
    }

    @Test
    fun `на совсем коротком экране делим поровну`() {
        val (weight, flow) = chartHeights(available = 480.dp)

        assertEquals("ниже равенства не опускаемся", weight, flow)
        assertTrue("оба ужаты", weight < 140.dp)
    }

    @Test
    fun `меньше нижнего предела графики не становятся`() {
        val (weight, flow) = chartHeights(available = 200.dp)

        assertEquals(48.dp, weight)
        assertEquals(48.dp, flow)
    }
}
