package com.pourista.scale

import java.util.UUID

/**
 * Eureka Precisa and its twins: the same scale is sold under the names CFS-9002 and
 * LSJ-001.
 *
 * A packet of eleven bytes: the sign in a byte of its own, the weight in two bytes
 * little-endian in tenths of a gram. Commands are four bytes into a characteristic of their
 * own, without acknowledgement.
 *
 * The protocol is written from open implementations and has not been checked on hardware.
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

    /** A command is the header, the length and the operation code twice. */
    private fun command(code: Byte): ByteArray = byteArrayOf(HEADER, BASE, code, code)

    private const val PACKET_SIZE = 9
    private const val HEADER: Byte = 0xAA.toByte()
    private const val BASE: Byte = 0x02
    private const val CMD_TARE: Byte = 0x31
}
