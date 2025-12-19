package com.litetask.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.litetask.app.MainActivity
import com.litetask.app.data.model.Reminder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 提醒调度器
 * 
 * 负责与系统 AlarmManager 交互，注册和取消闘钟。
 * 使用 setAlarmClock 实现最高优先级的闹钟，即使 App 被杀也能触发。
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    /**
     * 注册一个提醒闹钟
     * 
     * 使用 setAlarmClock 实现最高优先级闹钟：
     * - 系统会像对待系统闹钟一样对待它
     * - 即使 App 被杀后台也能触发
     * - 会在状态栏显示闹钟图标
     * - 不受 Doze 模式影响
     * 
     * @param reminder 提醒对象
     * @return true 如果成功注册，false 如果时间已过或权限不足
     */
    fun scheduleReminder(reminder: Reminder): Boolean {
        val now = System.currentTimeMillis()
        
        Log.d(TAG, "Attempting to schedule reminder: id=${reminder.id}, taskId=${reminder.taskId}, triggerAt=${reminder.triggerAt}, now=$now")
        
        // 时间已过，不注册
        if (reminder.triggerAt <= now) {
            Log.d(TAG, "Reminder ${reminder.id} trigger time has passed (triggerAt=${reminder.triggerAt} <= now=$now), skipping")
            return false
        }

        // 检查精确闹钟权限 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Cannot schedule exact alarms, permission not granted. SDK=${Build.VERSION.SDK_INT}")
                return false
            }
        }

        val pendingIntent = createPendingIntent(reminder)
        
        // 计算距离触发的时间
        val delayMs = reminder.triggerAt - now
        val delayMinutes = delayMs / 60000
        Log.d(TAG, "Scheduling alarm to trigger in $delayMinutes minutes ($delayMs ms)")

        try {
            // 使用 setAlarmClock - 最高优先级闹钟
            // 这是唯一能在国产手机上可靠触发的方式（即使 App 被杀）
            val showIntent = createShowIntent(reminder.taskId)
            val alarmClockInfo = AlarmManager.AlarmClockInfo(reminder.triggerAt, showIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            
            Log.i(TAG, "Successfully scheduled AlarmClock for reminder ${reminder.id}, task ${reminder.taskId}, will trigger at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(reminder.triggerAt))}")
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm: ${e.message}")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Exception scheduling alarm: ${e.message}")
            return false
        }
    }
    
    /**
     * 创建点击状态栏闹钟图标时的跳转 Intent
     */
    private fun createShowIntent(taskId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationHelper.EXTRA_TASK_ID, taskId)
            putExtra(NotificationHelper.EXTRA_FROM_NOTIFICATION, true)
        }
        return PendingIntent.getActivity(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 取消一个提醒闹钟
     * 
     * @param reminder 提醒对象
     */
    fun cancelReminder(reminder: Reminder) {
        val pendingIntent = createPendingIntent(reminder)
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Cancelled reminder ${reminder.id} for task ${reminder.taskId}")
    }

    /**
     * 取消某个任务的所有提醒
     * 
     * @param taskId 任务ID
     * @param reminderIds 该任务的所有提醒ID列表
     */
    fun cancelRemindersForTask(taskId: Long, reminderIds: List<Long>) {
        reminderIds.forEach { reminderId ->
            val pendingIntent = createPendingIntentById(reminderId, taskId)
            alarmManager.cancel(pendingIntent)
        }
        Log.d(TAG, "Cancelled ${reminderIds.size} reminders for task $taskId")
    }

    /**
     * 批量注册提醒（用于开机恢复）
     * 
     * @param reminders 提醒列表
     * @return 成功注册的数量
     */
    fun scheduleReminders(reminders: List<Reminder>): Int {
        var successCount = 0
        reminders.forEach { reminder ->
            if (scheduleReminder(reminder)) {
                successCount++
            }
        }
        Log.d(TAG, "Batch scheduled $successCount/${reminders.size} reminders")
        return successCount
    }

    /**
     * 检查是否有精确闹钟权限
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun createPendingIntent(reminder: Reminder): PendingIntent {
        return createPendingIntentById(reminder.id, reminder.taskId)
    }

    private fun createPendingIntentById(reminderId: Long, taskId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER_TRIGGER
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_TASK_ID, taskId)
        }
        
        // 使用 reminderId 作为 requestCode，确保每个提醒有唯一的 PendingIntent
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            flags
        )
    }
    
    /**
     * 注册提醒闹钟（带完整任务信息）
     * 将任务信息直接放入 Intent，这样 Receiver 被唤醒时可以直接显示通知
     */
    fun scheduleReminderWithTaskInfo(
        reminder: Reminder,
        taskTitle: String,
        taskType: String
    ): Boolean {
        val now = System.currentTimeMillis()
        
        if (reminder.triggerAt <= now) {
            Log.d(TAG, "Reminder ${reminder.id} trigger time has passed, skipping")
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Cannot schedule exact alarms, permission not granted")
                return false
            }
        }

        // 创建包含任务信息的 Intent
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER_TRIGGER
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_TASK_ID, reminder.taskId)
            putExtra(EXTRA_TASK_TITLE, taskTitle)
            putExtra(EXTRA_REMINDER_LABEL, reminder.label ?: "")
            putExtra(EXTRA_TASK_TYPE, taskType)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val showIntent = createShowIntent(reminder.taskId)
            val alarmClockInfo = AlarmManager.AlarmClockInfo(reminder.triggerAt, showIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            
            Log.i(TAG, "✓ Scheduled reminder with task info: id=${reminder.id}, title=$taskTitle, triggerAt=${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(reminder.triggerAt))}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception scheduling alarm: ${e.message}")
            return false
        }
    }
    
    companion object {
        private const val TAG = "ReminderScheduler"
        const val ACTION_REMINDER_TRIGGER = "com.litetask.app.REMINDER_TRIGGER"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TITLE = "task_title"
        const val EXTRA_REMINDER_LABEL = "reminder_label"
        const val EXTRA_TASK_TYPE = "task_type"
    }
}
