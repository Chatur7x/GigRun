package com.gigrun.data.database

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration4To5Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private lateinit var db: SupportSQLiteDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
    }

    @Test
    fun migration4To5CreatesPenaltiesTableAndPreservesData() {
        // Step 1: Create v4 database
        db = helper.createDatabase("test_migration", 4)

        // Insert a row into an existing v4 table (expenses) to prove data survives.
        // shiftId is NULL, not 1: expenses.shiftId is a FK to shifts(id), and
        // MigrationTestHelper enables PRAGMA foreign_keys=ON. Seeding an orphan
        // shiftId would fail in setup and read as a migration failure when the
        // migration itself is fine. NULL matches the entity (shiftId: Long? = null).
        db.execSQL(
            "INSERT INTO expenses (shiftId, category, amount, note, receiptUri, timestamp, isDeductible) " +
            "VALUES (NULL, 'FUEL', 500.0, 'Test expense', null, ${System.currentTimeMillis()}, 1)"
        )

        // Step 2: Run migration 4 -> 5
        val migratedDb = helper.runMigrationsAndValidate("test_migration", 5, true, MIGRATION_4_5)

        // Assert: penalties table exists
        val cursor = migratedDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='penalties'")
        assertThat(cursor.moveToFirst(), equalTo(true))
        assertThat(cursor.getString(0), equalTo("penalties"))
        cursor.close()

        // Assert: the previously-inserted expense row still exists (data preserved)
        val expenseCursor = migratedDb.query("SELECT COUNT(*) FROM expenses WHERE category = 'FUEL'")
        expenseCursor.moveToFirst()
        assertThat(expenseCursor.getInt(0), equalTo(1))
        expenseCursor.close()

        // Assert: indices exist
        val indexCursor = migratedDb.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name IN " +
            "('index_penalties_platform', 'index_penalties_timestamp', 'index_penalties_platform_timestamp')"
        )
        var indexCount = 0
        while (indexCursor.moveToNext()) {
            indexCount++
        }
        indexCursor.close()
        assertThat(indexCount, equalTo(3))

        // Also verify we can insert into the new penalties table
        migratedDb.execSQL(
            "INSERT INTO penalties (platform, amountInr, reason, timestamp, isDisputed) " +
            "VALUES ('Blinkit', 100.0, 'Test', ${System.currentTimeMillis()}, 0)"
        )
        val penaltyCursor = migratedDb.query("SELECT COUNT(*) FROM penalties")
        penaltyCursor.moveToFirst()
        assertThat(penaltyCursor.getInt(0), equalTo(1))
        penaltyCursor.close()

        migratedDb.close()
    }
}