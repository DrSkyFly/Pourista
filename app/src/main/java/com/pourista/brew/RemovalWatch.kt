package com.pourista.brew

/**
 * Detecting the end of a brew by the scale.
 *
 * The last pour is done, the water is draining — next the cone with the filter, or
 * the whole cup, is lifted off, and the weight drops hard. A lifted cone carries its
 * own weight away together with the soaked coffee: the scale was zeroed under it, but
 * the mass never went anywhere, so the drop is measured in tens of grams.
 *
 * The threshold is a share of the maximum, but never less than [minDropGrams]. A share
 * alone is not enough: on six hundred grams the cone takes away a quarter of the weight,
 * on two hundred more than half, and one common multiplier would either miss the first
 * or catch noise in the second. A cup lifted off whole means a large negative — it
 * passes the same rule with room to spare.
 *
 * The maximum the drop is counted from is taken from the settled weight: a jump upwards
 * on a single reading is not water but a hand on the cone, and it must not raise the
 * lift-off threshold.
 *
 * Drops are counted at all times, but the brew is only closed after the last pour:
 * before it the weight dips by mistake at most, and cutting a pour off halfway is not
 * allowed. The weight from before the drop is known either way — that is what goes into
 * the history, even when "Finish" was pressed by hand.
 */
internal class RemovalWatch(
    /** What share of the maximum the weight must fall by for a lift-off to be believed. */
    private val dropShare: Float = DROP_SHARE,
    /**
     * And no less than this in grams: on a light brew the share comes out at single
     * grams, and the scale rattles that much from a wobble alone.
     */
    private val minDropGrams: Float = MIN_DROP_GRAMS,
    /**
     * How long the drop must hold to be something other than a shake. Five seconds is
     * enough for the last wobble of the cone not to pass for the end of the brew: it is
     * swirled for two or three seconds and put back.
     */
    private val holdMs: Long = HOLD_MS,
    /**
     * The same for a weight gone negative. A minus only happens when everything is
     * taken off a zeroed scale at once — there is nothing to doubt there, and no reason
     * to wait as long.
     */
    private val negativeHoldMs: Long = NEGATIVE_HOLD_MS,
    /** Very light brews are not watched: any noise there is a fall by half. */
    private val minPeakGrams: Float = MIN_PEAK_GRAMS,
    /**
     * How long a sunken weight must hold to become the weight from before the drop.
     * Longer than the watch itself waits: while it decides whether the cup was taken
     * off, a reading on its way down must not be declared the truth.
     */
    private val settleMs: Long = SETTLE_MS,
) {

    var armed: Boolean = false
        private set

    private var peakGrams = 0f
    private var droppedAt = 0L

    /**
     * The weight from before the drop: that is what is in the cup and belongs in the history.
     *
     * It is counted from settled readings rather than from the last normal one. The drop
     * threshold sits tens of grams below the maximum, and the scale does not report a
     * lift-off in one jump — while the cone is being raised two or three intermediate
     * values arrive, and the first of them are still above the threshold. Taking the last
     * of those means writing a weight from the way down into the history.
     */
    var weightBeforeDrop: Float = 0f
        private set

    /**
     * The same trick as for the chart: neither a short dip nor a jump upwards counts as
     * the truth.
     */
    private val settled = MonotonicWeight(rebaseAfterMs = settleMs)

    /** The moment of the drop — the real end of the brew, not three seconds later. */
    val droppedAtMs: Long get() = droppedAt

    /**
     * The weight has sunk and is holding low. "Finish" gets pressed at this moment by
     * those who took the cone off and did not wait: the brew has to be closed the way
     * auto-finish closes it — at the time of the drop and with the weight from before it.
     * Whether the watch is armed makes no difference: the weight in the cup is the same.
     */
    val dropPending: Boolean get() = droppedAt != 0L

    /** The weight below which we take the cup to have been lifted off. */
    val cutoffGrams: Float get() = peakGrams - maxOf(minDropGrams, peakGrams * dropShare)

    /** Allow the brew to be closed: the recipe asks for no more water. */
    fun arm(currentWeightGrams: Float) {
        if (armed) return
        armed = true
        // If the weight sank before arming, the cone is already off, and the count runs
        // from that moment, not from this one. The weight before the drop is remembered too.
        if (droppedAt == 0L) weightBeforeDrop = maxOf(weightBeforeDrop, currentWeightGrams)
    }

    /**
     * Another reading from the scale. Returns true once the drop has held longer than
     * [holdMs] — time to close the brew.
     */
    fun onSample(weightGrams: Float, nowMs: Long): Boolean {
        if (weightGrams > cutoffGrams) {
            // The maximum is collected from the settled weight rather than from any
            // reading. An instant jump — a pressed lid, a knocked scale — would raise the
            // threshold above what is actually poured, and the real weight would turn out
            // to have fallen all by itself, with no cone lifted at all.
            weightBeforeDrop = settled.onSample(weightGrams, nowMs)
            if (weightBeforeDrop > peakGrams) peakGrams = weightBeforeDrop
            droppedAt = 0L
            return false
        }
        if (peakGrams < minPeakGrams) return false

        if (droppedAt == 0L) droppedAt = nowMs
        // While the recipe still asks for water a drop is only remembered: a brew must
        // not be closed halfway.
        if (!armed) return false

        val hold = if (weightGrams < 0f) negativeHoldMs else holdMs
        return nowMs - droppedAt >= hold
    }

    fun reset() {
        armed = false
        peakGrams = 0f
        droppedAt = 0L
        weightBeforeDrop = 0f
        settled.reset()
    }

    private companion object {
        const val DROP_SHARE = 0.12f
        const val MIN_DROP_GRAMS = 30f
        const val HOLD_MS = 5_000L
        const val NEGATIVE_HOLD_MS = 3_000L
        const val MIN_PEAK_GRAMS = 20f
        const val SETTLE_MS = 10_000L
    }
}
