package com.pourista.scale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор пакетов у всех известных весов. Протоколы, кроме Futula, на живом
 * железе не проверялись — тесты держат хотя бы раскладку байтов.
 */
class ScaleDriversTest {

    @Test
    fun `драйвер выбирается по имени устройства`() {
        assertSame(FutulaDriver, ScaleDrivers.forName("LFSmart Scale"))
        assertSame(FelicitaDriver, ScaleDrivers.forName("FELICITA ARC"))
        assertSame(BookooDriver, ScaleDrivers.forName("bookoo_sc"))
        assertSame(DecentScaleDriver, ScaleDrivers.forName("Decent Scale"))
        assertSame(TimemoreDotDriver, ScaleDrivers.forName("DOT-1234"))
        assertSame(TimemoreDotDriver, ScaleDrivers.forName("TES017"))
        // Имя из эфира у весов владельца — с заглавными буквами.
        assertSame(TimemoreDotDriver, ScaleDrivers.forName("TIMEMORE_Dot"))
        assertSame(TimemoreDotDriver, ScaleDrivers.forName("TIMEMORE Basic3"))
        assertSame(BookooDriver, ScaleDrivers.forName("BOOKOO_SC"))
        // Black Mirror второго поколения говорит другим протоколом.
        assertNull(ScaleDrivers.forName("TIMEMORE Scale"))
        // «dot» посреди чужого имени за весы не считаем.
        assertNull(ScaleDrivers.forName("Bluetooth dot speaker"))
        assertNull(ScaleDrivers.forName("Мои наушники"))
        assertNull(ScaleDrivers.forName(null))
    }

    @Test
    fun `тестовая поддержка помечена у всех, кроме Futula`() {
        assertEquals(false, FutulaDriver.experimental)
        ScaleDrivers.all.filter { it !== FutulaDriver }.forEach {
            assertTrue(it.title, it.experimental)
        }
    }

    @Test
    fun `Futula отдаёт вес младшим байтом вперёд и знак отдельно`() {
        val packet = ByteArray(9)
        packet[3] = 0xE2.toByte()   // 1250 — это 125,0 г
        packet[4] = 0x04
        val reading = FutulaDriver.parseWeight(packet)
        assertNotNull(reading)
        assertEquals(125f, reading!!.grams, 0.01f)

        packet[5] = 1               // тот же вес, но отрицательный
        assertEquals(-125f, FutulaDriver.parseWeight(packet)!!.grams, 0.01f)
    }

    @Test
    fun `Felicita читает вес цифрами ASCII`() {
        val reading = FelicitaDriver.parseWeight(
            felicitaPacket(sign = '+', digits = "001234", unit = "g ", battery = 158)
        )
        assertNotNull(reading)
        assertEquals(12.34f, reading!!.grams, 0.001f)
        assertEquals(WeightUnit.GRAM, reading.unitOnScale)
        assertEquals(100, reading.batteryPercent)

        val negative = FelicitaDriver.parseWeight(
            felicitaPacket(sign = '-', digits = "000050", unit = "g ", battery = 129)
        )
        assertEquals(-0.5f, negative!!.grams, 0.001f)
        assertEquals(0, negative.batteryPercent)
    }

    @Test
    fun `Felicita не верит короткому и нечисловому пакету`() {
        assertNull(FelicitaDriver.parseWeight(ByteArray(10)))
        assertNull(FelicitaDriver.parseWeight(felicitaPacket('+', "00A234", "g ", 140)))
    }

    @Test
    fun `Bookoo читает три байта старшим вперёд`() {
        val packet = ByteArray(20)
        packet[0] = 0x03            // номер изделия и тип пакета
        packet[1] = 0x0B
        packet[6] = '+'.code.toByte()
        packet[8] = 0x04
        packet[9] = 0xD2.toByte()   // 1234 — это 12,34 г
        packet[13] = 77
        val reading = BookooDriver.parseWeight(packet)
        assertNotNull(reading)
        assertEquals(12.34f, reading!!.grams, 0.001f)
        assertEquals(77, reading.batteryPercent)

        packet[6] = '-'.code.toByte()
        assertEquals(-12.34f, BookooDriver.parseWeight(packet)!!.grams, 0.001f)
    }

    @Test
    fun `Decent читает только пакеты веса`() {
        val stable = byteArrayOf(0x03, 0xCE.toByte(), 0x00, 0x7B)   // 123 — это 12,3 г
        assertEquals(12.3f, DecentScaleDriver.parseWeight(stable)!!.grams, 0.001f)

        val changing = byteArrayOf(0x03, 0xCA.toByte(), 0xFF.toByte(), 0x85.toByte())
        assertEquals(-12.3f, DecentScaleDriver.parseWeight(changing)!!.grams, 0.001f)

        // Нажатие кнопки на весах приходит той же характеристикой.
        assertNull(DecentScaleDriver.parseWeight(byteArrayOf(0x03, 0xAA.toByte(), 0x01, 0x00)))
    }

    @Test
    fun `команда Decent подписывается контрольной суммой`() {
        val command = DecentScaleDriver.tareCommand()
        assertEquals(7, command.size)
        var checksum = 0
        command.dropLast(1).forEach { checksum = checksum xor (it.toInt() and 0xff) }
        assertEquals(checksum.toByte(), command.last())

        // Счётчик тары растёт, иначе весы считают команду повтором предыдущей.
        assertTrue(DecentScaleDriver.tareCommand()[3] != command[3])
    }

    @Test
    fun `Timemore Dot читает вес из кадра с контрольной суммой`() {
        val weight = timemoreFrame(command = 0x01, data = byteArrayOf(0, 0, 0x04, 0xD2.toByte(), 0, 0, 0, 0))
        assertEquals(123.4f, TimemoreDotDriver.parseWeight(weight)!!.grams, 0.01f)

        val negative = timemoreFrame(
            command = 0x01,
            data = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFB.toByte(), 0x2E, 0, 0, 0, 0),
        )
        assertEquals(-123.4f, TimemoreDotDriver.parseWeight(negative)!!.grams, 0.01f)
    }

    @Test
    fun `Timemore Dot отличает заряд от веса`() {
        val battery = timemoreFrame(command = 0x05, data = byteArrayOf(0x00, 84))
        assertNull(TimemoreDotDriver.parseWeight(battery))
        assertEquals(84, TimemoreDotDriver.parseBattery(battery))

        val weight = timemoreFrame(command = 0x01, data = ByteArray(8))
        assertNull(TimemoreDotDriver.parseBattery(weight))
    }

    @Test
    fun `Timemore Dot разбирает кадры с живых весов`() {
        // Записи владельца весов из issue #2: пустые весы, монета 5 г,
        // отрицательный вес после снятия груза. CRC весы шлют нулями.
        assertEquals(0f, dotWeight("A55A010100090000000000000000000000"), 0.01f)
        assertEquals(5f, dotWeight("A55A010100090000003200000000000000"), 0.01f)
        assertEquals(-4.3f, dotWeight("A55A01010009FFFFFFD500000000000000"), 0.01f)
        assertEquals(110.7f, dotWeight("A55A010100090000045303E70000000000"), 0.01f)
    }

    @Test
    fun `Timemore Dot берёт скорость влива из кадра веса`() {
        // Кадры из записи протокола с живых весов: покой, разгон влива и его
        // конец. Скорость лежит следом за весом, в десятых грамма в секунду.
        val idle = dotReading("A55A01010009000008D100000000000000")
        assertEquals(225.7f, idle.grams, 0.01f)
        assertEquals(0f, idle.flowRate!!, 0.01f)

        val pouring = dotReading("A55A0101000900000956007A0000000000")
        assertEquals(239.0f, pouring.grams, 0.01f)
        assertEquals(12.2f, pouring.flowRate!!, 0.01f)

        val trailing = dotReading("A55A0101000900000D54000E0000000000")
        assertEquals(341.2f, trailing.grams, 0.01f)
        assertEquals(1.4f, trailing.flowRate!!, 0.01f)

        // Груз положили разом — весы упирают скорость в потолок, 99,9 г/с.
        // Драйвер отдаёт как есть: правдоподобие проверяет движок.
        assertEquals(99.9f, dotReading("A55A010100090000045303E70000000000").flowRate!!, 0.01f)

        // Воду снимают с весов — скорость уходит в минус.
        val negative = timemoreFrame(
            command = 0x01,
            data = byteArrayOf(0, 0, 0, 0, 0xFF.toByte(), 0xF6.toByte(), 0, 0, 0),
        )
        assertEquals(-1f, TimemoreDotDriver.parseWeight(negative)!!.flowRate!!, 0.01f)

        // В коротком кадре скорости нет — и выдумывать её нечем.
        val short = timemoreFrame(command = 0x01, data = ByteArray(4))
        assertNull(TimemoreDotDriver.parseWeight(short)!!.flowRate)
    }

    @Test
    fun `Timemore Dot берёт заряд из хвоста пакета с весом`() {
        // Заряд весы дописывают вторым кадром к первому, отдельным не шлют.
        val packet = ScaleDrivers.hexToBytes(
            "A55A010100090000003200000000000000" + "A55A0105000203550000"
        )
        val reading = TimemoreDotDriver.parseWeight(packet)
        assertNotNull(reading)
        assertEquals(5f, reading!!.grams, 0.01f)
        assertEquals(85, reading.batteryPercent)
        assertEquals(85, TimemoreDotDriver.parseBattery(packet))
    }

    @Test
    fun `Timemore Dot шлёт команды в отдельную характеристику`() {
        // fff1 только уведомляет: команды туда не уходят.
        assertEquals(bluetoothUuid("fff2"), TimemoreDotDriver.commandCharacteristic)
        assertEquals(bluetoothUuid("fff1"), TimemoreDotDriver.weightCharacteristic)
    }

    @Test
    fun `Timemore Dot просит граммы и обычный режим`() {
        assertEquals("a55a0306000100e8a7", TimemoreDotDriver.unitCommand(WeightUnit.GRAM)!!.toHex())
        assertEquals(
            "a55a030800020100eb31",
            TimemoreDotDriver.onConnectCommands().single().toHex(),
        )
    }

    @Test
    fun `Timemore Dot не верит чужому заголовку`() {
        val alien = byteArrayOf(0x01, 0x02, 0x01, 0x01, 0x00, 0x08, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        assertNull(TimemoreDotDriver.parseWeight(alien))
    }

    @Test
    fun `команда Timemore Dot заканчивается CRC`() {
        val command = TimemoreDotDriver.tareCommand()
        assertEquals(8, command.size)
        val crc = TimemoreDotDriver.crc16(command.dropLast(2).toByteArray())
        assertEquals((crc shr 8 and 0xff).toByte(), command[6])
        assertEquals((crc and 0xff).toByte(), command[7])
    }

    private fun dotWeight(hex: String): Float = dotReading(hex).grams

    private fun dotReading(hex: String): WeightReading =
        TimemoreDotDriver.parseWeight(ScaleDrivers.hexToBytes(hex))!!

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /** Кадр Timemore: заголовок, код, номер команды, длина, данные и CRC. */
    private fun timemoreFrame(command: Int, data: ByteArray): ByteArray {
        val body = byteArrayOf(
            0xA5.toByte(), 0x5A, 0x02, command.toByte(),
            (data.size shr 8).toByte(), (data.size and 0xff).toByte(),
        ) + data
        val crc = TimemoreDotDriver.crc16(body)
        return body + byteArrayOf((crc shr 8 and 0xff).toByte(), (crc and 0xff).toByte())
    }

    private fun felicitaPacket(sign: Char, digits: String, unit: String, battery: Int): ByteArray {
        val packet = ByteArray(18)
        packet[2] = sign.code.toByte()
        digits.forEachIndexed { index, char -> packet[3 + index] = char.code.toByte() }
        unit.forEachIndexed { index, char -> packet[9 + index] = char.code.toByte() }
        packet[15] = battery.toByte()
        return packet
    }
}
