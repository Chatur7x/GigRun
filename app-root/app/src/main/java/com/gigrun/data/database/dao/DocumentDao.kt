package com.gigrun.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gigrun.data.database.entities.Document
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Insert suspend fun insert(document: Document): Long
    @Update suspend fun update(document: Document)
    @Delete suspend fun delete(document: Document)

    @Query("SELECT * FROM documents ORDER BY expiryDate IS NULL, expiryDate ASC")
    fun getAll(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE expiryDate IS NOT NULL ORDER BY expiryDate ASC")
    fun getExpiring(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: Long): Document?

    @Query("SELECT * FROM documents WHERE type = :type")
    fun getByType(type: String): Flow<List<Document>>

    @Query("SELECT COUNT(*) FROM documents WHERE expiryDate IS NOT NULL AND expiryDate BETWEEN :from AND :to")
    suspend fun countExpiringBetween(from: Long, to: Long): Int
}