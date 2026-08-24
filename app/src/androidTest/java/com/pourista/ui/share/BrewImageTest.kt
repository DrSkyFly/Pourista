package com.pourista.ui.share

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Картинка рисуется вне экрана, поэтому глазами её в обычном тесте не увидеть:
 * смотрим на сами пиксели. Пустое полотно — самая вероятная поломка.
 */
@RunWith(AndroidJUnit4::class)
class BrewImageTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val colors = BrewImageColors(
        background = Color(0xFF161310),
        onBackground = Color(0xFFE7E1DA),
        muted = Color(0xFFCFC5B9),
        weightLine = Color(0xFFE0BE9C),
        flowLine = Color(0xFFA6BCCE),
        grid = Color(0xFF4B443B),
    )

    private fun content() = BrewImageContent(
        title = "V60 Уганда Фермент Washed Long",
        subtitle = "23 авг. 2026 г., 09:40",
        facts = "18,8г → 299г · 1:15.9 · 2:58.7",
        details = "Колумбия Уила · Tasty coffee · Comandante · помол: 5.8",
        weightSeries = List(180) { index -> index * 1.7f },
        flowSeries = List(180) { index -> if (index % 30 < 10) 4.5f else 0.2f },
        weightTitle = "Вес",
        flowTitle = "Скорость пролива",
        footer = "Brewed with Pourista",
    )

    @Test
    fun renderedImageIsNotBlank() {
        val bitmap = BrewImage.render(context, content(), colors)

        assertEquals(1080, bitmap.width)
        assertEquals(1600, bitmap.height)

        val background = colors.background.toArgb()
        var painted = 0
        for (y in 0 until bitmap.height step 3) {
            for (x in 0 until bitmap.width step 3) {
                if (bitmap.getPixel(x, y) != background) painted++
            }
        }
        assertTrue("на картинке должно быть нарисовано хоть что-то, было $painted", painted > 1_000)

        // Сохраняем рядом с кэшем: файл забирают adb-ом и смотрят глазами.
        BrewImage.save(context, bitmap, "preview.png")
    }

    /**
     * Раскладка не должна выезжать за полотно: длинное название переносится,
     * нижний график остаётся видимым, подпись внизу ничем не перекрыта.
     */
    @Test
    fun layoutFitsTheCanvas() {
        val bitmap = BrewImage.render(context, content(), colors)
        val background = colors.background.toArgb()
        val flowLine = colors.flowLine.toArgb()

        // Нижний график рисуется и виден: его цвет встречается в нижней трети.
        var flowPixels = 0
        for (y in bitmap.height * 2 / 3 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (bitmap.getPixel(x, y) == flowLine) flowPixels++
            }
        }
        assertTrue("график скорости должен быть виден целиком, точек $flowPixels", flowPixels > 100)

        // Полоса подписи занята только текстом: кривых там быть не должно.
        val footerTop = bitmap.height - FOOTER_STRIP
        var linesOverFooter = 0
        for (y in footerTop until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (pixel == flowLine || pixel == colors.weightLine.toArgb()) linesOverFooter++
            }
        }
        assertEquals("подпись не должна перекрываться графиком", 0, linesOverFooter)

        // Нижний край полотна — фон: содержимое кончается выше.
        for (x in 0 until bitmap.width step 7) {
            assertEquals(background, bitmap.getPixel(x, bitmap.height - 1))
        }
    }

    /** Файл кладётся туда, откуда его отдаёт FileProvider. */
    @Test
    fun savedFileLandsInShareDirectory() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

        val file = BrewImage.save(context, bitmap, "test.png")!!

        assertEquals(File(context.cacheDir, BrewImage.SHARE_DIRECTORY), file.parentFile)
        assertTrue("файл должен существовать", file.exists())
        file.delete()
    }

    private companion object {
        /** Высота полосы подписи в пикселях: 12sp при плотности 3 плюс поля. */
        const val FOOTER_STRIP = 60
    }
}
