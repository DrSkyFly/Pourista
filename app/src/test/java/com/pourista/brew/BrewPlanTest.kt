package com.pourista.brew

import com.pourista.data.model.Recipe
import com.pourista.data.model.RecipeStep
import com.pourista.data.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BrewPlanTest {

    @Test
    fun `свирл начинается сразу после влива, а остаток уходит в паузу`() {
        // Влив 30 с, свирл 5 с, пауза 10 с. Лили быстро и закончили на двадцатой.
        val plan = listOf(
            step(StepKind.POUR, 0, 30, 300f),
            step(StepKind.SWIRL, 30, 5, 300f),
            step(StepKind.WAIT, 35, 10, 300f),
        )

        val pulled = plan.pulledIn(stepStartSec = 30, shiftSec = 10)

        assertEquals(listOf(0 to 20, 20 to 5, 25 to 20), pulled.times())
        // Рецепт кончается тогда же: время не потеряно, оно в паузе.
        assertEquals(45, plan.last().endSec)
        assertEquals(45, pulled.last().endSec)
    }

    @Test
    fun `паузы в рецепте нет — она появляется`() {
        val plan = listOf(
            step(StepKind.POUR, 0, 30, 300f),
            step(StepKind.SWIRL, 30, 5, 300f),
            step(StepKind.POUR, 35, 10, 400f),
        )

        val pulled = plan.pulledIn(stepStartSec = 30, shiftSec = 10)

        assertEquals(listOf(0 to 20, 20 to 5, 25 to 10, 35 to 10), pulled.times())
        assertEquals(StepKind.WAIT, pulled[2].kind)
        // Пауза — это то же самое количество воды, что и после свирла.
        assertEquals(300f, pulled[2].targetWaterGrams, 0.01f)
        // Второй влив начинается тогда же, когда собирался.
        assertEquals(35, pulled[3].startSec)
    }

    @Test
    fun `перед сливом пауза не нужна`() {
        val plan = listOf(
            step(StepKind.POUR, 75, 30, 250f),
            step(StepKind.SWIRL, 105, 10, 250f),
            step(StepKind.DRAWDOWN, 115, 95, 250f),
        )

        val pulled = plan.pulledIn(stepStartSec = 105, shiftSec = 10)

        assertEquals(listOf(75 to 20, 95 to 10, 105 to 95), pulled.times())
        // Воды больше не будет, ждать нечего: заваривание кончается раньше.
        assertEquals(200, pulled.last().endSec)
    }

    @Test
    fun `свирл в конце рецепта просто заканчивает его раньше`() {
        val plan = listOf(
            step(StepKind.POUR, 0, 30, 300f),
            step(StepKind.SWIRL, 30, 5, 300f),
        )

        val pulled = plan.pulledIn(stepStartSec = 30, shiftSec = 10)

        assertEquals(listOf(0 to 20, 20 to 5), pulled.times())
    }

    @Test
    fun `размешивание подтягивается так же, как свирл`() {
        val plan = listOf(
            step(StepKind.BLOOM, 0, 15, 66f),
            step(StepKind.STIR, 15, 30, 66f),
            step(StepKind.POUR, 45, 60, 360f),
        )

        val pulled = plan.pulledIn(stepStartSec = 15, shiftSec = 3)

        assertEquals(listOf(0 to 12, 12 to 30, 42 to 3, 45 to 60), pulled.times())
    }

    @Test
    fun `тянуть можно только свирл и только за вливом`() {
        val plan = listOf(
            step(StepKind.POUR, 0, 30, 300f),
            step(StepKind.WAIT, 30, 10, 300f),
            step(StepKind.SWIRL, 40, 5, 300f),
        )

        // Пауза после влива подождёт: её место в рецепте выбрано осознанно.
        assertSame(plan, plan.pulledIn(stepStartSec = 30, shiftSec = 10))
        // Свирл не за вливом, а за паузой — значит, влив кончился давно.
        assertSame(plan, plan.pulledIn(stepStartSec = 40, shiftSec = 5))
        // Такого шага в рецепте нет.
        assertSame(plan, plan.pulledIn(stepStartSec = 33, shiftSec = 5))
    }

    @Test
    fun `подтяжка не съедает влив целиком`() {
        val plan = listOf(
            step(StepKind.POUR, 0, 30, 300f),
            step(StepKind.SWIRL, 30, 5, 300f),
            step(StepKind.WAIT, 35, 10, 300f),
        )

        assertSame(plan, plan.pulledIn(stepStartSec = 30, shiftSec = 30))
        assertSame(plan, plan.pulledIn(stepStartSec = 30, shiftSec = 40))
        assertSame(plan, plan.pulledIn(stepStartSec = 30, shiftSec = 0))
    }

    @Test
    fun `подтяжки переносятся на рецепт, пересчитанный под дозу`() {
        val recipe = recipe(
            step(StepKind.POUR, 0, 30, 300f),
            step(StepKind.SWIRL, 30, 5, 300f),
            step(StepKind.WAIT, 35, 10, 300f),
            step(StepKind.POUR, 45, 20, 500f),
            step(StepKind.SWIRL, 65, 5, 500f),
            step(StepKind.DRAWDOWN, 70, 60, 500f),
        )

        val planned = recipe.withPullIns(linkedMapOf(30 to 10, 65 to 5))

        assertEquals(
            listOf(0 to 20, 20 to 5, 25 to 20, 45 to 15, 60 to 5, 65 to 60),
            planned.steps.times(),
        )
        // Рецепт без подтяжек остаётся тем же самым объектом.
        assertSame(recipe, recipe.withPullIns(emptyMap()))
    }

    private fun List<RecipeStep>.times(): List<Pair<Int, Int>> =
        map { it.startSec to it.durationSec }

    private fun step(kind: StepKind, start: Int, duration: Int, target: Float) = RecipeStep(
        kind = kind,
        startSec = start,
        durationSec = duration,
        targetWaterGrams = target,
    )

    private fun recipe(vararg steps: RecipeStep) = Recipe(
        name = "Тест",
        brewer = "V60",
        doseGrams = 18f,
        waterGrams = 300f,
        waterTempC = 94,
        steps = steps.toList(),
    )
}
