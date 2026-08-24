package com.pourista.brew

/**
 * Определение конца заваривания по весам.
 *
 * Последний влив закончен, вода уходит — дальше человек снимает воронку с
 * фильтром или всю чашку целиком, и вес резко падает. Снятая воронка уносит
 * свой вес вместе с намокшим кофе: весы обнуляли под неё, но масса-то никуда
 * не делась, поэтому падение измеряется десятками граммов.
 *
 * Порог — доля от максимума, но не меньше [minDropGrams]. Долей одной мало:
 * на шестистах граммах воронка забирает четверть веса, а на двухстах — больше
 * половины, и общий множитель либо пропускал бы первое, либо ловил бы шум во
 * втором. Снятой целиком чашке соответствует большой минус — она проходит по
 * тому же правилу с запасом.
 *
 * Падения сторож считает всегда, а закрывает заваривание только после
 * последнего влива: до него вес проседает разве что по ошибке, и обрывать
 * пролив на середине нельзя. Зато вес до падения известен в любом случае —
 * его и записывают в историю, даже когда «Финиш» нажали руками.
 */
internal class RemovalWatch(
    /** На какую долю максимума должен упасть вес, чтобы поверить в снятое. */
    private val dropShare: Float = DROP_SHARE,
    /**
     * И не меньше этого в граммах: на лёгком заваривании доля даёт единицы
     * граммов, а столько весы шумят и от покачивания.
     */
    private val minDropGrams: Float = MIN_DROP_GRAMS,
    /**
     * Сколько падение должно продержаться, чтобы это была не встряска.
     * Пяти секунд хватает, чтобы последнее покачивание воронки не сошло за
     * конец заваривания: качают две-три секунды и ставят обратно.
     */
    private val holdMs: Long = HOLD_MS,
    /**
     * То же для ушедшего в минус веса. Минус бывает только когда с обнулённых
     * весов сняли всё разом — тут сомневаться не в чем, и ждать столько же
     * незачем.
     */
    private val negativeHoldMs: Long = NEGATIVE_HOLD_MS,
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

    /**
     * Вес просел и держится внизу. Кнопку «Финиш» в этот момент жмут те, кто
     * снял воронку и не стал ждать: заваривание надо закрывать так же, как по
     * автофинишу, — временем падения и весом до него. Взведён сторож или нет,
     * неважно: вес в чашке от этого не меняется.
     */
    val dropPending: Boolean get() = droppedAt != 0L

    /** Вес, ниже которого считаем, что чашку сняли. */
    val cutoffGrams: Float get() = peakGrams - maxOf(minDropGrams, peakGrams * dropShare)

    /** Разрешить закрывать заваривание: воды рецепт больше не требует. */
    fun arm(currentWeightGrams: Float) {
        if (armed) return
        armed = true
        // Если вес просел ещё до взведения — воронку уже сняли, и отсчёт идёт
        // с того момента, а не с этого. Вес до падения тоже уже запомнен.
        if (droppedAt == 0L) weightBeforeDrop = currentWeightGrams
    }

    /**
     * Очередное показание весов. Возвращает true, когда падение продержалось
     * дольше [holdMs] — заваривание пора закрывать.
     */
    fun onSample(weightGrams: Float, nowMs: Long): Boolean {
        // Максимум копим всегда: к концу заваривания это вся налитая вода.
        if (weightGrams > peakGrams) peakGrams = weightGrams
        if (peakGrams < minPeakGrams) return false

        if (weightGrams > cutoffGrams) {
            weightBeforeDrop = weightGrams
            droppedAt = 0L
            return false
        }
        if (droppedAt == 0L) droppedAt = nowMs
        // Пока рецепт требует воды, падение только запоминаем: закрывать
        // заваривание на середине нельзя.
        if (!armed) return false

        val hold = if (weightGrams < 0f) negativeHoldMs else holdMs
        return nowMs - droppedAt >= hold
    }

    fun reset() {
        armed = false
        peakGrams = 0f
        droppedAt = 0L
        weightBeforeDrop = 0f
    }

    private companion object {
        const val DROP_SHARE = 0.12f
        const val MIN_DROP_GRAMS = 30f
        const val HOLD_MS = 5_000L
        const val NEGATIVE_HOLD_MS = 3_000L
        const val MIN_PEAK_GRAMS = 20f
    }
}
