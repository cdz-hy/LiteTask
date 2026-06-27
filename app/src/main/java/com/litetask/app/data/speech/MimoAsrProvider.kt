package com.litetask.app.data.speech

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * MiMo-V2.5-ASR 语音识别服务提供者
 *
 * 使用小米 MiMo ASR API 进行语音识别
 * 采用分段录制 + 流式调用的方式实现实时识别效果
 */
class MimoAsrProvider @Inject constructor(
    @ApplicationContext private val appContext: Context
) : SpeechProvider {

    companion object {
        const val PROVIDER_ID = "mimo-asr"
        const val FIELD_BASE_URL = "baseUrl"
        const val FIELD_API_KEY = "apiKey"
        private const val TAG = "MimoAsr"

        private const val DEFAULT_BASE_URL = "https://api.xiaomimimo.com/v1"
        private const val MODEL = "mimo-v2.5-asr"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val SEGMENT_DURATION_MS = 3000L
        private const val BITS_PER_SAMPLE = 16
        private const val NUM_CHANNELS = 1
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val recordingActive = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null

    override fun getProviderId() = PROVIDER_ID
    override fun getProviderName() = "MiMo ASR"
    override fun getDescription() = "小米 MiMo-V2.5 语音识别"
    override fun getRequiredCredentials() = listOf(
        CredentialField(FIELD_BASE_URL, "Base URL", false, false, "默认: $DEFAULT_BASE_URL"),
        CredentialField(FIELD_API_KEY, "API Key", true, true, "MiMo API Key")
    )

    override fun startRecognition(credentials: Map<String, String>): Flow<SpeechRecognitionResult> =
        startRecognitionInternal(credentials)

    private fun startRecognitionInternal(credentials: Map<String, String>): Flow<SpeechRecognitionResult> = callbackFlow {
        val baseUrl = credentials[FIELD_BASE_URL]?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
        val apiKey = credentials[FIELD_API_KEY] ?: ""

        if (apiKey.isBlank()) {
            trySend(SpeechRecognitionResult.Error("CONFIG", "请配置 API Key"))
            close()
            return@callbackFlow
        }

        recordingActive.set(true)
        trySend(SpeechRecognitionResult.Started)

        val committedText = StringBuilder()

        val recordingThread = Thread {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
                    .coerceAtLeast(4096)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, CHANNEL, ENCODING, bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    trySend(SpeechRecognitionResult.Error("AUDIO", "音频录制初始化失败"))
                    return@Thread
                }

                audioRecord?.startRecording()
                val segmentBuffer = ByteArrayOutputStream()
                val readBuffer = ByteArray(1024)
                var segmentStartTime = System.currentTimeMillis()

                while (recordingActive.get()) {
                    val read = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1
                    if (read > 0) {
                        segmentBuffer.write(readBuffer, 0, read)

                        val elapsed = System.currentTimeMillis() - segmentStartTime
                        if (elapsed >= SEGMENT_DURATION_MS && segmentBuffer.size() > 0) {
                            val pcmData = segmentBuffer.toByteArray()
                            segmentBuffer.reset()
                            segmentStartTime = System.currentTimeMillis()

                            try {
                                val wavData = convertPcmToWav(pcmData)
                                val result = recognizeAudio(wavData, baseUrl, apiKey)
                                if (result != null && recordingActive.get()) {
                                    synchronized(committedText) {
                                        committedText.append(result)
                                    }
                                    trySend(SpeechRecognitionResult.PartialResult(committedText.toString()))
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Recognition error", e)
                            }
                        }
                    }
                }

                if (segmentBuffer.size() > 0 && recordingActive.get()) {
                    try {
                        val pcmData = segmentBuffer.toByteArray()
                        val wavData = convertPcmToWav(pcmData)
                        val result = recognizeAudio(wavData, baseUrl, apiKey)
                        if (result != null && recordingActive.get()) {
                            synchronized(committedText) {
                                committedText.append(result)
                            }
                            trySend(SpeechRecognitionResult.FinalResult(committedText.toString()))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Final segment recognition error", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recording error", e)
                trySend(SpeechRecognitionResult.Error("RECORD", e.message ?: "录音异常"))
            } finally {
                stopAudioCapture()
                if (recordingActive.get()) {
                    trySend(SpeechRecognitionResult.FinalResult(committedText.toString()))
                }
            }
        }

        recordingThread.start()

        awaitClose { stopRecognition() }
    }

    /**
     * 将 PCM 数据转换为 WAV 格式
     */
    private fun convertPcmToWav(pcmData: ByteArray): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = SAMPLE_RATE * NUM_CHANNELS * BITS_PER_SAMPLE / 8
        val blockAlign = NUM_CHANNELS * BITS_PER_SAMPLE / 8

        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        dos.writeBytes("RIFF")
        dos.writeIntLE(totalDataLen)
        dos.writeBytes("WAVE")

        dos.writeBytes("fmt ")
        dos.writeIntLE(16)
        dos.writeShortLE(1)
        dos.writeShortLE(NUM_CHANNELS)
        dos.writeIntLE(SAMPLE_RATE)
        dos.writeIntLE(byteRate)
        dos.writeShortLE(blockAlign)
        dos.writeShortLE(BITS_PER_SAMPLE)

        dos.writeBytes("data")
        dos.writeIntLE(pcmData.size)
        dos.write(pcmData)

        dos.flush()
        return baos.toByteArray()
    }

    private fun recognizeAudio(wavData: ByteArray, baseUrl: String, apiKey: String): String? {
        val audioBase64 = Base64.getEncoder().encodeToString(wavData)
        val dataUrl = "data:audio/wav;base64,$audioBase64"

        val requestBody = JSONObject().apply {
            put("model", MODEL)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "input_audio")
                            put("input_audio", JSONObject().apply {
                                put("data", dataUrl)
                            })
                        })
                    })
                })
            })
            put("asr_options", JSONObject().apply {
                put("language", "auto")
            })
            put("stream", false)
        }

        val url = "${baseUrl.trimEnd('/')}/chat/completions"
        val request = Request.Builder()
            .url(url)
            .addHeader("api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: return null

        if (!response.isSuccessful) {
            Log.e(TAG, "API error: ${response.code} - $responseBody")
            throw Exception("API 请求失败: ${response.code}")
        }

        return parseResponse(responseBody)
    }

    private fun parseResponse(responseBody: String): String? {
        return try {
            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null

            val message = choices.getJSONObject(0).optJSONObject("message") ?: return null
            val content = message.optString("content", "")
            content.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Response parse error", e)
            null
        }
    }

    override fun stopRecognition() {
        recordingActive.set(false)
        stopAudioCapture()
    }

    private fun stopAudioCapture() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    override suspend fun validateCredentials(credentials: Map<String, String>) =
        withContext(Dispatchers.IO) {
            try {
                val baseUrl = credentials[FIELD_BASE_URL]?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
                val apiKey = credentials[FIELD_API_KEY] ?: ""

                if (apiKey.isBlank()) {
                    return@withContext Result.failure(Exception("请输入 API Key"))
                }

                // 生成一个最小的静音 WAV 音频用于测试
                val testWavData = generateSilentWav(durationMs = 100)
                val audioBase64 = Base64.getEncoder().encodeToString(testWavData)
                val dataUrl = "data:audio/wav;base64,$audioBase64"

                val requestBody = JSONObject().apply {
                    put("model", MODEL)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("type", "input_audio")
                                    put("input_audio", JSONObject().apply {
                                        put("data", dataUrl)
                                    })
                                })
                            })
                        })
                    })
                    put("asr_options", JSONObject().apply {
                        put("language", "auto")
                    })
                }

                val url = "${baseUrl.trimEnd('/')}/chat/completions"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("api-key", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    val errorBody = response.body?.string() ?: ""
                    Log.e(TAG, "Validate error: ${response.code} - $errorBody")
                    val errorMsg = try {
                        val json = JSONObject(errorBody)
                        json.optJSONObject("error")?.optString("message") ?: json.optString("message")
                    } catch (_: Exception) { null }
                    val userMessage = when (response.code) {
                        401 -> "API Key 无效"
                        403 -> "API Key 权限不足"
                        404 -> "接口地址错误，请检查 Base URL"
                        429 -> "请求过于频繁，请稍后再试"
                        else -> errorMsg ?: "连接失败: ${response.code}"
                    }
                    Result.failure(Exception(userMessage))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Validate exception", e)
                Result.failure(Exception("连接异常: ${e.message}"))
            }
        }

    /**
     * 生成指定时长的静音 WAV 音频（用于测试连接）
     */
    private fun generateSilentWav(durationMs: Int): ByteArray {
        val numSamples = SAMPLE_RATE * durationMs / 1000
        val pcmData = ByteArray(numSamples * 2) // 16-bit = 2 bytes per sample
        return convertPcmToWav(pcmData)
    }
}

/**
 * DataOutputStream 扩展函数 - 写入小端序整数
 */
private fun DataOutputStream.writeIntLE(value: Int) {
    write(value and 0xFF)
    write((value shr 8) and 0xFF)
    write((value shr 16) and 0xFF)
    write((value shr 24) and 0xFF)
}

/**
 * DataOutputStream 扩展函数 - 写入小端序短整数
 */
private fun DataOutputStream.writeShortLE(value: Int) {
    write(value and 0xFF)
    write((value shr 8) and 0xFF)
}
