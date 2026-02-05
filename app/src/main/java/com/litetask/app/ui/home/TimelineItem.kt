package com.litetask.app.ui.home

import com.litetask.app.data.model.TaskDetailComposite

// 定义列表项类型，用于在 LazyColumn 中混排
sealed class TimelineItem {
    data class TaskItem(val composite: TaskDetailComposite) : TimelineItem()
    object ExpiredHeader : TimelineItem()  // 已过期任务分隔线
    object HistoryHeader : TimelineItem()  // 已完成任务分隔线
    object Loading : TimelineItem()
}