package com.litetask.app.data.model

import androidx.room.*

@Entity(
    tableName = "ai_history",
    indices = [
        Index(value = ["timestamp"])
    ]
)
data class AIHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "source_type")
    val sourceType: AIHistorySource, // 枚举：VOICE, TEXT, SUBTASK

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    // 解析出的任务数量
    @ColumnInfo(name = "parsed_count")
    val parsedCount: Int = 0,
    
    // 是否执行成功
    @ColumnInfo(name = "is_success")
    val isSuccess: Boolean = true
)

enum class AIHistorySource {
    VOICE,      // 语音转任务
    TEXT,       // 文本转任务
    SUBTASK     // 子任务拆解
}
