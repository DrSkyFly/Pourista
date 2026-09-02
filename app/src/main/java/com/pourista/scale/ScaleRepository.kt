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

    /** Bluetooth выключен на телефоне: искать нечем, и это не наша неполадка. */
    BLUETOOTH_OFF,
    SCANNING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
}

data class ScaleState(
    val status: ConnectionStatus = ConnectionStatus.IDLE,
    val deviceName: String? = null,
    /** Текущий вес на весах в граммах. */
    val weightGrams: Float = 0f,
    /**
     * Скорость влива в граммах в секунду, если весы считают её сами. Null —
     * весы её не шлют, и движок считает скорость по приросту веса.
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
 * Связь с весами: поиск, подключение, поток веса и команды. Живёт на всё время
 * работы приложения, поэтому экран заваривания можно закрывать и открывать,
 * не теряя соединение.
 */
class ScaleRepository(context: Context) {

    private val appContext = context.applicationContext
    private val central = BluetoothCentralManager(appContext)
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /**
     * Последний рубеж. Библиотека связи бросает исключения из корутин —
     * например когда просишь разорвать связь с устройством, о котором она уже
     * забыла. Без обработчика такое исключение уходит в обработчик потока,
     * то есть в вылет приложения.
     */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, error ->
                Log.e(TAG, "Сбой в работе с весами", error)
            },
    )

    private val _state = MutableStateFlow(ScaleState())
    val state: StateFlow<ScaleState> = _state.asStateFlow()

    private var peripheral: BluetoothPeripheral? = null
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var tareJob: Job? = null

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

    /** Эфир осматривает сама диагностика — обычный поиск при этом не идёт. */
    private var diagnosticScan = false

    private var lastPacketLogAt = 0L

    /** Когда последний раз просили весы вернуться в граммы. */
    private var lastUnitCommandAt = 0L

    /** Пришёл ли хоть один разобранный вес после подключения. */
    @Volatile
    private var readingSeen = false

    /** Идёт запись протокола: журнал ведём, пока он здесь. */
    @Volatile
    private var diagnostics: ScaleDiagnostics? = null

    private val _diagnosticsPackets = MutableStateFlow(0)

    /** Сколько пакетов уже записано: экран диагностики показывает счётчик. */
    val diagnosticsPackets: StateFlow<Int> = _diagnosticsPackets.asStateFlow()

    init {
        /*
          При выключенном Bluetooth библиотека связи молча ничего не делает:
          ни исключения, ни ошибки в обратном вызове. А включение она не
          отслеживает — в её обработчике STATE_ON только запись в журнал.
          Поэтому за адаптером следим сами.
        */
        central.observeAdapterState { adapterState ->
            when (adapterState) {
                BluetoothAdapter.STATE_ON -> onBluetoothOn()
                BluetoothAdapter.STATE_OFF -> onBluetoothOff()
            }
        }
    }

    /**
     * Bluetooth включили. Поиск возобновляем только если сами же его и не
     * начали из-за выключенного адаптера: в остальных случаях весы человеку
     * сейчас не нужны, и лезть в эфир незачем.
     */
    private fun onBluetoothOn() {
        if (_state.value.status != ConnectionStatus.BLUETOOTH_OFF) return
        Log.d(TAG, "Bluetooth включили, возобновляем поиск")
        startScan()
    }

    /**
     * Bluetooth выключили. Библиотека при этом забывает про устройство, и
     * просить её разорвать связь уже нельзя — она бросает исключение. Поэтому
     * ссылку на весы отпускаем здесь же.
     */
    private fun onBluetoothOff() {
        Log.d(TAG, "Bluetooth выключили")
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
        // Без этой проверки статус встал бы в «ищем», поиск при этом не пошёл
        // бы вовсе, и следующий вызов упёрся бы в проверку строчкой ниже.
        if (!bluetoothEnabled()) {
            Log.d(TAG, "Bluetooth выключен, поиск не запускаем")
            _state.update { it.copy(status = ConnectionStatus.BLUETOOTH_OFF) }
            return
        }
        if (_state.value.isConnected || _state.value.status == ConnectionStatus.SCANNING) return
        userDisconnected = false
        observeConnectionStateOnce()
        _state.update { it.copy(status = ConnectionStatus.SCANNING) }
        runCatching {
            // Смотрим весь эфир и сверяем имена сами: у библиотеки сравнение
            // с учётом регистра, и весы, объявленные как «TIMEMORE_Dot», под
            // строчный «timemore» не попадали.
            central.scanForPeripherals(
                { found, scanResult ->
                    val name = found.advertisedName(scanResult)
                    val matched = ScaleDrivers.forName(name) ?: return@scanForPeripherals
                    val mark = if (matched.experimental) ", поддержка тестовая" else ""
                    Log.d(TAG, "Найдены весы $name (${matched.title}$mark), RSSI ${scanResult.rssi}")
                    central.stopScan()
                    peripheral = found
                    driver = matched
                    _state.update {
                        it.copy(status = ConnectionStatus.CONNECTING, deviceName = name)
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
        heartbeatJob?.cancel()
        tareJob?.cancel()
        runCatching { central.stopScan() }
        // Устройство библиотека могла уже забыть — так бывает после выключения
        // адаптера. Тогда на просьбу разорвать связь она бросает исключение.
        peripheral?.let { device ->
            scope.launch { runCatching { central.cancelConnection(device) } }
        }
        peripheral = null
        _state.update { ScaleState() }
    }

    /**
     * Обнуление весов.
     *
     * Команда уходит без подтверждения: услышали её весы или нет, ответа не
     * будет. Поэтому смотрим на показания. Вес встал на ноль — тара прошла.
     * Вес остался прежним — команду не услышали, шлём ещё раз. Вес уехал —
     * на весы что-то положили или сняли, и повторять уже нельзя: обнулим не
     * то, что человек хотел.
     */
    fun tare() {
        diagnostics?.event("нажата «Тара»")
        val current = peripheral ?: run {
            diagnostics?.event("тара не ушла: весы не на связи")
            return
        }
        val command = driver.tareCommand() ?: run {
            diagnostics?.event("тара не ушла: ${driver.title} обнуления не умеет")
            return
        }
        tareJob?.cancel()
        tareJob = scope.launch {
            val before = _state.value.weightGrams
            repeat(TARE_ATTEMPTS) { attempt ->
                if (!_state.value.isConnected) return@launch
                if (attempt > 0) diagnostics?.event("тара не сработала, шлём ещё раз")
                sendCommand(current, command)
                delay(TARE_CHECK_MS)
                val now = _state.value.weightGrams
                if (abs(now) <= TARE_TOLERANCE_GRAMS) return@launch
                if (abs(now - before) > TARE_TOLERANCE_GRAMS) return@launch
            }
            diagnostics?.event("тара так и не сработала")
        }
    }

    /**
     * Начинает запись протокола весов.
     *
     * Если весы на связи — выкладывает их службы и подписывается на всё, что
     * умеет уведомлять: неработающая модель чаще всего шлёт данные не туда, где
     * их ждёт драйвер. Если связи нет — осматривает эфир: по имени в объявлении
     * видно, узнаёт ли приложение эти весы вообще.
     */
    @SuppressLint("MissingPermission")
    fun startDiagnostics(header: List<String>) {
        val log = ScaleDiagnostics(header)
        diagnostics = log
        _diagnosticsPackets.value = 0

        val device = peripheral
        if (_state.value.isConnected && device != null) {
            log.note("Драйвер: ${driver.title}")
            dumpServices(device, log)
            observeEverything(device, log)
        } else {
            log.note("Весы не подключены — смотрим, что в эфире")
            log.note("")
            scanForDiagnostics(log)
        }
    }

    /** Останавливает запись и отдаёт журнал. */
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
        log.note("Службы устройства:")
        device.services.forEach { service ->
            log.note("  ${service.uuid}")
            service.characteristics.forEach { characteristic ->
                log.note("    ${characteristic.uuid}  ${characteristic.propertyNames()}")
            }
        }
        log.note("")
        log.note("Драйвер ждёт вес в ${driver.weightCharacteristic}")
        log.note("")
        log.note("время   характеристика  байты → разбор")
    }

    /**
     * Подписка на все уведомляющие характеристики. Отписаться blessed не даёт,
     * поэтому лишние подписки живут до разрыва связи — записывать они перестают
     * вместе с концом журнала.
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
                            current.packet(characteristic.uuid.toString(), value, "не вес")
                            _diagnosticsPackets.value = current.packetCount
                        }
                    }.onFailure {
                        log.event("не удалось подписаться на ${characteristic.uuid}: $it")
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanForDiagnostics(log: ScaleDiagnostics) {
        if (!hasPermissions()) {
            log.note("Нет разрешения на Bluetooth — эфир посмотреть не получилось")
            return
        }
        diagnosticScan = true
        val seen = mutableSetOf<String>()
        runCatching {
            central.scanForPeripherals(
                { found, result ->
                    // Адрес нужен только чтобы не повторять устройство в журнале.
                    if (!seen.add(found.address)) return@scanForPeripherals
                    val advertised = found.advertisedName(result)
                    val name = advertised.ifBlank { "(без имени)" }
                    val services = result.scanRecord?.serviceUuids
                        ?.joinToString(", ") { it.uuid.toString() }
                        ?: "не объявлены"
                    val known = ScaleDrivers.forName(advertised)?.title ?: "приложению не знакомы"
                    log.event("в эфире: \"$name\", RSSI ${result.rssi}, службы: $services → $known")
                },
                { failure -> log.event("поиск не удался: $failure") },
            )
        }.onFailure { log.event("поиск не запустился: $it") }
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
                    heartbeatJob?.cancel()
                    _state.update {
                        it.copy(
                            status = if (userDisconnected) {
                                ConnectionStatus.IDLE
                            } else {
                                ConnectionStatus.RECONNECTING
                            },
                            // Последний вес на экране оставляем, а скорость
                            // без новых пакетов означала бы влив, которого нет.
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
            // Устройство, о котором библиотека не знает, она отдавать
            // отказывается — с исключением, а не с null.
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
                            // Заряд у части весов приходит отдельным кадром
                            // в ту же характеристику, что и вес.
                            val percent = active.parseBattery(value)
                            if (percent != null) {
                                _state.update { it.copy(batteryPercent = percent) }
                            }
                            logPacket(value, null, percent)
                            return@observe
                        }
                        logPacket(value, reading)
                        readingSeen = true
                        // Весы могут показывать унции: рецепты и подсказки в граммах,
                        // поэтому возвращаем их обратно, если модель это умеет.
                        // Пакеты идут по десять в секунду, а переключается
                        // единица не мгновенно — иначе весы успели бы сделать
                        // круг «граммы → унции → граммы».
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

                // Отдельная служба заряда есть не у всех: у остальных он приходит
                // в том же пакете, что и вес.
                if (battery != null) {
                    device.observe(battery) { value ->
                        _state.update { it.copy(batteryPercent = value.asUInt8()?.toInt()) }
                    }
                }

                // Связь могли поднять уже после начала записи: тогда службы
                // и остальные подписки достаются журналу здесь.
                diagnostics?.let { log ->
                    log.event("подключились: ${device.name}, драйвер ${active.title}")
                    dumpServices(device, log)
                    observeEverything(device, log)
                }

                val startup = buildList {
                    // Весы, у которых единица только переключается по кругу,
                    // вслепую трогать нельзя: граммы превратились бы в унции.
                    if (keepGrams && !active.unitCommandIsToggle) {
                        active.unitCommand(WeightUnit.GRAM)?.let(::add)
                    }
                    addAll(active.onConnectCommands())
                }
                // Знакомство у части весов идёт по шагам, и торопить его нельзя:
                // подряд отправленные команды они пропускают.
                startup.forEachIndexed { index, command ->
                    if (index > 0) delay(COMMAND_GAP_MS)
                    sendCommand(device, command)
                }
                startHeartbeat(device, active)
            } catch (e: Exception) {
                Log.d(TAG, "Не удалось подписаться на характеристики: $e")
            }
        }
    }

    /**
     * Уточняет драйвер по службам устройства. По имени видно модель, но не
     * поколение: у Acaia имя одно, а служб две, и различить их можно только
     * после подключения.
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

        Log.d(TAG, "Драйвер уточнён по службам: ${current.title} → ${better.title}")
        diagnostics?.event("драйвер уточнён по службам: ${better.title}")
        driver = better
        return better
    }

    /**
     * Напоминания весам, что мы на связи. Acaia без них перестаёт слать вес
     * через несколько секунд, Decent — засыпает.
     */
    private fun startHeartbeat(device: BluetoothPeripheral, active: ScaleDriver) {
        heartbeatJob?.cancel()
        val interval = active.heartbeatIntervalMs
        if (interval <= 0L || active.heartbeatCommands().isEmpty()) return
        heartbeatJob = scope.launch {
            while (_state.value.isConnected) {
                delay(interval)
                if (!_state.value.isConnected) break
                // Знакомство весы могли пропустить — тогда веса нет вовсе.
                // Повторяем его, пока не увидим первый пакет.
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
     * Диагностика протокола: раз в секунду печатает сырой пакет и то, как он
     * разобран. Нужна при проверке на живых весах, в релизе не собирается.
     */
    private fun logPacket(value: ByteArray, reading: WeightReading?, battery: Int? = null) {
        // Заряд весы шлют своим кадром в ту же характеристику. Разбирается он
        // не как вес, но разбирается: «не удалось» тут было бы неправдой.
        val parsed = when {
            reading != null -> "%.1f г, единица %s".format(reading.grams, reading.unitOnScale)
            battery != null -> "заряд $battery %"
            else -> "разобрать не удалось"
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
        Log.d(TAG, "Пакет веса [$hex] → $parsed")
    }

    private suspend fun sendCommand(
        device: BluetoothPeripheral,
        command: ByteArray,
        silent: Boolean = false,
    ) {
        val target = driver.commandCharacteristic ?: run {
            diagnostics?.event("команда не отправлена: у драйвера нет характеристики команд")
            return
        }
        val characteristic = device.getCharacteristic(driver.service, target) ?: run {
            diagnostics?.event("команда не отправлена: характеристики $target нет у устройства")
            return
        }
        // Часть весов принимает команды только без подтверждения — у
        // Timemore характеристика команд иначе и не умеет.
        val write = when {
            driver.writeWithoutResponse &&
                characteristic.supports(PROPERTY_WRITE_NO_RESPONSE) -> WriteType.WITHOUT_RESPONSE

            characteristic.supports(BluetoothGattCharacteristic.PROPERTY_WRITE) ->
                WriteType.WITH_RESPONSE

            else -> WriteType.WITHOUT_RESPONSE
        }
        // В журнал пишем до отправки, а не после: команда, которая застряла в
        // очереди, иначе не оставила бы следа вовсе.
        if (!silent) diagnostics?.command(target.toString(), command, write.toString())
        try {
            withTimeout(COMMAND_TIMEOUT_MS) {
                repeat(driver.commandRepeats.coerceAtLeast(1)) { attempt ->
                    if (attempt > 0) delay(COMMAND_GAP_MS)
                    device.writeCharacteristic(characteristic, command, write)
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.d(TAG, "Команда встала в очереди, поднимаем связь заново")
            diagnostics?.event("команда встала в очереди, связь поднимаем заново")
            recoverStuckLink(device)
        } catch (e: Exception) {
            Log.d(TAG, "Команда не доставлена: $e")
            diagnostics?.event("команда не ушла: $e")
        }
    }

    /**
     * Очередь команд встала.
     *
     * Библиотека связи ведёт команды по одной и следующую берёт только после
     * ответа системы на предыдущую. Ответ иногда не приходит — очередь встаёт
     * навсегда, и с этого мига не уходит ни одна команда, включая тару.
     * Разбирает очередь библиотека только вместе со связью, поэтому связь и
     * рвём: обратно её поднимет [scheduleReconnect].
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
     * Имя из объявления. У устройства оно бывает пустым, пока с ним не
     * связывались, а в самом объявлении при этом есть.
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

        /** Пауза между командами: подряд весы их теряют. */
        private const val COMMAND_GAP_MS = 200L

        /**
         * Сколько ждём отправки. Обычная команда уходит за миллисекунды, так
         * что этот срок означает не медленную связь, а вставшую очередь.
         */
        private const val COMMAND_TIMEOUT_MS = 5_000L

        /** Сколько раз повторить тару, если весы её не услышали. */
        private const val TARE_ATTEMPTS = 3

        /** Сколько ждать, прежде чем смотреть, сработала ли тара. */
        private const val TARE_CHECK_MS = 600L

        /** Ноль на весах не идеальный, да и показания дрожат. */
        private const val TARE_TOLERANCE_GRAMS = 0.5f

        /** Как часто можно просить весы вернуться в граммы. */
        private const val UNIT_COMMAND_PAUSE_MS = 3_000L

        private const val PROPERTY_WRITE_NO_RESPONSE =
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE

        private const val NOTIFYING_PROPERTIES =
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                BluetoothGattCharacteristic.PROPERTY_INDICATE

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
