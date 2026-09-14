package com.pourista.data.io

import com.pourista.data.model.BrewNotes
import com.pourista.data.model.BrewRecord
import com.pourista.data.model.Recipe
import com.pourista.data.model.RecipeStep
import com.pourista.data.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonTest {

    private val recipe = Recipe(
        id = 7,
        name = "V60 · morning",
        brewer = "Hario V60-02",
        doseGrams = 15f,
        waterGrams = 250f,
        waterTempC = 93,
        grindSetting = "coarse",
        filterName = "Hario",
        isFavorite = true,
        sortOrder = 20,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_500_000L,
        lastUsedAt = 1_700_000_900_000L,
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
                durationSec = 45,
                targetWaterGrams = 250f,
                pourFlowRate = 4.5f,
            ),
        ),
    )

    private val brew = BrewRecord(
        id = 42,
        brewedAt = 1_700_001_000_000L,
        doseGrams = 15.2f,
        weightGrams = 251.4f,
        elapsedMs = 214_000L,
        weightSeries = listOf(0f, 41.5f, 120f, 251.4f),
        flowSeries = listOf(0f, 4.1f, 5.2f, 0f),
        flowRateAvg = 4.7f,
        recipeId = 7,
        recipeName = "V60 · morning",
        notes = BrewNotes(
            bean = "Ethiopia Guji",
            roaster = "Coffee Snob",
            grindSetting = "24 clicks",
            filterName = "Cafec Abaca",
            extra = "sour",
        ),
    )

    @Test
    fun `a backup carries a recipe together with its favourite mark and order`() {
        val text = BackupJson.encode(listOf(recipe), emptyList(), now = 1_700_002_000_000L)
        val back = BackupJson.decode(text).recipes.single()

        assertEquals(recipe.name, back.name)
        assertEquals(recipe.doseGrams, back.doseGrams, 0.01f)
        assertEquals(recipe.waterTempC, back.waterTempC)
        assertEquals(recipe.filterName, back.filterName)
        assertTrue("the favourite mark must not be lost", back.isFavorite)
        assertEquals(recipe.sortOrder, back.sortOrder)
        assertEquals(recipe.createdAt, back.createdAt)
        assertEquals(recipe.lastUsedAt, back.lastUsedAt)
        assertEquals(recipe.steps.size, back.steps.size)
        assertEquals(250f, back.steps.last().targetWaterGrams, 0.01f)
        assertEquals(4.5f, back.steps.last().pourFlowRate, 0.01f)
    }

    @Test
    fun `a backup carries a brew with its charts and notes`() {
        val text = BackupJson.encode(emptyList(), listOf(brew), now = 1_700_002_000_000L)
        val back = BackupJson.decode(text).brews.single()

        assertEquals(brew.brewedAt, back.brewedAt)
        assertEquals(brew.weightGrams, back.weightGrams, 0.01f)
        assertEquals(brew.elapsedMs, back.elapsedMs)
        assertEquals(brew.weightSeries, back.weightSeries)
        assertEquals(brew.flowSeries, back.flowSeries)
        assertEquals("Ethiopia Guji", back.notes.bean)
        assertEquals("24 clicks", back.notes.grindSetting)
        assertEquals("Cafec Abaca", back.notes.filterName)
        assertEquals("sour", back.notes.extra)
        assertEquals(brew.recipeName, back.recipeName)
        // On a new installation the recipe lies under a different id: the link is not carried over.
        assertNull(back.recipeId)
    }

    @Test
    fun `a recipe file is not passed off as a backup`() {
        val recipes = RecipeJson.encode(listOf(recipe))
        assertThrows(IllegalArgumentException::class.java) { BackupJson.decode(recipes) }
    }

    @Test
    fun `a backup from a future version is not parsed`() {
        val text = BackupJson.encode(emptyList(), emptyList(), now = 0L)
            .replace("\"version\": 1", "\"version\": 99")
        assertThrows(IllegalArgumentException::class.java) { BackupJson.decode(text) }
    }

    @Test
    fun `a brew with no time is skipped and the rest is read`() {
        val text = """
            {
              "format": "pourista.backup",
              "version": 1,
              "recipes": [],
              "brews": [
                { "dose": 15 },
                { "at": 1700001000000, "dose": 15, "weight": 250 }
              ]
            }
        """.trimIndent()
        val brews = BackupJson.decode(text).brews
        assertEquals(1, brews.size)
        assertEquals(250f, brews.single().weightGrams, 0.01f)
    }
}
