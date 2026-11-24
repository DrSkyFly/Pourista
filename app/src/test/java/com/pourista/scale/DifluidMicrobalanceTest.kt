package com.pourista.scale

import com.pourista.brew.FlowRate
import com.pourista.brew.FlowSmoothing
import org.junit.Assert.*
import org.junit.Test

class DifluidMicrobalanceTest {
    private val ti = DifluidMicrobalanceTiDriver
    private val classic = DifluidMicrobalanceDriver
    private fun hex(s: String) = ScaleDrivers.hexToBytes(s)

    // Записи из difluid-korea/difluid-ble-guide, microbalance/protocol.md (§4, §5).
    private val full = hex("DF DF 03 00 0D 00 00 0F 64 00 00 00 00 00 07 57 88 00 27")
    private val zero = hex("DF DF 03 00 0D 00 00 00 00 00 00 00 00 00 07 60 E8 00 1D")
    private val battery = hex("DF DF 03 05 08 05 3F 00 00 00 00 00 00 12")

    @Test
    fun capturedTiAdvertisingNameSelectsTiService() {
        assertEquals(bluetoothUuid("00dd"), ScaleDrivers.forName("Mb Ti 123456")?.service)
        assertSame(ti, ScaleDrivers.forName("DiFluid Microbalance Ti"))
        assertSame(classic, ScaleDrivers.forName("Microbalance"))
        assertEquals(bluetoothUuid("00ee"), classic.service)
        assertEquals(bluetoothUuid("aa01"), ti.weightCharacteristic)
        assertEquals(ti.weightCharacteristic, ti.commandCharacteristic)
    }

    @Test
    fun advertisementAndScanResponseIdentifyTheScaleWithoutManufacturerFingerprint() {
        assertSame(ti, ScaleDrivers.forAdvertisement(null, null, listOf(bluetoothUuid("00dd"))))
        assertSame(classic, ScaleDrivers.forAdvertisement(null, null, listOf(bluetoothUuid("00ee"))))
        assertSame(ti, ScaleDrivers.forAdvertisement("Mb Ti 123456", "Microbalance", emptyList()))
        assertSame(ti, ScaleDrivers.forAdvertisement(null, "MB TI", emptyList()))
        assertNull(ScaleDrivers.forAdvertisement("DiFluid R2 123456", null, emptyList()))
        assertSame(FutulaDriver, ScaleDrivers.forAdvertisement("LFSmart Scale", null, emptyList()))
        assertSame(TimemoreDotDriver, ScaleDrivers.forAdvertisement(null, "TIMEMORE_Dot", emptyList()))
    }

    @Test
    fun bothModelsDecodeCapturedSensorAndStatusFrames() {
        for (model in listOf(ti, classic)) {
            assertEquals(394f, model.parseWeight(full)!!.grams, 0f)
            assertEquals(0f, model.parseWeight(zero)!!.grams, 0f)
            assertEquals(63, model.parseBattery(battery))
            assertNull(model.parseWeight(battery))
            assertNull(model.parseBattery(full))
            val frames = model.frames(battery + full + zero)
            assertEquals(3, frames.size)
            assertArrayEquals(battery, frames[0])
            assertArrayEquals(full, frames[1])
            assertArrayEquals(zero, frames[2])
        }
    }

    @Test
    fun signedAndOunceFixturesAreConvertedToGrams() {
        // Синтетические кадры: источники не публикуют запись отрицательного веса.
        val negative = hex("DF DF 03 00 0D FF FF FF 85 00 00 00 00 00 00 00 00 00 50")
        val ounce = hex("DF DF 03 00 0D 00 00 03 E8 00 00 00 00 00 00 00 00 01 BA")
        assertEquals(-12.3f, ti.parseWeight(negative)!!.grams, 0.001f)
        assertEquals(28.3495f, ti.parseWeight(ounce)!!.grams, 0.001f)
        assertEquals(WeightUnit.OUNCE, ti.parseWeight(ounce)!!.unitOnScale)
    }

    @Test
    fun brewFlowUsesWeightGainWhenScaleTimerIsIdle() {
        val halfGram = hex("DF DF 03 00 0D 00 00 00 05 00 00 00 00 00 00 00 00 00 D3")
        val flow = FlowRate(smoothing = FlowSmoothing.NONE)
        val first = ti.parseWeight(zero)!!
        val next = ti.parseWeight(halfGram)!!
        flow.onSample(first.grams, first.flowRate, 0)
        assertEquals(0f, flow.onSample(first.grams, first.flowRate, 250), 0f)
        assertEquals(1f, flow.onSample(next.grams, next.flowRate, 500), 0.001f)
    }

    @Test
    fun damagedTruncatedOrUnrelatedFramesNeverUpdateWeight() {
        for (size in 0 until full.size) assertNull(ti.parseWeight(full.copyOf(size)))
        assertNull(ti.parseWeight(full + 0))
        for (index in full.indices) {
            val damaged = full.copyOf()
            damaged[index] = (damaged[index].toInt() xor 1).toByte()
            assertNull(ti.parseWeight(damaged))
        }
        // Сумма сохранена: отклонение должно зависеть и от типа, длины и единицы.
        for (index in listOf(0, 1, 2, 3, 4, 17)) {
            val other = full.copyOf()
            other[index] = (other[index] + 2).toByte()
            other[other.lastIndex] = (other.last() + 2).toByte()
            assertNull(ti.parseWeight(other))
        }
        assertNull(ti.parseBattery(battery.copyOf(7)))
        assertNull(ti.parseBattery(battery.copyOf().apply { this[lastIndex] = 0 }))
    }

    @Test
    fun startupAndTareMatchDocumentedControlFrames() {
        assertArrayEquals(hex("DF DF 01 04 01 00 C4"), ti.unitCommand(WeightUnit.GRAM))
        assertEquals(2, ti.onConnectCommands().size)
        assertArrayEquals(hex("DF DF 01 00 01 01 C1"), ti.onConnectCommands()[0])
        assertArrayEquals(hex("DF DF 03 05 00 C6"), ti.onConnectCommands()[1])
        assertArrayEquals(hex("DF DF 03 02 01 01 C5"), ti.tareCommand())
    }
}
