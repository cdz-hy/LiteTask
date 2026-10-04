package com.litetask.app.util

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.litetask.app.data.local.FocusSessionDao
import com.litetask.app.data.local.PreferenceManager
import com.litetask.app.data.local.TaskDao
import com.litetask.app.data.model.FocusSessionEntity
import com.litetask.app.data.model.Task
import com.litetask.app.reminder.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class FocusMode {
    POMODORO,
    STOPWATCH
}

enum class FocusStatus {
    IDLE,
    RUNNING,
    PAUSED,
    FINISHED
}

data class FocusTimerState(
    val status: FocusStatus = FocusStatus.IDLE,
    val mode: FocusMode = FocusMode.POMODORO,
    val taskId: Long? = null,
    val taskTitle: String = "",
    val plannedSeconds: Long = 25 * 60L,
    val elapsedSeconds: Long = 0L,
    val startedAtWallMillis: Long = 0L
) {
    val isActive: Boolean
        get() = status == FocusStatus.RUNNING || status == FocusStatus.PAUSED

    val remainingSeconds: Long
        get() = (plannedSeconds - elapsedSeconds).coerceAtLeast(0L)

    val progress: Float
        get() = when {
            mode == FocusMode.POMODORO && plannedSeconds > 0L ->
                (elapsedSeconds.toFloat() / plannedSeconds.toFloat()).coerceIn(0f, 1f)
            else -> ((elapsedSeconds % 60L) / 60f)
        }
}

/**
 * 全局专注计时管理器，支持番茄钟（倒计时）与自由正向计时。
 * 基于 SystemClock.elapsedRealtime() 计算已用时长，休眠与切后台不漂移。
 */
@Singleton
class FocusTimerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val focusSessionDao: FocusSessionDao,
    private val taskDao: TaskDao,
    private val preferenceManager: PreferenceManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _timerState = MutableStateFlow(FocusTimerState())
    val timerState: StateFlow<FocusTimerState> = _timerState.asStateFlow()

    private var tickerJob: Job? = null
    private var accumulatedMillis: Long = 0L
    private var segmentStartElapsedMillis: Long = 0L

    /**
     * 启动新的专注计时。若已有运行中/暂停会话，先保存已完成秒数再开启新会话。
     */
    fun start(
        task: Task?,
        mode: FocusMode = FocusMode.POMODORO,
        plannedMinutes: Int = 25
    ) {
        if (_timerState.value.isActive) {
            persistAndClearInternal(completed = false)
        }
        NotificationHelper.cancelFocusNotification(context)

        val plannedSeconds = if (mode == FocusMode.POMODORO) {
            (plannedMinutes.coerceIn(1, 180)) * 60L
        } else {
            0L
        }

        accumulatedMillis = 0L
        segmentStartElapsedMillis = SystemClock.elapsedRealtime()
        _timerState.value = FocusTimerState(
            status = FocusStatus.RUNNING,
            mode = mode,
            taskId = task?.id,
            taskTitle = task?.title ?: "自由专注",
            plannedSeconds = plannedSeconds,
            elapsedSeconds = 0L,
            startedAtWallMillis = System.currentTimeMillis()
        )
        startTicker()
        updateOngoingNotification()
    }

    fun pause() {
        val current = _timerState.value
        if (current.status != FocusStatus.RUNNING) return
        accumulatedMillis = computeCurrentElapsedMillis()
        stopTicker()
        _timerState.value = current.copy(
            status = FocusStatus.PAUSED,
            elapsedSeconds = accumulatedMillis / 1000L
        )
        updateOngoingNotification()
    }

    fun resume() {
        val current = _timerState.value
        if (current.status != FocusStatus.PAUSED) return
        segmentStartElapsedMillis = SystemClock.elapsedRealtime()
        _timerState.value = current.copy(status = FocusStatus.RUNNING)
        startTicker()
        updateOngoingNotification()
    }

    /**
     * 提前结束或停止正向计时并保存有效专注记录
     */
    fun finishAndSave() {
        val current = _timerState.value
        if (current.status == FocusStatus.IDLE) return
        if (current.status == FocusStatus.FINISHED) {
            resetToIdle()
            return
        }
        persistAndClearInternal(completed = current.mode == FocusMode.STOPWATCH)
    }

    /**
     * 放弃当前未保存的计时会话
     */
    fun abandon() {
        stopTicker()
        accumulatedMillis = 0L
        segmentStartElapsedMillis = 0L
        NotificationHelper.cancelFocusNotification(context)
        _timerState.value = FocusTimerState()
    }

    fun resetToIdle() {
        stopTicker()
        accumulatedMillis = 0L
        segmentStartElapsedMillis = 0L
        NotificationHelper.cancelFocusNotification(context)
        _timerState.value = FocusTimerState()
    }

    /**
     * 以当前关联的任务信息再开启一轮专注（如番茄钟完成后点击“再来一轮”）
     */
    fun restartCurrent(mode: FocusMode = FocusMode.POMODORO, plannedMinutes: Int = 25) {
        val prev = _timerState.value
        val taskId = prev.taskId
        val taskTitle = prev.taskTitle
        NotificationHelper.cancelFocusNotification(context)
        val plannedSeconds = if (mode == FocusMode.POMODORO) {
            (plannedMinutes.coerceIn(1, 180)) * 60L
        } else {
            0L
        }
        accumulatedMillis = 0L
        segmentStartElapsedMillis = SystemClock.elapsedRealtime()
        _timerState.value = FocusTimerState(
            status = FocusStatus.RUNNING,
            mode = mode,
            taskId = taskId,
            taskTitle = taskTitle,
            plannedSeconds = plannedSeconds,
            elapsedSeconds = 0L,
            startedAtWallMillis = System.currentTimeMillis()
        )
        startTicker()
        updateOngoingNotification()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val current = _timerState.value
                if (current.status != FocusStatus.RUNNING) break
                val elapsedSecs = computeCurrentElapsedMillis() / 1000L
                if (current.mode == FocusMode.POMODORO && elapsedSecs >= current.plannedSeconds) {
                    onPomodoroCompleted(current)
                    break
                } else if (elapsedSecs != current.elapsedSeconds) {
                    _timerState.value = current.copy(elapsedSeconds = elapsedSecs)
                }
                delay(500L)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun computeCurrentElapsedMillis(): Long {
        return if (_timerState.value.status == FocusStatus.RUNNING) {
            accumulatedMillis + (SystemClock.elapsedRealtime() - segmentStartElapsedMillis).coerceAtLeast(0L)
        } else {
            accumulatedMillis
        }
    }

    private fun onPomodoroCompleted(snapshot: FocusTimerState) {
        stopTicker()
        val actualSeconds = snapshot.plannedSeconds
        accumulatedMillis = actualSeconds * 1000L
        _timerState.value = snapshot.copy(
            status = FocusStatus.FINISHED,
            elapsedSeconds = actualSeconds
        )
        NotificationHelper.cancelFocusNotification(context)
        NotificationHelper.showFocusCompletedNotification(context, snapshot.taskTitle, actualSeconds)
        triggerCompletionFeedback()
        persistSessionAsync(snapshot, actualSeconds, completed = true)
    }

    private fun persistAndClearInternal(completed: Boolean) {
        val snapshot = _timerState.value
        val actualSeconds = (computeCurrentElapsedMillis() / 1000L).coerceAtLeast(0L)
        stopTicker()
        accumulatedMillis = 0L
        segmentStartElapsedMillis = 0L
        NotificationHelper.cancelFocusNotification(context)
        _timerState.value = FocusTimerState()
        if (actualSeconds >= 1L) {
            persistSessionAsync(snapshot, actualSeconds, completed)
        }
    }

    private fun persistSessionAsync(
        snapshot: FocusTimerState,
        actualSeconds: Long,
        completed: Boolean
    ) {
        val endedAt = System.currentTimeMillis()
        val startedAt = if (snapshot.startedAtWallMillis > 0L) snapshot.startedAtWallMillis else (endedAt - actualSeconds * 1000L)
        scope.launch(Dispatchers.IO) {
            focusSessionDao.insertSession(
                FocusSessionEntity(
                    taskId = snapshot.taskId,
                    taskTitle = snapshot.taskTitle,
                    mode = snapshot.mode.name,
                    plannedSeconds = snapshot.plannedSeconds,
                    actualSeconds = actualSeconds,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    completed = completed
                )
            )
            snapshot.taskId?.let { id ->
                taskDao.addFocusSeconds(id, actualSeconds)
            }
        }
    }

    private fun updateOngoingNotification() {
        val state = _timerState.value
        if (!state.isActive) return
        NotificationHelper.showFocusOngoingNotification(
            context = context,
            taskTitle = state.taskTitle,
            isPomodoro = state.mode == FocusMode.POMODORO,
            isRunning = state.status == FocusStatus.RUNNING,
            elapsedSeconds = state.elapsedSeconds,
            remainingSeconds = state.remainingSeconds
        )
    }

    private fun triggerCompletionFeedback() {
        try {
            if (preferenceManager.isReminderSoundEnabled()) {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                RingtoneManager.getRingtone(context, uri)?.play()
            }
        } catch (_: Exception) {
        }
        try {
            if (preferenceManager.isReminderVibrationEnabled()) {
                val pattern = longArrayOf(0, 300, 150, 300)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(VibratorManager::class.java)
                    vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Vibrator::class.java)
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                }
            }
        } catch (_: Exception) {
        }
    }
}
