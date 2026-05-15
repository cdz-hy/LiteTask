package com.litetask.app.data.local

import androidx.room.*
import com.litetask.app.data.model.LocationStatsEntity
import kotlinx.coroutines.flow.Flow

/**
 * 地点统计数据访问对象
 */
@Dao
interface LocationStatsDao {
    
    /**
     * 获取所有地点统计
     */
    @Query("SELECT * FROM location_stats ORDER BY visit_count DESC")
    suspend fun getAllLocations(): List<LocationStatsEntity>
    
    /**
     * 获取所有地点统计Flow
     */
    @Query("SELECT * FROM location_stats ORDER BY visit_count DESC")
    fun getAllLocationsFlow(): Flow<List<LocationStatsEntity>>
    
    /**
     * 获取访问次数最多的N个地点
     */
    @Query("SELECT * FROM location_stats ORDER BY visit_count DESC LIMIT :limit")
    suspend fun getTopLocations(limit: Int): List<LocationStatsEntity>
    
    /**
     * 根据地点名称查询
     */
    @Query("SELECT * FROM location_stats WHERE location_name = :name")
    suspend fun getLocationByName(name: String): LocationStatsEntity?
    
    /**
     * 根据坐标查询附近地点（简单范围查询）
     */
    @Query("""
        SELECT * FROM location_stats 
        WHERE latitude BETWEEN :minLat AND :maxLat 
        AND longitude BETWEEN :minLng AND :maxLng
        ORDER BY visit_count DESC
    """)
    suspend fun getNearbyLocations(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): List<LocationStatsEntity>
    
    /**
     * 根据地点类型查询
     */
    @Query("SELECT * FROM location_stats WHERE location_type = :type ORDER BY visit_count DESC")
    suspend fun getLocationsByType(type: String): List<LocationStatsEntity>
    
    /**
     * 插入地点统计
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: LocationStatsEntity): Long
    
    /**
     * 更新地点统计
     */
    @Update
    suspend fun update(location: LocationStatsEntity)
    
    /**
     * 增加访问次数
     */
    @Query("UPDATE location_stats SET visit_count = visit_count + 1, last_visit_time = :visitTime WHERE id = :locationId")
    suspend fun incrementVisitCount(locationId: Long, visitTime: Long)
    
    /**
     * 增加任务数
     */
    @Query("UPDATE location_stats SET task_count = task_count + 1 WHERE id = :locationId")
    suspend fun incrementTaskCount(locationId: Long)
    
    /**
     * 删除地点统计
     */
    @Delete
    suspend fun delete(location: LocationStatsEntity)
    
    /**
     * 删除所有地点统计
     */
    @Query("DELETE FROM location_stats")
    suspend fun deleteAll()
}
