package com.gigrun.di

import android.content.Context
import com.gigrun.core.utils.GoalsCalculator
import com.gigrun.core.utils.PdfExporter
import com.gigrun.core.utils.TaxCalculator
import com.gigrun.data.database.AppDatabase
import com.gigrun.data.database.dao.*
import com.gigrun.data.preferences.UserPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        val db = AppDatabase.getInstance(context)
        // Unify the manual service escape hatch with the Hilt singleton.
        AppDatabase.setInstance(db)
        return db
    }

    @Provides @Singleton fun provideShiftDao(db: AppDatabase): ShiftDao = db.shiftDao()
    @Provides @Singleton fun provideTripDao(db: AppDatabase): TripDao = db.tripDao()
    @Provides @Singleton fun provideEarningDao(db: AppDatabase): EarningDao = db.earningDao()
    @Provides @Singleton fun provideServiceReminderDao(db: AppDatabase): ServiceReminderDao = db.serviceReminderDao()
    @Provides @Singleton fun provideVehicleDao(db: AppDatabase): VehicleDao = db.vehicleDao()
    @Provides @Singleton fun provideFuelLogDao(db: AppDatabase): FuelLogDao = db.fuelLogDao()
    @Provides @Singleton fun provideBlockDao(db: AppDatabase): BlockDao = db.blockDao()
    @Provides @Singleton fun provideTempTransactionDao(db: AppDatabase): TempTransactionDao = db.tempTransactionDao()
    @Provides @Singleton fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()
    @Provides @Singleton fun provideEarningsGoalDao(db: AppDatabase): EarningsGoalDao = db.earningsGoalDao()
    @Provides @Singleton fun providePenaltyDao(db: AppDatabase): PenaltyDao = db.penaltyDao()
    @Provides @Singleton fun provideInsuranceDao(db: AppDatabase): InsuranceDao = db.insuranceDao()
    @Provides @Singleton fun provideDocumentDao(db: AppDatabase): DocumentDao = db.documentDao()
    @Provides @Singleton fun provideShiftLogDao(db: AppDatabase): ShiftLogDao = db.shiftLogDao()

    @Provides
    @Singleton
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences {
        return UserPreferences(context)
    }

    // Kotlin object singletons — Hilt cannot inject object types directly,
    // so we provide them explicitly. DashboardViewModel depends on all three.
    @Provides @Singleton fun provideGoalsCalculator(): GoalsCalculator = GoalsCalculator
    @Provides @Singleton fun provideTaxCalculator(): TaxCalculator = TaxCalculator
    @Provides @Singleton fun providePdfExporter(): PdfExporter = PdfExporter
}
