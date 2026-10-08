package com.gigrun.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gigrun.data.database.entities.InsurancePolicy
import kotlinx.coroutines.flow.Flow

@Dao
interface InsuranceDao {
    @Insert suspend fun insert(policy: InsurancePolicy): Long
    @Update suspend fun update(policy: InsurancePolicy)
    @Delete suspend fun delete(policy: InsurancePolicy)

    @Query("SELECT * FROM insurance_policies ORDER BY endDate ASC")
    fun getAll(): Flow<List<InsurancePolicy>>

    @Query("SELECT * FROM insurance_policies WHERE id = :id")
    suspend fun getById(id: Long): InsurancePolicy?

    @Query("SELECT * FROM insurance_policies WHERE endDate BETWEEN :from AND :to")
    suspend fun getExpiringBetween(from: Long, to: Long): List<InsurancePolicy>

    @Query("SELECT SUM(premiumInr) FROM insurance_policies WHERE startDate >= :from AND startDate < :to")
    fun getPremiumTotal(from: Long, to: Long): Flow<Double>
}