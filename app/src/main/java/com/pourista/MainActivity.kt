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
     * До Android 13 система про выбранный в приложении язык не знает,
     * поэтому локаль подменяем сами — раньше, чем экран возьмётся за ресурсы.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Разрешения просим, только когда про весы уже спросили и ответили
        // «есть». Настройки читаем из хранилища: в общем состоянии на свежей
        // установке ещё значения по умолчанию, и окно выскочило бы раньше
        // вопроса.
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

    /** Приложение уже было открыто, а файл нажали снаружи. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRecipeFrom(intent)
    }

    /**
     * Файл рецепта, на который нажали в мессенджере или файловом менеджере.
     * «Открыть» приходит ссылкой на файл, «поделиться» — вложением; на деле
     * это один и тот же файл, поэтому разбираем оба.
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
     * Первый запуск: сперва спрашиваем про весы и только по ответу «есть»
     * просим Bluetooth. Пока хранилище не прочитано, не показываем ничего —
     * иначе вопрос мигнёт и у тех, кто давно ответил.
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
        ReleaseNotesDialog(onDismiss = close)
    }
}
