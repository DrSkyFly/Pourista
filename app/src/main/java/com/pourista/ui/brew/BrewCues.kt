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
 * Звук и вибрация на границах шагов: во время пролива смотреть в экран некогда,
 * подсказка должна доходить на слух и через руку.
 *
 * Сигналы лежат готовыми файлами в res/raw. Синтезировать их на месте не вышло:
 * чистый тон звучит стерильно, а живой звонок или щелчок таймера узнаётся ухом
 * мгновенно, даже вполоборота к телефону.
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
     * Поток будильника, а не уведомлений: на кухне шумно, а громкость уведомлений
     * часто прикручена. SoundPool держит сигналы распакованными в памяти, поэтому
     * между событием и звуком нет задержки на подготовку файла.
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
                Log.w(TAG, "Не удалось загрузить сигнал $sampleId, код $status")
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

    /** Узнаваемо не похоже на остальные: «цель близко, закрывай чайник». */
    fun nearTarget(sound: Boolean, haptic: Boolean) {
        if (haptic) vibrate(longArrayOf(0, 50, 60, 50, 60, 50))
        if (sound) play(stopSound)
    }

    /** План отыгран, вода уходит: тот же звоночек, но своя вибрация. */
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
        // Сигнал, который не успел загрузиться, лучше пропустить, чем ждать:
        // подсказка, опоздавшая на секунду, во время пролива только сбивает.
        if (sampleId !in loaded) {
            Log.d(TAG, "Сигнал $sampleId ещё не загружен, пропускаем")
            return
        }
        pool.play(sampleId, VOLUME, VOLUME, 1, 0, 1f)
    }

    private fun vibrate(pattern: LongArray) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        // Полная амплитуда: вибрация должна пробиваться через руку с чайником.
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

        /** Сигналы короткие, но отсчёт и «цель близко» могут наложиться. */
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
