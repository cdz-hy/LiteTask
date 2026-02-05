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
import java.util.concurrent.TimeUnit

/**
 * 截止提醒小组件 RemoteViewsService
 * 列表模式：根据截止时间显示不同颜色的倒计时
 * - 24h内：红色（紧急）
 * - 48h内：橙色（临近）
 * - 48h以上：绿色（正常）
 */
class DeadlineWidgetService : RemoteViewsService() {
    
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return DeadlineRemoteViewsFactory(applicationContext)
    }
}

class DeadlineRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {
    
    private var tasks: List<Task> = emptyList()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
    
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
                    // 自动同步状态
                    dao.autoSyncTaskExpiredStatus(System.currentTimeMillis())
                    
                    tasks = dao.getUpcomingDeadlinesSyncWithTime(System.currentTimeMillis(), 10)
                    
                    Log.d("DeadlineWidget", "Loaded ${tasks.size} deadline tasks")
                } catch (e: Exception) {
                    Log.e("DeadlineWidget", "Error loading tasks", e)
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
            return RemoteViews(context.packageName, R.layout.widget_deadline_item)
        }
        
        val task = tasks[position]
        val views = RemoteViews(context.packageName, R.layout.widget_deadline_item)
        val now = System.currentTimeMillis()
        
        // 计算剩余时间
        val remainingMs = task.deadline - now
        val hours = TimeUnit.MILLISECONDS.toHours(remainingMs)
        
        // 设置倒计时数值和单位
        val (countdownValue, countdownUnit) = when {
            hours < 1 -> {
                val mins = TimeUnit.MILLISECONDS.toMinutes(remainingMs).coerceAtLeast(1)
                Pair(mins.toString(), "分钟")
            }
            hours < 24 -> Pair(hours.toString(), "小时")
            hours < 48 -> Pair(hours.toString(), "小时")
            else -> {
                val days = TimeUnit.MILLISECONDS.toDays(remainingMs)
                Pair(days.toString(), "天")
            }
        }
        views.setTextViewText(R.id.countdown_value, countdownValue)
        views.setTextViewText(R.id.countdown_unit, countdownUnit)
        
        // 根据紧急程度设置倒计时颜色（与preview样式一致）
        when {
            hours < 24 -> {
                // 紧急：红色
                views.setTextColor(R.id.countdown_value, context.getColor(R.color.urgent_task))
                views.setTextColor(R.id.countdown_unit, context.getColor(R.color.on_surface_variant))
            }
            hours < 48 -> {
                // 临近：橙色
                views.setTextColor(R.id.countdown_value, context.getColor(R.color.warning_color))
                views.setTextColor(R.id.countdown_unit, context.getColor(R.color.on_surface_variant))
            }
            else -> {
                // 正常：默认颜色
                views.setTextColor(R.id.countdown_value, context.getColor(R.color.on_surface))
                views.setTextColor(R.id.countdown_unit, context.getColor(R.color.on_surface_variant))
            }
        }
        
        // 设置任务标题
        views.setTextViewText(R.id.task_title, task.title)
        
        // 设置任务类型指示条和标签
        views.setImageViewResource(R.id.type_indicator, getTypeIndicatorRes(task.type))
        views.setTextViewText(R.id.type_badge, getTypeText(task.type))
        views.setInt(R.id.type_badge, "setBackgroundResource", getTypeBadgeBackground(task.type))
        
        // 设置截止时间
        val deadlineTime = formatDeadlineTime(task.deadline)
        views.setTextViewText(R.id.deadline_time, deadlineTime)
        
        // 设置点击事件
        val fillInIntent = Intent()
        views.setOnClickFillInIntent(R.id.deadline_item_container, fillInIntent)
        
        return views
    }
    
    private fun formatDeadlineTime(deadline: Long): String {
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_YEAR)
        val thisYear = calendar.get(Calendar.YEAR)
        
        calendar.timeInMillis = deadline
        val deadlineDay = calendar.get(Calendar.DAY_OF_YEAR)
        val deadlineYear = calendar.get(Calendar.YEAR)
        
        val time = timeFormat.format(deadline)
        
        return when {
            deadlineDay == today && deadlineYear == thisYear -> "今天 $time"
            deadlineDay == today + 1 && deadlineYear == thisYear -> "明天 $time"
            else -> "${dateFormat.format(deadline)} $time"
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
    
    private fun getTypeIndicatorRes(type: TaskType): Int {
        return when (type) {
            TaskType.WORK -> R.drawable.widget_type_indicator_work
            TaskType.LIFE -> R.drawable.widget_type_indicator_life
            TaskType.STUDY -> R.drawable.widget_type_indicator_study
            TaskType.URGENT -> R.drawable.widget_type_indicator_urgent
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
