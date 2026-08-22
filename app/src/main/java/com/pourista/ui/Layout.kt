package com.pourista.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Широкий экран: планшет или телефон, повёрнутый набок.
 *
 * Считаем по ширине окна, а не по признаку планшета: приложение живёт и в
 * половине разделённого экрана, и в свободном окне — размер там ничего не
 * знает о самом устройстве.
 */
@Composable
fun isWideLayout(): Boolean = LocalConfiguration.current.screenWidthDp >= WIDE_WIDTH_DP

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

private const val WIDE_WIDTH_DP = 600
