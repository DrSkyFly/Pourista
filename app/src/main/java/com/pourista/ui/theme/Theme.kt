package com.pourista.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Набор цветов. [DYNAMIC] берёт их из обоев системы и есть только с Android 12;
 * на более старых он равносилен [COPPER].
 */
enum class AppPalette { COPPER, FOUR_SIX, DYNAMIC }

private val LocalBrewAccents = staticCompositionLocalOf { LightAccents }
private val LocalPalette = staticCompositionLocalOf { AppPalette.COPPER }

object AppTheme {
    val accents: BrewAccents
        @Composable @ReadOnlyComposable get() = LocalBrewAccents.current

    val palette: AppPalette
        @Composable @ReadOnlyComposable get() = LocalPalette.current

    /**
     * Цвета шапки экрана. В палитре «4:6» она бирюзовая, как в оригинале;
     * в остальных остаётся обычной поверхностью.
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

    /** В палитре «4:6» шапка снизу скруглена, как в оригинале. */
    @Composable
    fun topBarModifier(): Modifier =
        if (palette == AppPalette.FOUR_SIX) {
            Modifier.clip(RoundedCornerShape(bottomStart = BAR_CORNER, bottomEnd = BAR_CORNER))
        } else {
            Modifier
        }

    /**
     * Подложка плитки рецепта. Цветная она только там, где не спорит с
     * основным цветом: рядом с жёлтым бирюза тяжела, и плитка остаётся тёмной.
     */
    val recipeTile: Color
        @Composable get() = if (palette == AppPalette.FOUR_SIX) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
}

private val BAR_CORNER = 24.dp

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
        dark -> DarkColors
        else -> LightColors
    }
    // Подсказки пролива живут отдельно от палитры Material: их цвета обязаны
    // сохранять смысл, поэтому у обоев системы они остаются кофейными.
    val accents = when {
        palette == AppPalette.FOUR_SIX -> if (dark) FourSixDarkAccents else FourSixLightAccents
        dark -> DarkAccents
        else -> LightAccents
    }

    CompositionLocalProvider(
        LocalBrewAccents provides accents,
        LocalPalette provides palette,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FutulaTypography,
            content = content,
        )
    }
}
