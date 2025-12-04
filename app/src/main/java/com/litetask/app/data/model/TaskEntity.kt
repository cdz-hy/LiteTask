package com.litetask.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["deadline"]),   // 核心索引：首页列表完全依赖此字段排序
        Index(value = ["is_done"]),    // 过滤索引：区分"进行中"和"归档"
        Index(value = ["is_pinned"])   // 置顶索引
    ]
)
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // --- 1. 任务内容 ---
    @ColumnInfo(name = "title")
    val title: String,            // 例："完成毕业论文初稿"

    @ColumnInfo(name = "description")
    val description: String? = null, // 例："需要包含摘要和前三章"

    // --- 2. DDL 时间体系 (核心) ---
    
    // 截止时间 (App 的灵魂)
    // 必填。首页列表按此字段 ASC (由近到远) 排序。
    @ColumnInfo(name = "deadline")
    val deadline: Long, 

    // 开始/创建时间
    // 默认为创建那一刻的时间戳。
    // 作用：用于计算"时间余额"。例如：任务总长3天，已经过了2天，进度条显示 66% (红色预警)。
    @ColumnInfo(name = "start_time")
    val startTime: Long = System.currentTimeMillis(),

    // --- 3. 状态与权重 ---
    
    @ColumnInfo(name = "is_done")
    val isDone: Boolean = false,

    // 手动置顶 (比如虽然下周才截止，但我今天就想盯着它做)
    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,

    // 任务类型 (用于 UI 显示不同的图标/颜色，如 Work=蓝, Life=绿)
    @ColumnInfo(name = "type")
    val type: TaskType = TaskType.WORK, // 枚举：WORK, LIFE, URGENT

    // --- 4. AI 字段 ---
    @ColumnInfo(name = "original_voice_text")
    val originalVoiceText: String? = null
)