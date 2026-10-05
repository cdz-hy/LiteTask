package com.litetask.app.data.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * 数据备份实体类
 * 用于 JSON 序列化导出和导入
 */
@Keep
data class BackupData(
    @SerializedName("version")
    val version: Int = 1,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @SerializedName("tasks")
    val tasks: List<Task> = emptyList(),
    
    @SerializedName("sub_tasks")
    val subTasks: List<SubTask> = emptyList(),
    
    @SerializedName("task_components")
    val taskComponents: List<TaskComponentEntity> = emptyList(),
    
    @SerializedName("reminders")
    val reminders: List<Reminder> = emptyList(),
    
    @SerializedName("categories")
    val categories: List<Category> = emptyList(),

    @SerializedName("focus_sessions")
    val focusSessions: List<FocusSessionEntity> = emptyList(),

    @SerializedName("user_locations")
    val userLocations: List<UserLocationEntity>? = emptyList(),

    @SerializedName("user_profile_history")
    val userProfileHistory: List<UserProfileHistoryEntity>? = emptyList(),

    @SerializedName("ai_history")
    val aiHistory: List<AIHistory>? = emptyList(),

    @SerializedName("daily_schedule_advice")
    val dailyScheduleAdvice: List<DailyScheduleAdviceEntity>? = emptyList()
)
