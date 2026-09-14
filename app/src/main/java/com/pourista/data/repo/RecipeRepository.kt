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
     * A new recipe goes to the top of the list: it was created to be brewed, not to be
     * looked for at the tail among the built-in ones.
     */
    suspend fun saveNewOnTop(recipe: Recipe, now: Long = System.currentTimeMillis()): Long {
        val top = (dao.minSortOrder() ?: TOP_SORT_ORDER) - SORT_ORDER_GAP
        return save(recipe.copy(sortOrder = top), now)
    }

    suspend fun delete(recipeId: Long) = dao.deleteRecipeById(recipeId)

    /**
     * Writes the new order of the list. All the rows are renumbered: recipes from older
     * database versions could share a sort_order.
     */
    suspend fun reorder(ids: List<Long>) = dao.renumber(ids)

    suspend fun markUsed(recipeId: Long, now: Long = System.currentTimeMillis()) =
        dao.markUsed(recipeId, now)

    suspend fun setFavorite(recipeId: Long, favorite: Boolean) =
        dao.setFavorite(recipeId, favorite)

    suspend fun deleteUntouchedBuiltIns(): Int = dao.deleteUntouchedBuiltIns()

    /** Puts in the texts of a built-in recipe in the current app language. */
    suspend fun relocalizeBuiltIn(recipe: Recipe) =
        dao.localizeBuiltIn(recipe.name, recipe.notes, recipe.grindSetting)

    /** A copy of a recipe for editing: built-in recipes are not edited in place. */
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

    /**
     * Imported recipes land on top, in the order of the file. Returns their ids: a file
     * opened from outside is taken into work at once, and that needs a recipe rather than
     * a number.
     */
    suspend fun importAll(recipes: List<Recipe>): List<Long> =
        recipes.reversed().map { recipe ->
            saveNewOnTop(
                recipe.copy(
                    id = 0,
                    isBuiltIn = false,
                    createdAt = 0,
                    lastUsedAt = null,
                    steps = recipe.steps.map { it.copy(id = 0) },
                )
            )
        }.reversed()

    /** Everything in the database — for a backup. */
    suspend fun exportAll(): List<Recipe> = dao.allRecipes().map { it.toDomain() }

    /**
     * Restoring from a backup. A recipe with the same name is skipped: a fresh installation
     * already has the built-in set seeded, and restoring twice should not breed twins.
     * Everything else carries over as it is — favourites, order, times.
     */
    suspend fun restoreAll(recipes: List<Recipe>): Int {
        val known = dao.recipeNames().map { it.lowercase() }.toMutableSet()
        var restored = 0
        recipes.forEach { recipe ->
            if (!known.add(recipe.name.lowercase())) return@forEach
            dao.upsertRecipeWithSteps(
                recipe = recipe.copy(id = 0).toEntity(recipe.updatedAt.takeIf { it > 0 }
                    ?: System.currentTimeMillis()),
                steps = recipe.steps.map { it.copy(id = 0) }.toEntities(0),
            )
            restored++
        }
        return restored
    }

    private companion object {
        const val TOP_SORT_ORDER = 10
        const val SORT_ORDER_GAP = 10
    }
}
