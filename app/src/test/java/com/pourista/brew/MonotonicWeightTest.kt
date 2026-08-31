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
    fun `возврат в допуск отменяет отсчёт просадки`() {
        val weight = MonotonicWeight()

        // Максимум ставится на струе: падающая вода добавляет пару граммов, и
        // после чайника вес садится чуть ниже. К самому максимуму он больше
        // никогда не вернётся — но это не просадка.
        weight.onSample(251.4f, 0)
        assertEquals(251.4f, weight.onSample(251.2f, 1_000), 0.001f)

        // Одна шумная посылка ниже допуска — отсчёт пошёл.
        assertEquals(251.4f, weight.onSample(249.9f, 4_000), 0.001f)

        // Минута слива в пределах допуска. Отсчёт при этом должен сняться:
        // иначе он доживёт до снятия воронки уже истёкшим.
        var now = 5_000L
        repeat(60) {
            now += 1_000L
            assertEquals(251.4f, weight.onSample(251.0f, now), 0.001f)
        }

        // Воронку сняли. Первое показание на пути вниз — не новая правда.
        assertEquals(251.4f, weight.onSample(230f, now + 100), 0.001f)
    }

    @Test
    fun `показание на пути вниз новой правдой не становится`() {
        val weight = MonotonicWeight()
        weight.onSample(251.4f, 0)

        // Воронку поднимают: весы отдают спуск десятком посылок за секунду.
        // Ни одна из них внизу не держится, и правдой быть не может.
        assertEquals(251.4f, weight.onSample(230f, 60_000), 0.001f)
        assertEquals(251.4f, weight.onSample(180f, 60_100), 0.001f)
        assertEquals(251.4f, weight.onSample(90f, 60_200), 0.001f)
        assertEquals(251.4f, weight.onSample(5f, 60_300), 0.001f)

        // А снятая воронка внизу держится — вот это уже новая точка отсчёта.
        assertEquals(251.4f, weight.onSample(5f, 64_000), 0.001f)
        assertEquals(5f, weight.onSample(5f, 65_400), 0.001f)
    }

    @Test
    fun `сброс возвращает отсчёт к нулю`() {
        val weight = MonotonicWeight()
        weight.onSample(250f, 0)
        weight.reset()
        assertEquals(5f, weight.onSample(5f, 100), 0.001f)
    }
}
