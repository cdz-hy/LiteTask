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
        Index(value = ["is_pinned"]),  // 置顶索引
        Index(value = ["is_expired"]), // 过期状态索引
        Index(value = ["created_at"]), // 创建时间索引
        Index(value = ["category_id"]) // 分类索引
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

    // 任务类型 (已废弃，保留用于兼容迁移，实际逻辑使用 category_id)
    @Deprecated("Use categoryId instead")
    @ColumnInfo(name = "type")
    val type: TaskType = TaskType.WORK, 
    
    // --- 3.1 分类 (动态扩展) ---
    @ColumnInfo(name = "category_id")
    val categoryId: Long = 1, // 默认为 1 (Work)

    // --- 4. 新增状态字段 ---
    
    // 过期标记 - 任务已过截止时间但未完成
    @ColumnInfo(name = "is_expired")
    val isExpired: Boolean = false,

    // 过期时间 - 记录任务何时被标记为过期
    @ColumnInfo(name = "expired_at")
    val expiredAt: Long? = null,

    // 任务创建时间 - 记录任务实际创建的时间
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    // 任务完成时间 - 记录任务被标记为完成的时间
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,

    // --- 5. AI 字段 ---
    @ColumnInfo(name = "original_voice_text")
    val originalVoiceText: String? = null
) {
    // 计算属性：任务是否处于活跃状态（未完成且未过期）
    val isActive: Boolean get() = !isDone && !isExpired
    
    // 计算属性：任务是否已结束（完成或过期）
    val isFinished: Boolean get() = isDone || isExpired
    
    // 计算属性：获取任务的实际状态描述
    val statusDescription: String get() = when {
        isDone -> "已完成"
        isExpired -> "已过期"
        System.currentTimeMillis() < startTime -> "未开始"
        else -> "进行中"
    }
}