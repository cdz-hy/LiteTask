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
   - 明确起止: 仅在明确"从X到Y"时设定具体区间。
   - 无时间: endTime = Now + 24h。
3. **分类**: WORK | LIFE | STUDY | URGENT
4. **描述生成**: `description` 必须基于用户原始输入生成真实有意义的简要描述:
   - 提取并扩展用户提到的具体细节（地点、人物、数量、方式等）
   - 若用户输入简短，合理推断任务的目的或上下文
   - 禁止生成空洞模板化内容，每个描述必须与该任务直接相关
   - 长度控制在 10-50 字
5. **子任务逻辑**: 
   - 仅对需要拆解的复杂任务生成子任务
   - 简单任务（如"买菜"、"开会"等）不需要子任务
   - 子任务必须与主任务紧密相关，具体可执行

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
                    val errorBody = response.body?.string()
                    val errorMessage = if (errorBody != null && errorBody.isNotEmpty()) {
                        "API 请求失败: ${response.code} - $errorBody"
                    } else {
                        "API 请求失败: ${response.code} ${response.message}"
                    }
                    return@withContext Result.failure(Exception(errorMessage))
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
            } catch (e: java.net.ConnectException) {
                Result.failure(Exception("服务器连接失败"))
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
        } catch (e: org.json.JSONException) {
            e.printStackTrace()
            // 在这里可以添加错误处理逻辑，比如记录错误或返回默认值
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return tasks
    }
    
    override fun getProviderName(): String = "DeepSeek V3.2"
    
    /**
     * 根据任务信息生成子任务
     */
    suspend fun generateSubTasks(
        apiKey: String, 
        task: Task, 
        additionalContext: String = ""
    ): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            try {
                val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                
                val systemPrompt = """
# Role: 任务拆解专家
# Context: Now = $currentDate
# Goal: 将复杂任务拆解为具体可执行的子任务步骤

# Task Info
- 任务标题: ${task.title}
- 任务描述: ${task.description ?: "无"}
- 任务类型: ${task.type.name}
- 开始时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(task.startTime))}
- 截止时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(task.deadline))}

# Rules
1. **拆解原则**: 每个子任务应该是具体、可执行、可验证的行动步骤
2. **时间考虑**: 考虑任务的时间跨度，合理安排子任务的先后顺序
3. **实用性**: 子任务必须与主任务紧密相关，对完成主任务有实际帮助
4. **意义性**: 子任务需要结合任务具体内容，合适合理，避免空洞抽象
5. **数量控制**: 
   - 复杂任务: 生成 3-8 个子任务
   - 简单任务: 如果任务本身简单（如"买菜"、"开会"等），返回空列表
6. **格式要求**: 每行一个子任务，使用简洁明确的动词开头
7. **关联性**: 每个子任务必须与主任务内容紧密相关，不能脱离主任务

# Additional Context
${if (additionalContext.isNotBlank()) additionalContext else "用户未提供额外说明"}

# Output
直接输出子任务列表，每行一个，无需编号或格式化：
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
                            put("content", "请为这个任务生成合适的子任务步骤，如果任务本身简单无需拆解则返回空列表")
                        })
                    })
                    put("temperature", 0.7)
                    put("max_tokens", 800)
                }
                
                val request = Request.Builder()
                    .url(baseUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    val errorMessage = if (errorBody != null && errorBody.isNotEmpty()) {
                        "API 请求失败: ${response.code} - $errorBody"
                    } else {
                        "API 请求失败: ${response.code} ${response.message}"
                    }
                    return@withContext Result.failure(Exception(errorMessage))
                }
                
                val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("响应为空"))
                val jsonResponse = JSONObject(responseBody)
                
                val content = jsonResponse
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                
                // 解析子任务列表
                val subTasks = content.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("-") }
                    .map { 
                        // 移除可能的编号前缀
                        it.replace(Regex("^\\d+\\.\\s*"), "")
                          .replace(Regex("^[•·]\\s*"), "")
                          .trim()
                    }
                    .filter { it.isNotBlank() }
                    .takeIf { it.isNotEmpty() } ?: emptyList() // 如果列表为空，返回空列表而不是创建任务
                
                Result.success(subTasks)
                
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
    }
}