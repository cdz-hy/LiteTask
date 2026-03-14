package com.litetask.app.data.repository

import com.litetask.app.data.model.Category
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskType
import com.litetask.app.data.remote.ChatCompletionRequest
import com.litetask.app.data.remote.Message
import com.litetask.app.data.remote.OpenAIService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

interface AIRepository {
    suspend fun parseTasksFromText(
        apiKey: String, 
        text: String,
        onProgress: (String) -> Unit = {}
    ): Result<List<Task>>

    suspend fun generateSubTasks(
        task: Task,
        additionalContext: String = "",
        onProgress: (String) -> Unit = {}
    ): Result<List<String>>
}

@Singleton
class AIRepositoryImpl @Inject constructor(
    private val preferenceManager: com.litetask.app.data.local.PreferenceManager,
    private val aiProviderFactory: com.litetask.app.data.ai.AIProviderFactory,
    private val categoryRepository: CategoryRepository,
    private val taskRepository: TaskRepositoryImpl,
    private val agentAssistant: com.litetask.app.data.ai.AIAgentAssistant
) : AIRepository {

    override suspend fun parseTasksFromText(
        apiKey: String, 
        text: String,
        onProgress: (String) -> Unit
    ): Result<List<Task>> {
        val finalKey = if (apiKey.isNotBlank() && apiKey != "DEMO_KEY") {
            apiKey
        } else {
            preferenceManager.getApiKey()
        }
        
        if (finalKey.isNullOrBlank()) {
            return Result.failure(Exception("未设置 API Key，请在设置中配置"))
        }
        
        val providerId = preferenceManager.getAiProvider()
        val categories = categoryRepository.getAllCategoriesSync()
        val provider = aiProviderFactory.getProvider(providerId)

        return withContext(Dispatchers.IO) {
            try {
                if (preferenceManager.isAiAgentEnabled()) {
                    onProgress("准备启动 Agent 模式...")
                    runAgentProcess(provider, finalKey, text, categories, onProgress)
                } else {
                    onProgress("AI 正在分析任务内容...")
                    provider.parseTasksFromText(finalKey, text, categories)
                }
            } catch (e: Exception) {
                Result.failure(Exception("AI 解析失败: ${e.message}", e))
            }
        }
    }

    private suspend fun runAgentProcess(
        provider: com.litetask.app.data.ai.AIProvider,
        apiKey: String,
        text: String,
        categories: List<Category>,
        onProgress: (String) -> Unit
    ): Result<List<Task>> {
        val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        // 提取分类列表和默认分类名
        val categoryPrompt = categories.joinToString(" | ") { it.name }
        val defaultCategory = categories.firstOrNull { it.isDefault }?.name ?: "工作"

        val systemPrompt = """
            # Role: LiteTask 智能日程核心 Agent
            # Context: Current Time = ${currentDate}
            
            # Logic & Rules (MUST FOLLOW):
            1. **分类 (Categories)**: 
               - 必须严格从以下列表中选择一个：[$categoryPrompt]。
               - 严禁创造新分类。如果不确定，请使用默认分类：$defaultCategory。
            2. **时间处理 (Time Precision)**:
               - 必须将“明天”、“下周”、“下午三点”等所有相对/模糊时间转换为 yyyy-MM-dd HH:mm 格式。
               - **严禁**直接返回用户原话。必须基于 context 里的 $currentDate 进行偏移计算。
               - 如果用户只说了一个点（如“下午3点”），通常设为 `endTime`，`startTime` 为当前。
            3. **修改任务 (Modification)**:
               - 必须先调用 `search_tasks` 获取 ID 和现有详情。
               - **核心要求**: 在修改任务时，若用户未明确要求修改某项属性（如描述、分类），请务必**保留**工具返回的原始值。严禁随意改回默认。
            4. **新增任务 (Insertion)**:
               - 若判定为新日程，ID 设为 0。
            
            # JSON Schema (Final Response):
            必须先用一句话概述你的操作理由，然后紧跟 JSON 数组。
            [{"id": 123, "title": "...", "startTime": "yyyy-MM-dd HH:mm", "endTime": "yyyy-MM-dd HH:mm", "type": "分类名", "description": "...", "destination": "..."}]
        """.trimIndent()

        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
            put(JSONObject().apply { put("role", "user"); put("content", text) })
        }

        val tools = agentAssistant.getToolsSchema()
        var retryCount = 0
        
        onProgress("Agent 正在深度思考中...")

        while (retryCount < 5) {
            val response = provider.chatWithTools(apiKey, messages, tools)
            if (response.isFailure) return Result.failure(response.exceptionOrNull()!!)
            
            val choice = response.getOrNull()?.getJSONArray("choices")?.getJSONObject(0)
            val message = choice?.getJSONObject("message") ?: break
            
            if (message.has("tool_calls")) {
                messages.put(message) // 把 AI 的回复存入历史
                
                // 展示 AI 的前置思考
                val reasoning = message.optString("content")
                if (reasoning.isNotBlank()) {
                    onProgress(reasoning)
                }

                val toolCalls = message.getJSONArray("tool_calls")
                
                for (i in 0 until toolCalls.length()) {
                    val call = toolCalls.getJSONObject(i)
                    val function = call.getJSONObject("function")
                    val name = function.getString("name")
                    val arguments = JSONObject(function.getString("arguments"))
                    
                    val progressMsg = when(name) {
                        "get_recent_tasks" -> "正在查阅您的最近任务列表..."
                        "search_tasks" -> "正在搜索关键词: ${arguments.optString("keyword")}..."
                        "get_categories" -> "正在同步任务分类配置..."
                        "get_user_location" -> "正在获取您的当前位置..."
                        else -> "正在调用工具: $name..."
                    }
                    onProgress(progressMsg)
                    
                    val result = agentAssistant.handleToolCall(name, arguments)
                    
                    messages.put(JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", call.getString("id"))
                        put("content", result)
                    })
                }
                onProgress("正在整理资料进行思考...")
                retryCount++
            } else {
                // 没有 tool_calls，说明是最终回答
                onProgress("分析完成，正在生成最终建议...")
                val finalContent = message.getString("content")
                val tasks = agentAssistant.parseAgentOutput(finalContent, text, categories)
                return Result.success(tasks)
            }
        }
        
        return Result.failure(Exception("Agent 思考次数过多或循环调用"))
    }

    override suspend fun generateSubTasks(
        task: Task,
        additionalContext: String,
        onProgress: (String) -> Unit
    ): Result<List<String>> {
        val apiKey = preferenceManager.getApiKey()
        if (apiKey.isNullOrBlank()) {
            return Result.failure(Exception("未设置 API Key，请在设置中配置"))
        }

        val providerId = preferenceManager.getAiProvider()
        val provider = aiProviderFactory.getProvider(providerId)

        return withContext(Dispatchers.IO) {
            try {
                onProgress("AI 正在根据您的要求拆解子任务...")
                provider.generateSubTasks(apiKey, task, additionalContext)
            } catch (e: Exception) {
                Result.failure(Exception("子任务生成失败: ${e.message}", e))
            }
        }
    }
}
