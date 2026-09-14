package com.pourista.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The set of colours. The order is as in the settings list, with [DYNAMIC] by default: it takes the
 * colours from the system wallpaper and exists only from Android 12 on, while on older ones it is
 * the same as [COPPER].
 */
enum class AppPalette { DYNAMIC, CALM, COPPER, FOUR_SIX }

private val LocalBrewAccents = staticCompositionLocalOf { LightAccents }
private val LocalPalette = staticCompositionLocalOf { AppPalette.COPPER }

object AppTheme {
    val accents: BrewAccents
        @Composable @ReadOnlyComposable get() = LocalBrewAccents.current

    val palette: AppPalette
        @Composable @ReadOnlyComposable get() = LocalPalette.current

    /**
     * The colours of the screen header. In the "4:6" palette it is turquoise, as in the original; in
     * the rest it stays an ordinary surface.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun topBarColors(): TopAppBarColors =
        if (palette == AppPalette.FOUR_SIX) {
            TopAppBarDefaults.topAppBarColors(
                containerColor = FourSixBar,
                scrolledContainerColor = FourSixBar,
                titleContentColor = FourSixOnBar,
                actionIconContentColor = FourSixOnBar,
                navigationIconContentColor = FourSixOnBar,
            )
        } else {
            TopAppBarDefaults.topAppBarColors()
        }

    /** In the "4:6" palette the header is rounded at the bottom, as in the original. */
    @Composable
    fun topBarModifier(): Modifier =
        if (palette == AppPalette.FOUR_SIX) {
            Modifier.clip(RoundedCornerShape(bottomStart = BAR_CORNER, bottomEnd = BAR_CORNER))
        } else {
            Modifier
        }

    /**
     * The backing of the recipe tile. It is coloured only where it does not argue with the main
     * colour: next to yellow the turquoise is heavy, and the tile stays dark.
     */
    val recipeTile: Color
        @Composable get() = if (palette == AppPalette.FOUR_SIX) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
}

private val BAR_CORNER = 24.dp

/**
 * Shapes. Larger than the standard ones: Material's 12 dp card rounding is left over from the first
 * versions, while the present language of the system is softer and rounder. Buttons and fields do
 * not take part here, they have a shape of their own.
 */
private val PouristaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun PouristaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    palette: AppPalette = AppPalette.DYNAMIC,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val dynamic = palette == AppPalette.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        palette == AppPalette.FOUR_SIX -> if (dark) FourSixDarkColors else FourSixLightColors
        palette == AppPalette.CALM -> if (dark) CalmDarkColors else CalmLightColors
        dark -> DarkColors
        else -> LightColors
    }
    // The pour guidance lives apart from the Material palette: its colours are obliged to keep their
    // meaning, so with the system wallpaper they stay coffee-coloured.
    val accents = when {
        palette == AppPalette.FOUR_SIX -> if (dark) FourSixDarkAccents else FourSixLightAccents
        palette == AppPalette.CALM -> if (dark) CalmDarkAccents else CalmLightAccents
        dark -> DarkAccents
        else -> LightAccents
    }

    // The system bar icons: under the 4:6 header it is always turquoise, so there they are light
    // regardless of the theme, while at the bottom the colour comes from the app background.
    val view = LocalView.current
    if (!view.isInEditMode) {
        val barDark = dark || palette == AppPalette.FOUR_SIX
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !barDark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalBrewAccents provides accents,
        LocalPalette provides palette,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FutulaTypography,
            shapes = PouristaShapes,
            content = content,
        )
    }
}
