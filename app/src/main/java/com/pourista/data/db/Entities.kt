package com.pourista.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Заваривание из истории.
 *
 * Ряды весов и скорости лежат строками с разделителем «;»: это график, который
 * читают целиком и никогда не выбирают по одной точке, так что отдельная
 * таблица на сотни строк ради каждой чашки не нужна.
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

/** Заметки к завариванию: зерно, обжарщик, кофемолка, помол, фильтр, температура. */
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

/** Рецепт: параметры заваривания и помола, шаги хранятся отдельно. */
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
    /** Бумага: «Hario», «Cafec Abaca». От неё чашка меняется не меньше, чем от помола. */
    @ColumnInfo(name = "filter_name") val filterName: String? = null,
    @ColumnInfo(name = "bean_name") val beanName: String?,
    @ColumnInfo(name = "roaster") val roaster: String?,
    @ColumnInfo(name = "notes") val notes: String?,
    @ColumnInfo(name = "is_built_in") val isBuiltIn: Boolean,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    /**
     * Взводить ли автостарт сразу после записи дозы. У пуроверов это удобно,
     * у эспрессо — нет: там таймер пускают вместе с помпой.
     */
    @ColumnInfo(name = "auto_start", defaultValue = "1") val autoStart: Boolean = true,
    /**
     * Режим аэропресса: без автофиниша и без сглаживания веса. Отжим роняет
     * показания, и это единственный способ увидеть на графике, когда он начался.
     */
    @ColumnInfo(name = "aeropress_mode", defaultValue = "0")
    val aeropressMode: Boolean = false,
    /** Порядок в списке: его задаёт человек, перетаскивая карточки. */
    @ColumnInfo(name = "sort_order", defaultValue = "1000") val sortOrder: Int = 1000,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long?,
)

/**
 * Шаг рецепта. [targetWaterGrams] — накопительная цель к концу шага, то есть
 * ровно то число, которое должно быть на весах: так подсказку не нужно
 * пересчитывать в уме во время пролива.
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
     * Скорость влива, г/с. Хранится именно она, а не время: при смене дозы
     * объём воды меняется, а комфортная скорость остаётся той же — время влива
     * из них считается. Ноль означает «не задана», тогда берётся типовая.
     */
    @ColumnInfo(name = "pour_flow_rate", defaultValue = "0") val pourFlowRate: Float = 0f,
    @ColumnInfo(name = "note") val note: String?,
)

data class RecipeWithSteps(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipe_id")
    val steps: List<RecipeStepEntity>,
)
