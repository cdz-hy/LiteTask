package com.litetask.app.data.model

import androidx.annotation.Keep
import androidx.room.*
import com.google.gson.annotations.SerializedName

@Keep
@Entity(
    tableName = "ai_history",
    indices = [
        Index(value = ["timestamp"])
    ]
)
data class AIHistory(
    @SerializedName("id")
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @SerializedName("content")
    @ColumnInfo(name = "content")
    val content: String,

    @SerializedName("source_type")
    @ColumnInfo(name = "source_type")
    val sourceType: AIHistorySource, // 枚举：VOICE, TEXT, SUBTASK

    @SerializedName("timestamp")
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    // 解析出的任务数量
    @SerializedName("parsed_count")
    @ColumnInfo(name = "parsed_count")
    val parsedCount: Int = 0,
    
    // 是否执行成功
    @SerializedName("is_success")
    @ColumnInfo(name = "is_success")
    val isSuccess: Boolean = true,

    @SerializedName("target_title")
    @ColumnInfo(name = "target_title")
    val targetTitle: String? = null,

    @SerializedName("image_count")
    @ColumnInfo(name = "image_count", defaultValue = "0")
    val imageCount: Int = 0,

    @SerializedName("error_message")
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null
)

@Keep
enum class AIHistorySource {
    VOICE,      // 语音转任务
    TEXT,       // 文本转任务
    SUBTASK     // 子任务拆解
}
