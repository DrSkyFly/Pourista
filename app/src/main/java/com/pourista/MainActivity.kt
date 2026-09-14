package com.pourista

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.core.content.IntentCompat
import com.pourista.core.AppLocale
import com.pourista.scale.ScaleRepository
import com.pourista.ui.AppNavigation
import com.pourista.ui.components.ReleaseNotesDialog
import com.pourista.ui.components.ScaleQuestionDialog
import com.pourista.ui.theme.PouristaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            val settings = container.settingsState.value
            if (granted.values.all { it } && settings.useScale && settings.autoConnectOnLaunch) {
                container.scale.startScan()
            }
        }

    private val container: AppContainer get() = appContainer

    /**
     * Before Android 13 the system knows nothing about the language chosen in the app, so we
     * substitute the locale ourselves — before the screen gets to the resources.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // We ask for permissions only once the scale question has been asked and answered with
        // "yes". The settings are read from the storage: on a fresh installation the common state
        // still holds the defaults, and the dialog would pop up before the question.
        lifecycleScope.launch {
            val settings = container.settings.current()
            val ready = settings.useScale && !settings.needScaleQuestion
            if (ready && !container.scale.hasPermissions()) {
                permissionLauncher.launch(ScaleRepository.requiredPermissions())
            }
        }

        openRecipeFrom(intent)

        setContent {
            val settings by container.settingsState.collectAsStateWithLifecycle()
            PouristaTheme(
                themeMode = settings.themeMode,
                palette = settings.palette,
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
                FirstRun()
            }
        }
    }

    /** The app was already open, and a file was tapped outside it. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRecipeFrom(intent)
    }

    /**
     * A recipe file tapped in a messenger or a file manager. "Open" arrives as a link to the file,
     * "share" as an attachment; in fact it is one and the same file, so we take both apart.
     */
    private fun openRecipeFrom(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(
                intent,
                Intent.EXTRA_STREAM,
                Uri::class.java,
            )

            else -> null
        } ?: return
        container.openRecipeFile(uri)
    }

    /**
     * The first run: first we ask about the scale and only on a "yes" ask for Bluetooth. While the
     * storage has not been read we show nothing — otherwise the question would flash for those who
     * answered it long ago.
     */
    @Composable
    private fun FirstRun() {
        var ask by remember { mutableStateOf<Boolean?>(null) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) { ask = container.settings.current().needScaleQuestion }

        val answer = { hasScale: Boolean ->
            ask = false
            scope.launch {
                container.settings.setUseScale(hasScale)
                container.settings.setScaleAsked()
            }
            if (hasScale) permissionLauncher.launch(ScaleRepository.requiredPermissions())
        }

        when (ask) {
            null -> Unit
            true -> ScaleQuestionDialog(onYes = { answer(true) }, onNo = { answer(false) })
            false -> WhatsNewDialog()
        }
    }

    /**
     * What is new — once per version. The mark is read from the storage rather than from the common
     * settings state: before the first read it holds the defaults, and the dialog would flash for
     * everyone.
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
        ReleaseNotesDialog(onDismiss = close)
    }
}
