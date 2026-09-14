package com.pourista.scale

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.math.abs

enum class ConnectionStatus {
    IDLE,

    /** Bluetooth is off on the phone: nothing to search with, and it is not our fault. */
    BLUETOOTH_OFF,
    SCANNING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
}

data class ScaleState(
    val status: ConnectionStatus = ConnectionStatus.IDLE,
    val deviceName: String? = null,
    /** The current weight on the scale in grams. */
    val weightGrams: Float = 0f,
    /**
     * Flow rate in grams per second, if the scale counts it itself. Null means the scale does
     * not send one, and the engine counts the rate from the weight gain.
     */
    val flowRate: Float? = null,
    val batteryPercent: Int? = null,
) {
    val isConnected: Boolean get() = status == ConnectionStatus.CONNECTED
    val isBusy: Boolean
        get() = status == ConnectionStatus.SCANNING ||
            status == ConnectionStatus.CONNECTING ||
            status == ConnectionStatus.RECONNECTING
}

/**
 * The link to the scale: searching, connecting, the weight stream and commands. It lives for
 * as long as the app runs, so the brew screen can be closed and opened without losing the
 * connection.
 */
class ScaleRepository(context: Context) {

    private val appContext = context.applicationContext
    private val central = BluetoothCentralManager(appContext)
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /**
     * The last line of defence. The connection library throws exceptions out of coroutines —
     * for example when asked to disconnect from a device it has already forgotten about.
     * Without a handler such an exception goes to the stream handler, that is, to a crash.
     */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, error ->
                Log.e(TAG, "Failure while working with the scale", error)
            },
    )

    private val _state = MutableStateFlow(ScaleState())
    val state: StateFlow<ScaleState> = _state.asStateFlow()

    private var peripheral: BluetoothPeripheral? = null
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var tareJob: Job? = null

    /**
     * The protocol of the connected scale. Until the first find it is our own: it is the only
     * one checked on hardware, and it is what the app lived with before.
     */
    private var driver: ScaleDriver = FutulaDriver

    /** The user disconnected on purpose — we do not reconnect quietly. */
    private var userDisconnected = false

    /** Whether to put the scale back into grams if it was switched by the button on its body. */
    private var keepGrams = true

    private var connectionObserverStarted = false

    /** The diagnostics looks over the air itself — the ordinary search does not run meanwhile. */
    private var diagnosticScan = false

    private var lastPacketLogAt = 0L

    /** When the scale was last asked to come back to grams. */
    private var lastUnitCommandAt = 0L

    /** Whether at least one parsed weight has arrived since connecting. */
    @Volatile
    private var readingSeen = false

    /** A protocol recording is running: the log is kept while it is here. */
    @Volatile
    private var diagnostics: ScaleDiagnostics? = null

    private val _diagnosticsPackets = MutableStateFlow(0)

    /** How many packets are already written: the diagnostics screen shows the counter. */
    val diagnosticsPackets: StateFlow<Int> = _diagnosticsPackets.asStateFlow()

    init {
        /*
          With Bluetooth off the connection library quietly does nothing: no exception, no
          error in the callback. And it does not watch for it being turned on — its STATE_ON
          handler holds nothing but a log line. So we watch the adapter ourselves.
        */
        central.observeAdapterState { adapterState ->
            when (adapterState) {
                BluetoothAdapter.STATE_ON -> onBluetoothOn()
                BluetoothAdapter.STATE_OFF -> onBluetoothOff()
            }
        }
    }

    /**
     * Bluetooth has been turned on. The search resumes only if we were the ones who did not
     * start it because the adapter was off: in every other case the scale is not wanted right
     * now, and there is no reason to go on the air.
     */
    private fun onBluetoothOn() {
        if (_state.value.status != ConnectionStatus.BLUETOOTH_OFF) return
        Log.d(TAG, "Bluetooth turned on, resuming the search")
        startScan()
    }

    /**
     * Bluetooth has been turned off. The library forgets about the device at that point, and
     * it can no longer be asked to disconnect — it throws an exception. So the reference to the
     * scale is let go right here.
     */
    private fun onBluetoothOff() {
        Log.d(TAG, "Bluetooth turned off")
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        tareJob?.cancel()
        peripheral = null
        _state.update { ScaleState(status = ConnectionStatus.BLUETOOTH_OFF) }
    }

    private fun bluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun keepGrams(enabled: Boolean) {
        keepGrams = enabled
        if (!enabled) return
        val current = peripheral ?: return
        val command = driver.unitCommand(WeightUnit.GRAM) ?: return
        scope.launch { sendCommand(current, command) }
    }

    /**
     * Permissions are asked for by the screen, but a search request can arrive here earlier —
     * from the auto-connect at startup, for instance. Without the check the library throws a
     * SecurityException and brings the app down.
     */
    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasPermissions()) {
            Log.d(TAG, "No Bluetooth permissions, not starting the search")
            return
        }
        // Without this check the status would go to "searching" while no search started at
        // all, and the next call would run into the check a line below.
        if (!bluetoothEnabled()) {
            Log.d(TAG, "Bluetooth is off, not starting the search")
            _state.update { it.copy(status = ConnectionStatus.BLUETOOTH_OFF) }
            return
        }
        if (_state.value.isConnected || _state.value.status == ConnectionStatus.SCANNING) return
        userDisconnected = false
        observeConnectionStateOnce()
        _state.update { it.copy(status = ConnectionStatus.SCANNING) }
        runCatching {
            // We look over the whole air and match the names ourselves: the library compares
            // case-sensitively, and a scale advertised as "TIMEMORE_Dot" did not fall under a
            // lowercase "timemore".
            central.scanForPeripherals(
                { found, scanResult ->
                    val name = found.advertisedName(scanResult)
                    val matched = ScaleDrivers.forName(name) ?: return@scanForPeripherals
                    val mark = if (matched.experimental) ", support is in beta" else ""
                    Log.d(TAG, "Found a scale $name (${matched.title}$mark), RSSI ${scanResult.rssi}")
                    central.stopScan()
                    peripheral = found
                    driver = matched
                    _state.update {
                        it.copy(status = ConnectionStatus.CONNECTING, deviceName = name)
                    }
                    connect(found)
                },
                { failure ->
                    Log.d(TAG, "The search failed: $failure")
                    _state.update { it.copy(status = ConnectionStatus.IDLE) }
                },
            )
        }.onFailure { error ->
            Log.d(TAG, "Could not start the search: $error")
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
        heartbeatJob?.cancel()
        tareJob?.cancel()
        runCatching { central.stopScan() }
        // The library may have forgotten the device already — that happens after the adapter is
        // turned off. It then throws an exception when asked to disconnect.
        peripheral?.let { device ->
            scope.launch { runCatching { central.cancelConnection(device) } }
        }
        peripheral = null
        _state.update { ScaleState() }
    }

    /**
     * Zeroing the scale.
     *
     * The command goes out without acknowledgement: whether the scale heard it or not, there
     * will be no answer. So we watch the readings. The weight has gone to zero — the tare went
     * through. The weight is unchanged — the command was not heard, send it again. The weight
     * has moved — something was put on the scale or taken off, and repeating is no longer
     * allowed: we would zero out something other than what the person wanted.
     */
    fun tare() {
        diagnostics?.event("\"Tare\" pressed")
        val current = peripheral ?: run {
            diagnostics?.event("the tare did not go out: the scale is not connected")
            return
        }
        val command = driver.tareCommand() ?: run {
            diagnostics?.event("the tare did not go out: ${driver.title} cannot zero itself")
            return
        }
        tareJob?.cancel()
        tareJob = scope.launch {
            val before = _state.value.weightGrams
            repeat(TARE_ATTEMPTS) { attempt ->
                if (!_state.value.isConnected) return@launch
                if (attempt > 0) diagnostics?.event("the tare did not work, sending again")
                sendCommand(current, command)
                delay(TARE_CHECK_MS)
                val now = _state.value.weightGrams
                if (abs(now) <= TARE_TOLERANCE_GRAMS) return@launch
                if (abs(now - before) > TARE_TOLERANCE_GRAMS) return@launch
            }
            diagnostics?.event("the tare never worked")
        }
    }

    /**
     * Starts recording the scale protocol.
     *
     * If the scale is connected, it lays out its services and subscribes to everything that can
     * notify: a model that will not work usually sends the data somewhere other than where the
     * driver waits for it. If there is no connection, it looks over the air: the name in the
     * advertisement shows whether the app recognises this scale at all.
     */
    @SuppressLint("MissingPermission")
    fun startDiagnostics(header: List<String>) {
        val log = ScaleDiagnostics(header)
        diagnostics = log
        _diagnosticsPackets.value = 0

        val device = peripheral
        if (_state.value.isConnected && device != null) {
            log.note("Driver: ${driver.title}")
            dumpServices(device, log)
            observeEverything(device, log)
        } else {
            log.note("The scale is not connected — looking at what is on the air")
            log.note("")
            scanForDiagnostics(log)
        }
    }

    /** Stops the recording and hands over the log. */
    @SuppressLint("MissingPermission")
    fun stopDiagnostics(): String? {
        val log = diagnostics ?: return null
        diagnostics = null
        if (diagnosticScan) {
            diagnosticScan = false
            central.stopScan()
            if (_state.value.status == ConnectionStatus.SCANNING) {
                _state.update { it.copy(status = ConnectionStatus.IDLE) }
            }
        }
        return log.text()
    }

    private fun dumpServices(device: BluetoothPeripheral, log: ScaleDiagnostics) {
        log.note("")
        log.note("Services of the device:")
        device.services.forEach { service ->
            log.note("  ${service.uuid}")
            service.characteristics.forEach { characteristic ->
                log.note("    ${characteristic.uuid}  ${characteristic.propertyNames()}")
            }
        }
        log.note("")
        log.note("The driver waits for the weight in ${driver.weightCharacteristic}")
        log.note("")
        log.note("time    characteristic  bytes → parse")
    }

    /**
     * Subscribing to every notifying characteristic. Blessed does not allow unsubscribing, so
     * the extra subscriptions live until the connection breaks — they stop writing together with
     * the end of the log.
     */
    private fun observeEverything(device: BluetoothPeripheral, log: ScaleDiagnostics) {
        scope.launch {
            device.services.forEach { service ->
                service.characteristics.forEach { characteristic ->
                    if (!characteristic.notifies()) return@forEach
                    if (characteristic.uuid == driver.weightCharacteristic) return@forEach
                    runCatching {
                        device.observe(characteristic) { value ->
                            val current = diagnostics ?: return@observe
                            current.packet(characteristic.uuid.toString(), value, "not a weight")
                            _diagnosticsPackets.value = current.packetCount
                        }
                    }.onFailure {
                        log.event("could not subscribe to ${characteristic.uuid}: $it")
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanForDiagnostics(log: ScaleDiagnostics) {
        if (!hasPermissions()) {
            log.note("No Bluetooth permission — could not look over the air")
            return
        }
        diagnosticScan = true
        val seen = mutableSetOf<String>()
        runCatching {
            central.scanForPeripherals(
                { found, result ->
                    // The address is only needed so as not to repeat a device in the log.
                    if (!seen.add(found.address)) return@scanForPeripherals
                    val advertised = found.advertisedName(result)
                    val name = advertised.ifBlank { "(no name)" }
                    val services = result.scanRecord?.serviceUuids
                        ?.joinToString(", ") { it.uuid.toString() }
                        ?: "not advertised"
                    val known = ScaleDrivers.forName(advertised)?.title ?: "not known to the app"
                    log.event("on the air: \"$name\", RSSI ${result.rssi}, services: $services → $known")
                },
                { failure -> log.event("the search failed: $failure") },
            )
        }.onFailure { log.event("the search did not start: $it") }
    }

    private fun connect(target: BluetoothPeripheral) {
        scope.launch {
            runCatching { central.connectPeripheral(target) }
                .onFailure {
                    Log.d(TAG, "Could not connect: $it")
                    _state.update { state -> state.copy(status = ConnectionStatus.IDLE) }
                }
        }
    }

    private fun observeConnectionStateOnce() {
        if (connectionObserverStarted) return
        connectionObserverStarted = true
        central.observeConnectionState { device, state ->
            Log.d(TAG, "Scale ${device.name}: $state")
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
                    heartbeatJob?.cancel()
                    _state.update {
                        it.copy(
                            status = if (userDisconnected) {
                                ConnectionStatus.IDLE
                            } else {
                                ConnectionStatus.RECONNECTING
                            },
                            // The last weight is left on the screen, while a flow rate with no
                            // new packets would mean a pour that is not happening.
                            flowRate = null,
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
            // A device the library knows nothing about it refuses to hand over — with an
            // exception rather than with a null.
            val known = runCatching { central.getPeripheral(device.address) }.getOrNull()
            if (known?.getState() == ConnectionState.DISCONNECTED) {
                connect(device)
            }
        }
    }

    private fun startObserving(device: BluetoothPeripheral) {
        val active = refineDriver(device)
        active.reset()
        readingSeen = false
        scope.launch {
            try {
                val weight = device.getCharacteristic(active.service, active.weightCharacteristic)
                val battery = active.batteryService?.let { service ->
                    active.batteryCharacteristic?.let { device.getCharacteristic(service, it) }
                }

                if (weight != null) {
                    device.observe(weight) { value ->
                        val reading = active.parseWeight(value) ?: run {
                            // On some scales the battery arrives in a frame of its own into the
                            // same characteristic as the weight.
                            val percent = active.parseBattery(value)
                            if (percent != null) {
                                _state.update { it.copy(batteryPercent = percent) }
                            }
                            logPacket(value, null, percent)
                            return@observe
                        }
                        logPacket(value, reading)
                        readingSeen = true
                        // The scale may show ounces: recipes and guidance are in grams, so we
                        // put it back if the model can do that. Packets come ten a second, while
                        // the unit does not switch instantly — otherwise the scale would manage
                        // a full round of "grams to ounces to grams".
                        if (keepGrams && reading.unitOnScale != null &&
                            reading.unitOnScale != WeightUnit.GRAM &&
                            SystemClock.elapsedRealtime() - lastUnitCommandAt > UNIT_COMMAND_PAUSE_MS
                        ) {
                            lastUnitCommandAt = SystemClock.elapsedRealtime()
                            active.unitCommand(WeightUnit.GRAM)?.let { command ->
                                scope.launch { sendCommand(device, command) }
                            }
                        }
                        _state.update { state ->
                            state.copy(
                                weightGrams = reading.grams,
                                flowRate = reading.flowRate,
                                batteryPercent = reading.batteryPercent ?: state.batteryPercent,
                            )
                        }
                    }
                }

                // Not everything has a separate battery service: on the rest it arrives in the
                // same packet as the weight.
                if (battery != null) {
                    device.observe(battery) { value ->
                        _state.update { it.copy(batteryPercent = value.asUInt8()?.toInt()) }
                    }
                }

                // The connection could have come up after the recording started: the services
                // and the other subscriptions then fall to the log here.
                diagnostics?.let { log ->
                    log.event("connected: ${device.name}, driver ${active.title}")
                    dumpServices(device, log)
                    observeEverything(device, log)
                }

                val startup = buildList {
                    // A scale whose unit only cycles round must not be touched blindly: grams
                    // would turn into ounces.
                    if (keepGrams && !active.unitCommandIsToggle) {
                        active.unitCommand(WeightUnit.GRAM)?.let(::add)
                    }
                    addAll(active.onConnectCommands())
                }
                // On some scales the introduction goes in steps, and it cannot be hurried: they
                // drop commands sent one after another.
                startup.forEachIndexed { index, command ->
                    if (index > 0) delay(COMMAND_GAP_MS)
                    sendCommand(device, command)
                }
                startHeartbeat(device, active)
            } catch (e: Exception) {
                Log.d(TAG, "Could not subscribe to the characteristics: $e")
            }
        }
    }

    /**
     * Refines the driver by the services of the device. The name shows the model but not the
     * generation: Acaia has one name and two services, and they can only be told apart after
     * connecting.
     */
    @SuppressLint("MissingPermission")
    private fun refineDriver(device: BluetoothPeripheral): ScaleDriver {
        val current = driver
        if (device.getCharacteristic(current.service, current.weightCharacteristic) != null) {
            return current
        }
        val better = ScaleDrivers.all.firstOrNull { candidate ->
            candidate !== current &&
                candidate.matches(device.name) &&
                device.getCharacteristic(candidate.service, candidate.weightCharacteristic) != null
        } ?: return current

        Log.d(TAG, "Driver refined by the services: ${current.title} → ${better.title}")
        diagnostics?.event("driver refined by the services: ${better.title}")
        driver = better
        return better
    }

    /**
     * Reminders to the scale that we are still here. Without them Acaia stops sending the
     * weight after a few seconds, and Decent falls asleep.
     */
    private fun startHeartbeat(device: BluetoothPeripheral, active: ScaleDriver) {
        heartbeatJob?.cancel()
        val interval = active.heartbeatIntervalMs
        if (interval <= 0L || active.heartbeatCommands().isEmpty()) return
        heartbeatJob = scope.launch {
            while (_state.value.isConnected) {
                delay(interval)
                if (!_state.value.isConnected) break
                // The scale may have missed the introduction — then there is no weight at all.
                // We repeat it until the first packet shows up.
                if (!readingSeen) {
                    active.onConnectCommands().forEach {
                        sendCommand(device, it, silent = true)
                        delay(COMMAND_GAP_MS)
                    }
                }
                active.heartbeatCommands().forEach { sendCommand(device, it, silent = true) }
            }
        }
    }

    /**
     * Protocol diagnostics: once a second it prints the raw packet and how it was parsed.
     * Needed when checking against a live scale, not built into a release.
     */
    private fun logPacket(value: ByteArray, reading: WeightReading?, battery: Int? = null) {
        // The scale sends the battery in a frame of its own into the same characteristic. It is
        // parsed differently from a weight, but it is parsed: "failed" here would be a lie.
        val parsed = when {
            reading != null -> "%.1f g, unit %s".format(reading.grams, reading.unitOnScale)
            battery != null -> "battery $battery %"
            else -> "could not parse"
        }
        diagnostics?.let { log ->
            log.packet(driver.weightCharacteristic.toString(), value, parsed)
            _diagnosticsPackets.value = log.packetCount
        }
        if (!BuildConfig.DEBUG) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastPacketLogAt < PACKET_LOG_INTERVAL_MS) return
        lastPacketLogAt = now
        val hex = value.joinToString(" ") { "%02x".format(it) }
        Log.d(TAG, "Weight packet [$hex] → $parsed")
    }

    private suspend fun sendCommand(
        device: BluetoothPeripheral,
        command: ByteArray,
        silent: Boolean = false,
    ) {
        val target = driver.commandCharacteristic ?: run {
            diagnostics?.event("the command was not sent: the driver has no command characteristic")
            return
        }
        val characteristic = device.getCharacteristic(driver.service, target) ?: run {
            diagnostics?.event("the command was not sent: the device has no $target characteristic")
            return
        }
        // Some scales take commands only without acknowledgement — on Timemore the command
        // characteristic cannot do it any other way.
        val write = when {
            driver.writeWithoutResponse &&
                characteristic.supports(PROPERTY_WRITE_NO_RESPONSE) -> WriteType.WITHOUT_RESPONSE

            characteristic.supports(BluetoothGattCharacteristic.PROPERTY_WRITE) ->
                WriteType.WITH_RESPONSE

            else -> WriteType.WITHOUT_RESPONSE
        }
        // We write to the log before sending rather than after: a command stuck in the queue
        // would otherwise leave no trace at all.
        if (!silent) diagnostics?.command(target.toString(), command, write.toString())
        try {
            withTimeout(COMMAND_TIMEOUT_MS) {
                repeat(driver.commandRepeats.coerceAtLeast(1)) { attempt ->
                    if (attempt > 0) delay(COMMAND_GAP_MS)
                    device.writeCharacteristic(characteristic, command, write)
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.d(TAG, "The command got stuck in the queue, bringing the connection up again")
            diagnostics?.event("the command got stuck in the queue, bringing the connection up again")
            recoverStuckLink(device)
        } catch (e: Exception) {
            Log.d(TAG, "The command was not delivered: $e")
            diagnostics?.event("the command did not go out: $e")
        }
    }

    /**
     * The command queue has stalled.
     *
     * The connection library runs commands one at a time and takes the next one only after the
     * system answers for the previous. The answer sometimes never comes — the queue stalls
     * forever, and from that moment not a single command goes out, the tare included. The
     * library only clears the queue together with the connection, so the connection is what we
     * break: [scheduleReconnect] brings it back.
     */
    @SuppressLint("MissingPermission")
    private suspend fun recoverStuckLink(device: BluetoothPeripheral) {
        runCatching { central.cancelConnection(device) }
    }

    private fun BluetoothGattCharacteristic.notifies(): Boolean =
        properties and NOTIFYING_PROPERTIES != 0

    private fun BluetoothGattCharacteristic.supports(property: Int): Boolean =
        properties and property != 0

    /**
     * The name from the advertisement. A device can have none until it has been connected to,
     * while the advertisement itself has one.
     */
    @SuppressLint("MissingPermission")
    private fun BluetoothPeripheral.advertisedName(result: ScanResult): String =
        name.ifBlank { result.scanRecord?.deviceName.orEmpty() }

    private fun BluetoothGattCharacteristic.propertyNames(): String = buildList {
        if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("read")
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("write")
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            add("write-nr")
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("notify")
        if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("indicate")
    }.joinToString(", ").ifEmpty { "—" }

    companion object {
        private const val TAG = "ScaleRepository"
        private const val RECONNECT_DELAY_MS = 10_000L
        private const val PACKET_LOG_INTERVAL_MS = 1_000L

        /** The pause between commands: sent back to back, the scale loses them. */
        private const val COMMAND_GAP_MS = 200L

        /**
         * How long we wait for a send. An ordinary command goes out in milliseconds, so this
         * span means a stalled queue rather than a slow connection.
         */
        private const val COMMAND_TIMEOUT_MS = 5_000L

        /** How many times to repeat the tare if the scale did not hear it. */
        private const val TARE_ATTEMPTS = 3

        /** How long to wait before looking at whether the tare worked. */
        private const val TARE_CHECK_MS = 600L

        /** The zero on a scale is not perfect, and the readings shiver too. */
        private const val TARE_TOLERANCE_GRAMS = 0.5f

        /** How often the scale may be asked to come back to grams. */
        private const val UNIT_COMMAND_PAUSE_MS = 3_000L

        private const val PROPERTY_WRITE_NO_RESPONSE =
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE

        private const val NOTIFYING_PROPERTIES =
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                BluetoothGattCharacteristic.PROPERTY_INDICATE

        /** The permissions without which a BLE search is impossible. */
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
