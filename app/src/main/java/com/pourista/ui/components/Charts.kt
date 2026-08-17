package com.pourista.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pourista.core.formatClock
import java.util.Locale

/**
 * Лёгкий линейный график на Canvas. Раньше здесь был AAChart внутри WebView —
 * ради двух кривых это слишком дорого и мешало тёмной теме.
 */
@Composable
fun SeriesChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = lineColor.copy(alpha = 0.18f),
    /** Цели рецепта: рисуются заметной пунктирной линией с подписью. */
    guides: List<Float> = emptyList(),
    guideColor: Color = MaterialTheme.colorScheme.tertiary,
    /**
     * До какого значения растянуть ось. Нужен запас над текущей кривой, иначе
     * цель следующего шага оказывается за краем графика.
     */
    focusMax: Float? = null,
    showAxis: Boolean = true,
    /** Число делений сетки: у низких графиков подписи иначе сливаются. */
    ticks: Int = 4,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val guideStyle = TextStyle(fontSize = 10.sp, color = guideColor)

    val dataMax = values.maxOrNull() ?: 0f
    val axisMax = niceAxisMax(maxOf(dataMax, focusMax ?: guides.maxOrNull() ?: 0f), ticks)
    val tickStep = axisMax / ticks

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        // Слева оставляем место под подписи оси, иначе они лягут на кривую.
        val gutter = if (showAxis) AXIS_GUTTER_DP.dp.toPx() else 0f
        val plotLeft = gutter
        val plotWidth = (size.width - gutter).coerceAtLeast(1f)
        fun yOf(value: Float) = size.height - (value / axisMax).coerceIn(0f, 1f) * size.height

        if (showAxis) {
            for (tick in 0..ticks) {
                val value = tickStep * tick
                val y = yOf(value)
                drawLine(
                    color = gridColor.copy(alpha = if (tick == 0) 0.9f else 0.4f),
                    start = Offset(plotLeft, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
                val layout = measurer.measure(AnnotatedString(formatTick(value)), labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = (gutter - 4.dp.toPx() - layout.size.width).coerceAtLeast(0f),
                        y = (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height),
                    ),
                )
            }
        }

        guides.forEach { guide ->
            if (guide <= 0f || guide > axisMax) return@forEach
            val y = yOf(guide)
            drawLine(
                color = guideColor,
                start = Offset(plotLeft, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
            )
            val layout = measurer.measure(AnnotatedString(formatTick(guide)), guideStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = size.width - layout.size.width,
                    y = (y - layout.size.height - 2.dp.toPx()).coerceAtLeast(0f),
                ),
            )
        }

        if (values.size < 2) return@Canvas

        val stepX = plotWidth / (values.size - 1).toFloat()
        val line = Path()
        val area = Path()
        area.moveTo(plotLeft, size.height)
        values.forEachIndexed { index, value ->
            val x = plotLeft + index * stepX
            val y = yOf(value.coerceAtLeast(0f))
            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
            area.lineTo(x, y)
        }
        area.lineTo(plotLeft + (values.size - 1) * stepX, size.height)
        area.close()

        drawPath(path = area, brush = Brush.verticalGradient(listOf(fillColor, Color.Transparent)))
        drawPath(path = line, color = lineColor, style = Stroke(width = 2.5.dp.toPx()))
    }
}

private const val AXIS_GUTTER_DP = 34

/**
 * Верх оси — ближайшее «человеческое» число сверху. Округляем шаг сетки, а не
 * сам максимум: иначе 253 превращались бы в 400 и кривая липла бы ко дну.
 */
private fun niceAxisMax(rawMax: Float, ticks: Int): Float {
    val withHeadroom = rawMax * 1.15f
    val steps = listOf(
        0.25f, 0.5f, 1f, 2f, 2.5f, 5f, 10f, 15f, 20f, 25f, 30f, 40f,
        50f, 60f, 75f, 100f, 125f, 150f, 200f, 250f, 500f, 1000f,
    )
    val idealStep = withHeadroom / ticks
    val step = steps.firstOrNull { it >= idealStep } ?: idealStep
    return step * ticks
}

private fun formatTick(value: Float): String = when {
    value % 1f == 0f -> value.toInt().toString()
    value >= 1f -> String.format(Locale.US, "%.1f", value)
    // Мелкие деления скорости бывают дробными: 0,25 нельзя показывать как 0,3.
    else -> String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
}

@Composable
fun LabeledChart(
    title: String,
    unit: String,
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    guides: List<Float> = emptyList(),
    guideColor: Color = MaterialTheme.colorScheme.tertiary,
    focusMax: Float? = null,
    height: Dp = 120.dp,
    ticks: Int = 4,
    durationSec: Int = values.size,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = unit,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(modifier = Modifier.padding(top = 8.dp)) {
            SeriesChart(
                values = values,
                height = height,
                lineColor = lineColor,
                guides = guides,
                guideColor = guideColor,
                focusMax = focusMax,
                ticks = ticks,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = AXIS_GUTTER_DP.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatClock(0),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatClock(durationSec),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
