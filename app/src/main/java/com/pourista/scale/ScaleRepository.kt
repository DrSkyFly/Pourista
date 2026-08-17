package com.pourista.scale

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.pourista.BuildConfig
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.ConnectionState
import com.welie.blessed.WriteType
import com.welie.blessed.asUInt8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnectionStatus { IDLE, SCANNING, CONNECTING, CONNECTED, RECONNECTING }

data class ScaleState(
    val status: ConnectionStatus = ConnectionStatus.IDLE,
    val deviceName: String? = null,
    /** Текущий вес на весах в граммах. */
    val weightGrams: Float = 0f,
    val batteryPercent: Int? = null,
) {
    val isConnected: Boolean get() = status == ConnectionStatus.CONNECTED
    val isBusy: Boolean
        get() = status == ConnectionStatus.SCANNING ||
            status == ConnectionStatus.CONNECTING ||
            status == ConnectionStatus.RECONNECTING
}

/**
 * Связь с весами: поиск, подключение, поток веса и команды. Живёт на всё время
 * работы приложения, поэтому экран заваривания можно закрывать и открывать,
 * не теряя соединение.
 */
class ScaleRepository(context: Context) {

    private val appContext = context.applicationContext
    private val central = BluetoothCentralManager(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(ScaleState())
    val state: StateFlow<ScaleState> = _state.asStateFlow()

    private var peripheral: BluetoothPeripheral? = null
    private var reconnectJob: Job? = null

    /**
     * Протокол подключённых весов. До первой находки — наш собственный: он
     * единственный проверен на железе, и с ним же приложение жило раньше.
     */
    private var driver: ScaleDriver = FutulaDriver

    /** Пользователь отключился сам — не переподключаемся молча. */
    private var userDisconnected = false

    /** Возвращать ли весы в граммы, если их переключили кнопкой на корпусе. */
    private var keepGrams = true

    private var connectionObserverStarted = false

    private var lastPacketLogAt = 0L

    fun keepGrams(enabled: Boolean) {
        keepGrams = enabled
        if (!enabled) return
        val current = peripheral ?: return
        val command = driver.unitCommand(WeightUnit.GRAM) ?: return
        scope.launch { sendCommand(current, command) }
    }

    /**
     * Разрешения спрашивает экран, а сюда запрос на поиск может прийти раньше —
     * например от автоподключения при запуске. Без проверки библиотека бросает
     * SecurityException и роняет приложение.
     */
    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasPermissions()) {
            Log.d(TAG, "Нет разрешений на Bluetooth, поиск не запускаем")
            return
        }
        if (_state.value.isConnected || _state.value.status == ConnectionStatus.SCANNING) return
        userDisconnected = false
        observeConnectionStateOnce()
        _state.update { it.copy(status = ConnectionStatus.SCANNING) }
        runCatching {
            // Имена сверяет сама библиотека, подстрокой: у весов они пишутся
            // по-разному, и точное совпадение отсекало бы половину моделей.
            central.scanForPeripheralsWithNames(
                ScaleDrivers.nameFragments,
                { found, scanResult ->
                    val matched = ScaleDrivers.forName(found.name)
                    if (matched == null) {
                        Log.d(TAG, "Устройство ${found.name} не опознано, ищем дальше")
                        return@scanForPeripheralsWithNames
                    }
                    val mark = if (matched.experimental) ", поддержка тестовая" else ""
                    Log.d(TAG, "Найдены весы ${found.name} (${matched.title}$mark), RSSI ${scanResult.rssi}")
                    central.stopScan()
                    peripheral = found
                    driver = matched
                    _state.update {
                        it.copy(status = ConnectionStatus.CONNECTING, deviceName = found.name)
                    }
                    connect(found)
                },
                { failure ->
                    Log.d(TAG, "Поиск не удался: $failure")
                    _state.update { it.copy(status = ConnectionStatus.IDLE) }
                },
            )
        }.onFailure { error ->
            Log.d(TAG, "Не удалось запустить поиск: $error")
            _state.update { it.copy(status = ConnectionStatus.IDLE) }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        central.stopScan()
        if (_state.value.status == ConnectionStatus.SCANNING) {
            _state.update { it.copy(status = ConnectionStatus.IDLE) }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        userDisconnected = true
        reconnectJob?.cancel()
        central.stopScan()
        peripheral?.let { device -> scope.launch { central.cancelConnection(device) } }
        _state.update { ScaleState() }
    }

    fun tare() {
        val current = peripheral ?: return
        val command = driver.tareCommand() ?: return
        scope.launch { sendCommand(current, command) }
    }

    private fun connect(target: BluetoothPeripheral) {
        scope.launch {
            runCatching { central.connectPeripheral(target) }
                .onFailure {
                    Log.d(TAG, "Не удалось подключиться: $it")
                    _state.update { state -> state.copy(status = ConnectionStatus.IDLE) }
                }
        }
    }

    private fun observeConnectionStateOnce() {
        if (connectionObserverStarted) return
        connectionObserverStarted = true
        central.observeConnectionState { device, state ->
            Log.d(TAG, "Весы ${device.name}: $state")
            when (state) {
                ConnectionState.CONNECTING ->
                    _state.update { it.copy(status = ConnectionStatus.CONNECTING) }

                ConnectionState.CONNECTED -> {
                    peripheral = device
                    _state.update {
                        it.copy(status = ConnectionStatus.CONNECTED, deviceName = device.name)
                    }
                    startObserving(device)
                }

                ConnectionState.DISCONNECTING -> Unit

                ConnectionState.DISCONNECTED -> {
                    _state.update {
                        it.copy(
                            status = if (userDisconnected) {
                                ConnectionStatus.IDLE
                            } else {
                                ConnectionStatus.RECONNECTING
                            },
                            batteryPercent = null,
                        )
                    }
                    if (!userDisconnected) scheduleReconnect(device)
                }
            }
        }
    }

    private fun scheduleReconnect(device: BluetoothPeripheral) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (userDisconnected) return@launch
            if (central.getPeripheral(device.address).getState() == ConnectionState.DISCONNECTED) {
                connect(device)
            }
        }
    }

    private fun startObserving(device: BluetoothPeripheral) {
        val active = driver
        scope.launch {
            try {
                val weight = device.getCharacteristic(active.service, active.weightCharacteristic)
                val battery = active.batteryService?.let { service ->
                    active.batteryCharacteristic?.let { device.getCharacteristic(service, it) }
                }

                if (weight != null) {
                    device.observe(weight) { value ->
                        val reading = active.parseWeight(value) ?: run {
                            // Заряд у части весов приходит отдельным кадром
                            // в ту же характеристику, что и вес.
                            active.parseBattery(value)?.let { percent ->
                                _state.update { it.copy(batteryPercent = percent) }
                            }
                            logPacket(value, null)
                            return@observe
                        }
                        logPacket(value, reading)
                        // Весы могут показывать унции: рецепты и подсказки в граммах,
                        // поэтому возвращаем их обратно, если модель это умеет.
                        if (keepGrams && reading.unitOnScale != null &&
                            reading.unitOnScale != WeightUnit.GRAM
                        ) {
                            active.unitCommand(WeightUnit.GRAM)?.let { command ->
                                scope.launch { sendCommand(device, command) }
                            }
                        }
                        _state.update { state ->
                            state.copy(
                                weightGrams = reading.grams,
                                batteryPercent = reading.batteryPercent ?: state.batteryPercent,
                            )
                        }
                    }
                }

                // Отдельная служба заряда есть не у всех: у остальных он приходит
                // в том же пакете, что и вес.
                if (battery != null) {
                    device.observe(battery) { value ->
                        _state.update { it.copy(batteryPercent = value.asUInt8()?.toInt()) }
                    }
                }

                if (keepGrams) active.unitCommand(WeightUnit.GRAM)?.let { sendCommand(device, it) }
                active.onConnectCommands().forEach { sendCommand(device, it) }
            } catch (e: Exception) {
                Log.d(TAG, "Не удалось подписаться на характеристики: $e")
            }
        }
    }

    /**
     * Диагностика протокола: раз в секунду печатает сырой пакет и то, как он
     * разобран. Нужна при проверке на живых весах, в релизе не собирается.
     */
    private fun logPacket(value: ByteArray, reading: WeightReading?) {
        if (!BuildConfig.DEBUG) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastPacketLogAt < PACKET_LOG_INTERVAL_MS) return
        lastPacketLogAt = now
        val hex = value.joinToString(" ") { "%02x".format(it) }
        val parsed = reading?.let { "%.1f г, единица на весах %s".format(it.grams, it.unitOnScale) }
            ?: "разобрать не удалось"
        Log.d(TAG, "Пакет веса [$hex] → $parsed")
    }

    private suspend fun sendCommand(device: BluetoothPeripheral, command: ByteArray) {
        try {
            val target = driver.commandCharacteristic ?: return
            val characteristic = device.getCharacteristic(driver.service, target) ?: return
            device.writeCharacteristic(
                characteristic,
                command,
                WriteType.WITH_RESPONSE,
            )
        } catch (e: Exception) {
            Log.d(TAG, "Команда не доставлена: $e")
        }
    }

    companion object {
        private const val TAG = "ScaleRepository"
        private const val RECONNECT_DELAY_MS = 10_000L
        private const val PACKET_LOG_INTERVAL_MS = 1_000L

        /** Разрешения, без которых BLE-поиск невозможен. */
        fun requiredPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                )
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
    }
}
