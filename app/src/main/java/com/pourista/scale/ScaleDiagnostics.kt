package com.pourista.scale

import android.os.SystemClock

/**
 * Журнал протокола весов: что приходит с устройства и как приложение это
 * поняло. Нужен, когда модель не заводится: по сырым пакетам видно, где
 * разъехались протокол и драйвер.
 *
 * Адреса устройств не записываем: для разбора протокола они не нужны, а файл
 * человек отправляет постороннему.
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

    /** Строка с отметкой времени от начала записи. */
    fun event(text: String) = add("%s  %s".format(stamp(), text))

    /** Пакет с устройства: время, характеристика, сырые байты и разбор. */
    fun packet(characteristic: String, value: ByteArray, parsed: String) {
        packetCount++
        if (packetCount > MAX_PACKETS) return
        add("%s  %s  %s  → %s".format(stamp(), shortUuid(characteristic), value.toHex(), parsed))
        if (packetCount == MAX_PACKETS) add("… дальше пакеты не пишем, их уже достаточно")
    }

    /** Команда, ушедшая на весы. */
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
        /** Больше этого файл читать невозможно, а протокол виден и на сотне пакетов. */
        const val MAX_PACKETS = 2_000

        fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

        /** Полный UUID занимает полстроки, а различаются они четырьмя знаками. */
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
