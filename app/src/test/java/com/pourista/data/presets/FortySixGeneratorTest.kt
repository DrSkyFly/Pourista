package com.pourista.data.presets

import com.pourista.data.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Числа взяты из оригинального приложения 4:6: 15 г кофе, 250 г воды.
 * Расхождение с ним означало бы, что генератор варит другой рецепт.
 */
class FortySixGeneratorTest {

    private fun params(taste: FortySixTaste, strength: FortySixStrength) = FortySixParams(
        doseGrams = 15f,
        ratio = 250f / 15f,
        taste = taste,
        strength = strength,
    )

    @Test
    fun `sweet and lower repeats the reference plan`() {
        val pours = FortySixGenerator.pours(params(FortySixTaste.SWEET, FortySixStrength.LOWER))

        assertEquals(listOf(0, 45, 90), pours.map { it.startSec })
        assertEquals(listOf(32f, 68f, 150f), pours.map { it.addGrams })
        assertEquals(listOf(32f, 100f, 250f), pours.map { it.totalGrams })
    }

    @Test
    fun `sweet normal with two pours repeats the reference plan`() {
        val pours = FortySixGenerator.pours(
            params(FortySixTaste.SWEET_NORMAL, FortySixStrength.LOW)
        )

        assertEquals(listOf(0, 45, 90, 150), pours.map { it.startSec })
        assertEquals(listOf(41f, 59f, 75f, 75f), pours.map { it.addGrams })
        assertEquals(listOf(41f, 100f, 175f, 250f), pours.map { it.totalGrams })
    }

    @Test
    fun `normal repeats the reference plan`() {
        val pours = FortySixGenerator.pours(params(FortySixTaste.NORMAL, FortySixStrength.NORMAL))

        assertEquals(listOf(0, 45, 90, 130, 170), pours.map { it.startSec })
        assertEquals(listOf(50f, 50f, 50f, 50f, 50f), pours.map { it.addGrams })
        assertEquals(listOf(50f, 100f, 150f, 200f, 250f), pours.map { it.totalGrams })
    }

    @Test
    fun `normal acid with four pours repeats the reference plan`() {
        val pours = FortySixGenerator.pours(
            params(FortySixTaste.NORMAL_ACID, FortySixStrength.HIGH)
        )

        assertEquals(listOf(0, 45, 90, 120, 150, 180), pours.map { it.startSec })
        assertEquals(listOf(59f, 41f, 38f, 37f, 38f, 37f), pours.map { it.addGrams })
        assertEquals(listOf(59f, 100f, 138f, 175f, 213f, 250f), pours.map { it.totalGrams })
    }

    @Test
    fun `acid with five pours repeats the reference plan`() {
        val pours = FortySixGenerator.pours(params(FortySixTaste.ACID, FortySixStrength.HIGHER))

        assertEquals(listOf(0, 45, 90, 114, 138, 162, 186), pours.map { it.startSec })
        assertEquals(listOf(68f, 32f, 30f, 30f, 30f, 30f, 30f), pours.map { it.addGrams })
        assertEquals(250f, pours.last().totalGrams, 0.01f)
    }

    @Test
    fun `first pour is a bloom and the plan ends with a drawdown`() {
        val steps = FortySixGenerator.steps(params(FortySixTaste.NORMAL, FortySixStrength.NORMAL))

        assertEquals(StepKind.BLOOM, steps.first().kind)
        assertEquals(StepKind.DRAWDOWN, steps.last().kind)
        // Проливы кончаются на 3:30, как в оригинале, дальше только слив.
        assertEquals(210, steps.last().startSec)
        assertEquals(1, steps.count { it.kind == StepKind.DRAWDOWN })
    }

    @Test
    fun `water follows the dose and the ratio`() {
        val recipe = FortySixGenerator.recipe(
            params = FortySixParams(doseGrams = 20f, ratio = 15f),
            name = "4:6",
        )

        assertEquals(300f, recipe.waterGrams, 0.01f)
        assertEquals(300f, recipe.finalTargetGrams, 0.01f)
        assertEquals(93, recipe.waterTempC)
    }

    @Test
    fun `tiny dose still gives a plan with growing targets`() {
        val pours = FortySixGenerator.pours(FortySixParams(doseGrams = 1f, ratio = 15f))

        assertEquals(FortySixStrength.NORMAL.pours + 2, pours.size)
        pours.zipWithNext { previous, next ->
            assert(next.totalGrams > previous.totalGrams) { "$previous -> $next" }
        }
    }
}
