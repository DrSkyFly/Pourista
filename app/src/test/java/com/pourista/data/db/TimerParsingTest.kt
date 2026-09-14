package com.pourista.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Before the eighth version of the database the duration lay there as a ready-made string. The
 * migration parses it back into milliseconds, and the time of every old cup in the history rests on
 * that parsing.
 */
class TimerParsingTest {

    @Test
    fun `parses a time with tenths`() {
        assertEquals(83_400L, AppDatabase.parseTimer("1:23.4"))
        assertEquals(185_400L, AppDatabase.parseTimer("3:05.4"))
    }

    @Test
    fun `parses a time without tenths and with large minutes`() {
        assertEquals(725_000L, AppDatabase.parseTimer("12:05"))
        assertEquals(0L, AppDatabase.parseTimer("0:00.0"))
    }

    @Test
    fun `an unreadable string becomes zero rather than an exception`() {
        assertEquals(0L, AppDatabase.parseTimer(null))
        assertEquals(0L, AppDatabase.parseTimer(""))
        assertEquals(0L, AppDatabase.parseTimer("rubbish"))
        assertEquals(0L, AppDatabase.parseTimer("1:2:3"))
    }
}
