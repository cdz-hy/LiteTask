package com.litetask.app.data.ai

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
import javax.inject.Inject

/**
 * DeepSeek AI 提供商实现
 */
class DeepSeekProvider @Inject constructor() : AIProvider {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val baseUrl = "https://api.deepseek.com/v1/chat/completions"
    
    override suspend fun parseTasksFromText(apiKey: String, text: String): Result<List<Task>> {
        return withContext(Dispatchers.IO) {
            try {
                val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                
                val systemPrompt = """
# Role: LiteTask 智能日程规划师
# Context: Now = $currentDate
# Goal: 将语音输入解析为结构化 JSON Array。

# Rules
1. **拆分与纠错**: 识别多任务，修正语音/逻辑错误。无效输入返 `[]`。
2. **时间推断 (核心)**:
   - 默认/单点时间: 视为 endTime (截止)，startTime 设为 Now。
   - 明确起止: 仅在明确“从X到Y”时设定具体区间。
   - 无时间: endTime = Now + 24h。
3. **分类**: WORK | LIFE | STUDY | URGENT
4. **描述生成**: `description` 必须基于用户原始输入生成真实有意义的简要描述:
   - 提取并扩展用户提到的具体细节（地点、人物、数量、方式等）
   - 若用户输入简短，合理推断任务的目的或上下文
   - 禁止生成空洞模板化内容，每个描述必须与该任务直接相关
   - 长度控制在 10-50 字

# Output
仅返回纯 JSON 数组，无 Markdown。
[{"title":"精炼标题<20字","startTime":"yyyy-MM-dd HH:mm","endTime":"yyyy-MM-dd HH:mm","type":"分类","description":"基于输入的真实任务描述"}]
                """.trimIndent()
                
                val requestBody = JSONObject().apply {
                    put("model", "deepseek-chat")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", text)
                        })
                    })
                    put("temperature", 0.7)
                    put("max_tokens", 1000)
                }
                
                val request = Request.Builder()
                    .url(baseUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("API 请求失败: ${response.code} ${response.message}"))
                }
                
                val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("响应为空"))
                val jsonResponse = JSONObject(responseBody)
                
                val content = jsonResponse
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                
                val tasks = parseJsonToTasks(content, text)
                Result.success(tasks)
                
            } catch (e: Exception) {
                Result.failure(Exception("DeepSeek 解析失败: ${e.message}", e))
            }
        }
    }

    override suspend fun testConnection(apiKey: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                // 使用 /models 端点验证 API Key，不消耗 token
                val request = Request.Builder()
                    .url("https://api.deepseek.com/v1/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                
                when (response.code) {
                    200 -> Result.success(true)
                    401 -> Result.failure(Exception("API Key 无效"))
                    403 -> Result.failure(Exception("API Key 权限不足"))
                    429 -> Result.failure(Exception("请求过于频繁"))
                    else -> Result.failure(Exception("服务器错误: ${response.code}"))
                }
            } catch (e: java.net.UnknownHostException) {
                Result.failure(Exception("无法连接服务器，请检查网络"))
            } catch (e: java.net.SocketTimeoutException) {
                Result.failure(Exception("连接超时"))
            } catch (e: Exception) {
                Result.failure(Exception("网络错误: ${e.message}"))
            }
        }
    }
    
    private fun parseJsonToTasks(jsonString: String, originalVoiceText: String): List<Task> {
        val tasks = mutableListOf<Task>()
        try {
            // 清理可能存在的 Markdown 代码块标记
            val cleanJson = jsonString
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            val jsonArray = JSONArray(cleanJson)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val title = obj.getString("title")
                val startStr = obj.getString("startTime")
                val endStr = obj.getString("endTime")
                val typeStr = obj.optString("type", "WORK")
                val description = obj.optString("description", "")
                
                val startTime = dateFormat.parse(startStr)?.time ?: System.currentTimeMillis()
                val endTime = dateFormat.parse(endStr)?.time ?: (startTime + 3600000)
                
                val type = try {
                    TaskType.valueOf(typeStr.uppercase())
                } catch (e: Exception) {
                    TaskType.WORK
                }
                
                tasks.add(
                    Task(
                        title = title,
                        description = description.takeIf { it.isNotBlank() } ?: "",
                        startTime = startTime,
                        deadline = endTime,
                        type = type,
                        originalVoiceText = originalVoiceText  // 保存原始语音文本
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return tasks
    }
    
    override fun getProviderName(): String = "DeepSeek V3.2"
}
