package com.pourista.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pourista.R
import com.pourista.ui.listSidePadding
import com.pourista.core.formatGrams
import com.pourista.core.formatRatio
import com.pourista.core.formatTimerWithTenths
import com.pourista.data.model.BrewRecord
import com.pourista.ui.components.EmptyState
import com.pourista.ui.components.SearchField
import com.pourista.ui.components.SeriesChart
import kotlinx.coroutines.launch
import java.text.DateFormat
import com.pourista.ui.theme.AppTheme
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpen: (Long) -> Unit,
    onOpenDraft: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val brews by viewModel.brews.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    // Шапка уезжает при прокрутке: в истории каждая карточка высокая, и
    // отдавать ей строку с одним словом на весь экран жалко.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedText = stringResource(R.string.history_deleted)
    val undoText = stringResource(R.string.action_undo)

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_history)) },
                colors = AppTheme.topBarColors(),
                modifier = AppTheme.topBarModifier(),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        val side = listSidePadding()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = side,
                end = side,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SearchField(value = query, onValueChange = viewModel::setQuery)
            }

            if (brews.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.History,
                        text = stringResource(R.string.history_empty),
                    )
                }
            }

            items(brews, key = { it.id }) { record ->
                val recordedName = stringResource(R.string.recipe_recorded_name)
                SwipeToDelete(
                    onDelete = {
                        viewModel.delete(record)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = deletedText,
                                actionLabel = undoText,
                            )
                            if (result == SnackbarResult.ActionPerformed) viewModel.restore(record)
                        }
                    },
                    // Появление и уход карточки в списке анимируются: без этого
                    // удалённая запись исчезает рывком, а соседние прыгают.
                    modifier = Modifier.animateItem(),
                ) {
                    BrewHistoryCard(
                        record = record,
                        onClick = { onOpen(record.id) },
                        onMakeRecipe = {
                            val name = record.recipeName?.takeIf { it.isNotBlank() }
                                ?: "$recordedName ${formatDateTime(record.brewedAt)}"
                            if (viewModel.buildRecipe(record, name)) onOpenDraft()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Смахивание записи влево вместо корзины в каждой карточке. Подтверждения нет:
 * вместо вопроса даётся отмена в снек-баре — так быстрее и тому, кто смахнул
 * нарочно, и тому, кто задел случайно.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { onDelete() },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        content = { content() },
    )
}

@Composable
private fun BrewHistoryCard(
    record: BrewRecord,
    onClick: () -> Unit,
    onMakeRecipe: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = record.recipeName?.takeIf { it.isNotBlank() }
                            ?: record.notes.brewer?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.history_untitled),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = formatDateTime(record.brewedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Рецепт по проливу — только когда есть что разбирать:
                // без весов график пустой, и собирать шаги не из чего.
                if (record.weightSeries.size > 1) {
                    IconButton(onClick = onMakeRecipe) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = stringResource(R.string.history_make_recipe),
                        )
                    }
                }
                Text(
                    text = formatTimerWithTenths(record.elapsedMs),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            // Заваривали без весов — граммов в записи нет, и строка «0 г → 0 г»
            // только сбивала бы с толку. Тогда показываем одну дозу, если её ввели.
            val summary = when {
                record.weightGrams > 0f -> stringResource(
                    R.string.history_summary,
                    formatGrams(record.doseGrams),
                    formatGrams(record.weightGrams, 0),
                    formatRatio(record.doseGrams, record.weightGrams),
                )

                record.doseGrams > 0f -> stringResource(
                    R.string.history_dose_only,
                    formatGrams(record.doseGrams),
                )

                else -> null
            }
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // Помол без кофемолки — половина сведений: «22» на разных мельницах
            // означает разное. Пишем одной строкой, чтобы карточка не росла.
            val grind = listOfNotNull(
                record.notes.grinder?.takeIf { it.isNotBlank() },
                record.notes.grindSetting?.takeIf { it.isNotBlank() }
                    ?.let { stringResource(R.string.recipe_grind_value, it) },
            ).joinToString(" · ").takeIf { it.isNotBlank() }
            if (grind != null) {
                Text(
                    text = grind,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (record.weightSeries.size > 1) {
                // В списке график — не чертёж, а росчерк: по нему узнают форму
                // пролива, а цифры смотрят уже в карточке заваривания.
                SeriesChart(
                    values = record.weightSeries,
                    modifier = Modifier.padding(top = 12.dp),
                    height = 56.dp,
                    showAxis = false,
                )
            }
        }
    }
}

internal fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
