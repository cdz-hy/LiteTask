package com.litetask.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
@Entity(
    tableName = "sub_tasks",
    // 级联删除：如果你删除了"毕业论文"这个主任务，底下的"查文献"、"写大纲"自动删除
    foreignKeys = [ForeignKey(
        entity = Task::class,
        parentColumns = ["id"],
        childColumns = ["task_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("task_id")]
)
data class SubTask(
    @SerializedName("id", alternate = ["a"])
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @SerializedName("task_id", alternate = ["b"])
    @ColumnInfo(name = "task_id")
    val taskId: Long,

    @SerializedName("content", alternate = ["c"])
    @ColumnInfo(name = "content")
    val content: String, // 例如："去知网下载近3年参考文献"

    @SerializedName("is_completed", alternate = ["d"])
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,
    
    // 排序权重，保证计划有先后顺序 (Step 1, Step 2...)
    @SerializedName("sort_order", alternate = ["e"])
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)