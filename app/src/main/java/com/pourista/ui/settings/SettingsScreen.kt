package com.pourista.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pourista.BuildConfig
import com.pourista.R
import com.pourista.brew.FlowSmoothing
import com.pourista.appContainer
import com.pourista.core.AppLocale
import com.pourista.ui.components.ReleaseNotesDialog
import com.pourista.ui.listSidePadding
import com.pourista.core.formatGrams
import com.pourista.ui.labelRes
import com.pourista.ui.theme.AppPalette
import com.pourista.ui.theme.AppTheme
import com.pourista.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenScaleLog: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showFormat by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }

    // The file is picked by the system: the app needs no access to the whole storage.
    val saveBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME)
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importBackup) }

    val backupContext = LocalContext.current
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    LaunchedEffect(backupMessage) {
        val current = backupMessage ?: return@LaunchedEffect
        val text = backupContext.getString(current.textRes, current.recipes, current.brews)
        Toast.makeText(backupContext, text, Toast.LENGTH_LONG).show()
        viewModel.clearBackupMessage()
    }

    if (showFormat) {
        RecipeFormatDialog(onDismiss = { showFormat = false })
    }
    if (showNotes) {
        ReleaseNotesDialog(onDismiss = { showNotes = false })
    }

    // The header moves away on scroll: the screen is long, and the header holds a single word.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_settings)) },
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
                SettingsSection(stringResource(R.string.settings_appearance)) {
                    // The language first: whoever opened the app in a foreign language looks for this
                    // very row, and looking for it should not take long.
                    LanguageRow()
                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemeMode.entries.size,
                                ),
                            ) {
                                Text(stringResource(mode.labelRes()))
                            }
                        }
                    }
                    // The system wallpaper is only available from Android 12: on older ones there is
                    // nothing to choose from, and we simply do not show the item.
                    val palettes = AppPalette.entries.filter {
                        it != AppPalette.DYNAMIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    }
                    // A list rather than a row of buttons: there are four palettes already, and their
                    // names do not fit in a row.
                    ChoiceRow(
                        title = stringResource(R.string.settings_palette),
                        subtitle = null,
                        current = stringResource(settings.palette.labelRes()),
                        options = palettes.map { stringResource(it.labelRes()) to it },
                        onSelect = viewModel::setPalette,
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_brewing)) {
                    SwitchRow(
                        title = stringResource(R.string.settings_keep_screen_on),
                        checked = settings.keepScreenOn,
                        onCheckedChange = viewModel::setKeepScreenOn,
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_sound_cues),
                        checked = settings.soundCues,
                        onCheckedChange = viewModel::setSoundCues,
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_haptic_cues),
                        checked = settings.hapticCues,
                        onCheckedChange = viewModel::setHapticCues,
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_countdown),
                        subtitle = stringResource(R.string.settings_countdown_hint),
                        checked = settings.countdownCue,
                        onCheckedChange = viewModel::setCountdownCue,
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_auto_finish),
                        subtitle = stringResource(R.string.settings_auto_finish_hint),
                        checked = settings.autoFinish,
                        onCheckedChange = viewModel::setAutoFinish,
                    )
                    ChoiceRow(
                        title = stringResource(R.string.settings_pace_tolerance),
                        subtitle = stringResource(R.string.settings_pace_tolerance_hint),
                        current = percentLabel(settings.paceTolerance),
                        options = PACE_TOLERANCE_OPTIONS.map { percentLabel(it) to it },
                        onSelect = viewModel::setPaceTolerance,
                    )
                    ChoiceRow(
                        title = stringResource(R.string.settings_flow_smoothing),
                        subtitle = stringResource(R.string.settings_flow_smoothing_hint),
                        current = stringResource(settings.flowSmoothing.labelRes()),
                        options = FlowSmoothing.entries.map { stringResource(it.labelRes()) to it },
                        onSelect = viewModel::setFlowSmoothing,
                    )
                    ChoiceRow(
                        title = stringResource(R.string.settings_near_target),
                        subtitle = stringResource(R.string.settings_near_target_hint),
                        current = formatGrams(settings.nearTargetGrams, 0),
                        options = NEAR_TARGET_OPTIONS.map { formatGrams(it, 0) to it },
                        onSelect = viewModel::setNearTargetGrams,
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_recipes)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFormat = true }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_recipe_format),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.settings_recipe_format_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_scale)) {
                    // The checkbox is inverted: whoever has no scale looks in the list for "do not
                    // use" rather than for "use" with a switch.
                    SwitchRow(
                        title = stringResource(R.string.settings_no_scale),
                        subtitle = stringResource(R.string.settings_no_scale_hint),
                        checked = !settings.useScale,
                        onCheckedChange = { viewModel.setUseScale(!it) },
                    )
                    // The rest about the scale is of no use without one — we hide it.
                    if (settings.useScale) {
                        SwitchRow(
                            title = stringResource(R.string.settings_auto_connect),
                            checked = settings.autoConnectOnLaunch,
                            onCheckedChange = viewModel::setAutoConnect,
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_stop_on_disconnect),
                            checked = settings.stopTimerOnDisconnect,
                            onCheckedChange = viewModel::setStopTimerOnDisconnect,
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_fix_unit),
                            subtitle = stringResource(R.string.settings_fix_unit_hint),
                            checked = settings.keepScaleInGrams,
                            onCheckedChange = viewModel::setKeepScaleInGrams,
                        )
                    }
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_backup)) {
                    Text(
                        text = stringResource(R.string.settings_backup_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    // The buttons one under another: in long languages two do not fit in a row, and a
                    // wrap inside a button reads badly.
                    OutlinedButton(
                        onClick = { saveBackup.launch(BACKUP_FILE) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.settings_backup_export)) }
                    OutlinedButton(
                        onClick = { openBackup.launch(BACKUP_OPEN_MIME) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) { Text(stringResource(R.string.settings_backup_import)) }
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_diagnostics)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenScaleLog)
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_scale_log),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.settings_scale_log_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // The log is needed not only when the scale is broken but also when it is not in
                    // the list at all: the protocol is taken apart by it.
                    Text(
                        text = stringResource(R.string.settings_scale_log_unsupported),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_about)) {
                    // The change history stands before the update check: first "what is new", then
                    // "where to get it".
                    // The update check is left to the browser: the app has no way out to the network
                    // and should not have one. The latest page on GitHub leads to the freshest release
                    // by itself, and the version next to it says what to compare with.
                    // In the store build there is no button: the store does the updating.
                    val uriHandler = LocalUriHandler.current
                    val releases = stringResource(R.string.settings_updates_url)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(
                                R.string.settings_version,
                                BuildConfig.VERSION_NAME,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (BuildConfig.UPDATE_LINK) {
                            TextButton(
                                onClick = {
                                    runCatching { uriHandler.openUri("https://$releases") }
                                },
                            ) { Text(stringResource(R.string.settings_check_updates)) }
                        }
                    }
                    // The change history sits under the version and on the same button as the update
                    // check: both are about what is new in the app. Without the side padding of a
                    // button: the row has to start where the version above it does, not indented.
                    TextButton(
                        onClick = { showNotes = true },
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        Text(stringResource(R.string.settings_release_notes))
                    }
                    Text(
                        text = stringResource(R.string.settings_about_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    LinkRow(
                        label = stringResource(R.string.settings_contact),
                        link = stringResource(R.string.settings_contact_telegram),
                    )
                    LinkRow(
                        label = stringResource(R.string.settings_sources),
                        link = stringResource(R.string.settings_sources_link),
                    )

                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text(
                        text = stringResource(R.string.settings_thanks_coffeesaurus),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.settings_thanks_coffeesaurus_link),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    // Two authors we are obliged to name: their sounds are under licences with
                    // attribution. The other two are CC0, but since there is a list, let it be
                    // complete.
                    Text(
                        text = stringResource(R.string.settings_sound_credits),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

/**
 * Choosing the interface language.
 *
 * The value is read from the system (or from our own storage on older Android) rather than from the
 * common settings: from Android 13 on the language belongs to the system, and its choice has to be
 * visible here even if it was changed in the phone settings.
 */
@Composable
private fun LanguageRow() {
    val context = LocalContext.current
    val system = stringResource(R.string.language_system)
    val options = remember(system) {
        buildList<Pair<String, String?>> {
            add(system to null)
            AppLocale.languages.forEach { add(it.label to it.tag) }
        }
    }
    ChoiceRow(
        title = stringResource(R.string.settings_language),
        subtitle = null,
        current = AppLocale.selected(context)?.label ?: system,
        options = options,
        onSelect = { tag ->
            AppLocale.apply(context, tag)
            // The texts of the built-in recipes lie in the database and will not translate themselves.
            context.appContainer.syncPresets()
            // From Android 13 on the screen is recreated by the system, before that there is nobody to.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                context.activity()?.recreate()
            }
        },
    )
}

/** The screen we are on: from the Compose context it is one step away. */
private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * A label and a link under it. The address is shown whole rather than hidden behind a word: it can
 * be copied by eye if there is nothing to open it with.
 */
@Composable
private fun LinkRow(label: String, link: String) {
    val uriHandler = LocalUriHandler.current
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
    Text(
        text = link,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable { runCatching { uriHandler.openUri("https://$link") } }
            .padding(top = 2.dp),
    )
}

/**
 * The description of the recipe format. It is convenient to copy it whole and hand it to a neural
 * network together with a request to compose a recipe — that is why the text lies in one piece.
 */
@Composable
private fun RecipeFormatDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val text = remember {
        runCatching {
            context.resources.openRawResource(R.raw.recipe_format)
                .use { it.readBytes().decodeToString() }
        }.getOrDefault("")
    }
    val copied = stringResource(R.string.recipes_format_copied)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recipes_format_title)) },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(text))
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_copy)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
    )
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
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

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A row with a choice of several values. */
@Composable
private fun <T> ChoiceRow(
    title: String,
    subtitle: String?,
    current: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            // The value in a frame and with an arrow: without them the row reads as a label, and
            // nobody guesses it can be pressed.
            OutlinedButton(
                onClick = { expanded = true },
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp),
            ) {
                Text(current)
                Spacer(Modifier.size(4.dp))
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (label, value) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            onSelect(value)
                        },
                    )
                }
            }
        }
    }
}

private val NEAR_TARGET_OPTIONS = listOf(3f, 5f, 10f, 15f)

private val PACE_TOLERANCE_OPTIONS = listOf(0.05f, 0.1f, 0.15f, 0.2f, 0.3f)

/** "±10%" — the share is shown as a percentage, that way it is easier to try on. */
@Composable
private fun percentLabel(share: Float): String =
    stringResource(R.string.settings_pace_tolerance_value, kotlin.math.round(share * 100).toInt())

private fun FlowSmoothing.labelRes(): Int = when (this) {
    FlowSmoothing.NONE -> R.string.flow_smoothing_none
    FlowSmoothing.LIGHT -> R.string.flow_smoothing_light
    FlowSmoothing.NORMAL -> R.string.flow_smoothing_normal
    FlowSmoothing.STRONG -> R.string.flow_smoothing_strong
}

private fun AppPalette.labelRes(): Int = when (this) {
    AppPalette.COPPER -> R.string.palette_copper
    AppPalette.CALM -> R.string.palette_calm
    AppPalette.FOUR_SIX -> R.string.palette_four_six
    AppPalette.DYNAMIC -> R.string.palette_dynamic
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

/** A backup is ordinary JSON, and it should open with the file name we suggest. */
private const val BACKUP_MIME = "application/json"
private const val BACKUP_FILE = "pourista-backup.json"

/** Some file managers hand a backup over as text/plain, or not at all. */
private val BACKUP_OPEN_MIME = arrayOf("application/json", "text/plain", "*/*")
