package com.pourista.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pourista.R

/**
 * The first run: does the person have a scale. An answer of "no" removes everything about
 * Bluetooth from the app and, most importantly, spares them the system permission request, which
 * otherwise pops up for everyone, those who need no scale included.
 *
 * A dialog with no cross and no dismissal by tapping outside: the answer is required, otherwise
 * there is no telling whether to ask for permissions.
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
