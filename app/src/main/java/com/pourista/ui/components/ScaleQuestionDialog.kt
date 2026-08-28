package com.pourista.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pourista.R

/**
 * Первый запуск: есть ли у человека весы. Ответ «нет» убирает из приложения
 * всё про Bluetooth, а главное — избавляет от системного запроса разрешений,
 * который иначе выскакивает у всех подряд, включая тех, кому весы не нужны.
 *
 * Окно без крестика и без закрытия по фону: ответ нужен обязательно, иначе
 * непонятно, спрашивать разрешения или нет.
 */
@Composable
fun ScaleQuestionDialog(onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.scale_question_title)) },
        text = { Text(stringResource(R.string.scale_question_text)) },
        confirmButton = {
            TextButton(onClick = onYes) { Text(stringResource(R.string.scale_question_yes)) }
        },
        dismissButton = {
            TextButton(onClick = onNo) { Text(stringResource(R.string.scale_question_no)) }
        },
    )
}
