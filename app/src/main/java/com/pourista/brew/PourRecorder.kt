package com.pourista.brew

import com.pourista.data.model.Recipe
import com.pourista.data.model.RecipeStep
import com.pourista.data.model.StepKind
import kotlin.math.round

/** A recognised pour: from which second to which, and from which weight to which. */
internal data class PourSegment(
    val startMs: Long,
    val endMs: Long,
    val startWeight: Float,
    val endWeight: Float,
)

/**
 * Splits a stream of weight into pours and pauses, so that a live brew turns into a
 * recipe. A pour starts where the weight goes up and ends where it stops growing.
 *
 * The numbers are rounded: weight to 5 g, time to 5 s, rate to 1 g/s. Writing down
 * "to 148.3 g in 27 seconds" is pointless — that cannot be reproduced anyway.
 */
internal class PourRecorder {

    private val segments = mutableListOf<PourSegment>()

    private var pouring = false
    private var segmentStartMs = 0L
    private var segmentStartWeight = 0f
    private var riseCandidateMs = 0L
    private var restWeight = 0f
    private var peakWeight = 0f
    private var peakAtMs = 0L

    val pourCount: Int get() = segments.size

    fun reset() {
        segments.clear()
        pouring = false
        segmentStartMs = 0L
        segmentStartWeight = 0f
        riseCandidateMs = 0L
        restWeight = 0f
        peakWeight = 0f
        peakAtMs = 0L
    }

    fun onSample(elapsedMs: Long, weight: Float) {
        if (!pouring) {
            val rise = weight - restWeight
            if (rise >= RISE_EPSILON) {
                if (riseCandidateMs == 0L) riseCandidateMs = elapsedMs
                if (rise >= POUR_START_GRAMS) {
                    pouring = true
                    segmentStartMs = riseCandidateMs
                    segmentStartWeight = restWeight
                    peakWeight = weight
                    peakAtMs = elapsedMs
                }
            } else {
                riseCandidateMs = 0L
                if (weight < restWeight) restWeight = weight
            }
            return
        }

        if (weight > peakWeight + RISE_EPSILON) {
            peakWeight = weight
            peakAtMs = elapsedMs
            return
        }
        if (elapsedMs - peakAtMs >= POUR_STOP_MS) {
            segments += PourSegment(segmentStartMs, peakAtMs, segmentStartWeight, peakWeight)
            pouring = false
            riseCandidateMs = 0L
            restWeight = peakWeight
        }
    }

    /** An unclosed pour goes into the recipe too: "Finish" may have been pressed right after topping up. */
    private fun allSegments(): List<PourSegment> {
        if (!pouring || peakWeight <= segmentStartWeight) return segments
        return segments + PourSegment(segmentStartMs, peakAtMs, segmentStartWeight, peakWeight)
    }

    fun buildRecipe(
        name: String,
        brewer: String,
        doseGrams: Float,
        totalElapsedMs: Long,
        waterTempC: Int,
    ): Recipe? {
        val pours = allSegments()
        if (pours.isEmpty()) return null

        val totalSec = roundSeconds(totalElapsedMs / 1000f)
        val starts = pours.mapIndexed { index, segment ->
            if (index == 0) 0 else roundSeconds(segment.startMs / 1000f)
        }

        // The last pour ends where the weight stopped growing, and the time left over is
        // the drawdown: the water is leaving, there is nothing more to pour.
        val lastStart = starts.last()
        val lastPour = pours.last()
        val lastPourEnd = maxOf(
            roundSeconds(lastPour.endMs / 1000f),
            lastStart + TIME_STEP_SEC,
        )
        val drawdownSec = totalSec - lastPourEnd

        val steps = pours.mapIndexed { index, segment ->
            val start = starts[index]
            val end = starts.getOrNull(index + 1)
                ?: if (drawdownSec >= TIME_STEP_SEC) lastPourEnd else maxOf(totalSec, start + TIME_STEP_SEC)
            val poured = (segment.endWeight - segment.startWeight).coerceAtLeast(0f)
            val seconds = ((segment.endMs - segment.startMs) / 1000f).coerceAtLeast(1f)
            RecipeStep(
                // The first pour onto the cone is the bloom: it has its own place in a
                // recipe, and a recorded pour must fall the same way.
                kind = if (index == 0) StepKind.BLOOM else StepKind.POUR,
                startSec = start,
                durationSec = (end - start).coerceAtLeast(TIME_STEP_SEC),
                targetWaterGrams = roundGrams(segment.endWeight),
                pourFlowRate = roundFlow(poured / seconds),
            )
        }

        val withDrawdown = if (drawdownSec >= TIME_STEP_SEC) {
            steps + RecipeStep(
                kind = StepKind.DRAWDOWN,
                startSec = lastPourEnd,
                durationSec = drawdownSec,
                targetWaterGrams = steps.last().targetWaterGrams,
            )
        } else {
            steps
        }

        return Recipe(
            name = name,
            brewer = brewer,
            doseGrams = doseGrams,
            waterGrams = steps.last().targetWaterGrams,
            waterTempC = waterTempC,
            steps = withDrawdown,
        )
    }

    private fun roundGrams(value: Float) = round(value / WEIGHT_STEP_GRAMS) * WEIGHT_STEP_GRAMS

    private fun roundSeconds(value: Float) = (round(value / TIME_STEP_SEC) * TIME_STEP_SEC).toInt()

    private fun roundFlow(value: Float) =
        (round(value / FLOW_STEP) * FLOW_STEP).coerceAtLeast(FLOW_STEP)

    private companion object {
        /** How much the weight must grow for this to count as a started pour. */
        const val POUR_START_GRAMS = 3f
        const val RISE_EPSILON = 0.4f
        const val POUR_STOP_MS = 1_500L
        const val WEIGHT_STEP_GRAMS = 5f
        const val TIME_STEP_SEC = 5
        const val FLOW_STEP = 1f
    }
}
