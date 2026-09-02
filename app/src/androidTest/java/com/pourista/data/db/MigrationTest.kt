package com.pourista.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Переход на схему без наследия: таблицы и колонки переименованы, готовые
 * строки времени и пропорции заменены длительностью в миллисекундах.
 * Проверяем, что история завариваний это переживает.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate7To8_keepsBrewsNotesAndRecipes() {
        helper.createDatabase(TEST_DB, 7).use { db ->
            db.execSQL(
                """
                INSERT INTO weight_history
                (brew_date, weight_unit, dose_record, weight_record, weight_log,
                 flow_rate, flow_rate_avg, flow_rate_log, time_string, brew_ratio_string,
                 recipe_id, recipe_name)
                VALUES (1700000000000, 'g', 15.0, 250.0, '0;50;150;250',
                        2.5, 2.1, '0;2.5;3.0;2.0', '3:05.4', '1:16.7', 42, 'V60')
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO weight_history_extra
                (weight_id, coffee_bean, coffee_grinder, coffee_grinder_level,
                 gadget_name, water_temp, extra_info, coffee_roaster)
                VALUES (1, 'Ethiopia', 'Comandante', '24', 'V60', '95', 'проверка', 'Roaster')
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO recipes
                (name, brewer, dose_grams, water_grams, water_temp_c, grinder_name,
                 grind_setting, bean_name, roaster, notes, is_built_in, is_favorite,
                 auto_start, sort_order, created_at, updated_at, last_used_at)
                VALUES ('V60', 'Hario V60-02', 15.0, 250.0, 95, 'Comandante',
                        '24', NULL, NULL, NULL, 0, 0, 1, 10, 1700000000000, 1700000000000, NULL)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 8, true, AppDatabase.MIGRATION_7_8)

        db.query(
            "SELECT brewed_at, dose_grams, weight_grams, elapsed_ms, flow_rate_avg," +
                " weight_series, flow_series, recipe_id, recipe_name FROM brews"
        ).use { cursor ->
            assertTrue("Заваривание должно пережить миграцию", cursor.moveToFirst())
            assertEquals(1_700_000_000_000L, cursor.getLong(0))
            assertEquals(15.0f, cursor.getFloat(1), 0.01f)
            assertEquals(250.0f, cursor.getFloat(2), 0.01f)
            assertEquals("«3:05.4» в миллисекундах", 185_400L, cursor.getLong(3))
            assertEquals(2.1f, cursor.getFloat(4), 0.01f)
            assertEquals("0;50;150;250", cursor.getString(5))
            assertEquals("0;2.5;3.0;2.0", cursor.getString(6))
            assertEquals(42L, cursor.getLong(7))
            assertEquals("V60", cursor.getString(8))
        }

        db.query(
            "SELECT brew_id, bean, roaster, grinder, grind_setting, brewer, water_temp, note" +
                " FROM brew_notes"
        ).use { cursor ->
            assertTrue("Заметка должна пережить миграцию", cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals("Ethiopia", cursor.getString(1))
            assertEquals("Roaster", cursor.getString(2))
            assertEquals("Comandante", cursor.getString(3))
            assertEquals("24", cursor.getString(4))
            assertEquals("V60", cursor.getString(5))
            assertEquals("95", cursor.getString(6))
            assertEquals("проверка", cursor.getString(7))
        }

        db.query("SELECT name, auto_start, sort_order FROM recipes").use { cursor ->
            assertTrue("Рецепты миграция не трогает", cursor.moveToFirst())
            assertEquals("V60", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(10, cursor.getInt(2))
        }
    }

    /** Режим аэропресса добавляется колонкой: старые рецепты получают ноль. */
    @Test
    fun migrate8To9_addsAeropressMode() {
        helper.createDatabase(TEST_DB_AEROPRESS, 8).use { db ->
            db.execSQL(
                """
                INSERT INTO recipes
                (name, brewer, dose_grams, water_grams, water_temp_c, grinder_name,
                 grind_setting, bean_name, roaster, notes, is_built_in, is_favorite,
                 auto_start, sort_order, created_at, updated_at, last_used_at)
                VALUES ('AeroPress', 'AeroPress', 15.0, 220.0, 85, NULL,
                        NULL, NULL, NULL, NULL, 1, 0, 1, 50, 1700000000000, 1700000000000, NULL)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB_AEROPRESS,
            9,
            true,
            AppDatabase.MIGRATION_8_9,
        )

        db.query("SELECT name, aeropress_mode FROM recipes").use { cursor ->
            assertTrue("Рецепт должен пережить миграцию", cursor.moveToFirst())
            assertEquals("AeroPress", cursor.getString(0))
            assertEquals("Старым рецептам режим не включаем", 0, cursor.getInt(1))
        }
    }

    /** Фильтр добавляется колонкой: у старых рецептов он пустой. */
    @Test
    fun migrate9To10_addsFilter() {
        helper.createDatabase(TEST_DB_FILTER, 9).use { db ->
            db.execSQL(
                """
                INSERT INTO recipes
                (name, brewer, dose_grams, water_grams, water_temp_c, grinder_name,
                 grind_setting, bean_name, roaster, notes, is_built_in, is_favorite,
                 auto_start, aeropress_mode, sort_order, created_at, updated_at, last_used_at)
                VALUES ('V60', 'Hario V60-02', 15.0, 250.0, 95, NULL,
                        '24', NULL, NULL, NULL, 0, 0, 1, 0, 10, 1700000000000, 1700000000000, NULL)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB_FILTER,
            10,
            true,
            AppDatabase.MIGRATION_9_10,
        )

        db.query("SELECT name, grind_setting, filter_name FROM recipes").use { cursor ->
            assertTrue("Рецепт должен пережить миграцию", cursor.moveToFirst())
            assertEquals("V60", cursor.getString(0))
            assertEquals("24", cursor.getString(1))
            assertTrue("Фильтра у старого рецепта нет", cursor.isNull(2))
        }
    }

    /** Фильтр дописывается и в заметки: у старых записей он пустой. */
    @Test
    fun migrate10To11_addsFilterToNotes() {
        helper.createDatabase(TEST_DB_NOTES_FILTER, 10).use { db ->
            db.execSQL(
                """
                INSERT INTO brews
                (brewed_at, dose_grams, weight_grams, elapsed_ms, flow_rate_avg,
                 weight_series, flow_series, recipe_id, recipe_name)
                VALUES (1700000000000, 15.0, 250.0, 185400, 2.1, '0;250', '0;2.1', NULL, 'V60')
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO brew_notes
                (brew_id, bean, roaster, grinder, grind_setting, brewer, water_temp, note)
                VALUES (1, 'Ethiopia', 'Roaster', 'Comandante', '24', 'V60', '95', NULL)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB_NOTES_FILTER,
            11,
            true,
            AppDatabase.MIGRATION_10_11,
        )

        db.query("SELECT grind_setting, filter_name FROM brew_notes").use { cursor ->
            assertTrue("Заметка должна пережить миграцию", cursor.moveToFirst())
            assertEquals("24", cursor.getString(0))
            assertTrue("Фильтра у старой записи нет", cursor.isNull(1))
        }
    }

    /** Заметка без заваривания не должна ломать внешний ключ новой таблицы. */
    @Test
    fun migrate7To8_dropsOrphanNotes() {
        helper.createDatabase(TEST_DB_ORPHAN, 7).use { db ->
            db.execSQL(
                """
                INSERT INTO weight_history_extra
                (weight_id, coffee_bean, coffee_grinder, coffee_grinder_level,
                 gadget_name, water_temp, extra_info, coffee_roaster)
                VALUES (999, 'Ничей', NULL, NULL, NULL, NULL, NULL, NULL)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB_ORPHAN, 8, true, AppDatabase.MIGRATION_7_8)

        db.query("SELECT COUNT(*) FROM brew_notes").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
        const val TEST_DB_ORPHAN = "migration-test-orphan.db"
        const val TEST_DB_AEROPRESS = "migration-test-aeropress.db"
        const val TEST_DB_FILTER = "migration-test-filter.db"
        const val TEST_DB_NOTES_FILTER = "migration-test-notes-filter.db"
    }
}
