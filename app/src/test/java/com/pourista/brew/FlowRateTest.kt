package com.pourista.brew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowRateTest {

    /** Ровный влив: [gramsPerSecond] г/с тиками по [tickMs] мс. */
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
    fun `ровный влив даёт свою скорость`() {
        assertEquals(10f, pour(FlowRate(), gramsPerSecond = 10f, tickMs = 100, forMs = 3_000), 0.01f)
        assertEquals(4.5f, pour(FlowRate(), gramsPerSecond = 4.5f, tickMs = 100, forMs = 3_000), 0.01f)
    }

    @Test
    fun `плывущий тик не завышает скорость`() {
        // Тик обещает 100 мс, но delay даёт «не меньше». Прежний расчёт считал
        // десять тиков секундой и на тике в 130 мс завышал скорость на четверть.
        assertEquals(10f, pour(FlowRate(), gramsPerSecond = 10f, tickMs = 130, forMs = 3_000), 0.01f)
        assertEquals(10f, pour(FlowRate(), gramsPerSecond = 10f, tickMs = 250, forMs = 3_000), 0.01f)
    }

    @Test
    fun `в начале влива скорость не занижена`() {
        val flow = FlowRate()
        // Пока окно короче трёх десятых секунды, скорости просто нет.
        assertEquals(0f, flow.onSample(0f, null, 0), 0.001f)
        assertEquals(0f, flow.onSample(1f, null, 100), 0.001f)
        assertEquals(0f, flow.onSample(2f, null, 200), 0.001f)
        // А как только окно набралось — сразу правда, а не треть от неё.
        assertEquals(10f, flow.onSample(3f, null, 300), 0.01f)
    }

    @Test
    fun `число весов важнее своей оценки`() {
        val flow = FlowRate(smoothing = FlowSmoothing.NONE)
        pour(flow, gramsPerSecond = 10f, tickMs = 100, forMs = 1_000)

        // Весы посчитали сами — берём их число.
        assertEquals(6.5f, flow.onSample(11f, 6.5f, 1_100), 0.01f)
        // Обратный ход у них уходит в минус, влив он не значит.
        assertEquals(0f, flow.onSample(12f, -3f, 1_200), 0.01f)
        // Потолок весов на резком изменении веса — не скорость влива.
        assertEquals(10f, flow.onSample(13f, 99.9f, 1_300), 0.01f)
    }

    @Test
    fun `число весов сглаживается наравне со своей оценкой`() {
        val flow = FlowRate(smoothing = FlowSmoothing.NORMAL)
        pour(flow, gramsPerSecond = 10f, tickMs = 100, forMs = 2_000)

        // Весы показали вдвое меньше: до 1.9.2 это уходило на экран как есть,
        // и цифра прыгала с каждым пакетом. Теперь показание идёт к новому
        // числу постепенно.
        val first = flow.onSample(20f, 5f, 2_100)
        assertTrue(first > 9f)
        assertTrue(first < 10f)

        // Секунду спустя от прежних десяти в окне ничего не остаётся.
        var at = 2_200L
        var shown = first
        while (at <= 3_200) {
            shown = flow.onSample(20f, 5f, at)
            at += 100
        }
        assertEquals(5f, shown, 0.01f)
    }

    @Test
    fun `без сглаживания число весов идёт на экран как есть`() {
        val flow = FlowRate(smoothing = FlowSmoothing.NONE)
        pour(flow, gramsPerSecond = 10f, tickMs = 100, forMs = 2_000)
        assertEquals(5f, flow.onSample(20f, 5f, 2_100), 0.01f)
    }

    @Test
    fun `выброс не роняет показание в ноль`() {
        val flow = FlowRate()
        pour(flow, gramsPerSecond = 10f, tickMs = 100, forMs = 2_000)

        // Вернулись показания после долгой просадки: сотня граммов за тик.
        assertTrue(flow.onSample(120f, null, 2_100) > 9f)

        // Дальше льют как лили, и через окно скорость снова верна.
        assertEquals(
            10f,
            pour(flow, gramsPerSecond = 10f, tickMs = 100, forMs = 1_000, fromMs = 2_200, fromGrams = 121f),
            0.01f,
        )
    }

    @Test
    fun `перерыв не даёт всплеска`() {
        val flow = FlowRate()
        val before = pour(flow, gramsPerSecond = 10f, tickMs = 100, forMs = 2_000)

        // Паузу сняли через минуту: старым отсчётам в окне не место.
        assertEquals(before, flow.onSample(20f, null, 62_000), 0.001f)
        assertEquals(before, flow.onSample(20.5f, null, 62_100), 0.001f)

        // Считаем от новых отсчётов, а не от веса минутной давности.
        assertEquals(5f, flow.onSample(21.5f, null, 62_300), 0.01f)
    }

    @Test
    fun `после влива скорость спадает к нулю`() {
        val flow = FlowRate()
        pour(flow, gramsPerSecond = 10f, tickMs = 100, forMs = 2_000)

        // Чайник закрыли: вес стоит. Хвост влива держится в окне сглаживания,
        // поэтому нулю показание равно не сразу.
        assertEquals(0f, pour(flow, gramsPerSecond = 0f, tickMs = 100, forMs = 2_500, fromMs = 2_100, fromGrams = 20f), 0.01f)
    }

    @Test
    fun `сброс забывает прошлое заваривание`() {
        val flow = FlowRate()
        pour(flow, gramsPerSecond = 10f, tickMs = 100, forMs = 2_000)
        flow.reset()
        assertEquals(0f, flow.onSample(0f, null, 2_100), 0.001f)
    }
}
