package com.pourista.scale

import java.util.UUID

/**
 * Decent Scale.
 *
 * Вес приходит коротким пакетом: во втором байте признак устойчивости, дальше
 * два байта старшим вперёд в десятых долях грамма. Команды подписываются
 * контрольной суммой — исключающим ИЛИ по предыдущим байтам, а у тары есть
 * ещё и счётчик, иначе весы считают её повтором предыдущей.
 *
 * Служба и характеристика веса совпадают с Futula, поэтому драйвер выбирается
 * по имени устройства, а не по идентификаторам.
 *
 * Протокол написан по открытым реализациям, на железе не проверялся.
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

    /** Включаем подсветку веса и таймера: без неё весы гасят экран. */
    override fun onConnectCommands(): List<ByteArray> =
        listOf(command(byteArrayOf(HEADER, 0x0a, 0x01, 0x01, 0x00, 0x00)))

    /** Без напоминаний весы засыпают посреди пролива. */
    override fun heartbeatCommands(): List<ByteArray> =
        listOf(command(byteArrayOf(HEADER, 0x0a, 0x03, 0xff.toByte(), 0xff.toByte(), 0x00)))

    override val heartbeatIntervalMs = 2_000L

    /** Первую команду весы часто теряют, поэтому каждую шлём дважды. */
    override val commandRepeats = 2

    /** Дописывает к шести байтам контрольную сумму. */
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
