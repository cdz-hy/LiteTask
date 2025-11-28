package com.litetask.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(
        entity = Task::class,
        parentColumns = ["id"],
        childColumns = ["task_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("task_id")]
)
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "task_id")
    val taskId: Long,

    // 具体的触发时间 (毫秒时间戳)
    // AI 分析时会自动计算：如果用户说"提前1小时提醒"，这里存的就是 (deadline - 1h)
    @ColumnInfo(name = "trigger_at")
    val triggerAt: Long,

    // 提醒文案
    // 例："还有 1 小时就要交稿了！"
    @ColumnInfo(name = "label")
    val label: String? = null,

    // 状态标记：是否已经响过了
    @ColumnInfo(name = "is_fired")
    val isFired: Boolean = false
)