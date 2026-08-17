package com.pourista.scale

/**
 * Единица, в которой весы отдают вес и показывают его на своём экране.
 *
 * Приложение считает всё в граммах: рецепты, цели шагов и скорость влива иначе
 * не имеют смысла. Единица нужна только для разговора с весами — понять, что
 * они переключились, и вернуть их обратно.
 */
enum class WeightUnit(val commandHex: String, val scaleByte: Int) {
    GRAM("fd000400000000000000f9", 4),
    OUNCE("fd000600000000000000fb", 6),
    MILLILITER_WATER("fd000700000000000000fa", 7),
    MILLILITER_MILK("fd000800000000000000f5", 8);

    companion object {
        fun fromScaleByte(byte: Int): WeightUnit =
            entries.firstOrNull { it.scaleByte == byte } ?: GRAM
    }
}
