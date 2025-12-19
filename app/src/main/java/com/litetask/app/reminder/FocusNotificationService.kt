package com.litetask.app.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.litetask.app.MainActivity
import com.litetask.app.R
import com.litetask.app.data.model.TaskType

/**
 * 小米焦点通知前台服务
 *
 * 职责：通过前台服务 + 特殊配置触发小米灵动岛
 *
 * 策略说明：
 * - Android 14+ 限制了 phoneCall 类型需要特殊权限
 * - 改用 specialUse 类型 + 小米专用参数触发灵动岛
 * - 同时注入 miui.focus.param JSON 参数
 */
class FocusNotificationService : Service() {

    companion object {
        private const val TAG = "FocusNotificationSvc"

        const val CHANNEL_ID = "litetask_focus"
        private const val CHANNEL_NAME = "焦点通知"
        private const val NOTIFICATION_ID = 10001
        private const val AUTO_STOP_DELAY = 30_000L

        // Intent Extras
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_TASK_TITLE = "task_title"
        private const val EXTRA_REMINDER_LABEL = "reminder_label"
        private const val EXTRA_TASK_TYPE = "task_type"
        private const val EXTRA_IS_DEADLINE = "is_deadline"

        /** 启动服务 */
        fun start(
            context: Context,
            taskId: Long,
            taskTitle: String,
            reminderLabel: String?,
            taskType: TaskType,
            isDeadline: Boolean
        ) {
            Log.i(TAG, "★ Starting: $taskTitle")

            val intent = Intent(context, FocusNotificationService::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TASK_TITLE, taskTitle)
                putExtra(EXTRA_REMINDER_LABEL, reminderLabel)
                putExtra(EXTRA_TASK_TYPE, taskType.name)
                putExtra(EXTRA_IS_DEADLINE, isDeadline)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 停止服务 */
        fun stop(context: Context) {
            context.stopService(Intent(context, FocusNotificationService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "任务提醒"
        val reminderLabel = intent.getStringExtra(EXTRA_REMINDER_LABEL)
        val taskType = intent.getStringExtra(EXTRA_TASK_TYPE)?.let {
            try { TaskType.valueOf(it) } catch (e: Exception) { TaskType.WORK }
        } ?: TaskType.WORK
        val isDeadline = intent.getBooleanExtra(EXTRA_IS_DEADLINE, false)

        Log.i(TAG, "★★★ onStartCommand: $taskTitle ★★★")

        val notification = buildNotification(taskId, taskTitle, reminderLabel, taskType, isDeadline)
        startForegroundSafe(notification)

        // 自动停止
        handler.postDelayed({ stopSelf() }, AUTO_STOP_DELAY)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        Log.i(TAG, "Service destroyed")
    }


    /** 创建通知渠道 */
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "任务焦点通知，请开启「焦点通知」以启用灵动岛"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                enableLights(true)
                lightColor = Color.CYAN
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // 允许绕过勿扰模式
                setBypassDnd(true)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** 安全启动前台服务 */
    private fun startForegroundSafe(notification: Notification) {
        try {
            // Android 14+ 使用 specialUse 类型（不需要特殊权限）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10-13 使用默认类型
                startForeground(NOTIFICATION_ID, notification)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.i(TAG, "★ Foreground started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}")
            // 降级：直接发送通知
            try {
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
                Log.i(TAG, "★ Fallback: notification posted directly")
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback notification also failed: ${e2.message}")
            }
            stopSelf()
        }
    }

    /** 构建焦点通知 */
    private fun buildNotification(
        taskId: Long,
        taskTitle: String,
        reminderLabel: String?,
        taskType: TaskType,
        isDeadline: Boolean
    ): Notification {
        val contentText = formatReminderText(reminderLabel)
        val subText = if (isDeadline) "截止提醒" else "任务提醒"
        val color = getColor(taskType, isDeadline)

        // 点击跳转
        val viewIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationHelper.EXTRA_TASK_ID, taskId)
            putExtra(NotificationHelper.EXTRA_FROM_NOTIFICATION, true)
        }
        val viewPending = PendingIntent.getActivity(
            this, taskId.toInt(), viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 关闭按钮
        val dismissIntent = Intent(this, FocusNotificationDismissReceiver::class.java)
        val dismissPending = PendingIntent.getBroadcast(
            this, taskId.toInt() + 10000, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(taskTitle)
            .setContentText("${getTypeLabel(taskType)} · $contentText")
            .setSubText(subText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // 关键：使用 CATEGORY_REMINDER 而非 CATEGORY_ALARM，更容易通过小米鉴权
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(color)
            .setColorized(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(viewPending)
            .setDeleteIntent(dismissPending)
            .addAction(0, "知道了", dismissPending)
            .addAction(0, "查看", viewPending)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${getTypeLabel(taskType)} · $contentText")
                    .setBigContentTitle(taskTitle)
            )
            // 小米灵动岛需要这个标记
            .setLocalOnly(false)

        // 触发 Heads-up
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setFullScreenIntent(viewPending, true)
        }

        val notification = builder.build()

        // 注入小米灵动岛参数
        MiuiIslandHelper.decorateNotification(
            context = this,
            notification = notification,
            title = taskTitle,
            content = contentText,
            taskType = taskType,
            isDeadline = isDeadline
        )

        return notification
    }


    private fun formatReminderText(label: String?): String {
        if (label.isNullOrEmpty()) return "任务提醒"
        return when {
            label.contains("任务开始") || label == "开始时" -> "任务开始"
            label.contains("开始前") -> "还有 ${extractTime(label)} 开始"
            label.contains("截止前") -> "还有 ${extractTime(label)} 截止"
            else -> label
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

/** 焦点通知关闭接收器 */
class FocusNotificationDismissReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("FocusNotifDismiss", "Dismiss received")
        FocusNotificationService.stop(context)
    }
}
