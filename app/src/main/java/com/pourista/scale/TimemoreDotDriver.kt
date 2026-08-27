package com.pourista.scale

import java.util.UUID

/**
 * Timemore Black Mirror Dot и Basic 3 — у них один протокол.
 *
 * Говорит кадрами: два байта заголовка, код операции, номер команды, длина
 * данных и два байта в конце под CRC-16. Вес приходит четырьмя байтами
 * старшим вперёд в десятых долях грамма, заряд — отдельным кадром, который
 * весы дописывают в хвост к кадру веса.
 *
 * Контрольную сумму весы шлют нулями (проверено по записи с живого
 * устройства), поэтому на приёме её не сверяем — иначе не прошёл бы ни один
 * пакет. В своих командах CRC считаем как положено.
 *
 * Служба и характеристика веса совпадают с Futula, поэтому драйвер выбирается
 * по имени устройства.
 *
 * Разбор сверен с записью протокола Black Mirror Dot от владельца весов
 * (issue #2) и с открытой реализацией Beanconqueror.
 */
object TimemoreDotDriver : ScaleDriver {

    override val title = "Timemore Black Mirror Dot / Basic 3"
    override val nameFragments = listOf("dot", "tes017", "timemore", "basic3")

    override val service: UUID = bluetoothUuid("fff0")
    override val weightCharacteristic: UUID = bluetoothUuid("fff1")

    /** Команды идут в отдельную характеристику: fff1 только уведомляет. */
    override val commandCharacteristic: UUID = bluetoothUuid("fff2")

    /**
     * «dot» — слишком короткое слово, чтобы верить любому вхождению: под него
     * попадёт половина колонок и часов. Само по себе принимаем его только как
     * начало имени, иначе рядом должно стоять имя производителя.
     *
     * Простой «timemore» без модели не берём: Black Mirror второго поколения
     * зовётся «TIMEMORE Scale» и говорит стандартным протоколом весов, а не
     * этим.
     */
    override fun matches(deviceName: String): Boolean {
        val name = deviceName.trim().lowercase()
        val family = name.contains("timemore") || name.contains("tes017")
        return name.startsWith("dot") ||
            name.contains("tes017") ||
            name.contains("basic3") ||
            name.contains("basic 3") ||
            (family && (name.contains("dot") || name.contains("basic")))
    }

    /**
     * Вес и, если весы дописали его в тот же пакет, заряд. Кадры приходят
     * склеенными, поэтому пакет разбираем целиком, а не только с начала.
     */
    override fun parseWeight(value: ByteArray): WeightReading? {
        var reading: WeightReading? = null
        var battery: Int? = null
        forEachFrame(value) { command, data ->
            when (command) {
                CMD_WEIGHT -> if (reading == null && data.size >= WEIGHT_DATA_SIZE) {
                    val tenths = (data[0].toInt() and 0xff shl 24) or
                        (data[1].toInt() and 0xff shl 16) or
                        (data[2].toInt() and 0xff shl 8) or
                        (data[3].toInt() and 0xff)
                    reading = WeightReading(grams = tenths / 10f)
                }

                CMD_BATTERY -> if (battery == null) battery = batteryPercent(data)
            }
        }
        return reading?.copy(batteryPercent = battery)
    }

    /** Заряд приходит своим кадром — иногда отдельным пакетом, иногда в хвосте. */
    override fun parseBattery(value: ByteArray): Int? {
        var battery: Int? = null
        forEachFrame(value) { command, data ->
            if (command == CMD_BATTERY && battery == null) battery = batteryPercent(data)
        }
        return battery
    }

    override fun tareCommand(): ByteArray = frame(opcode = OPCODE_WRITE, command = CMD_TARE)

    /** Весы умеют унции; рецепты в граммах, поэтому просим граммы. */
    override fun unitCommand(unit: WeightUnit): ByteArray? =
        if (unit == WeightUnit.GRAM) {
            frame(opcode = OPCODE_WRITE, command = CMD_UNIT, data = byteArrayOf(UNIT_GRAM))
        } else {
            null
        }

    /** Обычный режим взвешивания: в остальных весы шлют вес по-своему. */
    override fun onConnectCommands(): List<ByteArray> = listOf(
        frame(opcode = OPCODE_WRITE, command = CMD_MODE, data = byteArrayOf(MODE_STANDARD, 0x00)),
    )

    private fun batteryPercent(data: ByteArray): Int? =
        data.getOrNull(1)?.let { it.toInt() and 0xff }?.takeIf { it in 0..100 }

    /**
     * Проходит по всем кадрам пакета. В одном уведомлении их бывает несколько:
     * весы дописывают заряд в хвост к весу.
     */
    private inline fun forEachFrame(value: ByteArray, block: (command: Int, data: ByteArray) -> Unit) {
        var offset = 0
        while (offset + HEADER_SIZE + CRC_SIZE <= value.size) {
            if (value[offset] != HEADER_FIRST || value[offset + 1] != HEADER_SECOND) return
            val opcode = value[offset + 2].toInt() and 0xff
            if (opcode != OPCODE_REPLY && opcode != OPCODE_PUSH) return

            val length = (value[offset + 4].toInt() and 0xff shl 8) or
                (value[offset + 5].toInt() and 0xff)
            val end = offset + HEADER_SIZE + length
            if (end + CRC_SIZE > value.size) return

            block(value[offset + 3].toInt() and 0xff, value.copyOfRange(offset + HEADER_SIZE, end))
            offset = end + CRC_SIZE
        }
    }

    private fun frame(opcode: Int, command: Int, data: ByteArray = ByteArray(0)): ByteArray {
        val body = ByteArray(HEADER_SIZE + data.size)
        body[0] = HEADER_FIRST
        body[1] = HEADER_SECOND
        body[2] = opcode.toByte()
        body[3] = command.toByte()
        body[4] = (data.size shr 8 and 0xff).toByte()
        body[5] = (data.size and 0xff).toByte()
        data.copyInto(body, HEADER_SIZE)

        val crc = crc16(body)
        return body + byteArrayOf((crc shr 8 and 0xff).toByte(), (crc and 0xff).toByte())
    }

    /** CRC-16/IBM: полином 0xA001 в обратном порядке, начальное значение 0xFFFF. */
    internal fun crc16(data: ByteArray): Int {
        var crc = 0xffff
        data.forEach { byte ->
            crc = crc xor (byte.toInt() and 0xff)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc shr 1) xor 0xa001 else crc shr 1
            }
        }
        return crc and 0xffff
    }

    private const val HEADER_SIZE = 6
    private const val CRC_SIZE = 2
    private const val WEIGHT_DATA_SIZE = 4
    private const val HEADER_FIRST = 0xA5.toByte()
    private const val HEADER_SECOND = 0x5A.toByte()
    private const val OPCODE_REPLY = 0x01
    private const val OPCODE_PUSH = 0x02
    private const val OPCODE_WRITE = 0x03
    private const val CMD_WEIGHT = 0x01
    private const val CMD_MODE = 0x08
    private const val CMD_UNIT = 0x06
    private const val CMD_BATTERY = 0x05
    private const val CMD_TARE = 0x0D
    private const val UNIT_GRAM: Byte = 0x00
    private const val MODE_STANDARD: Byte = 0x01
}
