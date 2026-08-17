package com.pourista.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pourista.R
import com.pourista.core.formatClock
import com.pourista.core.formatGrams
import com.pourista.data.model.RecipeStep
import com.pourista.ui.icon
import com.pourista.ui.labelRes

/**
 * Кнопка «Показать этапы»: рецепт разворачивают, чтобы заранее увидеть весь
 * план пролива, а не догадываться о нём по одной строке с числом проливов.
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
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
        )
    }
}

/**
 * Тот же разворот, но строкой, а не кнопкой. У кнопки площадь под палец в
 * 48 dp при строке в 20, и в плотной плитке рецепта она расталкивает соседей;
 * здесь высоту задаём сами, а под нажатие отдаём всю ширину.
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
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Все этапы рецепта подряд: что делать, когда и сколько долить.
 *
 * Вода в шаге хранится накопительной целью, а человеку нужен долив — разницу
 * считаем здесь, по тому же правилу, что и подсказки во время пролива.
 */
@Composable
fun RecipeStepsList(
    steps: List<RecipeStep>,
    modifier: Modifier = Modifier,
    /** Шаг, который идёт прямо сейчас, — его подсвечиваем. */
    currentIndex: Int? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        var previousTarget = 0f
        steps.forEachIndexed { index, step ->
            val delta = (step.targetWaterGrams - previousTarget).coerceAtLeast(0f)
            previousTarget = maxOf(previousTarget, step.targetWaterGrams)
            StepRow(step = step, deltaGrams = delta, current = index == currentIndex)
        }
    }
}

@Composable
private fun StepRow(step: RecipeStep, deltaGrams: Float, current: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = if (current) MaterialTheme.colorScheme.primary else muted

    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = step.kind.icon(),
            contentDescription = null,
            tint = accent,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
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
            val details = stepDetails(step, deltaGrams)
            if (details != null) {
                Text(text = details, style = MaterialTheme.typography.labelMedium, color = muted)
            }
            val note = step.note?.takeIf { it.isNotBlank() }
            if (note != null) {
                Text(text = note, style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = "${formatClock(step.startSec)}–${formatClock(step.endSec)}",
            style = MaterialTheme.typography.labelMedium,
            color = muted,
            modifier = Modifier.align(Alignment.Top),
        )
    }
}

/** «+50 г до 50 г · 5,0 г/с» — шаги без долива обходятся без второй строки. */
@Composable
private fun stepDetails(step: RecipeStep, deltaGrams: Float): String? {
    if (deltaGrams <= 0f) return null
    val water = stringResource(
        R.string.step_row_water,
        formatGrams(deltaGrams, 0),
        formatGrams(step.targetWaterGrams, 0),
    )
    val seconds = step.pourSeconds(deltaGrams)
    if (seconds <= 0f) return water
    val flow = stringResource(R.string.step_row_flow, formatGrams(deltaGrams / seconds))
    return "$water · $flow"
}
