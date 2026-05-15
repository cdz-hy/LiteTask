package com.litetask.app.data.local

import androidx.room.*
import com.litetask.app.data.model.ProfileAnalysisHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 用户画像分析历史数据访问对象
 */
@Dao
interface ProfileAnalysisHistoryDao {
    
    /**
     * 获取所有分析历史
     */
    @Query("SELECT * FROM profile_analysis_history ORDER BY analysis_time DESC")
    suspend fun getAllHistory(): List<ProfileAnalysisHistoryEntity>
    
    /**
     * 获取所有分析历史Flow
     */
    @Query("SELECT * FROM profile_analysis_history ORDER BY analysis_time DESC")
    fun getAllHistoryFlow(): Flow<List<ProfileAnalysisHistoryEntity>>
    
    /**
     * 获取最新的分析历史
     */
    @Query("SELECT * FROM profile_analysis_history ORDER BY analysis_time DESC LIMIT 1")
    suspend fun getLatestHistory(): ProfileAnalysisHistoryEntity?
    
    /**
     * 获取最近N条分析历史
     */
    @Query("SELECT * FROM profile_analysis_history ORDER BY analysis_time DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int): List<ProfileAnalysisHistoryEntity>
    
    /**
     * 根据触发原因查询
     */
    @Query("SELECT * FROM profile_analysis_history WHERE trigger_reason = :reason ORDER BY analysis_time DESC")
    suspend fun getHistoryByReason(reason: String): List<ProfileAnalysisHistoryEntity>
    
    /**
     * 插入分析历史
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: ProfileAnalysisHistoryEntity): Long
    
    /**
     * 删除分析历史
     */
    @Delete
    suspend fun delete(history: ProfileAnalysisHistoryEntity)
    
    /**
     * 删除过期的分析历史（超过90天）
     */
    @Query("DELETE FROM profile_analysis_history WHERE analysis_time < :beforeTime")
    suspend fun deleteOldHistory(beforeTime: Long)
    
    /**
     * 获取分析历史数量
     */
    @Query("SELECT COUNT(*) FROM profile_analysis_history")
    suspend fun getCount(): Int
}
