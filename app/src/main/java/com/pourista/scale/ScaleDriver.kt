package com.pourista.scale

import java.util.UUID

/** Короткий 16-битный идентификатор в полную форму Bluetooth SIG. */
fun bluetoothUuid(short: String): UUID =
    UUID.fromString("0000${short.lowercase().padStart(4, '0')}-0000-1000-8000-00805f9b34fb")

/** Что удалось вычитать из одного пакета весов. */
data class WeightReading(
    val grams: Float,
    /** Единица на экране весов, если пакет её сообщает. */
    val unitOnScale: WeightUnit? = null,
    /** Заряд в процентах, если он приходит тем же пакетом. */
    val batteryPercent: Int? = null,
)

/**
 * Протокол одной модели весов.
 *
 * Приложение говорит с весами через драйвер: по нему ищет устройство в эфире,
 * подписывается на нужную характеристику, разбирает пакет и шлёт команды. Всё,
 * что специфично для модели, живёт внутри драйвера и больше нигде.
 */
interface ScaleDriver {

    /** Название модели для человека. */
    val title: String

    /**
     * Протокол написан по открытым реализациям и на живом железе не проверялся.
     * Такие весы приложение поддерживает в тестовом режиме.
     */
    val experimental: Boolean get() = true

    /**
     * Куски имени устройства в эфире. По ним идёт поиск (подстрокой) и по ним
     * же выбирается драйвер, когда весы нашлись.
     */
    val nameFragments: List<String>

    val service: UUID

    /** Характеристика, с которой приходит вес. */
    val weightCharacteristic: UUID

    /** Куда писать команды. Null — весы команд не принимают. */
    val commandCharacteristic: UUID?

    /** Отдельная служба заряда, если он не приходит вместе с весом. */
    val batteryService: UUID? get() = null
    val batteryCharacteristic: UUID? get() = null

    fun matches(deviceName: String): Boolean =
        nameFragments.any { deviceName.contains(it, ignoreCase = true) }

    /** Разбор пакета веса. Null — пакет не про вес или испорчен. */
    fun parseWeight(value: ByteArray): WeightReading?

    /** Заряд, если он приходит отдельным пакетом в ту же характеристику. */
    fun parseBattery(value: ByteArray): Int? = null

    /** Обнуление показаний. Null — весы этого не умеют. */
    fun tareCommand(): ByteArray? = null

    /** Что отправить сразу после подключения: часы, подсветка, режим. */
    fun onConnectCommands(): List<ByteArray> = emptyList()

    /** Перевести весы в нужную единицу. Null — единица не переключается. */
    fun unitCommand(unit: WeightUnit): ByteArray? = null
}

/**
 * Известные приложению весы.
 *
 * Протоколы разобраны по открытым реализациям и написаны заново; на живом
 * железе проверен только Futula. Остальные ждут подтверждения от владельцев —
 * см. README.
 */
object ScaleDrivers {

    val all: List<ScaleDriver> = listOf(
        FutulaDriver,
        FelicitaDriver,
        BookooDriver,
        DecentScaleDriver,
        TimemoreDotDriver,
    )

    /** Всё, чем весы могут представиться: с этим списком идём сканировать. */
    val nameFragments: Array<String> =
        all.flatMap { it.nameFragments }.distinct().toTypedArray()

    fun forName(deviceName: String?): ScaleDriver? {
        val name = deviceName?.takeIf { it.isNotBlank() } ?: return null
        return all.firstOrNull { it.matches(name) }
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.filterNot { it.isWhitespace() }
        require(clean.length % 2 == 0) { "Нечётная длина hex-строки: $hex" }
        return ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
