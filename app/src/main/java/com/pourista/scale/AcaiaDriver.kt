package com.pourista.scale

import java.util.UUID

/**
 * Acaia: Pearl, Lunar, Pyxis, Cinco.
 *
 * Кадр: два байта заголовка EF DD, тип сообщения, длина, данные и две
 * контрольные суммы — отдельно по чётным и нечётным байтам данных.
 *
 * Весы молчат, пока с ними не поздороваются: после подписки нужно отправить
 * представление и список событий, на которые мы подписываемся, а дальше раз в
 * секунду слать пульс. Без пульса поток веса обрывается через несколько
 * секунд.
 *
 * Железо двух поколений отличается только идентификаторами службы, поэтому
 * разбор общий, а объектов два: [AcaiaClassicDriver] и [AcaiaPyxisDriver].
 * Какой из них подойдёт, видно после подключения — по службам устройства.
 *
 * Протокол написан по открытым реализациям (pyacaia, Beanconqueror), на
 * железе не проверялся.
 */
sealed class AcaiaDriver(private val pyxisStyle: Boolean) : ScaleDriver {

    override val nameFragments = listOf("acaia", "lunar", "pyxis", "proch", "pearl", "cinco")

    override val writeWithoutResponse = true

    /**
     * Имя у Acaia начинается с модели и продолжается серийником без пробела:
     * «LUNAR-1A2B3C», «PROCHBT001». Пробел сразу после модели выдаёт чужое
     * устройство вроде «Pearl Bluetooth Speaker».
     */
    override fun matches(deviceName: String): Boolean {
        val name = deviceName.trim().lowercase()
        val model = nameFragments.firstOrNull { name.startsWith(it) } ?: return false
        return name.getOrNull(model.length)?.isWhitespace() != true
    }

    /**
     * Куски кадров между пакетами: одно уведомление может принести половину
     * сообщения или сразу несколько.
     */
    private var buffer = ByteArray(0)

    private var pendingBattery: Int? = null
    private var ounces = false

    override fun reset() {
        buffer = ByteArray(0)
        pendingBattery = null
        ounces = false
    }

    override fun onConnectCommands(): List<ByteArray> = listOf(identCommand(), eventsCommand())

    override fun heartbeatCommands(): List<ByteArray> =
        if (pyxisStyle) listOf(identCommand(), HEARTBEAT) else listOf(HEARTBEAT)

    override val heartbeatIntervalMs = 1_000L

    override fun tareCommand(): ByteArray = encode(MSG_TARE, byteArrayOf(0))

    /**
     * Вес из потока кадров. Заряд и единица приходят отдельным сообщением
     * настроек, поэтому копятся в драйвере и отдаются вместе с ближайшим весом.
     */
    override fun parseWeight(value: ByteArray): WeightReading? {
        var grams: Float? = null
        decode(value) { message ->
            when (message) {
                is AcaiaMessage.Weight -> grams = message.grams
                is AcaiaMessage.Settings -> {
                    pendingBattery = message.batteryPercent
                    ounces = message.ounces
                }
            }
        }
        val weight = grams ?: return null
        val battery = pendingBattery
        pendingBattery = null
        return WeightReading(
            grams = if (ounces) weight * GRAMS_PER_OUNCE else weight,
            unitOnScale = if (ounces) WeightUnit.OUNCE else WeightUnit.GRAM,
            batteryPercent = battery,
        )
    }

    /**
     * Заряд из пакета, в котором веса не было. Пакет к этому времени уже
     * разобран в [parseWeight] — отдаём, что оттуда осталось.
     */
    override fun parseBattery(value: ByteArray): Int? {
        val battery = pendingBattery
        pendingBattery = null
        return battery
    }

    /** Представление весам: пятнадцать байт, свои у каждого поколения. */
    private fun identCommand(): ByteArray =
        encode(MSG_IDENT, if (pyxisStyle) PYXIS_ID else CLASSIC_ID)

    /**
     * На какие события подписываемся: вес, заряд, таймер и кнопки. Пары
     * «событие, аргумент», перед ними длина.
     */
    private fun eventsCommand(): ByteArray {
        val payload = byteArrayOf(0, 1, 1, 2, 2, 5, 3, 4)
        return encode(MSG_EVENTS, byteArrayOf((payload.size + 1).toByte()) + payload)
    }

    /**
     * Собирает пакеты в поток и вынимает из него целые кадры. Мусор до
     * заголовка отбрасывается, хвост остаётся ждать продолжения.
     */
    private inline fun decode(packet: ByteArray, block: (AcaiaMessage) -> Unit) {
        buffer = if (buffer.isEmpty()) packet else buffer + packet
        if (buffer.size > MAX_BUFFER) buffer = buffer.copyOfRange(buffer.size - MAX_BUFFER, buffer.size)

        var offset = 0
        while (true) {
            val start = headerAt(buffer, offset)
            if (start == null) {
                // Заголовок может разорваться между пакетами: последний байт бережём.
                offset = maxOf(buffer.size - 1, 0)
                break
            }
            offset = start
            if (buffer.size - start < MIN_FRAME) break
            val end = start + (buffer[start + 3].toInt() and 0xff) + FRAME_EXTRA
            if (end > buffer.size) break
            message(buffer, start, end)?.let(block)
            offset = end
        }
        if (offset > 0) buffer = buffer.copyOfRange(offset, buffer.size)
    }

    private fun headerAt(data: ByteArray, from: Int): Int? {
        for (index in from until data.size - 1) {
            if (data[index] == HEADER_FIRST && data[index + 1] == HEADER_SECOND) return index
        }
        return null
    }

    private fun message(data: ByteArray, start: Int, end: Int): AcaiaMessage? {
        return when (data[start + 2].toInt() and 0xff) {
            CMD_EVENT -> {
                val payload = data.copyOfRange(start + 5, end)
                when (data[start + 4].toInt() and 0xff) {
                    EVENT_WEIGHT -> weight(payload)
                    // Вес приходит и ответом на пульс, если весы им отвечают.
                    EVENT_HEARTBEAT ->
                        if (payload.getOrNull(2)?.toInt() == EVENT_WEIGHT) {
                            weight(payload.copyOfRange(3, payload.size))
                        } else {
                            null
                        }
                    // Кнопки на весах: тара и таймер присылают заодно вес.
                    EVENT_BUTTON -> buttonWeight(payload)
                    else -> null
                }
            }

            CMD_SETTINGS -> settings(data.copyOfRange(start + 3, end))
            else -> null
        }
    }

    /** Вес: два байта, делитель по числу знаков после запятой и знак. */
    private fun weight(payload: ByteArray): AcaiaMessage.Weight? {
        if (payload.size < WEIGHT_SIZE) return null
        val raw = ((payload[1].toInt() and 0xff) shl 8) or (payload[0].toInt() and 0xff)
        val divisor = when (payload[4].toInt() and 0xff) {
            1 -> 10f
            2 -> 100f
            3 -> 1_000f
            4 -> 10_000f
            else -> return null
        }
        val sign = if (payload[5].toInt() and 0x02 != 0) -1f else 1f
        return AcaiaMessage.Weight(sign * raw / divisor)
    }

    /** У нажатий кнопок свой заголовок, а вес лежит следом за ним. */
    private fun buttonWeight(payload: ByteArray): AcaiaMessage.Weight? {
        val first = payload.getOrNull(0)?.toInt() ?: return null
        val second = payload.getOrNull(1)?.toInt() ?: return null
        val offset = when {
            first == 0 && second == 5 -> 2      // тара
            first == 8 && second == 5 -> 2      // пуск таймера
            first == 10 && second == 7 -> 6     // стоп: сначала время
            first == 9 && second == 7 -> 6      // сброс: сначала время
            else -> return null
        }
        if (payload.size <= offset) return null
        return weight(payload.copyOfRange(offset, payload.size))
    }

    private fun settings(payload: ByteArray): AcaiaMessage.Settings? {
        if (payload.size < SETTINGS_SIZE) return null
        val battery = (payload[1].toInt() and 0x7f).takeIf { it in 0..100 }
        return AcaiaMessage.Settings(
            batteryPercent = battery,
            ounces = (payload[2].toInt() and 0xff) == UNIT_OUNCES,
        )
    }

    /** Кадр с двумя контрольными суммами: по чётным и по нечётным байтам. */
    private fun encode(type: Int, payload: ByteArray): ByteArray {
        val frame = ByteArray(payload.size + 5)
        frame[0] = HEADER_FIRST
        frame[1] = HEADER_SECOND
        frame[2] = type.toByte()
        var even = 0
        var odd = 0
        payload.forEachIndexed { index, byte ->
            frame[3 + index] = byte
            if (index % 2 == 0) even += byte.toInt() and 0xff else odd += byte.toInt() and 0xff
        }
        frame[payload.size + 3] = (even and 0xff).toByte()
        frame[payload.size + 4] = (odd and 0xff).toByte()
        return frame
    }

    private sealed interface AcaiaMessage {
        data class Weight(val grams: Float) : AcaiaMessage
        data class Settings(val batteryPercent: Int?, val ounces: Boolean) : AcaiaMessage
    }

    private companion object {
        const val HEADER_FIRST = 0xEF.toByte()
        const val HEADER_SECOND = 0xDD.toByte()

        /** Заголовок, тип, длина, две контрольные суммы. */
        const val MIN_FRAME = 6
        const val FRAME_EXTRA = 5
        const val MAX_BUFFER = 128
        const val WEIGHT_SIZE = 6
        const val SETTINGS_SIZE = 7

        const val CMD_EVENT = 12
        const val CMD_SETTINGS = 8
        const val EVENT_WEIGHT = 5
        const val EVENT_BUTTON = 8
        const val EVENT_HEARTBEAT = 11
        const val UNIT_OUNCES = 5

        const val MSG_HEARTBEAT = 0
        const val MSG_TARE = 4
        const val MSG_IDENT = 11
        const val MSG_EVENTS = 12

        const val GRAMS_PER_OUNCE = 28.349523f

        val CLASSIC_ID = ByteArray(15) { 0x2D }
        val PYXIS_ID = byteArrayOf(
            0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37,
            0x38, 0x39, 0x30, 0x31, 0x32, 0x33, 0x34,
        )
        val HEARTBEAT = byteArrayOf(
            HEADER_FIRST, HEADER_SECOND, MSG_HEARTBEAT.toByte(), 0x02, 0x00, 0x02, 0x00,
        )
    }
}

/** Pearl, Lunar до 2021 года и PROCHBT001: одна характеристика на всё. */
object AcaiaClassicDriver : AcaiaDriver(pyxisStyle = false) {
    override val title = "Acaia Pearl / Lunar"
    override val service: UUID = bluetoothUuid("1820")
    override val weightCharacteristic: UUID = bluetoothUuid("2a80")
    override val commandCharacteristic: UUID = bluetoothUuid("2a80")
}

/** Pyxis, Lunar 2021 и Cinco: служба последовательного порта, две характеристики. */
object AcaiaPyxisDriver : AcaiaDriver(pyxisStyle = true) {
    override val title = "Acaia Pyxis / Lunar 2021"
    override val service: UUID = UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455")
    override val weightCharacteristic: UUID =
        UUID.fromString("49535343-1e4d-4bd9-ba61-23c647249616")
    override val commandCharacteristic: UUID =
        UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3")
}
