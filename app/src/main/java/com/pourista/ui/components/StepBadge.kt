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
 * Значок этапа в кружке.
 *
 * Кружок обведён, а не залит: подложки под этапами всюду разные — плитка
 * рецепта, карточка редактора, подкрашенная темпом подсказка, — и любая заливка
 * на одной из них слилась бы с фоном. Обводка держится на всех.
 *
 * Заливку получает только текущий этап: он должен находиться взглядом сразу, и
 * ради этого разница с остальными обязана быть больше, чем оттенок текста.
 */
@Composable
fun StepBadge(
    kind: StepKind,
    modifier: Modifier = Modifier,
    size: Dp = StepBadgeSize,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    ring: Color = MaterialTheme.colorScheme.outlineVariant,
    /** Залитый кружок: тогда [tint] — цвет значка поверх заливки. */
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

/** Размер кружка по умолчанию: значок внутри выходит вровень со строкой текста. */
val StepBadgeSize = 30.dp

/** Толщина линии между кружками — она же толщина обводки. */
val StepLineWidth = 1.5.dp

private val RING_WIDTH = StepLineWidth
private const val ICON_SHARE = 0.55f
