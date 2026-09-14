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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
 * A light line chart on a Canvas. There used to be an AAChart inside a WebView here — for two
 * curves that is too expensive, and it got in the way of the dark theme.
 */
@Composable
fun SeriesChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = lineColor.copy(alpha = 0.18f),
    /** Recipe targets: drawn with a visible dashed line and a label. */
    guides: List<Float> = emptyList(),
    guideColor: Color = MaterialTheme.colorScheme.tertiary,
    /**
     * The value the axis is stretched to. Room above the current curve is needed, otherwise the
     * target of the next step ends up beyond the edge of the chart.
     */
    focusMax: Float? = null,
    showAxis: Boolean = true,
    /** The tick size: it differs for the weight and for the flow rate. */
    axisSteps: List<Float> = WEIGHT_AXIS_STEPS,
    /** The point held by a finger: it is marked with a vertical line and a circle. */
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
 * Drawing a series. It lives apart from the composable, because the same chart is needed off
 * screen too — in the picture shared from the history.
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
    /** The length of the brew: the time below is laid out by it. */
    durationSec: Int = values.size,
) {
    val tickStep = axisMax / ticks
    // Room is left on the left for the axis labels and at the bottom for the time, otherwise
    // they would lie on the curve.
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
            // Zero is not labelled: the bottom of the chart is clear enough without a figure,
            // and a line there collides with the time labels.
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
    // From the bottom of the chart rather than from the bottom of the canvas: the field of time
    // labels lies below it, and the fill would run into it with a slanted edge.
    area.moveTo(plotLeft, plotBottom)
    values.forEachIndexed { index, value ->
        val x = plotLeft + index * stepX
        val y = yOf(value.coerceAtLeast(0f))
        if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
        area.lineTo(x, y)
    }
    area.lineTo(plotLeft + (values.size - 1) * stepX, plotBottom)
    area.close()

    drawPath(path = area, brush = Brush.verticalGradient(listOf(fillColor, Color.Transparent)))
    // Rounding on the breaks: the flow rate has sharp peaks, and without it the tops look
    // chopped off.
    drawPath(
        path = line,
        color = lineColor,
        style = Stroke(
            width = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )

    // The time below: without it there is no telling when this or that stage began.
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
        // A circle with a backing: on the curve itself a single-colour dot gets lost.
        drawCircle(color = gridColor, radius = 6.dp.toPx(), center = Offset(x, y))
        drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(x, y))
    }
}

internal const val AXIS_GUTTER_DP = 34

/** The strip for the time labels. */
internal const val TIME_GUTTER_DP = 16

/** The smallest gap between grid lines: below it the labels climb over each other. */
internal val MIN_AXIS_LINE_SPACING = 22.dp

/** How many ticks fit the height of the chart without the figures sticking together. */
internal fun axisLinesFor(plotHeight: Dp): Int =
    (plotHeight / MIN_AXIS_LINE_SPACING).toInt().coerceIn(2, 7)

/**
 * The time tick size. Half a minute is the familiar recipe step; from there we grow until the
 * labels stop climbing over each other.
 */
private fun timeStep(durationSec: Int, maxLabels: Int = 8): Int {
    val steps = listOf(15, 30, 60, 120, 300, 600)
    return steps.firstOrNull { durationSec / it <= maxLabels } ?: durationSec.coerceAtLeast(1)
}

/** Weight axis ticks: nobody pours in 25 g, so the step starts at fifty. */
internal val WEIGHT_AXIS_STEPS = listOf(50f, 100f, 150f, 200f, 250f, 500f, 1000f)

/** The flow rate is read in 2.5 g/s: that is half of an ordinary pour. */
internal val FLOW_AXIS_STEPS = listOf(2.5f, 5f, 10f, 25f, 50f)

/** Beyond this the lines merge into a mesh and get in the way of the curve. */
private const val MAX_AXIS_LINES = 7

/**
 * The top of the axis and the number of ticks. The step is taken from a given set — it sets the
 * tick size familiar for this quantity — and grown until the number of lines is sensible. We
 * round the step rather than the maximum itself: otherwise 253 would turn into 400 and the curve
 * would cling to the bottom.
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
    // Small flow rate ticks come out fractional: 0.25 must not be shown as 0.3.
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
    // The point under the finger: hold it and we show what was here and when.
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
                    // A long press rather than an ordinary one: otherwise the gesture would take
                    // the scrolling away from the list the chart lives in.
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

/** Which point of the series is under the finger: counted from the left edge of the field, without the axis. */
private fun PointerInputScope.indexAt(x: Float, width: Int, lastIndex: Int): Int {
    if (lastIndex <= 0) return 0
    val gutter = AXIS_GUTTER_DP.dp.toPx()
    val plotWidth = (width - gutter).coerceAtLeast(1f)
    return ((x - gutter) / plotWidth * lastIndex).roundToInt().coerceIn(0, lastIndex)
}
