package com.pourista.ui

import com.pourista.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Список версий в окне «Что нового» ведётся руками, и его легко забыть при
 * выпуске. Тест ловит именно это.
 */
class ReleaseNotesTest {

    @Test
    fun `свежая версия описана в окне «Что нового»`() {
        assertEquals(BuildConfig.VERSION_NAME, ReleaseNotes.all.first().version)
    }

    @Test
    fun `версии идут сверху вниз и не повторяются`() {
        val versions = ReleaseNotes.all.map { it.version }
        assertEquals(versions.distinct(), versions)

        val numbers = versions.map { version -> version.split(".").map { it.toInt() } }
        numbers.zipWithNext { newer, older ->
            assertTrue("$newer должно быть выше $older", compare(newer, older) > 0)
        }
    }

    private fun compare(left: List<Int>, right: List<Int>): Int =
        left.zip(right).firstOrNull { (a, b) -> a != b }?.let { (a, b) -> a compareTo b } ?: 0
}
