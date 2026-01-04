package com.litetask.app.widget

import android.content.Context
import android.content.Intent
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
        }
        
        /**
         * 获取刚完成的任务（如果还在显示时间内）
         */
        fun getJustCompletedTask(taskId: Long): Task? {
            val (task, completedTime) = recentlyCompletedTasks[taskId] ?: return null
            if (System.currentTimeMillis() - completedTime <= COMPLETED_DISPLAY_DURATION) {
                return task
            }
            // 过期了，移除
            recentlyCompletedTasks.remove(taskId)
            return null
        }
        
        /**
         * 获取所有刚完成的任务（还在显示时间内的）
         */
        fun getAllJustCompletedTasks(): List<Task> {
            val now = System.currentTimeMillis()
            val result = mutableListOf<Task>()
            val expiredIds = mutableListOf<Long>()
            
            recentlyCompletedTasks.forEach { (id, pair) ->
                val (task, completedTime) = pair
                if (now - completedTime <= COMPLETED_DISPLAY_DURATION) {
                    result.add(task)
                } else {
                    expiredIds.add(id)
                }
            }
            
            // 清理过期的
            expiredIds.forEach { recentlyCompletedTasks.remove(it) }
            
            return result
        }
        
        /**
         * 检查任务是否刚完成
         */
        fun isJustCompleted(taskId: Long): Boolean {
            return getJustCompletedTask(taskId) != null
        }
        
        /**
         * 撤销任务完成状态（从缓存中移除）
         */
        fun undoTaskCompletion(taskId: Long) {
            recentlyCompletedTasks.remove(taskId)
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
        loadData()
    }
    
    private fun loadData() {
        runBlocking {
            try {
                val dao = AppDatabase.getInstance(context).taskDao()
                // 获取所有未完成任务
                val activeTasks = dao.getActiveTasksWithRecentlyCompletedSync().toMutableList()
                
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
                
                // 记录哪些任务刚完成
                justCompletedTaskIds = justCompletedTasks.map { it.id }.toSet()
                
                tasks = activeTasks
            } catch (e: Exception) {
                tasks = emptyList()
                justCompletedTaskIds = emptySet()
            }
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
            if (task.isPinned && !isJustCompleted) android.view.View.VISIBLE else android.view.View.GONE)
        
        // 根据完成状态设置不同的样式
        if (isJustCompleted) {
            // 刚完成：显示勾选图标，绿色背景
            views.setImageViewResource(R.id.checkbox, R.drawable.widget_checkbox_checked)
            views.setTextColor(R.id.task_title, context.getColor(R.color.on_surface_variant))
            views.setTextColor(R.id.task_time, context.getColor(R.color.outline))
            // 隐藏类型标签
            views.setViewVisibility(R.id.urgent_badge, android.view.View.GONE)
            // 设置整个条目背景为已完成样式
            views.setInt(R.id.task_item_container, "setBackgroundResource", R.drawable.widget_item_done_background)
        } else {
            // 未完成：显示空心圆，正常颜色
            views.setImageViewResource(R.id.checkbox, R.drawable.widget_checkbox_unchecked)
            views.setTextColor(R.id.task_title, context.getColor(R.color.on_surface))
            views.setTextColor(R.id.task_time, context.getColor(R.color.on_surface_variant))
            // 显示任务类型标签
            views.setViewVisibility(R.id.urgent_badge, android.view.View.VISIBLE)
            views.setTextViewText(R.id.urgent_badge, getTypeText(task.type))
            views.setInt(R.id.urgent_badge, "setBackgroundResource", getTypeBadgeBackground(task.type))
            // 正常背景
            views.setInt(R.id.task_item_container, "setBackgroundResource", R.drawable.widget_item_background)
        }
        
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
