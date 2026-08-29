package com.pourista.grind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Числа взяты со шкал самих кофемолок, как их печатает Honest Coffee Guide. */
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

/** Fellow Opus: между числами четвертинки, настройка пишется дробью. */
private val opus = Grinder(
    id = "fellow-opus", brand = "Fellow", model = "Opus",
    base = 137.0, step = 23.25, radix = listOf(4, 1), separator = '.',
    minClicks = 0, maxClicks = 40, decimals = 2,
)

class GrinderTest {

    @Test
    fun `клики переводятся в микроны`() {
        assertEquals(736.0, c40.microns(27), 0.5)
        assertEquals(0.0, c40.microns(0), 0.001)
    }

    @Test
    fun `запись из трёх частей читается как обороты, деления и клики`() {
        assertEquals(88, c5esp.parse("1.7.3"))
        assertEquals(50, c5esp.parse("1.0.0"))
        assertEquals(0, c5esp.parse("0.0.0"))
    }

    @Test
    fun `разделитель берём любой привычный`() {
        assertEquals(88, c5esp.parse("1:7:3"))
        assertEquals(88, c5esp.parse("1 7 3"))
        assertEquals(88, c5esp.parse("1-7-3"))
    }

    @Test
    fun `недописанные части считаются нулями`() {
        assertEquals(85, c5esp.parse("1.7"))
        assertEquals(50, c5esp.parse("1"))
    }

    @Test
    fun `настройка за пределами шкалы не принимается`() {
        assertNull(c40.parse("41"))
        assertNull(c5esp.parse("4.0.0"))
        assertNull(c5esp.parse("1.7.3.2"))
        assertNull(c40.parse("много"))
    }

    @Test
    fun `настройка печатается так же, как подписана на кофемолке`() {
        assertEquals("1.7.3", c5esp.format(88))
        assertEquals("27", c40.format(27))
        assertEquals("0.0.0", c5esp.format(0))
    }

    @Test
    fun `пересчёт повторяет ответ телеграм-бота`() {
        val match = convert(from = c40, clicks = 27, to = c5esp)
        assertEquals("1.7.3", c5esp.format(match.clicks))
        assertEquals(736.0, match.wantedMicrons, 0.5)
        assertEquals(733.0, match.microns, 0.5)
    }

    @Test
    fun `пересчёт туда и обратно возвращает исходную настройку`() {
        val there = convert(from = c40, clicks = 20, to = c5esp)
        val back = convert(from = c5esp, clicks = there.clicks, to = c40)
        assertEquals(20, back.clicks)
    }

    @Test
    fun `помол мельче шкалы упирается в её край`() {
        // Encore сводится только до 250 мкм, эспрессо-помол ему недоступен.
        val match = convert(from = c40, clicks = 5, to = encore)
        assertEquals(0, match.clicks)
        assertEquals(250.0, match.microns, 0.001)
        assertFalse(match.isExact(encore))
    }

    @Test
    fun `дробная шкала читается как дробь, а не как разряды`() {
        assertEquals(9, opus.parse("2.25"))
        assertEquals(10, opus.parse("2.5"))
        assertEquals(8, opus.parse("2"))
        assertEquals(9, opus.parse("2,25"))
    }

    @Test
    fun `дробная настройка печатается без лишних нулей`() {
        assertEquals("2.25", opus.format(9))
        assertEquals("2.5", opus.format(10))
        assertEquals("2", opus.format(8))
    }

    @Test
    fun `дробная шкала не путает разряды с дробью`() {
        // 2.25 — это два с четвертью, а не два и двадцать пять делений.
        assertEquals(137.0 + 9 * 23.25, opus.microns(opus.parse("2.25")!!), 0.001)
    }

    @Test
    fun `на грубой шкале точное попадание отмечается`() {
        val exact = convert(from = c5esp, clicks = 88, to = c5esp)
        assertTrue(exact.isExact(c5esp))
    }
}

/** Поиск модели по свободной записи из рецепта. */
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
    fun `полное имя находится`() {
        assertEquals(c40, find("Comandante C40 MK4"))
    }

    @Test
    fun `регистр, пробелы и дефисы не мешают`() {
        assertEquals(c40, find("comandante-c40  mk4"))
        assertEquals(c5esp, find("TIMEMORE C5ESP"))
    }

    @Test
    fun `часть имени тоже находит`() {
        assertEquals(c40, find("C40 MK4"))
        assertEquals(opus, find("Opus"))
    }

    @Test
    fun `лишние слова вокруг имени не мешают`() {
        assertEquals(encore, find("моя Baratza Encore на кухне"))
    }

    @Test
    fun `непонятное название не находит ничего`() {
        assertNull(find("ручная мельница"))
        assertNull(find(""))
        assertNull(find(null))
    }
}
