package com.pourista.scale

import java.util.UUID

/**
 * Timemore Black Mirror Dot and Basic 3 — they share one protocol.
 *
 * It speaks in frames: two header bytes, the operation code, the command number, the data
 * length and two bytes of CRC-16 at the end. The weight arrives in four bytes big-endian in
 * tenths of a gram, followed by two bytes of flow rate in tenths of a gram per second; the
 * battery comes in a frame of its own, which the scale appends to the tail of the weight
 * frame.
 *
 * The scale sends the checksum as zeros (verified against a recording from a live device),
 * so we do not check it on receipt — otherwise not a single packet would pass. In our own
 * commands the CRC is counted properly.
 *
 * The service and the weight characteristic are the same as Futula's, so the driver is
 * chosen by the device name.
 *
 * The parsing was verified against a Black Mirror Dot protocol recording from an owner of
 * the scale (issue #2) and against the open Beanconqueror implementation.
 */
object TimemoreDotDriver : ScaleDriver {

    override val title = "Timemore Black Mirror Dot / Basic 3"
    override val nameFragments = listOf("dot", "tes017", "timemore", "basic3")

    override val service: UUID = bluetoothUuid("fff0")
    override val weightCharacteristic: UUID = bluetoothUuid("fff1")

    /** Commands go into a separate characteristic: fff1 only notifies. */
    override val commandCharacteristic: UUID = bluetoothUuid("fff2")

    /**
     * "dot" is too short a word to trust any occurrence of: half the speakers and watches
     * would fall under it. On its own it is accepted only as the beginning of a name,
     * otherwise the manufacturer name has to stand next to it.
     *
     * A plain "timemore" without a model is not taken: the second-generation Black Mirror is
     * called "TIMEMORE Scale" and speaks the standard scale protocol, not this one.
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
     * The weight and, if the scale appended it to the same packet, the battery. Frames arrive
     * glued together, so the packet is parsed whole rather than from the start alone.
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
                    reading = WeightReading(grams = tenths / 10f, flowRate = flowRate(data))
                }

                CMD_BATTERY -> if (battery == null) battery = batteryPercent(data)
            }
        }
        return reading?.copy(batteryPercent = battery)
    }

    /** The battery comes in a frame of its own — sometimes a separate packet, sometimes a tail. */
    override fun parseBattery(value: ByteArray): Int? {
        var battery: Int? = null
        forEachFrame(value) { command, data ->
            if (command == CMD_BATTERY && battery == null) battery = batteryPercent(data)
        }
        return battery
    }

    override fun tareCommand(): ByteArray = frame(opcode = OPCODE_WRITE, command = CMD_TARE)

    /** The scale can do ounces; recipes are in grams, so we ask for grams. */
    override fun unitCommand(unit: WeightUnit): ByteArray? =
        if (unit == WeightUnit.GRAM) {
            frame(opcode = OPCODE_WRITE, command = CMD_UNIT, data = byteArrayOf(UNIT_GRAM))
        } else {
            null
        }

    /** The ordinary weighing mode: in the others the scale sends the weight its own way. */
    override fun onConnectCommands(): List<ByteArray> = listOf(
        frame(opcode = OPCODE_WRITE, command = CMD_MODE, data = byteArrayOf(MODE_STANDARD, 0x00)),
    )

    /**
     * The flow rate the scale counted itself. It counts faster than the rate shows up in the
     * weight gain: while the app is averaging a second, the scale already has a number.
     *
     * The sign is kept: when water is taken off the scale, the rate goes negative. In the
     * recording from a live device the field once showed 999 — a load was put on all at once,
     * and the scale hit its own ceiling. We hand it over as it is: the plausibility limit is
     * the engine's to check.
     *
     * A short frame has no rate — then null.
     */
    private fun flowRate(data: ByteArray): Float? {
        if (data.size < WEIGHT_DATA_SIZE + FLOW_DATA_SIZE) return null
        val tenths = (data[WEIGHT_DATA_SIZE].toInt() and 0xff shl 8) or
            (data[WEIGHT_DATA_SIZE + 1].toInt() and 0xff)
        return tenths.toShort() / 10f
    }

    private fun batteryPercent(data: ByteArray): Int? =
        data.getOrNull(1)?.let { it.toInt() and 0xff }?.takeIf { it in 0..100 }

    /**
     * Walks through every frame of the packet. One notification can hold several: the scale
     * appends the battery to the tail of the weight.
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

    /** CRC-16/IBM: polynomial 0xA001 reversed, initial value 0xFFFF. */
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
    private const val FLOW_DATA_SIZE = 2
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
