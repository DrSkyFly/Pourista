package com.pourista.scale

import java.util.Calendar
import java.util.UUID

/**
 * Futula Kitchen Scale 3, also sold as LEFU CK811.
 *
 * The only protocol checked on live hardware: tare, battery, switching the unit and parsing
 * the weight, negative included.
 */
object FutulaDriver : ScaleDriver {

    override val title = "Futula Kitchen Scale 3 / LEFU CK811"
    override val experimental = false
    override val nameFragments = listOf("LFSmart Scale", "LEFU-CK811")

    override val service: UUID = bluetoothUuid("fff0")
    override val weightCharacteristic: UUID = bluetoothUuid("fff4")
    override val commandCharacteristic: UUID = bluetoothUuid("fff1")
    override val batteryService: UUID = bluetoothUuid("180f")
    override val batteryCharacteristic: UUID = bluetoothUuid("2a19")

    /**
     * The weight lies at positions 3..4 little-endian, the sign at position 5, the unit on
     * the display at position 8. The weight itself the scale always sends in grams.
     */
    override fun parseWeight(value: ByteArray): WeightReading? {
        if (value.size < 9) return null
        val raw = (value[4].toInt() and 0xff shl 8) or (value[3].toInt() and 0xff)
        val sign = if (value[5].toInt() > 0) -1f else 1f
        return WeightReading(
            grams = raw / 10f * sign,
            unitOnScale = WeightUnit.fromScaleByte(value[8].toInt()),
        )
    }

    override fun tareCommand(): ByteArray =
        ScaleDrivers.hexToBytes("fd320000000000000000cf")

    override fun unitCommand(unit: WeightUnit): ByteArray =
        ScaleDrivers.hexToBytes(unit.commandHex)

    override fun onConnectCommands(): List<ByteArray> = listOf(timeSyncCommand())

    /**
     * Syncing the scale clock. Only the hours, minutes and seconds change, the rest of the
     * bytes the hardware takes as they come.
     */
    fun timeSyncCommand(calendar: Calendar = Calendar.getInstance()): ByteArray = byteArrayOf(
        0xF1.toByte(), 0x07, 0xE8.toByte(), 0x07, 0x05,
        calendar.get(Calendar.HOUR_OF_DAY).toByte(),
        calendar.get(Calendar.MINUTE).toByte(),
        calendar.get(Calendar.SECOND).toByte(),
    )
}
