package com.pourista.grind

import androidx.annotation.StringRes
import com.pourista.R

/**
 * Для чего годится помол такого размера.
 *
 * Полосы в микронах взяты у того же источника, что и шкалы кофемолок, поэтому
 * подпись согласована с пересчётом. Нужна она для проверки на глаз: если
 * вместо эспрессо в ответе оказалась турка, значит выбрана не та кофемолка.
 */
enum class BrewUse(
    private val from: Int,
    private val to: Int,
    @StringRes val labelRes: Int,
) {
    TURKISH(40, 220, R.string.grind_use_turkish),
    ESPRESSO(180, 380, R.string.grind_use_espresso),
    MOKA(360, 660, R.string.grind_use_moka),
    V60(400, 700, R.string.grind_use_v60),
    POUR_OVER(410, 930, R.string.grind_use_pour_over),
    FRENCH_PRESS(690, 1300, R.string.grind_use_french_press),
    COLD_BREW(800, 1400, R.string.grind_use_cold_brew);

    companion object {
        /** Способы, которым такой помол подходит. Больше трёх не показываем. */
        fun forMicrons(microns: Double): List<BrewUse> =
            entries.filter { microns >= it.from && microns <= it.to }.take(3)
    }
}
