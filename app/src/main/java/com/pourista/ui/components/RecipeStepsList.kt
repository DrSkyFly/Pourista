package com.pourista.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pourista.R
import com.pourista.core.formatClock
import com.pourista.core.formatGrams
import com.pourista.data.model.RecipeStep
import com.pourista.ui.labelRes

/**
 * The "Show steps" button: a recipe is expanded to see the whole pour plan in advance rather
 * than guess at it from a single line with the number of pours.
 */
@Composable
fun StepsToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onToggle, modifier = modifier) {
        Text(
            stringResource(
                if (expanded) R.string.recipe_steps_collapse else R.string.recipe_steps_expand
            )
        )
        Spacer(Modifier.size(4.dp))
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
        )
    }
}

/**
 * The same expander, but as a line rather than a button. A button has a 48 dp finger area for a
 * 20 dp line, and in the dense recipe tile it pushes its neighbours apart; here we set the height
 * ourselves and give the whole width to the press.
 */
@Composable
fun StepsToggleInline(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = stringResource(
                if (expanded) R.string.recipe_steps_collapse else R.string.recipe_steps_expand
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(4.dp))
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Every step of the recipe in a row: what to do, when, and how much to add.
 *
 * The steps go as a ribbon — a circle with an icon, a line from circle to circle. Pouring is a
 * sequence, and the order has to read before the text does; it also shows that the list ended
 * rather than broke off.
 *
 * The water in a step is stored as a cumulative target, while a person needs the addition — the
 * difference is counted here, by the same rule as the guidance during a pour.
 */
@Composable
fun RecipeStepsList(
    steps: List<RecipeStep>,
    modifier: Modifier = Modifier,
    /** The step running right now — we highlight it. */
    currentIndex: Int? = null,
) {
    // The gaps between rows are given to the rows themselves: the line has to run through them
    // too, otherwise the ribbon falls apart into separate circles.
    Column(modifier = modifier.fillMaxWidth()) {
        var previousTarget = 0f
        steps.forEachIndexed { index, step ->
            val delta = (step.targetWaterGrams - previousTarget).coerceAtLeast(0f)
            previousTarget = maxOf(previousTarget, step.targetWaterGrams)
            StepRow(
                step = step,
                deltaGrams = delta,
                current = index == currentIndex,
                first = index == 0,
                last = index == steps.lastIndex,
            )
        }
    }
}

@Composable
private fun StepRow(
    step: RecipeStep,
    deltaGrams: Float,
    current: Boolean,
    first: Boolean,
    last: Boolean,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant

    val details = stepDetails(step, deltaGrams)
    val note = step.note?.takeIf { it.isNotBlank() }
    // A drawdown and a wait have neither an addition nor a note — there is only one line, and all
    // of it stands level with the middle of the circle. Where there are more lines, the circle
    // stands in the middle and the name with the time stays at the top.
    val single = details == null && note == null
    val rowAlign = if (single) Alignment.CenterVertically else Alignment.Top

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (first && last) {
                    Modifier
                } else {
                    Modifier.drawBehind { drawStepLine(line, first, last) }
                }
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // A step with no labels below the circle — then there is nothing to run the lines from.
                .heightIn(min = StepBadgeSize),
        ) {
            StepBadge(
                kind = step.kind,
                tint = if (current) MaterialTheme.colorScheme.onPrimary else muted,
                ring = if (current) accent else line,
                fill = if (current) accent else null,
                // The circle sits in the middle of the whole step rather than of the name line: the
                // description is part of the same step, and the icon belongs to it too.
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            Spacer(Modifier.size(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(rowAlign),
            ) {
                Text(
                    text = step.title?.takeIf { it.isNotBlank() }
                        ?: stringResource(step.kind.labelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (current) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (details != null) {
                    Text(text = details, style = MaterialTheme.typography.labelMedium, color = muted)
                }
                if (note != null) {
                    Text(text = note, style = MaterialTheme.typography.labelSmall, color = muted)
                }
            }
            Spacer(Modifier.size(8.dp))
            Text(
                text = "${formatClock(step.startSec)}–${formatClock(step.endSec)}",
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                // The time stands level with the name: its line is smaller, and without the
                // correction it drifts upwards.
                modifier = Modifier
                    .align(rowAlign)
                    .padding(top = if (single) 0.dp else TIME_TOP),
            )
        }
        // The gap between steps is part of the row: the line runs through it as well.
        if (!last) Spacer(Modifier.height(STEP_GAP))
    }
}

/**
 * The line between the circles: drawn on the background of the step, behind the text.
 *
 * In two pieces — from the top of the row to its own circle and from it downwards. The circle
 * stands in the middle of the row, and on a tall step there is empty space above it: draw only
 * downwards and the line would not reach the next circle.
 */
private fun DrawScope.drawStepLine(color: Color, first: Boolean, last: Boolean) {
    val badge = StepBadgeSize.toPx()
    // The gap to the next step lies under the row; the last one has none.
    val rowHeight = size.height - if (last) 0f else STEP_GAP.toPx()
    val x = badge / 2f
    val width = StepLineWidth.toPx()
    if (!first) {
        drawLine(color, Offset(x, 0f), Offset(x, (rowHeight - badge) / 2f), width)
    }
    if (!last) {
        drawLine(color, Offset(x, (rowHeight + badge) / 2f), Offset(x, size.height), width)
    }
}

/** The gap between steps: this much of the line shows between the circles. */
private val STEP_GAP = 18.dp

/** The correction for the time on the right: its line is smaller than the name's. */
private val TIME_TOP = 2.dp

/** "+50 g to 50 g · 45 s · 5.0 g/s" — steps with no addition do without the second line. */
@Composable
private fun stepDetails(step: RecipeStep, deltaGrams: Float): String? {
    if (deltaGrams <= 0f) return null
    val water = stringResource(
        R.string.step_row_water,
        formatGrams(deltaGrams, 0),
        formatGrams(step.targetWaterGrams, 0),
    )
    // The length of the step: from the range on the right it has to be subtracted in one's head.
    val duration = stringResource(R.string.step_row_duration, step.durationSec)
    val seconds = step.pourSeconds(deltaGrams)
    if (seconds <= 0f) return "$water · $duration"
    val flow = stringResource(R.string.step_row_flow, formatGrams(deltaGrams / seconds))
    return "$water · $duration · $flow"
}
