package com.litetask.app.data.local

import androidx.room.*
import com.litetask.app.data.model.UserProfileHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 用户画像快照数据访问对象
 */
@Dao
interface UserProfileDao {
    
    /**
     * 获取最新生成的画像快照
     */
    @Query("SELECT * FROM user_profile_history ORDER BY id DESC LIMIT 1")
    suspend fun getLatestUserProfile(): UserProfileHistoryEntity?
    
    /**
     * 获取用户画像快照Flow (实时监听最新的一条)
     */
    @Query("SELECT * FROM user_profile_history ORDER BY id DESC LIMIT 1")
    fun getUserProfileFlow(): Flow<UserProfileHistoryEntity?>

    /**
     * 获取历史所有画像快照
     */
    @Query("SELECT * FROM user_profile_history ORDER BY created_at DESC")
    fun getAllUserProfileHistoryFlow(): Flow<List<UserProfileHistoryEntity>>
    
    /**
     * 插入新的画像快照 (Append-Only)
     */
    @Insert
    suspend fun insert(profile: UserProfileHistoryEntity)
    
    /**
     * 删除所有画像记录
     */
    @Query("DELETE FROM user_profile_history")
    suspend fun deleteAll()
}
