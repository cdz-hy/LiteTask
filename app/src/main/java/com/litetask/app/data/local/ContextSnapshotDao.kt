package com.litetask.app.data.local

import androidx.room.*
import com.litetask.app.data.model.ContextSnapshotEntity
import kotlinx.coroutines.flow.Flow

/**
 * 环境上下文快照数据访问对象
 */
@Dao
interface ContextSnapshotDao {
    
    /**
     * 获取所有快照
     */
    @Query("SELECT * FROM context_snapshots ORDER BY snapshot_time DESC")
    suspend fun getAllSnapshots(): List<ContextSnapshotEntity>
    
    /**
     * 根据类型获取最新快照
     */
    @Query("SELECT * FROM context_snapshots WHERE context_type = :type ORDER BY snapshot_time DESC LIMIT 1")
    suspend fun getLatestSnapshotByType(type: String): ContextSnapshotEntity?
    
    /**
     * 根据类型获取快照列表
     */
    @Query("SELECT * FROM context_snapshots WHERE context_type = :type ORDER BY snapshot_time DESC")
    suspend fun getSnapshotsByType(type: String): List<ContextSnapshotEntity>
    
    /**
     * 获取时间范围内的快照
     */
    @Query("SELECT * FROM context_snapshots WHERE snapshot_time BETWEEN :startTime AND :endTime ORDER BY snapshot_time DESC")
    suspend fun getSnapshotsInRange(startTime: Long, endTime: Long): List<ContextSnapshotEntity>
    
    /**
     * 插入快照
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: ContextSnapshotEntity): Long
    
    /**
     * 批量插入快照
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snapshots: List<ContextSnapshotEntity>)
    
    /**
     * 删除快照
     */
    @Delete
    suspend fun delete(snapshot: ContextSnapshotEntity)
    
    /**
     * 删除过期的快照（超过7天）
     */
    @Query("DELETE FROM context_snapshots WHERE snapshot_time < :beforeTime")
    suspend fun deleteOldSnapshots(beforeTime: Long)
    
    /**
     * 根据类型删除快照
     */
    @Query("DELETE FROM context_snapshots WHERE context_type = :type")
    suspend fun deleteByType(type: String)
}
