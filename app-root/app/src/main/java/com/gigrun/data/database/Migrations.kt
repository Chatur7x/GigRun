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

/**
 * v6 → v7: Safety Net tables for Features 29/31/32.
 * Column types and nullability are copied verbatim from the exported v7 schema.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `insurance_policies` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `provider` TEXT NOT NULL, `policyNumber` TEXT NOT NULL, `type` TEXT NOT NULL, `premiumInr` REAL NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, `isPlatformProvided` INTEGER NOT NULL, `claimDeadlineDays` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `documents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `filePath` TEXT NOT NULL, `expiryDate` INTEGER, `notes` TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `shift_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `breakCount` INTEGER NOT NULL, `totalBreakMinutes` INTEGER NOT NULL, `fatigueScore` INTEGER NOT NULL)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_insurance_policies_endDate` ON `insurance_policies` (`endDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_insurance_policies_type` ON `insurance_policies` (`type`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_expiryDate` ON `documents` (`expiryDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_type` ON `documents` (`type`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shift_logs_startTime` ON `shift_logs` (`startTime`)")
    }
}

/**
 * v7 → v8: Self-Sufficiency tables for Features 22/23.
 * repair_guides and tools tables, seeded on first run via RoomDatabase.Callback.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `repair_guides` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `symptom` TEXT NOT NULL, `title` TEXT NOT NULL, `stepsJson` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `estimatedCostMinInr` INTEGER NOT NULL, `estimatedCostMaxInr` INTEGER NOT NULL, `toolsNeededJson` TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `tools` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `description` TEXT NOT NULL, `priceInr` INTEGER NOT NULL, `buyUrl` TEXT)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_repair_guides_symptom` ON `repair_guides` (`symptom`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_repair_guides_difficulty` ON `repair_guides` (`difficulty`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tools_category` ON `tools` (`category`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tools_name` ON `tools` (`name`)")
    }
}