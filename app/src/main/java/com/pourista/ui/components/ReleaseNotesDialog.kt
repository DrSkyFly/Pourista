package com.pourista.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pourista.R
import com.pourista.ui.ReleaseNotes

/**
 * Что нового: вся история изменений, свежее сверху. Показывается после
 * обновления и открывается из настроек — кто пропустил несколько версий,
 * прочитает всё разом.
 */
@Composable
fun ReleaseNotesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whats_new_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ReleaseNotes.all.forEach { release ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = release.version,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(release.body),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
    )
}

/** Выше окно упирается в края экрана — дальше список листается. */
private val MAX_HEIGHT = 420.dp
