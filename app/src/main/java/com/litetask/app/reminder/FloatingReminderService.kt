package com.litetask.app.reminder

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
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
 * 强提醒悬浮窗服务
 *
 * 特性：
 * - 全屏遮罩 + 居中弹窗（仿 HTML 原型）
 * - 60秒持续显示
 * - 震动 + 闹钟铃声（可在设置中关闭）
 * - 只能通过按钮关闭
 * - Material Design 3 风格
 * - 支持深色模式
 * - Android 12+支持毛玻璃模糊效果
 */
class FloatingReminderService : Service() {

    companion object {
        private const val TAG = "FloatingReminder"
        private const val AUTO_DISMISS_DELAY = 60_000L // 60秒
        private const val VIBRATE_INTERVAL = 3000L // 每3秒震动一次
        
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
    
    // 设置项
    private var soundEnabled = true
    private var vibrationEnabled = true
    
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
        
        // 读取设置
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        vibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
        Log.d(TAG, "Settings: sound=$soundEnabled, vibration=$vibrationEnabled")
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

    private fun showFloating(
        taskId: Long,
        taskTitle: String,
        reminderText: String,
        taskType: TaskType,
        isDeadline: Boolean
    ) {
        cleanup()

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
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER
        }

        try {
            windowManager?.addView(floatingView, params)
            startAlertEffects()
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

        // 获取颜色
        val primaryColor = getPrimaryColor(taskType, isDeadline)
        val containerColor = getContainerColor(taskType, isDeadline, isDark)
        val surfaceColor = if (isDark) 0xFF1C1B1F.toInt() else 0xFFFFFFFF.toInt()
        val onSurface = if (isDark) 0xFFE6E1E5.toInt() else 0xFF1F1F1F.toInt()
        val onSurfaceVariant = if (isDark) 0xFFCAC4D0.toInt() else 0xFF757575.toInt()

        // 设置毛玻璃效果
        setupBlurEffect(view)

        // 遮罩层 - 点击不关闭
        view.findViewById<FrameLayout>(R.id.overlayContainer)?.setOnClickListener { /* 不处理 */ }

        // 卡片背景
        view.findViewById<LinearLayout>(R.id.cardContainer)?.let {
            (it.background?.mutate() as? GradientDrawable)?.setColor(surfaceColor)
        }

        // 顶部装饰条（使用 GradientDrawable 设置颜色）
        view.findViewById<View>(R.id.accentBar)?.let {
            (it.background?.mutate() as? GradientDrawable)?.setColor(primaryColor)
        }

        // 图标容器
        view.findViewById<FrameLayout>(R.id.iconBox)?.let {
            (it.background?.mutate() as? GradientDrawable)?.setColor(containerColor)
            // 启动呼吸动画
            startPulseAnimation(it, primaryColor)
        }

        // 图标
        view.findViewById<ImageView>(R.id.icon)?.setColorFilter(primaryColor)

        // 标题
        view.findViewById<TextView>(R.id.title)?.apply {
            text = taskTitle
            setTextColor(onSurface)
        }

        // 副标题
        view.findViewById<TextView>(R.id.subtitle)?.apply {
            text = "${getTypeLabel(taskType)} · ${if (isDeadline) "即将截止" else "即将开始"}"
            setTextColor(onSurfaceVariant)
        }

        // 时间徽章
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

        // 我知道了 按钮
        view.findViewById<TextView>(R.id.dismissBtn)?.apply {
            setTextColor(onSurface)
            setOnClickListener { dismiss() }
        }

        // 立即完成 按钮
        view.findViewById<TextView>(R.id.actionBtn)?.apply {
            (background?.mutate() as? GradientDrawable)?.setColor(primaryColor)
            setOnClickListener { openApp(taskId) }
        }

        return view
    }

    private fun setupBlurEffect(view: View) {
        val blurLayer = view.findViewById<View>(R.id.blurLayer)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 使用 RenderEffect 实现毛玻璃效果
            try {
                val renderEffectClass = Class.forName("android.view.RenderEffect")
                val createBlurEffectMethod = renderEffectClass.getMethod(
                    "createBlurEffect", 
                    Float::class.java, 
                    Float::class.java, 
                    android.graphics.Shader.TileMode::class.java, 
                    android.graphics.Shader.TileMode::class.java
                )
                
                val tileModeClamp = android.graphics.Shader.TileMode::class.java.getField("CLAMP").get(null)
                
                val blurEffect = createBlurEffectMethod.invoke(
                    null, 
                    12f, // 模糊半径
                    12f, // 模糊半径
                    tileModeClamp, 
                    tileModeClamp
                )
                
                val setRenderEffectMethod = View::class.java.getMethod("setRenderEffect", renderEffectClass)
                setRenderEffectMethod.invoke(blurLayer, blurEffect)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply blur effect: ${e.message}")
                // 降级到半透明遮罩
                blurLayer.setBackgroundColor(0x4D000000) // 30% 不透明度的黑色
            }
        } else {
            // 低版本 Android 使用半透明遮罩
            blurLayer.setBackgroundColor(0x4D000000) // 30% 不透明度的黑色
        }
    }

    private fun startPulseAnimation(view: View, color: Int) {
        // 呼吸动画
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
        // 播放闹钟铃声（根据设置）
        if (soundEnabled) {
            playAlarmSound()
        }
        // 开始震动（根据设置）
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
            Log.d(TAG, "Alarm sound started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alarm: ${e.message}")
        }
    }

    private fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 短暂高频震动模式: 100ms震动, 100ms停, 100ms震动, 100ms停, 100ms震动
                val pattern = longArrayOf(0, 100, 100, 100, 100, 100)
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 100, 100, 100, 100, 100), -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed: ${e.message}")
        }
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
        cleanup()
        stopSelf()
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        
        pulseAnimator?.cancel()
        pulseAnimator = null
        
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (e: Exception) { }
        }
        mediaPlayer = null

        floatingView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) { }
        }
        floatingView = null
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

    // 主色：截止比开始更显眼
    private fun getPrimaryColor(type: TaskType, isDeadline: Boolean) = when (type) {
        TaskType.URGENT -> if (isDeadline) 0xFFB3261E.toInt() else 0xFFDC362E.toInt()
        TaskType.WORK -> if (isDeadline) 0xFF0B57D0.toInt() else 0xFF1A73E8.toInt()
        TaskType.STUDY -> if (isDeadline) 0xFF7C3AED.toInt() else 0xFF9333EA.toInt()
        TaskType.LIFE -> if (isDeadline) 0xFF047857.toInt() else 0xFF059669.toInt()
    }

    // 容器背景色
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