package com.pourista.ui.brew

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.pourista.R

/**
 * Sound and vibration at the step boundaries: there is no time to look at the screen while
 * pouring, the cue has to arrive by ear and through the hand.
 *
 * The cues lie in res/raw as ready files. Synthesising them on the spot did not work out: a pure
 * tone sounds sterile, while a live bell or the click of a timer is recognised by the ear
 * instantly, even with the phone half turned away.
 */
class BrewCuePlayer(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * The alarm stream rather than the notification one: a kitchen is noisy, and the notification
     * volume is often turned down. SoundPool keeps the cues unpacked in memory, so there is no
     * delay for preparing the file between the event and the sound.
     */
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val loaded = mutableSetOf<Int>()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loaded += sampleId
            } else {
                Log.w(TAG, "Could not load cue $sampleId, code $status")
            }
        }
    }

    private val stepSound = load(context, R.raw.cue_step)
    private val countdownSound = load(context, R.raw.cue_countdown)
    private val stopSound = load(context, R.raw.cue_stop)
    private val finishSound = load(context, R.raw.cue_finish)

    fun stepChange(sound: Boolean, haptic: Boolean) {
        if (haptic) vibrate(longArrayOf(0, 120, 80, 120))
        if (sound) play(stepSound)
    }

    fun countdown(sound: Boolean, haptic: Boolean) {
        if (haptic) vibrate(longArrayOf(0, 60))
        if (sound) play(countdownSound)
    }

    /** Recognisably unlike the rest: "the target is close, close the kettle". */
    fun nearTarget(sound: Boolean, haptic: Boolean) {
        if (haptic) vibrate(longArrayOf(0, 50, 60, 50, 60, 50))
        if (sound) play(stopSound)
    }

    /** The plan is played out, the water is draining: the same bell, but a vibration of its own. */
    fun planFinished(sound: Boolean, haptic: Boolean) {
        if (haptic) vibrate(longArrayOf(0, 150, 100, 150))
        if (sound) play(stepSound)
    }

    fun finished(sound: Boolean, haptic: Boolean) {
        if (haptic) vibrate(longArrayOf(0, 200, 120, 200, 120, 320))
        if (sound) play(finishSound)
    }

    fun release() {
        loaded.clear()
        pool.release()
    }

    private fun load(context: Context, @RawRes res: Int): Int =
        runCatching { pool.load(context, res, 1) }.getOrDefault(0)

    private fun play(sampleId: Int) {
        if (sampleId == 0) return
        // A cue that has not finished loading is better skipped than waited for: a hint a second
        // late only throws you off mid-pour.
        if (sampleId !in loaded) {
            Log.d(TAG, "Cue $sampleId is not loaded yet, skipping")
            return
        }
        pool.play(sampleId, VOLUME, VOLUME, 1, 0, 1f)
    }

    private fun vibrate(pattern: LongArray) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        // Full amplitude: the vibration has to get through a hand holding a kettle.
        val amplitudes = IntArray(pattern.size) { index ->
            if (index % 2 == 0) 0 else VibrationEffect.DEFAULT_AMPLITUDE
        }
        runCatching {
            device.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
        }.onFailure {
            runCatching { device.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
        }
    }

    private companion object {
        const val TAG = "BrewCues"

        /** The cues are short, but the countdown and "the target is close" can overlap. */
        const val MAX_STREAMS = 4
        const val VOLUME = 1f
    }
}

@Composable
fun rememberBrewCuePlayer(): BrewCuePlayer {
    val context = LocalContext.current
    val player = remember(context) { BrewCuePlayer(context) }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}
