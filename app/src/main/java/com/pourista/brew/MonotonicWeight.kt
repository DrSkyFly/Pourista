package com.pourista.brew

import kotlin.math.abs

/**
 * Вес для расчётов: неубывающий, пока идёт заваривание.
 *
 * Воды на весах становится только больше. Если показания просели — воронку
 * качнули, задели чайником, весы дрогнули, — а через миг вернулись, то этот
 * возврат не влив: приняв его за влив, приложение показало бы скорость в
 * десятки граммов в секунду и записало бы выброс в график.
 *
 * Но просадка бывает и настоящей: нажали тару или сняли чашку. Такую от
 * покачивания отличает только время — она не проходит. Поэтому вес, который
 * держится ниже максимума дольше [rebaseAfterMs], принимается за новую точку
 * отсчёта.
 *
 * Ждём столько же, сколько сторож конца заваривания: свирл на полной воронке
 * занимает три-четыре секунды, и за две вес успевал стать «новой правдой» —
 * график проваливался, а возврат выглядел вливом в сотни граммов.
 *
 * «Держится» проверяется по двум условиям сразу, и оба обязательны. Вес не
 * возвращался к максимуму — иначе просадка кончилась. И вес всё это время
 * стоял на одном уровне — иначе он не держится, а едет вниз, и брать за
 * правду показание, снятое на полпути, нельзя.
 */
internal class MonotonicWeight(
    /** Мелкие просадки не считаем даже кратковременными. */
    private val toleranceGrams: Float = TOLERANCE_GRAMS,
    /** Сколько просадка должна продержаться, чтобы стать новой правдой. */
    private val rebaseAfterMs: Long = REBASE_AFTER_MS,
) {

    private var maxGrams = 0f
    private var belowSinceMs = 0L

    /** Уровень, с которого идёт отсчёт: просадка держится, пока вес на нём. */
    private var belowGrams = 0f

    /** Показание для расчётов: скорости, графиков, определения конца влива. */
    fun onSample(rawGrams: Float, nowMs: Long): Float {
        if (rawGrams >= maxGrams) {
            maxGrams = rawGrams
            belowSinceMs = 0L
            return maxGrams
        }

        // Вернулись в допуск — просадки больше нет. Отсчёт снимаем: иначе он
        // доживёт до следующей просадки и зачтёт ей чужое время.
        if (maxGrams - rawGrams <= toleranceGrams) {
            belowSinceMs = 0L
            return maxGrams
        }

        // Уровень сменился: вес не стоит внизу, а всё ещё падает. Отсчёт
        // начинается заново — для того уровня, на котором вес сейчас.
        if (belowSinceMs == 0L || abs(rawGrams - belowGrams) > toleranceGrams) {
            belowSinceMs = nowMs
            belowGrams = rawGrams
            return maxGrams
        }
        if (nowMs - belowSinceMs < rebaseAfterMs) return maxGrams

        // Просадка не прошла — значит это не рябь, а новая точка отсчёта.
        maxGrams = rawGrams
        belowSinceMs = 0L
        return maxGrams
    }

    fun reset() {
        maxGrams = 0f
        belowSinceMs = 0L
        belowGrams = 0f
    }

    private companion object {
        const val TOLERANCE_GRAMS = 1f
        const val REBASE_AFTER_MS = 5_000L
    }
}
