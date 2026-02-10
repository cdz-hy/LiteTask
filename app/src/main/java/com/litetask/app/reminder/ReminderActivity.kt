package com.litetask.app.reminder

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.litetask.app.MainActivity
import com.litetask.app.R
import com.litetask.app.data.model.TaskType

/**
 * 强提醒活动页面（闹钟级提醒）
 * 
 * 专门用于在息屏、锁屏状态下强制弹出显示
 * 使用 Activity 替代 Service 悬浮窗，以获得更好的锁屏展现兼容性
 */
class ReminderActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ReminderActivity"
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_TASK_TITLE = "task_title"
        private const val EXTRA_REMINDER_TEXT = "reminder_text"
        private const val EXTRA_TASK_TYPE = "task_type"
        private const val EXTRA_IS_DEADLINE = "is_deadline"

        fun start(
            context: Context,
            taskId: Long,
            taskTitle: String,
            reminderText: String,
            taskType: TaskType,
            isDeadline: Boolean
        ) {
            val intent = Intent(context, ReminderActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TASK_TITLE, taskTitle)
                putExtra(EXTRA_REMINDER_TEXT, reminderText)
                putExtra(EXTRA_TASK_TYPE, taskType.name)
                putExtra(EXTRA_IS_DEADLINE, isDeadline)
            }
            context.startActivity(intent)
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var pulseAnimator: ObjectAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    
    private var taskId: Long = -1
    private var taskTitle: String = ""
    private var reminderText: String = ""
    private var taskType: TaskType = TaskType.WORK
    private var isDeadline: Boolean = false

    private var soundEnabled = true
    private var vibrationEnabled = true
    private val PREFS_NAME = "litetask_prefs"
    private val KEY_SOUND_ENABLED = "reminder_sound_enabled"
    private val KEY_VIBRATION_ENABLED = "reminder_vibration_enabled"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 读取用户配置
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        vibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
        
        // 关键逻辑：配置锁屏显示
        setupLockScreenShow()
        
        // 解析数据
        parseIntent()
        
        // 设置布局
        setContentView(R.layout.floating_reminder)
        
        // 初始化 UI
        initUI()
        
        // 开始生效（响铃、震动）
        startAlertEffects()
        
        Log.i(TAG, "★ ReminderActivity onCreate: $taskTitle (sound=$soundEnabled, vib=$vibrationEnabled)")
    }

    private fun setupLockScreenShow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        // 设置窗口为全屏、透明背景（如果主题没设好的话保护一下）
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        window.statusBarColor = Color.TRANSPARENT
    }

    private fun parseIntent() {
        taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "任务提醒"
        reminderText = intent.getStringExtra(EXTRA_REMINDER_TEXT) ?: ""
        val typeStr = intent.getStringExtra(EXTRA_TASK_TYPE) ?: "WORK"
        taskType = try { TaskType.valueOf(typeStr) } catch (e: Exception) { TaskType.WORK }
        isDeadline = intent.getBooleanExtra(EXTRA_IS_DEADLINE, false)
    }

    private fun initUI() {
        val isDark = isDarkMode()
        val primaryColor = getPrimaryColor(taskType, isDeadline)
        val containerColor = getContainerColor(taskType, isDeadline, isDark)
        val surfaceColor = if (isDark) 0xFF1C1B1F.toInt() else 0xFFFFFFFF.toInt()
        val onSurface = if (isDark) 0xFFE6E1E5.toInt() else 0xFF1F1F1F.toInt()
        val onSurfaceVariant = if (isDark) 0xFFCAC4D0.toInt() else 0xFF757575.toInt()

        // 模糊层处理
        setupBlurEffect(findViewById(R.id.blurLayer))

        // 卡片基础颜色
        findViewById<LinearLayout>(R.id.cardContainer)?.let {
            (it.background?.mutate() as? GradientDrawable)?.setColor(surfaceColor)
        }

        findViewById<View>(R.id.accentBar)?.let {
            (it.background?.mutate() as? GradientDrawable)?.setColor(primaryColor)
        }

        findViewById<FrameLayout>(R.id.iconBox)?.let {
            (it.background?.mutate() as? GradientDrawable)?.setColor(containerColor)
            startPulseAnimation(it)
        }

        findViewById<ImageView>(R.id.icon)?.setColorFilter(primaryColor)

        findViewById<TextView>(R.id.title)?.apply {
            text = taskTitle
            setTextColor(onSurface)
        }

        findViewById<TextView>(R.id.subtitle)?.apply {
            text = "${getTypeLabel(taskType)} · ${if (isDeadline) "即将截止" else "即将开始"}"
            setTextColor(onSurfaceVariant)
        }

        findViewById<LinearLayout>(R.id.timeBadge)?.let {
            (it.background?.mutate() as? GradientDrawable)?.setColor(
                if (isDark) 0xFF2D2D2D.toInt() else 0xFFF5F5F5.toInt()
            )
        }
        findViewById<ImageView>(R.id.timeIcon)?.setColorFilter(onSurfaceVariant)
        findViewById<TextView>(R.id.timeText)?.apply {
            text = reminderText
            setTextColor(primaryColor)
        }

        findViewById<TextView>(R.id.dismissBtn)?.apply {
            setTextColor(onSurface)
            setOnClickListener { finish() }
        }

        findViewById<TextView>(R.id.actionBtn)?.apply {
            (background?.mutate() as? GradientDrawable)?.setColor(primaryColor)
            setOnClickListener { openApp() }
        }
        
        // 如果是截止提醒，对特定细节进行“紧急化”处理
        if (isDeadline) {
            findViewById<ImageView>(R.id.icon)?.setImageResource(R.drawable.ic_warning) // 截止时使用警告图标
            findViewById<TextView>(R.id.timeText)?.setTextColor(0xFFB3261E.toInt()) // 截止时时间文字显着变红
        } else {
            findViewById<ImageView>(R.id.icon)?.setImageResource(R.drawable.ic_alarm)
        }
    }

    private fun setupBlurEffect(blurLayer: View) {
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

    private fun startPulseAnimation(view: View) {
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
        if (vibrationEnabled) startVibrating()
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
                setDataSource(this@ReminderActivity, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alarm: ${e.message}")
        }
    }

    private fun startVibrating() {
        // 创建震动 Runnable 并循环执行
        val vibrateRunnable = object : Runnable {
            override fun run() {
                vibrateOnce()
                handler.postDelayed(this, 3000)
            }
        }
        handler.post(vibrateRunnable)
    }

    private fun vibrateOnce() {
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
        } catch (e: Exception) { }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationHelper.EXTRA_TASK_ID, taskId)
            putExtra(NotificationHelper.EXTRA_FROM_NOTIFICATION, true)
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        pulseAnimator?.cancel()
        mediaPlayer?.let {
            try { it.stop(); it.release() } catch (e: Exception) { }
        }
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

    /**
     * 获取任务类型主色 (符合 Theme.kt 定义)
     */
    private fun getPrimaryColor(type: TaskType, isDeadline: Boolean): Int {
        val isDark = isDarkMode()
        return if (isDark) {
            when (type) {
                TaskType.WORK -> 0xFFA8C7FA.toInt()
                TaskType.LIFE -> 0xFF81C995.toInt()
                TaskType.STUDY -> 0xFFCFBCFF.toInt()
                TaskType.URGENT -> 0xFFF2B8B5.toInt()
            }
        } else {
            // 亮色模式
            if (isDeadline) 0xFFB3261E.toInt() // 截止提醒在亮色下强制使用更显著的警示红
            else when (type) {
                TaskType.WORK -> 0xFF0B57D0.toInt()
                TaskType.LIFE -> 0xFF146C2E.toInt()
                TaskType.STUDY -> 0xFF65558F.toInt()
                TaskType.URGENT -> 0xFFB3261E.toInt()
            }
        }
    }

    /**
     * 获取图标容器背景颜色 (符合 Theme.kt 定义的 Surface 色)
     */
    private fun getContainerColor(type: TaskType, isDeadline: Boolean, isDark: Boolean): Int {
        return if (isDark) {
            // 暗色模式：使用较低明度的颜色容器
            if (isDeadline) 0xFF421F1F.toInt() // 截止提醒使用深暗红
            else when (type) {
                TaskType.WORK -> 0xFF1E3A5F.toInt()
                TaskType.LIFE -> 0xFF1E3B2F.toInt()
                TaskType.STUDY -> 0xFF2D2640.toInt()
                TaskType.URGENT -> 0xFF421F1F.toInt()
            }
        } else {
            // 亮色模式：使用浅色容器
            if (isDeadline) 0xFFFCE8E6.toInt() // 截止提醒使用浅警示红
            else when (type) {
                TaskType.WORK -> 0xFFEFF6FF.toInt()
                TaskType.LIFE -> 0xFFECFDF5.toInt()
                TaskType.STUDY -> 0xFFF5F3FF.toInt()
                TaskType.URGENT -> 0xFFFCE8E6.toInt()
            }
        }
    }
}
