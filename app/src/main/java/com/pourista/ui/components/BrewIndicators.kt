package com.pourista.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pourista.ui.theme.AppTheme
import kotlin.math.sin

/**
 * Шкала пролива: залитое — фактический вес, риска — где вес должен быть сейчас,
 * засечки — цели проливов рецепта. Одним взглядом видно, опережаешь или отстаёшь.
 */
@Composable
fun PourGauge(
    current: Float,
    targetNow: Float,
    total: Float,
    marks: List<Float>,
    accent: Color,
    modifier: Modifier = Modifier,
    height: Dp = 22.dp,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val markColor = MaterialTheme.colorScheme.outline
    val markerColor = MaterialTheme.colorScheme.onSurface
    val safeTotal = total.coerceAtLeast(1f)
    val fillFraction by animateFloatAsState(
        targetValue = (current / safeTotal).coerceIn(0f, 1f),
        label = "pourFill",
    )
    val fillColor by animateColorAsState(targetValue = accent, label = "pourColor")

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = track, cornerRadius = radius)

        if (fillFraction > 0f) {
            drawRoundRect(
                color = fillColor,
                size = Size(size.width * fillFraction, size.height),
                cornerRadius = radius,
            )
        }

        marks.forEach { mark ->
            val x = (mark / safeTotal).coerceIn(0f, 1f) * size.width
            drawLine(
                color = markColor.copy(alpha = 0.55f),
                start = Offset(x, size.height * 0.25f),
                end = Offset(x, size.height * 0.75f),
                strokeWidth = 1.5.dp.toPx(),
            )
        }

        val markerX = (targetNow / safeTotal).coerceIn(0f, 1f) * size.width
        drawLine(
            color = markerColor,
            start = Offset(markerX, 0f),
            end = Offset(markerX, size.height),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Кольцо со временем шага: снаружи прогресс, внутри — сколько осталось. */
@Composable
fun StepRing(
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 116.dp,
    centerText: String,
    caption: String? = null,
    /** Отметка, к которой влив должен закончиться, доля от шага 0..1. */
    markerFraction: Float? = null,
    /** Сколько влива уже сделано, 0..1: наливается водой внутрь кольца. */
    fillFraction: Float = 0f,
    waterColor: Color = AppTheme.accents.water,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val markerColor = MaterialTheme.colorScheme.onSurface
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "stepProgress",
    )
    // Уровень догоняет цель плавно: вес приходит с весов рывками, и без
    // сглаживания вода дёргалась бы вместе с ним.
    val level by animateFloatAsState(
        targetValue = fillFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400, easing = LinearEasing),
        label = "waterLevel",
    )
    // Волна идёт сама по себе — вода не должна выглядеть застывшей.
    val waves = rememberInfiniteTransition(label = "water")
    val phase by waves.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(WAVE_PERIOD_MS, easing = LinearEasing),
        ),
        label = "waterPhase",
    )

    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            if (level > 0f) drawWater(level, phase, stroke, waterColor)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            if (markerFraction != null && markerFraction > 0f) {
                val angle = Math.toRadians((-90f + 360f * markerFraction.coerceIn(0f, 1f)).toDouble())
                val radius = (size.minDimension - stroke) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    color = markerColor,
                    radius = stroke * 0.28f,
                    center = Offset(
                        x = center.x + radius * kotlin.math.cos(angle).toFloat(),
                        y = center.y + radius * kotlin.math.sin(angle).toFloat(),
                    ),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerText,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Период бега волны: заметно, но не мельтешит. */
private const val WAVE_PERIOD_MS = 2600
private const val WAVE_AMPLITUDE_DP = 2.5f
private const val WAVE_COUNT = 2f
private const val WAVE_STEP_PX = 4f

/**
 * Вода внутри кольца. Поверхность — синусоида в две волны: ровная линия
 * читалась бы как заливка индикатора, а не как жидкость.
 */
private fun DrawScope.drawWater(level: Float, phase: Float, stroke: Float, color: Color) {
    val inner = Path().apply {
        addOval(Rect(stroke, stroke, size.width - stroke, size.height - stroke))
    }
    clipPath(inner) {
        val surface = size.height * (1f - level)
        // У полного кольца волна ни к чему: воде некуда плескаться.
        val amplitude = if (level >= 1f) 0f else WAVE_AMPLITUDE_DP.dp.toPx()
        val water = Path().apply {
            moveTo(0f, surface)
            var x = 0f
            while (x <= size.width) {
                val angle = x / size.width * 2f * Math.PI.toFloat() * WAVE_COUNT + phase
                lineTo(x, surface + amplitude * sin(angle))
                x += WAVE_STEP_PX
            }
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(water, color.copy(alpha = 0.35f))
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = valueColor,
            )
            if (unit != null) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
    }
}

/** Полоска шагов рецепта: где мы сейчас и сколько осталось. */
@Composable
fun StepTimeline(
    stepCount: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val inactive = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(stepCount) { index ->
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
            ) {
                drawRoundRect(
                    color = if (index <= currentIndex) accent else inactive,
                    cornerRadius = CornerRadius(size.height / 2, size.height / 2),
                )
            }
        }
    }
}
