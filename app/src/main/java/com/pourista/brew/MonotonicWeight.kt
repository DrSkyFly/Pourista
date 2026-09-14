package com.pourista.brew

import kotlin.math.abs

/**
 * Weight for calculations: non-decreasing while the brew runs.
 *
 * Water on the scale only ever adds up. When the reading dips — the cone was
 * nudged, the kettle brushed it, the scale shivered — and comes back a moment
 * later, that return is not a pour: taken for one, the app would show tens of
 * grams per second and write a spike into the chart.
 *
 * But a dip can be real too: the tare was pressed, or the cup lifted off. Only
 * time tells the two apart — a real one does not pass. So a weight that stays
 * below the maximum for longer than [rebaseAfterMs] is taken as the new baseline.
 *
 * The wait matches the end-of-brew watch: a swirl on a full cone takes three or
 * four seconds, and with two the weight managed to become "the new truth" — the
 * chart fell through, and the return looked like a pour of hundreds of grams.
 *
 * "Stays" is checked by two conditions at once, and both are required. The weight
 * never came back to the maximum — otherwise the dip is over. And it held the same
 * level all that time — otherwise it is not staying but still on its way down, and
 * a reading taken halfway cannot be taken for the truth.
 *
 * A jump upwards is checked by the same clock. Water comes in a stream: a single
 * reading cannot bring more than [riseJumpGrams], and a second cannot bring more
 * than [riseGramsPerSec]. Anything above that is a pressed lid, a kettle parked on
 * the scale, a knocked stand — and such a weight is believed only if it holds for
 * [riseHoldMs]. Without this check an instant spike went into the calculations
 * whole: the app counted the water as poured and ended the brew halfway through
 * the recipe. A hand on the cone does not hold still either, so a press usually
 * does not live to the end of the wait.
 */
internal class MonotonicWeight(
    /** Small dips do not count even as brief ones. */
    private val toleranceGrams: Float = TOLERANCE_GRAMS,
    /** How long a dip must hold to become the new truth. */
    private val rebaseAfterMs: Long = REBASE_AFTER_MS,
    /** Water cannot bring more than this in a single reading. */
    private val riseJumpGrams: Float = RISE_JUMP_GRAMS,
    /** And no more than this per second: nobody pours that, even from a full kettle. */
    private val riseGramsPerSec: Float = RISE_GRAMS_PER_SEC,
    /** How long a jump upwards must hold to pass for water. */
    private val riseHoldMs: Long = RISE_HOLD_MS,
) {

    private var maxGrams = 0f
    private var belowSinceMs = 0L

    /** The level the countdown runs from: the dip holds while the weight sits on it. */
    private var belowGrams = 0f

    /** The same for a jump upwards: from which level and since when it holds. */
    private var aboveSinceMs = 0L
    private var aboveGrams = 0f

    /** Time of the previous reading: it tells whether water could have brought that much. */
    private var lastSampleMs = 0L

    /** The reading for calculations: flow rate, charts, detecting the end of a pour. */
    fun onSample(rawGrams: Float, nowMs: Long): Float {
        // The first reading is taken as it is: there is nothing to compare it with.
        val first = lastSampleMs == 0L
        val sinceLastMs = if (first) 0L else nowMs - lastSampleMs
        lastSampleMs = nowMs

        if (rawGrams >= maxGrams) {
            val couldBeWater = maxOf(riseJumpGrams, riseGramsPerSec * sinceLastMs / 1000f)
            if (!first && rawGrams - maxGrams > couldBeWater) {
                // The level changed: the weight is not standing up there, it is
                // still moving. The countdown starts over, for the level it is at now.
                if (aboveSinceMs == 0L || abs(rawGrams - aboveGrams) > toleranceGrams) {
                    aboveSinceMs = nowMs
                    aboveGrams = rawGrams
                    return maxGrams
                }
                if (nowMs - aboveSinceMs < riseHoldMs) return maxGrams
            }
            aboveSinceMs = 0L
            maxGrams = rawGrams
            belowSinceMs = 0L
            return maxGrams
        }

        // Below the maximum — the jump upwards is over.
        aboveSinceMs = 0L

        // Back inside the tolerance — the dip is over. The countdown is dropped:
        // otherwise it would outlive this dip and credit the next one with its time.
        if (maxGrams - rawGrams <= toleranceGrams) {
            belowSinceMs = 0L
            return maxGrams
        }

        // The level changed: the weight is not standing down there, it is still
        // falling. The countdown starts over, for the level it is at now.
        if (belowSinceMs == 0L || abs(rawGrams - belowGrams) > toleranceGrams) {
            belowSinceMs = nowMs
            belowGrams = rawGrams
            return maxGrams
        }
        if (nowMs - belowSinceMs < rebaseAfterMs) return maxGrams

        // The dip did not pass — so it is not ripple but a new baseline.
        maxGrams = rawGrams
        belowSinceMs = 0L
        return maxGrams
    }

    fun reset() {
        maxGrams = 0f
        belowSinceMs = 0L
        belowGrams = 0f
        aboveSinceMs = 0L
        aboveGrams = 0f
        lastSampleMs = 0L
    }

    private companion object {
        const val TOLERANCE_GRAMS = 1f
        const val REBASE_AFTER_MS = 5_000L
        const val RISE_JUMP_GRAMS = 20f
        const val RISE_GRAMS_PER_SEC = 40f
        const val RISE_HOLD_MS = 5_000L
    }
}
