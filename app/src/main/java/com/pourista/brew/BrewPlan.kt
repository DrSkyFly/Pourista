package com.pourista.brew

import com.pourista.data.model.Recipe
import com.pourista.data.model.RecipeStep
import com.pourista.data.model.StepKind

/**
 * Подтягивает свирл или размешивание к фактическому концу влива: шаг, который
 * начинался на [stepStartSec], начинается на [shiftSec] секунд раньше.
 *
 * Крутить воронку надо, пока вода стоит над кофе, а не в ту секунду, на которую
 * шаг поставил рецепт: влив часто заканчивают раньше отведённого времени — лили
 * быстрее, чем задумано, или время в рецепте взято на глаз.
 *
 * Сэкономленное время не пропадает: оно уходит в паузу за подтянутым шагом, и
 * всё, что дальше по рецепту, начинается тогда же, когда собиралось. Паузы в
 * рецепте может и не быть — тогда она появляется. Исключение одно: слив. Воды
 * больше не будет, ждать нечего, и он тоже начинается раньше.
 */
internal fun List<RecipeStep>.pulledIn(stepStartSec: Int, shiftSec: Int): List<RecipeStep> {
    if (shiftSec <= 0) return this
    val index = indexOfFirst { it.startSec == stepStartSec && it.kind.isAgitation }
    // Подтягивать некуда: шага нет или перед ним нет влива, который кончился бы
    // раньше времени.
    if (index <= 0 || !this[index - 1].kind.isPour) return this

    val pour = this[index - 1]
    val step = this[index]
    val startSec = step.startSec - shiftSec
    // Влив не может закончиться раньше, чем начался.
    if (startSec <= pour.startSec) return this
    val endSec = startSec + step.durationSec

    val moved = toMutableList()
    moved[index - 1] = pour.copy(durationSec = startSec - pour.startSec)
    moved[index] = step.copy(startSec = startSec)

    val next = getOrNull(index + 1)
    when {
        // Дальше рецепта нет: заваривание просто закончится раньше.
        next == null -> Unit
        // Слив: вода уже вся в воронке, уходить она начинает прямо сейчас.
        next.kind == StepKind.DRAWDOWN -> moved[index + 1] = next.copy(startSec = endSec)
        // Пауза становится длиннее ровно на то, что сэкономил влив.
        next.kind == StepKind.WAIT -> moved[index + 1] =
            next.copy(startSec = endSec, durationSec = next.endSec - endSec)
        // Паузы в рецепте нет — добавляем свою, иначе следующий влив начался бы
        // раньше времени.
        else -> {
            val pauseSec = next.startSec - endSec
            if (pauseSec > 0) {
                moved.add(
                    index + 1,
                    RecipeStep(
                        kind = StepKind.WAIT,
                        startSec = endSec,
                        durationSec = pauseSec,
                        targetWaterGrams = step.targetWaterGrams,
                    ),
                )
            }
        }
    }
    return moved
}

/**
 * Применяет к рецепту все подтяжки, случившиеся за это заваривание: секунда, на
 * которой шаг стоял в рецепте, и на сколько секунд он начался раньше.
 */
internal fun Recipe.withPullIns(pullIns: Map<Int, Int>): Recipe {
    if (pullIns.isEmpty()) return this
    val planned = pullIns.entries.fold(steps) { steps, (stepStartSec, shiftSec) ->
        steps.pulledIn(stepStartSec, shiftSec)
    }
    return if (planned === steps) this else copy(steps = planned)
}
