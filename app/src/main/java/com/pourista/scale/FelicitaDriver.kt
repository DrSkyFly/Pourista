package com.pourista.scale

import java.util.UUID
import kotlin.math.roundToInt

/**
 * Felicita Arc and related models.
 *
 * A packet of eighteen bytes: the sign, six digits of the weight in ASCII, the unit and the
 * battery. Commands are single bytes into the same characteristic as the notifications.
 *
 * The protocol is written from open implementations and has not been checked on hardware.
 */
object FelicitaDriver : ScaleDriver {

    override val title = "Felicita"
    override val nameFragments = listOf("FELICITA")

    override val service: UUID = bluetoothUuid("ffe0")
    override val weightCharacteristic: UUID = bluetoothUuid("ffe1")
    override val commandCharacteristic: UUID = bluetoothUuid("ffe1")

    override fun parseWeight(value: ByteArray): WeightReading? {
        if (value.size < PACKET_SIZE) return null

        // The weight is six ASCII digits at positions 3..8, in hundredths of a gram.
        var hundredths = 0
        for (index in 3..8) {
            val digit = value[index].toInt() - ASCII_ZERO
            if (digit !in 0..9) return null
            hundredths = hundredths * 10 + digit
        }
        val sign = if (value[2].toInt() == ASCII_MINUS) -1f else 1f

        val unit = String(value, 9, 2, Charsets.US_ASCII).trim().lowercase()
        val ounces = unit == "oz"
        // The scale may be set to ounces; the app counts in grams, so we convert at once
        // rather than waiting for the unit to be switched back.
        val displayed = sign * hundredths / 100f
        return WeightReading(
            grams = if (ounces) displayed * GRAMS_PER_OUNCE else displayed,
            unitOnScale = if (ounces) WeightUnit.OUNCE else WeightUnit.GRAM,
            batteryPercent = batteryPercent(value[15].toInt() and 0xff),
        )
    }

    override fun tareCommand(): ByteArray = byteArrayOf(CMD_TARE)

    /** The scale only cycles the unit round: grams, ounces and back. */
    override fun unitCommand(unit: WeightUnit): ByteArray? =
        if (unit == WeightUnit.GRAM) byteArrayOf(CMD_TOGGLE_UNIT) else null

    override val unitCommandIsToggle = true

    /** The battery arrives as a raw level, the bounds are fixed for the model. */
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
    private const val CMD_TOGGLE_UNIT: Byte = 0x55
    private const val GRAMS_PER_OUNCE = 28.349523f
}
