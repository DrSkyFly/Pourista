package com.pourista.ui.brew

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pourista.R
import com.pourista.core.formatClock
import com.pourista.core.formatGrams
import com.pourista.data.presets.FortySixGenerator
import com.pourista.data.presets.FortySixParams
import com.pourista.data.presets.FortySixStrength
import com.pourista.data.presets.FortySixTaste
import com.pourista.ui.components.RecipeStepsList
import com.pourista.ui.labelRes
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Генератор 4:6: две ручки задают вкус и крепость, план проливов
 * пересчитывается на каждое движение — видно, что получится, ещё до заваривания.
 */
@Composable
fun FortySixSheetContent(
    initial: FortySixParams,
    onGenerate: (FortySixParams) -> Unit,
    modifier: Modifier = Modifier,
) {
    var params by remember { mutableStateOf(initial) }

    val steps = remember(params) { FortySixGenerator.steps(params) }
    val water = remember(params) { FortySixGenerator.waterGrams(params) }
    val gram = stringResource(R.string.unit_gram)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.four_six_title),
            style = MaterialTheme.typography.titleLarge,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Крутят дозу и воду, пропорция считается по ним: так думают,
            // когда наливают в свою чашку — «сколько кофе» и «сколько воды».
            Stepper(
                label = stringResource(R.string.four_six_dose),
                value = "${formatGrams(params.doseGrams)} $gram",
                onStep = { direction ->
                    val dose = (params.doseGrams + direction * DOSE_STEP_GRAMS)
                        .coerceIn(MIN_DOSE_GRAMS, MAX_DOSE_GRAMS)
                    params = params.copy(doseGrams = dose, ratio = water / dose)
                },
                modifier = Modifier.weight(1f),
            )
            Stepper(
                label = stringResource(R.string.recipe_water),
                value = "${formatGrams(water, 0)} $gram",
                onStep = { direction ->
                    // Шаг всегда приводит воду к круглому числу: пропорция
                    // задаётся дробью, и без этого от 251 г уйти было некуда.
                    val steps = if (direction > 0) {
                        floor(water / WATER_STEP_GRAMS) + 1
                    } else {
                        ceil(water / WATER_STEP_GRAMS) - 1
                    }
                    val next = (steps * WATER_STEP_GRAMS)
                        .coerceIn(MIN_WATER_GRAMS, MAX_WATER_GRAMS)
                    params = params.copy(ratio = next / params.doseGrams)
                },
                modifier = Modifier.weight(1f),
            )
            Readout(
                label = stringResource(R.string.four_six_ratio),
                value = stringResource(R.string.four_six_ratio_value, formatGrams(params.ratio)),
                modifier = Modifier.weight(0.6f),
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

        RecipeStepsList(steps = steps)

        Button(
            onClick = { onGenerate(params) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.four_six_generate))
        }
    }
}

/** Число с кнопками «меньше» и «больше»: точнее клавиатуры и без неё. */
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
                    Icon(Icons.Default.Remove, contentDescription = null)
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onStep(1) }, modifier = Modifier.size(STEPPER_TOUCH)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        }
    }
}

/** Число, которое считается само: подпись и значение на месте шагового поля. */
@Composable
private fun Readout(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * Ползунок на несколько положений: тонкая линия с засечками и небольшая ручка.
 * Штатный Slider под такое не годится — он занимает полстроки и выглядит как
 * регулятор громкости, а здесь всего пять делений.
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
    // Ручка не может уехать за край, поэтому дорожка начинается на её радиус
    // правее левой границы — попадание по нажатию считаем от той же точки.
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
private val SLIDER_TOUCH = 32.dp
private val TRACK_WIDTH = 2.dp
private val DOT_RADIUS = 2.5.dp
private val THUMB_RADIUS = 8.dp
