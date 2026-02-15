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

/**
 * 甘特图小组件 RemoteViewsService
 * 显示当天相关的所有任务（包括已完成的）
 */
class GanttWidgetService : RemoteViewsService() {
    
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return GanttRemoteViewsFactory(applicationContext)
    }
}

class GanttRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {
    
    private var tasks: List<com.litetask.app.data.model.TaskDetailComposite> = emptyList()
    private val dateTimeFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    override fun onCreate() {
        loadData()
    }
    
    override fun onDataSetChanged() {
        loadData()
    }
    
    private fun loadData() {
        // 清除调用者身份，以便使用应用的权限访问数据库
        val identityToken = Binder.clearCallingIdentity()
        try {
            runBlocking {
                try {
                    val dao = AppDatabase.getInstance(context).taskDao()
                    
                    // 计算今天的开始和结束时间
                    val calendar = Calendar.getInstance()
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    val startOfDay = calendar.timeInMillis
                    
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    val endOfDay = calendar.timeInMillis
                    
                    // 自动同步状态
                    dao.autoSyncTaskExpiredStatus(System.currentTimeMillis())
                    
                    // 获取今日相关的所有任务（包括已完成的）
                    tasks = dao.getTodayAllTaskCompositesSync(startOfDay, endOfDay)
                    
                    Log.d("GanttWidget", "Loaded ${tasks.size} today tasks")
                } catch (e: Exception) {
                    Log.e("GanttWidget", "Error loading tasks", e)
                    tasks = emptyList()
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
            return RemoteViews(context.packageName, R.layout.widget_gantt_item)
        }
        
        val composite = tasks[position]
        val task = composite.task
        val category = composite.category
        val views = RemoteViews(context.packageName, R.layout.widget_gantt_item)
        val now = System.currentTimeMillis()
        
        // 设置任务标题
        views.setTextViewText(R.id.task_title, task.title)
        
        // 计算进度百分比
        val progress = calculateTimeProgress(task, now)
        views.setTextViewText(R.id.progress_text, "${progress}%")
        
        // 设置完整的时间范围（日期+时间）
        val timeRange = formatTimeRange(task.startTime, task.deadline)
        views.setTextViewText(R.id.time_range, timeRange)
        
        // 设置进度状态文字
        val statusText = getProgressStatusText(task, progress, now)
        views.setTextViewText(R.id.progress_status, statusText)
        
        // 设置进度条进度值
        views.setProgressBar(R.id.progress_bar, 100, progress, false)
        
        // 设置类型指示条颜色
        if (task.isDone || task.isExpired) {
            // 已完成或已过期任务使用灰色指示条
            views.setImageViewResource(R.id.type_indicator, R.drawable.widget_type_indicator_done)
            views.setInt(R.id.type_indicator, "setColorFilter", 0) // 清除滤镜或设为透明/默认
        } else {
            // 使用通用的白色指示条背景，然后染色
            // 注意：这里假设有一个白色的 drawable，或者我们可以复用现有的并染色（只要它本身不是多色的）
            // 为了安全起见，我们使用 R.drawable.widget_type_indicator_work 作为底图，因为它是单色的
            views.setImageViewResource(R.id.type_indicator, R.drawable.widget_type_indicator_work)
            
            val color = if (category != null) {
                try {
                    android.graphics.Color.parseColor(category.colorHex)
                } catch (e: Exception) {
                    getTypeColor(task.type)
                }
            } else {
                getTypeColor(task.type)
            }
            // 使用 setColorFilter 动态改变颜色 (PorterDuff.Mode.SRC_IN)
            views.setInt(R.id.type_indicator, "setColorFilter", color)
        }
        
        // 设置已完成或已过期任务样式 - 整体变灰变淡
        if (task.isDone || task.isExpired) {
            views.setTextColor(R.id.task_title, context.getColor(R.color.outline))
            views.setTextColor(R.id.progress_text, context.getColor(R.color.outline))
            views.setTextColor(R.id.time_range, context.getColor(R.color.outline))
            views.setTextColor(R.id.progress_status, context.getColor(R.color.outline))
            views.setInt(R.id.gantt_item_container, "setBackgroundResource", R.drawable.widget_item_done_background)
            // 显示灰色进度条，隐藏彩色进度条
            views.setViewVisibility(R.id.progress_bar, android.view.View.GONE)
            views.setViewVisibility(R.id.progress_bar_done, android.view.View.VISIBLE)
        } else {
            views.setTextColor(R.id.task_title, context.getColor(R.color.on_surface))
            
            // Set text color using category color if available
            val textColor = if (category != null) {
                try {
                    android.graphics.Color.parseColor(category.colorHex)
                } catch (e: Exception) {
                    getTypeColor(task.type)
                }
            } else {
                getTypeColor(task.type)
            }
            views.setTextColor(R.id.progress_text, textColor)
            
            views.setTextColor(R.id.time_range, context.getColor(R.color.on_surface_variant))
            views.setTextColor(R.id.progress_status, context.getColor(R.color.on_surface_variant))
            views.setInt(R.id.gantt_item_container, "setBackgroundResource", R.drawable.widget_item_background)
            // 显示彩色进度条，隐藏灰色进度条
            views.setViewVisibility(R.id.progress_bar, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.progress_bar_done, android.view.View.GONE)
        }
        
        // 设置点击事件
        val fillInIntent = Intent()
        views.setOnClickFillInIntent(R.id.gantt_item_container, fillInIntent)
        
        return views
    }
    
    /**
     * 计算任务时间进度百分比
     */
    private fun calculateTimeProgress(task: Task, now: Long): Int {
        if (task.isDone) return 100
        
        val start = task.startTime
        val end = task.deadline
        
        return when {
            now < start -> 0  // 未开始
            now >= end -> 100 // 已结束
            else -> {
                val total = end - start
                val elapsed = now - start
                ((elapsed.toFloat() / total) * 100).toInt().coerceIn(0, 100)
            }
        }
    }
    
    /**
     * 格式化时间范围（显示完整日期时间）
     */
    private fun formatTimeRange(startTime: Long, deadline: Long): String {
        val startCal = Calendar.getInstance().apply { timeInMillis = startTime }
        val endCal = Calendar.getInstance().apply { timeInMillis = deadline }
        
        val sameDay = startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR) &&
                      startCal.get(Calendar.DAY_OF_YEAR) == endCal.get(Calendar.DAY_OF_YEAR)
        
        return if (sameDay) {
            // 同一天：显示日期 + 时间范围
            val dateStr = SimpleDateFormat("MM/dd", Locale.getDefault()).format(startTime)
            val startTimeStr = timeFormat.format(startTime)
            val endTimeStr = timeFormat.format(deadline)
            "$dateStr $startTimeStr - $endTimeStr"
        } else {
            // 不同天：显示完整日期时间
            "${dateTimeFormat.format(startTime)} - ${dateTimeFormat.format(deadline)}"
        }
    }
    
    /**
     * 获取进度状态文字
     */
    private fun getProgressStatusText(task: Task, progress: Int, now: Long): String {
        return when {
            task.isDone -> context.getString(R.string.widget_task_done)
            task.isExpired -> context.getString(R.string.widget_task_expired)
            now < task.startTime -> context.getString(R.string.widget_task_not_started)
            now >= task.deadline -> context.getString(R.string.widget_task_ended)
            else -> context.getString(R.string.widget_task_in_progress, progress)
        }
    }
    
    private fun getProgressDrawable(type: TaskType): Int {
        return when (type) {
            TaskType.WORK -> R.drawable.widget_gantt_progress_work
            TaskType.LIFE -> R.drawable.widget_gantt_progress_life
            TaskType.STUDY -> R.drawable.widget_gantt_progress_study
            TaskType.URGENT -> R.drawable.widget_gantt_progress_urgent
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
    
    private fun getProgressBarBackground(type: TaskType): Int {
        return when (type) {
            TaskType.WORK -> R.drawable.widget_gantt_bar_work
            TaskType.LIFE -> R.drawable.widget_gantt_bar_life
            TaskType.STUDY -> R.drawable.widget_gantt_bar_study
            TaskType.URGENT -> R.drawable.widget_gantt_bar_urgent
        }
    }
    
    private fun getTypeColor(type: TaskType): Int {
        return when (type) {
            TaskType.WORK -> context.getColor(R.color.work_task)
            TaskType.LIFE -> context.getColor(R.color.life_task)
            TaskType.STUDY -> context.getColor(R.color.study_task)
            TaskType.URGENT -> context.getColor(R.color.urgent_task)
        }
    }
    
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = if (position < tasks.size) tasks[position].task.id else position.toLong()
    override fun hasStableIds(): Boolean = true
}
