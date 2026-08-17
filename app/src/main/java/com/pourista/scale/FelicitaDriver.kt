package com.pourista.scale

import java.util.UUID
import kotlin.math.roundToInt

/**
 * Felicita Arc и родственные модели.
 *
 * Пакет из восемнадцати байтов: знак, шесть цифр веса в ASCII, единица и
 * заряд. Команды — по одному байту в ту же характеристику, что и уведомления.
 *
 * Протокол написан по открытым реализациям, на железе не проверялся.
 */
object FelicitaDriver : ScaleDriver {

    override val title = "Felicita"
    override val nameFragments = listOf("FELICITA")

    override val service: UUID = bluetoothUuid("ffe0")
    override val weightCharacteristic: UUID = bluetoothUuid("ffe1")
    override val commandCharacteristic: UUID = bluetoothUuid("ffe1")

    override fun parseWeight(value: ByteArray): WeightReading? {
        if (value.size < PACKET_SIZE) return null

        // Вес — шесть цифр ASCII в позициях 3..8, сотые доли грамма.
        var hundredths = 0
        for (index in 3..8) {
            val digit = value[index].toInt() - ASCII_ZERO
            if (digit !in 0..9) return null
            hundredths = hundredths * 10 + digit
        }
        val sign = if (value[2].toInt() == ASCII_MINUS) -1f else 1f

        val unit = String(value, 9, 2, Charsets.US_ASCII).trim().lowercase()
        return WeightReading(
            grams = sign * hundredths / 100f,
            unitOnScale = if (unit == "oz") WeightUnit.OUNCE else WeightUnit.GRAM,
            batteryPercent = batteryPercent(value[15].toInt() and 0xff),
        )
    }

    override fun tareCommand(): ByteArray = byteArrayOf(CMD_TARE)

    /** Заряд приходит сырым уровнем, границы у модели фиксированные. */
    private fun batteryPercent(raw: Int): Int? {
        if (raw !in BATTERY_MIN..BATTERY_MAX) return null
        val share = (raw - BATTERY_MIN).toFloat() / (BATTERY_MAX - BATTERY_MIN)
        return (share * 100).roundToInt()
    }

    private const val PACKET_SIZE = 18
    private const val ASCII_ZERO = '0'.code
    private const val ASCII_MINUS = '-'.code
    private const val BATTERY_MIN = 129
    private const val BATTERY_MAX = 158
    private const val CMD_TARE: Byte = 0x54
}
