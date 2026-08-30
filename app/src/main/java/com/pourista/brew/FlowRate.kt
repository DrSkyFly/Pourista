package com.pourista.brew

/**
 * Скорость влива в граммах в секунду.
 *
 * Часть весов считает её сама и шлёт вместе с весом — такое число берём как
 * есть, оно свежее любой нашей оценки. Остальным считаем сами: сколько
 * граммов набежало и за какое время.
 *
 * Окно меряем временем, а не числом отсчётов. Тик движка плывёт: `delay`
 * обещает «не меньше», а не «ровно», и десять тиков это от секунды до
 * полутора. Принимая их за секунду, приложение завышало скорость — на тике в
 * 130 мс на четверть.
 *
 * Готовую оценку усредняем по [smoothMs]: одиночная разность прыгает на цену
 * деления весов, делённую на длину окна, — при 0,1 г и полусекунде это по
 * 0,2 г/с в каждую сторону.
 */
internal class FlowRate(
    /** За какой отрезок меряем прирост веса. */
    private val windowMs: Long = WINDOW_MS,
    /** По какому отрезку усредняем готовые оценки. */
    private val smoothMs: Long = SMOOTH_MS,
    /** Короче этого отрезка делить нельзя: получится не скорость, а шум. */
    private val minSpanMs: Long = MIN_SPAN_MS,
    /** Быстрее этого не льют: 25 г/с — это полтора литра в минуту. */
    private val maxGramsPerSecond: Float = MAX_PLAUSIBLE,
) {

    private class Sample(val atMs: Long, val value: Float)

    private val weights = ArrayDeque<Sample>()
    private val rates = ArrayDeque<Sample>()
    private var last = 0f

    /**
     * Скорость на текущий момент; [reported] — число самих весов, если они его
     * прислали.
     *
     * Свою оценку ведём в любом случае: на резком изменении веса весы упирают
     * скорость в потолок, и подставить вместо неё нечего, кроме собственной.
     */
    fun onSample(grams: Float, reported: Float?, nowMs: Long): Float {
        val own = estimate(grams, nowMs)
        return reported?.coerceAtLeast(0f)?.takeIf { it <= maxGramsPerSecond } ?: own
    }

    fun reset() {
        weights.clear()
        rates.clear()
        last = 0f
    }

    private fun estimate(grams: Float, nowMs: Long): Float {
        weights.addLast(Sample(nowMs, grams))
        while (weights.size > 1 && nowMs - weights.first().atMs > windowMs) weights.removeFirst()

        val oldest = weights.first()
        val spanMs = nowMs - oldest.atMs
        // Окно ещё не набралось: начало заваривания или возврат после паузы.
        // Показываем последнее, что знали, — это честнее, чем делить на
        // десятую секунды и выдавать шум за скорость.
        if (spanMs < minSpanMs) return last

        val rate = (grams - oldest.value) / (spanMs / 1000f)
        if (rate > maxGramsPerSecond) {
            // Столько из чайника не наливают: так выглядит возврат показаний
            // после долгой просадки. Окно начинаем заново, иначе выброс сидел
            // бы в нём ещё полсекунды.
            weights.clear()
            weights.addLast(Sample(nowMs, grams))
            return last
        }

        // Обратный ход веса — покачнули воронку, сняли чашку — не влив.
        rates.addLast(Sample(nowMs, rate.coerceAtLeast(0f)))
        while (rates.size > 1 && nowMs - rates.first().atMs > smoothMs) rates.removeFirst()

        last = (rates.sumOf { it.value.toDouble() } / rates.size).toFloat()
        return last
    }

    private companion object {
        const val WINDOW_MS = 500L
        const val SMOOTH_MS = 500L
        const val MIN_SPAN_MS = 300L
        const val MAX_PLAUSIBLE = 25f
    }
}
