package com.pourista.brew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemovalWatchTest {

    /** Наливаем 250 г за первые секунды: после этого можно взводить сторож. */
    private fun RemovalWatch.pourUpTo(grams: Float, untilMs: Long = 0L): Long {
        var now = 0L
        var weight = 0f
        while (weight < grams) {
            weight += 10f
            now += 1_000L
            assertFalse(onSample(weight, now))
        }
        return maxOf(now, untilMs)
    }

    @Test
    fun `до последнего влива падение веса ничего не значит`() {
        val watch = RemovalWatch()
        var now = watch.pourUpTo(250f)

        // Чашку сняли, но сторож не взведён: рецепт ещё требует воды.
        repeat(10) {
            now += 1_000L
            assertFalse(watch.onSample(-300f, now))
        }
    }

    @Test
    fun `снятая чашка заканчивает заваривание через три секунды`() {
        val watch = RemovalWatch()
        var now = watch.pourUpTo(250f)
        watch.arm(250f)

        now += 1_000L
        assertFalse(watch.onSample(250f, now))

        // Воронку сняли: вес упал больше чем вдвое.
        val droppedAt = now + 100L
        assertFalse(watch.onSample(60f, droppedAt))
        // Трёх секунд мало: столько длится покачивание воронки на весу.
        assertFalse(watch.onSample(60f, droppedAt + 3_000L))
        assertFalse(watch.onSample(60f, droppedAt + 4_900L))
        assertTrue(watch.onSample(60f, droppedAt + 5_000L))

        // Финиш относим к моменту падения, а вес в историю берём последний
        // нормальный: снимали уже готовую чашку.
        assertEquals(droppedAt, watch.droppedAtMs)
        assertEquals(250f, watch.weightBeforeDrop, 0.01f)
    }

    @Test
    fun `снятая целиком чашка уводит весы в минус и тоже считается финишем`() {
        val watch = RemovalWatch()
        var now = watch.pourUpTo(250f)
        watch.arm(250f)

        now += 500L
        assertFalse(watch.onSample(-420f, now))
        // Минусу верим быстрее: так бывает только когда сняли всё разом.
        assertFalse(watch.onSample(-420f, now + 2_900L))
        assertTrue(watch.onSample(-420f, now + 3_000L))
    }

    @Test
    fun `короткий рывок весов заваривание не заканчивает`() {
        val watch = RemovalWatch()
        var now = watch.pourUpTo(250f)
        watch.arm(250f)

        now += 500L
        assertFalse(watch.onSample(10f, now))
        assertFalse(watch.onSample(10f, now + 1_500L))
        // Чашку поставили обратно — отсчёт начинается заново.
        assertFalse(watch.onSample(248f, now + 2_000L))
        assertFalse(watch.onSample(10f, now + 2_500L))
        assertFalse(watch.onSample(10f, now + 6_000L))
        assertTrue(watch.onSample(10f, now + 7_500L))
    }

    @Test
    fun `на большом объёме снятая воронка весит меньше половины`() {
        val watch = RemovalWatch()
        var now = watch.pourUpTo(600f)
        watch.arm(600f)

        // Воронка с намокшим кофе — около сотни граммов из шестисот: вдвое
        // вес тут не упадёт никогда, а заваривание всё равно закончено.
        now += 1_000L
        val droppedAt = now
        assertFalse(watch.onSample(490f, droppedAt))
        assertTrue(watch.onSample(490f, droppedAt + 5_000L))
        assertEquals(600f, watch.weightBeforeDrop, 0.01f)
    }

    @Test
    fun `покачивание воронки на весах за снятие не считаем`() {
        val watch = RemovalWatch()
        var now = watch.pourUpTo(250f)
        watch.arm(250f)

        // Двадцать граммов туда-сюда — обычный шум при свирле.
        repeat(10) {
            now += 1_000L
            assertFalse(watch.onSample(if (it % 2 == 0) 232f else 250f, now))
        }
    }

    @Test
    fun `на совсем лёгком весе сторож молчит`() {
        val watch = RemovalWatch()
        // Пятнадцать граммов — это ещё доза, а не заваривание: шум весов в
        // пару граммов не должен считаться снятой чашкой.
        assertFalse(watch.onSample(15f, 1_000L))
        watch.arm(15f)
        assertFalse(watch.onSample(1f, 2_000L))
        assertFalse(watch.onSample(1f, 10_000L))
    }

    @Test
    fun `просевший вес виден до истечения выдержки`() {
        val watch = RemovalWatch()
        val now = watch.pourUpTo(600f)
        watch.arm(600f)
        assertFalse(watch.dropPending)

        // Воронку сняли секунду назад: выдержка ещё идёт, но факт падения
        // уже известен — «Финиш» по кнопке должен считать так же.
        assertFalse(watch.onSample(400f, now + 1_000L))
        assertTrue(watch.dropPending)
        assertEquals(600f, watch.weightBeforeDrop, 0.01f)
    }

    @Test
    fun `падение до взведения запоминается и досчитывается после`() {
        val watch = RemovalWatch()
        val now = watch.pourUpTo(600f)

        // Воронку сняли раньше, чем рецепт признал влив законченным.
        assertFalse(watch.onSample(400f, now + 1_000L))
        assertTrue("падение видно и без взведения", watch.dropPending)
        assertEquals("вес до падения запомнен", 600f, watch.weightBeforeDrop, 0.01f)

        // Взвели позже — отсчёт идёт с самого падения, а не с этого момента.
        watch.arm(400f)
        assertEquals(600f, watch.weightBeforeDrop, 0.01f)
        assertTrue(watch.onSample(400f, now + 6_000L))
    }

    @Test
    fun `промежуточные показания снятия не занижают вес в истории`() {
        val watch = RemovalWatch()
        var now = watch.pourUpTo(430f)
        watch.arm(430f)

        // После чайника вес садится чуть ниже максимума и там и остаётся.
        now += 1_000L
        assertFalse(watch.onSample(429.7f, now))

        // Весы отдают снятие не одним скачком: пока воронку поднимают, приходит
        // два-три промежуточных показания. Первое из них ещё выше порога, и
        // раньше именно оно уезжало в историю вместо налитого.
        assertFalse(watch.onSample(403f, now + 100L))
        assertFalse(watch.onSample(180f, now + 200L))
        assertTrue(watch.onSample(150f, now + 5_200L))

        assertEquals(430f, watch.weightBeforeDrop, 0.01f)
    }

    @Test
    fun `просадка, которая держится, всё-таки становится весом до падения`() {
        val watch = RemovalWatch()
        var now = watch.pourUpTo(430f)
        watch.arm(430f)
        now += 1_000L
        assertFalse(watch.onSample(429.7f, now))

        // Не всякое падение внутри порога — снятие: чашку могли подвинуть, и
        // новый вес держится на одном уровне. Пятнадцать секунд — это уже не
        // рябь и не спуск на пути вниз.
        repeat(15) {
            now += 1_000L
            assertFalse(watch.onSample(403f, now))
        }
        assertEquals(403f, watch.weightBeforeDrop, 0.01f)
    }

    @Test
    fun `после долгого слива снятие не занижает вес в истории`() {
        val watch = RemovalWatch()
        var now = 0L

        // Пролив. Максимум ставится на струе: падающая вода добавляет пару
        // граммов, и после чайника вес садится чуть ниже. К самому максимуму
        // он больше не вернётся — весь слив показания идут в пределах допуска.
        for (grams in listOf(45f, 100f, 148f, 200f, 251.4f)) {
            now += 30_000L
            assertFalse(watch.onSample(grams, now))
        }
        now += 1_000L
        assertFalse(watch.onSample(251.2f, now))
        watch.arm(251.2f)

        // Одна шумная посылка ниже допуска и минута слива.
        now += 3_000L
        assertFalse(watch.onSample(249.9f, now))
        repeat(60) {
            now += 1_000L
            assertFalse(watch.onSample(251.0f, now))
        }

        // Воронку сняли. Первое показание на пути вниз ещё выше порога — и
        // раньше именно оно уезжало в историю вместо налитого.
        now += 100L
        assertFalse(watch.onSample(230f, now))
        assertEquals(251.4f, watch.weightBeforeDrop, 0.01f)

        val droppedAt = now + 100L
        assertFalse(watch.onSample(5f, droppedAt))
        assertTrue(watch.onSample(5f, droppedAt + 5_000L))
        assertEquals(251.4f, watch.weightBeforeDrop, 0.01f)
    }

    @Test
    fun `сброс снимает сторож`() {
        val watch = RemovalWatch()
        val now = watch.pourUpTo(250f)
        watch.arm(250f)
        watch.reset()

        assertFalse(watch.armed)
        assertFalse(watch.onSample(-300f, now + 10_000L))
    }
}
