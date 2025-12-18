package com.litetask.app.data.speech

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * 讯飞实时语音转写服务提供商
 * 文档：https://www.xfyun.cn/doc/asr/rtasr/API.html
 * * 修复说明：
 * 1. 增加了对 `pd` (Previous Dropped) 字段的处理，解决重复显示问题。
 * 2. 优化了 JSON 解析逻辑，增加数据类封装。
 */
class XunfeiSpeechProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechProvider {

    companion object {
        const val PROVIDER_ID = "xunfei-rtasr"
        const val FIELD_APP_ID = "appId"
        const val FIELD_API_KEY = "apiKey"

        private const val BASE_URL = "wss://rtasr.xfyun.cn/v1/ws"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SIZE = 1280 // 40ms 音频数据
        private const val FRAME_INTERVAL = 40L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private val isActive = AtomicBoolean(false)

    // 解析结果的数据类
    private data class ParsedFrame(
        val segId: Int,
        val text: String,
        val isFinal: Boolean,
        val pd: Int // Previous Dropped: 需要回退（删除）的句子数量
    )

    override fun getProviderId(): String = PROVIDER_ID
    override fun getProviderName(): String = "讯飞语音转写"
    override fun getDescription(): String = "讯飞开放平台实时语音转写服务"

    override fun getRequiredCredentials(): List<CredentialField> = listOf(
        CredentialField(FIELD_APP_ID, "App ID", true, false, "讯飞开放平台应用的 APPID"),
        CredentialField(FIELD_API_KEY, "API Key", true, true, "实时语音转写的 APIKey")
    )

    override suspend fun validateCredentials(credentials: Map<String, String>): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val appId = credentials[FIELD_APP_ID]
                val apiKey = credentials[FIELD_API_KEY]

                if (appId.isNullOrBlank()) return@withContext Result.failure(Exception("App ID 不能为空"))
                if (apiKey.isNullOrBlank()) return@withContext Result.failure(Exception("API Key 不能为空"))
                if (!appId.matches(Regex("^[a-zA-Z0-9]{8}$"))) {
                    return@withContext Result.failure(Exception("App ID 格式不正确，应为8位数字或字母"))
                }
                if (apiKey.length < 16) return@withContext Result.failure(Exception("API Key 格式不正确"))

                testWebSocketConnection(appId, apiKey)
            } catch (e: Exception) {
                Result.failure(Exception("验证失败: ${e.message}"))
            }
        }
    }

    override fun startRecognition(credentials: Map<String, String>): Flow<SpeechRecognitionResult> = callbackFlow {
        val appId = credentials[FIELD_APP_ID] ?: ""
        val apiKey = credentials[FIELD_API_KEY] ?: ""

        if (appId.isBlank() || apiKey.isBlank()) {
            trySend(SpeechRecognitionResult.Error("CONFIG", "讯飞凭证未配置"))
            close()
            return@callbackFlow
        }

        isActive.set(true)

        // key: seg_id, value: 该句子的最终文本
        val finalSegments = mutableMapOf<Int, String>()
        // 当前正在识别的句子（中间结果）
        var currentSegId = -1
        var currentPartialText = ""

        fun safeSend(result: SpeechRecognitionResult) {
            if (isActive.get()) trySend(result)
        }

        // 构建完整文本：已确认的句子 + 当前中间结果
        fun buildFullText(): String {
            val confirmedText = finalSegments.toSortedMap().values.joinToString("")
            // 只有当当前中间结果的 ID 不在已确认列表中时才拼接（避免已确认的句子重复拼接）
            return if (currentPartialText.isNotBlank() && currentSegId !in finalSegments) {
                confirmedText + currentPartialText
            } else {
                confirmedText
            }
        }

        val url = generateAuthUrl(appId, apiKey)
        val request = Request.Builder().url(url).build()

        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                safeSend(SpeechRecognitionResult.Started)
                startAudioCapture(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isActive.get()) return
                try {
                    val json = JSONObject(text)
                    val code = json.optString("code", "0")
                    val action = json.optString("action")

                    if (code != "0") {
                        val desc = json.optString("desc", "未知错误")
                        safeSend(SpeechRecognitionResult.Error(code, parseErrorCode(code, desc)))
                        return
                    }

                    if (action == "result") {
                        val dataStr = json.optString("data", "")
                        if (dataStr.isNotEmpty()) {
                            val dataJson = if (dataStr.startsWith("{")) dataStr else {
                                try { String(android.util.Base64.decode(dataStr, android.util.Base64.DEFAULT)) }
                                catch (e: Exception) { dataStr }
                            }

                            parseResult(dataJson)?.let { frame ->
                                val (segId, resultText, isFinal, pd) = frame

                                // --- 核心修复：处理 pd (Previous Dropped) ---
                                // 如果 pd > 0，说明需要删除之前已确认的 pd 个句子（用于动态修正）
                                if (pd > 0) {
                                    val sortedKeys = finalSegments.keys.sortedDescending()
                                    repeat(pd) { i ->
                                        if (i < sortedKeys.size) {
                                            finalSegments.remove(sortedKeys[i])
                                        }
                                    }
                                }

                                if (isFinal) {
                                    // 最终结果：存储到 finalSegments
                                    if (resultText.isNotBlank()) {
                                        finalSegments[segId] = resultText
                                    }
                                    // 如果当前暂存的句子就是这个刚确认的句子，清空暂存
                                    if (currentSegId == segId) {
                                        currentPartialText = ""
                                        currentSegId = -1
                                    }
                                } else {
                                    // 中间结果：更新当前句子的临时文本
                                    currentSegId = segId
                                    currentPartialText = resultText
                                }

                                // 只有构建出的文本不为空时才发送更新
                                val fullText = buildFullText()
                                if (fullText.isNotBlank()) {
                                    // 实时语音场景下，无论Final还是Partial，对UI来说都是Partial更新（直到结束）
                                    // 如果需要明确区分Final事件，可在此处判断，但通常UI只关心显示文本
                                    if (isFinal) {
                                        safeSend(SpeechRecognitionResult.PartialResult(fullText)) // 保持Partial以支持连续识别
                                    } else {
                                        safeSend(SpeechRecognitionResult.PartialResult(fullText))
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                safeSend(SpeechRecognitionResult.Error("NETWORK", "连接失败: ${t.message}"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {}
        })

        webSocket = ws

        awaitClose {
            stopRecognition()
        }
    }

    override fun stopRecognition() {
        isActive.set(false)
        stopAudioCapture()
        try {
            webSocket?.send("{\"end\": true}")
            webSocket?.close(1000, "Recognition ended")
        } catch (_: Exception) {}
        webSocket = null
    }

    private fun startAudioCapture(webSocket: WebSocket) {
        Thread {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
                    .coerceAtLeast(FRAME_SIZE * 2)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) return@Thread

                audioRecord?.startRecording()
                val buffer = ByteArray(FRAME_SIZE)

                while (isActive.get()) {
                    val readSize = audioRecord?.read(buffer, 0, FRAME_SIZE) ?: -1
                    if (readSize > 0) {
                        try { webSocket.send(buffer.copyOf(readSize).toByteString()) }
                        catch (_: Exception) { break }
                    }
                    Thread.sleep(FRAME_INTERVAL)
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { stopAudioCapture() }
        }.start()
    }

    private fun stopAudioCapture() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    private suspend fun testWebSocketConnection(appId: String, apiKey: String): Result<Boolean> {
        return suspendCancellableCoroutine { continuation ->
            var isResumed = false
            val successTimer = java.util.Timer()

            try {
                val url = generateAuthUrl(appId, apiKey)
                val request = Request.Builder().url(url).build()

                val ws = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(ws: WebSocket, response: Response) {
                        successTimer.schedule(object : java.util.TimerTask() {
                            override fun run() {
                                if (!isResumed) {
                                    isResumed = true
                                    ws.close(1000, "Validation Passed")
                                    continuation.resume(Result.success(true))
                                }
                            }
                        }, 2000L)
                    }

                    override fun onMessage(ws: WebSocket, text: String) {
                        try {
                            val json = JSONObject(text)
                            val code = json.optString("code")
                            if (code.isNotEmpty() && code != "0") {
                                successTimer.cancel()
                                if (!isResumed) {
                                    isResumed = true
                                    ws.close(1000, "Validation Failed")
                                    continuation.resume(Result.failure(Exception(parseErrorCode(code, json.optString("desc")))))
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                        successTimer.cancel()
                        if (!isResumed) {
                            isResumed = true
                            continuation.resume(Result.failure(Exception("连接失败: ${t.message}")))
                        }
                    }

                    override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                        if (!isResumed && code != 1000) {
                            successTimer.cancel()
                            isResumed = true
                            continuation.resume(Result.failure(Exception("服务端关闭: [$code] $reason")))
                        }
                        ws.close(1000, null)
                    }
                })

                continuation.invokeOnCancellation {
                    successTimer.cancel()
                    ws.cancel()
                }
            } catch (e: Exception) {
                successTimer.cancel()
                if (!isResumed) {
                    isResumed = true
                    continuation.resume(Result.failure(Exception("初始化失败: ${e.message}")))
                }
            }
        }
    }

    private fun generateAuthUrl(appId: String, apiKey: String): String {
        val ts = (System.currentTimeMillis() / 1000).toString()
        val baseString = appId + ts
        val md5Bytes = MessageDigest.getInstance("MD5").digest(baseString.toByteArray())
        val md5Hex = md5Bytes.joinToString("") { "%02x".format(it) }

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(apiKey.toByteArray(), "HmacSHA1"))
        val signa = android.util.Base64.encodeToString(mac.doFinal(md5Hex.toByteArray()), android.util.Base64.NO_WRAP)

        return "$BASE_URL?appid=$appId&ts=$ts&signa=${URLEncoder.encode(signa, "UTF-8")}"
    }

    /**
     * 解析讯飞返回的识别结果
     * 更新：现在返回数据类 ParsedFrame，包含 pd 字段
     */
    private fun parseResult(dataJson: String): ParsedFrame? {
        return try {
            val json = JSONObject(dataJson)
            val segId = json.optInt("seg_id", 0)
            val st = json.optJSONObject("cn")?.optJSONObject("st") ?: return null
            // type: 0=中间结果(实时更新), 1=最终结果
            val isFinal = st.optString("type", "0") == "1"
            // pd: Previous Dropped，表示需要丢弃前面的多少个已确认结果（动态修正）
            val pd = st.optInt("pd", 0)

            val textBuilder = StringBuilder()
            val rtArray = st.optJSONArray("rt") ?: return null
            for (i in 0 until rtArray.length()) {
                val wsArray = rtArray.optJSONObject(i)?.optJSONArray("ws") ?: continue
                for (j in 0 until wsArray.length()) {
                    val cwArray = wsArray.optJSONObject(j)?.optJSONArray("cw") ?: continue
                    for (k in 0 until cwArray.length()) {
                        textBuilder.append(cwArray.optJSONObject(k)?.optString("w", "") ?: "")
                    }
                }
            }
            ParsedFrame(segId, textBuilder.toString(), isFinal, pd)
        } catch (e: Exception) { null }
    }

    private fun parseErrorCode(code: String, desc: String): String = when (code) {
        "10001" -> "App ID 不存在"
        "10002" -> "App ID 已被禁用"
        "10003" -> "App ID 没有实时语音转写权限"
        "10004" -> "签名错误，请检查 API Key"
        "10005" -> "时间戳过期，请检查系统时间"
        "10006" -> "参数错误"
        "10007" -> "服务繁忙，请稍后重试"
        "10008" -> "服务不可用"
        "10009" -> "转写时长已用完"
        "10010" -> "并发数超限"
        "10011" -> "音频格式不支持"
        "10012" -> "音频时长超限"
        "10110" -> "签名无效，请检查 API Key"
        else -> desc.ifEmpty { "错误码: $code" }
    }
}