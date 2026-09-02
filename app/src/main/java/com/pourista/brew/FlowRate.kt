package com.pourista.brew

/**
 * Насколько усреднять показанную скорость влива.
 *
 * Цифру на экране читают не ради неё самой, а чтобы под неё подстроить струю.
 * Мгновенная скорость для этого слишком подвижна: пока её прочитали, она уже
 * другая. Усреднение отнимает у показания свежесть и возвращает читаемость —
 * что важнее, человек решает сам.
 */
enum class FlowSmoothing(val windowMs: Long) {
    /** Как есть: ни своя оценка, ни число весов не усредняются. */
    NONE(0L),
    LIGHT(500L),
    NORMAL(1_000L),
    STRONG(2_000L),
}

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
 * Готовую оценку усредняем по [smoothing]: одиночная разность прыгает на цену
 * деления весов, делённую на длину окна, — при 0,1 г и полусекунде это по
 * 0,2 г/с в каждую сторону.
 */
internal class FlowRate(
    /** За какой отрезок меряем прирост веса. */
    private val windowMs: Long = WINDOW_MS,
    /** Короче этого отрезка делить нельзя: получится не скорость, а шум. */
    private val minSpanMs: Long = MIN_SPAN_MS,
    /** Быстрее этого не льют: 25 г/с — это полтора литра в минуту. */
    private val maxGramsPerSecond: Float = MAX_PLAUSIBLE,
    smoothing: FlowSmoothing = FlowSmoothing.NORMAL,
) {

    private class Sample(val atMs: Long, val value: Float)

    private val weights = ArrayDeque<Sample>()
    private val rates = ArrayDeque<Sample>()
    private var last = 0f

    /** По какому отрезку усредняем готовые оценки. Задаётся в настройках. */
    @Volatile
    var smoothing: FlowSmoothing = smoothing

    /**
     * Скорость на текущий момент; [reported] — число самих весов, если они его
     * прислали.
     *
     * Свою оценку ведём в любом случае: на резком изменении веса весы упирают
     * скорость в потолок, и подставить вместо неё нечего, кроме собственной.
     *
     * Усредняем оба числа одинаково. Своё считается по окну и этим уже сглажено
     * наполовину, а весы шлют мгновенное — без усреднения оно прыгает сильнее
     * нашей оценки, а не слабее.
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
     * Своя оценка по приросту веса. Null — сказать пока нечего: окна ещё нет
     * или прирост неправдоподобен.
     */
    private fun estimate(grams: Float, nowMs: Long): Float? {
        weights.addLast(Sample(nowMs, grams))
        while (weights.size > 1 && nowMs - weights.first().atMs > windowMs) weights.removeFirst()

        val oldest = weights.first()
        val spanMs = nowMs - oldest.atMs
        // Окно ещё не набралось: начало заваривания или возврат после паузы.
        // Показываем последнее, что знали, — это честнее, чем делить на
        // десятую секунды и выдавать шум за скорость.
        if (spanMs < minSpanMs) return null

        val rate = (grams - oldest.value) / (spanMs / 1000f)
        if (rate > maxGramsPerSecond) {
            // Столько из чайника не наливают: так выглядит возврат показаний
            // после долгой просадки. Окно начинаем заново, иначе выброс сидел
            // бы в нём ещё полсекунды.
            weights.clear()
            weights.addLast(Sample(nowMs, grams))
            return null
        }

        // Обратный ход веса — покачнули воронку, сняли чашку — не влив.
        return rate.coerceAtLeast(0f)
    }

    /** Среднее готовых оценок за последнее окно сглаживания. */
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
