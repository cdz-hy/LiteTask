package com.litetask.app.util

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import java.util.Locale
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// 录音状态
enum class RecordingState {
    IDLE,           // 空闲
    RECORDING,      // 录音中
    PLAYING,        // 回放中
    RECOGNIZING,    // 识别中
    DONE            // 完成
}

// 录音回调结果
sealed class VoiceRecordResult {
    object RecordingStarted : VoiceRecordResult()
    object RecordingStopped : VoiceRecordResult()
    object PlaybackStarted : VoiceRecordResult()
    object PlaybackFinished : VoiceRecordResult()
    object RecognitionStarted : VoiceRecordResult()
    data class RecognitionResult(val text: String) : VoiceRecordResult()
    data class Error(val message: String) : VoiceRecordResult()
}

@Singleton
class SpeechRecognizerHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState
    
    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration
    
    private var audioFile: File? = null
    private var recordingStartTime = 0L
    private var durationUpdateRunnable: Runnable? = null
    
    private fun getAudioFile(): File {
        val cacheDir = context.cacheDir
        return File(cacheDir, "voice_recording.m4a")
    }
    
    /**
     * 开始录音
     */
    fun startRecording(): Boolean {
        return try {
            audioFile = getAudioFile()
            audioFile?.delete() // 删除旧文件
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            
            _recordingState.value = RecordingState.RECORDING
            recordingStartTime = System.currentTimeMillis()
            startDurationUpdate()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 停止录音
     */
    fun stopRecording(): File? {
        return try {
            stopDurationUpdate()
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            _recordingState.value = RecordingState.IDLE
            audioFile
        } catch (e: Exception) {
            e.printStackTrace()
            mediaRecorder?.release()
            mediaRecorder = null
            _recordingState.value = RecordingState.IDLE
            null
        }
    }

    
    /**
     * 回放录音
     */
    fun playRecording(onComplete: () -> Unit): Boolean {
        val file = audioFile ?: return false
        if (!file.exists()) return false
        
        return try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    _recordingState.value = RecordingState.IDLE
                    onComplete()
                }
                start()
            }
            _recordingState.value = RecordingState.PLAYING
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 停止回放
     */
    fun stopPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        _recordingState.value = RecordingState.IDLE
    }
    
    /**
     * 使用 SpeechRecognizer 识别录音文件
     * 注意：Android SpeechRecognizer 不支持直接识别文件，需要实时语音
     * 所以我们改用录音时同时进行实时识别
     */
    fun recognizeFromRecording(onResult: (String?) -> Unit) {
        // Android 原生 SpeechRecognizer 不支持文件识别
        // 这里我们使用一个变通方案：播放录音同时进行识别
        // 但这在大多数设备上不可行，因为麦克风会捕获扬声器声音
        
        // 更好的方案是使用在线 API（如 Google Cloud Speech-to-Text）
        // 或者在录音时就进行实时识别
        
        onResult(null)
    }
    
    private fun startDurationUpdate() {
        durationUpdateRunnable = object : Runnable {
            override fun run() {
                val duration = System.currentTimeMillis() - recordingStartTime
                _recordingDuration.value = duration
                mainHandler.postDelayed(this, 100)
            }
        }
        mainHandler.post(durationUpdateRunnable!!)
    }
    
    private fun stopDurationUpdate() {
        durationUpdateRunnable?.let { mainHandler.removeCallbacks(it) }
        durationUpdateRunnable = null
    }
    
    /**
     * 录音并实时识别 - 推荐方案
     * 录音的同时进行语音识别，结束后可以回放录音
     */
    fun startRecordingWithRecognition(): Flow<VoiceRecordResult> = callbackFlow {
        audioFile = getAudioFile()
        audioFile?.delete()
        
        var recognizedText = ""
        
        // 启动录音
        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            
            _recordingState.value = RecordingState.RECORDING
            recordingStartTime = System.currentTimeMillis()
            startDurationUpdate()
            trySend(VoiceRecordResult.RecordingStarted)
        } catch (e: Exception) {
            trySend(VoiceRecordResult.Error("录音启动失败: ${e.message}"))
            close()
            return@callbackFlow
        }
        
        // 同时启动语音识别
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // 减少静默等待时间以提高响应速度
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            // 添加额外参数以提高准确性
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // 启用网络识别以提高准确度
            putExtra(RecognizerIntent.EXTRA_WEB_SEARCH_ONLY, false)
            // 设置识别模式为自由形式，适合任务识别场景
            putExtra("android.speech.extra.PREFER_OFFLINE", false)
        }
        
        var shouldContinue = true
        var retryCount = 0
        val maxRetries = 3
        
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            
            override fun onError(error: Int) {
                // 静默超时或无匹配时重新开始
                if (shouldContinue && (error == SpeechRecognizer.ERROR_NO_MATCH || 
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    mainHandler.postDelayed({
                        if (shouldContinue) {
                            try {
                                recognizer.startListening(intent)
                            } catch (e: Exception) {
                                // 忽略
                            }
                        }
                    }, 200)
                }
            }

            override fun onResults(results: Bundle?) {
                // 重置重试计数
                retryCount = 0
                
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    if (recognizedText.isNotEmpty()) {
                        recognizedText += " "
                    }
                    // 选择置信度最高的结果
                    recognizedText += matches[0]
                    trySend(VoiceRecordResult.RecognitionResult(recognizedText))
                }
                
                // 继续监听，但减少延迟以提高响应速度
                if (shouldContinue) {
                    mainHandler.postDelayed({
                        if (shouldContinue) {
                            try {
                                recognizer.startListening(intent)
                            } catch (e: Exception) {
                                trySend(VoiceRecordResult.Error("语音识别启动失败: ${e.message}"))
                            }
                        }
                    }, 100)  // 减少延迟时间
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val displayText = if (recognizedText.isNotEmpty()) {
                        "$recognizedText ${matches[0]}"
                    } else {
                        matches[0]
                    }
                    trySend(VoiceRecordResult.RecognitionResult(displayText))
                }
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        
        recognizer.setRecognitionListener(listener)
        recognizer.startListening(intent)
        
        awaitClose {
            shouldContinue = false
            stopDurationUpdate()
            
            // 停止录音
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            mediaRecorder = null
            
            // 停止识别
            try {
                recognizer.stopListening()
                recognizer.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            speechRecognizer = null
            
            _recordingState.value = RecordingState.IDLE
        }
    }
    
    /**
     * 获取录音文件用于回放
     */
    fun getRecordedFile(): File? = audioFile
    
    /**
     * 清理资源
     */
    fun release() {
        stopDurationUpdate()
        mediaRecorder?.release()
        mediaRecorder = null
        mediaPlayer?.release()
        mediaPlayer = null
        speechRecognizer?.destroy()
        speechRecognizer = null
        audioFile?.delete()
        audioFile = null
        _recordingState.value = RecordingState.IDLE
    }
}
