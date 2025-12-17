package com.litetask.app.data.speech

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * 讯飞实时语音转写服务提供商
 * 文档：https://www.xfyun.cn/doc/asr/rtasr/API.html
 */
class XunfeiSpeechProvider @Inject constructor() : SpeechProvider {

    companion object {
        const val PROVIDER_ID = "xunfei-rtasr"
        const val FIELD_APP_ID = "appId"
        const val FIELD_API_KEY = "apiKey"

        private const val BASE_URL = "wss://rtasr.xfyun.cn/v1/ws"
        private const val CONNECTION_TIMEOUT_MS = 10000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun getProviderId(): String = PROVIDER_ID

    override fun getProviderName(): String = "讯飞-实时语音转写标准版"

    override fun getDescription(): String = "讯飞开放平台实时语音转写服务，支持实时音频流转文字"

    override fun getRequiredCredentials(): List<CredentialField> {
        return listOf(
            CredentialField(
                id = FIELD_APP_ID,
                displayName = "App ID",
                isRequired = true,
                isSecret = false,
                hint = "讯飞开放平台应用的 APPID"
            ),
            CredentialField(
                id = FIELD_API_KEY,
                displayName = "API Key",
                isRequired = true,
                isSecret = true,
                hint = "实时语音转写的 APIKey"
            )
        )
    }

    override suspend fun validateCredentials(credentials: Map<String, String>): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val appId = credentials[FIELD_APP_ID]
                val apiKey = credentials[FIELD_API_KEY]

                // 基本格式校验
                if (appId.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("App ID 不能为空"))
                }
                if (apiKey.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("API Key 不能为空"))
                }

                // App ID 是8位数字或字母
                if (!appId.matches(Regex("^[a-zA-Z0-9]{8}$"))) {
                    return@withContext Result.failure(Exception("App ID 格式不正确，应为8位数字或字母"))
                }

                // API Key 长度校验
                if (apiKey.length < 16) {
                    return@withContext Result.failure(Exception("API Key 格式不正确"))
                }

                // 实际的 WebSocket 连接测试
                // 只建立连接验证凭证，不发送音频数据，不消耗转写时长
                testWebSocketConnection(appId, apiKey)

            } catch (e: Exception) {
                Result.failure(Exception("验证失败: ${e.message}"))
            }
        }
    }

    /**
     * 通过 WebSocket 连接测试凭证有效性
     * 逻辑修正：连接成功后需等待 2-3 秒，确认没有收到服务端的错误消息才算验证通过
     */
    private suspend fun testWebSocketConnection(appId: String, apiKey: String): Result<Boolean> {
        return suspendCancellableCoroutine { continuation ->
            var webSocket: WebSocket? = null
            // 标记是否已经返回了结果，防止多次 resume 导致 Crash
            var isResumed = false

            // 定义一个定时器，用于在验证通过后结束等待
            val successTimer = java.util.Timer()

            try {
                val url = generateAuthUrl(appId, apiKey)
                val request = Request.Builder().url(url).build()

                webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(ws: WebSocket, response: Response) {
                        // 【关键修改】不要立即 Close！
                        // 启动一个 2 秒的定时器。如果 2 秒内没有收到报错（onMessage/onFailure），则认为鉴权成功。
                        successTimer.schedule(object : java.util.TimerTask() {
                            override fun run() {
                                if (!isResumed) {
                                    isResumed = true
                                    // 主动关闭连接
                                    ws.close(1000, "Validation Passed")
                                    continuation.resume(Result.success(true))
                                }
                            }
                        }, 2000L) // 2000ms 等待期
                    }

                    override fun onMessage(ws: WebSocket, text: String) {
                        // 服务器返回了消息，极有可能是报错信息（因为我们没发音频，正常情况下服务端不会主动说话）
                        try {
                            val json = org.json.JSONObject(text)
                            val code = json.optString("code")

                            // 讯飞标准版：code 非 0 即为错
                            if (code.isNotEmpty() && code != "0") {
                                val desc = json.optString("desc", "未知错误")
                                val errorMsg = parseErrorCode(code, desc)

                                // 立即取消成功定时器
                                successTimer.cancel()

                                if (!isResumed) {
                                    isResumed = true
                                    ws.close(1000, "Validation Failed")
                                    continuation.resume(Result.failure(Exception(errorMsg)))
                                }
                            }
                        } catch (e: Exception) {
                            // JSON 解析失败，忽略非关键信息
                        }
                    }

                    override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                        // 立即取消成功定时器
                        successTimer.cancel()

                        if (!isResumed) {
                            isResumed = true
                            val errorMsg = when {
                                response?.code == 401 -> "凭证无效: 401 Unauthorized"
                                response?.code == 403 -> "权限不足或签名错误 (403)" // 鉴权失败常报 403
                                t.message?.contains("Unable to resolve host") == true -> "网络错误：无法解析域名"
                                t.message?.contains("timeout") == true -> "连接超时，请检查网络"
                                else -> "连接失败: ${t.message ?: response?.message}"
                            }
                            continuation.resume(Result.failure(Exception(errorMsg)))
                        }
                    }

                    override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                        // 服务端主动关闭，可能是鉴权失败
                        if(!isResumed && code != 1000) {
                            // 如果不是我们自己发起的关闭，且还没有结果
                            successTimer.cancel()
                            isResumed = true
                            continuation.resume(Result.failure(Exception("服务端关闭连接: [$code] $reason")))
                        }
                        ws.close(1000, null)
                    }
                })

                // 协程取消时的清理工作
                continuation.invokeOnCancellation {
                    successTimer.cancel()
                    webSocket.cancel()
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

    /**
     * 生成讯飞实时语音转写的鉴权 URL
     * 文档：https://www.xfyun.cn/doc/asr/rtasr/API.html#%E6%8E%A5%E5%8F%A3%E8%AF%B4%E6%98%8E
     */
    private fun generateAuthUrl(appId: String, apiKey: String): String {
        val ts = (System.currentTimeMillis() / 1000).toString()

        // 生成签名: MD5(appId + ts)，然后用 apiKey 进行 HMAC-SHA1 加密，最后 Base64 编码
        val baseString = appId + ts
        val md5Bytes = MessageDigest.getInstance("MD5").digest(baseString.toByteArray())
        val md5Hex = md5Bytes.joinToString("") { "%02x".format(it) }

        val mac = Mac.getInstance("HmacSHA1")
        val secretKey = SecretKeySpec(apiKey.toByteArray(), "HmacSHA1")
        mac.init(secretKey)
        val signBytes = mac.doFinal(md5Hex.toByteArray())
        val signa = android.util.Base64.encodeToString(signBytes, android.util.Base64.NO_WRAP)

        // URL 编码
        val encodedSigna = URLEncoder.encode(signa, "UTF-8")

        return "$BASE_URL?appid=$appId&ts=$ts&signa=$encodedSigna"
    }

    /**
     * 解析讯飞错误码
     */
    private fun parseErrorCode(code: String, desc: String): String {
        return when (code) {
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
            else -> desc.ifEmpty { "错误码: $code" }
        }
    }
}
