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
 * The picture is drawn off screen, so it cannot be seen by eye in an ordinary test: we look at the
 * pixels themselves. An empty canvas is the most likely breakage.
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
        title = "V60 Uganda Ferment Washed Long",
        subtitle = "23 Aug 2026, 09:40",
        facts = "18.8 g to 299 g · 1:15.9 · 2:58.7",
        details = "Colombia Huila · Tasty coffee · Comandante · grind: 5.8",
        weightSeries = List(180) { index -> index * 1.7f },
        flowSeries = List(180) { index -> if (index % 30 < 10) 4.5f else 0.2f },
        weightTitle = "Weight",
        flowTitle = "Flow rate",
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
        assertTrue("something should be drawn on the picture, it was $painted", painted > 1_000)

        // We save it next to the cache: the file is fetched with adb and looked at by eye.
        BrewImage.save(context, bitmap, "preview.png")
    }

    /**
     * The layout must not run off the canvas: a long name wraps, the bottom chart stays visible, and
     * the caption at the bottom is covered by nothing.
     */
    @Test
    fun layoutFitsTheCanvas() {
        val bitmap = BrewImage.render(context, content(), colors)
        val background = colors.background.toArgb()
        val flowLine = colors.flowLine.toArgb()

        // The bottom chart is drawn and visible: its colour turns up in the bottom third.
        var flowPixels = 0
        for (y in bitmap.height * 2 / 3 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (bitmap.getPixel(x, y) == flowLine) flowPixels++
            }
        }
        assertTrue("the flow chart should be visible whole, points $flowPixels", flowPixels > 100)

        // The caption strip holds text alone: there must be no curves there.
        val footerTop = bitmap.height - FOOTER_STRIP
        var linesOverFooter = 0
        for (y in footerTop until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (pixel == flowLine || pixel == colors.weightLine.toArgb()) linesOverFooter++
            }
        }
        assertEquals("the caption must not be covered by the chart", 0, linesOverFooter)

        // The bottom edge of the canvas is background: the content ends higher.
        for (x in 0 until bitmap.width step 7) {
            assertEquals(background, bitmap.getPixel(x, bitmap.height - 1))
        }
    }

    /** The file goes where FileProvider hands it out from. */
    @Test
    fun savedFileLandsInShareDirectory() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

        val file = BrewImage.save(context, bitmap, "test.png")!!

        assertEquals(File(context.cacheDir, BrewImage.SHARE_DIRECTORY), file.parentFile)
        assertTrue("the file should exist", file.exists())
        file.delete()
    }

    private companion object {
        /** The height of the caption strip in pixels: 12sp at density 3 plus the padding. */
        const val FOOTER_STRIP = 60
    }
}
