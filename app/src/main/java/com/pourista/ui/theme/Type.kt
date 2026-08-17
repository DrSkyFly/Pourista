package com.pourista.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val defaults = Typography()

/** Цифры фиксированной ширины: показания веса и таймера не должны дёргаться. */
private const val TabularFigures = "tnum"

val FutulaTypography = Typography(
    displayLarge = defaults.displayLarge.copy(fontFeatureSettings = TabularFigures),
    displayMedium = defaults.displayMedium.copy(fontFeatureSettings = TabularFigures),
    displaySmall = defaults.displaySmall.copy(fontFeatureSettings = TabularFigures),
    headlineLarge = defaults.headlineLarge.copy(fontFeatureSettings = TabularFigures),
    headlineMedium = defaults.headlineMedium.copy(fontFeatureSettings = TabularFigures),
    headlineSmall = defaults.headlineSmall.copy(fontFeatureSettings = TabularFigures),
    titleLarge = defaults.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = defaults.labelLarge.copy(fontWeight = FontWeight.Medium),
)

/** Главное показание веса на экране заваривания. */
val WeightReadoutStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Light,
    fontSize = 76.sp,
    lineHeight = 80.sp,
    letterSpacing = (-2).sp,
    fontFeatureSettings = TabularFigures,
)

/** Таймер и вторичные крупные значения. */
val MetricValueStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 30.sp,
    lineHeight = 34.sp,
    fontFeatureSettings = TabularFigures,
)
