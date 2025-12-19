package com.litetask.app.reminder

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.litetask.app.MainActivity
import com.litetask.app.R
import com.litetask.app.data.model.TaskType

/**
 * 悬浮窗提醒服务
 *
 * 职责：在屏幕顶部显示自定义悬浮通知卡片
 *
 * 使用条件：
 * - SYSTEM_ALERT_WINDOW 权限
 * - 屏幕亮起且未锁屏
 *
 * 特性：
 * - Material Design 3 风格
 * - 支持深色模式
 * - 8秒后自动消失
 */
class FloatingReminderService : Service() {

    companion object {
        private const val TAG = "FloatingReminder"
        private const val AUTO_DISMISS_DELAY = 8_000L

        // Intent Extras
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_TASK_TITLE = "task_title"
        private const val EXTRA_REMINDER_TEXT = "reminder_text"
        private const val EXTRA_TASK_TYPE = "task_type"
        private const val EXTRA_IS_DEADLINE = "is_deadline"

        /** 检查悬浮窗权限 */
        fun canDrawOverlays(context: Context): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
        }

        /** 显示悬浮提醒 */
        fun show(
            context: Context,
            taskId: Long,
            taskTitle: String,
            reminderText: String,
            taskType: TaskType,
            isDeadline: Boolean
        ) {
            if (!canDrawOverlays(context)) {
                Log.w(TAG, "No overlay permission")
                return
            }

            Log.i(TAG, "★ Starting: $taskTitle")

            context.startService(Intent(context, FloatingReminderService::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TASK_TITLE, taskTitle)
                putExtra(EXTRA_REMINDER_TEXT, reminderText)
                putExtra(EXTRA_TASK_TYPE, taskType.name)
                putExtra(EXTRA_IS_DEADLINE, isDeadline)
            })
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val taskId = it.getLongExtra(EXTRA_TASK_ID, -1)
            val taskTitle = it.getStringExtra(EXTRA_TASK_TITLE) ?: ""
            val reminderText = it.getStringExtra(EXTRA_REMINDER_TEXT) ?: ""
            val taskType = it.getStringExtra(EXTRA_TASK_TYPE)?.let { name ->
                try { TaskType.valueOf(name) } catch (e: Exception) { TaskType.WORK }
            } ?: TaskType.WORK
            val isDeadline = it.getBooleanExtra(EXTRA_IS_DEADLINE, false)

            showFloating(taskId, taskTitle, reminderText, taskType, isDeadline)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        removeView()
    }


    private fun showFloating(
        taskId: Long,
        taskTitle: String,
        reminderText: String,
        taskType: TaskType,
        isDeadline: Boolean
    ) {
        removeView()

        floatingView = createView(taskId, taskTitle, reminderText, taskType, isDeadline)

        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = getStatusBarHeight()
        }

        try {
            windowManager?.addView(floatingView, params)
            floatingView?.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left))
            handler.postDelayed({ dismiss() }, AUTO_DISMISS_DELAY)
            Log.i(TAG, "★ Floating shown: $taskTitle")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show: ${e.message}")
            stopSelf()
        }
    }

    private fun createView(
        taskId: Long,
        taskTitle: String,
        reminderText: String,
        taskType: TaskType,
        isDeadline: Boolean
    ): View {
        val view = LayoutInflater.from(this).inflate(R.layout.floating_reminder, null)
        val isDark = isDarkMode()

        val primary = getPrimaryColor(taskType, isDeadline)
        val container = getContainerColor(taskType, isDark)
        val surface = if (isDark) 0xFF1C1B1F.toInt() else 0xFFFFFBFE.toInt()
        val onSurface = if (isDark) 0xFFE6E1E5.toInt() else 0xFF1C1B1F.toInt()
        val onSurfaceVariant = if (isDark) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt()

        try {
            // 卡片背景
            view.findViewById<LinearLayout>(R.id.cardContainer)?.let {
                (it.background?.mutate() as? GradientDrawable)?.setColor(surface)
                it.setOnClickListener { openApp(taskId) }
            }

            // 图标容器
            view.findViewById<LinearLayout>(R.id.iconBox)?.let {
                (it.background?.mutate() as? GradientDrawable)?.setColor(container)
            }

            // 图标
            view.findViewById<ImageView>(R.id.icon)?.setColorFilter(primary)

            // 标题
            view.findViewById<TextView>(R.id.title)?.apply {
                text = taskTitle
                setTextColor(onSurface)
            }

            // 副标题
            view.findViewById<TextView>(R.id.subtitle)?.apply {
                text = reminderText
                setTextColor(primary)
            }

            // 类型标签
            view.findViewById<TextView>(R.id.typeLabel)?.apply {
                text = getTypeLabel(taskType, isDeadline)
                (background?.mutate() as? GradientDrawable)?.setColor(primary)
            }

            // 关闭按钮
            view.findViewById<ImageView>(R.id.closeBtn)?.apply {
                setColorFilter(onSurfaceVariant)
                setOnClickListener { dismiss() }
            }

            // 稍后按钮
            view.findViewById<TextView>(R.id.laterBtn)?.apply {
                setTextColor(onSurfaceVariant)
                setOnClickListener { dismiss() }
            }

            // 查看按钮
            view.findViewById<TextView>(R.id.actionBtn)?.apply {
                setTextColor(Color.WHITE)
                (background?.mutate() as? GradientDrawable)?.setColor(primary)
                setOnClickListener { openApp(taskId) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "View setup error: ${e.message}")
        }

        return view
    }

    private fun openApp(taskId: Long) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationHelper.EXTRA_TASK_ID, taskId)
            putExtra(NotificationHelper.EXTRA_FROM_NOTIFICATION, true)
        })
        dismiss()
    }

    private fun dismiss() {
        floatingView?.let {
            it.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right))
            handler.postDelayed({ removeView(); stopSelf() }, 300)
        }
    }

    private fun removeView() {
        floatingView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) { }
            floatingView = null
        }
    }

    private fun isDarkMode(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun getStatusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) + 8 else 8
    }

    private fun getPrimaryColor(type: TaskType, isDeadline: Boolean) = when (type) {
        TaskType.URGENT -> if (isDeadline) 0xFFBA1A1A.toInt() else 0xFFDC362E.toInt()
        TaskType.WORK -> if (isDeadline) 0xFF0B57D0.toInt() else 0xFF1A73E8.toInt()
        TaskType.STUDY -> if (isDeadline) 0xFF7C3AED.toInt() else 0xFF9333EA.toInt()
        TaskType.LIFE -> if (isDeadline) 0xFF047857.toInt() else 0xFF059669.toInt()
    }

    private fun getContainerColor(type: TaskType, isDark: Boolean) = if (isDark) {
        when (type) {
            TaskType.URGENT -> 0xFF93000A.toInt()
            TaskType.WORK -> 0xFF004A77.toInt()
            TaskType.STUDY -> 0xFF4A148C.toInt()
            TaskType.LIFE -> 0xFF004D40.toInt()
        }
    } else {
        when (type) {
            TaskType.URGENT -> 0xFFFFDAD6.toInt()
            TaskType.WORK -> 0xFFD3E3FD.toInt()
            TaskType.STUDY -> 0xFFE9DDFF.toInt()
            TaskType.LIFE -> 0xFFBCF0DA.toInt()
        }
    }

    private fun getTypeLabel(type: TaskType, isDeadline: Boolean): String {
        val text = when (type) {
            TaskType.URGENT -> "紧急"
            TaskType.WORK -> "工作"
            TaskType.STUDY -> "学习"
            TaskType.LIFE -> "生活"
        }
        return if (isDeadline) "$text · 即将截止" else "$text · 即将开始"
    }
}
