package com.pourista.scale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Весы, добавленные по открытым описаниям протоколов: Acaia, Eureka Precisa,
 * Varia AKU и DiFluid. Живого железа нет ни у кого из нас, поэтому кадры в
 * тестах собраны по тем же описаниям — они держат раскладку байтов и команды.
 */
class NewScaleDriversTest {

    @Before
    fun resetDrivers() {
        ScaleDrivers.all.forEach { it.reset() }
    }

    @Test
    fun `драйвер выбирается по имени`() {
        assertTrue(ScaleDrivers.forName("LUNAR-1A2B3C") is AcaiaDriver)
        assertTrue(ScaleDrivers.forName("PYXIS-000123") is AcaiaDriver)
        assertTrue(ScaleDrivers.forName("PROCHBT001") is AcaiaDriver)
        assertSame(EurekaPrecisaDriver, ScaleDrivers.forName("CFS-9002"))
        assertSame(EurekaPrecisaDriver, ScaleDrivers.forName("LSJ-001"))
        assertSame(VariaAkuDriver, ScaleDrivers.forName("AKU-12345"))
        assertSame(DifluidMicrobalanceDriver, ScaleDrivers.forName("Microbalance"))
        // Чужое имя с теми же буквами весами не считаем.
        assertNull(ScaleDrivers.forName("Pearl Bluetooth Speaker"))
    }

    @Test
    fun `Acaia читает вес из кадра события`() {
        val reading = AcaiaClassicDriver.parseWeight(hex("EFDD0C0805D20400000100DB09"))
        assertNotNull(reading)
        assertEquals(123.4f, reading!!.grams, 0.01f)

        AcaiaClassicDriver.reset()
        val negative = AcaiaClassicDriver.parseWeight(hex("EFDD0C0805D20400000102DB0B"))
        assertEquals(-123.4f, negative!!.grams, 0.01f)
    }

    @Test
    fun `Acaia собирает кадр из двух пакетов`() {
        // Одно уведомление приносит половину кадра, вес появляется со вторым.
        assertNull(AcaiaClassicDriver.parseWeight(hex("EFDD0C0805D204")))
        val reading = AcaiaClassicDriver.parseWeight(hex("00000100DB09"))
        assertEquals(123.4f, reading!!.grams, 0.01f)
    }

    @Test
    fun `Acaia берёт заряд и единицу из настроек`() {
        assertNull(AcaiaClassicDriver.parseWeight(hex("EFDD080A5A02000000010000000D5A")))
        assertEquals(90, AcaiaClassicDriver.parseBattery(ByteArray(0)))

        // Унции на весах переводим в граммы сами: команды смены единицы нет.
        AcaiaClassicDriver.reset()
        AcaiaClassicDriver.parseWeight(hex("EFDD080A5A0500000001000000105A"))
        val ounces = AcaiaClassicDriver.parseWeight(hex("EFDD0C0805D20400000100DB09"))
        assertEquals(WeightUnit.OUNCE, ounces!!.unitOnScale)
        assertEquals(123.4f * 28.349523f, ounces.grams, 0.5f)
    }

    @Test
    fun `Acaia здоровается и шлёт пульс`() {
        assertEquals("efdd04000000", AcaiaClassicDriver.tareCommand()!!.toHex())
        assertEquals(
            listOf("efdd0b2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d683b", "efdd0c0900010102020503041506"),
            AcaiaClassicDriver.onConnectCommands().map { it.toHex() },
        )
        assertEquals(listOf("efdd0002000200"), AcaiaClassicDriver.heartbeatCommands().map { it.toHex() })

        // Pyxis представляется другими байтами и повторяет это каждый пульс.
        assertEquals(
            "efdd0b3031323334353637383930313233349a6d",
            AcaiaPyxisDriver.onConnectCommands().first().toHex(),
        )
        assertEquals(2, AcaiaPyxisDriver.heartbeatCommands().size)
    }

    @Test
    fun `Eureka Precisa читает вес младшим байтом вперёд`() {
        val packet = ByteArray(11)
        packet[7] = 0xD2.toByte()   // 1234 — это 123,4 г
        packet[8] = 0x04
        assertEquals(123.4f, EurekaPrecisaDriver.parseWeight(packet)!!.grams, 0.01f)

        packet[6] = 1               // тот же вес, но отрицательный
        assertEquals(-123.4f, EurekaPrecisaDriver.parseWeight(packet)!!.grams, 0.01f)

        assertEquals("aa023131", EurekaPrecisaDriver.tareCommand()!!.toHex())
    }

    @Test
    fun `Varia AKU держит знак в старшей половине байта`() {
        val packet = byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x30, 0x39)   // 12345 — это 123,45 г
        assertEquals(123.45f, VariaAkuDriver.parseWeight(packet)!!.grams, 0.001f)

        packet[3] = 0x10
        assertEquals(-123.45f, VariaAkuDriver.parseWeight(packet)!!.grams, 0.001f)

        // Пакеты не про вес пропускаем.
        assertNull(VariaAkuDriver.parseWeight(byteArrayOf(0x00, 0x02, 0x00, 0x00, 0x30, 0x39)))
        assertEquals("fa82010182", VariaAkuDriver.tareCommand()!!.toHex())
    }

    @Test
    fun `DiFluid читает вес четырьмя байтами и подписывает команды суммой`() {
        val packet = ByteArray(19)
        packet[0] = 0xDF.toByte()
        packet[1] = 0xDF.toByte()
        packet[3] = 0x00
        packet[7] = 0x04
        packet[8] = 0xD2.toByte()   // 1234 — это 123,4 г
        assertEquals(123.4f, DifluidMicrobalanceDriver.parseWeight(packet)!!.grams, 0.01f)

        assertEquals("dfdf03020101c5", DifluidMicrobalanceDriver.tareCommand()!!.toHex())
        assertEquals(
            "dfdf01040100c4",
            DifluidMicrobalanceDriver.unitCommand(WeightUnit.GRAM)!!.toHex(),
        )
        assertEquals(
            listOf("dfdf01000101c1"),
            DifluidMicrobalanceDriver.onConnectCommands().map { it.toHex() },
        )
    }

    @Test
    fun `Felicita переводит унции в граммы и только переключает единицу`() {
        val packet = ByteArray(18)
        packet[2] = '+'.code.toByte()
        "000100".forEachIndexed { index, char -> packet[3 + index] = char.code.toByte() }
        "oz".forEachIndexed { index, char -> packet[9 + index] = char.code.toByte() }
        packet[15] = 158.toByte()

        val reading = FelicitaDriver.parseWeight(packet)!!
        assertEquals(WeightUnit.OUNCE, reading.unitOnScale)
        assertEquals(28.35f, reading.grams, 0.01f)
        assertTrue(FelicitaDriver.unitCommandIsToggle)
        assertEquals("55", FelicitaDriver.unitCommand(WeightUnit.GRAM)!!.toHex())
    }

    @Test
    fun `Bookoo читает только пакеты веса`() {
        val packet = ByteArray(20)
        packet[0] = 0x03
        packet[1] = 0x0B
        packet[6] = '+'.code.toByte()
        packet[9] = 0x64
        assertEquals(1f, BookooDriver.parseWeight(packet)!!.grams, 0.001f)

        packet[1] = 0x0D
        assertNull(BookooDriver.parseWeight(packet))
    }

    @Test
    fun `Decent будит весы и повторяет команды`() {
        assertEquals(2, DecentScaleDriver.commandRepeats)
        assertEquals(2_000L, DecentScaleDriver.heartbeatIntervalMs)
        assertEquals(listOf("030a03ffff000a"), DecentScaleDriver.heartbeatCommands().map { it.toHex() })
    }

    private fun hex(value: String): ByteArray = ScaleDrivers.hexToBytes(value)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
