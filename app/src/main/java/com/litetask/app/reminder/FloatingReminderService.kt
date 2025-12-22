package com.litetask.app.reminder

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.litetask.app.MainActivity
import com.litetask.app.R
import com.litetask.app.data.model.TaskType

/**
 * 强提醒悬浮窗服务（类似系统闹钟）
 *
 * 特性：
 * - 息屏时强制亮屏显示
 * - 锁屏时显示在锁屏之上
 * - 全屏遮罩 + 居中弹窗
 * - 60秒持续显示
 * - 震动 + 闹钟铃声（可在设置中关闭）
 * - 只能通过按钮关闭
 * - Material Design 3 风格
 * - 支持深色模式
 */
class FloatingReminderService : Service() {

    companion object {
        private const val TAG = "FloatingReminder"
        private const val AUTO_DISMISS_DELAY = 60_000L // 60秒
        private const val VIBRATE_INTERVAL = 3000L // 每3秒震动一次
        private const val WAKELOCK_TIMEOUT = 65_000L // WakeLock 超时
        
        private const val PREFS_NAME = "litetask_prefs"
        private const val KEY_SOUND_ENABLED = "reminder_sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "reminder_vibration_enabled"

        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_TASK_TITLE = "task_title"
        private const val EXTRA_REMINDER_TEXT = "reminder_text"
        private const val EXTRA_TASK_TYPE = "task_type"
        private const val EXTRA_IS_DEADLINE = "is_deadline"

        fun canDrawOverlays(context: Context): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
        }

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
    private var mediaPlayer: MediaPlayer? = null
    private var pulseAnimator: ObjectAnimator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private var soundEnabled = true
    private var vibrationEnabled = true
    
    // 当前提醒的任务信息（用于超时后发送系统通知）
    private var currentTaskId: Long = -1
    private var currentTaskTitle: String = ""
    private var currentReminderText: String = ""
    private var currentTaskType: TaskType = TaskType.WORK
    
    // 标记用户是否已处理提醒（点击了按钮）
    private var userHandled = false
    
    private val handler = Handler(Looper.getMainLooper())
    private val vibrateRunnable = object : Runnable {
        override fun run() {
            if (vibrationEnabled) vibrate()
            handler.postDelayed(this, VIBRATE_INTERVAL)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        vibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
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
        cleanup()
    }

    /** 强制亮屏（类似系统闹钟） */
    private fun wakeUpScreen() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            wakeLock = powerManager.newWakeLock(
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ON_AFTER_RELEASE,
                "LiteTask:ReminderWakeLock"
            )
            wakeLock?.acquire(WAKELOCK_TIMEOUT)
            Log.i(TAG, "★ Screen wake up triggered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wake up screen: ${e.message}")
        }
    }

    /** 解除锁屏（如果可能） */
    @Suppress("DEPRECATION")
    private fun dismissKeyguard() {
        try {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                val keyguardLock = keyguardManager.newKeyguardLock("LiteTask")
                keyguardLock.disableKeyguard()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss keyguard: ${e.message}")
        }
    }


    private fun showFloating(
        taskId: Long,
        taskTitle: String,
        reminderText: String,
        taskType: TaskType,
        isDeadline: Boolean
    ) {
        cleanup()
        
        // 保存当前任务信息（用于超时后发送系统通知）
        currentTaskId = taskId
        currentTaskTitle = taskTitle
        currentReminderText = reminderText
        currentTaskType = taskType
        userHandled = false
        
        // 1. 强制亮屏
        wakeUpScreen()
        // 2. 尝试解除锁屏
        dismissKeyguard()

        floatingView = createView(taskId, taskTitle, reminderText, taskType, isDeadline)

        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            // 关键 flags：显示在锁屏之上 + 亮屏 + 保持屏幕常亮
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER
        }

        try {
            windowManager?.addView(floatingView, params)
            startAlertEffects()
            // 超时自动关闭（会触发系统通知）
            handler.postDelayed({ dismissWithTimeout() }, AUTO_DISMISS_DELAY)
            Log.i(TAG, "★ Floating shown (with wake): $taskTitle")
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

        val primaryColor = getPrimaryColor(taskType, isDeadline)
        val containerColor = getContainerColor(taskType, isDeadline, isDark)
        val surfaceColor = if (isDark) 0xFF1C1B1F.toInt() else 0xFFFFFFFF.toInt()
        val onSurface = if (isDark) 0xFFE6E1E5.toInt() else 0xFF1F1F1F.toInt()
        val onSurfaceVariant = if (isDark) 0xFFCAC4D0.toInt() else 0xFF757575.toInt()

        setupBlurEffect(view)

        view.findViewById<FrameLayout>(R.id.overlayContainer)?.setOnClickListener { }

        view.findViewById<LinearLayout>(R.id.cardContainer)?.let {
            (it.background?.mutate() as? GradientDrawable)?.setColor(surfaceColor)
        }

        view.findViewById<View>(R.id.accentBar)?.let {
            (it.background?.mutate() as? GradientDrawable)?.setColor(primaryColor)
        }

        view.findViewById<FrameLayout>(R.id.iconBox)?.let {
            (it.background?.mutate() as? GradientDrawable)?.setColor(containerColor)
            startPulseAnimation(it, primaryColor)
        }

        view.findViewById<ImageView>(R.id.icon)?.setColorFilter(primaryColor)

        view.findViewById<TextView>(R.id.title)?.apply {
            text = taskTitle
            setTextColor(onSurface)
        }

        view.findViewById<TextView>(R.id.subtitle)?.apply {
            text = "${getTypeLabel(taskType)} · ${if (isDeadline) "即将截止" else "即将开始"}"
            setTextColor(onSurfaceVariant)
        }

        view.findViewById<LinearLayout>(R.id.timeBadge)?.let {
            (it.background?.mutate() as? GradientDrawable)?.setColor(
                if (isDark) 0xFF2D2D2D.toInt() else 0xFFF5F5F5.toInt()
            )
        }
        view.findViewById<ImageView>(R.id.timeIcon)?.setColorFilter(onSurfaceVariant)
        view.findViewById<TextView>(R.id.timeText)?.apply {
            text = reminderText
            setTextColor(primaryColor)
        }

        view.findViewById<TextView>(R.id.dismissBtn)?.apply {
            setTextColor(onSurface)
            setOnClickListener { dismissByUser() }
        }

        view.findViewById<TextView>(R.id.actionBtn)?.apply {
            (background?.mutate() as? GradientDrawable)?.setColor(primaryColor)
            setOnClickListener { openApp(taskId) }
        }

        return view
    }

    private fun setupBlurEffect(view: View) {
        val blurLayer = view.findViewById<View>(R.id.blurLayer)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val renderEffectClass = Class.forName("android.view.RenderEffect")
                val createBlurEffectMethod = renderEffectClass.getMethod(
                    "createBlurEffect", 
                    Float::class.java, Float::class.java, 
                    android.graphics.Shader.TileMode::class.java, 
                    android.graphics.Shader.TileMode::class.java
                )
                val tileModeClamp = android.graphics.Shader.TileMode::class.java.getField("CLAMP").get(null)
                val blurEffect = createBlurEffectMethod.invoke(null, 12f, 12f, tileModeClamp, tileModeClamp)
                val setRenderEffectMethod = View::class.java.getMethod("setRenderEffect", renderEffectClass)
                setRenderEffectMethod.invoke(blurLayer, blurEffect)
            } catch (e: Exception) {
                blurLayer.setBackgroundColor(0x4D000000)
            }
        } else {
            blurLayer.setBackgroundColor(0x4D000000)
        }
    }

    private fun startPulseAnimation(view: View, color: Int) {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.05f, 1f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.05f, 1f)
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun startAlertEffects() {
        if (soundEnabled) playAlarmSound()
        if (vibrationEnabled) {
            vibrate()
            handler.postDelayed(vibrateRunnable, VIBRATE_INTERVAL)
        }
    }

    private fun playAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@FloatingReminderService, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alarm: ${e.message}")
        }
    }

    private fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 100, 100, 100), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 100, 100, 100, 100, 100), -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed: ${e.message}")
        }
    }

    private fun openApp(taskId: Long) {
        userHandled = true // 用户已处理
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationHelper.EXTRA_TASK_ID, taskId)
            putExtra(NotificationHelper.EXTRA_FROM_NOTIFICATION, true)
        })
        dismiss()
    }

    /** 用户点击按钮关闭（已处理） */
    private fun dismissByUser() {
        userHandled = true
        Log.i(TAG, "★ Dismissed by user")
        dismiss()
    }

    /** 超时自动关闭（未处理，需要发送系统通知） */
    private fun dismissWithTimeout() {
        if (!userHandled && currentTaskId != -1L) {
            Log.i(TAG, "★ Timeout without user action, sending system notification")
            // 发送系统通知作为补充提醒
            NotificationHelper.showReminderNotification(
                this,
                currentTaskId,
                currentTaskTitle,
                "$currentReminderText（未查看）",
                currentTaskType
            )
        }
        dismiss()
    }

    private fun dismiss() {
        cleanup()
        stopSelf()
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        pulseAnimator?.cancel()
        pulseAnimator = null
        
        mediaPlayer?.let {
            try { if (it.isPlaying) it.stop(); it.release() } catch (e: Exception) { }
        }
        mediaPlayer = null

        floatingView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) { }
        }
        floatingView = null
        
        wakeLock?.let {
            try { if (it.isHeld) it.release() } catch (e: Exception) { }
        }
        wakeLock = null
    }

    private fun isDarkMode(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun getTypeLabel(type: TaskType) = when (type) {
        TaskType.URGENT -> "紧急任务"
        TaskType.WORK -> "工作任务"
        TaskType.STUDY -> "学习任务"
        TaskType.LIFE -> "生活任务"
    }

    private fun getPrimaryColor(type: TaskType, isDeadline: Boolean) = when (type) {
        TaskType.URGENT -> if (isDeadline) 0xFFB3261E.toInt() else 0xFFDC362E.toInt()
        TaskType.WORK -> if (isDeadline) 0xFF0B57D0.toInt() else 0xFF1A73E8.toInt()
        TaskType.STUDY -> if (isDeadline) 0xFF7C3AED.toInt() else 0xFF9333EA.toInt()
        TaskType.LIFE -> if (isDeadline) 0xFF047857.toInt() else 0xFF059669.toInt()
    }

    private fun getContainerColor(type: TaskType, isDeadline: Boolean, isDark: Boolean): Int {
        return if (isDark) {
            when (type) {
                TaskType.URGENT -> if (isDeadline) 0xFF93000A.toInt() else 0xFF601410.toInt()
                TaskType.WORK -> if (isDeadline) 0xFF004A77.toInt() else 0xFF1E3A5F.toInt()
                TaskType.STUDY -> if (isDeadline) 0xFF4A148C.toInt() else 0xFF3D1A6D.toInt()
                TaskType.LIFE -> if (isDeadline) 0xFF004D40.toInt() else 0xFF1B4332.toInt()
            }
        } else {
            when (type) {
                TaskType.URGENT -> if (isDeadline) 0xFFFFDAD6.toInt() else 0xFFFFF0F0.toInt()
                TaskType.WORK -> if (isDeadline) 0xFFD3E3FD.toInt() else 0xFFEFF6FF.toInt()
                TaskType.STUDY -> if (isDeadline) 0xFFE9DDFF.toInt() else 0xFFF3E8FF.toInt()
                TaskType.LIFE -> if (isDeadline) 0xFFBCF0DA.toInt() else 0xFFD1FAE5.toInt()
            }
        }
    }
}