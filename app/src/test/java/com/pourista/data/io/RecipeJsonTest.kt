package com.pourista.data.io

import com.pourista.data.model.Recipe
import com.pourista.data.model.RecipeStep
import com.pourista.data.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeJsonTest {

    private val hoffmann = Recipe(
        name = "V60 · James Hoffmann",
        brewer = "Hario V60-02",
        doseGrams = 15f,
        waterGrams = 250f,
        waterTempC = 95,
        grindSetting = "medium",
        filterName = "Cafec Abaca",
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
    fun `a recipe survives an export and an import`() {
        val restored = RecipeJson.decode(RecipeJson.encode(listOf(hoffmann))).single()

        assertEquals(hoffmann.name, restored.name)
        assertEquals(hoffmann.brewer, restored.brewer)
        assertEquals(hoffmann.doseGrams, restored.doseGrams, 0.01f)
        assertEquals(hoffmann.waterGrams, restored.waterGrams, 0.01f)
        assertEquals(hoffmann.waterTempC, restored.waterTempC)
        assertEquals(hoffmann.grindSetting, restored.grindSetting)
        assertEquals(hoffmann.filterName, restored.filterName)
        assertEquals(hoffmann.steps.map { it.kind }, restored.steps.map { it.kind })
        assertEquals(
            hoffmann.steps.map { it.targetWaterGrams },
            restored.steps.map { it.targetWaterGrams },
        )
        assertEquals(hoffmann.steps.map { it.startSec }, restored.steps.map { it.startSec })
        assertEquals(hoffmann.steps.map { it.pourFlowRate }, restored.steps.map { it.pourFlowRate })
    }

    @Test
    fun `in the file the step volume is the addition rather than the sum`() {
        val text = RecipeJson.encode(listOf(hoffmann))

        // The second step adds 100 g up to a cumulative 150 g.
        assertEquals(true, text.contains("\"water\": 100"))
    }

    /** A recipe can be written by a person: only the name and the steps are required. */
    @Test
    fun `a minimal recipe is read`() {
        val text = """
            {
              "recipes": [
                {
                  "name": "By hand",
                  "steps": [
                    { "duration": 30, "water": 50 },
                    { "kind": "DRAWDOWN", "duration": 60 }
                  ]
                }
              ]
            }
        """.trimIndent()

        val recipe = RecipeJson.decode(text).single()

        assertEquals("By hand", recipe.name)
        assertEquals(94, recipe.waterTempC)
        assertEquals(50f, recipe.waterGrams, 0.01f)
        assertEquals(listOf(StepKind.POUR, StepKind.DRAWDOWN), recipe.steps.map { it.kind })
        assertEquals(listOf(0, 30), recipe.steps.map { it.startSec })
    }

    /** From the clipboard a recipe often arrives wrapped by a neural network. */
    @Test
    fun `json in triple backticks and with explanations around it is read`() {
        val text = """
            Of course! Here is the recipe:

            ```json
            {
              "recipes": [
                { "name": "From a chat", "steps": [ { "duration": 30, "water": 50 } ] }
              ]
            }
            ```

            Enjoy your coffee!
        """.trimIndent()

        val recipe = RecipeJson.decode(text).single()

        assertEquals("From a chat", recipe.name)
        assertEquals(50f, recipe.waterGrams, 0.01f)
    }

    @Test
    fun `rubbish instead of recipes does not pass quietly`() {
        assertThrows(IllegalArgumentException::class.java) { RecipeJson.decode("not json") }
        assertThrows(IllegalArgumentException::class.java) { RecipeJson.decode("""{"recipes":[]}""") }
    }

    @Test
    fun `aeropress mode survives an export and an import`() {
        val recipe = Recipe(
            name = "AeroPress",
            brewer = "AeroPress",
            doseGrams = 15f,
            waterGrams = 220f,
            waterTempC = 85,
            aeropressMode = true,
            steps = listOf(
                RecipeStep(kind = StepKind.POUR, startSec = 0, durationSec = 20, targetWaterGrams = 220f),
            ),
        )

        val restored = RecipeJson.decode(RecipeJson.encode(listOf(recipe)))

        assertEquals(1, restored.size)
        assertTrue("The mode should be kept", restored.first().aeropressMode)
    }

    @Test
    fun `a recipe without aeropress mode is read as an ordinary one`() {
        val json = """
            {"recipes":[{"name":"V60","brewer":"Hario","doseGrams":15,"waterGrams":250,
             "waterTempC":95,"steps":[{"kind":"POUR","startSec":0,"durationSec":30,
             "targetWaterGrams":250}]}]}
        """.trimIndent()

        assertFalse(RecipeJson.decode(json).first().aeropressMode)
    }
}
