package com.litetask.app.reminder

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.litetask.app.data.local.AppDatabase
import com.litetask.app.data.model.TaskType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 提醒广播接收器
 * 
 * 负责接收 AlarmManager 发出的提醒信号并显示通知
 * 
 * 通知策略：
 * 1. 屏幕亮着且未锁屏 + 有悬浮窗权限 → 使用悬浮窗（铃声+震动）
 * 2. 屏幕息屏或锁屏 → 直接使用系统通知（系统铃声+震动）
 * 3. 无悬浮窗权限 → 使用系统通知
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"
        private const val WAKELOCK_TIMEOUT = 10000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "★★★ onReceive: action=${intent.action}")
        
        if (intent.action != ReminderScheduler.ACTION_REMINDER_TRIGGER) {
            Log.w(TAG, "Unknown action: ${intent.action}")
            return
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LiteTask:ReminderWakeLock"
        )
        wakeLock.acquire(WAKELOCK_TIMEOUT)

        try {
            val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1)
            val taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1)
            val taskTitle = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_TITLE)
            val reminderLabel = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_LABEL)
            val taskTypeStr = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_TYPE)

            Log.i(TAG, "★ Reminder: id=$reminderId, taskId=$taskId, title=$taskTitle")

            if (reminderId == -1L || taskId == -1L) {
                Log.w(TAG, "Invalid reminder data")
                return
            }

            if (!taskTitle.isNullOrEmpty()) {
                // 快速路径：直接从 Intent 获取信息
                val taskType = try {
                    TaskType.valueOf(taskTypeStr ?: "WORK")
                } catch (e: Exception) { TaskType.WORK }
                
                val isDeadline = reminderLabel?.contains("截止") == true

                showReminder(context, taskId, taskTitle, reminderLabel ?: "任务提醒", taskType, isDeadline)
                updateReminderStatusAsync(context, reminderId)
            } else {
                // 慢路径：从数据库查询
                processReminderFromDatabase(context, reminderId, taskId)
            }
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    /**
     * 显示提醒
     * 
     * 策略：
     * 1. 屏幕亮着且未锁屏 + 有悬浮窗权限 → 使用悬浮窗
     * 2. 屏幕息屏或锁屏 → 直接使用系统通知（更可靠）
     * 3. 无悬浮窗权限 → 使用系统通知
     */
    private fun showReminder(
        context: Context,
        taskId: Long,
        taskTitle: String,
        reminderText: String,
        taskType: TaskType,
        isDeadline: Boolean
    ) {
        val screenState = getScreenState(context)
        Log.i(TAG, "★ Screen state: isOn=${screenState.isScreenOn}, isLocked=${screenState.isLocked}")
        
        // 只有屏幕亮着且未锁屏时才尝试悬浮窗
        val shouldUseFloating = screenState.isScreenOn && !screenState.isLocked && hasOverlayPermission(context)
        
        if (shouldUseFloating) {
            try {
                FloatingReminderService.show(context, taskId, taskTitle, reminderText, taskType, isDeadline)
                Log.i(TAG, "★ Floating window shown (screen on, unlocked)")
                return
            } catch (e: Exception) {
                Log.e(TAG, "Floating window failed: ${e.message}")
            }
        }
        
        // 息屏/锁屏/无权限/失败 → 使用系统通知
        val reason = when {
            !screenState.isScreenOn -> "screen off"
            screenState.isLocked -> "screen locked"
            !hasOverlayPermission(context) -> "no overlay permission"
            else -> "floating failed"
        }
        Log.i(TAG, "★ Using system notification ($reason)")
        NotificationHelper.showReminderNotification(context, taskId, taskTitle, reminderText, taskType)
    }

    private data class ScreenState(val isScreenOn: Boolean, val isLocked: Boolean)

    /**
     * 获取屏幕状态
     */
    private fun getScreenState(context: Context): ScreenState {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        
        val isScreenOn = powerManager.isInteractive
        val isLocked = keyguardManager.isKeyguardLocked
        
        return ScreenState(isScreenOn, isLocked)
    }

    /**
     * 检查悬浮窗权限
     */
    private fun hasOverlayPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    private fun updateReminderStatusAsync(context: Context, reminderId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.getInstance(context).taskDao().updateReminderFired(reminderId, true)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating reminder: ${e.message}")
            }
        }
    }

    private fun processReminderFromDatabase(context: Context, reminderId: Long, taskId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val task = db.taskDao().getTaskByIdSync(taskId) ?: return@launch
                if (task.isDone) {
                    db.taskDao().updateReminderFired(reminderId, true)
                    return@launch
                }

                val reminder = db.taskDao().getReminderByIdSync(reminderId)
                if (reminder == null || reminder.isFired) return@launch

                db.taskDao().updateReminderFired(reminderId, true)
                NotificationHelper.showReminderNotification(context, taskId, task.title, reminder.label, task.type)
                Log.i(TAG, "★ Notification shown: ${task.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
            }
        }
    }
}
