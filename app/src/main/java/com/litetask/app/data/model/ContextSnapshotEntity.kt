package com.litetask.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * 环境上下文快照表
 * 记录生成规划时的环境信息（位置、天气、交通、用户状态）
 */
@Keep
@Entity(
    tableName = "context_snapshots",
    indices = [
        Index(value = ["snapshot_time"]),
        Index(value = ["context_type"])
    ]
)
data class ContextSnapshotEntity(
    @SerializedName("id")
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    
    @SerializedName("snapshot_time")
    @ColumnInfo(name = "snapshot_time")
    val snapshotTime: Long,                   // 快照时间
    
    @SerializedName("context_type")
    @ColumnInfo(name = "context_type")
    val contextType: String,                  // 上下文类型：LOCATION/WEATHER/TRAFFIC/USER_STATE
    
    // ==================== 位置上下文 ====================
    @SerializedName("latitude")
    @ColumnInfo(name = "latitude")
    val latitude: Double? = null,
    
    @SerializedName("longitude")
    @ColumnInfo(name = "longitude")
    val longitude: Double? = null,
    
    @SerializedName("location_name")
    @ColumnInfo(name = "location_name")
    val locationName: String? = null,
    
    @SerializedName("location_type")
    @ColumnInfo(name = "location_type")
    val locationType: String? = null,         // 高德POI类型
    
    @SerializedName("address")
    @ColumnInfo(name = "address")
    val address: String? = null,
    
    // ==================== 天气上下文 ====================
    @SerializedName("weather_code")
    @ColumnInfo(name = "weather_code")
    val weatherCode: String? = null,          // 天气代码
    
    @SerializedName("weather_desc")
    @ColumnInfo(name = "weather_desc")
    val weatherDesc: String? = null,          // 天气描述：晴/多云/雨等
    
    @SerializedName("temperature")
    @ColumnInfo(name = "temperature")
    val temperature: Float? = null,           // 温度（摄氏度）
    
    @SerializedName("air_quality")
    @ColumnInfo(name = "air_quality")
    val airQuality: String? = null,           // 空气质量
    
    @SerializedName("humidity")
    @ColumnInfo(name = "humidity")
    val humidity: Int? = null,                // 湿度（百分比）
    
    @SerializedName("wind_speed")
    @ColumnInfo(name = "wind_speed")
    val windSpeed: Float? = null,             // 风速
    
    // ==================== 交通上下文 ====================
    @SerializedName("traffic_status")
    @ColumnInfo(name = "traffic_status")
    val trafficStatus: String? = null,        // 交通状况：畅通/缓行/拥堵
    
    @SerializedName("nearby_transit")
    @ColumnInfo(name = "nearby_transit")
    val nearbyTransit: String? = null,        // JSON: 附近交通工具信息
    
    // ==================== 用户状态上下文 ====================
    @SerializedName("active_task_count")
    @ColumnInfo(name = "active_task_count")
    val activeTaskCount: Int = 0,             // 活跃任务数
    
    @SerializedName("urgent_task_count")
    @ColumnInfo(name = "urgent_task_count")
    val urgentTaskCount: Int = 0,             // 紧急任务数
    
    @SerializedName("today_completed_count")
    @ColumnInfo(name = "today_completed_count")
    val todayCompletedCount: Int = 0,         // 今日完成数
    
    @SerializedName("overdue_task_count")
    @ColumnInfo(name = "overdue_task_count")
    val overdueTaskCount: Int = 0,            // 过期任务数
    
    // ==================== 原始数据 ====================
    @SerializedName("raw_data")
    @ColumnInfo(name = "raw_data")
    val rawData: String? = null               // JSON: 原始API响应（用于调试）
)

/**
 * 上下文类型枚举
 */
enum class ContextType {
    LOCATION,    // 位置上下文
    WEATHER,     // 天气上下文
    TRAFFIC,     // 交通上下文
    USER_STATE   // 用户状态上下文
}
