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
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
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
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Все этапы рецепта подряд: что делать, когда и сколько долить.
 *
 * Этапы идут лентой — кружок со значком, от кружка к кружку линия. Пролив
 * последователен, и порядок должен читаться раньше текста; заодно видно, что
 * список кончился, а не оборвался.
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
    // Промежутки между строками отданы самим строкам: линия должна тянуться и
    // через них, иначе лента распадётся на отдельные кружки.
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
    // У слива и ожидания нет ни долива, ни заметки — строка всего одна, и вся
    // она встаёт по середине кружка. Где строк больше, по середине стоит
    // кружок, а название с временем остаются наверху.
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
                // Этап без подписей ниже кружка — линии тогда не от чего вести.
                .heightIn(min = StepBadgeSize),
        ) {
            StepBadge(
                kind = step.kind,
                tint = if (current) MaterialTheme.colorScheme.onPrimary else muted,
                ring = if (current) accent else line,
                fill = if (current) accent else null,
                // Кружок по середине всего этапа, а не по строке с названием:
                // описание — часть того же этапа, и значок относится к нему же.
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
                // Время стоит вровень с названием: строчка у него мелче, и
                // без поправки она уезжает вверх.
                modifier = Modifier
                    .align(rowAlign)
                    .padding(top = if (single) 0.dp else TIME_TOP),
            )
        }
        // Просвет между этапами — часть строки: линия идёт и по нему.
        if (!last) Spacer(Modifier.height(STEP_GAP))
    }
}

/**
 * Линия между кружками: рисуем по фону этапа, за текстом.
 *
 * Двумя кусками — от верха строки к своему кружку и от него вниз. Кружок стоит
 * посередине строки, и над ним у высокого этапа остаётся пустое место: рисуй
 * только вниз — и до следующего кружка линия не достанет.
 */
private fun DrawScope.drawStepLine(color: Color, first: Boolean, last: Boolean) {
    val badge = StepBadgeSize.toPx()
    // Просвет до следующего этапа лежит под строкой; у последнего его нет.
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

/** Просвет между этапами: столько линии видно между кружками. */
private val STEP_GAP = 18.dp

/** Поправка для времени справа: у него строка мелче, чем у названия. */
private val TIME_TOP = 2.dp

/** «+50г до 50г · 45с · 5,0г/с» — шаги без долива обходятся без второй строки. */
@Composable
private fun stepDetails(step: RecipeStep, deltaGrams: Float): String? {
    if (deltaGrams <= 0f) return null
    val water = stringResource(
        R.string.step_row_water,
        formatGrams(deltaGrams, 0),
        formatGrams(step.targetWaterGrams, 0),
    )
    // Длительность шага: по диапазону справа её приходится вычитать в уме.
    val duration = stringResource(R.string.step_row_duration, step.durationSec)
    val seconds = step.pourSeconds(deltaGrams)
    if (seconds <= 0f) return "$water · $duration"
    val flow = stringResource(R.string.step_row_flow, formatGrams(deltaGrams / seconds))
    return "$water · $duration · $flow"
}
