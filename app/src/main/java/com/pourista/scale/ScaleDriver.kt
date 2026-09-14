package com.pourista.scale

import java.util.UUID

/** A short 16-bit identifier into the full Bluetooth SIG form. */
fun bluetoothUuid(short: String): UUID =
    UUID.fromString("0000${short.lowercase().padStart(4, '0')}-0000-1000-8000-00805f9b34fb")

/** What could be read out of a single packet from the scale. */
data class WeightReading(
    val grams: Float,
    /**
     * Flow rate in grams per second, if the scale counts it itself. Null means this scale
     * does not send one, and it has to be counted from the weight gain.
     */
    val flowRate: Float? = null,
    /** The unit on the scale display, if the packet reports it. */
    val unitOnScale: WeightUnit? = null,
    /** Battery in percent, if it arrives in the same packet. */
    val batteryPercent: Int? = null,
)

/**
 * The protocol of one scale model.
 *
 * The app talks to a scale through a driver: by it the device is found on the air, the right
 * characteristic subscribed to, the packet parsed and the commands sent. Everything specific
 * to a model lives inside the driver and nowhere else.
 */
interface ScaleDriver {

    /** The model name for a person. */
    val title: String

    /**
     * The protocol is written from open implementations and has not been checked on live
     * hardware. The app supports such scales in beta.
     */
    val experimental: Boolean get() = true

    /**
     * Pieces of the device name on the air: by them the driver recognises its own scale.
     * Case does not matter — scales write their names any way they like.
     */
    val nameFragments: List<String>

    val service: UUID

    /** The characteristic the weight arrives from. */
    val weightCharacteristic: UUID

    /** Where to write commands. Null means the scale takes no commands. */
    val commandCharacteristic: UUID?

    /**
     * Write commands without acknowledgement. Some scales will not take them otherwise, and
     * if a characteristic can do only one of the two, the choice is its anyway.
     */
    val writeWithoutResponse: Boolean get() = false

    /** How many times to send every command: Decent loses the first one. */
    val commandRepeats: Int get() = 1

    /**
     * What to send every [heartbeatIntervalMs] while the scale is connected. Acaia goes
     * silent without it after a few seconds, Decent falls asleep.
     */
    fun heartbeatCommands(): List<ByteArray> = emptyList()

    val heartbeatIntervalMs: Long get() = 0L

    /** A separate battery service, for when it does not arrive together with the weight. */
    val batteryService: UUID? get() = null
    val batteryCharacteristic: UUID? get() = null

    fun matches(deviceName: String): Boolean =
        nameFragments.any { deviceName.contains(it, ignoreCase = true) }

    /** Parsing a weight packet. Null means the packet is not about weight, or is broken. */
    fun parseWeight(value: ByteArray): WeightReading?

    /** The battery, if it arrives in a separate packet into the same characteristic. */
    fun parseBattery(value: ByteArray): Int? = null

    /** Zeroing the readings. Null means the scale cannot do it. */
    fun tareCommand(): ByteArray? = null

    /** What to send right after connecting: the clock, the backlight, the mode. */
    fun onConnectCommands(): List<ByteArray> = emptyList()

    /** Switch the scale to the unit we need. Null means the unit does not switch. */
    fun unitCommand(unit: WeightUnit): ByteArray? = null

    /**
     * The unit command does not set a unit but cycles through them. Such a command must not
     * be sent blindly on connecting — only when the packet shows the scale is not in grams.
     */
    val unitCommandIsToggle: Boolean get() = false

    /** Forget the parsing state: called before every connection. */
    fun reset() {}
}

/**
 * The scales the app knows about.
 *
 * The protocols are read from open implementations and written anew; on live hardware only
 * Futula has been checked, and for Timemore the parsing was verified against a protocol
 * recording from an owner. The rest are beta and await confirmation, see README.
 */
object ScaleDrivers {

    val all: List<ScaleDriver> = listOf(
        FutulaDriver,
        FelicitaDriver,
        BookooDriver,
        DecentScaleDriver,
        TimemoreDotDriver,
        EurekaPrecisaDriver,
        VariaAkuDriver,
        DifluidMicrobalanceDriver,
        AcaiaClassicDriver,
        AcaiaPyxisDriver,
    )

    fun forName(deviceName: String?): ScaleDriver? {
        val name = deviceName?.takeIf { it.isNotBlank() } ?: return null
        return all.firstOrNull { it.matches(name) }
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.filterNot { it.isWhitespace() }
        require(clean.length % 2 == 0) { "Odd length of the hex string: $hex" }
        return ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
