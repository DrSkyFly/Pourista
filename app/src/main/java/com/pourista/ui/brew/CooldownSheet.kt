package com.pourista.ui.brew

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pourista.R
import com.pourista.brew.COOLDOWN_STEP_SECONDS
import com.pourista.brew.CooldownState
import com.pourista.brew.MAX_COOLDOWN_SECONDS
import com.pourista.brew.MIN_COOLDOWN_SECONDS
import com.pourista.core.formatClock

/**
 * Таймер остывания. Кофе из воронки идёт кипятком, и первые минуты его не
 * столько пьют, сколько дуют на чашку, — таймер снимает необходимость
 * караулить её самому.
 *
 * К проливу отношения не имеет, поэтому и настройки у него всего две: сколько
 * ждать и заводить ли его самому по окончании заваривания.
 */
@Composable
fun CooldownSheetContent(
    seconds: Int,
    autoStart: Boolean,
    state: CooldownState,
    onSeconds: (Int) -> Unit,
    onAutoStart: (Boolean) -> Unit,
    onStart: () -> Unit,
    onSave: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.cooldown_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 4.dp),
        )
        Text(
            text = stringResource(R.string.cooldown_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )

        // Пока таймер идёт, на месте настройки — обратный отсчёт: крутить
        // время у заведённого таймера некуда, а знать, сколько осталось, надо.
        if (state.running) Countdown(state) else Duration(seconds, onSeconds)

        Block {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAutoStart(!autoStart) }
                    .padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.cooldown_auto_start),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = autoStart, onCheckedChange = onAutoStart)
            }
        }

        if (state.running) {
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.cooldown_stop))
            }
        } else {
            // «Запустить» заводит таймер, не дожидаясь конца заваривания:
            // чашку остужают и просто так. «Сохранить» только закрывает лист —
            // настройки запоминаются на каждое движение, — но без него
            // непонятно, что выставленное время никуда не денется.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cooldown_start))
                }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

/** Время остывания: полминуты на нажатие — мельче для чашки не имеет смысла. */
@Composable
private fun Duration(seconds: Int, onSeconds: (Int) -> Unit) {
    Block {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    onSeconds((seconds - COOLDOWN_STEP_SECONDS).coerceAtLeast(MIN_COOLDOWN_SECONDS))
                },
                enabled = seconds > MIN_COOLDOWN_SECONDS,
                modifier = Modifier.size(STEP_TOUCH),
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = null)
            }
            Text(
                text = formatClock(seconds),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    onSeconds((seconds + COOLDOWN_STEP_SECONDS).coerceAtMost(MAX_COOLDOWN_SECONDS))
                },
                enabled = seconds < MAX_COOLDOWN_SECONDS,
                modifier = Modifier.size(STEP_TOUCH),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
            }
        }
    }
}

/** Сколько осталось. Заведённое время подписью снизу: видно, докуда идём. */
@Composable
private fun Countdown(state: CooldownState) {
    Card(
        shape = BlockShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(READOUT_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatClock(state.remainingSeconds),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(
                        R.string.cooldown_of_total,
                        formatClock(state.totalSeconds),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/** Подложка блока — та же, что в пересчёте помола: листы должны быть похожи. */
@Composable
private fun Block(content: @Composable () -> Unit) {
    Card(
        shape = BlockShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(10.dp)) { content() }
    }
}

private val BlockShape = RoundedCornerShape(24.dp)
private val STEP_TOUCH = 48.dp

/** Высота окна с отсчётом равна высоте настройки: лист не должен дёргаться. */
private val READOUT_HEIGHT = 68.dp
