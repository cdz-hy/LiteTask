package com.litetask.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * 每日日程建议表
 * 存储 AI 生成的短期/长期建议，JSON 格式
 */
@Keep
@Entity(
    tableName = "daily_schedule_advice",
    indices = [Index(value = ["created_at"]), Index(value = ["generation_status"])]
)
data class DailyScheduleAdviceEntity(
    @SerializedName("id")
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @SerializedName("created_at")
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @SerializedName("short_term_json")
    @ColumnInfo(name = "short_term_json")
    val shortTermJson: String,   // 短期建议 JSON

    @SerializedName("long_term_json")
    @ColumnInfo(name = "long_term_json")
    val longTermJson: String,    // 长期建议 JSON

    @SerializedName("is_read")
    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false, // 是否已读

    @SerializedName("generation_status")
    @ColumnInfo(name = "generation_status")
    val generationStatus: String = "GENERATING" // 生成状态: GENERATING / COMPLETED / FAILED
)
