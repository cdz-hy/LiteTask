package com.litetask.app.data.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Android 原生语音识别提供商 (优化版)
 * 修复了连续识别中断、语言设置和结果拼接逻辑
 */
class AndroidSttProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechProvider {

    companion object {
        const val PROVIDER_ID = "android-stt"
        private const val TAG = "AndroidSttProvider"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    // 使用 AtomicBoolean 确保跨线程状态安全
    private val isActive = AtomicBoolean(false)

    override fun getProviderId(): String = PROVIDER_ID
    override fun getProviderName(): String = "Android STT"
    override fun getDescription(): String = "Android 系统内置语音识别"
    override fun getRequiredCredentials(): List<CredentialField> = emptyList()

    override suspend fun validateCredentials(credentials: Map<String, String>): Result<Boolean> {
        return if (SpeechRecognizer.isRecognitionAvailable(context)) {
            Result.success(true)
        } else {
            Result.failure(Exception("当前设备不支持原生语音识别"))
        }
    }

    override fun startRecognition(credentials: Map<String, String>): Flow<SpeechRecognitionResult> = callbackFlow {
        // 1. 严格检查可用性
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(SpeechRecognitionResult.Error("UNAVAILABLE", "设备不支持语音识别，请检查是否安装了 Google App 或其他语音引擎"))
            close()
            return@callbackFlow
        }

        isActive.set(true)

        // 状态变量：分离"已确认文本"和"当前片段文本"
        var committedText = ""
        var currentSegmentText = ""

        // 统一发送结果的方法
        fun sendUpdate() {
            val fullText = (committedText + " " + currentSegmentText).trim()
            if (fullText.isNotEmpty()) {
                // 实时发送完整结果，UI层直接覆盖显示即可
                trySend(SpeechRecognitionResult.PartialResult(fullText))
            }
        }

        // 必须在主线程创建和操作 SpeechRecognizer
        mainHandler.post {
            if (!isActive.get()) return@post

            try {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer

                // 配置 Intent
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    // 跟随系统语言，避免中文无法识别英文或反之
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    // 必须开启部分结果返回，否则无法实时显示
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    // 增加最大结果数，提高准确率（虽然我们只取第一个）
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                val listener = object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        trySend(SpeechRecognitionResult.Started)
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {} // 可以用来做音量波形动画

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        // 用户停止说话，但这不代表识别结束（还在处理），也不代表我们要停止监听
                        // 真正的逻辑在 onResults 或 onError 中处理
                    }

                    override fun onError(error: Int) {
                        if (!isActive.get()) return

                        // 处理非致命错误，自动重启以实现“连续识别”
                        if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {

                            // 稍微延迟重启，避免死循环
                            restartListening(recognizer, intent)
                            return
                        }

                        // 将错误码转换为可读信息
                        val message = when(error) {
                            SpeechRecognizer.ERROR_AUDIO -> "音频录制错误"
                            SpeechRecognizer.ERROR_CLIENT -> "客户端错误" // 常见于被其他应用抢占或快速重启
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "麦克风权限不足"
                            SpeechRecognizer.ERROR_NETWORK -> "网络连接失败"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                            SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别服务忙"
                            else -> "未知错误 ($error)"
                        }

                        Log.e(TAG, "Speech Error: $error - $message")

                        // 客户端错误有时可以忽略并重启
                        if (error == SpeechRecognizer.ERROR_CLIENT) {
                            restartListening(recognizer, intent)
                        } else {
                            // 致命错误，通知 UI
                            trySend(SpeechRecognitionResult.Error("ERROR_$error", message))
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        if (!isActive.get()) return

                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val result = matches[0]
                            if (result.isNotBlank()) {
                                // 确认当前片段，追加到总文本
                                committedText = (committedText + " " + result).trim()
                            }
                        }
                        // 清空当前片段缓存
                        currentSegmentText = ""
                        sendUpdate()

                        // 关键：收到结果后，立即重启监听，实现连续对话
                        restartListening(recognizer, intent)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        if (!isActive.get()) return

                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            // 更新当前正在说的片段
                            currentSegmentText = matches[0]
                            sendUpdate()
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                }

                recognizer.setRecognitionListener(listener)
                recognizer.startListening(intent)

            } catch (e: Exception) {
                trySend(SpeechRecognitionResult.Error("INIT_FAIL", "初始化失败: ${e.message}"))
                Log.e(TAG, "Init failed", e)
            }
        }

        awaitClose {
            stopRecognition()
        }
    }

    private fun restartListening(recognizer: SpeechRecognizer, intent: Intent) {
        if (!isActive.get()) return
        mainHandler.postDelayed({
            if (isActive.get()) {
                try {
                    // 确保先停止再开始，避免 ERROR_RECOGNIZER_BUSY
                    recognizer.cancel()
                    recognizer.startListening(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Restart failed", e)
                }
            }
        }, 100) // 100ms 延迟确保状态重置
    }

    override fun stopRecognition() {
        isActive.set(false)
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Stop failed", e)
            }
            speechRecognizer = null
        }
    }
}