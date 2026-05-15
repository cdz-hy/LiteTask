package com.litetask.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * 用户画像实体
 * 基于任务数据、位置信息、完成情况等通过AI分析得出
 */
@Keep
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @SerializedName("id")
    @PrimaryKey 
    val id: Long = 1, // 单用户，固定ID
    
    // ==================== 基础画像 ====================
    @SerializedName("user_identity")
    @ColumnInfo(name = "user_identity")
    val userIdentity: String? = null,        // 用户身份：在校学生/企业职员/自由职业者/党政机关等
    
    @SerializedName("industry")
    @ColumnInfo(name = "industry")
    val industry: String? = null,             // 行业领域：教育/IT/金融/法律/医疗等
    
    @SerializedName("confidence")
    @ColumnInfo(name = "confidence")
    val confidence: Float = 0f,               // AI分析置信度 0-1
    
    // ==================== 地理画像 ====================
    @SerializedName("primary_location")
    @ColumnInfo(name = "primary_location")
    val primaryLocation: String? = null,      // 主要活动地点（如公司/学校）
    
    @SerializedName("primary_location_lat")
    @ColumnInfo(name = "primary_location_lat")
    val primaryLocationLat: Double? = null,
    
    @SerializedName("primary_location_lng")
    @ColumnInfo(name = "primary_location_lng")
    val primaryLocationLng: Double? = null,
    
    @SerializedName("secondary_location")
    @ColumnInfo(name = "secondary_location")
    val secondaryLocation: String? = null,    // 次要活动地点（如家）
    
    @SerializedName("secondary_location_lat")
    @ColumnInfo(name = "secondary_location_lat")
    val secondaryLocationLat: Double? = null,
    
    @SerializedName("secondary_location_lng")
    @ColumnInfo(name = "secondary_location_lng")
    val secondaryLocationLng: Double? = null,
    
    @SerializedName("location_update_time")
    @ColumnInfo(name = "location_update_time")
    val locationUpdateTime: Long = 0,
    
    // ==================== 行为画像 ====================
    @SerializedName("task_completion_rate")
    @ColumnInfo(name = "task_completion_rate")
    val taskCompletionRate: Float = 0f,       // 任务完成率 0-1
    
    @SerializedName("procrastination_index")
    @ColumnInfo(name = "procrastination_index")
    val procrastinationIndex: Float = 0f,     // 拖延指数 0-1（越高越拖延）
    
    @SerializedName("planning_ability")
    @ColumnInfo(name = "planning_ability")
    val planningAbility: Float = 0f,          // 规划能力评分 0-1
    
    @SerializedName("stress_capacity")
    @ColumnInfo(name = "stress_capacity")
    val stressCapacity: Float = 0f,           // 压力承受度 0-1
    
    @SerializedName("personality_traits")
    @ColumnInfo(name = "personality_traits")
    val personalityTraits: String? = null,    // 综合性格特征描述（如：行动派、完美主义、INTJ等）
    
    // ==================== 时间画像 ====================
    @SerializedName("peak_hours_start")
    @ColumnInfo(name = "peak_hours_start")
    val peakHoursStart: Int = 9,              // 活跃时段开始（小时 0-23）
    
    @SerializedName("peak_hours_end")
    @ColumnInfo(name = "peak_hours_end")
    val peakHoursEnd: Int = 18,               // 活跃时段结束（小时 0-23）
    
    @SerializedName("average_task_duration")
    @ColumnInfo(name = "average_task_duration")
    val averageTaskDuration: Long = 0,        // 平均任务周期（毫秒）
    
    // ==================== 偏好画像 ====================
    @SerializedName("preferred_categories")
    @ColumnInfo(name = "preferred_categories")
    val preferredCategories: String = "",     // JSON数组: 偏好分类ID列表 "[1,3,4]"
    
    @SerializedName("transport_mode")
    @ColumnInfo(name = "transport_mode")
    val transportMode: String? = null,        // 主要出行方式：驾车/公交/骑行/步行
    
    // ==================== 元数据 ====================
    @SerializedName("last_analysis_time")
    @ColumnInfo(name = "last_analysis_time")
    val lastAnalysisTime: Long = 0,           // 上次分析时间
    
    @SerializedName("total_tasks_analyzed")
    @ColumnInfo(name = "total_tasks_analyzed")
    val totalTasksAnalyzed: Int = 0,          // 已分析任务数
    
    @SerializedName("profile_version")
    @ColumnInfo(name = "profile_version")
    val profileVersion: Int = 1               // 画像版本号
)
