package com.pourista.brew

import com.pourista.data.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Запись рецепта проверить руками без весов нельзя, поэтому прогоняем через
 * детектор синтетический пролив V60 — такой, каким его видят весы: десять
 * отсчётов в секунду и лёгкое дрожание показаний.
 */
class PourRecorderTest {

    private class Trace {
        val recorder = PourRecorder()
        private var elapsedMs = 0L
        private var weight = 0f

        /** Льём [grams] за [seconds] секунд. */
        fun pour(grams: Float, seconds: Int) {
            val perTick = grams / (seconds * TICKS_PER_SECOND)
            repeat(seconds * TICKS_PER_SECOND) {
                weight += perTick
                tick()
            }
        }

        /** Ждём, вес стоит и слегка дрожит, как на реальных весах. */
        fun wait(seconds: Int) {
            repeat(seconds * TICKS_PER_SECOND) { index ->
                val jitter = if (index % 2 == 0) 0.1f else -0.1f
                weight += jitter
                tick()
            }
        }

        private fun tick() {
            recorder.onSample(elapsedMs, weight)
            elapsedMs += 100
        }

        val totalMs: Long get() = elapsedMs
    }

    private companion object {
        const val TICKS_PER_SECOND = 10
    }

    @Test
    fun `три пролива с паузами распознаются как три шага`() {
        val trace = Trace()
        trace.pour(grams = 50f, seconds = 10)
        trace.wait(seconds = 35)
        trace.pour(grams = 100f, seconds = 20)
        trace.wait(seconds = 10)
        trace.pour(grams = 100f, seconds = 20)
        trace.wait(seconds = 85)

        assertEquals("должно быть три пролива", 3, trace.recorder.pourCount)

        val recipe = trace.recorder.buildRecipe(
            name = "Запись",
            brewer = "",
            doseGrams = 15f,
            totalElapsedMs = trace.totalMs,
            waterTempC = 94,
        )!!

        // Три пролива плюс слив: после последнего влива вода ещё уходит.
        assertEquals(4, recipe.steps.size)
        assertEquals("первый влив — блуминг", StepKind.BLOOM, recipe.steps.first().kind)
        assertEquals("последний шаг — слив", StepKind.DRAWDOWN, recipe.steps.last().kind)
        val poured = recipe.steps.filter { it.kind.isPour }
        assertEquals(3, poured.size)
        assertTrue("между блумингом и сливом обычные проливы", poured.drop(1).all { it.kind == StepKind.POUR })

        // Цели округлены до пяти граммов и накопительны, слив держит последнюю.
        assertEquals(listOf(50f, 150f, 250f, 250f), recipe.steps.map { it.targetWaterGrams })
        assertEquals(250f, recipe.waterGrams, 0.01f)

        // Время начала шагов округлено до пяти секунд, первый всегда с нуля.
        assertEquals(listOf(0, 45, 75, 95), recipe.steps.map { it.startSec })

        // Скорость округлена до 1 г/с: 50 г за 10 с и 100 г за 20 с — это 5 г/с.
        assertEquals(listOf(5f, 5f, 5f), poured.map { it.pourFlowRate })
    }

    @Test
    fun `шаги идут подряд и покрывают всё заваривание`() {
        val trace = Trace()
        trace.pour(grams = 60f, seconds = 15)
        trace.wait(seconds = 30)
        trace.pour(grams = 60f, seconds = 10)
        trace.wait(seconds = 35)

        val recipe = trace.recorder.buildRecipe(
            name = "Запись",
            brewer = "",
            doseGrams = 20f,
            totalElapsedMs = trace.totalMs,
            waterTempC = 94,
        )!!

        var expectedStart = 0
        recipe.steps.forEach { step ->
            assertEquals("шаги должны идти без разрывов", expectedStart, step.startSec)
            expectedStart += step.durationSec
        }
        assertTrue("последний шаг доводит до конца заваривания", expectedStart >= 85)
    }

    @Test
    fun `дрожание весов без пролива не создаёт шагов`() {
        val trace = Trace()
        trace.wait(seconds = 60)

        assertEquals(0, trace.recorder.pourCount)
        assertNull(
            trace.recorder.buildRecipe("Запись", "", 15f, trace.totalMs, 94),
        )
    }

    @Test
    fun `последний влив кончается там, где перестал расти вес`() {
        val trace = Trace()
        trace.pour(grams = 100f, seconds = 20)
        trace.wait(seconds = 100)

        val recipe = trace.recorder.buildRecipe("Запись", "", 15f, trace.totalMs, 94)!!

        assertEquals(2, recipe.steps.size)
        val pour = recipe.steps.first()
        val drawdown = recipe.steps.last()
        assertEquals(StepKind.DRAWDOWN, drawdown.kind)
        assertEquals("слив начинается сразу после влива", pour.endSec, drawdown.startSec)
        assertTrue("слив занимает всё оставшееся время", drawdown.durationSec >= 90)
    }

    @Test
    fun `незакрытый пролив всё равно попадает в рецепт`() {
        val trace = Trace()
        trace.pour(grams = 40f, seconds = 10)

        val recipe = trace.recorder.buildRecipe("Запись", "", 15f, trace.totalMs, 94)!!

        assertEquals(1, recipe.steps.size)
        assertEquals(40f, recipe.steps.first().targetWaterGrams, 0.01f)
    }
}
