package com.litetask.app.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.litetask.app.MainActivity
import com.litetask.app.R
import com.litetask.app.data.model.TaskType

/**
 * 系统通知工具类
 *
 * 职责：
 * - 创建和管理通知渠道
 * - 构建和发送系统通知
 * - 作为悬浮窗的兜底方案
 */
object NotificationHelper {

    private const val TAG = "NotificationHelper"

    // 通知渠道
    private const val CHANNEL_NORMAL = "litetask_reminder_normal"
    private const val CHANNEL_URGENT = "litetask_reminder_urgent"

    // Intent Extras
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_FROM_NOTIFICATION = "from_notification"
    const val EXTRA_CATEGORY_NAME = "category_name"
    const val EXTRA_CATEGORY_COLOR = "category_color"

    /**
     * 创建通知渠道（Application 启动时调用）
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)

        // 普通提醒渠道
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_NORMAL, "任务开始提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "任务开始时间提醒"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                enableLights(true)
                lightColor = Color.BLUE
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        )

        // 紧急提醒渠道
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_URGENT, "任务截止提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "任务截止时间提醒"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                enableLights(true)
                lightColor = Color.RED
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        )
    }

    /**
     * 显示提醒通知
     */
    fun showReminderNotification(
        context: Context,
        taskId: Long,
        taskTitle: String,
        reminderLabel: String?,
        taskType: TaskType,
        categoryName: String? = null,
        categoryColor: String? = null
    ) {
        Log.i(TAG, "★ showReminderNotification: $taskTitle")

        val notification = buildReminderNotification(context, taskId, taskTitle, reminderLabel, taskType, categoryName, categoryColor)

        try {
            NotificationManagerCompat.from(context).notify(taskId.toInt(), notification)
            Log.i(TAG, "★ Notification posted: $taskTitle")
        } catch (e: SecurityException) {
            Log.e(TAG, "No notification permission: ${e.message}")
        }
    }

    /**
     * 构建提醒通知
     */
    private fun buildReminderNotification(
        context: Context,
        taskId: Long,
        taskTitle: String,
        reminderLabel: String?,
        taskType: TaskType,
        categoryName: String?,
        categoryColor: String?
    ): Notification {
        val info = parseReminderLabel(reminderLabel)
        val channelId = if (info.isDeadline) CHANNEL_URGENT else CHANNEL_NORMAL
        val color = getColor(taskType, info.isDeadline, categoryColor)

        // 点击通知跳转到 MainActivity 并打开任务详情
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_FROM_NOTIFICATION, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, taskId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 全屏意图仍然使用 ReminderActivity（用于锁屏/息屏时自动弹出）
        val fullScreenIntent = Intent(context, ReminderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_FROM_NOTIFICATION, true)
            putExtra("task_title", taskTitle)
            putExtra("reminder_text", info.displayText)
            putExtra("task_type", taskType.name)
            putExtra("is_deadline", info.isDeadline)
            putExtra(EXTRA_CATEGORY_NAME, categoryName)
            putExtra(EXTRA_CATEGORY_COLOR, categoryColor)
        }
        
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, (taskId + 100000).toInt(), // 使用不同的 request code 避免冲突
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(taskTitle)
            .setContentText(info.displayText)
            .setSubText(categoryName ?: getTypeLabel(taskType))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent) // 点击通知 → MainActivity
            .setFullScreenIntent(fullScreenPendingIntent, true) // 全屏意图 → ReminderActivity
            .setColor(color)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(info.displayText)
                    .setBigContentTitle(taskTitle)
            )

        return builder.build()
    }

    /**
     * 取消通知
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    // ==================== 私有方法 ====================

    private data class ReminderInfo(val displayText: String, val isDeadline: Boolean)

    private fun parseReminderLabel(label: String?): ReminderInfo {
        if (label.isNullOrEmpty()) return ReminderInfo("任务提醒", false)

        return when {
            label.contains("任务开始") || label == "开始时" -> ReminderInfo("任务开始", false)
            label.contains("开始前") -> ReminderInfo("还有 ${extractTime(label)} 开始", false)
            label.contains("截止前") -> ReminderInfo("还有 ${extractTime(label)} 截止", true)
            else -> ReminderInfo(label, label.contains("截止"))
        }
    }

    private fun extractTime(label: String): String {
        return Regex("(\\d+)(分钟|小时|天)").find(label)?.value ?: "一段时间"
    }

    private fun getTypeLabel(type: TaskType) = when (type) {
        TaskType.URGENT -> "紧急任务"
        TaskType.WORK -> "工作任务"
        TaskType.STUDY -> "学习任务"
        TaskType.LIFE -> "生活任务"
    }

    private fun getColor(type: TaskType, isDeadline: Boolean, categoryColor: String?): Int {
        // 通知小图标颜色：截止提醒强制显着红，普通提醒使用任务主色
        return if (isDeadline) {
            0xFFB3261E.toInt()
        } else {
            if (categoryColor != null) {
                try {
                    return android.graphics.Color.parseColor(categoryColor)
                } catch (e: Exception) {
                    // Fallback
                }
            }
            when (type) {
                TaskType.WORK -> 0xFF0B57D0.toInt()
                TaskType.LIFE -> 0xFF146C2E.toInt()
                TaskType.STUDY -> 0xFF65558F.toInt()
                TaskType.URGENT -> 0xFFB3261E.toInt()
            }
        }
    }
}
