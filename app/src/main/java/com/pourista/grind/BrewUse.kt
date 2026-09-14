package com.pourista.grind

import androidx.annotation.StringRes
import com.pourista.R

/**
 * What a grind of this size is good for.
 *
 * The micron bands are taken from the same source as the grinder scales, so the label agrees with
 * the conversion. It is there for an eye check: if a cezve turns up in the answer instead of
 * espresso, the wrong grinder was picked.
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
        /** The methods such a grind suits. We show no more than three. */
        fun forMicrons(microns: Double): List<BrewUse> =
            entries.filter { microns >= it.from && microns <= it.to }.take(3)
    }
}
