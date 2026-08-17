package com.pourista.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * До восьмой версии базы длительность лежала готовой строкой. Миграция
 * разбирает её обратно в миллисекунды, и на этом разборе держится время
 * всех старых чашек в истории.
 */
class TimerParsingTest {

    @Test
    fun `разбирает время с десятыми`() {
        assertEquals(83_400L, AppDatabase.parseTimer("1:23.4"))
        assertEquals(185_400L, AppDatabase.parseTimer("3:05.4"))
    }

    @Test
    fun `разбирает время без десятых и с большими минутами`() {
        assertEquals(725_000L, AppDatabase.parseTimer("12:05"))
        assertEquals(0L, AppDatabase.parseTimer("0:00.0"))
    }

    @Test
    fun `непонятная строка становится нулём, а не исключением`() {
        assertEquals(0L, AppDatabase.parseTimer(null))
        assertEquals(0L, AppDatabase.parseTimer(""))
        assertEquals(0L, AppDatabase.parseTimer("мусор"))
        assertEquals(0L, AppDatabase.parseTimer("1:2:3"))
    }
}
