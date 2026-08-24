package com.pourista.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Цена деления должна быть привычной: вес — полусотнями, скорость — по 2,5 г/с.
 */
class ChartAxisTest {

    private fun step(axis: Pair<Float, Int>) = axis.first / axis.second

    @Test
    fun `вес на обычной чашке идёт полусотнями`() {
        val axis = chartAxis(rawMax = 250f)

        assertEquals(50f, step(axis), 0.01f)
        assertEquals(300f, axis.first, 0.01f)
        assertEquals(6, axis.second)
    }

    @Test
    fun `на большом объёме шаг растёт, чтобы линии не слились`() {
        val axis = chartAxis(rawMax = 600f)

        assertEquals(100f, step(axis), 0.01f)
        assertEquals(700f, axis.first, 0.01f)
    }

    @Test
    fun `четверти сотни в делениях не бывает`() {
        listOf(80f, 150f, 250f, 360f, 600f, 1000f).forEach { max ->
            val step = step(chartAxis(rawMax = max))
            assertEquals("шаг для $max должен быть кратен 50", 0f, step % 50f, 0.01f)
        }
    }

    @Test
    fun `скорость размечается по два с половиной`() {
        val axis = chartAxis(rawMax = 10f, steps = FLOW_AXIS_STEPS)

        assertEquals(2.5f, step(axis), 0.01f)
        assertEquals(12.5f, axis.first, 0.01f)
    }

    @Test
    fun `выброс скорости не превращает сетку в частокол`() {
        val axis = chartAxis(rawMax = 90f, steps = FLOW_AXIS_STEPS)

        assert(axis.second <= 7) { "делений вышло ${axis.second}" }
    }

    @Test
    fun `пустой ряд не ломает ось`() {
        val axis = chartAxis(rawMax = 0f)

        assertEquals(50f, axis.first, 0.01f)
        assertEquals(1, axis.second)
    }
}
