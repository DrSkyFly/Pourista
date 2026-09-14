package com.pourista.scale

import java.util.UUID

/**
 * Bookoo Themis and compatibles.
 *
 * A packet of twenty bytes: the weight in three bytes big-endian in hundredths of a gram,
 * the sign in a byte of its own, the battery in percent as it comes. Commands go into a
 * separate characteristic.
 *
 * The protocol is written from open implementations and has not been checked on hardware.
 */
object BookooDriver : ScaleDriver {

    override val title = "Bookoo"
    override val nameFragments = listOf("bookoo")

    override val service: UUID = bluetoothUuid("0ffe")
    override val weightCharacteristic: UUID = bluetoothUuid("ff11")
    override val commandCharacteristic: UUID = bluetoothUuid("ff12")

    override fun parseWeight(value: ByteArray): WeightReading? {
        if (value.size < PACKET_SIZE) return null
        // Other packets arrive in the same characteristic: the weight has a number of its own.
        if (value[0] != PRODUCT || value[1] != TYPE_WEIGHT) return null

        val hundredths = (value[7].toInt() and 0xff shl 16) or
            (value[8].toInt() and 0xff shl 8) or
            (value[9].toInt() and 0xff)
        val sign = if (value[6].toInt() == ASCII_MINUS) -1f else 1f
        val battery = (value[13].toInt() and 0xff).takeIf { it in 0..100 }

        return WeightReading(grams = sign * hundredths / 100f, batteryPercent = battery)
    }

    override fun tareCommand(): ByteArray =
        byteArrayOf(0x03, 0x0a, 0x01, 0x00, 0x00, 0x08)

    private const val PACKET_SIZE = 20
    private const val PRODUCT: Byte = 0x03
    private const val TYPE_WEIGHT: Byte = 0x0B
    private const val ASCII_MINUS = '-'.code
}
