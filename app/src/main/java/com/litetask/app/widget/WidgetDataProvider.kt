package com.litetask.app.widget

import android.os.Binder
import android.util.Log
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Widget 数据提供者
 * 
 * 为所有小组件提供统一的工具方法
 */
object WidgetDataProvider {
    
    private const val TAG = "WidgetDataProvider"
    
    // 最近完成的任务缓存（taskId -> 完成时间戳）
    // 用于在小组件中显示已完成状态，然后延迟移除
    private val recentlyCompletedTasks = ConcurrentHashMap<Long, Long>()
    
    // 缓存过期时间（3秒）
    private const val CACHE_EXPIRY_MS = 3000L
    
    /**
     * 标记任务完成
     * 使用 Binder.clearCallingIdentity() 确保在 App 进程被杀后也能正常访问数据库
     */
    suspend fun markTaskDone(context: android.content.Context, taskId: Long): Boolean {
        val identityToken = Binder.clearCallingIdentity()
        return try {
            val dao = com.litetask.app.data.local.AppDatabase.getInstance(context).taskDao()
            val task = dao.getTaskById(taskId)
            if (task != null && !task.isDone) {
                dao.updateTask(task.copy(isDone = true))
                // 记录完成时间到缓存
                recentlyCompletedTasks[taskId] = System.currentTimeMillis()
                Log.d(TAG, "Task marked done: $taskId")
                true
            } else {
                Log.w(TAG, "Task not found or already done: $taskId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking task done: $taskId", e)
            false
        } finally {
            Binder.restoreCallingIdentity(identityToken)
        }
    }
    
    /**
     * 撤销任务完成（标记为未完成）
     * 使用 Binder.clearCallingIdentity() 确保在 App 进程被杀后也能正常访问数据库
     */
    suspend fun markTaskUndone(context: android.content.Context, taskId: Long): Boolean {
        val identityToken = Binder.clearCallingIdentity()
        return try {
            val dao = com.litetask.app.data.local.AppDatabase.getInstance(context).taskDao()
            val task = dao.getTaskById(taskId)
            if (task != null) {
                // 直接设为未完成，不检查当前状态（因为调用方已通过 isJustCompleted 确认）
                dao.updateTask(task.copy(isDone = false))
                // 从缓存中移除
                recentlyCompletedTasks.remove(taskId)
                Log.d(TAG, "Task marked undone: $taskId")
                true
            } else {
                Log.w(TAG, "Task not found: $taskId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking task undone: $taskId", e)
            false
        } finally {
            Binder.restoreCallingIdentity(identityToken)
        }
    }
    
    /**
     * 获取任务（带 Binder 身份清理）
     */
    suspend fun getTaskById(context: android.content.Context, taskId: Long): Task? {
        val identityToken = Binder.clearCallingIdentity()
        return try {
            val dao = com.litetask.app.data.local.AppDatabase.getInstance(context).taskDao()
            dao.getTaskById(taskId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting task: $taskId", e)
            null
        } finally {
            Binder.restoreCallingIdentity(identityToken)
        }
    }
    
    /**
     * 检查任务是否是最近完成的（用于显示完成状态）
     */
    fun isRecentlyCompleted(taskId: Long): Boolean {
        val completedTime = recentlyCompletedTasks[taskId] ?: return false
        val elapsed = System.currentTimeMillis() - completedTime
        if (elapsed > CACHE_EXPIRY_MS) {
            // 过期了，移除缓存
            recentlyCompletedTasks.remove(taskId)
            return false
        }
        return true
    }
    
    /**
     * 清理过期的缓存
     */
    fun cleanupExpiredCache() {
        val now = System.currentTimeMillis()
        recentlyCompletedTasks.entries.removeIf { (_, completedTime) ->
            now - completedTime > CACHE_EXPIRY_MS
        }
    }
    
    /**
     * 计算任务进度百分比
     */
    fun calculateProgress(task: Task): Int {
        if (task.isDone) return 100
        
        val now = System.currentTimeMillis()
        val start = task.startTime
        val end = task.deadline
        
        if (now <= start) return 0
        if (now >= end) return 100
        
        val total = end - start
        val elapsed = now - start
        return ((elapsed.toFloat() / total) * 100).toInt().coerceIn(0, 100)
    }
    
    /**
     * 格式化剩余时间
     */
    fun formatRemainingTime(deadline: Long): Pair<String, String> {
        val remaining = deadline - System.currentTimeMillis()
        if (remaining <= 0) return "0" to "h"
        
        val hours = TimeUnit.MILLISECONDS.toHours(remaining)
        val days = TimeUnit.MILLISECONDS.toDays(remaining)
        
        return when {
            days > 0 -> days.toString() to "d"
            hours > 0 -> hours.toString() to "h"
            else -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
                minutes.toString() to "m"
            }
        }
    }
    
    /**
     * 判断任务是否紧急（24小时内截止）
     */
    fun isUrgent(task: Task): Boolean {
        if (task.isDone) return false
        val remaining = task.deadline - System.currentTimeMillis()
        return remaining in 1..TimeUnit.HOURS.toMillis(24)
    }
    
    /**
     * 获取任务类型对应的颜色资源ID
     */
    fun getTypeColorRes(type: TaskType): Int {
        return when (type) {
            TaskType.WORK -> com.litetask.app.R.color.work_task
            TaskType.LIFE -> com.litetask.app.R.color.life_task
            TaskType.STUDY -> com.litetask.app.R.color.study_task
            TaskType.URGENT -> com.litetask.app.R.color.urgent_task
        }
    }
    
    /**
     * 获取任务类型对应的背景资源ID
     */
    fun getTypeBackgroundRes(type: TaskType): Int {
        return when (type) {
            TaskType.WORK -> com.litetask.app.R.drawable.widget_item_background_work
            TaskType.LIFE -> com.litetask.app.R.drawable.widget_item_background_life
            TaskType.STUDY -> com.litetask.app.R.drawable.widget_item_background_study
            TaskType.URGENT -> com.litetask.app.R.drawable.widget_item_background_urgent
        }
    }
    
    /**
     * 获取甘特图条背景资源ID
     */
    fun getGanttBarBackgroundRes(type: TaskType): Int {
        return when (type) {
            TaskType.WORK -> com.litetask.app.R.drawable.widget_gantt_bar_work
            TaskType.LIFE -> com.litetask.app.R.drawable.widget_gantt_bar_life
            TaskType.STUDY -> com.litetask.app.R.drawable.widget_gantt_bar_study
            TaskType.URGENT -> com.litetask.app.R.drawable.widget_gantt_bar_urgent
        }
    }
}
