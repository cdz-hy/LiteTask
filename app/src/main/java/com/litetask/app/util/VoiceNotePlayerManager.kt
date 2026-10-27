package com.litetask.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import com.litetask.app.data.model.VoiceNoteData
import com.litetask.app.data.model.VoiceNoteSupport
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
import java.io.File

data class VoiceNotePlaybackState(
    val activeKey: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f
)

/**
 * 全局唯一的语音备忘录播放器管理器，保证全应用同一时刻仅有一条语音在播放。
 */
object VoiceNotePlayerManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var tickerJob: Job? = null

    private val _playbackState = MutableStateFlow(VoiceNotePlaybackState())
    val playbackState: StateFlow<VoiceNotePlaybackState> = _playbackState.asStateFlow()

    fun togglePlayPause(
        context: Context,
        key: String,
        data: VoiceNoteData
    ) {
        val current = _playbackState.value
        if (current.activeKey == key && mediaPlayer != null) {
            val mp = mediaPlayer ?: return
            if (current.isPlaying) {
                runCatching { mp.pause() }
                tickerJob?.cancel()
                tickerJob = null
                _playbackState.value = current.copy(
                    isPlaying = false,
                    positionMs = runCatching { mp.currentPosition.toLong() }.getOrDefault(current.positionMs)
                )
            } else {
                runCatching {
                    mp.start()
                    applySpeedSafe(mp, current.speed)
                }
                _playbackState.value = current.copy(isPlaying = true)
                startTicker()
            }
            return
        }

        stop()

        if (!AttachmentStorageHelper.isVoiceNoteAccessible(context, data)) {
            Toast.makeText(context, "语音文件不存在或不可读", Toast.LENGTH_SHORT).show()
            return
        }

        val speed = current.speed
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            val localFile = data.filePath.trim().takeIf { it.isNotEmpty() }?.let { File(it) }
                ?.takeIf { it.exists() && it.canRead() }
            if (localFile != null) {
                mp.setDataSource(localFile.absolutePath)
            } else {
                val uri = Uri.parse(data.fileUri ?: "")
                mp.setDataSource(context, uri)
            }

            mp.setOnPreparedListener { player ->
                val totalDuration = player.duration.takeIf { it > 0 }?.toLong() ?: data.durationMs
                player.start()
                applySpeedSafe(player, speed)
                _playbackState.value = VoiceNotePlaybackState(
                    activeKey = key,
                    isPlaying = true,
                    positionMs = 0L,
                    durationMs = totalDuration,
                    speed = speed
                )
                startTicker()
            }
            mp.setOnCompletionListener {
                tickerJob?.cancel()
                tickerJob = null
                val dur = _playbackState.value.durationMs
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    positionMs = 0L,
                    durationMs = dur
                )
                runCatching { mp.seekTo(0) }
            }
            mp.setOnErrorListener { _, _, _ ->
                Toast.makeText(context, "语音播放失败", Toast.LENGTH_SHORT).show()
                stop()
                true
            }
            mediaPlayer = mp
            mp.prepareAsync()
        } catch (_: Exception) {
            runCatching { mp.release() }
            mediaPlayer = null
            Toast.makeText(context, "无法打开语音文件", Toast.LENGTH_SHORT).show()
        }
    }

    fun seekToFraction(key: String, fraction: Float) {
        val current = _playbackState.value
        if (current.activeKey != key) return
        val mp = mediaPlayer ?: return
        val duration = current.durationMs.coerceAtLeast(1L)
        val targetMs = (duration * fraction.coerceIn(0f, 1f)).toLong().coerceIn(0L, duration)
        runCatching { mp.seekTo(targetMs.toInt()) }
        _playbackState.value = current.copy(positionMs = targetMs)
    }

    fun cycleSpeed() {
        val nextSpeed = VoiceNoteSupport.nextPlaybackSpeed(_playbackState.value.speed)
        _playbackState.value = _playbackState.value.copy(speed = nextSpeed)
        val mp = mediaPlayer ?: return
        // 仅在播放中实时更新 PlaybackParams，避免部分机型在暂停态 setPlaybackParams 自动恢复播放
        if (_playbackState.value.isPlaying) {
            applySpeedSafe(mp, nextSpeed)
        }
    }

    fun stop() {
        tickerJob?.cancel()
        tickerJob = null
        mediaPlayer?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.reset() }
            runCatching { mp.release() }
        }
        mediaPlayer = null
        _playbackState.value = VoiceNotePlaybackState(speed = _playbackState.value.speed)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val mp = mediaPlayer ?: break
                val state = _playbackState.value
                if (!state.isPlaying) break
                val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(state.positionMs)
                _playbackState.value = state.copy(positionMs = pos.coerceAtLeast(0L))
                delay(80L)
            }
        }
    }

    private fun applySpeedSafe(mp: MediaPlayer, speed: Float) {
        runCatching {
            val params = mp.playbackParams
            params.speed = speed
            mp.playbackParams = params
        }
    }
}
