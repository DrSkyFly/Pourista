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
 *
 * Скачок вверх проверяется тем же временем. Вода идёт струёй: за одно
 * показание её не может стать больше чем на [riseJumpGrams], а за секунду —
 * чем на [riseGramsPerSec]. Всё, что выше, — нажатая крышка, поставленный на
 * весы чайник, задетая подставка, — и такому весу верим, только если он
 * продержится [riseHoldMs]. Без этой проверки мгновенный скачок уходил в
 * расчёты целиком: приложение считало воду налитой и заканчивало заваривание
 * на середине рецепта. Рука на воронке к тому же не стоит на месте, так что
 * до конца выдержки нажатие обычно и не доживает.
 */
internal class MonotonicWeight(
    /** Мелкие просадки не считаем даже кратковременными. */
    private val toleranceGrams: Float = TOLERANCE_GRAMS,
    /** Сколько просадка должна продержаться, чтобы стать новой правдой. */
    private val rebaseAfterMs: Long = REBASE_AFTER_MS,
    /** Больше этого за одно показание вода прибыть не может. */
    private val riseJumpGrams: Float = RISE_JUMP_GRAMS,
    /** А за секунду — больше этого: столько не льют и с полного чайника. */
    private val riseGramsPerSec: Float = RISE_GRAMS_PER_SEC,
    /** Сколько скачок вверх должен продержаться, чтобы сойти за воду. */
    private val riseHoldMs: Long = RISE_HOLD_MS,
) {

    private var maxGrams = 0f
    private var belowSinceMs = 0L

    /** Уровень, с которого идёт отсчёт: просадка держится, пока вес на нём. */
    private var belowGrams = 0f

    /** То же для скачка вверх: с какого уровня и с какого момента он держится. */
    private var aboveSinceMs = 0L
    private var aboveGrams = 0f

    /** Время прошлого показания: по нему считаем, могла ли вода столько дать. */
    private var lastSampleMs = 0L

    /** Показание для расчётов: скорости, графиков, определения конца влива. */
    fun onSample(rawGrams: Float, nowMs: Long): Float {
        // Первому показанию верим как есть: сравнивать его не с чем.
        val first = lastSampleMs == 0L
        val sinceLastMs = if (first) 0L else nowMs - lastSampleMs
        lastSampleMs = nowMs

        if (rawGrams >= maxGrams) {
            val couldBeWater = maxOf(riseJumpGrams, riseGramsPerSec * sinceLastMs / 1000f)
            if (!first && rawGrams - maxGrams > couldBeWater) {
                // Уровень сменился: вес не стоит наверху, а всё ещё едет.
                // Отсчёт начинается заново — для того уровня, что сейчас.
                if (aboveSinceMs == 0L || abs(rawGrams - aboveGrams) > toleranceGrams) {
                    aboveSinceMs = nowMs
                    aboveGrams = rawGrams
                    return maxGrams
                }
                if (nowMs - aboveSinceMs < riseHoldMs) return maxGrams
            }
            aboveSinceMs = 0L
            maxGrams = rawGrams
            belowSinceMs = 0L
            return maxGrams
        }

        // Вес ниже максимума — скачка вверх больше нет.
        aboveSinceMs = 0L

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
        aboveSinceMs = 0L
        aboveGrams = 0f
        lastSampleMs = 0L
    }

    private companion object {
        const val TOLERANCE_GRAMS = 1f
        const val REBASE_AFTER_MS = 5_000L
        const val RISE_JUMP_GRAMS = 20f
        const val RISE_GRAMS_PER_SEC = 40f
        const val RISE_HOLD_MS = 5_000L
    }
}
