package com.litetask.app.data.local

import androidx.room.*
import com.litetask.app.data.model.DailyScheduleAdviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyScheduleAdviceDao {

    @Query("SELECT * FROM daily_schedule_advice ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatest(): DailyScheduleAdviceEntity?

    @Query("SELECT * FROM daily_schedule_advice ORDER BY created_at DESC LIMIT 1")
    fun getLatestFlow(): Flow<DailyScheduleAdviceEntity?>

    @Query("SELECT * FROM daily_schedule_advice ORDER BY created_at DESC")
    fun getAllFlow(): Flow<List<DailyScheduleAdviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(advice: DailyScheduleAdviceEntity): Long

    @Query("UPDATE daily_schedule_advice SET generation_status = :status WHERE id = :id")
    suspend fun updateGenerationStatus(id: Long, status: String)

    @Query("UPDATE daily_schedule_advice SET is_read = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("DELETE FROM daily_schedule_advice WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM daily_schedule_advice")
    suspend fun deleteAll()
}
