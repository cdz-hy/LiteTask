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
 * - 作为所有通知方式的兜底方案
 *
 * 注意：此类只负责标准 Android 通知，小米特有逻辑在 MiuiIslandHelper
 */
object NotificationHelper {

    private const val TAG = "NotificationHelper"

    // 通知渠道
    private const val CHANNEL_NORMAL = "litetask_reminder_normal"
    private const val CHANNEL_URGENT = "litetask_reminder_urgent"
    const val CHANNEL_ID_FOCUS = "litetask_focus"

    // Intent Extras
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_FROM_NOTIFICATION = "from_notification"

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
        taskType: TaskType
    ) {
        Log.i(TAG, "★ showReminderNotification: $taskTitle")

        val notification = buildReminderNotification(context, taskId, taskTitle, reminderLabel, taskType)

        try {
            NotificationManagerCompat.from(context).notify(taskId.toInt(), notification)
            Log.i(TAG, "★ Notification posted: $taskTitle")
        } catch (e: SecurityException) {
            Log.e(TAG, "No notification permission: ${e.message}")
        }
    }

    /**
     * 构建提醒通知（不发送）
     */
    fun buildReminderNotification(
        context: Context,
        taskId: Long,
        taskTitle: String,
        reminderLabel: String?,
        taskType: TaskType
    ): Notification {
        val info = parseReminderLabel(reminderLabel)
        val channelId = if (info.isDeadline) CHANNEL_URGENT else CHANNEL_NORMAL
        val color = getColor(taskType, info.isDeadline)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_FROM_NOTIFICATION, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, taskId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(taskTitle)
            .setContentText(info.displayText)
            .setSubText(getTypeLabel(taskType))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(color)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(info.displayText)
                    .setBigContentTitle(taskTitle)
            )

        // Heads-up 触发
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setFullScreenIntent(pendingIntent, true)
        }

        return builder.build()
    }

    /**
     * 发送通知
     */
    fun postNotification(context: Context, notificationId: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Log.i(TAG, "★ Notification posted: id=$notificationId")
        } catch (e: SecurityException) {
            Log.e(TAG, "No notification permission: ${e.message}")
        }
    }

    /**
     * 取消通知
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    /**
     * 取消焦点通知
     */
    fun cancelFocusNotification(context: Context, taskId: Long) {
        val notificationId = (taskId.toInt() + 20000)
        NotificationManagerCompat.from(context).cancel(notificationId)
        // 同时停止焦点通知服务
        FocusNotificationService.stop(context)
    }

    /**
     * 检查焦点通知渠道是否启用
     */
    fun isFocusChannelEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            val channel = manager.getNotificationChannel(CHANNEL_ID_FOCUS)
            if (channel == null) {
                Log.w(TAG, "Focus channel not created")
                return false
            }
            if (channel.importance == android.app.NotificationManager.IMPORTANCE_NONE) {
                Log.w(TAG, "Focus channel disabled")
                return false
            }
            return true
        }
        return true
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

    private fun getColor(type: TaskType, isDeadline: Boolean) = when (type) {
        TaskType.URGENT -> if (isDeadline) 0xFFB3261E.toInt() else 0xFFF43F5E.toInt()
        TaskType.WORK -> if (isDeadline) 0xFF0B57D0.toInt() else 0xFF3B82F6.toInt()
        TaskType.STUDY -> if (isDeadline) 0xFF7C3AED.toInt() else 0xFF8B5CF6.toInt()
        TaskType.LIFE -> if (isDeadline) 0xFF059669.toInt() else 0xFF10B981.toInt()
    }
}
