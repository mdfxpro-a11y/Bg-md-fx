package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM processed_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ProcessedItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ProcessedItemEntity)

    @Delete
    suspend fun deleteItem(item: ProcessedItemEntity)

    @Query("DELETE FROM processed_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM processed_history")
    suspend fun clearAll()
}
