package com.pourista.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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

    if (showFormat) {
        RecipeFormatDialog(onDismiss = { showFormat = false })
    }
    if (showNotes) {
        ReleaseNotesDialog(onDismiss = { showNotes = false })
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_settings)) },
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
                SettingsSection(stringResource(R.string.settings_appearance)) {
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
                    Text(
                        text = stringResource(R.string.settings_palette),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                    // Обои системы умеет только Android 12: на старых
                    // выбирать не из чего, и пункт просто не показываем.
                    val palettes = AppPalette.entries.filter {
                        it != AppPalette.DYNAMIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        palettes.forEachIndexed { index, palette ->
                            SegmentedButton(
                                selected = settings.palette == palette,
                                onClick = { viewModel.setPalette(palette) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = palettes.size,
                                ),
                            ) {
                                Text(stringResource(palette.labelRes()))
                            }
                        }
                    }
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
                    ChoiceRow(
                        title = stringResource(R.string.settings_pace_tolerance),
                        subtitle = stringResource(R.string.settings_pace_tolerance_hint),
                        current = percentLabel(settings.paceTolerance),
                        options = PACE_TOLERANCE_OPTIONS.map { percentLabel(it) to it },
                        onSelect = viewModel::setPaceTolerance,
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
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_about)) {
                    // История изменений стоит перед проверкой обновлений:
                    // сначала «что нового», потом «где взять».
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showNotes = true }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_release_notes),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }

                    // Проверку обновлений отдаём браузеру: у приложения нет и не
                    // должно быть выхода в сеть. Страница latest на GitHub сама
                    // ведёт на свежий релиз, а версия рядом — с чем сравнивать.
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
                        TextButton(
                            onClick = { runCatching { uriHandler.openUri("https://$releases") } },
                        ) { Text(stringResource(R.string.settings_check_updates)) }
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
                    Text(
                        text = stringResource(R.string.settings_thanks),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.settings_thanks_link),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    // Двух авторов указать обязаны: их звуки под лицензиями
                    // с атрибуцией. Остальные два — CC0, но раз уж список
                    // есть, пусть будет полным.
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
 * Подпись и ссылка под ней. Адрес показан целиком, а не спрятан за словом:
 * его можно переписать глазами, если открывать нечем.
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
 * Описание формата рецептов. Его удобно скопировать целиком и отдать нейросети
 * вместе с просьбой составить рецепт — поэтому текст лежит одним куском.
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

/** Строка с выбором из нескольких значений. */
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
            TextButton(onClick = { expanded = true }) { Text(current) }
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

/** «±10 %» — доля показывается процентами, так её проще примерить на себя. */
@Composable
private fun percentLabel(share: Float): String =
    stringResource(R.string.settings_pace_tolerance_value, kotlin.math.round(share * 100).toInt())

private fun AppPalette.labelRes(): Int = when (this) {
    AppPalette.COPPER -> R.string.palette_copper
    AppPalette.FOUR_SIX -> R.string.palette_four_six
    AppPalette.DYNAMIC -> R.string.palette_dynamic
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}
