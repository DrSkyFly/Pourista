package com.pourista.brew

/**
 * Вес для расчётов: неубывающий, пока идёт заваривание.
 *
 * Воды на весах становится только больше. Если показания просели — воронку
 * качнули, задели чайником, весы дрогнули, — а через миг вернулись, то этот
 * возврат не влив: приняв его за влив, приложение показало бы скорость в
 * десятки граммов в секунду и записало бы выброс в график.
 *
 * Но просадка бывает и настоящей: нажали тару или сняли чашку. Такую от
 * покачивания отличает только время — она не проходит. Поэтому вес, который
 * держится ниже максимума дольше [rebaseAfterMs], принимается за новую точку
 * отсчёта.
 */
internal class MonotonicWeight(
    /** Мелкие просадки не считаем даже кратковременными. */
    private val toleranceGrams: Float = TOLERANCE_GRAMS,
    /** Сколько просадка должна продержаться, чтобы стать новой правдой. */
    private val rebaseAfterMs: Long = REBASE_AFTER_MS,
) {

    private var maxGrams = 0f
    private var belowSinceMs = 0L

    /** Показание для расчётов: скорости, графиков, определения конца влива. */
    fun onSample(rawGrams: Float, nowMs: Long): Float {
        if (rawGrams >= maxGrams) {
            maxGrams = rawGrams
            belowSinceMs = 0L
            return maxGrams
        }

        if (maxGrams - rawGrams <= toleranceGrams) return maxGrams

        if (belowSinceMs == 0L) {
            belowSinceMs = nowMs
            return maxGrams
        }
        if (nowMs - belowSinceMs < rebaseAfterMs) return maxGrams

        // Просадка не прошла — значит это не рябь, а новая точка отсчёта.
        maxGrams = rawGrams
        belowSinceMs = 0L
        return maxGrams
    }

    fun reset() {
        maxGrams = 0f
        belowSinceMs = 0L
    }

    private companion object {
        const val TOLERANCE_GRAMS = 1f
        const val REBASE_AFTER_MS = 2_000L
    }
}
