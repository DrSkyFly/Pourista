package com.pourista.scale

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
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

    @Volatile
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
    @Volatile
    private var userDisconnected = false

    /** Whether to put the scale back into grams if it was switched by the button on its body. */
    private var keepGrams = true

    private var connectionObserverStarted = false

    private var scanTimeout: Job? = null
    private var scanId = 0L
    private var connectionTimeout: Job? = null
    private var observationJob: Job? = null
    private val recentEvents = ArrayDeque<String>()

    @Synchronized
    private fun event(message: String) {
        Log.d(DIFLUID_TAG, message)
        if (recentEvents.size == 100) recentEvents.removeFirst()
        recentEvents.addLast("${SystemClock.elapsedRealtime()} ms  $message")
        diagnostics?.event(message)
    }

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
    @Synchronized
    private fun onBluetoothOff() {
        event("Bluetooth turned off")
        scanId++
        scanTimeout?.cancel()
        connectionTimeout?.cancel()
        observationJob?.cancel()
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        tareJob?.cancel()
        peripheral = null
        _state.update { ScaleState(status = ConnectionStatus.BLUETOOTH_OFF) }
    }

    /**
     * Debug builds only: pretends a scale is connected and pushes readings from outside.
     *
     * The store screenshots are shot with it. An emulator has no Bluetooth and no scale to pair
     * with, while half the screens of the app are about the weight — and they have to be shot in
     * every language of the listing.
     */
    fun simulate(connected: Boolean, grams: Float = 0f, title: String = "LFSmart Scale") {
        if (!BuildConfig.DEBUG) return
        _state.update {
            if (!connected) {
                ScaleState()
            } else {
                it.copy(
                    status = ConnectionStatus.CONNECTED,
                    deviceName = title,
                    weightGrams = grams,
                    batteryPercent = 96,
                )
            }
        }
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
    @Synchronized
    fun startScan() {
        if (!hasPermissions()) {
            event("Bluetooth permissions denied")
            return
        }
        // Without this check the status would go to "searching" while no search started at
        // all, and the next call would run into the check a line below.
        if (!bluetoothEnabled()) {
            event("Bluetooth is off")
            _state.update { it.copy(status = ConnectionStatus.BLUETOOTH_OFF) }
            return
        }
        if (_state.value.isConnected || _state.value.isBusy) return
        val id = ++scanId
        val seen = mutableSetOf<String>()
        event("BLE scan started")
        scanTimeout = scope.launch {
            delay(SCAN_TIMEOUT_MS)
            synchronized(this@ScaleRepository) {
                if (id == scanId) {
                    event("BLE scan timed out")
                    stopScan()
                }
            }
        }
        userDisconnected = false
        observeConnectionStateOnce()
        _state.update { it.copy(status = ConnectionStatus.SCANNING) }
        runCatching {
            // We look over the whole air and match the names ourselves: the library compares
            // case-sensitively, and a scale advertised as "TIMEMORE_Dot" did not fall under a
            // lowercase "timemore".
            central.scanForPeripherals(
                { found, scanResult ->
                    synchronized(this@ScaleRepository) {
                        if (id != scanId) return@scanForPeripherals
                        val record = scanResult.scanRecord
                        val name = record?.deviceName?.takeIf { it.isNotBlank() } ?: found.name
                        val services = record?.serviceUuids.orEmpty().map { it.uuid }
                        val matched = ScaleDrivers.forAdvertisement(name, found.name, services)
                        val manufacturers = record?.manufacturerSpecificData
                        val ids = manufacturers?.let { data -> (0 until data.size()).map(data::keyAt) }.orEmpty()
                        val detail = "name=$name, services=$services, manufacturer IDs=$ids → ${matched?.title ?: "unknown"}"
                        if (seen.add(detail)) event(detail)
                        if (matched == null) return@scanForPeripherals
                        stopScan()
                        peripheral = found
                        driver = matched
                        _state.value = ScaleState(
                            status = ConnectionStatus.CONNECTING,
                            deviceName = name.ifBlank { matched.title },
                        )
                        connect(found)
                    }
                },
                { failure ->
                    synchronized(this@ScaleRepository) {
                        if (id == scanId) {
                            event("Scan failed: $failure")
                            stopScan()
                        }
                    }
                },
            )
        }.onFailure {
            event("Cannot start scan: $it")
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun stopScan() {
        scanId++
        scanTimeout?.cancel()
        runCatching { central.stopScan() }
        if (_state.value.status == ConnectionStatus.SCANNING) {
            _state.update { it.copy(status = ConnectionStatus.IDLE) }
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun disconnect() {
        event("Closing GATT connection")
        stopScan()
        connectionTimeout?.cancel()
        observationJob?.cancel()
        userDisconnected = true
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        tareJob?.cancel()
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
                delay(if (driver is DifluidDriver) 2_000L else TARE_CHECK_MS)
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
    @Synchronized
    fun startDiagnostics(header: List<String>) {
        val log = ScaleDiagnostics(header)
        recentEvents.forEach(log::note)
        diagnostics = log
        _diagnosticsPackets.value = 0

        val device = peripheral
        if (_state.value.isConnected && device != null) {
            log.note("Driver: ${driver.title}")
            dumpServices(device, log)
            if (BuildConfig.DEBUG) observeEverything(device, log)
        } else {
            log.note("The scale is not connected — looking at what is on the air")
            log.note("")
            startScan()
        }
    }

    /** Stops the recording and hands over the log. */
    @SuppressLint("MissingPermission")
    fun stopDiagnostics(): String? {
        val log = diagnostics ?: return null
        diagnostics = null
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

    private fun connect(target: BluetoothPeripheral) {
        connectionTimeout?.cancel()
        connectionTimeout = scope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            synchronized(this@ScaleRepository) {
                if (peripheral === target && !_state.value.isConnected) {
                    event("Timed out waiting for GATT or first DiFluid reading")
                    disconnect()
                }
            }
        }
        scope.launch {
            runCatching { central.connectPeripheral(target) }.onFailure {
                synchronized(this@ScaleRepository) {
                    event("GATT connection failed: $it")
                    if (peripheral === target) {
                        disconnect()
                    }
                }
            }
        }
    }

    private fun observeConnectionStateOnce() {
        if (connectionObserverStarted) return
        connectionObserverStarted = true
        central.observeConnectionState { device, state -> handleConnectionState(device, state) }
    }

    @Synchronized
    private fun handleConnectionState(device: BluetoothPeripheral, state: ConnectionState) {
        if (device !== peripheral || userDisconnected) return
        event("GATT ${device.name}: $state")
        when (state) {
            ConnectionState.CONNECTING ->
                _state.update { it.copy(status = ConnectionStatus.CONNECTING) }

            ConnectionState.CONNECTED -> {
                if (driver !is DifluidDriver) {
                    connectionTimeout?.cancel()
                    _state.update { it.copy(status = ConnectionStatus.CONNECTED, deviceName = device.name) }
                }
                startObserving(device)
            }

            ConnectionState.DISCONNECTING -> Unit

            ConnectionState.DISCONNECTED -> {
                connectionTimeout?.cancel()
                observationJob?.cancel()
                tareJob?.cancel()
                heartbeatJob?.cancel()
                if (!_state.value.isConnected) {
                    event("GATT disconnected before initialization completed")
                    peripheral = null
                    _state.value = ScaleState()
                    return
                }
                _state.update {
                    it.copy(status = ConnectionStatus.RECONNECTING, flowRate = null, batteryPercent = null)
                }
                scheduleReconnect(device)
            }
        }
    }

    private fun scheduleReconnect(device: BluetoothPeripheral) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            synchronized(this@ScaleRepository) {
                if (userDisconnected || peripheral !== device) return@launch
                val known = runCatching { central.getPeripheral(device.address) }.getOrNull()
                if (known != null && known.getState() == ConnectionState.DISCONNECTED) {
                    peripheral = known
                    connect(known)
                } else {
                    event("Reconnect unavailable")
                    disconnect()
                }
            }
        }
    }

    private fun startObserving(device: BluetoothPeripheral) {
        val active = refineDriver(device)
        active.reset()
        readingSeen = false
        observationJob?.cancel()
        observationJob = scope.launch {
            try {
                device.services.forEach { service ->
                    event("GATT service ${service.uuid}: " + service.characteristics.joinToString {
                        "${it.uuid} [${it.propertyNames()}]"
                    })
                }
                val weight = device.getCharacteristic(active.service, active.weightCharacteristic)
                val battery = active.batteryService?.let { service ->
                    active.batteryCharacteristic?.let { device.getCharacteristic(service, it) }
                }

                if (active is DifluidDriver) {
                    check(weight != null && weight.notifies() &&
                        weight.supports(BluetoothGattCharacteristic.PROPERTY_WRITE)) {
                        "Missing DiFluid notify/write characteristic ${active.service}/${active.weightCharacteristic}"
                    }
                }
                if (weight != null) {
                    val subscribed = device.observe(weight) { packet ->
                        receivePacket(device, active, packet)
                    }
                    event("Subscribe ${weight.uuid}: $subscribed")
                    if (active is DifluidDriver) check(subscribed) { "DiFluid notification subscription rejected" }
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
                    if (BuildConfig.DEBUG) observeEverything(device, log)
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
                if (active is DifluidDriver) delay(COMMAND_GAP_MS)
                startup.forEachIndexed { index, command ->
                    if (index > 0) delay(COMMAND_GAP_MS)
                    sendCommand(device, command)
                }
                startHeartbeat(device, active)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                event("GATT initialization failed: $e")
                synchronized(this@ScaleRepository) {
                    if (active is DifluidDriver && peripheral === device) disconnect()
                }
            }
        }
    }

    @Synchronized
    private fun receivePacket(device: BluetoothPeripheral, active: ScaleDriver, packet: ByteArray) {
        if (device !== peripheral || userDisconnected || _state.value.status == ConnectionStatus.RECONNECTING) return
        val frames = if (active is DifluidDriver) active.frames(packet) else listOf(packet)
        for (frame in frames) {
            val reading = active.parseWeight(frame)
            if (reading == null) {
                val battery = active.parseBattery(frame)
                if (battery != null) _state.update { it.copy(batteryPercent = battery) }
                logPacket(frame, null, battery)
                continue
            }
            logPacket(frame, reading)
            if (active is DifluidDriver && !readingSeen) {
                connectionTimeout?.cancel()
                event("First valid weight from ${active.title}")
            }
            readingSeen = true
            if (keepGrams && reading.unitOnScale != null && reading.unitOnScale != WeightUnit.GRAM &&
                SystemClock.elapsedRealtime() - lastUnitCommandAt > UNIT_COMMAND_PAUSE_MS
            ) {
                lastUnitCommandAt = SystemClock.elapsedRealtime()
                active.unitCommand(WeightUnit.GRAM)?.let { command ->
                    scope.launch { sendCommand(device, command) }
                }
            }
            _state.update {
                it.copy(
                    status = ConnectionStatus.CONNECTED,
                    weightGrams = reading.grams,
                    flowRate = reading.flowRate,
                    batteryPercent = reading.batteryPercent ?: it.batteryPercent,
                )
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
                (candidate.matches(device.name) || current is DifluidDriver && candidate is DifluidDriver) &&
                device.getCharacteristic(candidate.service, candidate.weightCharacteristic) != null
        } ?: return current

        event("Driver refined: ${current.title} → ${better.title}")
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
        if (!BuildConfig.DEBUG) return
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
        val now = SystemClock.elapsedRealtime()
        if (driver !is DifluidDriver && now - lastPacketLogAt < PACKET_LOG_INTERVAL_MS) return
        lastPacketLogAt = now
        val hex = value.joinToString(" ") { "%02x".format(it) }
        Log.d(if (driver is DifluidDriver) DIFLUID_TAG else TAG, "Weight packet [$hex] → $parsed")
    }

    private suspend fun sendCommand(
        device: BluetoothPeripheral,
        command: ByteArray,
        silent: Boolean = false,
    ) {
        if (device !== peripheral || userDisconnected) return
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
        if (BuildConfig.DEBUG && driver is DifluidDriver) {
            event("TX $target ($write) " + command.joinToString(" ") { "%02X".format(it) })
        }
        if (!silent && BuildConfig.DEBUG && driver !is DifluidDriver) diagnostics?.command(target.toString(), command, write.toString())
        try {
            withTimeout(COMMAND_TIMEOUT_MS) {
                repeat(driver.commandRepeats.coerceAtLeast(1)) { attempt ->
                    if (attempt > 0) delay(COMMAND_GAP_MS)
                    val result = device.writeCharacteristic(characteristic, command, write)
                    if (driver is DifluidDriver) check(result.isNotEmpty()) { "GATT write rejected" }
                }
            }
        } catch (e: TimeoutCancellationException) {
            event("GATT write timed out")

            recoverStuckLink(device)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            event("Command delivery failed: $e")
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
        if (peripheral === device) runCatching { central.cancelConnection(device) }
    }

    private fun BluetoothGattCharacteristic.notifies(): Boolean =
        properties and NOTIFYING_PROPERTIES != 0

    private fun BluetoothGattCharacteristic.supports(property: Int): Boolean =
        properties and property != 0

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
        private const val SCAN_TIMEOUT_MS = 20_000L
        private const val CONNECTION_TIMEOUT_MS = 35_000L
        private const val DIFLUID_TAG = "PouristaDiFluid"
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
