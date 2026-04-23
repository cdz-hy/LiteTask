package com.litetask.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * 地点统计实体
 * 记录用户常去地点及相关任务统计
 */
@Keep
@Entity(
    tableName = "location_stats",
    indices = [
        Index(value = ["visit_count"]),
        Index(value = ["last_visit_time"])
    ]
)
data class LocationStatsEntity(
    @SerializedName("id")
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    
    @SerializedName("location_name")
    @ColumnInfo(name = "location_name")
    val locationName: String,                 // 地点名称（高德POI名称）
    
    @SerializedName("latitude")
    @ColumnInfo(name = "latitude")
    val latitude: Double,
    
    @SerializedName("longitude")
    @ColumnInfo(name = "longitude")
    val longitude: Double,
    
    @SerializedName("visit_count")
    @ColumnInfo(name = "visit_count")
    val visitCount: Int = 0,                  // 访问次数
    
    @SerializedName("task_count")
    @ColumnInfo(name = "task_count")
    val taskCount: Int = 0,                   // 在此地创建的任务数
    
    @SerializedName("last_visit_time")
    @ColumnInfo(name = "last_visit_time")
    val lastVisitTime: Long = 0,
    
    @SerializedName("location_type")
    @ColumnInfo(name = "location_type")
    val locationType: String? = null,         // 地点类型：公司/学校/住宅/商圈/餐饮等
    
    @SerializedName("address")
    @ColumnInfo(name = "address")
    val address: String? = null               // 详细地址
)
