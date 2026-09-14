package com.pourista.brew

/**
 * How much to smooth the flow rate shown on screen.
 *
 * The number on screen is not read for its own sake but to fit the stream to it. The
 * instantaneous rate is too lively for that: by the time it is read it is already a
 * different one. Smoothing takes freshness from the reading and gives back readability —
 * which matters more is for the person to decide.
 */
enum class FlowSmoothing(val windowMs: Long) {
    /** As it comes: neither our estimate nor the number from the scale is averaged. */
    NONE(0L),
    LIGHT(500L),
    NORMAL(1_000L),
    STRONG(2_000L),
}

/**
 * Flow rate in grams per second.
 *
 * Some scales count it themselves and send it along with the weight — such a number is
 * taken as it is, it is fresher than any estimate of ours. For the rest we count it
 * ourselves: how many grams arrived and over what time.
 *
 * The window is measured in time, not in samples. The engine tick drifts: `delay`
 * promises "no less", not "exactly", and ten ticks are anything from a second to one and
 * a half. Taking them for a second, the app overstated the rate — by a quarter at a tick
 * of 130 ms.
 *
 * The finished estimate is averaged over [smoothing]: a single difference jumps by the
 * scale resolution divided by the window length — at 0.1 g and half a second that is
 * 0.2 g/s either way.
 */
internal class FlowRate(
    /** The stretch the weight gain is measured over. */
    private val windowMs: Long = WINDOW_MS,
    /** Nothing shorter than this may be divided: that gives noise, not a rate. */
    private val minSpanMs: Long = MIN_SPAN_MS,
    /** Nobody pours faster than this: 25 g/s is a litre and a half a minute. */
    private val maxGramsPerSecond: Float = MAX_PLAUSIBLE,
    smoothing: FlowSmoothing = FlowSmoothing.NORMAL,
) {

    private class Sample(val atMs: Long, val value: Float)

    private val weights = ArrayDeque<Sample>()
    private val rates = ArrayDeque<Sample>()
    private var last = 0f

    /** The stretch the finished estimates are averaged over. Set in the settings. */
    @Volatile
    var smoothing: FlowSmoothing = smoothing

    /**
     * The rate at this moment; [reported] is the number from the scale itself, if it sent
     * one.
     *
     * Our own estimate is kept either way: on a sharp change of weight the scale pins the
     * rate to its ceiling, and there is nothing to put in its place but our own.
     *
     * Both numbers are averaged the same way. Ours is counted over a window and is half
     * smoothed by that already, while the scale sends an instantaneous one — unaveraged it
     * jumps harder than our estimate, not softer.
     */
    fun onSample(grams: Float, reported: Float?, nowMs: Long): Float {
        val own = estimate(grams, nowMs)
        val instant = reported?.coerceAtLeast(0f)?.takeIf { it <= maxGramsPerSecond }
            ?: own
            ?: return last
        return smooth(instant, nowMs)
    }

    fun reset() {
        weights.clear()
        rates.clear()
        last = 0f
    }

    /**
     * Our own estimate from the weight gain. Null means there is nothing to say yet: the
     * window is not there, or the gain is implausible.
     */
    private fun estimate(grams: Float, nowMs: Long): Float? {
        weights.addLast(Sample(nowMs, grams))
        while (weights.size > 1 && nowMs - weights.first().atMs > windowMs) weights.removeFirst()

        val oldest = weights.first()
        val spanMs = nowMs - oldest.atMs
        // The window has not filled up yet: the start of a brew, or a return after a
        // pause. We show the last thing we knew — that is more honest than dividing by a
        // tenth of a second and passing noise off as a rate.
        if (spanMs < minSpanMs) return null

        val rate = (grams - oldest.value) / (spanMs / 1000f)
        if (rate > maxGramsPerSecond) {
            // Nobody pours that much out of a kettle: this is what the readings coming
            // back after a long dip look like. The window starts over, otherwise the spike
            // would sit in it for another half second.
            weights.clear()
            weights.addLast(Sample(nowMs, grams))
            return null
        }

        // The weight going backwards — the cone was nudged, the cup lifted — is not a pour.
        return rate.coerceAtLeast(0f)
    }

    /** The average of the finished estimates over the last smoothing window. */
    private fun smooth(rate: Float, nowMs: Long): Float {
        val window = smoothing.windowMs
        rates.addLast(Sample(nowMs, rate))
        while (rates.size > 1 && nowMs - rates.first().atMs > window) rates.removeFirst()

        last = (rates.sumOf { it.value.toDouble() } / rates.size).toFloat()
        return last
    }

    private companion object {
        const val WINDOW_MS = 500L
        const val MIN_SPAN_MS = 300L
        const val MAX_PLAUSIBLE = 25f
    }
}
