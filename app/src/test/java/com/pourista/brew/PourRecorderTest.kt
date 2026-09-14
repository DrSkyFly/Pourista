package com.pourista.brew

import com.pourista.data.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recording a recipe cannot be checked by hand without a scale, so we run a synthetic V60 pour
 * through the detector — the way a scale sees it: ten samples a second and a slight shiver of the
 * readings.
 */
class PourRecorderTest {

    private class Trace {
        val recorder = PourRecorder()
        private var elapsedMs = 0L
        private var weight = 0f

        /** Pour [grams] over [seconds] seconds. */
        fun pour(grams: Float, seconds: Int) {
            val perTick = grams / (seconds * TICKS_PER_SECOND)
            repeat(seconds * TICKS_PER_SECOND) {
                weight += perTick
                tick()
            }
        }

        /** Wait, the weight stands and shivers slightly, as on a real scale. */
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
    fun `three pours with pauses are recognised as three steps`() {
        val trace = Trace()
        trace.pour(grams = 50f, seconds = 10)
        trace.wait(seconds = 35)
        trace.pour(grams = 100f, seconds = 20)
        trace.wait(seconds = 10)
        trace.pour(grams = 100f, seconds = 20)
        trace.wait(seconds = 85)

            assertEquals("there should be three pours", 3, trace.recorder.pourCount)

        val recipe = trace.recorder.buildRecipe(
            name = "Recording",
            brewer = "",
            doseGrams = 15f,
            totalElapsedMs = trace.totalMs,
            waterTempC = 94,
        )!!

        // Three pours plus the drawdown: after the last pour the water is still leaving.
        assertEquals(4, recipe.steps.size)
        assertEquals("the first pour is the bloom", StepKind.BLOOM, recipe.steps.first().kind)
        assertEquals("the last step is the drawdown", StepKind.DRAWDOWN, recipe.steps.last().kind)
        val poured = recipe.steps.filter { it.kind.isPour }
        assertEquals(3, poured.size)
        assertTrue("between the bloom and the drawdown are ordinary pours", poured.drop(1).all { it.kind == StepKind.POUR })

        // The targets are rounded to five grams and cumulative, the drawdown holds the last one.
        assertEquals(listOf(50f, 150f, 250f, 250f), recipe.steps.map { it.targetWaterGrams })
        assertEquals(250f, recipe.waterGrams, 0.01f)

        // The start times of the steps are rounded to five seconds, the first is always at zero.
        assertEquals(listOf(0, 45, 75, 95), recipe.steps.map { it.startSec })

        // The rate is rounded to 1 g/s: 50 g in 10 s and 100 g in 20 s are both 5 g/s.
        assertEquals(listOf(5f, 5f, 5f), poured.map { it.pourFlowRate })
    }

    @Test
    fun `the steps run one after another and cover the whole brew`() {
        val trace = Trace()
        trace.pour(grams = 60f, seconds = 15)
        trace.wait(seconds = 30)
        trace.pour(grams = 60f, seconds = 10)
        trace.wait(seconds = 35)

        val recipe = trace.recorder.buildRecipe(
            name = "Recording",
            brewer = "",
            doseGrams = 20f,
            totalElapsedMs = trace.totalMs,
            waterTempC = 94,
        )!!

        var expectedStart = 0
        recipe.steps.forEach { step ->
            assertEquals("the steps must run without gaps", expectedStart, step.startSec)
            expectedStart += step.durationSec
        }
        assertTrue("the last step carries through to the end of the brew", expectedStart >= 85)
    }

    @Test
    fun `a shiver of the scale with no pour creates no steps`() {
        val trace = Trace()
        trace.wait(seconds = 60)

        assertEquals(0, trace.recorder.pourCount)
        assertNull(
            trace.recorder.buildRecipe("Recording", "", 15f, trace.totalMs, 94),
        )
    }

    @Test
    fun `the last pour ends where the weight stopped growing`() {
        val trace = Trace()
        trace.pour(grams = 100f, seconds = 20)
        trace.wait(seconds = 100)

        val recipe = trace.recorder.buildRecipe("Recording", "", 15f, trace.totalMs, 94)!!

        assertEquals(2, recipe.steps.size)
        val pour = recipe.steps.first()
        val drawdown = recipe.steps.last()
        assertEquals(StepKind.DRAWDOWN, drawdown.kind)
        assertEquals("the drawdown starts right after the pour", pour.endSec, drawdown.startSec)
        assertTrue("the drawdown takes all the time that is left", drawdown.durationSec >= 90)
    }

    @Test
    fun `an unclosed pour still gets into the recipe`() {
        val trace = Trace()
        trace.pour(grams = 40f, seconds = 10)

        val recipe = trace.recorder.buildRecipe("Recording", "", 15f, trace.totalMs, 94)!!

        assertEquals(1, recipe.steps.size)
        assertEquals(40f, recipe.steps.first().targetWaterGrams, 0.01f)
    }
}
