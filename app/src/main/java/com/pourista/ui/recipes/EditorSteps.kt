package com.pourista.ui.recipes

import com.pourista.data.model.DEFAULT_POUR_FLOW_RATE
import com.pourista.data.model.StepKind

/**
 * A step in the editor is set by its length and the volume added — that is how a recipe reads too
 * ("pour 60 g over 15 seconds"). The absolute time and the cumulative weight are counted when
 * saving.
 *
 * The numbers are kept as strings: otherwise a field could not be cleared to be typed anew — an
 * empty string would turn into a zero at once and get in the way of typing.
 */
/** How the pour was last set by the person: by rate or by time. */
enum class PourInput { FLOW, TIME }

data class EditableStep(
    val key: Long,
    val kind: StepKind,
    val title: String = "",
    val duration: String = "30",
    val water: String = "",
    val flow: String = trimNumber(DEFAULT_POUR_FLOW_RATE),
    /** The pour time. What the recipe stores is the rate, not this — this is only a second way of entering it. */
    val pourSec: String = "",
    val lastPourInput: PourInput = PourInput.FLOW,
    val note: String = "",
) {
    val durationSec: Int get() = duration.toIntOrNull()?.coerceAtLeast(0) ?: 0

    val deltaGrams: Float get() = if (kind.isPour) water.toNumber() ?: 0f else 0f

    val flowRate: Float
        get() = flow.toNumber()?.takeIf { it > 0f } ?: DEFAULT_POUR_FLOW_RATE

    /** How many seconds the pour will take at this rate. */
    val pourSeconds: Int
        get() = if (deltaGrams > 0f) {
            kotlin.math.round(deltaGrams / flowRate).toInt().coerceAtLeast(1)
        } else {
            0
        }

    /** A pour cannot be longer than the step itself — the recipe would be impossible. */
    val pourTooLong: Boolean get() = pourSeconds > durationSec
}

/**
 * The rate and the pour time are one and the same number from different sides. Recipes are written
 * both ways ("5 g/s" or "pour over 10 seconds"), so the editor takes both and recalculates the
 * other field at once. What lies in the database is the rate either way.
 */
fun EditableStep.withFlow(text: String): EditableStep {
    val rate = text.toNumber()?.takeIf { it > 0f }
    val seconds = if (rate != null && deltaGrams > 0f) secondsFor(deltaGrams, rate) else pourSec
    return copy(flow = text, pourSec = seconds, lastPourInput = PourInput.FLOW)
}

fun EditableStep.withPourSeconds(text: String): EditableStep {
    val seconds = text.toNumber()?.takeIf { it > 0f }
    val rate = if (seconds != null && deltaGrams > 0f) trimNumber(deltaGrams / seconds) else flow
    return copy(pourSec = text, flow = rate, lastPourInput = PourInput.TIME)
}

/**
 * The volume added changes whichever quantity the person did not set by hand: if they wrote the
 * pour time, it stays, and the rate adjusts.
 */
fun EditableStep.withWater(text: String): EditableStep {
    val grams = if (kind.isPour) text.toNumber() ?: 0f else 0f
    if (grams <= 0f) return copy(water = text)
    return when (lastPourInput) {
        PourInput.FLOW -> copy(water = text, pourSec = secondsFor(grams, flowRate))
        PourInput.TIME -> {
            val seconds = pourSec.toNumber()?.takeIf { it > 0f }
            copy(water = text, flow = if (seconds != null) trimNumber(grams / seconds) else flow)
        }
    }
}

/**
 * Trims the pour to the length of the step.
 *
 * The length is something the person knows exactly — "this step is thirty seconds" — while the pour
 * time in recipes is more often put down by eye. So it is not the step that argues with the pour but
 * the pour with the step: it is shortened to the length, and the rate is recalculated for the same
 * volume.
 *
 * From then on the step counts as set by time: correct the volume and the pour time stays the same,
 * and the pour still fits inside the step.
 */
fun EditableStep.pourFittedToDuration(): EditableStep {
    if (!kind.isPour || deltaGrams <= 0f) return this
    val seconds = durationSec
    // An empty length is not yet "zero seconds" but a field that has not been typed out.
    if (seconds <= 0 || pourSeconds <= seconds) return this
    return copy(
        pourSec = seconds.toString(),
        flow = trimNumber(deltaGrams / seconds),
        lastPourInput = PourInput.TIME,
    )
}

/** Fills in the pour time for the current volume and rate: when a recipe is loaded. */
fun EditableStep.syncPourSeconds(): EditableStep =
    if (kind.isPour && deltaGrams > 0f) copy(pourSec = secondsFor(deltaGrams, flowRate)) else this

private fun secondsFor(grams: Float, rate: Float): String =
    kotlin.math.round(grams / rate).toInt().coerceAtLeast(1).toString()

/** The decimal comma is typed on a phone keyboard more often than the dot. */
fun String.toNumber(): Float? = replace(',', '.').toFloatOrNull()

fun trimNumber(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString()
    else String.format(java.util.Locale.US, "%.1f", value)

val List<EditableStep>.hasBloom: Boolean get() = any { it.kind == StepKind.BLOOM }

val List<EditableStep>.hasDrawdown: Boolean get() = any { it.kind == StepKind.DRAWDOWN }

/** The first place available to an ordinary step: right after the bloom. */
private val List<EditableStep>.firstFreeIndex: Int get() = if (hasBloom) 1 else 0

/** The last available place: before the drawdown. */
private val List<EditableStep>.lastFreeIndex: Int
    get() = size - 1 - (if (hasDrawdown) 1 else 0)

/** An ordinary step always goes before the drawdown: after it there is nothing left to pour. */
fun List<EditableStep>.withRegularStep(step: EditableStep): List<EditableStep> {
    val at = (lastFreeIndex + 1).coerceIn(firstFreeIndex, size)
    return toMutableList().apply { add(at, step) }
}

/** The bloom takes the first place and only it. */
fun List<EditableStep>.withBloom(step: EditableStep): List<EditableStep> {
    if (hasBloom) return this
    return listOf(step.copy(kind = StepKind.BLOOM)) + this
}

/** The drawdown takes the last place and only it. */
fun List<EditableStep>.withDrawdown(step: EditableStep): List<EditableStep> {
    if (hasDrawdown) return this
    return this + step.copy(kind = StepKind.DRAWDOWN)
}

/**
 * Whether a step can be moved. The bloom and the drawdown do not move at all, ordinary steps walk
 * only between them.
 */
fun List<EditableStep>.canMove(key: Long, delta: Int): Boolean {
    val index = indexOfFirst { it.key == key }
    if (index < 0 || this[index].kind.isPinned) return false
    val target = index + delta
    return target in firstFreeIndex..lastFreeIndex
}

fun List<EditableStep>.moved(key: Long, delta: Int): List<EditableStep> {
    if (!canMove(key, delta)) return this
    val index = indexOfFirst { it.key == key }
    val updated = toMutableList()
    val step = updated.removeAt(index)
    updated.add(index + delta, step)
    return updated
}
