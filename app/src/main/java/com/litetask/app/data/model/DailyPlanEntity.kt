package com.litetask.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * 日程规划主表
 * 存储AI生成的每日/每周任务规划
 */
@Keep
@Entity(
    tableName = "daily_plans",
    indices = [
        Index(value = ["plan_date"], unique = true),
        Index(value = ["plan_type"]),
        Index(value = ["is_valid"])
    ]
)
data class DailyPlanEntity(
    @SerializedName("id")
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    
    // ==================== 规划基础信息 ====================
    @SerializedName("plan_date")
    @ColumnInfo(name = "plan_date")
    val planDate: Long,                       // 规划日期（当天0点时间戳）
    
    @SerializedName("plan_type")
    @ColumnInfo(name = "plan_type")
    val planType: String,                     // SHORT_TERM(短期) / LONG_TERM(长期)
    
    @SerializedName("generated_at")
    @ColumnInfo(name = "generated_at")
    val generatedAt: Long,                    // 生成时间
    
    // ==================== 规划内容 ====================
    @SerializedName("summary")
    @ColumnInfo(name = "summary")
    val summary: String,                      // 规划摘要："今日有3个紧急任务，建议..."
    
    @SerializedName("suggestions")
    @ColumnInfo(name = "suggestions")
    val suggestions: String,                  // JSON: 具体建议列表
    
    @SerializedName("time_blocks")
    @ColumnInfo(name = "time_blocks")
    val timeBlocks: String,                   // JSON: 时间块分配（简化版，详细的在TimeBlock表）
    
    // ==================== 上下文快照 ====================
    @SerializedName("weather_condition")
    @ColumnInfo(name = "weather_condition")
    val weatherCondition: String? = null,     // 天气状况
    
    @SerializedName("weather_temperature")
    @ColumnInfo(name = "weather_temperature")
    val weatherTemperature: Float? = null,    // 温度
    
    @SerializedName("current_location")
    @ColumnInfo(name = "current_location")
    val currentLocation: String? = null,      // 当前位置
    
    @SerializedName("user_energy_level")
    @ColumnInfo(name = "user_energy_level")
    val userEnergyLevel: Float = 0.5f,       // 用户精力水平（基于历史活跃时段）0-1
    
    // ==================== 状态管理 ====================
    @SerializedName("is_valid")
    @ColumnInfo(name = "is_valid")
    val isValid: Boolean = true,              // 是否有效（任务大量变更后失效）
    
    @SerializedName("invalidated_at")
    @ColumnInfo(name = "invalidated_at")
    val invalidatedAt: Long? = null,          // 失效时间
    
    @SerializedName("invalidation_reason")
    @ColumnInfo(name = "invalidation_reason")
    val invalidationReason: String? = null,   // 失效原因
    
    // ==================== AI元数据 ====================
    @SerializedName("ai_model")
    @ColumnInfo(name = "ai_model")
    val aiModel: String,                      // 使用的AI模型
    
    @SerializedName("ai_prompt_hash")
    @ColumnInfo(name = "ai_prompt_hash")
    val aiPromptHash: String,                 // 提示词哈希（用于追踪）
    
    @SerializedName("ai_response_time")
    @ColumnInfo(name = "ai_response_time")
    val aiResponseTime: Long = 0,             // AI响应时间（毫秒）
    
    @SerializedName("confidence")
    @ColumnInfo(name = "confidence")
    val confidence: Float = 0f                // AI置信度 0-1
)

/**
 * 规划类型枚举
 */
enum class PlanType {
    SHORT_TERM,  // 短期：今日/明日/3天内
    LONG_TERM    // 长期：本周/本月
}
