package com.pourista.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val defaults = Typography()

/** Fixed-width figures: the weight and timer readings must not twitch. */
private const val TabularFigures = "tnum"

/**
 * Headings heavier than the standard ones. Material keeps separate "emphasised" faces for them, but
 * in this version of the library they are closed off, while the weight achieves the same thing: a
 * heading differs from the text by more than its size.
 */
val FutulaTypography = Typography(
    displayLarge = defaults.displayLarge.copy(fontFeatureSettings = TabularFigures),
    displayMedium = defaults.displayMedium.copy(fontFeatureSettings = TabularFigures),
    displaySmall = defaults.displaySmall.copy(fontFeatureSettings = TabularFigures),
    headlineLarge = defaults.headlineLarge.copy(fontFeatureSettings = TabularFigures),
    headlineMedium = defaults.headlineMedium.copy(fontFeatureSettings = TabularFigures),
    headlineSmall = defaults.headlineSmall.copy(fontFeatureSettings = TabularFigures),
    titleLarge = defaults.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = defaults.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = defaults.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = defaults.labelLarge.copy(fontWeight = FontWeight.Medium),
)

/** The main weight reading on the brew screen. */
val WeightReadoutStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Light,
    fontSize = 76.sp,
    lineHeight = 80.sp,
    letterSpacing = (-2).sp,
    fontFeatureSettings = TabularFigures,
)

/** The timer and the secondary large values. */
val MetricValueStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 30.sp,
    lineHeight = 34.sp,
    fontFeatureSettings = TabularFigures,
)
