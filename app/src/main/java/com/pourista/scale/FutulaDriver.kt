package com.pourista.scale

import java.util.Calendar
import java.util.UUID

/**
 * Futula Kitchen Scale 3, они же LEFU CK811.
 *
 * Единственный протокол, проверенный на живом железе: тара, заряд, смена
 * единицы и разбор веса, включая отрицательный.
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
     * Вес лежит в позициях 3..4 младшим байтом вперёд, знак в позиции 5,
     * единица на экране — в позиции 8. Сам вес весы всегда шлют в граммах.
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
     * Синхронизация часов весов. Меняются только часы, минуты и секунды,
     * остальные байты железо принимает как есть.
     */
    fun timeSyncCommand(calendar: Calendar = Calendar.getInstance()): ByteArray = byteArrayOf(
        0xF1.toByte(), 0x07, 0xE8.toByte(), 0x07, 0x05,
        calendar.get(Calendar.HOUR_OF_DAY).toByte(),
        calendar.get(Calendar.MINUTE).toByte(),
        calendar.get(Calendar.SECOND).toByte(),
    )
}
