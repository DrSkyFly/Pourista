package com.pourista.ui.brew

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pourista.R
import com.pourista.core.formatClock
import com.pourista.core.formatGrams
import com.pourista.data.presets.FortySixGenerator
import com.pourista.data.prefs.FortySixPreset
import com.pourista.data.presets.FortySixParams
import com.pourista.data.presets.FortySixStrength
import com.pourista.data.presets.FortySixTaste
import com.pourista.ui.components.RecipeStepsList
import com.pourista.ui.labelRes
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The 4:6 generator: two dials set the taste and the strength, the plan of pours is recalculated
 * at every movement — you see what will come out before the brewing starts.
 */
@Composable
fun FortySixSheetContent(
    initial: FortySixParams,
    initialLockRatio: Boolean,
    onGenerate: (FortySixParams, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Saved settings: they are picked from the list under the title. */
    presets: List<FortySixPreset> = emptyList(),
    onSavePreset: (String, FortySixParams, Boolean) -> Unit = { _, _, _ -> },
    onDeletePreset: (String) -> Unit = {},
) {
    var params by remember { mutableStateOf(initial) }
    // The pinned ratio is counted from the value at the moment of the lock rather than from the
    // current one: otherwise rounding the dose would drag it off at every step of the water.
    var lockRatio by remember { mutableStateOf(initialLockRatio) }
    var lockedAt by remember { mutableFloatStateOf(initial.ratio) }
    /**
     * The preset taken as the base — whole, rather than by name alone: by it one can see whether
     * the dials were turned after loading, and whether to offer to save.
     */
    var currentPreset by remember { mutableStateOf<FortySixPreset?>(null) }
    var presetMenu by remember { mutableStateOf(false) }
    var savingPreset by remember { mutableStateOf(false) }

    val steps = remember(params) { FortySixGenerator.steps(params) }
    val water = remember(params) { FortySixGenerator.waterGrams(params) }
    val gram = stringResource(R.string.unit_gram)

    // The "Done" button lives outside the scroll while the dials are inside: it is wanted after
    // every edit, and nobody should have to look for it by scrolling the sheet.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // The title on the left, the presets on the right: the sheet is about the generator after
            // all, and the list of one's own settings is what one reaches for separately.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.four_six_title),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Box {
                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { presetMenu = true }
                            .padding(
                                start = TITLE_TOUCH_PADDING,
                                end = 2.dp,
                                top = 6.dp,
                                bottom = 6.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = currentPreset?.name ?: stringResource(R.string.four_six_presets),
                            style = MaterialTheme.typography.titleMedium,
                            // The theme colour: in the "4:6" palette it is that very amber.
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                        // "No preset" always stands there: one starts from it and comes back to it.
                        // The dials stay as they are — a person is unlinking the settings from the
                        // preset, not resetting them.
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.four_six_preset_off)) },
                            onClick = {
                                presetMenu = false
                                currentPreset = null
                            },
                        )
                        if (presets.isNotEmpty()) HorizontalDivider()
                        presets.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.name) },
                                onClick = {
                                    presetMenu = false
                                    params = preset.params
                                    lockRatio = preset.lockRatio
                                    lockedAt = preset.params.ratio
                                    currentPreset = preset
                                },
                                // The cross right in the row: deleting a preset is done where it is
                                // picked, and that is not worth a screen of its own.
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            onDeletePreset(preset.name)
                                            if (currentPreset?.name == preset.name) {
                                                currentPreset = null
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.action_delete),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.four_six_preset_save)) },
                            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                            onClick = {
                                presetMenu = false
                                savingPreset = true
                            },
                        )
                    }
                }

                // The room for the floppy disk is always taken, while it only shows when there is
                // something to save. Appearing out of nothing, it would shift both the preset name
                // and everything below — the sheet twitched at every turn of a dial.
                val edited = currentPreset?.let {
                    it.params != params || it.lockRatio != lockRatio
                } == true
                IconButton(
                    onClick = {
                        val name = currentPreset?.name ?: return@IconButton
                        onSavePreset(name, params, lockRatio)
                        currentPreset = FortySixPreset(name, params, lockRatio)
                    },
                    enabled = edited,
                    modifier = Modifier
                        .size(SAVE_BUTTON_SIZE)
                        .alpha(if (edited) 1f else 0f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = stringResource(R.string.action_save),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // The dose and the water are dialled, the ratio is counted from them: that is how
                // one thinks when pouring into one's own cup — "how much coffee" and "how much water".
                Stepper(
                    label = stringResource(R.string.four_six_dose),
                    value = "${formatGrams(params.doseGrams)} $gram",
                    onStep = { direction ->
                        val dose = (params.doseGrams + direction * DOSE_STEP_GRAMS)
                            .coerceIn(MIN_DOSE_GRAMS, MAX_DOSE_GRAMS)
                        params = if (lockRatio) {
                            // The ratio is pinned: the water follows the dose.
                            params.copy(doseGrams = dose, ratio = lockedAt)
                        } else {
                            params.copy(doseGrams = dose, ratio = water / dose)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                Stepper(
                    label = stringResource(R.string.recipe_water),
                    value = "${formatGrams(water, 0)} $gram",
                    onStep = { direction ->
                        // The step always brings the water to a round number: the ratio is set as a
                        // fraction, and without this there was no way away from 251 g.
                        val steps = if (direction > 0) {
                            floor(water / WATER_STEP_GRAMS) + 1
                        } else {
                            ceil(water / WATER_STEP_GRAMS) - 1
                        }
                        val next = (steps * WATER_STEP_GRAMS)
                            .coerceIn(MIN_WATER_GRAMS, MAX_WATER_GRAMS)
                        params = if (lockRatio) {
                            // The dose follows the water: rounded to a tenth of a gram — coffee scales
                            // do not go finer — and the ratio is corrected for the rounded dose, so
                            // that the water stays exactly as dialled.
                            val dose = (next / lockedAt)
                                .coerceIn(MIN_DOSE_GRAMS, MAX_DOSE_GRAMS)
                                .let { (it * 10f).roundToInt() / 10f }
                            params.copy(doseGrams = dose, ratio = next / dose)
                        } else {
                            params.copy(ratio = next / params.doseGrams)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                Readout(
                    label = stringResource(R.string.four_six_ratio),
                    value = stringResource(R.string.four_six_ratio_value, formatGrams(params.ratio)),
                    locked = lockRatio,
                    onToggle = {
                        lockRatio = !lockRatio
                        if (lockRatio) lockedAt = params.ratio
                    },
                    modifier = Modifier.weight(0.6f),
                )
            }

            if (lockRatio) {
                Text(
                    text = stringResource(R.string.four_six_locked_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DotSlider(
                title = stringResource(R.string.four_six_taste),
                value = stringResource(params.taste.labelRes()),
                hint = stringResource(R.string.four_six_taste_hint),
                index = params.taste.ordinal,
                count = FortySixTaste.entries.size,
                onIndex = { params = params.copy(taste = FortySixTaste.entries[it]) },
            )

            DotSlider(
                title = stringResource(R.string.four_six_strength),
                value = stringResource(params.strength.labelRes()),
                hint = stringResource(R.string.four_six_strength_hint),
                index = params.strength.ordinal,
                count = FortySixStrength.entries.size,
                onIndex = { params = params.copy(strength = FortySixStrength.entries[it]) },
            )

            Text(
                text = stringResource(
                    R.string.four_six_summary,
                    formatGrams(water, 0),
                    steps.count { it.kind.isPour },
                    formatClock(steps.lastOrNull()?.startSec ?: 0),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // A box of constant height: the number of pours changes with the strength, and without
            // this the sheet grew and shrank at every turn of a dial, dragging everything else along
            // with it. What does not fit scrolls inside the box.
            Column(
                modifier = Modifier
                    .height(STEPS_WINDOW_HEIGHT)
                    .verticalScroll(rememberScrollState()),
            ) {
                RecipeStepsList(steps = steps)
            }
        }

        Button(
            onClick = { onGenerate(params, lockRatio) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            Text(stringResource(R.string.four_six_generate))
        }
    }

    if (savingPreset) {
        var name by remember { mutableStateOf(currentPreset?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { savingPreset = false },
            title = { Text(stringResource(R.string.four_six_preset_save)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.four_six_preset_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val trimmed = name.trim()
                        onSavePreset(trimmed, params, lockRatio)
                        currentPreset = FortySixPreset(trimmed, params, lockRatio)
                        savingPreset = false
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { savingPreset = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** The press padding of the preset list: this much backing around the label. */
private val TITLE_TOUCH_PADDING = 8.dp

/** The save button: the room for it is always held, so it is compact. */
private val SAVE_BUTTON_SIZE = 36.dp

/** The box with the list of steps: about four rows, the rest scroll inside it. */
private val STEPS_WINDOW_HEIGHT = 220.dp

/** A number with "less" and "more" buttons: more precise than a keyboard, and without one. */
@Composable
private fun Stepper(
    label: String,
    value: String,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onStep(-1) }, modifier = Modifier.size(STEPPER_TOUCH)) {
                    Icon(Icons.Rounded.Remove, contentDescription = null)
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onStep(1) }, modifier = Modifier.size(STEPPER_TOUCH)) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                }
            }
        }
    }
}

/**
 * A number that counts itself: the label and the value in place of a stepper field. With the lock
 * it turns into a toggle — a pinned ratio shows by its colour and its icon, and the dose is then
 * counted from the water rather than the other way round.
 */
@Composable
private fun Readout(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    locked: Boolean? = null,
    onToggle: (() -> Unit)? = null,
) {
    val accent = if (locked == true) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Unspecified
    }
    Column(
        modifier = if (onToggle == null) modifier else modifier.clickable(onClick = onToggle),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (locked != null) {
                Spacer(Modifier.size(2.dp))
                Icon(
                    imageVector = if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = stringResource(R.string.four_six_lock_ratio),
                    tint = if (locked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(LOCK_ICON),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(STEPPER_TOUCH),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * A slider of a few positions: a thin line with notches and a small knob. The standard Slider will
 * not do for this — it takes up half a row and looks like a volume control, while there are only
 * five ticks here.
 */
@Composable
private fun DotSlider(
    title: String,
    value: String,
    index: Int,
    count: Int,
    onIndex: (Int) -> Unit,
    hint: String? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    var width by remember { mutableIntStateOf(0) }
    // The knob cannot run off the edge, so the track starts its radius to the right of the left
    // boundary — a press is counted from the same point.
    val inset = with(LocalDensity.current) { THUMB_RADIUS.toPx() }

    val pick: (Float) -> Unit = { x ->
        val usable = (width - 2 * inset).coerceAtLeast(1f)
        val position = ((x - inset) / usable * (count - 1)).roundToInt()
        onIndex(position.coerceIn(0, count - 1))
    }

    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = accent,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SLIDER_TOUCH)
                .onSizeChanged { width = it.width }
                .pointerInput(count) { detectTapGestures { pick(it.x) } }
                .pointerInput(count) {
                    detectHorizontalDragGestures { change, _ -> pick(change.position.x) }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val thumb = THUMB_RADIUS.toPx()
                val left = thumb
                val right = size.width - thumb
                val y = size.height / 2f
                val selected = left + (right - left) * index / (count - 1)

                drawLine(
                    color = track,
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = TRACK_WIDTH.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = accent,
                    start = Offset(left, y),
                    end = Offset(selected, y),
                    strokeWidth = TRACK_WIDTH.toPx(),
                    cap = StrokeCap.Round,
                )
                repeat(count) { dot ->
                    val x = left + (right - left) * dot / (count - 1)
                    drawCircle(
                        color = if (x <= selected) accent else track,
                        radius = DOT_RADIUS.toPx(),
                        center = Offset(x, y),
                    )
                }
                drawCircle(color = accent, radius = thumb, center = Offset(selected, y))
            }
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val DOSE_STEP_GRAMS = 0.5f
private const val WATER_STEP_GRAMS = 5f
private const val MIN_WATER_GRAMS = 50f
private const val MAX_WATER_GRAMS = 1_000f
private const val MIN_DOSE_GRAMS = 5f
private const val MAX_DOSE_GRAMS = 60f

private val STEPPER_TOUCH = 40.dp
private val LOCK_ICON = 14.dp
private val SLIDER_TOUCH = 32.dp
private val TRACK_WIDTH = 2.dp
private val DOT_RADIUS = 2.5.dp
private val THUMB_RADIUS = 8.dp
