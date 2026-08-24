package com.pourista.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pourista.core.formatClock
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

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
    /** Цена деления: у веса и у скорости она разная. */
    axisSteps: List<Float> = WEIGHT_AXIS_STEPS,
    /** Точка, которую держат пальцем: её отмечаем вертикалью и кружком. */
    scrubIndex: Int? = null,
    durationSec: Int = values.size,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val guideStyle = TextStyle(fontSize = 10.sp, color = guideColor)

    val dataMax = values.maxOrNull() ?: 0f
    val (axisMax, ticks) = chartAxis(
        rawMax = maxOf(dataMax, focusMax ?: guides.maxOrNull() ?: 0f),
        steps = axisSteps,
        maxLines = axisLinesFor(height - if (showAxis) TIME_GUTTER_DP.dp else 0.dp),
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        drawSeries(
            values = values,
            axisMax = axisMax,
            ticks = ticks,
            showAxis = showAxis,
            lineColor = lineColor,
            fillColor = fillColor,
            guides = guides,
            guideColor = guideColor,
            gridColor = gridColor,
            labelColor = labelColor,
            measurer = measurer,
            labelStyle = labelStyle,
            guideStyle = guideStyle,
            scrubIndex = scrubIndex,
            durationSec = durationSec,
        )
    }
}

/**
 * Рисование ряда. Живёт отдельно от composable, потому что тот же график
 * нужен и вне экрана — в картинке, которой делятся из истории.
 */
internal fun DrawScope.drawSeries(
    values: List<Float>,
    axisMax: Float,
    ticks: Int,
    showAxis: Boolean,
    lineColor: Color,
    fillColor: Color,
    guides: List<Float>,
    guideColor: Color,
    gridColor: Color,
    labelColor: Color,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    guideStyle: TextStyle,
    scrubIndex: Int? = null,
    /** Длительность заваривания: по ней размечается время внизу. */
    durationSec: Int = values.size,
) {
    val tickStep = axisMax / ticks
    // Слева оставляем место под подписи оси, снизу — под время, иначе они
    // лягут на кривую.
    val gutter = if (showAxis) AXIS_GUTTER_DP.dp.toPx() else 0f
    val timeGutter = if (showAxis) TIME_GUTTER_DP.dp.toPx() else 0f
    val plotLeft = gutter
    val plotWidth = (size.width - gutter).coerceAtLeast(1f)
    val plotBottom = (size.height - timeGutter).coerceAtLeast(1f)
    fun yOf(value: Float) = plotBottom - (value / axisMax).coerceIn(0f, 1f) * plotBottom

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
            // Ноль не подписываем: дно графика и без цифры понятно, а строка
            // там сталкивается с подписями времени.
            if (tick == 0) continue
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

    if (values.size < 2) return

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
    area.lineTo(plotLeft + (values.size - 1) * stepX, plotBottom)
    area.close()

    drawPath(path = area, brush = Brush.verticalGradient(listOf(fillColor, Color.Transparent)))
    drawPath(path = line, color = lineColor, style = Stroke(width = 2.5.dp.toPx()))

    // Время внизу: без него не понять, когда начался тот или иной этап.
    if (showAxis && durationSec > 0) {
        val step = timeStep(durationSec)
        var second = 0
        while (second <= durationSec) {
            val x = plotLeft + plotWidth * (second / durationSec.toFloat())
            drawLine(
                color = gridColor.copy(alpha = 0.25f),
                start = Offset(x, 0f),
                end = Offset(x, plotBottom),
                strokeWidth = 1.dp.toPx(),
            )
            val layout = measurer.measure(AnnotatedString(formatClock(second)), labelStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = (x - layout.size.width / 2f)
                        .coerceIn(0f, size.width - layout.size.width),
                    y = plotBottom + 3.dp.toPx(),
                ),
            )
            second += step
        }
    }

    val marked = scrubIndex?.coerceIn(0, values.lastIndex)
    if (marked != null) {
        val x = plotLeft + marked * stepX
        val y = yOf(values[marked].coerceAtLeast(0f))
        drawLine(
            color = labelColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1.dp.toPx(),
        )
        // Кружок с подложкой: на самой кривой одноцветная точка теряется.
        drawCircle(color = gridColor, radius = 6.dp.toPx(), center = Offset(x, y))
        drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(x, y))
    }
}

internal const val AXIS_GUTTER_DP = 34

/** Полоса под подписи времени. */
internal const val TIME_GUTTER_DP = 16

/** Минимальный просвет между линиями сетки: ниже подписи налезают друг на друга. */
internal val MIN_AXIS_LINE_SPACING = 22.dp

/** Сколько делений помещается по высоте графика, чтобы цифры не слиплись. */
internal fun axisLinesFor(plotHeight: Dp): Int =
    (plotHeight / MIN_AXIS_LINE_SPACING).toInt().coerceIn(2, 7)

/**
 * Цена деления времени. Полминуты — привычный шаг рецепта, дальше растём, пока
 * подписи не перестанут налезать друг на друга.
 */
private fun timeStep(durationSec: Int, maxLabels: Int = 8): Int {
    val steps = listOf(15, 30, 60, 120, 300, 600)
    return steps.firstOrNull { durationSec / it <= maxLabels } ?: durationSec.coerceAtLeast(1)
}

/** Деления оси веса: лить по 25 г никто не будет, шаг начинается с полусотни. */
internal val WEIGHT_AXIS_STEPS = listOf(50f, 100f, 150f, 200f, 250f, 500f, 1000f)

/** Скорость читают по 2,5 г/с: это половина обычного пролива. */
internal val FLOW_AXIS_STEPS = listOf(2.5f, 5f, 10f, 25f, 50f)

/** Больше этого линии сливаются в сетку и мешают смотреть на кривую. */
private const val MAX_AXIS_LINES = 7

/**
 * Верх оси и число делений. Шаг берём из заданного набора — он задаёт цену
 * деления, привычную для этой величины, — и увеличиваем, пока линий не станет
 * разумное количество. Округляем шаг, а не сам максимум: иначе 253 превращались
 * бы в 400 и кривая липла бы ко дну.
 */
internal fun chartAxis(
    rawMax: Float,
    steps: List<Float> = WEIGHT_AXIS_STEPS,
    maxLines: Int = MAX_AXIS_LINES,
): Pair<Float, Int> {
    val withHeadroom = rawMax * 1.15f
    if (withHeadroom <= 0f) {
        val step = steps.first()
        return step to 1
    }
    val step = steps.firstOrNull { candidate ->
        ceil(withHeadroom / candidate).toInt() <= maxLines
    } ?: steps.last()
    val ticks = ceil(withHeadroom / step).toInt().coerceAtLeast(1)
    return step * ticks to ticks
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
    axisSteps: List<Float> = WEIGHT_AXIS_STEPS,
    durationSec: Int = values.size,
) {
    // Точка под пальцем: держат — показываем, сколько тут было и когда.
    var scrub by remember(values.size) { mutableStateOf<Int?>(null) }
    val marked = scrub?.coerceIn(0, values.lastIndex.coerceAtLeast(0))
    val readout = marked?.takeIf { values.size > 1 }?.let { index ->
        val second = index * durationSec / (values.size - 1)
        "${formatClock(second)} · ${formatTick(values[index])} $unit"
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = readout ?: unit,
                style = MaterialTheme.typography.labelMedium,
                color = if (readout != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Box(modifier = Modifier.padding(top = 8.dp)) {
            SeriesChart(
                modifier = Modifier.pointerInput(values.size) {
                    // Долгое нажатие, а не обычное: иначе жест отбирал бы
                    // прокрутку у списка, в котором график живёт.
                    val lastIndex = values.lastIndex
                    detectDragGesturesAfterLongPress(
                        onDragStart = { scrub = indexAt(it.x, size.width, lastIndex) },
                        onDrag = { change, _ ->
                            scrub = indexAt(change.position.x, size.width, lastIndex)
                        },
                        onDragEnd = { scrub = null },
                        onDragCancel = { scrub = null },
                    )
                },
                values = values,
                height = height,
                lineColor = lineColor,
                guides = guides,
                guideColor = guideColor,
                focusMax = focusMax,
                axisSteps = axisSteps,
                scrubIndex = marked,
                durationSec = durationSec,
            )
        }
    }
}

/** Какая точка ряда под пальцем: отсчёт от левого края поля, без оси. */
private fun PointerInputScope.indexAt(x: Float, width: Int, lastIndex: Int): Int {
    if (lastIndex <= 0) return 0
    val gutter = AXIS_GUTTER_DP.dp.toPx()
    val plotWidth = (width - gutter).coerceAtLeast(1f)
    return ((x - gutter) / plotWidth * lastIndex).roundToInt().coerceIn(0, lastIndex)
}
