package com.gigrun.data.database.dao

import androidx.room.*
import com.gigrun.data.database.entities.EarningsGoal
import kotlinx.coroutines.flow.Flow

@Dao
interface EarningsGoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: EarningsGoal)

    @Query("SELECT * FROM earnings_goals WHERE id = 1")
    fun getGoal(): Flow<EarningsGoal?>

    @Query("SELECT * FROM earnings_goals WHERE id = 1")
    suspend fun getGoalSync(): EarningsGoal?
}
