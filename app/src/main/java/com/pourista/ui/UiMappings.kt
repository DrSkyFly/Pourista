package com.pourista.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import com.pourista.R
import com.pourista.brew.NextPourHint
import com.pourista.brew.Pace
import com.pourista.data.model.StepKind
import com.pourista.data.presets.FortySixStrength
import com.pourista.data.presets.FortySixTaste

@StringRes
fun StepKind.labelRes(): Int = when (this) {
    StepKind.BLOOM -> R.string.step_bloom
    StepKind.POUR -> R.string.step_pour
    StepKind.WAIT -> R.string.step_wait
    StepKind.SWIRL -> R.string.step_swirl
    StepKind.STIR -> R.string.step_stir
    StepKind.DRAWDOWN -> R.string.step_drawdown
    StepKind.PRESS -> R.string.step_press
}

fun StepKind.icon(): ImageVector = when (this) {
    StepKind.BLOOM -> Icons.Rounded.Spa
    StepKind.POUR -> Icons.Rounded.WaterDrop
    StepKind.WAIT -> Icons.Rounded.HourglassEmpty
    StepKind.SWIRL -> Icons.Rounded.Refresh
    StepKind.STIR -> Icons.Rounded.Sync
    StepKind.DRAWDOWN -> Icons.Rounded.Timer
    StepKind.PRESS -> Icons.Rounded.ArrowDownward
}

@StringRes
fun Pace.labelRes(isPour: Boolean): Int = when {
    !isPour -> R.string.pace_hold
    this == Pace.ON_TRACK -> R.string.pace_on_track
    this == Pace.TOO_FAST -> R.string.pace_too_fast
    else -> R.string.pace_too_slow
}

@StringRes
fun NextPourHint.labelRes(): Int = when (this) {
    NextPourHint.SAME -> R.string.hint_next_same
    NextPourHint.FASTER -> R.string.hint_next_faster
    NextPourHint.SLOWER -> R.string.hint_next_slower
}

@StringRes
fun FortySixTaste.labelRes(): Int = when (this) {
    FortySixTaste.SWEET -> R.string.four_six_taste_sweet
    FortySixTaste.SWEET_NORMAL -> R.string.four_six_taste_sweet_normal
    FortySixTaste.NORMAL -> R.string.four_six_taste_normal
    FortySixTaste.NORMAL_ACID -> R.string.four_six_taste_normal_acid
    FortySixTaste.ACID -> R.string.four_six_taste_acid
}

@StringRes
fun FortySixStrength.labelRes(): Int = when (this) {
    FortySixStrength.LOWER -> R.string.four_six_strength_lower
    FortySixStrength.LOW -> R.string.four_six_strength_low
    FortySixStrength.NORMAL -> R.string.four_six_strength_normal
    FortySixStrength.HIGH -> R.string.four_six_strength_high
    FortySixStrength.HIGHER -> R.string.four_six_strength_higher
}
