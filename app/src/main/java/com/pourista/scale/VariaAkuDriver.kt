package com.pourista.scale

import java.util.UUID

/**
 * Varia AKU и AKU mini.
 *
 * Вес приходит тремя с половиной байтами: старшая половина байта занята
 * знаком, дальше двадцать бит в сотых долях грамма. Тара подписывается
 * исключающим ИЛИ.
 *
 * Протокол написан по открытым реализациям, на железе не проверялся.
 */
object VariaAkuDriver : ScaleDriver {

    override val title = "Varia AKU"
    override val nameFragments = listOf("varia aku", "aku mini")

    override val service: UUID = bluetoothUuid("fff0")
    override val weightCharacteristic: UUID = bluetoothUuid("fff1")
    override val commandCharacteristic: UUID = bluetoothUuid("fff2")
    override val writeWithoutResponse = true

    override fun matches(deviceName: String): Boolean {
        val name = deviceName.trim().lowercase()
        return name.startsWith("aku") || nameFragments.any { name.contains(it) }
    }

    override fun parseWeight(value: ByteArray): WeightReading? {
        if (value.size < PACKET_SIZE) return null
        if (value[1].toInt() and 0xff != PACKET_WEIGHT) return null

        val hundredths = ((value[3].toInt() and 0x0f) shl 16) or
            ((value[4].toInt() and 0xff) shl 8) or
            (value[5].toInt() and 0xff)
        val sign = if (value[3].toInt() and 0x10 != 0) -1f else 1f
        return WeightReading(grams = sign * hundredths / 100f)
    }

    override fun tareCommand(): ByteArray {
        val body = byteArrayOf(0x82.toByte(), 0x01, 0x01)
        var checksum = 0
        body.forEach { checksum = checksum xor (it.toInt() and 0xff) }
        return byteArrayOf(HEADER) + body + checksum.toByte()
    }

    private const val PACKET_SIZE = 6
    private const val PACKET_WEIGHT = 0x01
    private const val HEADER: Byte = 0xFA.toByte()
}
