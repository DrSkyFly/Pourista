package com.pourista.ui

import androidx.annotation.StringRes
import com.pourista.R

/**
 * История изменений для окна «Что нового». Свежая версия сверху.
 *
 * Список ведётся руками вместе с `CHANGELOG.md`: в файле подробности для тех,
 * кто читает репозиторий, здесь — то же коротко и на языке приложения.
 */
object ReleaseNotes {

    data class Release(val version: String, @param:StringRes val body: Int)

    val all: List<Release> = listOf(
        Release("1.7.2", R.string.notes_1_7_2),
        Release("1.7.1", R.string.notes_1_7_1),
        Release("1.7.0", R.string.notes_1_7_0),
        Release("1.6.1", R.string.notes_1_6_1),
        Release("1.6.0", R.string.notes_1_6_0),
        Release("1.5.0", R.string.notes_1_5_0),
        Release("1.4.0", R.string.notes_1_4_0),
        Release("1.3.0", R.string.notes_1_3_0),
        Release("1.2.0", R.string.notes_1_2_0),
        Release("1.1.0", R.string.notes_1_1_0),
        Release("1.0.0", R.string.notes_1_0_0),
    )
}
