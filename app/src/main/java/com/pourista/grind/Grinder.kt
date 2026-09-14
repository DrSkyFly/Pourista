package com.pourista.grind

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A grinder: how its setting turns into a particle size.
 *
 * The scale is linear on all of them — from zero gap to the coarsest grind — and the difference is
 * only in the notation. On a Comandante it is plain clicks from closed burrs, on a Timemore C5 ESP
 * the notation has three parts: turn, tick, click. So a setting is stored as the number of clicks
 * from zero, and [radix] says how many clicks stand behind every part of the notation: [50, 5, 1]
 * reads as "a turn is 50 clicks, a tick is 5, a click is 1".
 *
 * There is a third kind of scale too: on a Fellow Opus or an Etzinger there are quarters between the
 * numbers, and the setting is written as a fraction — "2.25". Those are marked with [decimals]: the
 * dot there does not separate the parts but the fractional half.
 */
data class Grinder(
    val id: String,
    val brand: String,
    val model: String,
    /** Microns at the zero setting: not every grinder closes its burrs. */
    val base: Double,
    /** How many microns coarser the grind gets per click. */
    val step: Double,
    val radix: List<Int>,
    /** How the parts of the notation are separated on the grinder itself. */
    val separator: Char,
    val minClicks: Int,
    val maxClicks: Int,
    /** Decimal places, if the setting is written as a fraction. 0 means notation by parts. */
    val decimals: Int = 0,
) {
    val name: String get() = "$brand $model"

    /** The particle size at this setting. */
    fun microns(clicks: Int): Double = base + clicks * step

    /** The setting that gives such a grind. Finer or coarser than the scale — we stop at its edge. */
    fun clicksFor(microns: Double): Int =
        ((microns - base) / step).roundToInt().coerceIn(minClicks, maxClicks)

    /**
     * Parsing what the person copied off the grinder. We take any of the usual separators: in the
     * instructions to one and the same Timemore both "1.7.2" and "1:7:2" turn up.
     */
    fun parse(text: String): Int? {
        // A fractional scale: "2.25" is two and a quarter, not two and twenty-five.
        if (decimals > 0) {
            val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
            return (value * radix[0]).roundToInt().takeIf { it in minClicks..maxClicks }
        }
        val parts = text.trim().split(*SEPARATORS).filter { it.isNotBlank() }
        if (parts.isEmpty() || parts.size > radix.size) return null
        // A scale with no parts — then a fractional setting is allowed too: between the ticks of an
        // Encore there is room to stand, and we round to the nearest one anyway.
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

    /** The setting written the way it is labelled on the grinder itself. */
    fun format(clicks: Int): String {
        if (decimals > 0) {
            val text = String.format(Locale.US, "%.${decimals}f", clicks.toDouble() / radix[0])
            // Trailing zeros in a fraction only get in the way: "2.5", not "2.50".
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
        /** The separators of the notation parts, as they turn up in the instructions. */
        val SEPARATORS = charArrayOf('.', ':', '/', '+', '-', ',', ' ').map { it.toString() }.toTypedArray()
    }
}

/** The setting on the target grinder and how exactly it could be hit. */
data class GrindMatch(
    val clicks: Int,
    val microns: Double,
    /** The grind that was asked for: on a coarse scale hitting it exactly does not always work out. */
    val wantedMicrons: Double,
) {
    /** A miss of more than half a click of the target grinder means the scale is coarser than needed. */
    fun isExact(target: Grinder): Boolean = abs(microns - wantedMicrons) <= target.step / 2 + 0.001
}

/** Converting a setting from one grinder to another through the particle size. */
fun convert(from: Grinder, clicks: Int, to: Grinder): GrindMatch {
    val wanted = from.microns(clicks)
    val target = to.clicksFor(wanted)
    return GrindMatch(clicks = target, microns = to.microns(target), wantedMicrons = wanted)
}
