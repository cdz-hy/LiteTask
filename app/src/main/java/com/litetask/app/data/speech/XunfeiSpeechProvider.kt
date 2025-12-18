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

/**
 * 讯飞实时语音转写 (RT-ASR) 服务提供者
 *
 * 采用 Final + Temp 双缓冲架构处理识别结果：
 * - committedText: 已确认的最终文本，不会再变化
 * - currentDraft: 当前正在识别的临时文本，会随语音输入实时更新
 *
 * 该架构解决了两个常见问题：
 * 1. 重复文本问题：收到最终结果时清空草稿，避免新旧内容叠加
 * 2. 覆盖问题：中间结果默认采用替换模式，避免字词重复拼接
 */
class XunfeiSpeechProvider @Inject constructor(
    @ApplicationContext private val appContext: Context
) : SpeechProvider {

    companion object {
        const val PROVIDER_ID = "xunfei-rtasr"
        const val FIELD_APP_ID = "appId"
        const val FIELD_API_KEY = "apiKey"
        private const val TAG = "XunfeiSpeech"

        private const val BASE_URL = "wss://rtasr.xfyun.cn/v1/ws"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SIZE = 1280        // 每帧音频数据大小 (字节)
        private const val FRAME_INTERVAL = 40L    // 发送间隔 (毫秒)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val isActive = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null

    /**
     * 解析后的识别帧数据
     * @param text 识别出的文本内容
     * @param type 结果类型: 0=最终结果, 1=中间结果
     * @param pgs 处理模式: rpl=替换, apd=追加
     */
    private data class ParsedFrame(
        val text: String,
        val type: Int,
        val pgs: String
    )

    override fun getProviderId() = PROVIDER_ID
    override fun getProviderName() = "讯飞语音转写"
    override fun getDescription() = "支持中文长语音流式识别"
    override fun getRequiredCredentials() = listOf(
        CredentialField(FIELD_APP_ID, "App ID", true, false, "讯飞应用ID"),
        CredentialField(FIELD_API_KEY, "API Key", true, true, "RT-ASR API Key")
    )

    override fun startRecognition(credentials: Map<String, String>): Flow<SpeechRecognitionResult> =
        startRecognitionInternal(credentials)

    private fun startRecognitionInternal(credentials: Map<String, String>): Flow<SpeechRecognitionResult> = callbackFlow {
        val appId = credentials[FIELD_APP_ID] ?: ""
        val apiKey = credentials[FIELD_API_KEY] ?: ""

        if (appId.isBlank() || apiKey.isBlank()) {
            trySend(SpeechRecognitionResult.Error("CONFIG", "请配置 AppID 和 API Key"))
            close()
            return@callbackFlow
        }

        isActive.set(true)

        // 双缓冲状态管理
        val committedText = StringBuilder()  // 已确认文本
        var currentDraft = ""                // 当前草稿

        fun emitUpdate() {
            if (!isActive.get()) return
            val fullText = committedText.toString() + currentDraft
            trySend(SpeechRecognitionResult.PartialResult(fullText))
        }

        val url = generateAuthUrl(appId, apiKey)
        val request = Request.Builder().url(url).build()

        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected to Xunfei")
                safeSend(SpeechRecognitionResult.Started)
                startAudioCapture(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isActive.get()) return
                try {
                    val json = JSONObject(text)
                    val action = json.optString("action")

                    if (action == "error") {
                        safeSend(SpeechRecognitionResult.Error("API_ERR", json.optString("desc")))
                        return
                    }

                    if (action == "result") {
                        val dataStr = json.optString("data", "")
                        if (dataStr.isNotEmpty()) {
                            // 解析数据，可能是 JSON 字符串或 Base64 编码
                            val resultJsonStr = if (dataStr.trim().startsWith("{")) dataStr else {
                                try { String(android.util.Base64.decode(dataStr, android.util.Base64.DEFAULT)) }
                                catch (e: Exception) { dataStr }
                            }

                            val frame = parseContent(resultJsonStr) ?: return

                            synchronized(committedText) {
                                if (frame.type == 0) {
                                    // 最终结果：追加到已确认文本，清空草稿
                                    committedText.append(frame.text)
                                    currentDraft = ""
                                } else {
                                    // 中间结果：更新草稿
                                    currentDraft = if (frame.pgs == "apd") {
                                        currentDraft + frame.text
                                    } else {
                                        frame.text  // 默认替换模式
                                    }
                                }
                            }
                            emitUpdate()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Message parse error", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failed", t)
                safeSend(SpeechRecognitionResult.Error("NET_FAIL", t.message ?: "连接断开"))
            }

            fun safeSend(res: SpeechRecognitionResult) {
                if (isActive.get()) trySend(res)
            }
        })

        awaitClose { stopRecognition() }
    }

    /**
     * 解析讯飞返回的 JSON 识别结果
     */
    private fun parseContent(jsonStr: String): ParsedFrame? {
        return try {
            val json = JSONObject(jsonStr)
            val cn = json.optJSONObject("cn") ?: return null
            val st = cn.optJSONObject("st") ?: return null

            val type = st.optString("type", "1").toIntOrNull() ?: 1
            val pgs = st.optString("pgs", "rpl")

            val sb = StringBuilder()
            val rtArray = st.optJSONArray("rt")
            if (rtArray != null) {
                for (i in 0 until rtArray.length()) {
                    val rtObj = rtArray.getJSONObject(i)
                    val wsArray = rtObj.optJSONArray("ws") ?: continue
                    for (j in 0 until wsArray.length()) {
                        val wsObj = wsArray.getJSONObject(j)
                        val cwArray = wsObj.optJSONArray("cw") ?: continue
                        // 只取第一个候选词，避免多选词重复
                        if (cwArray.length() > 0) {
                            sb.append(cwArray.getJSONObject(0).optString("w"))
                        }
                    }
                }
            }
            ParsedFrame(sb.toString(), type, pgs)
        } catch (e: Exception) {
            Log.e(TAG, "Parse error", e)
            null
        }
    }

    override fun stopRecognition() {
        isActive.set(false)
        stopAudioCapture()
    }

    /**
     * 启动音频采集并通过 WebSocket 发送
     */
    private fun startAudioCapture(webSocket: WebSocket) {
        Thread {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
                    .coerceAtLeast(FRAME_SIZE * 2)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, CHANNEL, ENCODING, bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    return@Thread
                }

                audioRecord?.startRecording()
                val buffer = ByteArray(FRAME_SIZE)

                while (isActive.get()) {
                    val read = audioRecord?.read(buffer, 0, FRAME_SIZE) ?: -1
                    if (read > 0) {
                        try {
                            webSocket.send(buffer.copyOf(read).toByteString())
                        } catch (e: Exception) {
                            break
                        }
                    }
                    Thread.sleep(FRAME_INTERVAL)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio capture error", e)
            } finally {
                try {
                    webSocket.send("{\"end\": true}")
                    webSocket.close(1000, "Normal closure")
                } catch (_: Exception) {}
                stopAudioCapture()
            }
        }.start()
    }

    private fun stopAudioCapture() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    /**
     * 生成带鉴权参数的 WebSocket URL
     * 使用 HMAC-SHA1 签名算法
     */
    private fun generateAuthUrl(appId: String, apiKey: String): String {
        val ts = (System.currentTimeMillis() / 1000).toString()
        val baseString = appId + ts

        // MD5 哈希
        val md5 = MessageDigest.getInstance("MD5")
            .digest(baseString.toByteArray())
            .joinToString("") { "%02x".format(it) }

        // HMAC-SHA1 签名
        val mac = Mac.getInstance("HmacSHA1").apply {
            init(SecretKeySpec(apiKey.toByteArray(), "HmacSHA1"))
        }
        val signa = android.util.Base64.encodeToString(
            mac.doFinal(md5.toByteArray()),
            android.util.Base64.NO_WRAP
        )

        return "$BASE_URL?appid=$appId&ts=$ts&signa=${URLEncoder.encode(signa, "UTF-8")}"
    }

    override suspend fun validateCredentials(credentials: Map<String, String>) =
        withContext(Dispatchers.IO) { Result.success(true) }
}
