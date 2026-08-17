package com.pourista.scale

import java.util.UUID

/**
 * Timemore Black Mirror Dot.
 *
 * Говорит кадрами: два байта заголовка, код операции, номер команды, длина
 * данных и контрольная сумма CRC-16 в конце. Вес приходит четырьмя байтами
 * старшим вперёд в десятых долях грамма, заряд — отдельной командой.
 *
 * Служба и характеристика совпадают с Futula, поэтому драйвер выбирается по
 * имени устройства.
 *
 * Протокол написан по открытым реализациям, на железе не проверялся.
 */
object TimemoreDotDriver : ScaleDriver {

    override val title = "Timemore Black Mirror Dot"
    override val nameFragments = listOf("dot", "tes017", "timemore")

    override val service: UUID = bluetoothUuid("fff0")
    override val weightCharacteristic: UUID = bluetoothUuid("fff1")
    override val commandCharacteristic: UUID = bluetoothUuid("fff1")

    /**
     * «dot» — слишком короткое слово, чтобы верить любому вхождению: под него
     * попадёт половина колонок и часов. Принимаем его только как отдельное
     * начало имени, остальные метки проверяем как есть.
     */
    override fun matches(deviceName: String): Boolean {
        val name = deviceName.trim().lowercase()
        return name.startsWith("dot") || name.contains("tes017") || name.contains("timemore")
    }

    override fun parseWeight(value: ByteArray): WeightReading? {
        val data = payload(value, command = CMD_WEIGHT) ?: return null
        if (data.size < 4) return null
        val tenths = (data[0].toInt() and 0xff shl 24) or
            (data[1].toInt() and 0xff shl 16) or
            (data[2].toInt() and 0xff shl 8) or
            (data[3].toInt() and 0xff)
        return WeightReading(grams = tenths / 10f)
    }

    /** Заряд приходит своим кадром, не вместе с весом. */
    override fun parseBattery(value: ByteArray): Int? {
        val data = payload(value, command = CMD_BATTERY) ?: return null
        return data.getOrNull(1)?.let { it.toInt() and 0xff }?.takeIf { it in 0..100 }
    }

    override fun tareCommand(): ByteArray = frame(opcode = 0x03, command = CMD_TARE)

    /** Данные нужного кадра или null, если пришло что-то другое. */
    private fun payload(value: ByteArray, command: Int): ByteArray? {
        if (value.size < HEADER_SIZE + CRC_SIZE) return null
        if (value[0] != HEADER_FIRST || value[1] != HEADER_SECOND) return null
        val opcode = value[2].toInt() and 0xff
        if (opcode != OPCODE_REPLY && opcode != OPCODE_PUSH) return null
        if ((value[3].toInt() and 0xff) != command) return null

        val length = (value[4].toInt() and 0xff shl 8) or (value[5].toInt() and 0xff)
        if (value.size < HEADER_SIZE + length + CRC_SIZE) return null
        return value.copyOfRange(HEADER_SIZE, HEADER_SIZE + length)
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
    private const val HEADER_FIRST = 0xA5.toByte()
    private const val HEADER_SECOND = 0x5A.toByte()
    private const val OPCODE_REPLY = 0x01
    private const val OPCODE_PUSH = 0x02
    private const val CMD_WEIGHT = 0x01
    private const val CMD_BATTERY = 0x05
    private const val CMD_TARE = 0x0D
}
