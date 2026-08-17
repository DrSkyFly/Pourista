package com.pourista.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Палитра построена от медно-кофейного основного тона: тёплые поверхности,
// оливковый акцент под подсказки пролива.

private val CopperLight = Color(0xFF8A5024)
private val CopperOnLight = Color(0xFFFFFFFF)
private val CopperContainerLight = Color(0xFFFFDCC2)
private val CopperOnContainerLight = Color(0xFF2E1500)

private val MochaLight = Color(0xFF75584A)
private val MochaContainerLight = Color(0xFFFFDCC8)
private val MochaOnContainerLight = Color(0xFF2B160B)

private val OliveLight = Color(0xFF5A6136)
private val OliveContainerLight = Color(0xFFDEE7B0)
private val OliveOnContainerLight = Color(0xFF181E00)

val LightColors = lightColorScheme(
    primary = CopperLight,
    onPrimary = CopperOnLight,
    primaryContainer = CopperContainerLight,
    onPrimaryContainer = CopperOnContainerLight,
    secondary = MochaLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = MochaContainerLight,
    onSecondaryContainer = MochaOnContainerLight,
    tertiary = OliveLight,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = OliveContainerLight,
    onTertiaryContainer = OliveOnContainerLight,
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF211A16),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF211A16),
    surfaceVariant = Color(0xFFF3DFD3),
    onSurfaceVariant = Color(0xFF52443C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDF1EA),
    surfaceContainer = Color(0xFFF7EBE4),
    surfaceContainerHigh = Color(0xFFF2E5DE),
    surfaceContainerHighest = Color(0xFFECDFD9),
    outline = Color(0xFF85736B),
    outlineVariant = Color(0xFFD7C3B8),
    inverseSurface = Color(0xFF372F2B),
    inverseOnSurface = Color(0xFFFDEEE7),
    inversePrimary = Color(0xFFFFB784),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB784),
    onPrimary = Color(0xFF4C2600),
    primaryContainer = Color(0xFF6C390C),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFE5BFAC),
    onSecondary = Color(0xFF422B1F),
    secondaryContainer = Color(0xFF5B4133),
    onSecondaryContainer = Color(0xFFFFDCC8),
    tertiary = Color(0xFFC2CB96),
    onTertiary = Color(0xFF2C330C),
    tertiaryContainer = Color(0xFF424A20),
    onTertiaryContainer = Color(0xFFDEE7B0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF191210),
    onBackground = Color(0xFFEDE0DA),
    surface = Color(0xFF191210),
    onSurface = Color(0xFFEDE0DA),
    surfaceVariant = Color(0xFF52443C),
    onSurfaceVariant = Color(0xFFD7C3B8),
    surfaceContainerLowest = Color(0xFF130D0B),
    surfaceContainerLow = Color(0xFF211A18),
    surfaceContainer = Color(0xFF261E1B),
    surfaceContainerHigh = Color(0xFF312825),
    surfaceContainerHighest = Color(0xFF3C3330),
    outline = Color(0xFF9F8D84),
    outlineVariant = Color(0xFF52443C),
    inverseSurface = Color(0xFFEDE0DA),
    inverseOnSurface = Color(0xFF372F2B),
    inversePrimary = Color(0xFF8A5024),
)

/**
 * Цвета подсказок пролива. Живут отдельно от [androidx.compose.material3.ColorScheme],
 * потому что должны сохранять смысл и при динамической палитре Material You.
 */
data class BrewAccents(
    val onTrack: Color,
    val onTrackContainer: Color,
    val tooFast: Color,
    val tooFastContainer: Color,
    val tooSlow: Color,
    val tooSlowContainer: Color,
    val water: Color,
    /** Тревога: весы не на связи. Заметнее, чем error из палитры Material You. */
    val alarm: Color,
)

val LightAccents = BrewAccents(
    onTrack = Color(0xFF2E6B39),
    onTrackContainer = Color(0xFFB6F0BE),
    tooFast = Color(0xFF9C4715),
    tooFastContainer = Color(0xFFFFDBC9),
    tooSlow = Color(0xFF2C5EA8),
    tooSlowContainer = Color(0xFFD4E3FF),
    water = Color(0xFF2C5EA8),
    alarm = Color(0xFFD32020),
)

val DarkAccents = BrewAccents(
    onTrack = Color(0xFF8FD89A),
    onTrackContainer = Color(0xFF1D5027),
    tooFast = Color(0xFFFFB68F),
    tooFastContainer = Color(0xFF7A3308),
    tooSlow = Color(0xFFA9C7FF),
    tooSlowContainer = Color(0xFF10457F),
    water = Color(0xFFA9C7FF),
    alarm = Color(0xFFFF5A52),
)
