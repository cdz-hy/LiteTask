package com.litetask.app.widget

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.litetask.app.R
import com.litetask.app.data.local.AppDatabase
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskType
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 任务列表小组件 RemoteViewsService
 */
class TaskListWidgetService : RemoteViewsService() {
    
    companion object {
        // 存储刚完成的任务数据（taskId -> Task + 完成时间）
        private val recentlyCompletedTasks = ConcurrentHashMap<Long, Pair<Task, Long>>()
        private const val COMPLETED_DISPLAY_DURATION = 2000L // 2秒
        
        /**
         * 标记任务为刚完成状态，并缓存任务数据
         */
        fun markTaskAsJustCompleted(task: Task) {
            recentlyCompletedTasks[task.id] = task to System.currentTimeMillis()
            Log.d("TaskListWidget", "markTaskAsJustCompleted: ${task.id}")
        }
        
        /**
         * 获取刚完成的任务（如果还在显示时间内）
         * 注意：此方法不会移除过期缓存，只返回是否在时间内
         */
        fun getJustCompletedTask(taskId: Long): Task? {
            val pair = recentlyCompletedTasks[taskId] ?: return null
            val (task, completedTime) = pair
            if (System.currentTimeMillis() - completedTime <= COMPLETED_DISPLAY_DURATION) {
                return task
            }
            return null
        }
        
        /**
         * 获取所有刚完成的任务（还在显示时间内的）
         */
        fun getAllJustCompletedTasks(): List<Task> {
            val now = System.currentTimeMillis()
            val result = mutableListOf<Task>()
            
            recentlyCompletedTasks.forEach { (_, pair) ->
                val (task, completedTime) = pair
                if (now - completedTime <= COMPLETED_DISPLAY_DURATION) {
                    result.add(task)
                }
            }
            
            return result
        }
        
        /**
         * 检查任务是否刚完成（在缓存中且未过期，或者在缓存中已过期但还未被清理）
         * 用于判断点击时应该执行撤销还是完成操作
         */
        fun isJustCompleted(taskId: Long): Boolean {
            // 只要在缓存中就认为是刚完成的，允许撤销
            val pair = recentlyCompletedTasks[taskId] ?: return false
            val (_, completedTime) = pair
            // 给用户更多时间撤销（比显示时间多1秒）
            return System.currentTimeMillis() - completedTime <= COMPLETED_DISPLAY_DURATION + 1000L
        }
        
        /**
         * 撤销任务完成状态（从缓存中移除）
         * 立即移除，提供即时反馈
         */
        fun undoTaskCompletion(taskId: Long) {
            val removed = recentlyCompletedTasks.remove(taskId)
            Log.d("TaskListWidget", "undoTaskCompletion: $taskId, removed: ${removed != null}")
        }
        
        /**
         * 清理过期的缓存（在数据加载后调用）
         */
        fun cleanupExpiredCache() {
            val now = System.currentTimeMillis()
            val expiredIds = recentlyCompletedTasks.filter { (_, pair) ->
                now - pair.second > COMPLETED_DISPLAY_DURATION + 2000L // 多给2秒缓冲
            }.keys
            expiredIds.forEach { recentlyCompletedTasks.remove(it) }
        }
    }
    
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TaskListRemoteViewsFactory(applicationContext)
    }
}

class TaskListRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {
    
    private var tasks: List<Task> = emptyList()
    private var justCompletedTaskIds: Set<Long> = emptySet()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
    
    override fun onCreate() {
        loadData()
    }
    
    override fun onDataSetChanged() {
        // 系统自动更新或手动刷新时，总是重新加载数据
        loadData()
    }
    
    private fun loadData() {
        // 清除调用者身份，以便使用应用的权限访问数据库
        val identityToken = Binder.clearCallingIdentity()
        try {
            runBlocking {
                try {
                    val dao = AppDatabase.getInstance(context).taskDao()
                    // 获取所有未完成任务
                    val activeTasks = dao.getActiveTasksWithRecentlyCompletedSync().toMutableList()
                    
                    Log.d("TaskListWidget", "Loaded ${activeTasks.size} active tasks")
                    
                    // 获取刚完成的任务（还在显示时间内的）
                    val justCompletedTasks = TaskListWidgetService.getAllJustCompletedTasks()
                    
                    // 将刚完成的任务添加到列表中（如果不在列表中）
                    val activeTaskIds = activeTasks.map { it.id }.toSet()
                    justCompletedTasks.forEach { completedTask ->
                        if (completedTask.id !in activeTaskIds) {
                            // 找到合适的位置插入（按原来的排序规则）
                            val insertIndex = activeTasks.indexOfFirst { 
                                !it.isPinned && completedTask.isPinned ||
                                (it.isPinned == completedTask.isPinned && it.startTime > completedTask.startTime)
                            }
                            if (insertIndex >= 0) {
                                activeTasks.add(insertIndex, completedTask)
                            } else {
                                activeTasks.add(completedTask)
                            }
                        }
                    }
                    
                    // 记录哪些任务刚完成（用于UI显示）
                    justCompletedTaskIds = justCompletedTasks.map { it.id }.toSet()
                    
                    tasks = activeTasks
                    
                    // 清理过期缓存（减少频率，只在数据加载时清理）
                    if (System.currentTimeMillis() % 5000 < 1000) { // 大约每5秒清理一次
                        TaskListWidgetService.cleanupExpiredCache()
                    }
                } catch (e: Exception) {
                    Log.e("TaskListWidget", "Error loading tasks", e)
                    tasks = emptyList()
                    justCompletedTaskIds = emptySet()
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identityToken)
        }
    }
    
    override fun onDestroy() {
        tasks = emptyList()
    }
    
    override fun getCount(): Int = tasks.size
    
    override fun getViewAt(position: Int): RemoteViews {
        if (position >= tasks.size) {
            return RemoteViews(context.packageName, R.layout.widget_task_list_item)
        }
        
        val task = tasks[position]
        val views = RemoteViews(context.packageName, R.layout.widget_task_list_item)
        val isJustCompleted = justCompletedTaskIds.contains(task.id)
        
        // 设置任务标题
        views.setTextViewText(R.id.task_title, task.title)
        
        // 设置时间
        views.setTextViewText(R.id.task_time, formatTaskTime(task))
        
        // 设置类型指示条颜色
        views.setImageViewResource(R.id.type_indicator, getTypeIndicatorRes(task.type))
        
        // 设置置顶图标
        views.setViewVisibility(R.id.pin_icon, 
            if (task.isPinned) android.view.View.VISIBLE else android.view.View.GONE)
        
        // 设置任务类型标签（始终显示）
        views.setViewVisibility(R.id.urgent_badge, android.view.View.VISIBLE)
        views.setTextViewText(R.id.urgent_badge, getTypeText(task.type))
        views.setInt(R.id.urgent_badge, "setBackgroundResource", getTypeBadgeBackground(task.type))
        
        // 根据完成状态只改变复选框图标
        if (isJustCompleted) {
            // 刚完成：显示勾选图标
            views.setImageViewResource(R.id.checkbox, R.drawable.widget_checkbox_checked)
        } else {
            // 未完成：显示空心圆
            views.setImageViewResource(R.id.checkbox, R.drawable.widget_checkbox_unchecked)
        }
        
        // 统一使用相同的文字颜色和背景
        views.setTextColor(R.id.task_title, context.getColor(R.color.on_surface))
        views.setTextColor(R.id.task_time, context.getColor(R.color.on_surface_variant))
        views.setInt(R.id.task_item_container, "setBackgroundResource", R.drawable.widget_item_background)
        
        // 设置点击事件（未完成和刚完成的都可以点击，刚完成的用于撤销）
        val fillInIntent = Intent().apply {
            putExtra(TaskListWidgetProvider.EXTRA_TASK_ID, task.id)
        }
        views.setOnClickFillInIntent(R.id.checkbox, fillInIntent)
        
        return views
    }
    
    private fun formatTaskTime(task: Task): String {
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_YEAR)
        
        calendar.timeInMillis = task.deadline
        val deadlineDay = calendar.get(Calendar.DAY_OF_YEAR)
        
        val startTime = timeFormat.format(task.startTime)
        val endTime = timeFormat.format(task.deadline)
        
        return if (deadlineDay == today) {
            "$startTime-$endTime"
        } else {
            "${dateFormat.format(task.deadline)} $endTime"
        }
    }
    
    private fun getTypeIndicatorRes(type: TaskType): Int {
        return when (type) {
            TaskType.WORK -> R.drawable.widget_type_indicator_work
            TaskType.LIFE -> R.drawable.widget_type_indicator_life
            TaskType.STUDY -> R.drawable.widget_type_indicator_study
            TaskType.URGENT -> R.drawable.widget_type_indicator_urgent
        }
    }
    
    private fun getTypeText(type: TaskType): String {
        return when (type) {
            TaskType.WORK -> context.getString(R.string.task_type_work)
            TaskType.LIFE -> context.getString(R.string.task_type_life)
            TaskType.STUDY -> context.getString(R.string.task_type_study)
            TaskType.URGENT -> context.getString(R.string.task_type_urgent)
        }
    }
    
    private fun getTypeBadgeBackground(type: TaskType): Int {
        return when (type) {
            TaskType.WORK -> R.drawable.widget_badge_work
            TaskType.LIFE -> R.drawable.widget_badge_life
            TaskType.STUDY -> R.drawable.widget_badge_study
            TaskType.URGENT -> R.drawable.widget_urgent_badge
        }
    }
    
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = if (position < tasks.size) tasks[position].id else position.toLong()
    override fun hasStableIds(): Boolean = true
}
