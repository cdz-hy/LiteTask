package com.litetask.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.litetask.app.data.local.AppDatabase
import com.litetask.app.data.model.TaskType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 提醒广播接收器
 * 
 * 负责接收 AlarmManager 发出的提醒信号并显示通知
 * 
 * 优化后的策略：
 * - 使用 ReminderDisplayStrategy 智能决策显示方式
 * - 根据屏幕状态、锁屏状态、权限情况自动选择最佳方案
 * - 支持快速路径（Intent 携带数据）和慢速路径（查询数据库）
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"
        private const val WAKELOCK_TIMEOUT = 10000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMINDER_TRIGGER) return
        
        val pendingResult = goAsync() // 允许异步处理，防止进程被立即杀死
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 获取 WakeLock 保持 CPU 运行
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, 
                    "LiteTask:ReminderWakeLock"
                )
                wakeLock.acquire(WAKELOCK_TIMEOUT)
                
                try {
                    processReminder(context, intent)
                } finally {
                    if (wakeLock.isHeld) wakeLock.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onReceive: ${e.message}")
            } finally {
                pendingResult.finish() // 必须调用，告知系统异步处理结束
            }
        }
    }

    /**
     * 处理提醒
     */
    private suspend fun processReminder(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1)
        val taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1)
        val taskTitle = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_TITLE)
        val reminderLabel = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_LABEL)
        val categoryName = intent.getStringExtra(ReminderScheduler.EXTRA_CATEGORY_NAME)
        val categoryColor = intent.getStringExtra(ReminderScheduler.EXTRA_CATEGORY_COLOR)

        Log.i(TAG, "★ Receiver wake: reminderId=$reminderId, taskId=$taskId, title=$taskTitle")

        // 快速路径：Intent 中包含完整数据
        if (reminderId != -1L && !taskTitle.isNullOrEmpty()) {
            val taskType = try { 
                TaskType.valueOf(intent.getStringExtra("task_type") ?: "WORK") 
            } catch (e: Exception) { 
                TaskType.WORK 
            }
            val isDeadline = reminderLabel?.contains("截止") == true

            withContext(Dispatchers.Main) {
                showReminder(context, taskId, taskTitle, reminderLabel ?: "任务提醒", taskType, isDeadline, categoryName, categoryColor)
            }
            
            // 更新数据库状态
            AppDatabase.getInstance(context).taskDao().updateReminderFired(reminderId, true)
            
        } else if (taskId != -1L) {
            // 慢速路径：数据缺失，查询数据库
            Log.w(TAG, "Data incomplete, querying database...")
            processReminderFromDatabase(context, reminderId, taskId)
        } else {
            Log.e(TAG, "Invalid reminder data: reminderId=$reminderId, taskId=$taskId")
        }
    }

    /**
     * 从数据库查询并处理提醒（慢速路径）
     */
    private suspend fun processReminderFromDatabase(context: Context, reminderId: Long, taskId: Long) {
        try {
            val db = AppDatabase.getInstance(context)
            val taskDetail = db.taskDao().getTaskDetailCompositeSync(taskId)
            
            if (taskDetail == null) {
                Log.e(TAG, "Task not found: taskId=$taskId")
                return
            }
            val task = taskDetail.task
            
            if (task.isDone) {
                Log.i(TAG, "Task already done, skipping reminder")
                db.taskDao().updateReminderFired(reminderId, true)
                return
            }

            val reminder = db.taskDao().getRemindersByTaskIdSync(taskId).find { it.id == reminderId }
            val label = reminder?.label ?: "任务提醒"
            val isDeadline = label.contains("截止")
            
            val category = taskDetail.category
            
            withContext(Dispatchers.Main) {
                showReminder(context, taskId, task.title, label, task.type, isDeadline, category?.name, category?.colorHex)
            }
            
            db.taskDao().updateReminderFired(reminderId, true)
        } catch (e: Exception) {
            Log.e(TAG, "Database query failed: ${e.message}")
        }
    }

    /**
     * 显示提醒
     * 
     * 使用智能策略管理器，根据设备状态和权限自动选择最佳显示方式：
     * - 息屏：唤醒屏幕 + 全屏 Activity
     * - 锁屏：在锁屏上显示 Activity
     * - 正常使用：只显示通知横幅
     */
    private fun showReminder(
        context: Context,
        taskId: Long,
        taskTitle: String,
        reminderText: String,
        taskType: TaskType,
        isDeadline: Boolean,
        categoryName: String? = null,
        categoryColor: String? = null
    ) {
        Log.i(TAG, "★ showReminder: $taskTitle")
        
        // 使用策略管理器智能决策并执行显示
        ReminderDisplayStrategy.executeDisplay(
            context = context,
            taskId = taskId,
            taskTitle = taskTitle,
            reminderText = reminderText,
            taskType = taskType,
            isDeadline = isDeadline,
            categoryName = categoryName,
            categoryColor = categoryColor
        )
    }
}
