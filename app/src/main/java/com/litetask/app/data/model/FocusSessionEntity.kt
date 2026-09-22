package com.litetask.app.data.model

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * 专注计时会话记录实体
 */
@Keep
@Entity(
    tableName = "focus_sessions",
    foreignKeys = [
        ForeignKey(
            entity = Task::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["task_id"]),
        Index(value = ["started_at"])
    ]
)
data class FocusSessionEntity(
    @SerializedName("id", alternate = ["a"])
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @SerializedName("task_id", alternate = ["b"])
    @ColumnInfo(name = "task_id")
    val taskId: Long? = null,

    @SerializedName("task_title", alternate = ["c"])
    @ColumnInfo(name = "task_title")
    val taskTitle: String = "",

    @SerializedName("mode", alternate = ["d"])
    @ColumnInfo(name = "mode")
    val mode: String = "POMODORO", // POMODORO | STOPWATCH

    @SerializedName("planned_seconds", alternate = ["e"])
    @ColumnInfo(name = "planned_seconds")
    val plannedSeconds: Long = 0L,

    @SerializedName("actual_seconds", alternate = ["f"])
    @ColumnInfo(name = "actual_seconds")
    val actualSeconds: Long = 0L,

    @SerializedName("started_at", alternate = ["g"])
    @ColumnInfo(name = "started_at")
    val startedAt: Long = System.currentTimeMillis(),

    @SerializedName("ended_at", alternate = ["h"])
    @ColumnInfo(name = "ended_at")
    val endedAt: Long = System.currentTimeMillis(),

    @SerializedName("completed", alternate = ["i"])
    @ColumnInfo(name = "completed")
    val completed: Boolean = true
)
