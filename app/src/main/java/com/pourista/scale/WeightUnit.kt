package com.pourista.scale

/**
 * The unit the scale reports the weight in and shows on its own display.
 *
 * The app counts everything in grams: recipes, step targets and the flow rate make no sense
 * otherwise. The unit is only needed for talking to the scale — to see that it has switched
 * and to switch it back.
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
