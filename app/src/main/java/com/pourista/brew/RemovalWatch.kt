package com.pourista.brew

/**
 * Определение конца заваривания по весам.
 *
 * Последний влив закончен, вода уходит — дальше человек снимает воронку с
 * фильтром или всю чашку целиком, и вес резко падает. Если весы обнуляли под
 * чашку, снятой чашке соответствует большой минус, поэтому порог задан долей
 * от максимума, а не абсолютным весом.
 *
 * Слежение включается только после последнего влива: до него вес падает разве
 * что от ошибки, и обрывать заваривание на середине нельзя.
 */
internal class RemovalWatch(
    /** Ниже какой доли максимума считаем, что с весов сняли. */
    private val dropFactor: Float = DROP_FACTOR,
    /** Сколько падение должно продержаться, чтобы это была не встряска. */
    private val holdMs: Long = HOLD_MS,
    /** Совсем лёгкие заваривания не сторожим: там любой шум — падение вдвое. */
    private val minPeakGrams: Float = MIN_PEAK_GRAMS,
) {

    var armed: Boolean = false
        private set

    private var peakGrams = 0f
    private var droppedAt = 0L

    /** Вес до падения: именно он налит в чашку и должен попасть в историю. */
    var weightBeforeDrop: Float = 0f
        private set

    /** Момент падения — настоящий конец заваривания, а не тремя секундами позже. */
    val droppedAtMs: Long get() = droppedAt

    /** Вес, ниже которого считаем, что чашку сняли. */
    val cutoffGrams: Float get() = peakGrams * dropFactor

    /** Включить слежение: последний влив закончен. */
    fun arm(currentWeightGrams: Float) {
        if (armed) return
        armed = true
        droppedAt = 0L
        weightBeforeDrop = currentWeightGrams
    }

    /**
     * Очередное показание весов. Возвращает true, когда падение продержалось
     * дольше [holdMs] — заваривание пора закрывать.
     */
    fun onSample(weightGrams: Float, nowMs: Long): Boolean {
        // Максимум копим всегда: к концу заваривания это вся налитая вода.
        if (weightGrams > peakGrams) peakGrams = weightGrams
        if (!armed || peakGrams < minPeakGrams) return false

        if (weightGrams > cutoffGrams) {
            weightBeforeDrop = weightGrams
            droppedAt = 0L
            return false
        }
        if (droppedAt == 0L) {
            droppedAt = nowMs
            return false
        }
        return nowMs - droppedAt >= holdMs
    }

    fun reset() {
        armed = false
        peakGrams = 0f
        droppedAt = 0L
        weightBeforeDrop = 0f
    }

    private companion object {
        const val DROP_FACTOR = 0.5f
        const val HOLD_MS = 3_000L
        const val MIN_PEAK_GRAMS = 20f
    }
}
