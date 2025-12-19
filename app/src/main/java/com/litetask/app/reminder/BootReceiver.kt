package com.litetask.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.litetask.app.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机广播接收器
 * 
 * 手机重启后，AlarmManager 的所有闹钟会自动清空。
 * 此接收器负责在开机后重新注册所有待触发的提醒。
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON" &&
            intent.action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        Log.d(TAG, "Boot completed, restoring reminders...")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                restoreReminders(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring reminders: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun restoreReminders(context: Context) {
        val database = AppDatabase.getInstance(context)
        val taskDao = database.taskDao()
        val scheduler = ReminderScheduler(context)

        val now = System.currentTimeMillis()

        // 查询所有未触发且触发时间在未来的提醒
        val pendingReminders = taskDao.getFutureReminders(now)

        if (pendingReminders.isEmpty()) {
            Log.d(TAG, "No pending reminders to restore")
            return
        }

        var successCount = 0
        
        // 遍历每个提醒，获取任务信息后注册
        for (reminder in pendingReminders) {
            val task = taskDao.getTaskByIdSync(reminder.taskId)
            
            // 跳过已完成或不存在的任务
            if (task == null || task.isDone) {
                Log.d(TAG, "Skipping reminder ${reminder.id}: task null or done")
                continue
            }
            
            // 使用带任务信息的方法注册，确保触发时能直接显示通知
            val success = scheduler.scheduleReminderWithTaskInfo(
                reminder = reminder,
                taskTitle = task.title,
                taskType = task.type.name
            )
            
            if (success) {
                successCount++
            }
        }

        Log.d(TAG, "Restored $successCount/${pendingReminders.size} reminders after boot")
    }
}
