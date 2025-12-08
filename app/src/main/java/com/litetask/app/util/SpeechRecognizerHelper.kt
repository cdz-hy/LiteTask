package com.litetask.app.util

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.json.JSONObject
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
    private var speechService: SpeechService? = null
    private var model: Model? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState
    
    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration
    
    private var recordingStartTime = 0L
    private var durationUpdateRunnable: Runnable? = null
    
    // 模型初始化状态
    private var isModelInitialized = false
    
    /**
     * 初始化 Vosk 模型
     * 在后台线程自动调用
     */
    private suspend fun ensureModelInitialized(): Boolean = withContext(Dispatchers.IO) {
        if (isModelInitialized && model != null) {
            return@withContext true
        }
        
        try {
            // 检查模型是否已解压
            if (!VoskModelManager.isModelExists(context)) {
                // 从 assets 解压模型
                val result = VoskModelManager.unpackModelFromAssets(context)
                if (result.isFailure) {
                    return@withContext false
                }
            }
            
            // 加载模型
            val modelDir = VoskModelManager.getModelDir(context)
            model = Model(modelDir.path)
            isModelInitialized = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
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
     * 使用 Vosk 进行录音并实时识别
     * 完全离线，不需要网络连接
     */
    fun startRecordingWithRecognition(): Flow<VoiceRecordResult> = callbackFlow {
        // 确保模型已初始化
        if (!ensureModelInitialized()) {
            trySend(VoiceRecordResult.Error("语音识别模型加载失败，请检查模型文件"))
            close()
            return@callbackFlow
        }
        
        try {
            // 创建识别器（16kHz 采样率）
            val recognizer = Recognizer(model, 16000.0f)
            
            // 创建语音服务
            val service = SpeechService(recognizer, 16000.0f)
            speechService = service
            
            _recordingState.value = RecordingState.RECORDING
            recordingStartTime = System.currentTimeMillis()
            startDurationUpdate()
            trySend(VoiceRecordResult.RecordingStarted)
            
            var fullText = ""
            
            // 设置识别监听器
            service.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    hypothesis?.let {
                        try {
                            val json = JSONObject(it)
                            val partial = json.optString("partial", "")
                            if (partial.isNotEmpty()) {
                                // 显示部分结果（实时反馈）
                                val displayText = if (fullText.isNotEmpty()) {
                                    "$fullText $partial"
                                } else {
                                    partial
                                }
                                trySend(VoiceRecordResult.RecognitionResult(displayText))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                
                override fun onResult(hypothesis: String?) {
                    hypothesis?.let {
                        try {
                            val json = JSONObject(it)
                            val text = json.optString("text", "")
                            if (text.isNotEmpty()) {
                                // 累积完整结果
                                if (fullText.isNotEmpty()) {
                                    fullText += " "
                                }
                                fullText += text
                                trySend(VoiceRecordResult.RecognitionResult(fullText))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                
                override fun onFinalResult(hypothesis: String?) {
                    hypothesis?.let {
                        try {
                            val json = JSONObject(it)
                            val text = json.optString("text", "")
                            if (text.isNotEmpty()) {
                                if (fullText.isNotEmpty()) {
                                    fullText += " "
                                }
                                fullText += text
                                trySend(VoiceRecordResult.RecognitionResult(fullText))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                
                override fun onError(exception: Exception?) {
                    trySend(VoiceRecordResult.Error("识别错误: ${exception?.message ?: "未知错误"}"))
                }
                
                override fun onTimeout() {
                    // 超时不做处理，继续等待用户说话
                }
            })
            
        } catch (e: Exception) {
            trySend(VoiceRecordResult.Error("启动识别失败: ${e.message}"))
            close()
            return@callbackFlow
        }
        
        awaitClose {
            stopDurationUpdate()
            
            // 停止识别
            try {
                speechService?.stop()
                speechService?.shutdown()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            speechService = null
            
            _recordingState.value = RecordingState.IDLE
        }
    }
    
    /**
     * 清理资源
     */
    fun release() {
        stopDurationUpdate()
        
        try {
            speechService?.stop()
            speechService?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        speechService = null
        
        _recordingState.value = RecordingState.IDLE
    }
    
    /**
     * 释放模型资源（通常在应用退出时调用）
     */
    fun releaseModel() {
        release()
        model?.close()
        model = null
        isModelInitialized = false
    }
}
