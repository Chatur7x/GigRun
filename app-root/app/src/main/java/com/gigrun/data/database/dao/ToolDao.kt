package com.gigrun.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gigrun.data.database.entities.Tool
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolDao {
    @Insert suspend fun insert(tool: Tool): Long
    @Update suspend fun update(tool: Tool)
    @Delete suspend fun delete(tool: Tool)

    @Query("SELECT * FROM tools ORDER BY category ASC, name ASC")
    fun getAll(): Flow<List<Tool>>

    @Query("SELECT * FROM tools WHERE category = :category ORDER BY name ASC")
    fun getByCategory(category: String): Flow<List<Tool>>

    @Query("SELECT * FROM tools WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun search(query: String): Flow<List<Tool>>

    @Query("SELECT COUNT(*) FROM tools")
    suspend fun count(): Int
}