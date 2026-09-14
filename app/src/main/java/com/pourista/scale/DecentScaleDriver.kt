package com.pourista.scale

import java.util.UUID

/**
 * Decent Scale.
 *
 * The weight arrives in a short packet: the second byte holds the stability flag, then two
 * bytes big-endian in tenths of a gram. Commands are signed with a checksum — an exclusive
 * OR over the preceding bytes — and the tare carries a counter as well, otherwise the scale
 * takes it for a repeat of the previous one.
 *
 * The service and the weight characteristic are the same as Futula's, so the driver is
 * chosen by the device name rather than by identifiers.
 *
 * The protocol is written from open implementations and has not been checked on hardware.
 */
object DecentScaleDriver : ScaleDriver {

    override val title = "Decent Scale"
    override val nameFragments = listOf("decent")

    override val service: UUID = bluetoothUuid("fff0")
    override val weightCharacteristic: UUID = bluetoothUuid("fff4")
    override val commandCharacteristic: UUID = bluetoothUuid("36f5")

    private var tareCounter = 0

    override fun parseWeight(value: ByteArray): WeightReading? {
        if (value.size < PACKET_SIZE) return null
        val kind = value[1].toInt() and 0xff
        if (kind != WEIGHT_STABLE && kind != WEIGHT_CHANGING) return null

        val tenths = ((value[2].toInt() and 0xff shl 8) or (value[3].toInt() and 0xff)).toShort()
        return WeightReading(grams = tenths / 10f)
    }

    override fun tareCommand(): ByteArray {
        tareCounter = (tareCounter + 1) and 0xff
        return command(byteArrayOf(HEADER, 0x0f, 0xfd.toByte(), tareCounter.toByte(), 0x00, 0x01))
    }

    /** Turn on the weight and timer backlight: without it the scale blanks the display. */
    override fun onConnectCommands(): List<ByteArray> =
        listOf(command(byteArrayOf(HEADER, 0x0a, 0x01, 0x01, 0x00, 0x00)))

    /** Without reminders the scale falls asleep in the middle of a pour. */
    override fun heartbeatCommands(): List<ByteArray> =
        listOf(command(byteArrayOf(HEADER, 0x0a, 0x03, 0xff.toByte(), 0xff.toByte(), 0x00)))

    override val heartbeatIntervalMs = 2_000L

    /** The scale often loses the first command, so every one is sent twice. */
    override val commandRepeats = 2

    /** Appends the checksum to the six bytes. */
    private fun command(body: ByteArray): ByteArray {
        var checksum = 0
        body.forEach { checksum = checksum xor (it.toInt() and 0xff) }
        return body + checksum.toByte()
    }

    private const val PACKET_SIZE = 4
    private const val HEADER: Byte = 0x03
    private const val WEIGHT_STABLE = 0xce
    private const val WEIGHT_CHANGING = 0xca
}
