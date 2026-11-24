package com.pourista.scale

import java.nio.ByteBuffer

object DifluidMicrobalanceDriver : DifluidDriver("DiFluid Microbalance", "00ee", listOf("microbalance"))

object DifluidMicrobalanceTiDriver : DifluidDriver(
    "DiFluid Microbalance Ti", "00dd", listOf("microbalance ti", "mb ti"),
)

sealed class DifluidDriver(
    final override val title: String,
    shortService: String,
    final override val nameFragments: List<String>,
) : ScaleDriver {
    final override val service = bluetoothUuid(shortService)
    final override val weightCharacteristic = bluetoothUuid("aa01")
    final override val commandCharacteristic = weightCharacteristic

    // Одна нотификация может содержать несколько полных кадров. Хвост с неверной
    // длиной передаём в диагностику, но не принимаем в качестве измерения.
    fun frames(value: ByteArray): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < value.size) {
            val end = if (value.size - offset >= 6) {
                (offset + 6 + (value[offset + 4].toInt() and 0xff)).coerceAtMost(value.size)
            } else value.size
            frames += value.copyOfRange(offset, end)
            offset = end
        }
        return frames
    }

    private fun valid(value: ByteArray, cmd: Int, length: Int): Boolean =
        value.size == length + 6 &&
            value[0] == HEADER && value[1] == HEADER &&
            value[2] == 3.toByte() && value[3].toInt() == cmd && value[4].toInt() == length &&
            checksum(value, value.lastIndex) == value.last()

    override fun parseWeight(value: ByteArray): WeightReading? {
        if (!valid(value, 0, 13)) return null
        val raw = ByteBuffer.wrap(value).getInt(5)
        // Поле скорости бывает нулевым даже при изменении веса. BrewEngine
        // рассчитывает г/с по тем же отсчётам, что использует для графика веса.
        return when (value[17].toInt()) {
            0 -> WeightReading(raw / 10f, unitOnScale = WeightUnit.GRAM)
            1 -> WeightReading(raw / 1000f * 28.349523f, unitOnScale = WeightUnit.OUNCE)
            else -> null
        }
    }

    override fun parseBattery(value: ByteArray): Int? =
        if (valid(value, 5, 8)) (value[6].toInt() and 0xff).takeIf { it <= 100 } else null

    override fun tareCommand() = command(3, 2, 1)

    override fun unitCommand(unit: WeightUnit) =
        if (unit == WeightUnit.GRAM) command(1, 4, 0) else null

    override fun onConnectCommands() = listOf(command(1, 0, 1), command(3, 5))

    private fun command(group: Int, code: Int, argument: Int? = null): ByteArray {
        val data = if (argument == null) byteArrayOf() else byteArrayOf(argument.toByte())
        val body = byteArrayOf(HEADER, HEADER, group.toByte(), code.toByte(), data.size.toByte()) + data
        return body + checksum(body, body.size)
    }

    private fun checksum(bytes: ByteArray, count: Int): Byte {
        var sum = 0
        for (i in 0 until count) sum += bytes[i].toInt()
        return sum.toByte()
    }

    private companion object {
        val HEADER = 0xdf.toByte()
    }
}
