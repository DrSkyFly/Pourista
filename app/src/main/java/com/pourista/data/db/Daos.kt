package com.pourista.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BrewDao {

    @Insert
    suspend fun insertBrew(brew: BrewEntity): Long

    @Insert
    suspend fun insertNotes(notes: BrewNotesEntity): Long

    @Update
    suspend fun updateNotes(notes: BrewNotesEntity)

    @Transaction
    @Query("SELECT * FROM brews ORDER BY brewed_at DESC")
    fun observeBrews(): Flow<List<BrewWithNotes>>

    @Transaction
    @Query(
        """
        SELECT b.* FROM brews b
        LEFT JOIN brew_notes n ON b.id = n.brew_id
        WHERE LOWER(IFNULL(n.bean, '')) LIKE '%' || :query || '%'
           OR LOWER(IFNULL(n.roaster, '')) LIKE '%' || :query || '%'
           OR LOWER(IFNULL(n.grinder, '')) LIKE '%' || :query || '%'
           OR LOWER(IFNULL(n.brewer, '')) LIKE '%' || :query || '%'
           OR LOWER(IFNULL(n.note, '')) LIKE '%' || :query || '%'
           OR LOWER(IFNULL(b.recipe_name, '')) LIKE '%' || :query || '%'
        ORDER BY b.brewed_at DESC
        """
    )
    fun searchBrews(query: String): Flow<List<BrewWithNotes>>

    @Transaction
    @Query("SELECT * FROM brews WHERE id = :id")
    fun observeBrew(id: Long): Flow<BrewWithNotes?>

    /** The whole history at once — for a backup. */
    @Transaction
    @Query("SELECT * FROM brews ORDER BY brewed_at ASC")
    suspend fun allBrews(): List<BrewWithNotes>

    /**
     * The times of brews already written down. Duplicates are filtered out by them when
     * restoring: the millisecond of the start is as good a key as any other, and the
     * record has none of its own.
     */
    @Query("SELECT brewed_at FROM brews")
    suspend fun brewTimestamps(): List<Long>

    /** The notes leave by themselves: they have a foreign key with cascading delete. */
    @Query("DELETE FROM brews WHERE id = :id")
    suspend fun deleteBrew(id: Long)

    @Query("SELECT * FROM brew_notes WHERE brew_id = :brewId LIMIT 1")
    suspend fun notesForBrew(brewId: Long): BrewNotesEntity?

    @Query("SELECT DISTINCT bean FROM brew_notes WHERE bean IS NOT NULL AND length(bean) > 0 ORDER BY bean")
    fun observeBeans(): Flow<List<String>>

    @Query("SELECT DISTINCT roaster FROM brew_notes WHERE roaster IS NOT NULL AND length(roaster) > 0 ORDER BY roaster")
    fun observeRoasters(): Flow<List<String>>

    @Query("SELECT DISTINCT grinder FROM brew_notes WHERE grinder IS NOT NULL AND length(grinder) > 0 ORDER BY grinder")
    fun observeGrinders(): Flow<List<String>>
}

@Dao
interface RecipeDao {

    /**
     * The order is set by the person dragging the cards, so we sort by sort_order alone:
     * any other key would break what is on the screen.
     */
    @Transaction
    @Query("SELECT * FROM recipes ORDER BY sort_order ASC, name COLLATE NOCASE ASC")
    fun observeRecipes(): Flow<List<RecipeWithSteps>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeRecipe(id: Long): Flow<RecipeWithSteps?>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun recipeById(id: Long): RecipeWithSteps?

    /** All the recipes at once — for a backup. */
    @Transaction
    @Query("SELECT * FROM recipes ORDER BY sort_order ASC, name COLLATE NOCASE ASC")
    suspend fun allRecipes(): List<RecipeWithSteps>

    @Query("SELECT name FROM recipes")
    suspend fun recipeNames(): List<String>

    @Query("SELECT MIN(sort_order) FROM recipes")
    suspend fun minSortOrder(): Int?

    @Query("UPDATE recipes SET sort_order = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    /** Lays the order out with room between neighbours — easier to move things later. */
    @Transaction
    suspend fun renumber(ids: List<Long>) {
        ids.forEachIndexed { index, id -> setSortOrder(id, (index + 1) * 10) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteRecipeById(id: Long)

    /**
     * Built-in recipes the user has not touched: an edit changes updated_at, so their own
     * versions of recipes survive an update of the set.
     */
    @Query("DELETE FROM recipes WHERE is_built_in = 1 AND updated_at = created_at AND is_favorite = 0")
    suspend fun deleteUntouchedBuiltIns(): Int

    /**
     * Translates the texts of a built-in recipe into the current language. We update in
     * place rather than reseeding: the recipe keeps its id, its position in the list and
     * its favourite mark. The recipe name does not depend on the language, so it works as
     * a key, and updated_at is deliberately left alone — otherwise the recipe would count
     * as "edited by hand".
     */
    @Query(
        """
        UPDATE recipes SET notes = :notes, grind_setting = :grind
        WHERE name = :name AND is_built_in = 1 AND updated_at = created_at
        """
    )
    suspend fun localizeBuiltIn(name: String, notes: String?, grind: String?)

    @Insert
    suspend fun insertSteps(steps: List<RecipeStepEntity>)

    @Query("DELETE FROM recipe_steps WHERE recipe_id = :recipeId")
    suspend fun deleteStepsFor(recipeId: Long)

    @Query("UPDATE recipes SET last_used_at = :timestamp WHERE id = :id")
    suspend fun markUsed(id: Long, timestamp: Long)

    @Query("UPDATE recipes SET is_favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Transaction
    suspend fun upsertRecipeWithSteps(recipe: RecipeEntity, steps: List<RecipeStepEntity>): Long {
        val recipeId = if (recipe.id == 0L) {
            insertRecipe(recipe)
        } else {
            updateRecipe(recipe)
            deleteStepsFor(recipe.id)
            recipe.id
        }
        insertSteps(steps.map { it.copy(id = 0, recipeId = recipeId) })
        return recipeId
    }
}
