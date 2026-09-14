package com.pourista.ui.brew

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pourista.R
import com.pourista.brew.BrewEvent
import com.pourista.brew.BrewPhase
import com.pourista.brew.CooldownState
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
import com.pourista.ui.isWideLayout
import com.pourista.ui.listSidePadding
import com.pourista.ui.labelRes
import com.pourista.ui.components.FLOW_AXIS_STEPS
import com.pourista.ui.components.LabeledChart
import com.pourista.ui.components.PourGauge
import com.pourista.ui.components.RecipeStepsList
import com.pourista.ui.components.StatTile
import com.pourista.ui.components.StepsToggleInline
import com.pourista.ui.components.StepBadge
import com.pourista.ui.components.StepRing
import com.pourista.ui.components.StepTimeline
import com.pourista.ui.theme.AppTheme
import com.pourista.ui.theme.MetricValueStyle
import com.pourista.ui.theme.WeightReadoutStyle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

/** The width of a header button: it has a lot of air around the icon, and three in a row spread
 *  across the whole screen. The press area stays full in height. */
private val ACTION_WIDTH = 40.dp

/**
 * Squeeze a button in width, keeping the content centred. The button measures as usual but
 * reports a smaller width to its neighbours — the icons come closer together.
 */
private fun Modifier.narrowAction(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val width = ACTION_WIDTH.roundToPx()
    layout(width, placeable.height) {
        placeable.place((width - placeable.width) / 2, 0)
    }
}

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
    var showFortySix by remember { mutableStateOf(false) }
    var showGrind by remember { mutableStateOf(false) }
    var showCooldown by remember { mutableStateOf(false) }
    var showDoseDialog by remember { mutableStateOf(false) }

    // An expanded recipe is wanted before the start — to check against the pour plan. With the
    // start it collapses: from there the weight and the step guidance are what matter.
    var recipeExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(brew.phase) {
        if (brew.phase == BrewPhase.RUNNING) recipeExpanded = false
    }

    // Whether to show the weight as the main figure. We follow the live connection: the scale is
    // turned off and the screen becomes about time. The single concession: mid-pour we do not
    // switch the mode back, so the layout does not jump from an accidental disconnect.
    val brewing = brew.phase == BrewPhase.RUNNING || brew.phase == BrewPhase.PAUSED
    val wide = isWideLayout()
    var weightMode by remember { mutableStateOf(scale.isConnected) }
    LaunchedEffect(scale.isConnected, brewing) {
        if (scale.isConnected) weightMode = true else if (!brewing) weightMode = false
    }

    val savedMessage = stringResource(R.string.brew_saved)
    val draftReady by viewModel.draftReady.collectAsStateWithLifecycle()

    // The recording is over — open the editor with the ready numbers at once.
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

                BrewEvent.PlanFinished ->
                    cues.planFinished(settings.soundCues, settings.hapticCues)

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
                colors = AppTheme.topBarColors(),
                modifier = AppTheme.topBarModifier(),
                // Under the name — which scale we work with. It used to be told by the colour of
                // the icon alone, and nobody ever saw the name of the scale.
                title = {
                    Column {
                        Text(stringResource(R.string.tab_brew))
                        // Without a scale the second line and the icon only get in the way: there
                        // is no point reporting a search for something the person does not have.
                        if (settings.useScale) {
                            Text(
                                text = when {
                                    scale.isConnected -> scale.deviceName
                                        ?: stringResource(R.string.scale_state_off)

                                    scale.isBusy -> stringResource(R.string.scale_state_searching)

                                    scale.status == ConnectionStatus.BLUETOOTH_OFF ->
                                        stringResource(R.string.scale_state_bluetooth_off)

                                    else -> stringResource(R.string.scale_state_off)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (settings.useScale) {
                        ConnectionAction(
                            status = scale.status,
                            battery = scale.batteryPercent,
                            onClick = onConnectClick,
                        )
                    }
                    CooldownAction(
                        states = viewModel.cooldown,
                        onClick = { showCooldown = true },
                    )
                    IconButton(
                        onClick = { showGrind = true },
                        modifier = Modifier.narrowAction(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_hand_grinder),
                            contentDescription = stringResource(R.string.grind_title),
                        )
                    }
                    IconButton(
                        onClick = viewModel::reset,
                        modifier = Modifier.narrowAction(),
                    ) {
                        Icon(Icons.Rounded.RestartAlt, stringResource(R.string.action_reset))
                    }
                },
            )
        },
        bottomBar = {
            // A backing of its own is required: without it the scrollable content shows through
            // the control panel.
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
        // During a pour the recipe tile goes away: it is too late to pick a recipe, and its figures
        // are repeated by the step guidance. The readings are pinned at the top instead, so that the
        // weight, the target and the timer stay in sight while the steps and charts scroll below.
        val readout: @Composable (Modifier) -> Unit = { cardModifier ->
            ReadoutCard(
                modifier = cardModifier,
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

        // The cards are the same, laid out differently: in portrait in a column, sideways in two
        // columns, because there is no height there and the width is going spare.
        val recipeTile: @Composable () -> Unit = {
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
                    onFortySix = { showFortySix = true },
                    onRecord = viewModel::startRecording,
                    onClear = { viewModel.selectRecipe(null) },
                    onEdit = onEditRecipe,
                )
            }
        }
        val guidanceCard: @Composable (Modifier) -> Unit = { cardModifier ->
            brew.guidance?.let { guidance ->
                GuidanceCard(
                    modifier = cardModifier,
                    guidance = guidance,
                    currentGrams = brew.weightGrams,
                    recipe = brew.recipe,
                    phase = brew.phase,
                    measuring = scale.isConnected,
                )
            }
        }
        val hasCharts = weightMode && brew.weightSeries.size > 1
        val pourGuides = brew.recipe?.steps
            ?.filter { it.kind.isPour }
            ?.map { it.targetWaterGrams }
            .orEmpty()
        // The axis is stretched to the target of the next step, otherwise its line would end up
        // beyond the top edge of the chart.
        val chartFocusMax = brew.guidance?.let { g ->
            g.nextStep?.targetWaterGrams ?: g.targetEndGrams
        }
        val charts: @Composable () -> Unit = {
            ChartsCard(
                weights = brew.weightSeries,
                flows = brew.flowSeries,
                guides = pourGuides,
                focusMax = chartFocusMax,
                targetFlowRate = brew.guidance?.targetFlowRate,
                flowAvg = brew.flowRateAvg,
            )
        }

        if (wide) {
            // The scrolling is shared: the columns move together, otherwise the rows in them drift
            // apart and checking one against the other by eye becomes impossible.
            val guidance = brew.guidance
            val split = guidance != null
            val side = if (split) 16.dp else listSidePadding()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = side,
                        end = side,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // The recipe goes on top across the full width: it is one for both columns.
                if (!brewing) recipeTile()

                if (!split) {
                    readout(Modifier)
                    if (hasCharts) charts()
                    return@Column
                }

                // The height of a pair is set by the taller card: the second stretches to it, and
                // the row comes out even.
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    readout(Modifier.weight(1f).fillMaxHeight())
                    guidanceCard(Modifier.weight(1f).fillMaxHeight())
                }

                if (hasCharts) {
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        WeightChartCard(
                            weights = brew.weightSeries,
                            guides = pourGuides,
                            focusMax = chartFocusMax,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        FlowChartCard(
                            flows = brew.flowSeries,
                            targetFlowRate = brew.guidance?.targetFlowRate,
                            flowAvg = brew.flowRateAvg,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
            return@Scaffold
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
                ) { readout(Modifier) }
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
                    item { recipeTile() }
                    item { readout(Modifier) }
                }
                if (brew.guidance != null) item { guidanceCard(Modifier) }
                if (hasCharts) item { charts() }
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

    if (showFortySix) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFortySix = false },
            sheetState = sheetState,
        ) {
            FortySixSheetContent(
                initial = settings.fortySix,
                initialLockRatio = settings.fortySixLockRatio,
                onGenerate = { params, lockRatio ->
                    viewModel.generateFortySix(params, lockRatio)
                    showFortySix = false
                },
                presets = settings.fortySixPresets,
                onSavePreset = viewModel::saveFortySixPreset,
                onDeletePreset = viewModel::deleteFortySixPreset,
            )
        }
    }

    if (showGrind) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showGrind = false },
            sheetState = sheetState,
        ) {
            GrindSheetContent(
                fromId = settings.grindFromId,
                toId = settings.grindToId,
                setting = settings.grindSetting,
                onRemember = viewModel::rememberGrindPair,
            )
        }
    }

    if (showCooldown) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCooldown = false },
            sheetState = sheetState,
        ) {
            val cooldown by viewModel.cooldown.collectAsStateWithLifecycle()
            CooldownSheetContent(
                seconds = settings.cooldownSeconds,
                autoStart = settings.cooldownAutoStart,
                state = cooldown,
                onSeconds = viewModel::setCooldownSeconds,
                onAutoStart = viewModel::setCooldownAutoStart,
                onStart = {
                    viewModel.startCooldown()
                    showCooldown = false
                },
                onSave = { showCooldown = false },
                onStop = {
                    viewModel.stopCooldown()
                    showCooldown = false
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

/**
 * The cooldown timer in the header. While it is not wound it is a clock face, and the setting
 * opens from it. While it runs a countdown takes its place: the time left needs no sheet opened
 * for it, and a press leads to the same place the timer is taken off.
 */
@Composable
private fun CooldownAction(states: StateFlow<CooldownState>, onClick: () -> Unit) {
    // The countdown is read here rather than in the screen area: it ticks five times a second, and
    // the brew screen has no business redrawing for the company.
    val state by states.collectAsStateWithLifecycle()
    val title = stringResource(R.string.cooldown_title)
    if (!state.running) {
        IconButton(onClick = onClick, modifier = Modifier.narrowAction()) {
            Icon(Icons.Rounded.Schedule, contentDescription = title)
        }
        return
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.padding(horizontal = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = title,
                modifier = Modifier.size(COOLDOWN_ICON),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = formatClock(state.remainingSeconds),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** The icon next to the countdown is smaller than usual: it is a label here, not a button. */
private val COOLDOWN_ICON = 16.dp

@Composable
private fun ConnectionAction(
    status: ConnectionStatus,
    battery: Int?,
    onClick: () -> Unit,
) {
    val icon = when (status) {
        ConnectionStatus.CONNECTED -> Icons.Rounded.BluetoothConnected
        ConnectionStatus.SCANNING, ConnectionStatus.CONNECTING, ConnectionStatus.RECONNECTING ->
            Icons.AutoMirrored.Rounded.BluetoothSearching

        ConnectionStatus.BLUETOOTH_OFF -> Icons.Rounded.BluetoothDisabled
        ConnectionStatus.IDLE -> Icons.Rounded.Bluetooth
    }
    val connected = status == ConnectionStatus.CONNECTED

    // No connection — the icon does not merely go out but blinks red, and always does: a forgotten
    // scale has to catch the eye before the pouring starts. The app allows brewing without a scale,
    // but quietly pretending everything is fine when it was simply left switched off is none of its
    // business.
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
        IconButton(onClick = onClick, modifier = Modifier.narrowAction()) {
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

/** Half a blink period: noticeable, but not flickering. */
private const val BLINK_MS = 700

/** The padding of the buttons in the recipe tile: there are three in a row, and the standard one leaves no room. */
private val TileButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

@Composable
private fun RecipeSummaryCard(
    recipe: Recipe?,
    scaled: Boolean,
    keepWater: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onKeepWater: () -> Unit,
    onPick: () -> Unit,
    onFortySix: () -> Unit,
    onRecord: () -> Unit,
    onClear: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppTheme.recipeTile),
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
                // Three buttons in one row: it is tight, so they have padding of their own, cut
                // down — otherwise "Record" wraps below the rest.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onPick,
                        contentPadding = TileButtonPadding,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.brew_pick_recipe),
                            maxLines = 1,
                        )
                    }
                    // Outlined rather than filled: the backing of the tile itself is sometimes the
                    // colour of a tonal button, and such buttons disappear on it.
                    OutlinedButton(
                        onClick = onFortySix,
                        contentPadding = TileButtonPadding,
                    ) {
                        Text(stringResource(R.string.four_six_button))
                    }
                    OutlinedButton(
                        onClick = onRecord,
                        contentPadding = TileButtonPadding,
                    ) {
                        Text(
                            text = stringResource(R.string.brew_record_recipe),
                            maxLines = 1,
                        )
                    }
                }
            }
            return@Card
        }

        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(text = recipe.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = recipe.brewer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onPick) { Text(stringResource(R.string.brew_change_recipe)) }
            }
            // The dose, the water and the ratio are what the tile is looked at for before the
            // start: the same tiles as in the readings, and the figures just as large. The
            // temperature, the grind and the filter stay as labels: they are read once, while the
            // coffee is ground and the cone is folded.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatTile(
                    label = stringResource(R.string.recipe_dose),
                    value = formatGrams(recipe.doseGrams),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.recipe_water),
                    value = formatGrams(recipe.waterGrams, 0),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
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
                val filter = recipe.filterName?.takeIf { it.isNotBlank() }
                RecipeFact(
                    label = stringResource(R.string.recipe_grind),
                    value = recipe.grindSetting?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.value_not_set),
                    // The grind takes the full width until a filter is set: there is no point
                    // writing "not set" next to a field a recipe usually does not have.
                    modifier = Modifier.weight(if (filter == null) 2f else 1f),
                )
                if (filter != null) {
                    RecipeFact(
                        label = stringResource(R.string.recipe_filter),
                        value = filter,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // The notes on a recipe are what has to be known before the start: how many times to
            // swirl the cone, what makes this method different. Short ones are visible whole, long
            // ones collapse: a half-screen description pushes everything else down.
            val notes = recipe.notes?.takeIf { it.isNotBlank() }
            if (notes != null) {
                var notesExpanded by remember(notes) { mutableStateOf(false) }
                var notesClipped by remember(notes) { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (notesExpanded) Int.MAX_VALUE else NOTES_LINES,
                            overflow = TextOverflow.Ellipsis,
                            // Expanded there is no clipping, and the flag would have been reset
                            // together with the "Collapse" button.
                            onTextLayout = { layout ->
                                if (!notesExpanded) notesClipped = layout.hasVisualOverflow
                            },
                        )
                        if (notesClipped) {
                            Text(
                                text = stringResource(
                                    if (notesExpanded) {
                                        R.string.recipe_notes_collapse
                                    } else {
                                        R.string.recipe_notes_expand
                                    }
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(top = 2.dp, end = 8.dp)
                                    .clickable { notesExpanded = !notesExpanded },
                            )
                        }
                    }
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
            // The button and the checkbox have no padding of their own: the button already has a
            // 48 dp finger area, and any addition pushes them too far apart.
            if (recipe.steps.isNotEmpty()) {
                StepsToggleInline(expanded = expanded, onToggle = onToggleExpanded)
                AnimatedVisibility(visible = expanded) {
                    RecipeStepsList(
                        steps = recipe.steps,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            // Sometimes more coffee is ground on purpose, for a denser cup: the water volume should
            // then stay as the recipe wrote it rather than follow the dose.
            FilterChip(
                selected = keepWater,
                onClick = onKeepWater,
                label = { Text(stringResource(R.string.brew_fixed_water)) },
                leadingIcon = if (keepWater) {
                    { Icon(Icons.Rounded.Check, contentDescription = null) }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // There is nothing to open a generator build with: it is not in the recipe list, and
                // it is changed where it was assembled — by the button next door.
                val editable = recipe.id > 0
                if (editable) {
                    FilledTonalButton(
                        onClick = { onEdit(recipe.id) },
                        contentPadding = TileButtonPadding,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.action_open_recipe), maxLines = 1)
                    }
                }
                FilledTonalButton(
                    onClick = onFortySix,
                    contentPadding = TileButtonPadding,
                    modifier = if (editable) Modifier else Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.four_six_button))
                }
                FilledTonalButton(
                    onClick = onClear,
                    contentPadding = TileButtonPadding,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.brew_without_recipe), maxLines = 1)
                }
            }
        }
    }
}

/** The recording mode card: while a recording runs there is no point picking a recipe. */
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
 * The readings. With a scale the main figure is the weight: the pour is led by it. Without a scale
 * the weight is always zero, and the time takes its place: brewing to a recipe is above all a
 * timer, and the grams a person measures with whatever they have.
 */
@Composable
private fun ReadoutCard(
    modifier: Modifier = Modifier,
    weightGrams: Float,
    doseGrams: Float,
    flowRate: Float,
    elapsedMs: Long,
    targetGrams: Float?,
    remainingGrams: Float?,
    unitLabel: String,
    /** Show the weight: the scale is connected. Otherwise the main figure is the time. */
    weightMode: Boolean,
    onDoseClick: () -> Unit,
) {
    if (!weightMode) {
        TimerReadoutCard(
            modifier = modifier,
            elapsedMs = elapsedMs,
            doseGrams = doseGrams,
            targetGrams = targetGrams,
            unitLabel = unitLabel,
            onDoseClick = onDoseClick,
        )
        return
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.readout_weight),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        // The figure is squeezed rather than wrapped: with a system font at one and
                        // a half times, "1000.0" would otherwise break the card.
                        BasicText(
                            text = formatGrams(weightGrams),
                            style = WeightReadoutStyle.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = WeightReadoutMinSize,
                                maxFontSize = WeightReadoutStyle.fontSize,
                            ),
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = unitLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    // The step target stands next to the current weight: the eye must not jump
                    // across the screen to see how much is left to pour.
                    if (targetGrams != null) {
                        Text(
                            text = stringResource(R.string.readout_target),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${formatGrams(targetGrams, 0)}$unitLabel",
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
                StatTile(
                    label = stringResource(R.string.readout_dose),
                    value = formatGrams(doseGrams),
                    unit = stringResource(R.string.unit_gram),
                    onClick = onDoseClick,
                    modifier = Modifier.weight(1f),
                )
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

/** The readings without a scale: the time large, the step target beside it, the entered dose below. */
@Composable
private fun TimerReadoutCard(
    modifier: Modifier = Modifier,
    elapsedMs: Long,
    doseGrams: Float,
    targetGrams: Float?,
    unitLabel: String,
    onDoseClick: () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.readout_time),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BasicText(
                        text = formatTimerWithTenths(elapsedMs),
                        style = WeightReadoutStyle.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = WeightReadoutMinSize,
                            maxFontSize = WeightReadoutStyle.fontSize,
                        ),
                    )
                }
                if (targetGrams != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.readout_target),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${formatGrams(targetGrams, 0)}$unitLabel",
                            style = MetricValueStyle,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            // The dose is entered by hand: it is measured with kitchen scales or a scoop, and the
            // app needs it to recalculate the recipe for it.
            StatTile(
                label = stringResource(R.string.readout_dose),
                value = formatGrams(doseGrams),
                unit = stringResource(R.string.unit_gram),
                onClick = onDoseClick,
            )
        }
    }
}

@Composable
private fun GuidanceCard(
    modifier: Modifier = Modifier,
    guidance: Guidance,
    currentGrams: Float,
    recipe: Recipe?,
    phase: BrewPhase,
    /** The scale is connected: only then does it make sense to talk about what is left and the pace. */
    measuring: Boolean,
) {
    val accents = AppTheme.accents
    // Before the start the card is a preview of the first step. There is nothing to judge the pace
    // by there: the time is not running, and any weight on the scale would look like being ahead.
    val started = phase == BrewPhase.RUNNING || phase == BrewPhase.PAUSED
    val running = phase == BrewPhase.RUNNING
    // The pace colour changes by a transition: a pour is now ahead of the plan, now behind, and a
    // card blinking at every wobble of the scale tugs at the eye harder than the pace itself is
    // worth.
    val paceColor by animateColorAsState(
        targetValue = when {
            !started -> MaterialTheme.colorScheme.primary
            guidance.pace == Pace.TOO_FAST -> accents.tooFast
            guidance.pace == Pace.TOO_SLOW -> accents.tooSlow
            else -> accents.onTrack
        },
        label = "paceColor",
    )
    val container by animateColorAsState(
        targetValue = when {
            !started -> MaterialTheme.colorScheme.surfaceContainer
            guidance.pace == Pace.TOO_FAST -> accents.tooFastContainer
            guidance.pace == Pace.TOO_SLOW -> accents.tooSlowContainer
            else -> accents.onTrackContainer
        },
        label = "paceContainer",
    )
    // Once the pour is over, the step must not be called "Pouring" — we are waiting.
    val stepName = if (guidance.stepPhase == StepPhase.WAITING && guidance.step.kind.isPour) {
        stringResource(R.string.step_wait)
    } else {
        stringResource(guidance.step.kind.labelRes())
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (running) container else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The circle is the same as in the list of steps: it is one and the same step, only
                // larger — and it has to be recognised without reading.
                StepBadge(
                    kind = guidance.step.kind,
                    size = 36.dp,
                    tint = paceColor,
                    ring = paceColor,
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = guidance.step.title?.takeIf { it.isNotBlank() } ?: stepName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    // The recipe tile is hidden during a pour, so the recipe name lives here — the
                    // line is there anyway and adds no height.
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
                    fillFraction = if (started) guidance.pourFill(currentGrams, measuring) else 0f,
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
                        // What is left is counted from the scale readings. Without them we say not
                        // "left" but how much to add on this step.
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
                    // Judging the pace rests on the scale: without it the line keeps quiet rather
                    // than cheering at random.
                    if (started && measuring) {
                        // During a pour what matters is the rate right now, and in a pause — how to
                        // pour the next one compared with the one just shown.
                        val hint = guidance.nextPourHint
                        val last = guidance.lastPourFlowRate
                        val text = if (hint != null && guidance.nextPourFlowRate != null && last != null) {
                            // Our own rate as the first figure: that is what the comparison is asked against.
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

            // The scale shows what is poured against the plan — without a scale what is poured is
            // unknown, and the bar would stand at zero forever, portraying a hopeless lag.
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
                // There has to be time to get ready for the next step, so both the volume to add and
                // the seconds left until it are here.
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

            // Before the start the pour plan is shown by the recipe tile; during a pour it is not on
            // the screen, and checking against the recipe is sometimes needed here too.
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

/**
 * How much of the pour on the current step is already done, 0..1 — that is what fills the ring.
 * With a scale we count by fact, without one by the plan: otherwise the water would sit at the bottom.
 */
private fun Guidance.pourFill(currentGrams: Float, measuring: Boolean): Float {
    if (stepDeltaGrams <= 0f) return 0f
    val startedAt = targetEndGrams - stepDeltaGrams
    val poured = if (measuring) currentGrams - startedAt else targetNowGrams - startedAt
    return (poured / stepDeltaGrams).coerceIn(0f, 1f)
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
    // On a short screen the two charts do not fit at full height, and the flow rate runs off the
    // bottom edge. We measure how much height there is at all and squeeze to it.
    BoxWithConstraints {
        val (weightHeight, flowHeight) = chartHeights(maxHeight)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                WeightChart(
                    weights = weights,
                    guides = guides,
                    focusMax = focusMax,
                    height = weightHeight,
                )
                Spacer(Modifier.height(16.dp))
                FlowChart(
                    flows = flows,
                    targetFlowRate = targetFlowRate,
                    flowAvg = flowAvg,
                    height = flowHeight,
                )
            }
        }
    }
}

/**
 * The heights of the weight and flow charts fitted to the available screen height.
 *
 * The weight is squeezed first — it is the larger one and bears it. We do not go below equality:
 * the flow rate is secondary, and there is no point making it taller than the weight. When even
 * equal does not fit, both shrink, but no smaller than [MIN_CHART_HEIGHT] — beyond that it is a
 * strip rather than a chart.
 */
internal fun chartHeights(available: Dp): Pair<Dp, Dp> {
    val budget = available * CHART_SHARE - CHART_CHROME
    return when {
        budget >= WEIGHT_CHART_HEIGHT + FLOW_CHART_HEIGHT ->
            WEIGHT_CHART_HEIGHT to FLOW_CHART_HEIGHT

        budget >= FLOW_CHART_HEIGHT * 2 -> (budget - FLOW_CHART_HEIGHT) to FLOW_CHART_HEIGHT

        else -> {
            val half = (budget / 2).coerceAtLeast(MIN_CHART_HEIGHT)
            half to half
        }
    }
}

private val WEIGHT_CHART_HEIGHT = 140.dp
private val FLOW_CHART_HEIGHT = 72.dp
private val MIN_CHART_HEIGHT = 64.dp

/** The charts take no more than half the screen: above them are the weight and the step guidance. */
private const val CHART_SHARE = 0.5f

/** Labels, the average rate and the card padding — the room taken beside the charts themselves. */
private val CHART_CHROME = 100.dp

/** The same weight chart, but as a card of its own: sideways the charts stand in columns. */
@Composable
private fun WeightChartCard(
    weights: List<Float>,
    guides: List<Float>,
    focusMax: Float?,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            WeightChart(weights = weights, guides = guides, focusMax = focusMax)
        }
    }
}

@Composable
private fun FlowChartCard(
    flows: List<Float>,
    targetFlowRate: Float?,
    flowAvg: Float,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            FlowChart(flows = flows, targetFlowRate = targetFlowRate, flowAvg = flowAvg)
        }
    }
}

@Composable
private fun WeightChart(
    weights: List<Float>,
    guides: List<Float>,
    focusMax: Float?,
    height: Dp = WEIGHT_CHART_HEIGHT,
) {
    LabeledChart(
        title = stringResource(R.string.chart_weight),
        unit = stringResource(R.string.unit_gram),
        values = weights,
        guides = guides,
        guideColor = AppTheme.accents.onTrack,
        focusMax = focusMax,
        height = height,
    )
}

@Composable
private fun FlowChart(
    flows: List<Float>,
    targetFlowRate: Float?,
    flowAvg: Float,
    height: Dp = FLOW_CHART_HEIGHT,
) {
    // The flow rate is a secondary quantity, half the height is enough for it.
    LabeledChart(
        title = stringResource(R.string.chart_flow),
        unit = stringResource(R.string.unit_gram_per_second),
        values = flows,
        lineColor = AppTheme.accents.water,
        guides = listOfNotNull(targetFlowRate?.takeIf { it > 0f }),
        guideColor = AppTheme.accents.onTrack,
        height = height,
        axisSteps = FLOW_AXIS_STEPS,
    )
    Text(
        text = stringResource(R.string.chart_flow_average, formatGrams(flowAvg)),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun BrewControls(
    phase: BrewPhase,
    connected: Boolean,
    /** The scale is connected: without it half the buttons here are of no use. */
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
        // The tare and the dosing are needed before the start: to weigh the coffee and zero the
        // scale. After that they only take up room — with the beginning of the pour the row goes
        // away. Without a scale it is not there at all: this is not "unavailable for now" but not
        // about this person.
        if (weightMode && phase == BrewPhase.IDLE) {
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
                        Icons.Rounded.Pause
                    } else {
                        Icons.Rounded.PlayArrow
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
            // The switch is only needed before the start, after that "Finish" takes its place.
            // Auto-start catches water appearing on the scale — without one there is nothing to catch.
            if (phase == BrewPhase.IDLE && weightMode) {
                FilterChip(
                    selected = autoStart,
                    onClick = onToggleAutoStart,
                    label = { Text(stringResource(R.string.action_auto_start)) },
                    leadingIcon = if (autoStart) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
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
                    Icon(Icons.Rounded.Stop, contentDescription = null)
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
                Icon(Icons.Rounded.Check, null)
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** How many lines of the description show when collapsed. */
private const val NOTES_LINES = 4

/** The main figure is not squeezed below this: any smaller and it cannot be made out from the kettle. */
private val WeightReadoutMinSize = 40.sp
