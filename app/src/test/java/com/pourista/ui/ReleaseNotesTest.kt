package com.pourista.ui

import com.pourista.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The list of versions in the "What is new" dialog is kept by hand, and it is easy to forget at
 * release time. This is exactly what the test catches.
 */
class ReleaseNotesTest {

    @Test
    fun `the freshest version is described in the What is new dialog`() {
        assertEquals(BuildConfig.VERSION_NAME, ReleaseNotes.all.first().version)
    }

    @Test
    fun `the versions run top to bottom and do not repeat`() {
        val versions = ReleaseNotes.all.map { it.version }
        assertEquals(versions.distinct(), versions)

        val numbers = versions.map { version -> version.split(".").map { it.toInt() } }
        numbers.zipWithNext { newer, older ->
            assertTrue("$newer should stand above $older", compare(newer, older) > 0)
        }
    }

    private fun compare(left: List<Int>, right: List<Int>): Int =
        left.zip(right).firstOrNull { (a, b) -> a != b }?.let { (a, b) -> a compareTo b } ?: 0
}
