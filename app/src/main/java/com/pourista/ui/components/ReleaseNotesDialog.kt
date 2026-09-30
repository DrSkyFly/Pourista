package com.pourista.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.pourista.R
import com.pourista.BuildConfig
import com.pourista.ui.ReleaseNotes

/**
 * What is new: the whole history of changes, the freshest on top. Shown after an update and
 * opened from the settings — whoever skipped a few versions reads it all at once.
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
                if (BuildConfig.PLAY_LINK) GooglePlayCall()
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

/**
 * The app is in the store. It stands above the list of changes and is set off in colour: whoever
 * put the APK on by hand goes on updating it by hand, while from the store the updates come by
 * themselves.
 *
 * The store's own badge artwork is not used: it comes under brand rules of its own, it is drawn
 * anew for every language, and a banner pasted into a dialog would look like an advertisement.
 * A card of the app's own, pressable whole, says the same thing.
 */
@Composable
private fun GooglePlayCall() {
    val uriHandler = LocalUriHandler.current
    val link = stringResource(R.string.play_link)

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
        onClick = { runCatching { uriHandler.openUri("https://$link") } },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Shop,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.play_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.play_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.play_open),
                        style = MaterialTheme.typography.labelLarge,
                        textDecoration = TextDecoration.Underline,
                    )
                    Spacer(Modifier.size(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** Higher and the dialog runs into the edges of the screen — beyond that the list scrolls. */
private val MAX_HEIGHT = 420.dp
