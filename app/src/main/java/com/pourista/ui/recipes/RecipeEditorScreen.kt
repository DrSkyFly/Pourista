package com.pourista.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pourista.R
import com.pourista.grind.GrinderCatalog
import com.pourista.ui.brew.GrindSheetContent
import com.pourista.ui.listSidePadding
import com.pourista.core.formatClock
import com.pourista.core.formatGrams
import com.pourista.core.formatRatio
import com.pourista.data.model.StepKind
import com.pourista.ui.components.SeriesChart
import com.pourista.ui.components.StepBadge
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
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showGrind by remember { mutableStateOf(false) }
    val stepRows = state.stepRows()

    val stepCard: @Composable (StepRow) -> Unit = { row ->
        StepEditorCard(
            step = row.step,
            startSec = row.startSec,
            cumulative = row.cumulative,
            canMoveUp = viewModel.canMoveStep(row.step.key, -1),
            canMoveDown = viewModel.canMoveStep(row.step.key, 1),
            onChange = { transform -> viewModel.updateStep(row.step.key, transform) },
            onDurationEntered = { viewModel.onDurationEntered(row.step.key) },
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
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    // Built-in recipes are deleted too: if a method is not wanted, it should not
                    // take up room in the list.
                    if (state.id > 0) {
                        IconButton(onClick = { viewModel.delete(onClose) }) {
                            Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete))
                        }
                    }
                    IconButton(
                        onClick = { viewModel.save { onClose() } },
                        enabled = state.canSave,
                    ) {
                        Icon(Icons.Rounded.Check, stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        val side = listSidePadding()
        LazyColumn(
            // The keyboard used to cover the lower fields: the list shrinks by its height, and the
            // field being typed into comes out into view by itself.
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(
                start = side,
                end = side,
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
                            // A short label: the full one wraps onto two lines and gets cut at a
                            // third of the screen width.
                            label = stringResource(R.string.recipe_temp_field),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // The ratio is not set but counted: the water and the dose are entered by hand,
                    // and there is no point fitting one to the other with buttons.
                    Text(
                        text = stringResource(
                            R.string.recipe_ratio_value,
                            formatRatio(state.doseValue, state.waterValue),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.recipe_aeropress_mode),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.recipe_aeropress_mode_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.aeropressMode,
                            onCheckedChange = viewModel::setAeropressMode,
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
                    OutlinedTextField(
                        value = state.filter,
                        onValueChange = viewModel::setFilter,
                        label = { Text(stringResource(R.string.recipe_filter)) },
                        supportingText = { Text(stringResource(R.string.recipe_filter_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    // The grind is most often copied from someone else's recipe for one's own
                    // grinder — the conversion fills both fields itself.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showGrind = true }) {
                            Text(stringResource(R.string.grind_title))
                        }
                    }
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

            // The bloom lives in the first place; delete it and a button takes its place, so that
            // bringing the step back is easier than assembling it again.
            if (!state.steps.hasBloom) {
                item {
                    AddStepButton(
                        text = stringResource(R.string.step_bloom),
                        onClick = viewModel::addBloom,
                    )
                }
            }

            // The drawdown is always last and new steps go before it — so the "Add step" button has
            // to stand where the step will appear.
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

    if (showGrind) {
        GrindConverterSheet(
            settings = settings,
            known = GrinderCatalog.find(LocalContext.current, state.grinder),
            recipeGrind = state.grind,
            onRemember = viewModel::rememberGrindPair,
            onApply = { grinder, setting ->
                viewModel.setGrinder(grinder)
                viewModel.setGrind(setting)
                showGrind = false
            },
            onDismiss = { showGrind = false },
        )
    }
}

/**
 * A step together with the counted start time and cumulative weight: the editor has to read the
 * same way as the guidance does during a pour.
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
        Icon(Icons.Rounded.Add, null)
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
    onDurationEntered: () -> Unit,
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
                StepBadge(kind = step.kind, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                // The bloom and the drawdown have no kind to change: their place is fixed, and
                // turning them into an ordinary step simply means deleting them.
                if (step.kind.isPinned) {
                    Text(
                        text = stringResource(step.kind.labelRes()),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                } else {
                    Box {
                        // The arrow to the left of the name: without it the row looks like a label,
                        // and there is no sign the kind of step can be changed.
                        TextButton(
                            onClick = { kindMenu = true },
                            contentPadding = PaddingValues(start = 4.dp, end = 12.dp),
                        ) {
                            Icon(Icons.Rounded.ArrowDropDown, null)
                            Spacer(Modifier.size(4.dp))
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
                        Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.action_move_up))
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                        Icon(Icons.Rounded.ArrowDownward, stringResource(R.string.action_move_down))
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The pour is fitted to the length at the end of the input rather than at every digit
                // typed: the length gets corrected several times in a row as well.
                NumberField(
                    value = step.duration,
                    onValueChange = { value -> onChange { it.copy(duration = value) } },
                    label = stringResource(R.string.step_duration),
                    onEntered = onDurationEntered,
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
                    // Recipes are written both through the rate and through the pour time. We take
                    // both: the other field is recalculated on the fly, and what goes into the recipe
                    // is the rate either way.
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
    /** The input is over: the field was left, or "Done" was pressed. */
    onEntered: (() -> Unit)? = null,
) {
    val focus = LocalFocusManager.current
    // Leaving a field one was never in does not count as input: otherwise the very first drawing of
    // the step list would recalculate everything by itself.
    var wasFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            imeAction = if (onEntered != null) ImeAction.Done else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier.onFocusChanged { focusState ->
            if (focusState.isFocused) {
                wasFocused = true
            } else if (wasFocused) {
                wasFocused = false
                onEntered?.invoke()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GrindConverterSheet(
    settings: com.pourista.data.prefs.AppSettings,
    known: com.pourista.grind.Grinder?,
    recipeGrind: String,
    onRemember: (String, String, String) -> Unit,
    onApply: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // The grinder from the recipe was recognised — it is the source one, together with its grind
        // setting. Not recognised, and we open on whatever was used last time.
        GrindSheetContent(
            fromId = known?.id ?: settings.grindFromId,
            toId = settings.grindToId,
            setting = if (known != null) recipeGrind else settings.grindSetting,
            onRemember = onRemember,
            onApply = { grinder, setting -> onApply(grinder.name, setting) },
            onCancel = onDismiss,
        )
    }
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
