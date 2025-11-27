package com.litetask.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskType {
    WORK, LIFE, DEV, HEALTH, OTHER
}

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String? = null,
    val startTime: Long,
    val endTime: Long,
    val isDone: Boolean = false,
    val type: TaskType = TaskType.WORK,
    val location: String? = null,
    val deadline: Long? = null, // 添加截止时间字段
    val progress: Int = 0, // 添加进度字段
    val createdAt: Long = System.currentTimeMillis()
)