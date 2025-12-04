package com.litetask.app.ui.home

import com.litetask.app.data.model.TaskDetailComposite

// 定义列表项类型，用于在 LazyColumn 中混排
sealed class TimelineItem {
    data class PinnedHeader(val count: Int) : TimelineItem()
    data class TaskItem(val composite: TaskDetailComposite) : TimelineItem()
    object HistoryHeader : TimelineItem()
    object Loading : TimelineItem()
}