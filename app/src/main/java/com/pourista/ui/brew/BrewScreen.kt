package com.pourista.ui.brew

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pourista.R
import com.pourista.brew.BrewEvent
import com.pourista.brew.BrewPhase
import com.pourista.brew.Guidance
import com.pourista.brew.Pace
import com.pourista.brew.StepPhase
import com.pourista.core.formatClock
import com.pourista.core.formatGrams
import com.pourista.core.formatRatio
import com.pourista.core.formatTimerWithTenths
import com.pourista.data.model.Recipe
import com.pourista.scale.ConnectionStatus
import com.pourista.scale.ScaleRepository
import com.pourista.ui.icon
import com.pourista.ui.labelRes
import com.pourista.ui.components.LabeledChart
import com.pourista.ui.components.PourGauge
import com.pourista.ui.components.RecipeStepsList
import com.pourista.ui.components.StatTile
import com.pourista.ui.components.StepsToggleInline
import com.pourista.ui.components.StepRing
import com.pourista.ui.components.StepTimeline
import com.pourista.ui.theme.AppTheme
import com.pourista.ui.theme.MetricValueStyle
import com.pourista.ui.theme.WeightReadoutStyle
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewScreen(
    viewModel: BrewViewModel,
    onEditRecipe: (Long) -> Unit,
    onOpenDraft: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val brew by viewModel.brew.collectAsStateWithLifecycle()
    val scale by viewModel.scale.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val cues = rememberBrewCuePlayer()
    var showRecipePicker by remember { mutableStateOf(false) }
    var showDoseDialog by remember { mutableStateOf(false) }

    // Развёрнутый рецепт нужен до старта — свериться с планом пролива. Со
    // стартом он сворачивается: дальше на экране важны вес и подсказка шага.
    var recipeExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(brew.phase) {
        if (brew.phase == BrewPhase.RUNNING) recipeExpanded = false
    }

    // Показывать ли вес главной цифрой. Идём за живой связью: выключили весы —
    // экран становится про время. Единственная поблажка: посреди пролива режим
    // назад не переключаем, чтобы вёрстка не прыгала от случайного разрыва.
    val brewing = brew.phase == BrewPhase.RUNNING || brew.phase == BrewPhase.PAUSED
    var weightMode by remember { mutableStateOf(scale.isConnected) }
    LaunchedEffect(scale.isConnected, brewing) {
        if (scale.isConnected) weightMode = true else if (!brewing) weightMode = false
    }

    val savedMessage = stringResource(R.string.brew_saved)
    val draftReady by viewModel.draftReady.collectAsStateWithLifecycle()

    // Запись закончена — сразу открываем редактор с готовыми числами.
    LaunchedEffect(draftReady) {
        if (draftReady) {
            viewModel.clearDraftReady()
            onOpenDraft()
        }
    }

    LaunchedEffect(saved) {
        if (!saved) return@LaunchedEffect
        snackbarHostState.showSnackbar(savedMessage)
        viewModel.clearSaved()
    }

    LaunchedEffect(cues, settings.soundCues, settings.hapticCues, settings.countdownCue) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is BrewEvent.StepChanged -> cues.stepChange(settings.soundCues, settings.hapticCues)
                is BrewEvent.Countdown ->
                    if (settings.countdownCue) cues.countdown(settings.soundCues, settings.hapticCues)

                is BrewEvent.NearTarget -> cues.nearTarget(settings.soundCues, settings.hapticCues)

                BrewEvent.Finished -> cues.finished(settings.soundCues, settings.hapticCues)
            }
        }
    }

    val view = LocalView.current
    val keepScreenOn = settings.keepScreenOn && brew.phase == BrewPhase.RUNNING
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val blePermissions = remember { ScaleRepository.requiredPermissions() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) viewModel.connect()
    }
    val onConnectClick = {
        if (viewModel.hasScalePermissions()) {
            viewModel.toggleConnection()
        } else {
            permissionLauncher.launch(blePermissions)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_brew)) },
                actions = {
                    ConnectionAction(
                        status = scale.status,
                        battery = scale.batteryPercent,
                        onClick = onConnectClick,
                    )
                    IconButton(onClick = viewModel::reset) {
                        Icon(Icons.Default.RestartAlt, stringResource(R.string.action_reset))
                    }
                },
            )
        },
        bottomBar = {
            // Своя подложка обязательна: без неё прокручиваемый контент
            // просвечивает сквозь панель управления.
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    BrewControls(
                        phase = brew.phase,
                        connected = scale.isConnected,
                        weightMode = weightMode,
                        autoStart = brew.autoStartArmed,
                        onToggleAutoStart = viewModel::toggleAutoStart,
                        onTare = viewModel::tare,
                        onDose = viewModel::captureDose,
                        onToggleTimer = viewModel::toggleTimer,
                        onFinish = viewModel::finish,
                    )
                    bottomBar()
                }
            }
        },
    ) { padding ->
        // Во время пролива плитка рецепта уходит: выбирать рецепт уже поздно, а его
        // цифры дублирует подсказка шага. Показания при этом закрепляются сверху,
        // чтобы вес, цель и таймер были на виду, а шаги и графики листались под ними.
        val readout: @Composable () -> Unit = {
            ReadoutCard(
                weightGrams = brew.weightGrams,
                doseGrams = brew.doseGrams,
                flowRate = brew.flowRate,
                elapsedMs = brew.elapsedMs,
                targetGrams = brew.guidance?.targetEndGrams?.takeIf { brewing },
                remainingGrams = brew.guidance
                    ?.takeIf { brewing && it.stepPhase == StepPhase.POURING }
                    ?.remainingGrams,
                unitLabel = stringResource(R.string.unit_gram),
                weightMode = weightMode,
                onDoseClick = { showDoseDialog = true },
            )
        }

        Column(Modifier.fillMaxWidth()) {
            if (brewing) {
                Box(
                    Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = 12.dp,
                    )
                ) { readout() }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (brewing) 0.dp else padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!brewing) {
                    item {
                        if (brew.recording) {
                            RecordingCard(
                                pours = brew.recordedPours,
                                onCancel = viewModel::cancelRecording,
                            )
                        } else {
                            RecipeSummaryCard(
                                recipe = brew.recipe,
                                scaled = brew.recipeScaled,
                                keepWater = settings.keepRecipeWater,
                                expanded = recipeExpanded,
                                onToggleExpanded = { recipeExpanded = !recipeExpanded },
                                onKeepWater = viewModel::toggleKeepRecipeWater,
                                onPick = { showRecipePicker = true },
                                onRecord = viewModel::startRecording,
                                onClear = { viewModel.selectRecipe(null) },
                                onEdit = onEditRecipe,
                            )
                        }
                    }
                    item { readout() }
                }

                val guidance = brew.guidance
                if (guidance != null) {
                    item {
                        GuidanceCard(
                            guidance = guidance,
                            currentGrams = brew.weightGrams,
                            recipe = brew.recipe,
                            phase = brew.phase,
                            measuring = scale.isConnected,
                        )
                    }
                }

                if (weightMode && brew.weightSeries.size > 1) {
                    item {
                        ChartsCard(
                            weights = brew.weightSeries,
                            flows = brew.flowSeries,
                            guides = brew.recipe?.steps
                                ?.filter { it.kind.isPour }
                                ?.map { it.targetWaterGrams }
                                .orEmpty(),
                            // Ось растягиваем до цели следующего шага, иначе его
                            // линия оказалась бы за верхним краем графика.
                            focusMax = brew.guidance?.let { g ->
                                g.nextStep?.targetWaterGrams ?: g.targetEndGrams
                            },
                            targetFlowRate = brew.guidance?.targetFlowRate,
                            flowAvg = brew.flowRateAvg,
                        )
                    }
                }
            }
        }
    }

    if (showRecipePicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showRecipePicker = false },
            sheetState = sheetState,
        ) {
            RecipePickerContent(
                recipes = recipes,
                onSelect = {
                    viewModel.selectRecipe(it)
                    showRecipePicker = false
                },
            )
        }
    }

    if (showDoseDialog) {
        DoseDialog(
            initial = brew.doseGrams,
            onConfirm = {
                viewModel.setDose(it)
                showDoseDialog = false
            },
            onDismiss = { showDoseDialog = false },
        )
    }
}

@Composable
private fun ConnectionAction(
    status: ConnectionStatus,
    battery: Int?,
    onClick: () -> Unit,
) {
    val icon = when (status) {
        ConnectionStatus.CONNECTED -> Icons.Default.BluetoothConnected
        ConnectionStatus.SCANNING, ConnectionStatus.CONNECTING, ConnectionStatus.RECONNECTING ->
            Icons.AutoMirrored.Filled.BluetoothSearching

        ConnectionStatus.IDLE -> Icons.Default.Bluetooth
    }
    val connected = status == ConnectionStatus.CONNECTED

    // Нет связи — значок не просто гаснет, а мигает красным, и так всегда:
    // забытые весы должны бросаться в глаза раньше, чем начнётся пролив.
    // Заваривать без весов приложение позволяет, но молча делать вид, что всё
    // в порядке, когда их просто забыли включить, — не его дело.
    val alarming = !connected
    val blink = rememberInfiniteTransition(label = "bluetooth")
    val alpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(BLINK_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bluetoothAlpha",
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (connected && battery != null) {
            Text(
                text = "$battery%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(R.string.action_connect),
                tint = when {
                    connected -> AppTheme.accents.onTrack
                    alarming -> AppTheme.accents.alarm
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = if (alarming) Modifier.alpha(alpha) else Modifier,
            )
        }
    }
}

/** Полпериода мигания: заметно, но не мельтешит. */
private const val BLINK_MS = 700

@Composable
private fun RecipeSummaryCard(
    recipe: Recipe?,
    scaled: Boolean,
    keepWater: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onKeepWater: () -> Unit,
    onPick: () -> Unit,
    onRecord: () -> Unit,
    onClear: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (recipe == null) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.brew_no_recipe_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.brew_no_recipe_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onPick) {
                        Text(stringResource(R.string.brew_pick_recipe))
                    }
                    OutlinedButton(onClick = onRecord) {
                        Text(stringResource(R.string.brew_record_recipe))
                    }
                }
            }
            return@Card
        }

        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(text = recipe.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = recipe.brewer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onPick) { Text(stringResource(R.string.brew_change_recipe)) }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RecipeFact(
                    label = stringResource(R.string.recipe_dose),
                    value = "${formatGrams(recipe.doseGrams)} ${stringResource(R.string.unit_gram)}",
                    modifier = Modifier.weight(1f),
                )
                RecipeFact(
                    label = stringResource(R.string.recipe_water),
                    value = "${formatGrams(recipe.waterGrams, 0)} ${stringResource(R.string.unit_gram)}",
                    modifier = Modifier.weight(1f),
                )
                RecipeFact(
                    label = stringResource(R.string.recipe_ratio),
                    value = formatRatio(recipe.doseGrams, recipe.waterGrams),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RecipeFact(
                    label = stringResource(R.string.recipe_temp),
                    value = "${recipe.waterTempC} °C",
                    modifier = Modifier.weight(1f),
                )
                RecipeFact(
                    label = stringResource(R.string.recipe_grind),
                    value = recipe.grindSetting?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.value_not_set),
                    modifier = Modifier.weight(2f),
                )
            }

            // Заметки к рецепту — это то, что нужно знать до начала: сколько
            // раз качнуть воронку, чем этот способ отличается. Показываем
            // целиком, обрезать такое нельзя.
            val notes = recipe.notes?.takeIf { it.isNotBlank() }
            if (notes != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (scaled) {
                Text(
                    text = stringResource(
                        R.string.recipe_scaled_note,
                        formatGrams(recipe.doseGrams),
                        formatGrams(recipe.waterGrams, 0),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            // Своих отступов у кнопки и галки нет: у кнопки и без того площадь
            // под палец в 48 dp, и любая добавка расталкивает их слишком далеко.
            if (recipe.steps.isNotEmpty()) {
                StepsToggleInline(expanded = expanded, onToggle = onToggleExpanded)
                AnimatedVisibility(visible = expanded) {
                    RecipeStepsList(
                        steps = recipe.steps,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            // Иногда кофе сыплют больше специально, ради плотной чашки: тогда
            // объём воды должен остаться рецептурным, а не поехать за дозой.
            FilterChip(
                selected = keepWater,
                onClick = onKeepWater,
                label = { Text(stringResource(R.string.brew_fixed_water)) },
                leadingIcon = if (keepWater) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else {
                    null
                },
                modifier = if (recipe.steps.isEmpty()) Modifier.padding(top = 8.dp) else Modifier,
            )
            Text(
                text = stringResource(R.string.brew_fixed_water_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { onEdit(recipe.id) }) {
                    Text(stringResource(R.string.action_open_recipe))
                }
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.brew_without_recipe))
                }
            }
        }
    }
}

/** Карточка режима записи: пока идёт запись, выбирать рецепт незачем. */
@Composable
private fun RecordingCard(pours: Int, onCancel: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.brew_recording_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.brew_recording_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stringResource(R.string.brew_recording_pours, pours),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = onCancel, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.brew_recording_cancel))
            }
        }
    }
}

@Composable
private fun RecipeFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * Показания. С весами главная цифра — вес: по ней ведут пролив. Без весов
 * вес всегда ноль, и его место занимает время: заваривание по рецепту это
 * прежде всего таймер, а граммы человек отмеряет чем есть.
 */
@Composable
private fun ReadoutCard(
    weightGrams: Float,
    doseGrams: Float,
    flowRate: Float,
    elapsedMs: Long,
    targetGrams: Float?,
    remainingGrams: Float?,
    unitLabel: String,
    /** Показывать вес: весы на связи. Иначе главной цифрой идёт время. */
    weightMode: Boolean,
    onDoseClick: () -> Unit,
) {
    if (!weightMode) {
        TimerReadoutCard(
            elapsedMs = elapsedMs,
            doseGrams = doseGrams,
            targetGrams = targetGrams,
            unitLabel = unitLabel,
            onDoseClick = onDoseClick,
        )
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.readout_weight),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(text = formatGrams(weightGrams), style = WeightReadoutStyle)
                        Text(
                            text = " $unitLabel",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    // Цель шага стоит рядом с текущим весом: взгляд не должен
                    // прыгать по экрану, чтобы понять, сколько ещё лить.
                    if (targetGrams != null) {
                        Text(
                            text = stringResource(R.string.readout_target),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${formatGrams(targetGrams, 0)} $unitLabel",
                            style = MetricValueStyle,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (remainingGrams != null) {
                            Text(
                                text = stringResource(
                                    R.string.readout_remaining,
                                    formatGrams(remainingGrams, 0),
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(
                        text = stringResource(R.string.readout_time),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = formatTimerWithTenths(elapsedMs), style = MetricValueStyle)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(Modifier.weight(1f)) {
                    TextButton(onClick = onDoseClick, contentPadding = PaddingValues(0.dp)) {
                        StatTile(
                            label = stringResource(R.string.readout_dose),
                            value = formatGrams(doseGrams),
                            unit = stringResource(R.string.unit_gram),
                        )
                    }
                }
                StatTile(
                    label = stringResource(R.string.readout_flow),
                    value = formatGrams(flowRate),
                    unit = stringResource(R.string.unit_gram_per_second),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.readout_ratio),
                    value = formatRatio(doseGrams, weightGrams),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Показания без весов: время крупно, рядом — цель шага, ниже введённая доза. */
@Composable
private fun TimerReadoutCard(
    elapsedMs: Long,
    doseGrams: Float,
    targetGrams: Float?,
    unitLabel: String,
    onDoseClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.readout_time),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = formatTimerWithTenths(elapsedMs), style = WeightReadoutStyle)
                }
                if (targetGrams != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.readout_target),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${formatGrams(targetGrams, 0)} $unitLabel",
                            style = MetricValueStyle,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            // Дозу вводят руками: её отмеряют кухонными весами или меркой,
            // а приложению она нужна, чтобы пересчитать рецепт под неё.
            TextButton(onClick = onDoseClick, contentPadding = PaddingValues(0.dp)) {
                StatTile(
                    label = stringResource(R.string.readout_dose),
                    value = formatGrams(doseGrams),
                    unit = stringResource(R.string.unit_gram),
                )
            }
        }
    }
}

@Composable
private fun GuidanceCard(
    guidance: Guidance,
    currentGrams: Float,
    recipe: Recipe?,
    phase: BrewPhase,
    /** Весы на связи: только тогда есть смысл говорить об остатке и темпе. */
    measuring: Boolean,
) {
    val accents = AppTheme.accents
    // До старта карточка — это предпросмотр первого шага. Судить о темпе там не о
    // чем: время не идёт, и любой вес на весах выглядел бы опережением.
    val started = phase == BrewPhase.RUNNING || phase == BrewPhase.PAUSED
    val running = phase == BrewPhase.RUNNING
    val paceColor = when {
        !started -> MaterialTheme.colorScheme.primary
        guidance.pace == Pace.TOO_FAST -> accents.tooFast
        guidance.pace == Pace.TOO_SLOW -> accents.tooSlow
        else -> accents.onTrack
    }
    val container = when {
        !started -> MaterialTheme.colorScheme.surfaceContainer
        guidance.pace == Pace.TOO_FAST -> accents.tooFastContainer
        guidance.pace == Pace.TOO_SLOW -> accents.tooSlowContainer
        else -> accents.onTrackContainer
    }
    // Когда влив уже закончен, называть шаг «Проливом» нельзя — мы ждём.
    val stepName = if (guidance.stepPhase == StepPhase.WAITING && guidance.step.kind.isPour) {
        stringResource(R.string.step_wait)
    } else {
        stringResource(guidance.step.kind.labelRes())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (running) container else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = guidance.step.kind.icon(),
                    contentDescription = null,
                    tint = paceColor,
                )
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = guidance.step.title?.takeIf { it.isNotBlank() } ?: stepName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    // Плитка рецепта во время пролива скрыта, поэтому название
                    // рецепта живёт здесь — строка и так есть, высоты не добавляет.
                    val position = stringResource(
                        R.string.guidance_step_position,
                        guidance.stepIndex + 1,
                        guidance.stepCount,
                    )
                    Text(
                        text = recipe?.name?.let { "$position · $it" } ?: position,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${formatClock(guidance.step.startSec)}–${formatClock(guidance.step.endSec)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepRing(
                    progress = guidance.stepProgress,
                    accent = paceColor,
                    centerText = "${guidance.secondsLeftInStep}",
                    caption = stringResource(
                        if (started) R.string.guidance_seconds_left else R.string.guidance_seconds_total
                    ),
                    markerFraction = guidance.pourEndFraction.takeIf { it > 0f },
                )
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    if (guidance.stepPhase == StepPhase.POURING) {
                        Text(
                            text = stringResource(
                                R.string.guidance_pour_to,
                                formatGrams(guidance.targetEndGrams, 0),
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        // Остаток считается от показаний весов. Без них говорим
                        // не «осталось», а сколько долить на этом шаге.
                        Text(
                            text = if (measuring) {
                                stringResource(
                                    R.string.guidance_remaining_at_rate,
                                    formatGrams(guidance.remainingGrams, 0),
                                    formatGrams(guidance.targetFlowRate),
                                )
                            } else {
                                stringResource(
                                    R.string.guidance_add_at_rate,
                                    formatGrams(guidance.stepDeltaGrams, 0),
                                    formatGrams(guidance.targetFlowRate),
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(text = stepName, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = stringResource(
                                R.string.guidance_hold_at,
                                formatGrams(guidance.targetEndGrams, 0),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Оценка темпа держится на весах: без них строка молчит,
                    // а не подбадривает наугад.
                    if (started && measuring) {
                        // Во время влива важна скорость прямо сейчас, а на паузе —
                        // как лить следующий по сравнению с только что показанным.
                        val hint = guidance.nextPourHint
                        val last = guidance.lastPourFlowRate
                        val text = if (hint != null && guidance.nextPourFlowRate != null && last != null) {
                            // Своя скорость первым числом: сравнивать просят с ней.
                            stringResource(
                                hint.labelRes(),
                                formatGrams(last),
                                formatGrams(guidance.nextPourFlowRate),
                            )
                        } else {
                            stringResource(
                                guidance.pace.labelRes(guidance.stepPhase == StepPhase.POURING)
                            )
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.titleSmall,
                            color = paceColor,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }

            // Шкала показывает налитое против плана — без весов налитое неизвестно,
            // и полоска вечно стояла бы на нуле, изображая безнадёжное отставание.
            if (measuring) {
                PourGauge(
                    current = currentGrams,
                    targetNow = guidance.targetNowGrams,
                    total = recipe?.finalTargetGrams ?: guidance.targetEndGrams,
                    marks = recipe?.steps?.filter { it.kind.isPour }?.map { it.targetWaterGrams }
                        .orEmpty(),
                    accent = paceColor,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            StepTimeline(
                stepCount = guidance.stepCount,
                currentIndex = guidance.stepIndex,
                accent = paceColor,
                modifier = Modifier.padding(top = 12.dp),
            )

            val next = guidance.nextStep
            if (next != null) {
                // К следующему шагу нужно успеть подготовиться, поэтому здесь
                // и объём долива, и сколько секунд осталось до него.
                val nextDelta = guidance.nextStepDeltaGrams
                Text(
                    text = if (nextDelta != null) {
                        stringResource(
                            R.string.guidance_next_pour,
                            stringResource(next.kind.labelRes()),
                            formatGrams(nextDelta, 0),
                            formatGrams(next.targetWaterGrams, 0),
                            guidance.secondsLeftInStep,
                        )
                    } else {
                        stringResource(
                            R.string.guidance_next_plain,
                            stringResource(next.kind.labelRes()),
                            guidance.secondsLeftInStep,
                        )
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            // До старта план пролива показывает плитка рецепта; во время пролива
            // её на экране нет, а свериться с рецептом иногда нужно и здесь.
            val steps = recipe?.steps.orEmpty()
            if (started && steps.isNotEmpty()) {
                var stepsShown by remember { mutableStateOf(false) }
                StepsToggleInline(
                    expanded = stepsShown,
                    onToggle = { stepsShown = !stepsShown },
                    modifier = Modifier.padding(top = 4.dp),
                )
                AnimatedVisibility(visible = stepsShown) {
                    RecipeStepsList(
                        steps = steps,
                        currentIndex = guidance.stepIndex,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartsCard(
    weights: List<Float>,
    flows: List<Float>,
    guides: List<Float>,
    focusMax: Float?,
    targetFlowRate: Float?,
    flowAvg: Float,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            LabeledChart(
                title = stringResource(R.string.chart_weight),
                unit = stringResource(R.string.unit_gram),
                values = weights,
                guides = guides,
                guideColor = AppTheme.accents.onTrack,
                focusMax = focusMax,
                height = 140.dp,
            )
            Spacer(Modifier.height(16.dp))
            // Скорость — вспомогательная величина, ей хватает половины высоты.
            LabeledChart(
                title = stringResource(R.string.chart_flow),
                unit = stringResource(R.string.unit_gram_per_second),
                values = flows,
                lineColor = AppTheme.accents.water,
                guides = listOfNotNull(targetFlowRate?.takeIf { it > 0f }),
                guideColor = AppTheme.accents.onTrack,
                height = 72.dp,
                ticks = 2,
            )
            Text(
                text = stringResource(R.string.chart_flow_average, formatGrams(flowAvg)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun BrewControls(
    phase: BrewPhase,
    connected: Boolean,
    /** Весы на связи: без них половина кнопок здесь ни к чему. */
    weightMode: Boolean,
    autoStart: Boolean,
    onToggleAutoStart: () -> Unit,
    onTare: () -> Unit,
    onDose: () -> Unit,
    onToggleTimer: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Тара и дозирование обращаются к весам. Без весов это не «пока
        // недоступно», а просто не про этого человека — ряд не показываем.
        if (weightMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onTare,
                    enabled = connected,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_tare)) }
                OutlinedButton(
                    onClick = onDose,
                    enabled = connected,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_dose)) }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onToggleTimer,
                enabled = phase != BrewPhase.FINISHED,
                modifier = Modifier
                    .weight(2f)
                    .height(52.dp),
            ) {
                Icon(
                    imageVector = if (phase == BrewPhase.RUNNING) {
                        Icons.Default.Pause
                    } else {
                        Icons.Default.PlayArrow
                    },
                    contentDescription = null,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(
                        when (phase) {
                            BrewPhase.RUNNING -> R.string.action_pause
                            BrewPhase.PAUSED -> R.string.action_resume
                            else -> R.string.action_start
                        }
                    )
                )
            }
            // Переключатель нужен только до старта, дальше на его месте «Финиш».
            // Автостарт ловит появление воды на весах — без них ловить нечего.
            if (phase == BrewPhase.IDLE && weightMode) {
                FilterChip(
                    selected = autoStart,
                    onClick = onToggleAutoStart,
                    label = { Text(stringResource(R.string.action_auto_start)) },
                    leadingIcon = if (autoStart) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
            AnimatedVisibility(visible = phase == BrewPhase.RUNNING || phase == BrewPhase.PAUSED) {
                FilledTonalButton(
                    onClick = onFinish,
                    modifier = Modifier.height(52.dp),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.action_finish))
                }
            }
        }
    }
}

@Composable
private fun RecipePickerContent(
    recipes: List<Recipe>,
    onSelect: (Recipe) -> Unit,
) {
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            text = stringResource(R.string.brew_pick_recipe),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn {
            items(recipes, key = { it.id }) { recipe ->
                ListItem(
                    headlineContent = { Text(recipe.name) },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.recipe_summary,
                                formatGrams(recipe.doseGrams),
                                formatGrams(recipe.waterGrams, 0),
                                formatRatio(recipe.doseGrams, recipe.waterGrams),
                            )
                        )
                    },
                    trailingContent = {
                        AssistChip(
                            onClick = { onSelect(recipe) },
                            label = { Text(stringResource(R.string.action_choose)) },
                            colors = AssistChipDefaults.assistChipColors(),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DoseDialog(
    initial: Float,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(if (initial > 0f) formatGrams(initial) else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dose_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.replace(',', '.') },
                singleLine = true,
                label = { Text(stringResource(R.string.unit_gram)) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.toFloatOrNull() ?: 0f) }) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
