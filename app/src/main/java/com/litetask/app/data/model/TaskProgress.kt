package com.litetask.app.data.model

/**
 * 任务进度数据类
 * 用于 Widget 查询今日任务完成情况
 */
data class TaskProgress(
    val done: Int,
    val total: Int
)
