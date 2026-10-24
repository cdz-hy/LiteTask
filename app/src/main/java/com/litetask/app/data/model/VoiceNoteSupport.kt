package com.litetask.app.data.model

import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 语音备忘录（VOICE_NOTE）纯逻辑支持工具（零 Android 依赖，可完整 JVM 单测）。
 */
object VoiceNoteSupport {

    const val WAVEFORM_BAR_COUNT = 36
    const val MAX_RECORDING_DURATION_MS = 180_000L
    val PLAYBACK_SPEEDS = listOf(0.75f, 1.0f, 1.25f, 1.5f)

    /**
     * 将录制过程采样到的原始振幅序列（0..32767）重采样并归一化到 8..100 的固定柱高列表，
     * 保证静音片段也保留清晰可见的基线。
     */
    fun normalizeWaveformPeaks(
        rawAmplitudes: List<Int>,
        targetBars: Int = WAVEFORM_BAR_COUNT
    ): List<Int> {
        if (targetBars <= 0) return emptyList()
        if (rawAmplitudes.isEmpty()) return List(targetBars) { 12 }

        val maxRaw = rawAmplitudes.maxOrNull()?.coerceAtLeast(1500) ?: 1500
        val normalized = rawAmplitudes.map { amp ->
            val ratio = (amp.coerceAtLeast(0).toFloat() / maxRaw.toFloat()).coerceIn(0f, 1f)
            (10f + ratio * 90f).roundToInt().coerceIn(8, 100)
        }

        if (normalized.size == targetBars) return normalized

        if (normalized.size < targetBars) {
            return List(targetBars) { idx ->
                val mapped = idx.toFloat() * (normalized.size - 1).coerceAtLeast(0) / (targetBars - 1).coerceAtLeast(1)
                val lo = mapped.toInt().coerceIn(0, normalized.lastIndex)
                val hi = (lo + 1).coerceAtMost(normalized.lastIndex)
                val frac = mapped - lo
                (normalized[lo] * (1f - frac) + normalized[hi] * frac).roundToInt().coerceIn(8, 100)
            }
        }

        return List(targetBars) { bucket ->
            val start = (bucket * normalized.size) / targetBars
            val end = (((bucket + 1) * normalized.size) / targetBars).coerceAtLeast(start + 1).coerceAtMost(normalized.size)
            var peak = 0
            for (i in start until end) {
                if (normalized[i] > peak) peak = normalized[i]
            }
            peak.coerceIn(8, 100)
        }
    }

    fun formatDurationClock(durationMs: Long): String {
        val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    fun formatSpeedLabel(speed: Float): String {
        val rounded = (speed * 100f).roundToInt()
        return when (rounded) {
            75 -> "0.75x"
            100 -> "1.0x"
            125 -> "1.25x"
            150 -> "1.5x"
            else -> String.format(Locale.US, "%.2fx", speed).trimEnd('0').trimEnd('.') + "x"
        }
    }

    fun nextPlaybackSpeed(currentSpeed: Float): Float {
        val currentIdx = PLAYBACK_SPEEDS.indexOfFirst { kotlin.math.abs(it - currentSpeed) < 0.05f }
        return PLAYBACK_SPEEDS[(currentIdx + 1) % PLAYBACK_SPEEDS.size]
    }

    fun extractVoiceFileName(data: VoiceNoteData): String? {
        data.filePath.trim().takeIf { it.isNotEmpty() }?.let { path ->
            val name = File(path.replace('\\', '/')).name
            if (name.isNotBlank()) return name
        }
        val rawUri = data.fileUri?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return rawUri.substringBefore('?').substringAfterLast('/').takeIf { it.isNotBlank() }
    }
}
