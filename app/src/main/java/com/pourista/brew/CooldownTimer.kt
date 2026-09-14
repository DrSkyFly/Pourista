package com.pourista.brew

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** How long coffee cools by default: three minutes, and the cup stops scalding. */
const val DEFAULT_COOLDOWN_SECONDS = 180

/** The bounds the cooldown time turns within. */
const val MIN_COOLDOWN_SECONDS = 30
const val MAX_COOLDOWN_SECONDS = 20 * 60

/** The setting step: half a minute, finer makes no sense for a cooling cup. */
const val COOLDOWN_STEP_SECONDS = 30

data class CooldownState(
    /** How long the timer was wound for. Zero means the timer is not running. */
    val totalSeconds: Int = 0,
    val remainingMs: Long = 0,
) {
    val running: Boolean get() = remainingMs > 0

    /** Seconds left, rounded up: "0:00" must mean the end. */
    val remainingSeconds: Int get() = ((remainingMs + 999) / 1000).toInt()
}

/**
 * The cooldown timer: wound after a brew, it rings when the coffee can be drunk without
 * scalding.
 *
 * It has nothing to do with the course of a brew and lives apart from [BrewEngine]: it is
 * wound when the brew is already over, and it has to see its time out even if the scale
 * disconnected and the brew screen was closed.
 *
 * The count runs off a deadline rather than by adding up ticks: the phone can hold a
 * coroutine back, and after twenty minutes the accumulated error would show.
 */
class CooldownTimer(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(CooldownState())
    val state: StateFlow<CooldownState> = _state.asStateFlow()

    private val _rings = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Time is up — ring. */
    val rings: SharedFlow<Unit> = _rings.asSharedFlow()

    private var ticker: Job? = null

    /**
     * The run number. A cancelled coroutine can still manage to write its state over a new
     * run: between the check and the write it will not stop any more. By the number it sees
     * it has been replaced, and leaves quietly.
     */
    private var generation = 0

    fun start(seconds: Int) {
        val total = seconds.coerceIn(MIN_COOLDOWN_SECONDS, MAX_COOLDOWN_SECONDS)
        val mine = ++generation
        ticker?.cancel()
        _state.value = CooldownState(totalSeconds = total, remainingMs = total * 1000L)
        val deadline = SystemClock.elapsedRealtime() + total * 1000L
        ticker = scope.launch {
            while (isActive) {
                val left = deadline - SystemClock.elapsedRealtime()
                if (left <= 0) break
                if (generation != mine) return@launch
                _state.value = CooldownState(totalSeconds = total, remainingMs = left)
                delay(TICK_MS)
            }
            if (generation != mine) return@launch
            _state.value = CooldownState()
            _rings.tryEmit(Unit)
        }
    }

    /** Take the timer off. There will be no ring: the cooldown was cut short, not seen out. */
    fun stop() {
        generation++
        ticker?.cancel()
        ticker = null
        _state.value = CooldownState()
    }

    private companion object {
        /** We show minutes and seconds, but the last second has to tick out in full. */
        const val TICK_MS = 200L
    }
}
