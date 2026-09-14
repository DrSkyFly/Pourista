package com.pourista.scale

import java.util.UUID

/**
 * DiFluid Microbalance.
 *
 * The frame: two header bytes DF DF, the group, the command, the data length and a checksum —
 * a plain sum of the preceding bytes. The weight arrives in four bytes big-endian in tenths
 * of a gram.
 *
 * The scale sends no weight until asked: after connecting we turn on automatic notifications
 * and ask for grams.
 *
 * The protocol is written from open implementations and has not been checked on hardware.
 */
object DifluidMicrobalanceDriver : ScaleDriver {

    override val title = "DiFluid Microbalance"
    override val nameFragments = listOf("microbalance")

    override val service: UUID = bluetoothUuid("00ee")
    override val weightCharacteristic: UUID = bluetoothUuid("aa01")
    override val commandCharacteristic: UUID = bluetoothUuid("aa01")

    override fun parseWeight(value: ByteArray): WeightReading? {
        if (value.size < PACKET_SIZE) return null
        if (value[0] != HEADER_FIRST || value[1] != HEADER_SECOND) return null
        if (value[3].toInt() != DATA_WEIGHT) return null

        val tenths = ((value[5].toInt() and 0xff) shl 24) or
            ((value[6].toInt() and 0xff) shl 16) or
            ((value[7].toInt() and 0xff) shl 8) or
            (value[8].toInt() and 0xff)
        return WeightReading(grams = tenths / 10f)
    }

    override fun tareCommand(): ByteArray = command(0x03, 0x02, 0x01, 0x01)

    override fun unitCommand(unit: WeightUnit): ByteArray? =
        if (unit == WeightUnit.GRAM) command(0x01, 0x04, 0x01, 0x00) else null

    /** Without this command the scale keeps quiet and only answers polling. */
    override fun onConnectCommands(): List<ByteArray> = listOf(command(0x01, 0x00, 0x01, 0x01))

    private fun command(group: Int, code: Int, length: Int, argument: Int): ByteArray {
        val body = byteArrayOf(
            HEADER_FIRST,
            HEADER_SECOND,
            group.toByte(),
            code.toByte(),
            length.toByte(),
            argument.toByte(),
        )
        var checksum = 0
        body.forEach { checksum += it.toInt() and 0xff }
        return body + (checksum and 0xff).toByte()
    }

    private const val PACKET_SIZE = 19
    private const val DATA_WEIGHT = 0
    private const val HEADER_FIRST = 0xDF.toByte()
    private const val HEADER_SECOND = 0xDF.toByte()
}
