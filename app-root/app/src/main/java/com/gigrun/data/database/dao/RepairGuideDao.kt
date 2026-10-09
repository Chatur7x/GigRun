package com.gigrun.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gigrun.data.database.entities.RepairGuide
import kotlinx.coroutines.flow.Flow

@Dao
interface RepairGuideDao {
    @Insert suspend fun insert(guide: RepairGuide): Long
    @Update suspend fun update(guide: RepairGuide)
    @Delete suspend fun delete(guide: RepairGuide)

    @Query("SELECT * FROM repair_guides ORDER BY symptom ASC")
    fun getAll(): Flow<List<RepairGuide>>

    @Query("SELECT * FROM repair_guides WHERE difficulty = :difficulty ORDER BY symptom ASC")
    fun getByDifficulty(difficulty: String): Flow<List<RepairGuide>>

    @Query("SELECT * FROM repair_guides WHERE symptom LIKE '%' || :query || '%' ORDER BY symptom ASC")
    fun search(query: String): Flow<List<RepairGuide>>

    @Query("SELECT COUNT(*) FROM repair_guides")
    suspend fun count(): Int
}