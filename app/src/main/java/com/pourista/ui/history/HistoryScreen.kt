package com.pourista.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pourista.R
import com.pourista.ui.listSidePadding
import com.pourista.core.formatGrams
import com.pourista.core.formatRatio
import com.pourista.core.formatTimerWithTenths
import com.pourista.data.model.BrewRecord
import com.pourista.ui.components.SeriesChart
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_history)) },
                colors = AppTheme.topBarColors(),
                modifier = AppTheme.topBarModifier(),
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
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text(stringResource(R.string.action_search)) },
                )
            }

            if (brews.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                    )
                }
            }

            items(brews, key = { it.id }) { record ->
                val recordedName = stringResource(R.string.recipe_recorded_name)
                BrewHistoryCard(
                    record = record,
                    onClick = { onOpen(record.id) },
                    onMakeRecipe = {
                        val name = record.recipeName?.takeIf { it.isNotBlank() }
                            ?: "$recordedName ${formatDateTime(record.brewedAt)}"
                        if (viewModel.buildRecipe(record, name)) onOpenDraft()
                    },
                    onDelete = { viewModel.delete(record) },
                )
            }
        }
    }
}

@Composable
private fun BrewHistoryCard(
    record: BrewRecord,
    onClick: () -> Unit,
    onMakeRecipe: () -> Unit,
    onDelete: () -> Unit,
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
                    IconButton(onClick = onMakeRecipe, modifier = Modifier.size(ICON_BUTTON, ICON_TOUCH)) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = stringResource(R.string.history_make_recipe),
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(ICON_BUTTON, ICON_TOUCH)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatTimerWithTenths(record.elapsedMs),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 2.dp),
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

            val grind = record.notes.grindSetting
            if (!grind.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.recipe_grind_value, grind),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (record.weightSeries.size > 1) {
                SeriesChart(
                    values = record.weightSeries,
                    modifier = Modifier.padding(top = 12.dp),
                    height = 64.dp,
                )
            }
        }
    }
}

internal fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

/**
 * Кнопки в строке заголовка. Зазор между значками — это рамка кнопки, а не
 * сами значки: сужаем её до ширины значка с небольшим запасом, высоту под
 * палец оставляем прежней.
 */
private val ICON_BUTTON = 30.dp
private val ICON_TOUCH = 40.dp
