package com.pourista.ui.recipes

import com.pourista.data.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Блуминг и слив закреплены по краям рецепта: перед первым лить нечего, после
 * последнего — некуда. Проверяем, что порядок держится при любых правках.
 */
class EditorStepsTest {

    private fun step(key: Long, kind: StepKind) = EditableStep(key = key, kind = kind)

    private val full = listOf(
        step(1, StepKind.BLOOM),
        step(2, StepKind.POUR),
        step(3, StepKind.POUR),
        step(4, StepKind.DRAWDOWN),
    )

    @Test
    fun `новый шаг встаёт перед сливом`() {
        val result = full.withRegularStep(step(9, StepKind.POUR))

        assertEquals(
            listOf(StepKind.BLOOM, StepKind.POUR, StepKind.POUR, StepKind.POUR, StepKind.DRAWDOWN),
            result.map { it.kind },
        )
        assertEquals("новый шаг — предпоследний", 9L, result[result.lastIndex - 1].key)
    }

    @Test
    fun `без слива новый шаг просто в конце`() {
        val result = full.dropLast(1).withRegularStep(step(9, StepKind.POUR))

        assertEquals(9L, result.last().key)
    }

    @Test
    fun `блуминг встаёт первым и только один раз`() {
        val withoutBloom = full.drop(1)

        val added = withoutBloom.withBloom(step(9, StepKind.BLOOM))
        assertEquals(StepKind.BLOOM, added.first().kind)
        assertEquals(9L, added.first().key)

        assertEquals("второй блуминг не добавляется", added, added.withBloom(step(10, StepKind.BLOOM)))
    }

    @Test
    fun `слив встаёт последним и только один раз`() {
        val withoutDrawdown = full.dropLast(1)

        val added = withoutDrawdown.withDrawdown(step(9, StepKind.DRAWDOWN))
        assertEquals(StepKind.DRAWDOWN, added.last().kind)

        assertEquals(added, added.withDrawdown(step(10, StepKind.DRAWDOWN)))
    }

    @Test
    fun `закреплённые шаги не двигаются`() {
        assertFalse(full.canMove(1, 1))
        assertFalse(full.canMove(4, -1))
        assertEquals(full, full.moved(1, 1))
    }

    @Test
    fun `обычный шаг не выходит за блуминг и слив`() {
        assertFalse("выше блуминга нельзя", full.canMove(2, -1))
        assertFalse("ниже слива нельзя", full.canMove(3, 1))
        assertTrue(full.canMove(2, 1))

        val moved = full.moved(2, 1)
        assertEquals(listOf(1L, 3L, 2L, 4L), moved.map { it.key })
    }

    @Test
    fun `время влива считается из скорости, а пустое поле не ломает шаг`() {
        val step = EditableStep(key = 1, kind = StepKind.POUR, water = "50", flow = "5")
        assertEquals(10, step.pourSeconds)

        val empty = step.copy(water = "", flow = "")
        assertEquals(0f, empty.deltaGrams, 0.001f)
        assertEquals(0, empty.pourSeconds)
    }

    private val pour = EditableStep(key = 1, kind = StepKind.POUR, water = "50", flow = "5")
        .syncPourSeconds()

    @Test
    fun `скорость и время влива пересчитывают друг друга`() {
        assertEquals("10", pour.pourSec)

        val byTime = pour.withPourSeconds("20")
        assertEquals("2.5", byTime.flow)
        assertEquals("20", byTime.pourSec)

        val byFlow = byTime.withFlow("10")
        assertEquals("5", byFlow.pourSec)
    }

    @Test
    fun `объём меняет то, что не задавали руками`() {
        // Скорость задана руками — она и остаётся, время пересчитывается.
        val keepsFlow = pour.withFlow("5").withWater("100")
        assertEquals("5", keepsFlow.flow)
        assertEquals("20", keepsFlow.pourSec)

        // Время задано руками — остаётся оно, а скорость подстраивается.
        val keepsTime = pour.withPourSeconds("10").withWater("100")
        assertEquals("10", keepsTime.pourSec)
        assertEquals("10", keepsTime.flow)
    }

    @Test
    fun `влив подрезается под длительность шага`() {
        // 50 г при 5 г/с — это 10 секунд, а шаг стал шестисекундным.
        val short = pour.copy(duration = "6").pourFittedToDuration()

        assertEquals("6", short.pourSec)
        assertEquals("50 г за 6 секунд", "8.3", short.flow)
        assertFalse(short.pourTooLong)
    }

    @Test
    fun `влив короче шага не трогаем`() {
        val roomy = pour.copy(duration = "30")

        assertEquals(roomy, roomy.pourFittedToDuration())
    }

    @Test
    fun `недонабранная длительность не пересчитывает скорость`() {
        // Поле очистили, чтобы набрать заново: это ещё не «ноль секунд».
        val typing = pour.copy(duration = "")

        assertEquals(typing, typing.pourFittedToDuration())
    }

    @Test
    fun `после подрезки объём меняет скорость, а не время`() {
        val short = pour.copy(duration = "6").pourFittedToDuration()

        // Влив уже занимает весь шаг, и добавленный объём должен уложиться
        // в те же шесть секунд.
        val more = short.withWater("60")
        assertEquals("6", more.pourSec)
        assertEquals("10", more.flow)
    }

    @Test
    fun `недописанное число не затирает соседнее поле`() {
        val cleared = pour.withFlow("")

        assertEquals("", cleared.flow)
        assertEquals("время влива осталось прежним", "10", cleared.pourSec)
    }
}
