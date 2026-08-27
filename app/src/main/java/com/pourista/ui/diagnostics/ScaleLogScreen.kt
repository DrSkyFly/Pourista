package com.pourista.ui.diagnostics

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pourista.R
import com.pourista.scale.ScaleRepository
import com.pourista.ui.listSidePadding
import com.pourista.ui.theme.AppTheme

/**
 * Запись протокола весов. Экран ведёт по шагам: подключиться, начать запись,
 * поделать с весами понятные вещи, отправить файл.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleLogScreen(
    viewModel: ScaleLogViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by viewModel.scale.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val packets by viewModel.packets.collectAsStateWithLifecycle()
    val file by viewModel.file.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> if (granted.values.all { it }) viewModel.connect() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = AppTheme.topBarColors(),
                modifier = AppTheme.topBarModifier(),
                title = { Text(stringResource(R.string.scale_log_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel),
                        )
                    }
                },
            )
        },
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
                Text(
                    text = stringResource(R.string.scale_log_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(
                                if (scale.isConnected) {
                                    R.string.scale_log_connected
                                } else {
                                    R.string.scale_log_disconnected
                                },
                                scale.deviceName.orEmpty(),
                            ),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (!scale.isConnected) {
                            OutlinedButton(
                                onClick = {
                                    if (viewModel.hasPermissions()) {
                                        viewModel.connect()
                                    } else {
                                        permissionLauncher.launch(
                                            ScaleRepository.requiredPermissions()
                                        )
                                    }
                                },
                                modifier = Modifier.padding(top = 8.dp),
                            ) { Text(stringResource(R.string.action_connect)) }
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.scale_log_steps),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            item {
                if (recording) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.scale_log_recording, packets),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = viewModel::tare,
                                enabled = scale.isConnected,
                            ) { Text(stringResource(R.string.action_tare)) }
                            Button(onClick = viewModel::stop) {
                                Text(stringResource(R.string.scale_log_stop))
                            }
                        }
                    }
                } else {
                    Button(onClick = viewModel::start) {
                        Text(stringResource(R.string.scale_log_start))
                    }
                }
            }

            val ready = file
            if (ready != null && !recording) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.scale_log_ready, ready.name),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(R.string.scale_log_send_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 12.dp),
                            ) {
                                Button(onClick = {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.files",
                                        ready,
                                    )
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(
                                            Intent.EXTRA_SUBJECT,
                                            context.getString(R.string.scale_log_subject),
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    val chooser = Intent.createChooser(
                                        send,
                                        context.getString(R.string.scale_log_share),
                                    )
                                    runCatching { context.startActivity(chooser) }
                                }) {
                                    Icon(Icons.Rounded.Share, contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                    Text(stringResource(R.string.scale_log_share))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
