package com.pourista.data.io

import com.pourista.data.model.Recipe
import com.pourista.data.model.RecipeStep
import com.pourista.data.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecipeJsonTest {

    private val hoffmann = Recipe(
        name = "V60 · James Hoffmann",
        brewer = "Hario V60-02",
        doseGrams = 15f,
        waterGrams = 250f,
        waterTempC = 95,
        grindSetting = "средний",
        autoStart = true,
        steps = listOf(
            RecipeStep(
                kind = StepKind.BLOOM,
                startSec = 0,
                durationSec = 45,
                targetWaterGrams = 50f,
                pourFlowRate = 5f,
            ),
            RecipeStep(
                kind = StepKind.POUR,
                startSec = 45,
                durationSec = 30,
                targetWaterGrams = 150f,
                pourFlowRate = 4.5f,
            ),
            RecipeStep(
                kind = StepKind.DRAWDOWN,
                startSec = 75,
                durationSec = 95,
                targetWaterGrams = 150f,
            ),
        ),
    )

    @Test
    fun `рецепт переживает выгрузку и загрузку`() {
        val restored = RecipeJson.decode(RecipeJson.encode(listOf(hoffmann))).single()

        assertEquals(hoffmann.name, restored.name)
        assertEquals(hoffmann.brewer, restored.brewer)
        assertEquals(hoffmann.doseGrams, restored.doseGrams, 0.01f)
        assertEquals(hoffmann.waterGrams, restored.waterGrams, 0.01f)
        assertEquals(hoffmann.waterTempC, restored.waterTempC)
        assertEquals(hoffmann.grindSetting, restored.grindSetting)
        assertEquals(hoffmann.steps.map { it.kind }, restored.steps.map { it.kind })
        assertEquals(
            hoffmann.steps.map { it.targetWaterGrams },
            restored.steps.map { it.targetWaterGrams },
        )
        assertEquals(hoffmann.steps.map { it.startSec }, restored.steps.map { it.startSec })
        assertEquals(hoffmann.steps.map { it.pourFlowRate }, restored.steps.map { it.pourFlowRate })
    }

    @Test
    fun `в файле объём шага — долив, а не сумма`() {
        val text = RecipeJson.encode(listOf(hoffmann))

        // Второй шаг доливает 100 г до накопительных 150 г.
        assertEquals(true, text.contains("\"water\": 100"))
    }

    /** Рецепт может написать человек: обязательны только название и шаги. */
    @Test
    fun `минимальный рецепт читается`() {
        val text = """
            {
              "recipes": [
                {
                  "name": "Ручной",
                  "steps": [
                    { "duration": 30, "water": 50 },
                    { "kind": "DRAWDOWN", "duration": 60 }
                  ]
                }
              ]
            }
        """.trimIndent()

        val recipe = RecipeJson.decode(text).single()

        assertEquals("Ручной", recipe.name)
        assertEquals(94, recipe.waterTempC)
        assertEquals(50f, recipe.waterGrams, 0.01f)
        assertEquals(listOf(StepKind.POUR, StepKind.DRAWDOWN), recipe.steps.map { it.kind })
        assertEquals(listOf(0, 30), recipe.steps.map { it.startSec })
    }

    /** Из буфера обмена рецепт часто прилетает с обрамлением от нейросети. */
    @Test
    fun `json в тройных кавычках и с пояснениями вокруг читается`() {
        val text = """
            Конечно! Вот рецепт:

            ```json
            {
              "recipes": [
                { "name": "Из чата", "steps": [ { "duration": 30, "water": 50 } ] }
              ]
            }
            ```

            Приятного кофе!
        """.trimIndent()

        val recipe = RecipeJson.decode(text).single()

        assertEquals("Из чата", recipe.name)
        assertEquals(50f, recipe.waterGrams, 0.01f)
    }

    @Test
    fun `мусор вместо рецептов не проходит молча`() {
        assertThrows(IllegalArgumentException::class.java) { RecipeJson.decode("не json") }
        assertThrows(IllegalArgumentException::class.java) { RecipeJson.decode("""{"recipes":[]}""") }
    }
}
