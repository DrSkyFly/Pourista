package com.pourista.ui.diagnostics

import android.os.Build
import androidx.lifecycle.ViewModel
import com.pourista.AppContainer
import com.pourista.BuildConfig
import com.pourista.scale.ScaleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Запись протокола весов. Файл кладём в кэш: он нужен ровно до отправки,
 * и система вольна убрать его сама.
 */
class ScaleLogViewModel(private val container: AppContainer) : ViewModel() {

    val scale: StateFlow<ScaleState> = container.scale.state
    val packets: StateFlow<Int> = container.scale.diagnosticsPackets

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _file = MutableStateFlow<File?>(null)

    /** Записанный журнал — его предлагаем отправить. */
    val file: StateFlow<File?> = _file.asStateFlow()

    fun start() {
        _file.value = null
        container.scale.startDiagnostics(header())
        _recording.value = true
    }

    fun stop() {
        val text = container.scale.stopDiagnostics()
        _recording.value = false
        if (text != null) _file.value = save(text)
    }

    fun tare() = container.scale.tare()

    fun connect() = container.scale.startScan()

    fun hasPermissions(): Boolean = container.scale.hasPermissions()

    private fun header(): List<String> {
        val state = scale.value
        val scales = state.deviceName?.let { "\"$it\"" } ?: "не подключены"
        return listOf(
            "Pourista ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), " +
                "${Build.MANUFACTURER} ${Build.MODEL}",
            "Весы: $scales, состояние ${state.status}",
        )
    }

    private fun save(text: String): File? = runCatching {
        val directory = File(container.appContext.cacheDir, LOG_DIRECTORY).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        File(directory, "pourista-scale-$stamp.txt").apply { writeText(text) }
    }.getOrNull()

    private companion object {
        const val LOG_DIRECTORY = "logs"
    }
}
