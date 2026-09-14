package com.pourista.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * A brew from the history.
 *
 * The weight and flow series lie in strings separated by ";": this is a chart, read whole
 * and never queried a point at a time, so a separate table of hundreds of rows for every
 * cup is not needed.
 */
@Entity(tableName = "brews")
data class BrewEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "brewed_at") val brewedAt: Long,
    @ColumnInfo(name = "dose_grams") val doseGrams: Float,
    @ColumnInfo(name = "weight_grams") val weightGrams: Float,
    @ColumnInfo(name = "elapsed_ms") val elapsedMs: Long,
    @ColumnInfo(name = "flow_rate_avg") val flowRateAvg: Float,
    @ColumnInfo(name = "weight_series") val weightSeries: String,
    @ColumnInfo(name = "flow_series") val flowSeries: String,
    @ColumnInfo(name = "recipe_id") val recipeId: Long? = null,
    @ColumnInfo(name = "recipe_name") val recipeName: String? = null,
)

/** Notes on a brew: bean, roaster, grinder, grind setting, filter, temperature. */
@Entity(
    tableName = "brew_notes",
    foreignKeys = [
        ForeignKey(
            entity = BrewEntity::class,
            parentColumns = ["id"],
            childColumns = ["brew_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("brew_id")],
)
data class BrewNotesEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "brew_id") val brewId: Long,
    @ColumnInfo(name = "bean") val bean: String?,
    @ColumnInfo(name = "roaster") val roaster: String?,
    @ColumnInfo(name = "grinder") val grinder: String?,
    @ColumnInfo(name = "grind_setting") val grindSetting: String?,
    @ColumnInfo(name = "filter_name") val filterName: String? = null,
    @ColumnInfo(name = "brewer") val brewer: String?,
    @ColumnInfo(name = "water_temp") val waterTemp: String?,
    @ColumnInfo(name = "note") val note: String?,
)

data class BrewWithNotes(
    @Embedded val brew: BrewEntity,
    @Relation(parentColumn = "id", entityColumn = "brew_id")
    val notes: BrewNotesEntity?,
)

/** A recipe: brewing and grinding parameters; the steps are stored separately. */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "brewer") val brewer: String,
    @ColumnInfo(name = "dose_grams") val doseGrams: Float,
    @ColumnInfo(name = "water_grams") val waterGrams: Float,
    @ColumnInfo(name = "water_temp_c") val waterTempC: Int,
    @ColumnInfo(name = "grinder_name") val grinderName: String?,
    @ColumnInfo(name = "grind_setting") val grindSetting: String?,
    /** Paper: "Hario", "Cafec Abaca". It changes the cup no less than the grind does. */
    @ColumnInfo(name = "filter_name") val filterName: String? = null,
    @ColumnInfo(name = "bean_name") val beanName: String?,
    @ColumnInfo(name = "roaster") val roaster: String?,
    @ColumnInfo(name = "notes") val notes: String?,
    @ColumnInfo(name = "is_built_in") val isBuiltIn: Boolean,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    /**
     * Whether to arm auto-start right after the dose is recorded. On pour-overs that is
     * convenient, on espresso it is not: there the timer starts with the pump.
     */
    @ColumnInfo(name = "auto_start", defaultValue = "1") val autoStart: Boolean = true,
    /**
     * Aeropress mode: no auto-finish and no weight smoothing. The press drops the
     * readings, and this is the only way to see on the chart when it began.
     */
    @ColumnInfo(name = "aeropress_mode", defaultValue = "0")
    val aeropressMode: Boolean = false,
    /** Position in the list: set by the person dragging the cards. */
    @ColumnInfo(name = "sort_order", defaultValue = "1000") val sortOrder: Int = 1000,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long?,
)

/**
 * A recipe step. [targetWaterGrams] is the cumulative target for the end of the step,
 * that is exactly the number that should be on the scale: this way the guidance does not
 * have to be recalculated in one head while pouring.
 */
@Entity(
    tableName = "recipe_steps",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("recipe_id")],
)
data class RecipeStepEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "recipe_id") val recipeId: Long,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "start_sec") val startSec: Int,
    @ColumnInfo(name = "duration_sec") val durationSec: Int,
    @ColumnInfo(name = "target_water_grams") val targetWaterGrams: Float,
    /**
     * Flow rate, g/s. It is this that is stored rather than the time: when the dose
     * changes the water volume changes with it, while the comfortable rate stays the
     * same — the pour time is counted from the two. Zero means "unset", and then the
     * typical one is used.
     */
    @ColumnInfo(name = "pour_flow_rate", defaultValue = "0") val pourFlowRate: Float = 0f,
    @ColumnInfo(name = "note") val note: String?,
)

data class RecipeWithSteps(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipe_id")
    val steps: List<RecipeStepEntity>,
)
