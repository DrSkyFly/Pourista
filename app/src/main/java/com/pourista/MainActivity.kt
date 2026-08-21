package com.pourista

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pourista.scale.ScaleRepository
import com.pourista.ui.AppNavigation
import com.pourista.ui.theme.PouristaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.all { it } && container.settingsState.value.autoConnectOnLaunch) {
                container.scale.startScan()
            }
        }

    private val container: AppContainer get() = appContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (!container.scale.hasPermissions()) {
            permissionLauncher.launch(ScaleRepository.requiredPermissions())
        }

        setContent {
            val settings by container.settingsState.collectAsStateWithLifecycle()
            PouristaTheme(
                themeMode = settings.themeMode,
                palette = settings.palette,
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
                WhatsNewDialog()
            }
        }
    }

    /**
     * Что нового — один раз на версию. Отметку читаем из хранилища, а не из
     * общего состояния настроек: до первого чтения там значения по умолчанию,
     * и окно мигнуло бы у всех подряд.
     */
    @Composable
    private fun WhatsNewDialog() {
        var show by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            show = container.settings.current().whatsNewSeenVersion != BuildConfig.VERSION_CODE
        }
        if (!show) return

        val close = {
            show = false
            scope.launch { container.settings.setWhatsNewSeenVersion(BuildConfig.VERSION_CODE) }
            Unit
        }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.whats_new_title, BuildConfig.VERSION_NAME)) },
            text = { Text(stringResource(R.string.whats_new_body)) },
            confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}
