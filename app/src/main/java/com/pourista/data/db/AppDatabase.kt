package com.pourista.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(
    version = 11,
    entities = [
        BrewEntity::class,
        BrewNotesEntity::class,
        RecipeEntity::class,
        RecipeStepEntity::class,
    ],
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun brewDao(): BrewDao
    abstract fun recipeDao(): RecipeDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val NAME = "coffee_scale.db"

        /** Имя файла из ранних сборок; при первом запуске он переезжает под новое. */
        private const val PREVIOUS_NAME = "futula_coffee_scale_database.db"

        fun build(context: Context): AppDatabase {
            renamePreviousFile(context)
            return Room
                .databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .build()
        }

        /**
         * Переименование файла базы. Room умеет менять схему, но не имя файла,
         * поэтому переносим его руками — до того, как база будет открыта.
         */
        private fun renamePreviousFile(context: Context) {
            val target = context.getDatabasePath(NAME)
            if (target.exists()) return
            val source = context.getDatabasePath(PREVIOUS_NAME)
            if (!source.exists()) return

            // Журнал и разделяемая память переезжают вместе с базой: без них
            // потерялись бы записи, не успевшие попасть в основной файл.
            val moved = listOf("", "-wal", "-shm").all { suffix ->
                val from = File(source.path + suffix)
                if (!from.exists()) true else from.renameTo(File(target.path + suffix))
            }
            Log.i(TAG, if (moved) "База переименована в $NAME" else "Не удалось переименовать базу")
        }

        /** Версия 11: фильтр дописывается и в заметки к завариванию. */
        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `brew_notes` ADD COLUMN `filter_name` TEXT")
            }
        }

        /** Версия 10: у рецепта появился фильтр. */
        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recipes` ADD COLUMN `filter_name` TEXT")
            }
        }

        /** Версия 9: у рецепта появился режим аэропресса. */
        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `recipes` ADD COLUMN `aeropress_mode` " +
                        "INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Версия 8: имена таблиц и колонок больше не тянутся из первых версий.
         * Заодно ушли поля, которые дублировали уже сохранённое: единица веса
         * (всегда граммы), готовые строки времени и пропорции — они считаются
         * из длительности и веса при показе.
         */
        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `brews` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`brewed_at` INTEGER NOT NULL, " +
                        "`dose_grams` REAL NOT NULL, " +
                        "`weight_grams` REAL NOT NULL, " +
                        "`elapsed_ms` INTEGER NOT NULL, " +
                        "`flow_rate_avg` REAL NOT NULL, " +
                        "`weight_series` TEXT NOT NULL, " +
                        "`flow_series` TEXT NOT NULL, " +
                        "`recipe_id` INTEGER, " +
                        "`recipe_name` TEXT)"
                )
                db.execSQL(
                    "INSERT INTO `brews` (`id`, `brewed_at`, `dose_grams`, `weight_grams`," +
                        " `elapsed_ms`, `flow_rate_avg`, `weight_series`, `flow_series`," +
                        " `recipe_id`, `recipe_name`) " +
                        "SELECT `id`, `brew_date`, `dose_record`, `weight_record`, 0," +
                        " `flow_rate_avg`, `weight_log`, `flow_rate_log`, `recipe_id`, `recipe_name`" +
                        " FROM `weight_history`"
                )
                copyElapsedTime(db)

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `brew_notes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`brew_id` INTEGER NOT NULL, " +
                        "`bean` TEXT, `roaster` TEXT, `grinder` TEXT, `grind_setting` TEXT, " +
                        "`brewer` TEXT, `water_temp` TEXT, `note` TEXT, " +
                        "FOREIGN KEY(`brew_id`) REFERENCES `brews`(`id`)" +
                        " ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brew_notes_brew_id` ON `brew_notes` (`brew_id`)")
                db.execSQL(
                    "INSERT INTO `brew_notes` (`id`, `brew_id`, `bean`, `roaster`, `grinder`," +
                        " `grind_setting`, `brewer`, `water_temp`, `note`) " +
                        "SELECT `id`, `weight_id`, `coffee_bean`, `coffee_roaster`, `coffee_grinder`," +
                        " `coffee_grinder_level`, `gadget_name`, `water_temp`, `extra_info`" +
                        " FROM `weight_history_extra`" +
                        " WHERE `weight_id` IN (SELECT `id` FROM `brews`)"
                )

                db.execSQL("DROP TABLE `weight_history_extra`")
                db.execSQL("DROP TABLE `weight_history`")
            }
        }

        /**
         * Длительность раньше лежала готовой строкой «1:23.4». Разбираем её
         * обратно в миллисекунды, иначе у старых чашек пропало бы время.
         */
        private fun copyElapsedTime(db: SupportSQLiteDatabase) {
            db.query("SELECT `id`, `time_string` FROM `weight_history`").use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val millis = parseTimer(cursor.getString(1))
                    db.execSQL("UPDATE `brews` SET `elapsed_ms` = ? WHERE `id` = ?", arrayOf(millis, id))
                }
            }
        }

        /** «1:23.4» → 83 400 мс. Непонятную строку считаем нулём, а не падаем. */
        internal fun parseTimer(value: String?): Long {
            val text = value?.trim().orEmpty()
            val match = Regex("""^(\d+):(\d{1,2})(?:\.(\d))?$""").find(text) ?: return 0L
            val (minutes, seconds, tenths) = match.destructured
            return minutes.toLong() * 60_000 +
                seconds.toLong() * 1_000 +
                (tenths.toLongOrNull() ?: 0L) * 100
        }
    }
}
