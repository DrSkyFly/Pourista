package com.pourista.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The landscape layout. We look at the orientation of the window: a tablet in portrait has width
 * to spare, but there is no point laying the content out in columns there — there is even more
 * height.
 */
@Composable
fun isWideLayout(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/** The width beyond which a line is uncomfortable to read: the list is kept inside it. */
val ReadableWidth: Dp = 720.dp

/**
 * The side padding of a list. On a narrow screen it is ordinary padding, on a wide one exactly
 * enough for the line not to stretch across the whole width of a tablet: reading it by eye would
 * then be impossible.
 */
@Composable
fun listSidePadding(minimum: Dp = 16.dp): Dp {
    val width = LocalConfiguration.current.screenWidthDp.dp
    return maxOf(minimum, (width - ReadableWidth) / 2)
}
