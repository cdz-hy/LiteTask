package com.litetask.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * 用户画像分析历史
 * 记录每次AI分析的输入输出，用于追踪画像变化
 */
@Keep
@Entity(
    tableName = "profile_analysis_history",
    indices = [Index(value = ["analysis_time"])]
)
data class ProfileAnalysisHistoryEntity(
    @SerializedName("id")
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    
    @SerializedName("analysis_time")
    @ColumnInfo(name = "analysis_time")
    val analysisTime: Long,                   // 分析时间
    
    @SerializedName("trigger_reason")
    @ColumnInfo(name = "trigger_reason")
    val triggerReason: String,                // 触发原因：INITIAL/PERIODIC/MANUAL
    
    @SerializedName("tasks_analyzed")
    @ColumnInfo(name = "tasks_analyzed")
    val tasksAnalyzed: Int,                   // 本次分析的任务数
    
    @SerializedName("changed_fields")
    @ColumnInfo(name = "changed_fields")
    val changedFields: String,                // JSON: 变化的字段列表
    
    @SerializedName("ai_prompt")
    @ColumnInfo(name = "ai_prompt")
    val aiPrompt: String,                     // AI分析使用的提示词
    
    @SerializedName("ai_response")
    @ColumnInfo(name = "ai_response")
    val aiResponse: String                    // AI返回的完整分析结果
)
