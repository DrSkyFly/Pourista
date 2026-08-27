package com.pourista.scale

import java.util.UUID

/**
 * Eureka Precisa и её близнецы: одни и те же весы продаются под именами
 * CFS-9002 и LSJ-001.
 *
 * Пакет из одиннадцати байтов: знак отдельным байтом, вес двумя байтами
 * младшим вперёд в десятых долях грамма. Команды — четыре байта в свою
 * характеристику, без подтверждения.
 *
 * Протокол написан по открытым реализациям, на железе не проверялся.
 */
object EurekaPrecisaDriver : ScaleDriver {

    override val title = "Eureka Precisa"
    override val nameFragments = listOf("cfs-9002", "lsj-001")

    override val service: UUID = bluetoothUuid("fff0")
    override val weightCharacteristic: UUID = bluetoothUuid("fff1")
    override val commandCharacteristic: UUID = bluetoothUuid("fff2")
    override val writeWithoutResponse = true

    override fun parseWeight(value: ByteArray): WeightReading? {
        if (value.size < PACKET_SIZE) return null
        val tenths = ((value[8].toInt() and 0xff) shl 8) or (value[7].toInt() and 0xff)
        val sign = if (value[6].toInt() != 0) -1f else 1f
        return WeightReading(grams = sign * tenths / 10f)
    }

    override fun tareCommand(): ByteArray = command(CMD_TARE)

    /** Команда — заголовок, длина и код операции дважды. */
    private fun command(code: Byte): ByteArray = byteArrayOf(HEADER, BASE, code, code)

    private const val PACKET_SIZE = 9
    private const val HEADER: Byte = 0xAA.toByte()
    private const val BASE: Byte = 0x02
    private const val CMD_TARE: Byte = 0x31
}
