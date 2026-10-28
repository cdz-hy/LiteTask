package com.litetask.app.util

import android.os.Handler
import android.os.Looper
import com.litetask.app.data.local.PreferenceManager
import com.litetask.app.data.speech.AndroidSttProvider
import com.litetask.app.data.speech.MimoAsrProvider
import com.litetask.app.data.speech.SpeechProvider
import com.litetask.app.data.speech.SpeechRecognitionResult
import com.litetask.app.data.speech.XunfeiSpeechProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// 识别状态
enum class RecordingState {
    IDLE, RECORDING, PLAYING, RECOGNIZING, DONE
}

// 识别回调结果（兼容旧接口）
sealed class VoiceRecordResult {
    object RecordingStarted : VoiceRecordResult()
    object RecordingStopped : VoiceRecordResult()
    object PlaybackStarted : VoiceRecordResult()
    object PlaybackFinished : VoiceRecordResult()
    object RecognitionStarted : VoiceRecordResult()
    data class RecognitionResult(val text: String) : VoiceRecordResult()
    data class Error(val message: String) : VoiceRecordResult()
}

// 语音识别源信息
data class SpeechSourceInfo(
    val sourceId: String,
    val displayName: String,
    val isConfigured: Boolean
)

/**
 * 语音识别帮助类
 * 负责协调不同的语音识别提供商
 */
@Singleton
class SpeechRecognizerHelper @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val xunfeiProvider: XunfeiSpeechProvider,
    private val mimoAsrProvider: MimoAsrProvider,
    private val androidSttProvider: AndroidSttProvider
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState
    
    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration
    
    private var recognitionStartTime = 0L
    private var durationUpdateRunnable: Runnable? = null
    private var currentProvider: SpeechProvider? = null

    /**
     * 获取当前语音识别源信息
     */
    fun getCurrentSourceInfo(): SpeechSourceInfo {
        val isConfigured = preferenceManager.isSpeechConfigured()
        return if (isConfigured) {
            val providerId = preferenceManager.getSpeechProvider()
            val provider = getProvider(providerId)
            SpeechSourceInfo(providerId, provider.getProviderName(), true)
        } else {
            SpeechSourceInfo(androidSttProvider.getProviderId(), androidSttProvider.getProviderName(), false)
        }
    }

    /**
     * 获取对应的 Provider
     */
    private fun getProvider(providerId: String): SpeechProvider {
        return when (providerId) {
            XunfeiSpeechProvider.PROVIDER_ID -> xunfeiProvider
            MimoAsrProvider.PROVIDER_ID -> mimoAsrProvider
            else -> androidSttProvider
        }
    }

    /**
     * 获取凭证
     */
    private fun getCredentials(providerId: String): Map<String, String> {
        val provider = getProvider(providerId)
        val credentialIds = provider.getRequiredCredentials().map { it.id }
        return preferenceManager.getSpeechCredentials(providerId, credentialIds)
    }

    private fun startDurationUpdate() {
        recognitionStartTime = System.currentTimeMillis()
        durationUpdateRunnable = object : Runnable {
            override fun run() {
                _recordingDuration.value = System.currentTimeMillis() - recognitionStartTime
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
     * 开始语音识别
     */
    fun startRecognition(): Flow<VoiceRecordResult> {
        val sourceInfo = getCurrentSourceInfo()
        val providerId = if (sourceInfo.isConfigured) sourceInfo.sourceId else AndroidSttProvider.PROVIDER_ID
        val provider = getProvider(providerId)
        val credentials = getCredentials(providerId)
        
        currentProvider = provider
        _recordingState.value = RecordingState.RECORDING
        startDurationUpdate()

        // 将 SpeechRecognitionResult 转换为 VoiceRecordResult
        return provider.startRecognition(credentials).map { result ->
            when (result) {
                is SpeechRecognitionResult.Started -> VoiceRecordResult.RecordingStarted
                is SpeechRecognitionResult.PartialResult -> VoiceRecordResult.RecognitionResult(result.text)
                is SpeechRecognitionResult.FinalResult -> VoiceRecordResult.RecognitionResult(result.text)
                is SpeechRecognitionResult.Error -> VoiceRecordResult.Error(result.message)
            }
        }
    }

    /**
     * 对本地语音备忘录音频文件（`.m4a` 等）执行解码并调用已配置的云端 ASR 转文字。
     */
    suspend fun transcribeAudioFile(filePath: String): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val file = java.io.File(filePath)
        if (!file.exists() || !file.isFile || !file.canRead()) {
            return@withContext Result.failure(Exception("语音文件不存在或无法读取"))
        }

        val providerId = resolveOfflineAsrProviderId()
            ?: return@withContext Result.failure(Exception("请先在设置中配置讯飞或 MiMo 语音转写服务"))
        val credentials = getCredentials(providerId)

        val pcmData = AudioPcmDecoder.decodeTo16kMonoPcm(file)
            ?: return@withContext Result.failure(Exception("音频解码失败，无法提取语音波形"))

        when (providerId) {
            MimoAsrProvider.PROVIDER_ID -> mimoAsrProvider.transcribePcm(pcmData, credentials)
            XunfeiSpeechProvider.PROVIDER_ID -> xunfeiProvider.transcribePcm(pcmData, credentials)
            else -> Result.failure(Exception("当前语音源暂不支持音频文件转写"))
        }
    }

    private fun resolveOfflineAsrProviderId(): String? {
        if (preferenceManager.isSpeechConfigured()) {
            val current = preferenceManager.getSpeechProvider()
            if (current == MimoAsrProvider.PROVIDER_ID || current == XunfeiSpeechProvider.PROVIDER_ID) {
                return current
            }
        }
        val mimoKey = getCredentials(MimoAsrProvider.PROVIDER_ID)[MimoAsrProvider.FIELD_API_KEY]
        if (!mimoKey.isNullOrBlank()) return MimoAsrProvider.PROVIDER_ID

        val xfCreds = getCredentials(XunfeiSpeechProvider.PROVIDER_ID)
        if (!xfCreds[XunfeiSpeechProvider.FIELD_APP_ID].isNullOrBlank() &&
            !xfCreds[XunfeiSpeechProvider.FIELD_API_KEY].isNullOrBlank()
        ) {
            return XunfeiSpeechProvider.PROVIDER_ID
        }
        return null
    }

    /**
     * 释放资源
     */
    fun release() {
        stopDurationUpdate()
        currentProvider?.stopRecognition()
        currentProvider = null
        _recordingState.value = RecordingState.IDLE
        _recordingDuration.value = 0L
    }
}
