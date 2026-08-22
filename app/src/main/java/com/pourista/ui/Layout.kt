package com.pourista.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Альбомная раскладка. Смотрим на ориентацию окна: у планшета в портрете
 * ширины хватает с запасом, но раскладывать содержимое по колонкам там
 * незачем — высоты ещё больше.
 */
@Composable
fun isWideLayout(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/** Ширина, дальше которой строку читать неудобно: список держим в этих рамках. */
val ReadableWidth: Dp = 720.dp

/**
 * Боковые поля списка. На узком экране это обычный отступ, на широком —
 * ровно столько, чтобы строка не растягивалась во всю ширину планшета:
 * читать её глазами тогда невозможно.
 */
@Composable
fun listSidePadding(minimum: Dp = 16.dp): Dp {
    val width = LocalConfiguration.current.screenWidthDp.dp
    return maxOf(minimum, (width - ReadableWidth) / 2)
}
