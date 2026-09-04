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

/** Сколько остывать кофе по умолчанию: три минуты — чашка перестаёт обжигать. */
const val DEFAULT_COOLDOWN_SECONDS = 180

/** Границы, в которых крутится время остывания. */
const val MIN_COOLDOWN_SECONDS = 30
const val MAX_COOLDOWN_SECONDS = 20 * 60

/** Шаг настройки: полминуты — мельче для остывающей чашки бессмысленно. */
const val COOLDOWN_STEP_SECONDS = 30

data class CooldownState(
    /** На сколько таймер заводили. Ноль — таймер не идёт. */
    val totalSeconds: Int = 0,
    val remainingMs: Long = 0,
) {
    val running: Boolean get() = remainingMs > 0

    /** Осталось секунд, с округлением вверх: «0:00» должен значить конец. */
    val remainingSeconds: Int get() = ((remainingMs + 999) / 1000).toInt()
}

/**
 * Таймер остывания: заводится после заваривания и звонит, когда кофе можно
 * пить не обжигаясь.
 *
 * К ходу заваривания отношения не имеет и живёт отдельно от [BrewEngine]: его
 * заводят, когда заваривание уже закончено, и он должен доработать, даже если
 * весы отключились, а экран заваривания закрыли.
 *
 * Отсчёт идёт от срока, а не сложением тиков: телефон может задержать
 * корутину, и через двадцать минут накопленная ошибка была бы заметной.
 */
class CooldownTimer(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(CooldownState())
    val state: StateFlow<CooldownState> = _state.asStateFlow()

    private val _rings = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Время вышло — пора звонить. */
    val rings: SharedFlow<Unit> = _rings.asSharedFlow()

    private var ticker: Job? = null

    /**
     * Номер запуска. Отменённая корутина может успеть дописать своё состояние
     * поверх нового запуска: между проверкой и записью она уже не остановится.
     * По номеру она видит, что её сменили, и молча уходит.
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

    /** Снять таймер. Звонка не будет: остывание прервали, а не досидели. */
    fun stop() {
        generation++
        ticker?.cancel()
        ticker = null
        _state.value = CooldownState()
    }

    private companion object {
        /** Показываем минуты и секунды, но последняя секунда должна дотикать ровно. */
        const val TICK_MS = 200L
    }
}
