package com.pourista.brew

import org.junit.Assert.assertEquals
import org.junit.Test

class MonotonicWeightTest {

    @Test
    fun `растущий вес проходит как есть`() {
        val weight = MonotonicWeight()
        assertEquals(0f, weight.onSample(0f, 0), 0.001f)
        assertEquals(50f, weight.onSample(50f, 100), 0.001f)
        assertEquals(120.5f, weight.onSample(120.5f, 200), 0.001f)
    }

    @Test
    fun `покачивание воронки не роняет вес и не даёт выброса при возврате`() {
        val weight = MonotonicWeight()
        weight.onSample(150f, 0)

        // Качнули: показания просели и через полсекунды вернулись.
        assertEquals(150f, weight.onSample(120f, 100), 0.001f)
        assertEquals(150f, weight.onSample(135f, 300), 0.001f)
        assertEquals(150f, weight.onSample(149f, 500), 0.001f)
        assertEquals(150f, weight.onSample(150f, 600), 0.001f)

        // Настоящий долив виден сразу.
        assertEquals(160f, weight.onSample(160f, 700), 0.001f)
    }

    @Test
    fun `затяжная просадка становится новой точкой отсчёта`() {
        val weight = MonotonicWeight()
        weight.onSample(200f, 0)

        // Нажали тару: вес упал и не возвращается.
        assertEquals(200f, weight.onSample(0f, 1_000), 0.001f)
        assertEquals(200f, weight.onSample(0f, 4_500), 0.001f)
        assertEquals(0f, weight.onSample(0f, 6_100), 0.001f)
        assertEquals(30f, weight.onSample(30f, 6_200), 0.001f)
    }

    @Test
    fun `свирл на четыре секунды просадкой не считается`() {
        val weight = MonotonicWeight()
        weight.onSample(600f, 0)

        // Воронку качают несколько секунд: показания гуляют, но возвращаются.
        assertEquals(600f, weight.onSample(380f, 500), 0.001f)
        assertEquals(600f, weight.onSample(420f, 2_000), 0.001f)
        assertEquals(600f, weight.onSample(390f, 3_500), 0.001f)
        assertEquals(600f, weight.onSample(600f, 4_200), 0.001f)
        // И следующий влив считается от прежнего максимума, а не от провала.
        assertEquals(610f, weight.onSample(610f, 4_500), 0.001f)
    }

    @Test
    fun `дрожь весов в пределах грамма не считается просадкой`() {
        val weight = MonotonicWeight()
        weight.onSample(100f, 0)
        assertEquals(100f, weight.onSample(99.4f, 5_000), 0.001f)
        assertEquals(100f, weight.onSample(99.4f, 10_000), 0.001f)
    }

    @Test
    fun `сброс возвращает отсчёт к нулю`() {
        val weight = MonotonicWeight()
        weight.onSample(250f, 0)
        weight.reset()
        assertEquals(5f, weight.onSample(5f, 100), 0.001f)
    }
}
