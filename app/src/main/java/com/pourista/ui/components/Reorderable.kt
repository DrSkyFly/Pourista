package com.pourista.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Dragging items in an [androidx.compose.foundation.lazy.LazyColumn].
 *
 * Compose has nothing of the kind out of the box. A long list cannot be rearranged with arrows:
 * a recipe at the bottom would have to be moved a dozen times, so a grab handle is needed.
 *
 * The order changes during the drag itself — the list under the finger has to look the way it
 * will look afterwards. Saving happens once, when the finger is lifted.
 */
class ReorderState(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onDrop: () -> Unit,
) {
    /** The key of the dragged item: indices move around during a rearrangement. */
    var draggingKey: Any? by mutableStateOf(null)
        private set

    private var draggedDistance by mutableFloatStateOf(0f)
    private var initialOffset = 0
    private var initialSize = 0

    /** How far to shift the card so that it follows the finger. */
    val draggedOffset: Float
        get() {
            val key = draggingKey ?: return 0f
            val item = itemFor(key) ?: return 0f
            return initialOffset + draggedDistance - item.offset
        }

    /** A request to scroll the list when a card is dragged to the edge of the screen. */
    val scrollRequests = Channel<Float>(Channel.CONFLATED)

    fun start(key: Any) {
        val item = itemFor(key) ?: return
        draggingKey = key
        draggedDistance = 0f
        initialOffset = item.offset
        initialSize = item.size
    }

    fun drag(amount: Float) {
        val key = draggingKey ?: return
        draggedDistance += amount

        val start = initialOffset + draggedDistance
        val end = start + initialSize
        val current = itemFor(key) ?: return

        // The neighbour whose place the card has already taken: we shift it at once, so that
        // what is under the finger is the future order rather than a hint about it.
        listState.layoutInfo.visibleItemsInfo
            .filter { it.key is Long && it.key != key }
            .filterNot { it.offset + it.size < start || it.offset > end }
            .firstOrNull { other ->
                if (start > current.offset) end > other.offset + other.size else start < other.offset
            }
            ?.let { target -> onMove(current.index, target.index) }

        requestEdgeScroll(start, end)
    }

    fun stop() {
        if (draggingKey != null) onDrop()
        draggingKey = null
        draggedDistance = 0f
    }

    /**
     * At the edge of the screen the list moves by itself: otherwise dragging a recipe beyond the
     * visible part would be impossible.
     */
    private fun requestEdgeScroll(start: Float, end: Float) {
        val info = listState.layoutInfo
        val top = info.viewportStartOffset + EDGE_PX
        val bottom = info.viewportEndOffset - EDGE_PX
        val scroll = when {
            start < top -> start - top
            end > bottom -> end - bottom
            else -> 0f
        }
        if (scroll != 0f) scrollRequests.trySend(scroll.coerceIn(-EDGE_PX, EDGE_PX) * SCROLL_GAIN)
    }

    private fun itemFor(key: Any): LazyListItemInfo? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    private companion object {
        const val EDGE_PX = 160f
        const val SCROLL_GAIN = 0.12f
    }
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    scope: CoroutineScope,
    onMove: (from: Int, to: Int) -> Unit,
    onDrop: () -> Unit,
): ReorderState {
    val state = remember(listState) { ReorderState(listState, onMove, onDrop) }
    LaunchedEffect(state) {
        for (amount in state.scrollRequests) {
            if (state.draggingKey == null) continue
            scope.launch { listState.scrollBy(amount) }
        }
    }
    return state
}

/**
 * Dragging by a long press.
 *
 * The card has no handle: it took up room in the row and was needed once a year. A long press
 * does not argue with scrolling — the list moves from an ordinary finger movement, while the card
 * is only picked up after a delay.
 */
fun Modifier.reorderByLongPress(
    state: ReorderState,
    key: Any,
    onStart: () -> Unit = {},
): Modifier = pointerInput(key) {
    detectDragGesturesAfterLongPress(
        onDragStart = {
            onStart()
            state.start(key)
        },
        onDrag = { change, amount ->
            change.consume()
            state.drag(amount.y)
        },
        onDragEnd = { state.stop() },
        onDragCancel = { state.stop() },
    )
}
