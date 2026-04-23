package com.litetask.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * 规划建议表
 * 存储AI给出的各类建议
 */
@Keep
@Entity(
    tableName = "plan_suggestions",
    foreignKeys = [
        ForeignKey(
            entity = DailyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["plan_id"]),
        Index(value = ["suggestion_type"]),
        Index(value = ["is_read"])
    ]
)
data class PlanSuggestionEntity(
    @SerializedName("id")
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    
    @SerializedName("plan_id")
    @ColumnInfo(name = "plan_id")
    val planId: Long,                         // 关联的规划ID
    
    // ==================== 建议内容 ====================
    @SerializedName("suggestion_type")
    @ColumnInfo(name = "suggestion_type")
    val suggestionType: String,               // 建议类型
    
    @SerializedName("title")
    @ColumnInfo(name = "title")
    val title: String,                        // 建议标题
    
    @SerializedName("content")
    @ColumnInfo(name = "content")
    val content: String,                      // 建议内容
    
    @SerializedName("priority")
    @ColumnInfo(name = "priority")
    val priority: Int = 0,                    // 重要程度（越大越重要）
    
    // ==================== 关联信息 ====================
    @SerializedName("related_task_ids")
    @ColumnInfo(name = "related_task_ids")
    val relatedTaskIds: String? = null,       // JSON数组: 相关任务ID列表 "[1,2,3]"
    
    @SerializedName("related_time_block_ids")
    @ColumnInfo(name = "related_time_block_ids")
    val relatedTimeBlockIds: String? = null,  // JSON数组: 相关时间块ID列表
    
    // ==================== 用户交互 ====================
    @SerializedName("is_read")
    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false,              // 是否已读
    
    @SerializedName("is_accepted")
    @ColumnInfo(name = "is_accepted")
    val isAccepted: Boolean? = null,          // 是否采纳（null=未响应）
    
    @SerializedName("user_note")
    @ColumnInfo(name = "user_note")
    val userNote: String? = null              // 用户备注
)

/**
 * 建议类型枚举
 */
enum class SuggestionType {
    PRIORITY_ADJUSTMENT,  // 优先级调整建议
    TIME_OPTIMIZATION,    // 时间优化建议
    TASK_GROUPING,        // 任务分组建议
    BREAK_REMINDER,       // 休息提醒
    WEATHER_ALERT,        // 天气预警
    TRAFFIC_ALERT,        // 交通提醒
    ENERGY_MANAGEMENT,    // 精力管理
    DEADLINE_WARNING,     // 截止日期预警
    WORKLOAD_BALANCE      // 工作量平衡
}
