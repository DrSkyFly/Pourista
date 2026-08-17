package com.pourista.ui.recipes

import com.pourista.data.model.DEFAULT_POUR_FLOW_RATE
import com.pourista.data.model.StepKind

/**
 * Шаг в редакторе задаётся длительностью и объёмом долива — так рецепт и
 * читается («налить 60 г за 15 секунд»). Абсолютное время и накопительный
 * вес считаются при сохранении.
 *
 * Числа хранятся строками: иначе поле нельзя очистить, чтобы набрать заново —
 * пустая строка тут же превращалась бы в ноль и мешала печатать.
 */
/** Чем человек задал влив в последний раз: скоростью или временем. */
enum class PourInput { FLOW, TIME }

data class EditableStep(
    val key: Long,
    val kind: StepKind,
    val title: String = "",
    val duration: String = "30",
    val water: String = "",
    val flow: String = trimNumber(DEFAULT_POUR_FLOW_RATE),
    /** Время влива. Хранится в рецепте не оно, а скорость — это лишь второй способ ввода. */
    val pourSec: String = "",
    val lastPourInput: PourInput = PourInput.FLOW,
    val note: String = "",
) {
    val durationSec: Int get() = duration.toIntOrNull()?.coerceAtLeast(0) ?: 0

    val deltaGrams: Float get() = if (kind.isPour) water.toNumber() ?: 0f else 0f

    val flowRate: Float
        get() = flow.toNumber()?.takeIf { it > 0f } ?: DEFAULT_POUR_FLOW_RATE

    /** Сколько секунд займёт влив при этой скорости. */
    val pourSeconds: Int
        get() = if (deltaGrams > 0f) {
            kotlin.math.round(deltaGrams / flowRate).toInt().coerceAtLeast(1)
        } else {
            0
        }

    /** Влив не может быть длиннее самого шага — иначе рецепт невыполним. */
    val pourTooLong: Boolean get() = pourSeconds > durationSec
}

/**
 * Скорость и время влива — одно и то же число с разных сторон. Рецепты пишут
 * и так, и так («5 г/с» или «влить за 10 секунд»), поэтому редактор принимает
 * оба, а второе поле пересчитывает сразу. В базе всё равно лежит скорость.
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
 * Объём долива меняет ту величину, которую человек не задавал руками: если он
 * писал время влива, оно и остаётся, а скорость подстраивается.
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

/** Заполняет время влива под текущие объём и скорость: при загрузке рецепта. */
fun EditableStep.syncPourSeconds(): EditableStep =
    if (kind.isPour && deltaGrams > 0f) copy(pourSec = secondsFor(deltaGrams, flowRate)) else this

private fun secondsFor(grams: Float, rate: Float): String =
    kotlin.math.round(grams / rate).toInt().coerceAtLeast(1).toString()

/** Десятичную запятую на телефонной клавиатуре набирают чаще точки. */
fun String.toNumber(): Float? = replace(',', '.').toFloatOrNull()

fun trimNumber(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString()
    else String.format(java.util.Locale.US, "%.1f", value)

val List<EditableStep>.hasBloom: Boolean get() = any { it.kind == StepKind.BLOOM }

val List<EditableStep>.hasDrawdown: Boolean get() = any { it.kind == StepKind.DRAWDOWN }

/** Первое место, доступное обычному шагу: сразу за блумингом. */
private val List<EditableStep>.firstFreeIndex: Int get() = if (hasBloom) 1 else 0

/** Последнее доступное место: перед сливом. */
private val List<EditableStep>.lastFreeIndex: Int
    get() = size - 1 - (if (hasDrawdown) 1 else 0)

/** Обычный шаг всегда встаёт перед сливом: после слива лить уже нечего. */
fun List<EditableStep>.withRegularStep(step: EditableStep): List<EditableStep> {
    val at = (lastFreeIndex + 1).coerceIn(firstFreeIndex, size)
    return toMutableList().apply { add(at, step) }
}

/** Блуминг занимает первое место и только его. */
fun List<EditableStep>.withBloom(step: EditableStep): List<EditableStep> {
    if (hasBloom) return this
    return listOf(step.copy(kind = StepKind.BLOOM)) + this
}

/** Слив занимает последнее место и только его. */
fun List<EditableStep>.withDrawdown(step: EditableStep): List<EditableStep> {
    if (hasDrawdown) return this
    return this + step.copy(kind = StepKind.DRAWDOWN)
}

/**
 * Можно ли сдвинуть шаг. Блуминг и слив не двигаются вовсе, обычные шаги
 * ходят только между ними.
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
