package com.pourista.brew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextPourHintTest {

    @Test
    fun `совпадающие скорости не считаются разными`() {
        assertEquals(NextPourHint.SAME, compareNextPour(lastFlowRate = 5f, nextFlowRate = 5f))
        // Восемь процентов — в пределах допуска по умолчанию, упоминать не о чем.
        assertEquals(NextPourHint.SAME, compareNextPour(lastFlowRate = 5f, nextFlowRate = 5.4f))
        assertEquals(NextPourHint.SAME, compareNextPour(lastFlowRate = 5f, nextFlowRate = 4.6f))
    }

    @Test
    fun `по умолчанию допуск десять процентов`() {
        assertEquals(NextPourHint.FASTER, compareNextPour(lastFlowRate = 5f, nextFlowRate = 5.6f))
        assertEquals(NextPourHint.SLOWER, compareNextPour(lastFlowRate = 5f, nextFlowRate = 4.4f))
    }

    @Test
    fun `допуск задаётся снаружи`() {
        // Тот же случай при широком допуске упоминания не стоит.
        assertEquals(
            NextPourHint.SAME,
            compareNextPour(lastFlowRate = 5f, nextFlowRate = 5.6f, tolerance = 0.3f),
        )
    }

    @Test
    fun `заметная разница превращается в подсказку`() {
        assertEquals(NextPourHint.FASTER, compareNextPour(lastFlowRate = 4f, nextFlowRate = 6f))
        assertEquals(NextPourHint.SLOWER, compareNextPour(lastFlowRate = 6f, nextFlowRate = 4f))
    }

    @Test
    fun `без измеренной скорости подсказки нет`() {
        assertNull(compareNextPour(lastFlowRate = 0f, nextFlowRate = 5f))
        assertNull(compareNextPour(lastFlowRate = 5f, nextFlowRate = null))
        assertNull(compareNextPour(lastFlowRate = 5f, nextFlowRate = 0f))
    }
}
