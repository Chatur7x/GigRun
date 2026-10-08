package com.gigrun.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v4 → v5: penalties table for Feature 27 (Penalty Tracker).
 * Idempotent — safe to run on any v4 database.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `penalties` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`platform` TEXT NOT NULL, " +
                "`amountInr` REAL NOT NULL, " +
                "`reason` TEXT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`isDisputed` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_penalties_platform` ON `penalties` (`platform`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_penalties_timestamp` ON `penalties` (`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_penalties_platform_timestamp` ON `penalties` (`platform`, `timestamp`)")
    }
}

/**
 * v5 → v6: extend the vehicles table for Feature 21 (Your Vehicle).
 * All columns are nullable with no DEFAULT so the schema matches the entity
 * exactly and no "DEFAULT mismatch" at validation (Feature 27 rule).
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vehicles ADD COLUMN fuelType TEXT")
        db.execSQL("ALTER TABLE vehicles ADD COLUMN claimedKmpl REAL")
        db.execSQL("ALTER TABLE vehicles ADD COLUMN tankCapacityLitres REAL")
        db.execSQL("ALTER TABLE vehicles ADD COLUMN emiPerMonth REAL")
        db.execSQL("ALTER TABLE vehicles ADD COLUMN insurancePerYear REAL")
        db.execSQL("ALTER TABLE vehicles ADD COLUMN phoneBillPerMonth REAL")
    }
}
