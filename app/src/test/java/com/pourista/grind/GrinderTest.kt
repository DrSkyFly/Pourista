package com.pourista.grind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The numbers are taken from the scales of the grinders themselves, as Honest Coffee Guide prints them. */
private val c40 = Grinder(
    id = "comandante-c40-mk4", brand = "Comandante", model = "C40 MK4",
    base = 0.0, step = 27.25, radix = listOf(1), separator = '.',
    minClicks = 0, maxClicks = 40,
)

private val c5esp = Grinder(
    id = "timemore-c5-esp", brand = "Timemore", model = "C5 ESP",
    base = 0.0, step = 1250.0 / 150, radix = listOf(50, 5, 1), separator = '.',
    minClicks = 0, maxClicks = 150,
)

private val encore = Grinder(
    id = "baratza-encore", brand = "Baratza", model = "Encore",
    base = 250.0, step = 23.75, radix = listOf(1), separator = '.',
    minClicks = 0, maxClicks = 40,
)

/** Fellow Opus: quarters between the numbers, the setting is written as a fraction. */
private val opus = Grinder(
    id = "fellow-opus", brand = "Fellow", model = "Opus",
    base = 137.0, step = 23.25, radix = listOf(4, 1), separator = '.',
    minClicks = 0, maxClicks = 40, decimals = 2,
)

class GrinderTest {

    @Test
    fun `clicks convert into microns`() {
        assertEquals(736.0, c40.microns(27), 0.5)
        assertEquals(0.0, c40.microns(0), 0.001)
    }

    @Test
    fun `a three-part notation reads as turns, ticks and clicks`() {
        assertEquals(88, c5esp.parse("1.7.3"))
        assertEquals(50, c5esp.parse("1.0.0"))
        assertEquals(0, c5esp.parse("0.0.0"))
    }

    @Test
    fun `we take any of the usual separators`() {
        assertEquals(88, c5esp.parse("1:7:3"))
        assertEquals(88, c5esp.parse("1 7 3"))
        assertEquals(88, c5esp.parse("1-7-3"))
    }

    @Test
    fun `parts left out count as zeros`() {
        assertEquals(85, c5esp.parse("1.7"))
        assertEquals(50, c5esp.parse("1"))
    }

    @Test
    fun `a setting beyond the scale is not accepted`() {
        assertNull(c40.parse("41"))
        assertNull(c5esp.parse("4.0.0"))
        assertNull(c5esp.parse("1.7.3.2"))
        assertNull(c40.parse("a lot"))
    }

    @Test
    fun `a setting prints the way it is labelled on the grinder`() {
        assertEquals("1.7.3", c5esp.format(88))
        assertEquals("27", c40.format(27))
        assertEquals("0.0.0", c5esp.format(0))
    }

    @Test
    fun `the conversion repeats the answer of the telegram bot`() {
        val match = convert(from = c40, clicks = 27, to = c5esp)
        assertEquals("1.7.3", c5esp.format(match.clicks))
        assertEquals(736.0, match.wantedMicrons, 0.5)
        assertEquals(733.0, match.microns, 0.5)
    }

    @Test
    fun `converting there and back returns the original setting`() {
        val there = convert(from = c40, clicks = 20, to = c5esp)
        val back = convert(from = c5esp, clicks = there.clicks, to = c40)
        assertEquals(20, back.clicks)
    }

    @Test
    fun `a grind finer than the scale stops at its edge`() {
        // An Encore only closes to 250 microns, an espresso grind is out of its reach.
        val match = convert(from = c40, clicks = 5, to = encore)
        assertEquals(0, match.clicks)
        assertEquals(250.0, match.microns, 0.001)
        assertFalse(match.isExact(encore))
    }

    @Test
    fun `a fractional scale reads as a fraction rather than as parts`() {
        assertEquals(9, opus.parse("2.25"))
        assertEquals(10, opus.parse("2.5"))
        assertEquals(8, opus.parse("2"))
        assertEquals(9, opus.parse("2,25"))
    }

    @Test
    fun `a fractional setting prints without extra zeros`() {
        assertEquals("2.25", opus.format(9))
        assertEquals("2.5", opus.format(10))
        assertEquals("2", opus.format(8))
    }

    @Test
    fun `a fractional scale does not confuse parts with a fraction`() {
        // 2.25 is two and a quarter, not two and twenty-five ticks.
        assertEquals(137.0 + 9 * 23.25, opus.microns(opus.parse("2.25")!!), 0.001)
    }

    @Test
    fun `on a coarse scale an exact hit is marked`() {
        val exact = convert(from = c5esp, clicks = 88, to = c5esp)
        assertTrue(exact.isExact(c5esp))
    }
}

/** Looking a model up by the free-form notation from a recipe. */
class GrinderLookupTest {

    private val catalog = listOf(c40, c5esp, encore, opus)

    private fun find(query: String?): Grinder? {
        val needle = simplify(query ?: return null)
        if (needle.length < 3) return null
        catalog.firstOrNull { simplify(it.name) == needle }?.let { return it }
        catalog.filter { needle.contains(simplify(it.name)) }
            .maxByOrNull { simplify(it.name).length }
            ?.let { return it }
        return catalog.filter { simplify(it.name).contains(needle) }
            .minByOrNull { simplify(it.name).length }
    }

    private fun simplify(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

    @Test
    fun `a full name is found`() {
        assertEquals(c40, find("Comandante C40 MK4"))
    }

    @Test
    fun `case, spaces and hyphens do not get in the way`() {
        assertEquals(c40, find("comandante-c40  mk4"))
        assertEquals(c5esp, find("TIMEMORE C5ESP"))
    }

    @Test
    fun `a part of the name finds it too`() {
        assertEquals(c40, find("C40 MK4"))
        assertEquals(opus, find("Opus"))
    }

    @Test
    fun `extra words around the name do not get in the way`() {
        assertEquals(encore, find("my Baratza Encore in the kitchen"))
    }

    @Test
    fun `an unrecognisable name finds nothing`() {
        assertNull(find("a hand mill"))
        assertNull(find(""))
        assertNull(find(null))
    }
}
