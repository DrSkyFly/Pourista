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
 * Перетаскивание элементов в [androidx.compose.foundation.lazy.LazyColumn].
 *
 * В Compose такого из коробки нет. Стрелками длинный список не переставишь:
 * рецепт снизу пришлось бы двигать десяток раз, поэтому нужен захват за ручку.
 *
 * Порядок меняется прямо во время перетаскивания — список под пальцем должен
 * выглядеть так, как он будет выглядеть после. Сохранение происходит один раз,
 * когда палец отпущен.
 */
class ReorderState(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onDrop: () -> Unit,
) {
    /** Ключ перетаскиваемого элемента: индексы во время перестановки едут. */
    var draggingKey: Any? by mutableStateOf(null)
        private set

    private var draggedDistance by mutableFloatStateOf(0f)
    private var initialOffset = 0
    private var initialSize = 0

    /** Куда сдвинуть карточку, чтобы она шла за пальцем. */
    val draggedOffset: Float
        get() {
            val key = draggingKey ?: return 0f
            val item = itemFor(key) ?: return 0f
            return initialOffset + draggedDistance - item.offset
        }

    /** Просьба подкрутить список, когда карточку тянут за край экрана. */
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

        // Сосед, на место которого карточка уже заехала: сдвигаем его сразу,
        // чтобы под пальцем был будущий порядок, а не подсказка о нём.
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
     * У края экрана список едет сам: иначе перетащить рецепт дальше видимой
     * части было бы невозможно.
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
 * Перетаскивание по долгому нажатию.
 *
 * Ручки у карточки нет: она занимала место в строке, а нужна была раз в год.
 * Долгое нажатие с прокруткой не спорит — список едет от обычного движения
 * пальца, а карточка берётся только после задержки.
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
