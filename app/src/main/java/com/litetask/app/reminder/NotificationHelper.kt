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
    private const val CHANNEL_FOCUS = "litetask_focus_timer"

    // 专注通知固定 ID
    private const val FOCUS_NOTIFICATION_ID = 90001

    // Intent Extras
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_FROM_NOTIFICATION = "from_notification"
    const val EXTRA_CATEGORY_NAME = "category_name"
    const val EXTRA_CATEGORY_COLOR = "category_color"
    const val EXTRA_OPEN_FOCUS = "open_focus"
    const val EXTRA_COMPONENT_ACTION = "component_action"
    const val COMPONENT_ACTION_ROUTE = "route"
    const val COMPONENT_ACTION_LINK = "link"

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
        categoryColor: String? = null,
        componentExtras: ReminderComponentExtras? = null
    ) {
        Log.i(TAG, "★ showReminderNotification: $taskTitle")

        val notification = buildReminderNotification(
            context, taskId, taskTitle, reminderLabel, taskType, categoryName, categoryColor, componentExtras
        )

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
        categoryColor: String?,
        componentExtras: ReminderComponentExtras? = null
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
            componentExtras?.putToIntent(this)
        }
        
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, (taskId + 100000).toInt(), // 使用不同的 request code 避免冲突
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val highlight = componentExtras?.highlightLine?.takeIf { it.isNotBlank() }
        val contentLine = if (highlight != null) "${info.displayText} · $highlight" else info.displayText
        val expandedText = if (highlight != null) "${info.displayText}\n$highlight" else info.displayText

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(taskTitle)
            .setContentText(contentLine)
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
                    .bigText(expandedText)
                    .setBigContentTitle(taskTitle)
            )

        addComponentQuickActions(builder, context, taskId, componentExtras)

        return builder.build()
    }

    private fun addComponentQuickActions(
        builder: NotificationCompat.Builder,
        context: Context,
        taskId: Long,
        componentExtras: ReminderComponentExtras?
    ) {
        if (componentExtras == null || !componentExtras.hasAnyContent) return
        val baseRequestCode = (taskId.toInt() and 0x03FFFFFF) * 16
        var actionCount = 0

        if (componentExtras.hasPass) {
            val code = componentExtras.passCode!!
            val copyIntent = Intent(context, ReminderQuickActionReceiver::class.java).apply {
                action = ReminderQuickActionReceiver.ACTION_COPY_TEXT
                putExtra(ReminderQuickActionReceiver.EXTRA_COPY_LABEL, componentExtras.passTitle ?: "凭证号")
                putExtra(ReminderQuickActionReceiver.EXTRA_COPY_TEXT, code)
            }
            val copyPending = PendingIntent.getBroadcast(
                context,
                baseRequestCode + 1,
                copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "复制: $code", copyPending)
            actionCount++
        }

        if (componentExtras.hasRoute && actionCount < 2) {
            val navIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_FROM_NOTIFICATION, true)
                putExtra(EXTRA_COMPONENT_ACTION, COMPONENT_ACTION_ROUTE)
                componentExtras.putToIntent(this)
            }
            val navPending = PendingIntent.getActivity(
                context,
                baseRequestCode + 2,
                navIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "导航去目的地", navPending)
            actionCount++
        }

        if (componentExtras.hasLink && actionCount < 2) {
            val linkIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_FROM_NOTIFICATION, true)
                putExtra(EXTRA_COMPONENT_ACTION, COMPONENT_ACTION_LINK)
                componentExtras.putToIntent(this)
            }
            val linkPending = PendingIntent.getActivity(
                context,
                baseRequestCode + 3,
                linkIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "打开链接", linkPending)
            actionCount++
        }

        val secret = componentExtras.passSecret?.takeIf { it.isNotBlank() }
        if (secret != null && actionCount < 2) {
            val copySecretIntent = Intent(context, ReminderQuickActionReceiver::class.java).apply {
                action = ReminderQuickActionReceiver.ACTION_COPY_TEXT
                putExtra(ReminderQuickActionReceiver.EXTRA_COPY_LABEL, "密码")
                putExtra(ReminderQuickActionReceiver.EXTRA_COPY_TEXT, secret)
            }
            val secretPending = PendingIntent.getBroadcast(
                context,
                baseRequestCode + 4,
                copySecretIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "复制密码", secretPending)
        }
    }

    /**
     * 取消通知
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    /**
     * 更新专注计时进行中通知（使用系统 Chronometer 零功耗实时计时）
     */
    fun showFocusOngoingNotification(
        context: Context,
        taskTitle: String,
        isPomodoro: Boolean,
        isRunning: Boolean,
        elapsedSeconds: Long,
        remainingSeconds: Long
    ) {
        if (!PermissionHelper.hasNotificationPermission(context)) return
        ensureFocusChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_FOCUS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            FOCUS_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = taskTitle.ifBlank { "自由专注" }
        val modeLabel = if (isPomodoro) "番茄钟专注" else "正向专注计时"
        val builder = NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_clock)
            .setContentTitle("$modeLabel · $title")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (isRunning) {
            val baseTime = if (isPomodoro) {
                System.currentTimeMillis() + remainingSeconds * 1000L
            } else {
                System.currentTimeMillis() - elapsedSeconds * 1000L
            }
            builder.setWhen(baseTime)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(isPomodoro)
                .setContentText(if (isPomodoro) "专注倒计时进行中" else "正向计时进行中")
        } else {
            val mins = elapsedSeconds / 60
            val secs = elapsedSeconds % 60
            builder.setUsesChronometer(false)
                .setShowWhen(false)
                .setContentText(String.format(java.util.Locale.getDefault(), "已暂停 · 已专注 %02d:%02d", mins, secs))
        }

        try {
            NotificationManagerCompat.from(context).notify(FOCUS_NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException showing focus notification: ${e.message}")
        }
    }

    /**
     * 发送专注完成通知
     */
    fun showFocusCompletedNotification(context: Context, taskTitle: String, focusedSeconds: Long) {
        if (!PermissionHelper.hasNotificationPermission(context)) return
        ensureFocusChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_FOCUS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            FOCUS_NOTIFICATION_ID + 1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mins = (focusedSeconds / 60).coerceAtLeast(1)
        val notification = NotificationCompat.Builder(context, CHANNEL_NORMAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("专注完成 · ${taskTitle.ifBlank { "自由专注" }}")
            .setContentText("已完成 ${mins} 分钟专注，休息一下吧！")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(FOCUS_NOTIFICATION_ID + 1, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException showing focus complete notification: ${e.message}")
        }
    }

    fun cancelFocusNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(FOCUS_NOTIFICATION_ID)
    }

    private fun ensureFocusChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_FOCUS) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_FOCUS, "专注计时器", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "进行中的专注倒计时或正向计时状态"
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
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
