package com.litetask.app.reminder

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
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
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.litetask.app.MainActivity
import com.litetask.app.R
import com.litetask.app.data.model.TaskType
import com.litetask.app.ui.theme.LiteTaskTheme
import com.litetask.app.ui.theme.LocalExtendedColors

/**
 * 强提醒活动页面（闹钟级提醒）
 * 
 * 使用 Jetpack Compose 重构，支持 MD3 动态取色和深色模式
 */
class ReminderActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ReminderActivity"
        private const val AUTO_DISMISS_DELAY = 60_000L // 1分钟自动关闭
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_TASK_TITLE = "task_title"
        private const val EXTRA_REMINDER_TEXT = "reminder_text"
        private const val EXTRA_TASK_TYPE = "task_type"
        private const val EXTRA_IS_DEADLINE = "is_deadline"
        private const val EXTRA_CATEGORY_NAME = "category_name"
        private const val EXTRA_CATEGORY_COLOR = "category_color"

        fun start(
            context: Context,
            taskId: Long,
            taskTitle: String,
            reminderText: String,
            taskType: TaskType,
            isDeadline: Boolean,
            categoryName: String? = null,
            categoryColor: String? = null
        ) {
            val intent = Intent(context, ReminderActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TASK_TITLE, taskTitle)
                putExtra(EXTRA_REMINDER_TEXT, reminderText)
                putExtra(EXTRA_TASK_TYPE, taskType.name)
                putExtra(EXTRA_IS_DEADLINE, isDeadline)
                putExtra(EXTRA_CATEGORY_NAME, categoryName)
                putExtra(EXTRA_CATEGORY_COLOR, categoryColor)
            }
            context.startActivity(intent)
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    
    private var taskId: Long = -1
    private var taskTitle: String = ""
    private var reminderText: String = ""
    private var taskType: TaskType = TaskType.WORK
    private var isDeadline: Boolean = false
    private var categoryName: String? = null
    private var categoryColor: String? = null
    
    // 标记用户是否已处理提醒
    private var userHandled = false

    private var soundEnabled = true
    private var vibrationEnabled = true
    private val PREFS_NAME = "litetask_prefs"
    private val KEY_SOUND_ENABLED = "reminder_sound_enabled"
    private val KEY_VIBRATION_ENABLED = "reminder_vibration_enabled"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 沉浸式状态栏
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 读取用户配置
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        vibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
        
        // 关键逻辑：配置锁屏显示
        setupLockScreenShow()
        
        // 解析数据
        parseIntent()
        
        // 开始生效（响铃、震动）
        startAlertEffects()
        
        // 设置 1 分钟后自动关闭
        handler.postDelayed({
            dismissWithTimeout()
        }, AUTO_DISMISS_DELAY)
        
        Log.i(TAG, "ReminderActivity onCreate: $taskTitle (sound=$soundEnabled, vib=$vibrationEnabled)")

        setContent {
            LiteTaskTheme {
                // 全屏半透明背景容器
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)), // 模拟 dimAmount
                    contentAlignment = Alignment.Center
                ) {
                    ReminderCard(
                        taskTitle = taskTitle,
                        reminderText = reminderText,
                        taskType = taskType,
                        isDeadline = isDeadline,
                        categoryName = categoryName,
                        categoryColor = categoryColor,
                        onDismiss = {
                            userHandled = true
                            finish()
                        },
                        onAction = {
                            userHandled = true
                            openApp()
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun ReminderCard(
        taskTitle: String,
        reminderText: String,
        taskType: TaskType,
        isDeadline: Boolean,
        categoryName: String?,
        categoryColor: String?,
        onDismiss: () -> Unit,
        onAction: () -> Unit
    ) {
        val extendedColors = LocalExtendedColors.current
        
        // 根据任务类型和分类动态获取颜色
        val (primaryColor, containerColor) = remember(isDeadline, taskType, categoryColor) {
            if (categoryColor != null) {
                try {
                    val color = com.litetask.app.ui.util.ColorUtils.parseColor(categoryColor)
                    // 如果是截止日期且没有明确颜色，可以使用红色装饰，
                    // 但这里优先尊重分类的主色调
                    val surface = com.litetask.app.ui.util.ColorUtils.getSurfaceColor(color)
                    color to surface
                } catch (e: Exception) {
                    extendedColors.workTask to extendedColors.workTaskSurface
                }
            } else if (isDeadline) {
                extendedColors.deadlineUrgent to extendedColors.deadlineUrgentSurface
            } else {
                when (taskType) {
                    TaskType.WORK -> extendedColors.workTask to extendedColors.workTaskSurface
                    TaskType.LIFE -> extendedColors.lifeTask to extendedColors.lifeTaskSurface
                    TaskType.STUDY -> extendedColors.studyTask to extendedColors.studyTaskSurface
                    else -> extendedColors.urgentTask to extendedColors.urgentTaskSurface
                }
            }
        }
        
        // 截止日期的警告特有颜色（用于图标和警告文字）
        val warningColor = if (isDeadline) extendedColors.deadlineUrgent else primaryColor

        // 呼吸动画
        val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "IconScale"
        )

        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                // 顶部色条
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(primaryColor)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 图标区域
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(containerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(
                            id = if (isDeadline) R.drawable.ic_warning else R.drawable.ic_alarm
                        ),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(warningColor),
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 标题和类型
                Text(
                    text = taskTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${categoryName ?: getTypeLabel(taskType)} · ${if (isDeadline) "即将截止" else "即将开始"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 时间胶囊
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    ) {
                        // 使用 compose icon
                        Icon(
                            painter = painterResource(R.drawable.ic_clock),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = reminderText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = warningColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // 按钮组
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "我知道了",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = onAction,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 0.dp
                        )
                    ) {
                        Text(
                            text = "去完成",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
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
        
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
    }

    private fun parseIntent() {
        taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "任务提醒"
        reminderText = intent.getStringExtra(EXTRA_REMINDER_TEXT) ?: ""
        val typeStr = intent.getStringExtra(EXTRA_TASK_TYPE) ?: "WORK"
        taskType = try { TaskType.valueOf(typeStr) } catch (e: Exception) { TaskType.WORK }
        isDeadline = intent.getBooleanExtra(EXTRA_IS_DEADLINE, false)
        categoryName = intent.getStringExtra(EXTRA_CATEGORY_NAME)
        categoryColor = intent.getStringExtra(EXTRA_CATEGORY_COLOR)
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
    
    private fun dismissWithTimeout() {
        if (!userHandled && taskId != -1L) {
            Log.i(TAG, "Timeout without user action, sending system notification")
            NotificationHelper.showReminderNotification(
                this,
                taskId,
                taskTitle,
                "$reminderText（未查看）",
                taskType,
                categoryName,
                categoryColor
            )
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.let {
            try { it.stop(); it.release() } catch (e: Exception) { }
        }
    }

    private fun getTypeLabel(type: TaskType) = when (type) {
        TaskType.URGENT -> "紧急任务"
        TaskType.WORK -> "工作任务"
        TaskType.STUDY -> "学习任务"
        TaskType.LIFE -> "生活任务"
    }
}
