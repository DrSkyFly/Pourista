package com.pourista.scale

import java.util.Calendar
import java.util.UUID

/**
 * Протокол весов Futula Kitchen Scale 3, они же LEFU CK811.
 *
 * Идентификаторы служб, команды и раскладка пакета веса — свойства самого
 * устройства; все они проверены на живых весах: тара, заряд, смена единицы и
 * разбор веса, включая отрицательный. На других моделях работа не проверялась.
 */
object ScaleProtocol {

    val DEVICE_NAMES = listOf("LFSmart Scale", "LEFU-CK811")

    val SERVICE: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val COMMAND_CHARACTERISTIC: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    val WEIGHT_CHARACTERISTIC: UUID = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb")
    val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val BATTERY_CHARACTERISTIC: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    /** Обнуление показаний (тара). */
    const val COMMAND_TARE = "fd320000000000000000cf"

    /**
     * Пересчёт граммов в единицу отображения. Весы отдают вес в граммах всегда,
     * выбранная на них единица влияет только на их собственный экран.
     */

    data class WeightReading(val grams: Float, val unitOnScale: WeightUnit)

    /**
     * Разбор пакета характеристики веса: младший байт первым в позициях 3..4,
     * знак в позиции 5, единица измерения в позиции 8.
     */
    fun parseWeight(value: ByteArray): WeightReading? {
        if (value.size < 9) return null
        val raw = (value[4].toInt() and 0xff shl 8) or (value[3].toInt() and 0xff)
        val sign = if (value[5].toInt() > 0) -1f else 1f
        val unitOnScale = WeightUnit.fromScaleByte(value[8].toInt())
        return WeightReading(grams = raw / 10f * sign, unitOnScale = unitOnScale)
    }

    /**
     * Синхронизация часов весов. Структура пакета повторяет версию 1.x: меняются
     * только часы, минуты и секунды, остальные байты железо принимает как есть.
     */
    fun timeSyncCommand(calendar: Calendar = Calendar.getInstance()): String {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minutes = calendar.get(Calendar.MINUTE)
        val seconds = calendar.get(Calendar.SECOND)
        return byteArrayOf(
            0xF1.toByte(), 0x07, 0xE8.toByte(), 0x07, 0x05,
            hour.toByte(), minutes.toByte(), seconds.toByte(),
        ).joinToString("") { "%02x".format(it) }
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.filterNot { it.isWhitespace() }
        require(clean.length % 2 == 0) { "Нечётная длина hex-строки: $hex" }
        return ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
