package com.pourista.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pourista.R
import com.pourista.core.formatClock
import com.pourista.core.formatGrams
import com.pourista.core.formatRatio
import com.pourista.data.model.StepKind
import com.pourista.ui.components.SeriesChart
import com.pourista.ui.icon
import com.pourista.ui.theme.AppTheme
import com.pourista.ui.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(
    viewModel: RecipeEditorViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stepRows = state.stepRows()

    val stepCard: @Composable (StepRow) -> Unit = { row ->
        StepEditorCard(
            step = row.step,
            startSec = row.startSec,
            cumulative = row.cumulative,
            canMoveUp = viewModel.canMoveStep(row.step.key, -1),
            canMoveDown = viewModel.canMoveStep(row.step.key, 1),
            onChange = { transform -> viewModel.updateStep(row.step.key, transform) },
            onMoveUp = { viewModel.moveStep(row.step.key, -1) },
            onMoveDown = { viewModel.moveStep(row.step.key, 1) },
            onRemove = { viewModel.removeStep(row.step.key) },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = AppTheme.topBarColors(),
                modifier = AppTheme.topBarModifier(),
                title = {
                    Text(
                        if (state.id > 0) {
                            stringResource(R.string.recipe_edit_title)
                        } else {
                            stringResource(R.string.recipe_new)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    // Встроенные рецепты тоже удаляются: если способ не нужен,
                    // он не должен занимать место в списке.
                    if (state.id > 0) {
                        IconButton(onClick = { viewModel.delete(onClose) }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.action_delete))
                        }
                    }
                    IconButton(
                        onClick = { viewModel.save { onClose() } },
                        enabled = state.canSave,
                    ) {
                        Icon(Icons.Default.Check, stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = stringResource(R.string.recipe_section_main)) {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::setName,
                        label = { Text(stringResource(R.string.recipe_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.brewer,
                        onValueChange = viewModel::setBrewer,
                        label = { Text(stringResource(R.string.recipe_brewer)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NumberField(
                            value = state.dose,
                            onValueChange = viewModel::setDose,
                            label = stringResource(R.string.recipe_dose),
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            value = state.water,
                            onValueChange = viewModel::setWater,
                            label = stringResource(R.string.recipe_water),
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            value = state.temp,
                            onValueChange = viewModel::setTemp,
                            label = stringResource(R.string.recipe_temp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.recipe_ratio_value,
                                formatRatio(state.doseValue, state.waterValue),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.weight(1f))
                        listOf(15f, 16f, 16.7f).forEach { ratio ->
                            AssistChip(
                                onClick = { viewModel.applyRatio(ratio) },
                                label = { Text("1:${formatGrams(ratio)}") },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.recipe_auto_start),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.recipe_auto_start_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.autoStart,
                            onCheckedChange = viewModel::setAutoStart,
                        )
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.recipe_section_grind)) {
                    OutlinedTextField(
                        value = state.grinder,
                        onValueChange = viewModel::setGrinder,
                        label = { Text(stringResource(R.string.recipe_grinder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.grind,
                        onValueChange = viewModel::setGrind,
                        label = { Text(stringResource(R.string.recipe_grind)) },
                        supportingText = { Text(stringResource(R.string.recipe_grind_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.recipe_section_steps)) {
                    if (state.waterMismatch) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.recipe_water_mismatch,
                                    formatGrams(state.stepsWater, 0),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = viewModel::distributeWater) {
                                Text(stringResource(R.string.recipe_distribute))
                            }
                        }
                    }
                    Text(
                        text = stringResource(
                            R.string.recipe_steps_total,
                            formatClock(state.totalSec),
                            formatGrams(state.stepsWater, 0),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Блуминг живёт на первом месте; удалили — на его месте кнопка,
            // чтобы вернуть шаг было куда проще, чем собирать заново.
            if (!state.steps.hasBloom) {
                item {
                    AddStepButton(
                        text = stringResource(R.string.step_bloom),
                        onClick = viewModel::addBloom,
                    )
                }
            }

            // Слив всегда последний, а новые шаги встают перед ним — значит и
            // кнопка «Добавить шаг» должна стоять там же, где появится шаг.
            val drawdownRow = stepRows.lastOrNull()?.takeIf { it.step.kind == StepKind.DRAWDOWN }
            val regularRows = if (drawdownRow == null) stepRows else stepRows.dropLast(1)

            items(regularRows, key = { it.step.key }) { row -> stepCard(row) }

            item {
                AddStepButton(
                    text = stringResource(R.string.step_add),
                    onClick = viewModel::addStep,
                )
            }

            if (drawdownRow != null) {
                item(key = drawdownRow.step.key) { stepCard(drawdownRow) }
            } else {
                item {
                    AddStepButton(
                        text = stringResource(R.string.step_drawdown),
                        onClick = viewModel::addDrawdown,
                    )
                }
            }

            val preview = state.previewSeries()
            if (preview.size > 1) {
                item {
                    SectionCard(title = stringResource(R.string.recipe_preview)) {
                        SeriesChart(values = preview)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatClock(0),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = formatClock(state.totalSec),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.recipe_section_extra)) {
                    OutlinedTextField(
                        value = state.bean,
                        onValueChange = viewModel::setBean,
                        label = { Text(stringResource(R.string.recipe_bean)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.roaster,
                        onValueChange = viewModel::setRoaster,
                        label = { Text(stringResource(R.string.recipe_roaster)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = viewModel::setNotes,
                        label = { Text(stringResource(R.string.recipe_notes)) },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Шаг вместе с посчитанными временем начала и накопительным весом: редактор
 * должен читаться так же, как подсказка во время пролива.
 */
private data class StepRow(
    val step: EditableStep,
    val startSec: Int,
    val cumulative: Float,
)

private fun EditorState.stepRows(): List<StepRow> {
    var start = 0
    var cumulative = 0f
    return steps.map { step ->
        val target = cumulative + step.deltaGrams
        val row = StepRow(step = step, startSec = start, cumulative = target)
        start += step.durationSec
        cumulative = target
        row
    }
}

@Composable
private fun AddStepButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, null)
        Spacer(Modifier.size(6.dp))
        Text(text)
    }
}

@Composable
private fun StepEditorCard(
    step: EditableStep,
    startSec: Int,
    cumulative: Float,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: ((EditableStep) -> EditableStep) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    var kindMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(step.kind.icon(), null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                // У блуминга и слива вид менять нечем: место у них закреплено,
                // и превратить их в обычный шаг значит просто удалить.
                if (step.kind.isPinned) {
                    Text(
                        text = stringResource(step.kind.labelRes()),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                } else {
                    Box {
                        TextButton(onClick = { kindMenu = true }) {
                            Text(stringResource(step.kind.labelRes()))
                        }
                        DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                            StepKind.selectable.forEach { kind ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(kind.labelRes())) },
                                    leadingIcon = { Icon(kind.icon(), null) },
                                    onClick = {
                                        kindMenu = false
                                        onChange { it.copy(kind = kind) }
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatClock(startSec),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!step.kind.isPinned) {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                        Icon(Icons.Default.ArrowUpward, stringResource(R.string.action_move_up))
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                        Icon(Icons.Default.ArrowDownward, stringResource(R.string.action_move_down))
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, stringResource(R.string.action_delete))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NumberField(
                    value = step.duration,
                    onValueChange = { value -> onChange { it.copy(duration = value) } },
                    label = stringResource(R.string.step_duration),
                    modifier = Modifier.weight(1f),
                )
                if (step.kind.isPour) {
                    NumberField(
                        value = step.water,
                        onValueChange = { value -> onChange { it.withWater(value) } },
                        label = stringResource(R.string.step_add_water),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (step.kind.isPour) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Рецепты пишут и через скорость, и через время влива.
                    // Принимаем оба: второе поле пересчитывается на лету, а в
                    // рецепт всё равно уходит скорость.
                    NumberField(
                        value = step.flow,
                        onValueChange = { value -> onChange { it.withFlow(value) } },
                        label = stringResource(R.string.step_flow_rate),
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = step.pourSec,
                        onValueChange = { value -> onChange { it.withPourSeconds(value) } },
                        label = stringResource(R.string.step_pour_seconds),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (step.pourTooLong) {
                    Text(
                        text = stringResource(
                            R.string.step_pour_too_long,
                            step.pourSeconds,
                            step.durationSec,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.step_cumulative,
                    formatGrams(cumulative, 0),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            OutlinedTextField(
                value = step.title,
                onValueChange = { value -> onChange { it.copy(title = value) } },
                label = { Text(stringResource(R.string.step_title)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            content()
        }
    }
}
