package com.pourista.grind

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Кофемолка: как её настройка превращается в размер частиц.
 *
 * Шкала у всех линейная — от нулевого зазора до самого грубого помола, — и
 * разница только в записи. У Comandante это просто клики от сведённых
 * жерновов, у Timemore C5 ESP запись из трёх частей: оборот, деление, клик.
 * Поэтому настройка хранится как число кликов от нуля, а [radix] говорит,
 * сколько кликов стоит за каждой частью записи: [50, 5, 1] читается как
 * «оборот — 50 кликов, деление — 5, клик — 1».
 *
 * Есть и третий вид шкалы: у Fellow Opus или Etzinger между числами стоят
 * четвертинки, и настройка пишется дробью — «2.25». Такие отмечены
 * [decimals]: точка там не разделяет разряды, а отделяет дробную часть.
 */
data class Grinder(
    val id: String,
    val brand: String,
    val model: String,
    /** Микроны на нулевой настройке: не у всех кофемолок жернова сводятся. */
    val base: Double,
    /** Насколько микрон грубеет помол за один клик. */
    val step: Double,
    val radix: List<Int>,
    /** Чем части записи разделены на самой кофемолке. */
    val separator: Char,
    val minClicks: Int,
    val maxClicks: Int,
    /** Знаков после точки, если настройка пишется дробью. 0 — запись разрядами. */
    val decimals: Int = 0,
) {
    val name: String get() = "$brand $model"

    /** Размер частиц на этой настройке. */
    fun microns(clicks: Int): Double = base + clicks * step

    /** Настройка, дающая такой помол. Мельче или грубее шкалы — упираемся в край. */
    fun clicksFor(microns: Double): Int =
        ((microns - base) / step).roundToInt().coerceIn(minClicks, maxClicks)

    /**
     * Разбор того, что человек списал с кофемолки. Разделитель берём любой
     * привычный: в инструкциях к одной и той же Timemore встречается и
     * «1.7.2», и «1:7:2».
     */
    fun parse(text: String): Int? {
        // Дробная шкала: «2.25» — это два с четвертью, а не два и двадцать пять.
        if (decimals > 0) {
            val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
            return (value * radix[0]).roundToInt().takeIf { it in minClicks..maxClicks }
        }
        val parts = text.trim().split(*SEPARATORS).filter { it.isNotBlank() }
        if (parts.isEmpty() || parts.size > radix.size) return null
        // Шкала без частей — тогда можно и дробную настройку: между делениями
        // Encore есть куда встать, а мы всё равно округлим к ближайшему.
        if (radix.size == 1) {
            val value = parts.single().replace(',', '.').toDoubleOrNull() ?: return null
            return (value / radix[0]).roundToInt().takeIf { it in minClicks..maxClicks }
        }
        var clicks = 0
        parts.forEachIndexed { index, part ->
            val value = part.toIntOrNull() ?: return null
            if (value < 0) return null
            clicks += value * radix[index]
        }
        return clicks.takeIf { it in minClicks..maxClicks }
    }

    /** Запись настройки так, как она подписана на самой кофемолке. */
    fun format(clicks: Int): String {
        if (decimals > 0) {
            val text = String.format(Locale.US, "%.${decimals}f", clicks.toDouble() / radix[0])
            // Нули в конце дроби только мешают: «2.5», а не «2.50».
            return text.trimEnd('0').trimEnd('.')
        }
        if (radix.size == 1) return (clicks * radix[0]).toString()
        var rest = clicks
        return radix.joinToString(separator.toString()) { weight ->
            val digit = rest / weight
            rest %= weight
            digit.toString()
        }
    }

    private companion object {
        /** Разделители частей записи, какие попадаются в инструкциях. */
        val SEPARATORS = charArrayOf('.', ':', '/', '+', '-', ',', ' ').map { it.toString() }.toTypedArray()
    }
}

/** Настройка на целевой кофемолке и то, насколько точно в неё удалось попасть. */
data class GrindMatch(
    val clicks: Int,
    val microns: Double,
    /** Помол, который просили: у грубой шкалы точно попасть не всегда выходит. */
    val wantedMicrons: Double,
) {
    /** Промах больше половины клика целевой кофемолки — значит шкала грубее нужного. */
    fun isExact(target: Grinder): Boolean = abs(microns - wantedMicrons) <= target.step / 2 + 0.001
}

/** Пересчёт настройки с одной кофемолки на другую через размер частиц. */
fun convert(from: Grinder, clicks: Int, to: Grinder): GrindMatch {
    val wanted = from.microns(clicks)
    val target = to.clicksFor(wanted)
    return GrindMatch(clicks = target, microns = to.microns(target), wantedMicrons = wanted)
}
