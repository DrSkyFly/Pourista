package com.pourista.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Кофейная палитра. Тон медный, но приглушённый: на экране во время пролива
// заливкой идут целые карточки, и насыщенный цвет на такой площади утомляет
// глаза. Цветом здесь помечают смысл, а не украшают.

val LightColors = lightColorScheme(
    primary = Color(0xFF6E4B33),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBD9C9),
    onPrimaryContainer = Color(0xFF2A1709),
    secondary = Color(0xFF6B5A4C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9DED2),
    onSecondaryContainer = Color(0xFF2A211B),
    tertiary = Color(0xFF5D6349),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE1E4D6),
    onTertiaryContainer = Color(0xFF1B1F12),
    error = Color(0xFFA8443C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF2DCD8),
    onErrorContainer = Color(0xFF3A0F0C),
    background = Color(0xFFFBF7F1),
    onBackground = Color(0xFF221D19),
    surface = Color(0xFFFBF7F1),
    onSurface = Color(0xFF221D19),
    surfaceVariant = Color(0xFFE7DED2),
    onSurfaceVariant = Color(0xFF56504A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2EA),
    surfaceContainer = Color(0xFFF2ECE3),
    surfaceContainerHigh = Color(0xFFECE5DC),
    surfaceContainerHighest = Color(0xFFE6DFD5),
    outline = Color(0xFF898074),
    outlineVariant = Color(0xFFD9D0C4),
    inverseSurface = Color(0xFF37312C),
    inverseOnSurface = Color(0xFFF5F0EB),
    inversePrimary = Color(0xFFDBBBA0),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFE0BE9C),
    onPrimary = Color(0xFF3A2A1C),
    primaryContainer = Color(0xFF574029),
    onPrimaryContainer = Color(0xFFF2E0D0),
    secondary = Color(0xFFD0C3B9),
    onSecondary = Color(0xFF362D26),
    secondaryContainer = Color(0xFF4C4137),
    onSecondaryContainer = Color(0xFFEDE2D9),
    tertiary = Color(0xFFBCC1A6),
    onTertiary = Color(0xFF2B3020),
    tertiaryContainer = Color(0xFF414634),
    onTertiaryContainer = Color(0xFFDCE0C6),
    error = Color(0xFFE8A29B),
    onError = Color(0xFF5A1712),
    errorContainer = Color(0xFF7A2A24),
    onErrorContainer = Color(0xFFF7DAD6),
    background = Color(0xFF171310),
    onBackground = Color(0xFFE7E1DA),
    surface = Color(0xFF171310),
    onSurface = Color(0xFFE7E1DA),
    surfaceVariant = Color(0xFF4B443B),
    onSurfaceVariant = Color(0xFFCFC5B9),
    surfaceContainerLowest = Color(0xFF100E0B),
    surfaceContainerLow = Color(0xFF1E1A15),
    surfaceContainer = Color(0xFF231E19),
    surfaceContainerHigh = Color(0xFF2D2721),
    surfaceContainerHighest = Color(0xFF372F27),
    outline = Color(0xFF958B80),
    outlineVariant = Color(0xFF4B443B),
    inverseSurface = Color(0xFFE7E1DA),
    inverseOnSurface = Color(0xFF332E29),
    inversePrimary = Color(0xFF6E4B33),
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
    onTrack = Color(0xFF476E52),
    onTrackContainer = Color(0xFFDCE8DD),
    tooFast = Color(0xFF8E5637),
    tooFastContainer = Color(0xFFEFE1D7),
    tooSlow = Color(0xFF4A6785),
    tooSlowContainer = Color(0xFFDFE5EC),
    water = Color(0xFF4A6785),
    alarm = Color(0xFFC24A40),
)

val DarkAccents = BrewAccents(
    onTrack = Color(0xFFA8C8AE),
    onTrackContainer = Color(0xFF2C3F32),
    tooFast = Color(0xFFDCAE90),
    tooFastContainer = Color(0xFF4C3A2B),
    tooSlow = Color(0xFFA6BCCE),
    tooSlowContainer = Color(0xFF2F4152),
    water = Color(0xFFA6BCCE),
    alarm = Color(0xFFE0776C),
)

// Палитра «4:6»: чёрный фон, бирюзовая шапка и янтарная кнопка — цвета сняты
// с приложения метода 4:6 Тэцу Кацуи.

private val Amber = Color(0xFFFFC145)
private val Teal = Color(0xFF1C677E)

/** Шапка экрана в палитре «4:6» — та самая бирюза из оригинала. */
val FourSixBar = Teal
val FourSixOnBar = Color(0xFFEBF6FA)
private val SkyBlue = Color(0xFF1D89E4)
private val Ember = Color(0xFFF34433)

val FourSixDarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF241900),
    primaryContainer = Color(0xFF63450A),
    onPrimaryContainer = Color(0xFFFFE3B4),
    secondary = Color(0xFF9FCBD8),
    onSecondary = Color(0xFF003543),
    secondaryContainer = Color(0xFF2E3438),
    onSecondaryContainer = Color(0xFFDCE6EA),
    tertiary = Color(0xFFFF8A7A),
    onTertiary = Color(0xFF5F1500),
    tertiaryContainer = Color(0xFF8C2A1E),
    onTertiaryContainer = Color(0xFFFFDAD4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF303030),
    onSurfaceVariant = Color(0xFFC6C6C6),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainer = Color(0xFF181818),
    surfaceContainerHigh = Color(0xFF232323),
    surfaceContainerHighest = Color(0xFF2C2C2C),
    outline = Color(0xFF8A8A8A),
    outlineVariant = Color(0xFF3A3A3A),
    inverseSurface = Color(0xFFECECEC),
    inverseOnSurface = Color(0xFF1B1B1B),
    inversePrimary = Color(0xFF7D5A00),
)

val FourSixLightColors = lightColorScheme(
    primary = Color(0xFF7D5A00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE3B4),
    onPrimaryContainer = Color(0xFF271A00),
    secondary = Teal,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDEEF9),
    onSecondaryContainer = Color(0xFF001F28),
    tertiary = Color(0xFFB3261E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD4),
    onTertiaryContainer = Color(0xFF410001),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1B1B1B),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFE3E3E3),
    onSurfaceVariant = Color(0xFF474747),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFEFEFEF),
    surfaceContainerHigh = Color(0xFFE9E9E9),
    surfaceContainerHighest = Color(0xFFE3E3E3),
    outline = Color(0xFF787878),
    outlineVariant = Color(0xFFC9C9C9),
    inverseSurface = Color(0xFF2F2F2F),
    inverseOnSurface = Color(0xFFF2F2F2),
    inversePrimary = Amber,
)

val FourSixDarkAccents = BrewAccents(
    onTrack = Color(0xFF7CD992),
    onTrackContainer = Color(0xFF16401F),
    tooFast = Ember,
    tooFastContainer = Color(0xFF5A1B14),
    tooSlow = SkyBlue,
    tooSlowContainer = Color(0xFF0C3D66),
    water = SkyBlue,
    alarm = Color(0xFFFF4B3E),
)

val FourSixLightAccents = BrewAccents(
    onTrack = Color(0xFF2E6B39),
    onTrackContainer = Color(0xFFB6F0BE),
    tooFast = Color(0xFFB3261E),
    tooFastContainer = Color(0xFFFFDAD4),
    tooSlow = Color(0xFF1264A3),
    tooSlowContainer = Color(0xFFCFE6FA),
    water = Color(0xFF1264A3),
    alarm = Color(0xFFD32020),
)

// Палитра «спокойная»: бледный оливковый фон, шалфейные карточки и травяной
// акцент. Цвета сняты со светлой темы Material You на зелёных обоях — она
// вышла тише всего, что рисуют динамические цвета.

private val Herb = Color(0xFF47662E)
private val Sage = Color(0xFFE0E4D6)

val CalmLightColors = lightColorScheme(
    primary = Herb,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC4EFA0),
    onPrimaryContainer = Color(0xFF142B06),
    secondary = Color(0xFF58624A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE7CE),
    onSecondaryContainer = Color(0xFF161E0F),
    tertiary = Color(0xFF386669),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCEBEE),
    onTertiaryContainer = Color(0xFF002022),
    error = Color(0xFFA8443C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF2DCD8),
    onErrorContainer = Color(0xFF3A0F0C),
    background = Color(0xFFF8FAEC),
    onBackground = Color(0xFF25291B),
    surface = Color(0xFFF8FAEC),
    onSurface = Color(0xFF25291B),
    surfaceVariant = Sage,
    onSurfaceVariant = Color(0xFF454A3B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F5E6),
    surfaceContainer = Color(0xFFECF0E0),
    surfaceContainerHigh = Color(0xFFE6EADB),
    surfaceContainerHighest = Sage,
    outline = Color(0xFF757B69),
    outlineVariant = Color(0xFFC5CBB6),
    inverseSurface = Color(0xFF2A2E22),
    inverseOnSurface = Color(0xFFF1F5E4),
    inversePrimary = Color(0xFFA9D286),
)

val CalmDarkColors = darkColorScheme(
    primary = Color(0xFFA9D286),
    onPrimary = Color(0xFF1B3700),
    primaryContainer = Color(0xFF304F19),
    onPrimaryContainer = Color(0xFFC4EFA0),
    secondary = Color(0xFFC0CBB0),
    onSecondary = Color(0xFF2B3421),
    secondaryContainer = Color(0xFF414A37),
    onSecondaryContainer = Color(0xFFDCE7CE),
    tertiary = Color(0xFFA0CFD2),
    onTertiary = Color(0xFF003739),
    tertiaryContainer = Color(0xFF1E4E51),
    onTertiaryContainer = Color(0xFFBCEBEE),
    error = Color(0xFFE8A29B),
    onError = Color(0xFF5A1712),
    errorContainer = Color(0xFF7A2A24),
    onErrorContainer = Color(0xFFF7DAD6),
    background = Color(0xFF12140E),
    onBackground = Color(0xFFE3E7D6),
    surface = Color(0xFF12140E),
    onSurface = Color(0xFFE3E7D6),
    surfaceVariant = Color(0xFF444A3A),
    onSurfaceVariant = Color(0xFFC5CBB6),
    surfaceContainerLowest = Color(0xFF0D0F09),
    surfaceContainerLow = Color(0xFF1A1D15),
    surfaceContainer = Color(0xFF1E2119),
    surfaceContainerHigh = Color(0xFF292C23),
    surfaceContainerHighest = Color(0xFF34372D),
    outline = Color(0xFF8F9581),
    outlineVariant = Color(0xFF444A3A),
    inverseSurface = Color(0xFFE3E7D6),
    inverseOnSurface = Color(0xFF2A2E22),
    inversePrimary = Herb,
)

val CalmLightAccents = BrewAccents(
    onTrack = Herb,
    onTrackContainer = Color(0xFFDCE7CE),
    tooFast = Color(0xFF96543E),
    tooFastContainer = Color(0xFFF0DFD6),
    tooSlow = Color(0xFF3F6480),
    tooSlowContainer = Color(0xFFD9E4EC),
    water = Color(0xFF3F6480),
    alarm = Color(0xFFC0453B),
)

val CalmDarkAccents = BrewAccents(
    onTrack = Color(0xFFA9D286),
    onTrackContainer = Color(0xFF2C3F22),
    tooFast = Color(0xFFDFAE96),
    tooFastContainer = Color(0xFF4B382D),
    tooSlow = Color(0xFFA5BFD0),
    tooSlowContainer = Color(0xFF2E4150),
    water = Color(0xFFA5BFD0),
    alarm = Color(0xFFE0776C),
)
