package com.litetask.app.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.litetask.app.data.model.VoiceNoteSupport
import java.io.File

/**
 * 基于 MediaRecorder 的轻量级 AAC (.m4a) 语音备忘录录制器。
 * 16kHz / 单声道 / 32kbps，体积约 ~240KB/分钟，支持实时振幅采样。
 */
class VoiceNoteRecorder(private val context: Context) {

    data class RecordingOutput(
        val file: File,
        val durationMs: Long,
        val waveformPeaks: List<Int>
    )

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L
    private val sampledAmplitudes = ArrayList<Int>(256)
    private var isRecording = false

    fun start(): Boolean {
        if (isRecording) return false
        sampledAmplitudes.clear()
        val targetFile = AttachmentStorageHelper.createVoiceNoteFile(context)
        return try {
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16_000)
                setAudioChannels(1)
                setAudioEncodingBitRate(32_000)
                setOutputFile(targetFile.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            outputFile = targetFile
            startedAtMs = System.currentTimeMillis()
            isRecording = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voice note recording", e)
            runCatching { targetFile.delete() }
            releaseRecorder()
            false
        }
    }

    fun sampleAmplitude(): Int {
        if (!isRecording) return 0
        val raw = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0).coerceAtLeast(0)
        sampledAmplitudes.add(raw)
        return raw
    }

    fun currentDurationMs(): Long =
        if (isRecording) (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L) else 0L

    fun stopAndSave(): RecordingOutput? {
        if (!isRecording) return null
        isRecording = false
        val file = outputFile
        val duration = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
        val stoppedCleanly = runCatching {
            recorder?.stop()
            true
        }.getOrDefault(false)
        releaseRecorder()

        if (!stoppedCleanly || file == null || !file.exists() || file.length() <= 0L || duration < 400L) {
            runCatching { file?.delete() }
            outputFile = null
            return null
        }

        val peaks = VoiceNoteSupport.normalizeWaveformPeaks(sampledAmplitudes)
        outputFile = null
        return RecordingOutput(
            file = file,
            durationMs = duration,
            waveformPeaks = peaks
        )
    }

    fun cancel() {
        isRecording = false
        runCatching { recorder?.stop() }
        releaseRecorder()
        outputFile?.let { f -> runCatching { f.delete() } }
        outputFile = null
        sampledAmplitudes.clear()
    }

    private fun releaseRecorder() {
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
    }

    companion object {
        private const val TAG = "VoiceNoteRecorder"
    }
}
