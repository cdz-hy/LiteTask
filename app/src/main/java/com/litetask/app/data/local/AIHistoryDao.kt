package com.litetask.app.data.local

import androidx.room.*
import com.litetask.app.data.model.AIHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface AIHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: AIHistory): Long

    @Query("SELECT * FROM ai_history ORDER BY timestamp DESC")
    fun getAllHistoryFlow(): Flow<List<AIHistory>>

    @Query("SELECT * FROM ai_history ORDER BY timestamp DESC")
    suspend fun getAllHistory(): List<AIHistory>

    @Delete
    suspend fun deleteHistory(history: AIHistory)

    @Query("DELETE FROM ai_history")
    suspend fun clearAllHistory()
    
    @Query("DELETE FROM ai_history WHERE is_success = 0")
    suspend fun clearFailedHistory()

    @Query("SELECT COUNT(*) FROM ai_history")
    suspend fun getHistoryCount(): Int
}
