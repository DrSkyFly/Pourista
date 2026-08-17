package com.pourista.data.repo

import com.pourista.data.db.RecipeDao
import com.pourista.data.model.Recipe
import com.pourista.data.model.toDomain
import com.pourista.data.model.toEntities
import com.pourista.data.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecipeRepository(private val dao: RecipeDao) {

    fun observeRecipes(): Flow<List<Recipe>> =
        dao.observeRecipes().map { rows -> rows.map { it.toDomain() } }

    fun observeRecipe(id: Long): Flow<Recipe?> =
        dao.observeRecipe(id).map { it?.toDomain() }

    suspend fun recipeById(id: Long): Recipe? = dao.recipeById(id)?.toDomain()

    suspend fun save(recipe: Recipe, now: Long = System.currentTimeMillis()): Long {
        val id = dao.upsertRecipeWithSteps(
            recipe = recipe.toEntity(now),
            steps = recipe.steps.toEntities(recipe.id),
        )
        return id
    }

    /**
     * Новый рецепт встаёт в начало списка: его завели, чтобы заварить, а не
     * чтобы искать в хвосте среди встроенных.
     */
    suspend fun saveNewOnTop(recipe: Recipe, now: Long = System.currentTimeMillis()): Long {
        val top = (dao.minSortOrder() ?: TOP_SORT_ORDER) - SORT_ORDER_GAP
        return save(recipe.copy(sortOrder = top), now)
    }

    suspend fun delete(recipeId: Long) = dao.deleteRecipeById(recipeId)

    /**
     * Записывает новый порядок списка. Перенумеровываем все строки: у рецептов
     * из старых версий базы sort_order мог совпадать.
     */
    suspend fun reorder(ids: List<Long>) = dao.renumber(ids)

    suspend fun markUsed(recipeId: Long, now: Long = System.currentTimeMillis()) =
        dao.markUsed(recipeId, now)

    suspend fun setFavorite(recipeId: Long, favorite: Boolean) =
        dao.setFavorite(recipeId, favorite)

    suspend fun deleteUntouchedBuiltIns(): Int = dao.deleteUntouchedBuiltIns()

    /** Подставляет тексты встроенного рецепта на текущем языке приложения. */
    suspend fun relocalizeBuiltIn(recipe: Recipe) =
        dao.localizeBuiltIn(recipe.name, recipe.notes, recipe.grindSetting)

    /** Копия рецепта для правки: встроенные рецепты не редактируются на месте. */
    suspend fun duplicate(recipe: Recipe, newName: String): Long = saveNewOnTop(
        recipe.copy(
            id = 0,
            name = newName,
            isBuiltIn = false,
            isFavorite = false,
            createdAt = 0,
            lastUsedAt = null,
            steps = recipe.steps.map { it.copy(id = 0) },
        )
    )

    /** Импортированные рецепты ложатся сверху, в порядке файла. */
    suspend fun importAll(recipes: List<Recipe>): Int {
        recipes.reversed().forEach { recipe ->
            saveNewOnTop(
                recipe.copy(
                    id = 0,
                    isBuiltIn = false,
                    createdAt = 0,
                    lastUsedAt = null,
                    steps = recipe.steps.map { it.copy(id = 0) },
                )
            )
        }
        return recipes.size
    }

    private companion object {
        const val TOP_SORT_ORDER = 10
        const val SORT_ORDER_GAP = 10
    }
}
