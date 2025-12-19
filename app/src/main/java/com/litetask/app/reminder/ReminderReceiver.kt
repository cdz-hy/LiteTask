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

/**
 * 提醒广播接收器
 * 
 * 负责接收 AlarmManager 发出的"时间到了"的信号。
 * 关键优化：
 * 1. 使用 WakeLock 确保设备不会在处理过程中休眠
 * 2. 优先从 Intent 中获取任务信息，避免数据库查询
 * 3. 快速显示通知，不做耗时操作
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"
        private const val WAKELOCK_TIMEOUT = 10000L // 10秒超时
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "★★★ ReminderReceiver.onReceive called! action=${intent.action}")
        
        if (intent.action != ReminderScheduler.ACTION_REMINDER_TRIGGER) {
            Log.w(TAG, "Unknown action: ${intent.action}")
            return
        }

        // 获取 WakeLock 确保设备不会休眠
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LiteTask:ReminderWakeLock"
        )
        wakeLock.acquire(WAKELOCK_TIMEOUT)

        try {
            val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1)
            val taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1)
            
            // 尝试从 Intent 中获取任务信息（新方式）
            val taskTitle = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_TITLE)
            val reminderLabel = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_LABEL)
            val taskTypeStr = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_TYPE)

            Log.i(TAG, "★ Reminder data: id=$reminderId, taskId=$taskId, title=$taskTitle")

            if (reminderId == -1L || taskId == -1L) {
                Log.w(TAG, "Invalid reminder data")
                return
            }

            // 如果 Intent 中有完整的任务信息，直接显示通知（快速路径）
            if (!taskTitle.isNullOrEmpty()) {
                Log.i(TAG, "★ Fast path: showing notification directly from Intent")
                val taskType = try {
                    TaskType.valueOf(taskTypeStr ?: "WORK")
                } catch (e: Exception) {
                    TaskType.WORK
                }
                
                // 判断是否是截止提醒
                val isDeadline = reminderLabel?.contains("截止") == true || 
                                 reminderLabel?.contains("deadline") == true
                
                // 使用通知分发器自动选择最佳展示方式
                // 会根据权限、屏幕状态、设备类型自动选择：灵动岛 / 悬浮窗 / 系统通知
                try {
                    NotificationDispatcher.dispatch(
                        context = context,
                        taskId = taskId,
                        taskTitle = taskTitle,
                        reminderText = reminderLabel ?: "任务提醒",
                        taskType = taskType,
                        isDeadline = isDeadline
                    )
                    Log.i(TAG, "★ NotificationDispatcher.dispatch() called successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "★ Error dispatching notification: ${e.message}", e)
                    // 降级到系统通知
                    NotificationHelper.showReminderNotification(
                        context = context,
                        taskId = taskId,
                        taskTitle = taskTitle,
                        reminderLabel = reminderLabel,
                        taskType = taskType
                    )
                }
                
                // 异步更新数据库状态（不阻塞通知显示）
                updateReminderStatusAsync(context, reminderId)
            } else {
                // 旧方式：从数据库查询（兼容旧的提醒）
                Log.i(TAG, "★ Slow path: querying database for task info")
                processReminderFromDatabase(context, reminderId, taskId)
            }
        } finally {
            // 释放 WakeLock
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
    
    /**
     * 异步更新提醒状态
     */
    private fun updateReminderStatusAsync(context: Context, reminderId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                database.taskDao().updateReminderFired(reminderId, true)
                Log.d(TAG, "Updated reminder $reminderId status to fired")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating reminder status: ${e.message}")
            }
        }
    }

    /**
     * 从数据库查询任务信息并显示通知（兼容旧提醒）
     */
    private fun processReminderFromDatabase(context: Context, reminderId: Long, taskId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val taskDao = database.taskDao()

                val task = taskDao.getTaskByIdSync(taskId)
                if (task == null) {
                    Log.d(TAG, "Task $taskId not found, skipping")
                    return@launch
                }

                if (task.isDone) {
                    Log.d(TAG, "Task $taskId is done, skipping")
                    taskDao.updateReminderFired(reminderId, true)
                    return@launch
                }

                val reminder = taskDao.getReminderByIdSync(reminderId)
                if (reminder == null || reminder.isFired) {
                    Log.d(TAG, "Reminder $reminderId not found or already fired")
                    return@launch
                }

                // 标记为已触发
                taskDao.updateReminderFired(reminderId, true)

                // 显示通知
                NotificationHelper.showReminderNotification(
                    context = context,
                    taskId = taskId,
                    taskTitle = task.title,
                    reminderLabel = reminder.label,
                    taskType = task.type
                )
                
                Log.i(TAG, "★ Notification shown for task: ${task.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing reminder: ${e.message}", e)
            }
        }
    }
}
