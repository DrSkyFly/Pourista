package com.pourista.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pourista.data.model.StepKind
import com.pourista.ui.icon

/**
 * A step icon in a circle.
 *
 * The circle is outlined rather than filled: the backings under steps differ everywhere — the
 * recipe tile, the editor card, the guidance tinted by the pace — and any fill would merge with
 * one of them. An outline holds on all of them.
 *
 * Only the current step gets a fill: it has to be found by the eye at once, and for that the
 * difference from the rest must be more than a shade of text.
 */
@Composable
fun StepBadge(
    kind: StepKind,
    modifier: Modifier = Modifier,
    size: Dp = StepBadgeSize,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    ring: Color = MaterialTheme.colorScheme.outlineVariant,
    /** A filled circle: [tint] is then the colour of the icon over the fill. */
    fill: Color? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .then(
                if (fill != null) {
                    Modifier.background(fill, CircleShape)
                } else {
                    Modifier.border(RING_WIDTH, ring, CircleShape)
                }
            ),
    ) {
        Icon(
            imageVector = kind.icon(),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * ICON_SHARE),
        )
    }
}

/** The default circle size: the icon inside comes out level with a line of text. */
val StepBadgeSize = 30.dp

/** The thickness of the line between the circles — the outline is the same. */
val StepLineWidth = 1.5.dp

private val RING_WIDTH = StepLineWidth
private const val ICON_SHARE = 0.55f
