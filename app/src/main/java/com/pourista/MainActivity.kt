package com.pourista

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pourista.scale.ScaleRepository
import com.pourista.ui.AppNavigation
import com.pourista.ui.theme.PouristaTheme

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
                dynamicColor = settings.dynamicColor,
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }

}
