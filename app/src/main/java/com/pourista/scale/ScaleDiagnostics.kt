package com.pourista.scale

import android.os.SystemClock

/**
 * A log of the scale protocol: what comes from the device and how the app understood it.
 * Needed when a model will not start: the raw packets show where the protocol and the driver
 * parted ways.
 *
 * Device addresses are not written down: they are of no use for reading a protocol, and the
 * file goes to a stranger.
 */
class ScaleDiagnostics(header: List<String>) {

    private val startedAt = SystemClock.elapsedRealtime()
    private val lines = mutableListOf<String>()

    @Volatile
    var packetCount: Int = 0
        private set

    init {
        synchronized(lines) { lines += header }
    }

    fun note(text: String) = add(text)

    /** A line with a timestamp from the start of the recording. */
    fun event(text: String) = add("%s  %s".format(stamp(), text))

    /** A packet from the device: time, characteristic, raw bytes and the parse. */
    fun packet(characteristic: String, value: ByteArray, parsed: String) {
        packetCount++
        if (packetCount > MAX_PACKETS) return
        add("%s  %s  %s  → %s".format(stamp(), shortUuid(characteristic), value.toHex(), parsed))
        if (packetCount == MAX_PACKETS) add("… no more packets from here, there are enough already")
    }

    /** A command sent to the scale. */
    fun command(characteristic: String, value: ByteArray, title: String) =
        add("%s  → %s  %s  (%s)".format(stamp(), shortUuid(characteristic), value.toHex(), title))

    fun text(): String = synchronized(lines) { lines.joinToString("\n") }

    private fun add(line: String) {
        synchronized(lines) { lines += line }
    }

    private fun stamp(): String {
        val ms = SystemClock.elapsedRealtime() - startedAt
        return "%3d.%03d".format(ms / 1000, ms % 1000)
    }

    private companion object {
        /** Beyond this the file is unreadable, and a protocol shows itself in a hundred packets. */
        const val MAX_PACKETS = 2_000

        fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

        /** A full UUID takes half a line, while they differ in four characters. */
        fun shortUuid(uuid: String): String {
            val text = uuid.lowercase()
            return if (text.endsWith("-0000-1000-8000-00805f9b34fb") && text.startsWith("0000")) {
                text.substring(4, 8)
            } else {
                text
            }
        }
    }
}
