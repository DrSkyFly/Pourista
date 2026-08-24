package com.pourista.ui.share

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.pourista.R
import com.pourista.ui.components.AXIS_GUTTER_DP
import com.pourista.ui.components.drawSeries
import com.pourista.ui.components.FLOW_AXIS_STEPS
import com.pourista.ui.components.WEIGHT_AXIS_STEPS
import com.pourista.ui.components.TIME_GUTTER_DP
import com.pourista.ui.components.axisLinesFor
import com.pourista.ui.components.chartAxis
import java.io.File
import java.io.FileOutputStream

/** Цвета картинки: те же, что на экране, чтобы палитра совпадала. */
data class BrewImageColors(
    val background: Color,
    val onBackground: Color,
    val muted: Color,
    val weightLine: Color,
    val flowLine: Color,
    val grid: Color,
)

/** Что писать на картинке. Тексты приходят готовыми — переводит их экран. */
data class BrewImageContent(
    val title: String,
    val subtitle: String,
    val facts: String,
    val details: String?,
    val weightSeries: List<Float>,
    val flowSeries: List<Float>,
    val weightTitle: String,
    val flowTitle: String,
    val footer: String,
)

/**
 * Картинка заваривания для «поделиться».
 *
 * Рисуется не с экрана, а заново: снимок был бы обрезан по высоте телефона и
 * тащил бы с собой поля и кнопки. Размер фиксированный, поэтому в переписке
 * картинка выглядит одинаково у всех.
 */
object BrewImage {

    fun render(context: Context, content: BrewImageContent, colors: BrewImageColors): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH_PX, HEIGHT_PX, Bitmap.Config.ARGB_8888)
        val density = Density(DENSITY)
        val measurer = TextMeasurer(
            defaultFontFamilyResolver = createFontFamilyResolver(context),
            defaultDensity = density,
            defaultLayoutDirection = LayoutDirection.Ltr,
        )

        CanvasDrawScope().draw(
            density = density,
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(bitmap.asImageBitmap()),
            size = Size(WIDTH_PX.toFloat(), HEIGHT_PX.toFloat()),
        ) {
            drawRect(color = colors.background)

            val margin = MARGIN.toPx()
            val width = size.width - margin * 2
            var y = margin

            val title = TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground,
            )
            val body = TextStyle(fontSize = 15.sp, color = colors.onBackground)
            val muted = TextStyle(fontSize = 12.sp, color = colors.muted)

            // Длинное название переносится и обрезается многоточием: за край
            // полотна ему уходить нельзя.
            fun line(value: String, style: TextStyle, lines: Int, gap: Float) {
                val layout = measurer.measure(
                    text = AnnotatedString(value),
                    style = style,
                    maxLines = lines,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = width.toInt()),
                )
                drawText(textLayoutResult = layout, topLeft = Offset(margin, y))
                y += layout.size.height + gap
            }

            line(content.title, title, lines = 2, gap = 4.dp.toPx())
            line(content.subtitle, muted, lines = 1, gap = 10.dp.toPx())
            line(content.facts, body, lines = 1, gap = 4.dp.toPx())
            content.details?.let { line(it, muted, lines = 2, gap = 0f) }

            // Подпись со значком внизу, и место под неё занято заранее:
            // графики делят то, что осталось, и на неё не налезают.
            val footer = measurer.measure(
                text = AnnotatedString(content.footer),
                style = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = width.toInt()),
            )
            val icon = appIcon(context, ICON.toPx().toInt())
            val footerHeight = maxOf(footer.size.height, icon?.height ?: 0).toFloat()
            val footerTop = size.height - margin - footerHeight
            if (icon != null) {
                drawImage(
                    image = icon.asImageBitmap(),
                    dstOffset = IntOffset(
                        x = margin.toInt(),
                        y = (footerTop + (footerHeight - icon.height) / 2f).toInt(),
                    ),
                    dstSize = IntSize(icon.width, icon.height),
                )
            }
            drawText(
                textLayoutResult = footer,
                topLeft = Offset(
                    x = margin + (icon?.width?.plus(ICON_GAP.toPx()) ?: 0f),
                    y = footerTop + (footerHeight - footer.size.height) / 2f,
                ),
            )

            val bottom = footerTop - GAP.toPx()
            val free = (bottom - y - GAP.toPx() * 2).coerceAtLeast(0f)

            // Вес крупнее скорости: по нему читают заваривание.
            val weightBlock = free * WEIGHT_SHARE
            y += GAP.toPx()
            y += drawChart(
                title = content.weightTitle,
                values = content.weightSeries,
                lineColor = colors.weightLine,
                colors = colors,
                measurer = measurer,
                titleStyle = body,
                labelStyle = muted,
                left = margin,
                top = y,
                width = width,
                height = (weightBlock - body.fontSize.toPx() * TITLE_LINE - 8.dp.toPx())
                    .coerceAtLeast(MIN_CHART.toPx()),
            )

            y += GAP.toPx()
            y += drawChart(
                title = content.flowTitle,
                values = content.flowSeries,
                lineColor = colors.flowLine,
                colors = colors,
                measurer = measurer,
                titleStyle = body,
                labelStyle = muted,
                left = margin,
                top = y,
                width = width,
                height = (free - weightBlock - body.fontSize.toPx() * TITLE_LINE - 8.dp.toPx())
                    .coerceAtLeast(MIN_CHART.toPx()),
                axisSteps = FLOW_AXIS_STEPS,
            )
        }
        return bitmap
    }

    /** Значок приложения для подписи. Не нашёлся — обойдёмся одним текстом. */
    private fun appIcon(context: Context, size: Int): android.graphics.Bitmap? = runCatching {
        ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(size, size)
    }.getOrNull()

    /** Кладёт картинку в кэш и отдаёт файл — дальше его подхватывает «поделиться». */
    fun save(context: Context, bitmap: Bitmap, name: String): File? = runCatching {
        val directory = File(context.cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
        val file = File(directory, name)
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        file
    }.getOrNull()

    /**
     * Заголовок и сам график. Возвращает занятую высоту — по ней сдвигается
     * следующий блок.
     */
    private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChart(
        title: String,
        values: List<Float>,
        lineColor: Color,
        colors: BrewImageColors,
        measurer: TextMeasurer,
        titleStyle: TextStyle,
        labelStyle: TextStyle,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        axisSteps: List<Float> = WEIGHT_AXIS_STEPS,
        durationSec: Int = values.size,
    ): Float {
        val heading = measurer.measure(AnnotatedString(title), titleStyle)
        drawText(textLayoutResult = heading, topLeft = Offset(left, top))
        val plotTop = top + heading.size.height + 8.dp.toPx()

        val plotHeight = (height - TIME_GUTTER_DP.dp.toPx()).coerceAtLeast(1f)
        val (axisMax, ticks) = chartAxis(
            rawMax = values.maxOrNull() ?: 0f,
            steps = axisSteps,
            maxLines = axisLinesFor((plotHeight / density).dp),
        )
        // Сдвигаем начало координат к месту графика и ужимаем поле до его
        // размера: drawSeries рисует по всей выданной площади.
        translate(left = left, top = plotTop) {
            inset(
                left = 0f,
                top = 0f,
                right = size.width - width,
                bottom = size.height - height,
            ) {
                drawSeries(
                        values = values,
                        axisMax = axisMax,
                        ticks = ticks,
                        showAxis = true,
                        lineColor = lineColor,
                        fillColor = lineColor.copy(alpha = 0.18f),
                        guides = emptyList(),
                        guideColor = lineColor,
                        gridColor = colors.grid,
                        labelColor = colors.muted,
                        measurer = measurer,
                        labelStyle = labelStyle,
                    guideStyle = labelStyle,
                    durationSec = durationSec,
                )
            }
        }
        return heading.size.height + 8.dp.toPx() + height
    }

    const val SHARE_DIRECTORY = "share"

    private const val WIDTH_PX = 1080
    private const val HEIGHT_PX = 1600

    /** Плотность фиксированная: картинка не должна зависеть от телефона. */
    private const val DENSITY = 3f

    private val MARGIN = 32.dp
    private val GAP = 12.dp
    private val ICON = 28.dp
    private val ICON_GAP = 10.dp
    private val MIN_CHART = 76.dp

    /** Доля свободной высоты под график веса, остальное — скорости. */
    private const val WEIGHT_SHARE = 0.58f

    /** Заголовок графика занимает строку: считаем её по кеглю с интерлиньяжем. */
    private const val TITLE_LINE = 1.4f
}
