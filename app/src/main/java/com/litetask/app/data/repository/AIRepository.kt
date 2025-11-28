package com.litetask.app.data.repository

import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskType
import com.litetask.app.data.remote.ChatCompletionRequest
import com.litetask.app.data.remote.Message
import com.litetask.app.data.remote.OpenAIService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

interface AIRepository {
    suspend fun parseTasksFromText(apiKey: String, text: String): Result<List<Task>>
}

@Singleton
class AIRepositoryImpl @Inject constructor(
    private val openAIService: OpenAIService,
    private val preferenceManager: com.litetask.app.data.local.PreferenceManager
) : AIRepository {

    override suspend fun parseTasksFromText(apiKey: String, text: String): Result<List<Task>> {
        // 优先使用传入的 Key，如果为空则使用存储的 Key
        val finalKey = if (apiKey.isNotBlank() && apiKey != "DEMO_KEY") apiKey else preferenceManager.getApiKey()
        
        if (finalKey.isNullOrBlank()) {
            return Result.failure(Exception("API Key not found. Please set it in Settings."))
        }

        return withContext(Dispatchers.IO) {
            try {
                val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                val prompt = """
                    Current Date: $currentDate
                    User Input: "$text"
                    
                    Extract tasks from the input. Return ONLY a JSON array. No markdown.
                    Schema:
                    [
                      {
                        "title": "String",
                        "startTime": "yyyy-MM-dd HH:mm",
                        "endTime": "yyyy-MM-dd HH:mm (default 1h if missing)",
                        "type": "WORK|LIFE|DEV|HEALTH|OTHER",
                        "location": "String (optional)"
                      }
                    ]
                """.trimIndent()

                val request = ChatCompletionRequest(
                    messages = listOf(
                        Message("system", "You are a schedule assistant. Output strict JSON."),
                        Message("user", prompt)
                    )
                )

                val response = openAIService.createChatCompletion("Bearer $finalKey", request)
                val content = response.choices.firstOrNull()?.message?.content ?: return@withContext Result.failure(Exception("Empty response"))

                val tasks = parseJsonToTasks(content)
                Result.success(tasks)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun parseJsonToTasks(jsonString: String): List<Task> {
        val tasks = mutableListOf<Task>()
        try {
            // 清理可能存在的 Markdown 代码块标记
            val cleanJson = jsonString.replace("```json", "").replace("```", "").trim()
            val jsonArray = JSONArray(cleanJson)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val title = obj.getString("title")
                val startStr = obj.getString("startTime")
                val endStr = obj.getString("endTime")
                val typeStr = obj.optString("type", "WORK")
                val location = obj.optString("location", null)

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
                        startTime = startTime,
                        deadline = endTime, // 将 endTime 替换为 deadline
                        type = type
                        // 移除 location 字段，因为新的 Task 模型中不再包含该字段
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return tasks
    }
}
