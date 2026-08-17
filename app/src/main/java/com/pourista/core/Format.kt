package com.pourista.core

import java.util.Locale

/** «1:23.4» — десятые доли помогают попадать в тайминги пролива. */
fun formatTimerWithTenths(millis: Long): String {
    val totalTenths = millis / 100
    val tenths = totalTenths % 10
    val totalSeconds = totalTenths / 10
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d.%d", minutes, seconds, tenths)
}

/** «1:23» — для таймлайна рецепта и истории. */
fun formatClock(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", safe / 60, safe % 60)
}

fun formatGrams(value: Float, decimals: Int = 1): String =
    String.format(Locale.US, "%.${decimals}f", value)

fun formatRatio(dose: Float, water: Float): String =
    if (dose <= 0f) "—" else "1:" + String.format(Locale.US, "%.1f", water / dose)
