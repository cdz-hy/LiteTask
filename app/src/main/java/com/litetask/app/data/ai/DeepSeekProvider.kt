package com.litetask.app.data.ai

import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskType
import com.litetask.app.data.model.Category
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
    
    override suspend fun parseTasksFromText(apiKey: String, text: String, categories: List<Category>): Result<List<Task>> {
        return withContext(Dispatchers.IO) {
            try {
                val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                
                // 动态构建分类列表字符串
                val categoryPrompt = if (categories.isNotEmpty()) {
                    categories.joinToString(" | ") { it.name }
                } else {
                    "工作 | 生活 | 学习 | 紧急"
                }

                val defaultCategoryName = categories.firstOrNull { it.isDefault }?.name ?: (categories.firstOrNull()?.name ?: "工作")

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
                
                val tasks = parseJsonToTasks(content, text, categories)
                Result.success(tasks) // Return result
                
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
    
    private fun parseJsonToTasks(jsonString: String, originalVoiceText: String, categories: List<Category>): List<Task> {
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
                val destination = obj.optString("destination", "").takeIf { it.isNotBlank() }
                
                val startTime = dateFormat.parse(startStr)?.time ?: System.currentTimeMillis()
                val endTime = dateFormat.parse(endStr)?.time ?: (startTime + 3600000)
                
                // Match Category by Name
                // 从 AI 返回的 typeStr 查找对应的 Category
                val matchedCategory = categories.find { 
                    it.name.equals(typeStr, ignoreCase = true) 
                } ?: categories.firstOrNull { it.isDefault } // 默认使用第一个默认分类
                
                val categoryId = matchedCategory?.id ?: 1L
                
                // 为了兼容旧的 Enum，尝试映射（仅用于调试或旧逻辑，未来可废弃）
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
                    type = legacyType, // Deprecated
                    categoryId = categoryId, // New Dynamic ID
                    originalVoiceText = originalVoiceText  // 保存原始语音文本
                )
                // 设置解析出的目的地（临时字段）
                task.parsedDestination = destination
                tasks.add(task)
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
                
                val userInstruction = if (additionalContext.isNotBlank()) {
                    "用户补充说明 (必须优先遵循): $additionalContext"
                } else {
                    "用户未提供额外说明，请根据任务标题和描述自由发挥。"
                }

                val systemPrompt = """
# Role: LiteTask 智能日程规划师
# Context: Now = $currentDate
# Goal: 将复杂任务拆解为 3-6 个具体、可执行的子任务步骤，并以 JSON 数组格式返回。

# Task Details
- 标题: ${task.title}
- 描述: ${task.description?.ifBlank { "无" } ?: "无"}
- 类型: ${task.type.name}
- 时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(task.startTime))} 至 ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(task.deadline))}

# User Instruction
$userInstruction

# Rules
1. **执行性**: 每个子任务必须是可直接执行的动作（"动词 + 名词"结构），如"撰写大纲"、"购买材料"。
2. **相关性**: 子任务必须服务于主任务的完成，如果用户提供了补充说明，请严格按照说明的方向进行拆解。
3. **逻辑性**: 按时间先后顺序排列步骤。
4. **简洁性**: 每个步骤不超过 15 个字。
5. **完整性**: 步骤应覆盖任务的关键节点。

# Output Format
仅返回一个纯 JSON 字符串数组，不要包含 Markdown 标记或其他文本。
示例: ["第一步内容", "第二步内容", "第三步内容"]
                """.trimIndent()
                
                val requestBody = JSONObject().apply {
                    put("model", "deepseek-chat")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                    })
                    put("temperature", 0.7)
                    put("max_tokens", 800)
                    // 强制 JSON 模式（如果模型支持）
                    put("response_format", JSONObject().apply { put("type", "json_object") }) 
                }
                
                // DeepSeek V3 可能还不完全支持 response_format json_object，所以我们在 Prompt 里也强调了 JSON
                // 为了兼容性，我们暂时移除 response_format 参数，完全依赖 Prompt 约束
                requestBody.remove("response_format") 

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
                    .trim()
                
                // 解析 JSON 数组
                val subTasks = try {
                    // 尝试清理 Markdown 代码块
                    val cleanContent = content
                        .replace("```json", "")
                        .replace("```", "")
                        .trim()
                    
                    val jsonArray = JSONArray(cleanContent)
                    val list = mutableListOf<String>()
                    for (i in 0 until jsonArray.length()) {
                        list.add(jsonArray.getString(i))
                    }
                    list
                } catch (e: Exception) {
                    // 如果 JSON 解析失败，尝试回退到换行符分割（容错处理）
                    content.split("\n")
                        .map { it.trim() }
                        .filter { it.isNotBlank() && !it.startsWith("[") && !it.startsWith("]") }
                        .map { it.replace(Regex("^\\d+\\.\\s*"), "").replace(Regex("^[•·-]\\s*"), "") }
                }

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