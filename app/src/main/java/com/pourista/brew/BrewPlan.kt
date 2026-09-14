package com.pourista.brew

import com.pourista.data.model.Recipe
import com.pourista.data.model.RecipeStep
import com.pourista.data.model.StepKind

/**
 * Pulls a swirl or a stir up to where the pour actually ended: the step that
 * started at [stepStartSec] now starts [shiftSec] seconds earlier.
 *
 * The cone has to be swirled while the water still stands above the coffee, not
 * on the second the recipe assigned to it: pours often end ahead of their slot —
 * poured faster than planned, or the timing was written by eye.
 *
 * The saved time is not lost: it goes into the pause behind the pulled step, and
 * everything further down the recipe starts exactly when it meant to. The recipe
 * may have no pause at all — then one appears. There is a single exception, the
 * drawdown: no more water is coming, nothing to wait for, so it starts earlier too.
 */
internal fun List<RecipeStep>.pulledIn(stepStartSec: Int, shiftSec: Int): List<RecipeStep> {
    if (shiftSec <= 0) return this
    val index = indexOfFirst { it.startSec == stepStartSec && it.kind.isAgitation }
    // Nothing to pull: no such step, or no pour in front of it that could have
    // ended early.
    if (index <= 0 || !this[index - 1].kind.isPour) return this

    val pour = this[index - 1]
    val step = this[index]
    val startSec = step.startSec - shiftSec
    // A pour cannot end before it started.
    if (startSec <= pour.startSec) return this
    val endSec = startSec + step.durationSec

    val moved = toMutableList()
    moved[index - 1] = pour.copy(durationSec = startSec - pour.startSec)
    moved[index] = step.copy(startSec = startSec)

    val next = getOrNull(index + 1)
    when {
        // Nothing left in the recipe: the brew simply ends earlier.
        next == null -> Unit
        // Drawdown: the water is already in the cone, it starts leaving now.
        next.kind == StepKind.DRAWDOWN -> moved[index + 1] = next.copy(startSec = endSec)
        // The pause grows by exactly what the pour saved.
        next.kind == StepKind.WAIT -> moved[index + 1] =
            next.copy(startSec = endSec, durationSec = next.endSec - endSec)
        // The recipe has no pause — add one, otherwise the next pour would
        // start ahead of time.
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
 * Applies every pull-in collected during this brew: the second a step stood at in
 * the recipe, and how many seconds earlier it started.
 */
internal fun Recipe.withPullIns(pullIns: Map<Int, Int>): Recipe {
    if (pullIns.isEmpty()) return this
    val planned = pullIns.entries.fold(steps) { steps, (stepStartSec, shiftSec) ->
        steps.pulledIn(stepStartSec, shiftSec)
    }
    return if (planned === steps) this else copy(steps = planned)
}
