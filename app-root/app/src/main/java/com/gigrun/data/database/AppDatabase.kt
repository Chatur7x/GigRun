package com.gigrun.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gigrun.data.database.dao.*
import com.gigrun.data.database.entities.*

@Database(
    entities = [
        Shift::class,
        Trip::class,
        Earning::class,
        ServiceReminder::class,
        Vehicle::class,
        FuelLog::class,
        Block::class,
        TempTransaction::class,
        Expense::class,
        EarningsGoal::class,
        Penalty::class,
        InsurancePolicy::class,
        Document::class,
        ShiftLog::class,
        RepairGuide::class,
        Tool::class
    ],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shiftDao(): ShiftDao
    abstract fun tripDao(): TripDao
    abstract fun earningDao(): EarningDao
    abstract fun serviceReminderDao(): ServiceReminderDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun fuelLogDao(): FuelLogDao
    abstract fun blockDao(): BlockDao
    abstract fun tempTransactionDao(): TempTransactionDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun earningsGoalDao(): EarningsGoalDao
    abstract fun penaltyDao(): PenaltyDao
    abstract fun insuranceDao(): InsuranceDao
    abstract fun documentDao(): DocumentDao
    abstract fun shiftLogDao(): ShiftLogDao
    abstract fun repairGuideDao(): RepairGuideDao
    abstract fun toolDao(): ToolDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v2 → v3: new tables + Trip surge/bonus/tip columns + ledger indices.
         * v1 → v3: same body (v1 never had the v2 delta either).
         * All statements are idempotent (IF NOT EXISTS / pragma-guarded ADD
         * COLUMN) so a half-migrated DB can never abort an upgrade.
         */
        private fun migrateToV3(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `vehicles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL, `company` TEXT NOT NULL, `model` TEXT NOT NULL, `currentOdometer` REAL NOT NULL, `isSelected` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `fuel_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `vehicleId` INTEGER NOT NULL, `amountInr` REAL NOT NULL, `liters` REAL NOT NULL, `odometer` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `isClosed` INTEGER NOT NULL, `closedOdometer` REAL, `calculatedMileage` REAL, FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `blocks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `index` INTEGER NOT NULL, `previousHash` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `dataPayload` TEXT NOT NULL, `nonce` INTEGER NOT NULL, `hash` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `temp_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `serializedData` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `shiftId` INTEGER, `category` TEXT NOT NULL, `amount` REAL NOT NULL, `note` TEXT, `receiptUri` TEXT, `timestamp` INTEGER NOT NULL, `isDeductible` INTEGER NOT NULL, FOREIGN KEY(`shiftId`) REFERENCES `shifts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `earnings_goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dailyTarget` REAL NOT NULL, `weeklyTarget` REAL NOT NULL, `monthlyTarget` REAL NOT NULL, `autoCalculate` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL)")
                // Trip surge/bonus/tip breakdown columns (v3 delta).
                addColumnIfMissing(db, "trips", "baseFare", "REAL")
                addColumnIfMissing(db, "trips", "surgeAmount", "REAL")
                addColumnIfMissing(db, "trips", "bonusAmount", "REAL")
                addColumnIfMissing(db, "trips", "tipAmount", "REAL")
                addColumnIfMissing(db, "trips", "surgeReason", "TEXT")
                addColumnIfMissing(db, "trips", "isSurgeTrip", "INTEGER NOT NULL DEFAULT 0")
                // Hot-path indices (match @Entity indices in v3 schema).
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_blocks_index` ON `blocks` (`index`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trips_startTime` ON `trips` (`startTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trips_platform_startTime` ON `trips` (`platform`, `startTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trips_shiftId_endTime` ON `trips` (`shiftId`, `endTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shifts_startTime` ON `shifts` (`startTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shifts_endTime` ON `shifts` (`endTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_earnings_timestamp` ON `earnings` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_earnings_platform_timestamp` ON `earnings` (`platform`, `timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_shiftId` ON `expenses` (`shiftId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_timestamp` ON `expenses` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_category_timestamp` ON `expenses` (`category`, `timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fuel_logs_vehicleId` ON `fuel_logs` (`vehicleId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fuel_logs_timestamp` ON `fuel_logs` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fuel_logs_isClosed` ON `fuel_logs` (`isClosed`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_temp_transactions_timestamp` ON `temp_transactions` (`timestamp`)")
            }

            private fun addColumnIfMissing(db: SupportSQLiteDatabase, table: String, column: String, type: String) {
                val cursor = db.query("PRAGMA table_info(`$table`)")
                val names = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    names.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                cursor.close()
                if (!names.contains(column)) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $type")
                }
            }

            /**
             * v3 → v4: unique index on service_reminders(vehicleName, reminderType).
             * Existing rows are de-duplicated first (keeping the newest row per pair)
             * so the CREATE UNIQUE INDEX cannot fail on legacy duplicates. Idempotent.
             */
            private fun migrateToV4(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    DELETE FROM service_reminders WHERE id NOT IN (
                        SELECT MAX(id) FROM service_reminders
                        GROUP BY vehicleName, reminderType
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_service_reminders_vehicleName_reminderType` " +
                        "ON `service_reminders` (`vehicleName`, `reminderType`)"
                )
            }

            val MIGRATION_3_4 = object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) = migrateToV4(db)
            }

            // Explicit multi-step migrations (rather than relying on Room chaining),
            // matching the MIGRATION_*_3 convention above. Both bodies are idempotent.
            val MIGRATION_2_4 = object : Migration(2, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    migrateToV3(db)
                    migrateToV4(db)
                }
            }

            val MIGRATION_1_4 = object : Migration(1, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    migrateToV3(db)
                    migrateToV4(db)
                }
            }

        /**
         * Returns a process-wide singleton database instance.
         * Used by services that cannot use Hilt constructor injection.
         * Hilt-injected components should use the Hilt-provided instance from AppModule.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gigrun_db"
                ).addMigrations(
                    MIGRATION_1_4, MIGRATION_2_4, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
                ).build().also { INSTANCE = it }
            }
        }

        /**
         * Allows Hilt to set the singleton instance so that
         * both DI-provided and manual access use the same database.
         */
        fun setInstance(db: AppDatabase) {
            INSTANCE = db
        }
    }
}
