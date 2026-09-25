package com.litetask.app.data.ai

import com.litetask.app.data.model.Category
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容协议提供方基类，统一封装解析、连通性测试、工具调用与子任务生成流程。
 */
abstract class BaseOpenAICompatibleProvider : AIProvider {

    protected val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(75, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(75, TimeUnit.SECONDS)
        .build()

    protected abstract fun resolveChatCompletionsUrl(): String
    protected abstract fun resolveModelsUrl(): String
    protected abstract val providerErrorLabel: String

    protected open val maxTokensParamName: String = "max_tokens"
    protected open val fallbackChatTestOnModelsUnsupported: Boolean = false

    protected open fun decorateRequestBody(body: JSONObject) {}

    override suspend fun parseTasksFromText(
        apiKey: String,
        model: String,
        text: String,
        categories: List<Category>,
        imageBases64: List<String>?
    ): Result<List<Task>> = withContext(Dispatchers.IO) {
        try {
            val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val categoryPrompt = if (categories.isNotEmpty()) {
                categories.joinToString(" | ") { it.name }
            } else {
                "工作 | 生活 | 学习 | 紧急"
            }
            val defaultCategoryName = categories.firstOrNull { it.isDefault }?.name
                ?: categories.firstOrNull()?.name
                ?: "工作"

            val systemPrompt = """
# Role: LiteTask 智能日程规划师
# Context: Now = $currentDate
# Goal: 将语音输入解析为结构化 JSON Array。

# Rules
1. **拆分与纠错**: 识别多任务，修正语音/逻辑错误。无效输入返 `[]`。
2. **时间推断 (核心)**:
   - 默认/单点时间: 视为 endTime (截止)，startTime 设为 Now。
   - 明确起止: 仅在明确"从X到Y"时设定具体区间。
   - 无时间: endTime = Now + 24h。
3. **分类**: 请严格从以下类别中选择 (默认: $defaultCategoryName):
   $categoryPrompt
4. **描述生成**: `description` 必须基于用户原始输入生成真实有意义的简要描述:
   - 提取并扩展用户提到的具体细节（地点、人物、数量、方式等）
   - 若用户输入简短，合理推断任务的目的或上下文
   - 禁止生成空洞模板化内容，每个描述必须与该任务直接相关
   - 长度控制在 10-50 字
5. **子任务逻辑**:
   - 仅对需要拆解的复杂任务生成子任务
   - 简单任务（如"买菜"、"开会"等）不需要子任务
   - 子任务必须与主任务紧密相关，具体可执行
6. **目的地识别**:
   - 若用户提及明确地点（如“去万达”、“到图书馆”），提取地名放入 `destination` 字段。
   - 若无明确地点，该字段省略或为空字符串。

# Output
仅返回纯 JSON 数组，无 Markdown。
[{"title":"精炼标题<20字","startTime":"yyyy-MM-dd HH:mm","endTime":"yyyy-MM-dd HH:mm","type":"分类名称","description":"基于输入的真实任务描述","destination":"明确的地点名称(可选)"}]
            """.trimIndent()

            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(buildUserMessage(text, imageBases64))
                })
                put("temperature", 0.7)
                put(maxTokensParamName, 4096)
                decorateRequestBody(this)
            }

            val jsonResponse = executeChatPost(apiKey, requestBody)
                .getOrElse { return@withContext Result.failure(it) }
            val content = extractAssistantContent(jsonResponse)
            val tasks = parseJsonToTasks(content, text, categories)
            Result.success(tasks)
        } catch (e: org.json.JSONException) {
            Result.failure(Exception("JSON 解析失败: ${e.message}", e))
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("网络连接失败，请检查网络设置", e))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("请求超时，请稍后重试", e))
        } catch (e: java.net.ConnectException) {
            Result.failure(Exception("无法连接到服务器，请稍后重试", e))
        } catch (e: java.net.SocketException) {
            Result.failure(Exception("网络连接异常，请检查网络设置", e))
        } catch (e: Exception) {
            Result.failure(Exception("$providerErrorLabel 解析失败: ${e.message}", e))
        }
    }

    override suspend fun testConnection(apiKey: String, model: String): Result<List<Pair<String, String>>> =
        withContext(Dispatchers.IO) {
            try {
                val requestBuilder = Request.Builder().url(resolveModelsUrl()).get()
                if (apiKey.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: return@withContext Result.success(emptyList())
                        val jsonResponse = JSONObject(responseBody)
                        val dataArray = jsonResponse.optJSONArray("data") ?: JSONArray()
                        val models = mutableListOf<Pair<String, String>>()
                        for (i in 0 until dataArray.length()) {
                            val obj = dataArray.optJSONObject(i) ?: continue
                            val id = obj.optString("id")
                            if (id.isNotBlank()) {
                                models.add(id to id)
                            }
                        }
                        return@withContext Result.success(models)
                    }

                    if (fallbackChatTestOnModelsUnsupported && response.code in setOf(404, 405, 501)) {
                        return@withContext testViaMinimalChat(apiKey, model)
                    }

                    Result.failure(mapHttpError(response.code))
                }
            } catch (e: java.net.UnknownHostException) {
                Result.failure(Exception("无法连接服务器，请检查网络或接口地址"))
            } catch (e: java.net.SocketTimeoutException) {
                Result.failure(Exception("连接超时"))
            } catch (e: java.net.ConnectException) {
                Result.failure(Exception("服务器连接失败"))
            } catch (e: IllegalArgumentException) {
                Result.failure(Exception("Base URL 格式无效，请检查地址"))
            } catch (e: Exception) {
                Result.failure(Exception("网络错误: ${e.message}"))
            }
        }

    private fun testViaMinimalChat(apiKey: String, model: String): Result<List<Pair<String, String>>> {
        if (model.isBlank()) {
            return Result.failure(Exception("该接口不支持 /models 查询，请先填写模型 ID 后再测试连接"))
        }
        val probeBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "hi")
                })
            })
            put(maxTokensParamName, 16)
        }
        val requestBuilder = Request.Builder()
            .url(resolveChatCompletionsUrl())
            .addHeader("Content-Type", "application/json")
            .post(probeBody.toString().toRequestBody(JSON_MEDIA_TYPE))
        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            return if (response.isSuccessful) {
                Result.success(emptyList())
            } else {
                Result.failure(mapHttpError(response.code))
            }
        }
    }

    override suspend fun chatWithTools(
        apiKey: String,
        model: String,
        messages: JSONArray,
        tools: JSONArray?
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                if (tools != null && tools.length() > 0) {
                    put("tools", tools)
                }
                put("temperature", 0.3)
                put(maxTokensParamName, 4096)
                decorateRequestBody(this)
            }
            executeChatPost(apiKey, requestBody)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun generateSubTasks(
        apiKey: String,
        model: String,
        context: SubTaskGenerationContext,
        imageBases64: List<String>?
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = SubTaskGenerationSupport.buildSystemPrompt(context)
            val promptText = if (context.refinementFeedback.isNotBlank()) {
                "请根据任务详情和我的微调要求重新输出优化后的子任务 JSON 数组。"
            } else {
                "请帮我结合任务详情与当前进度进行子任务拆解。"
            }

            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(buildUserMessage(promptText, imageBases64))
                })
                put("temperature", 0.4)
                put(maxTokensParamName, 1200)
                decorateRequestBody(this)
            }

            val jsonResponse = executeChatPost(apiKey, requestBody)
                .getOrElse { return@withContext Result.failure(it) }
            val content = extractAssistantContent(jsonResponse).trim()

            SubTaskGenerationSupport.parseAndCleanSubTasks(
                rawContent = content,
                existingSubTasks = context.existingSubTasks,
                mode = context.mode
            )
        } catch (e: org.json.JSONException) {
            Result.failure(Exception("子任务 JSON 解析失败: ${e.message}", e))
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("子任务生成失败：网络连接失败，请检查网络设置", e))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("子任务生成失败：请求超时，请稍后重试", e))
        } catch (e: java.net.ConnectException) {
            Result.failure(Exception("子任务生成失败：无法连接到服务器，请稍后重试", e))
        } catch (e: Exception) {
            Result.failure(Exception("子任务生成失败: ${e.message}", e))
        }
    }

    private fun executeChatPost(apiKey: String, requestBody: JSONObject): Result<JSONObject> {
        val requestBuilder = Request.Builder()
            .url(resolveChatCompletionsUrl())
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val responseBody = response.body?.string()
            if (!response.isSuccessful) {
                val errorMessage = if (!responseBody.isNullOrEmpty()) {
                    "API 请求失败: ${response.code} - $responseBody"
                } else {
                    "API 请求失败: ${response.code} ${response.message}"
                }
                return Result.failure(Exception(errorMessage))
            }
            if (responseBody.isNullOrBlank()) {
                return Result.failure(Exception("响应为空"))
            }
            return Result.success(JSONObject(responseBody))
        }
    }

    private fun buildUserMessage(text: String, imageBases64: List<String>?): JSONObject {
        return JSONObject().apply {
            put("role", "user")
            if (!imageBases64.isNullOrEmpty()) {
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", text)
                    })
                    imageBases64.forEach { base64 ->
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$base64")
                            })
                        })
                    }
                })
            } else {
                put("content", text)
            }
        }
    }

    private fun extractAssistantContent(jsonResponse: JSONObject): String {
        val message = jsonResponse
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
        if (message.isNull("content")) return ""
        return message.optString("content", "")
    }

    private fun parseJsonToTasks(
        jsonString: String,
        originalVoiceText: String,
        categories: List<Category>
    ): List<Task> {
        val tasks = mutableListOf<Task>()
        try {
            val cleanJson = stripCodeFences(jsonString)
            val jsonArray = JSONArray(cleanJson)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val title = obj.getString("title")
                val startStr = obj.getString("startTime")
                val endStr = obj.getString("endTime")
                val typeStr = obj.optString("type", "WORK")
                val description = obj.optString("description", "")
                val destination = obj.optString("destination", "").takeIf { it.isNotBlank() }

                val startTime = dateFormat.parse(startStr)?.time ?: System.currentTimeMillis()
                val endTime = dateFormat.parse(endStr)?.time ?: (startTime + 3600000)

                val matchedCategory = categories.find {
                    it.name.equals(typeStr, ignoreCase = true)
                } ?: categories.firstOrNull { it.isDefault }

                val categoryId = matchedCategory?.id ?: 1L
                val legacyType = when (typeStr.uppercase()) {
                    "WORK", "工作" -> TaskType.WORK
                    "LIFE", "生活" -> TaskType.LIFE
                    "STUDY", "学习" -> TaskType.STUDY
                    "URGENT", "紧急" -> TaskType.URGENT
                    else -> TaskType.WORK
                }

                val task = Task(
                    title = title,
                    description = description.takeIf { it.isNotBlank() } ?: "",
                    startTime = startTime,
                    deadline = endTime,
                    type = legacyType,
                    categoryId = categoryId,
                    originalVoiceText = originalVoiceText
                )
                task.parsedDestination = destination
                tasks.add(task)
            }
        } catch (e: org.json.JSONException) {
            throw Exception("AI 返回格式错误，请重试: ${e.message}", e)
        } catch (e: Exception) {
            throw Exception("任务解析失败: ${e.message}", e)
        }
        return tasks
    }

    private fun stripCodeFences(raw: String): String {
        return raw.replace("```json", "")
            .replace("```", "")
            .trim()
    }

    private fun mapHttpError(code: Int): Exception {
        return when (code) {
            401 -> Exception("API Key 无效")
            403 -> Exception("API Key 权限不足")
            429 -> Exception("请求过于频繁")
            else -> Exception("服务器错误: $code")
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
