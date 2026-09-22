package com.litetask.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.litetask.app.data.model.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {
    @Insert
    suspend fun insertSession(session: FocusSessionEntity): Long

    @Query("SELECT * FROM focus_sessions ORDER BY started_at DESC")
    fun getAllSessionsFlow(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions ORDER BY started_at DESC")
    suspend fun getAllSessionsSync(): List<FocusSessionEntity>

    @Query("SELECT * FROM focus_sessions WHERE task_id = :taskId ORDER BY started_at DESC")
    fun getSessionsForTaskFlow(taskId: Long): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE started_at >= :sinceMillis ORDER BY started_at DESC")
    suspend fun getSessionsSinceSync(sinceMillis: Long): List<FocusSessionEntity>

    @Query("SELECT * FROM focus_sessions WHERE started_at >= :startMillis AND started_at < :endMillis ORDER BY started_at DESC")
    suspend fun getSessionsInRangeSync(startMillis: Long, endMillis: Long): List<FocusSessionEntity>

    @Query("SELECT COALESCE(SUM(actual_seconds), 0) FROM focus_sessions")
    fun getTotalFocusSecondsFlow(): Flow<Long>

    @Query("SELECT COALESCE(SUM(actual_seconds), 0) FROM focus_sessions WHERE started_at >= :startOfDay")
    fun getTodayFocusSecondsFlow(startOfDay: Long): Flow<Long>
}
