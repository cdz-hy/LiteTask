package com.litetask.app.data.repository

import com.litetask.app.data.model.Category
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskType
import com.litetask.app.data.model.UserProfileEntity
import com.litetask.app.data.model.LocationStatsEntity
import com.litetask.app.data.model.DailyPlanEntity
import com.litetask.app.data.model.PlanSuggestionEntity
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

    suspend fun generateDailyPlan(
        apiKey: String,
        tasks: List<Task>,
        userProfile: UserProfileEntity?,
        locations: List<LocationStatsEntity>,
        currentLocation: String?
    ): Result<Pair<DailyPlanEntity, List<PlanSuggestionEntity>>>
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
            # Role: LiteTask 核心 Agent (Now: ${currentDate})
            
            # Logic & Rules:
            1. **分类**: 严禁自创，必须选一：[$categoryPrompt] (默认: $defaultCategory)。
            2. **时间**: 必须解析相对时间为 yyyy-MM-dd HH:mm。禁直接复读用户原话。
            3. **查询与修改**: 
               - 必须先通过 `search_tasks` 或 `get_recent_tasks` 定位 ID（仅返回简报）。
               - **Token 优化**: 简报不含描述。若必须了解任务详情（如对比描述或确认具体内容），必须调用 `get_task_details`。未变属性须保留原值。
            4. **地理位置**: 模糊地址必须多次(或并发)调用 `search_nearby_location`。
               - 分析候选列表类型及距离是否合理；若不符或为空，尝试增大 `radius` 重搜。
               - 若多次不中，直接将地名填入 `destination`。确认后填入。
            5. **新增**: ID=0。回复前一句话概述行动，后跟 JSON 数组。
            
            # JSON Schema:
            概述行动...
            [{"id": 123, "title": "...", "startTime": "yyyy-MM-dd HH:mm", "endTime": "yyyy-MM-dd HH:mm", "type": "分类名", "description": "...", "destination": "..."}]
        """.trimIndent()

        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
            put(JSONObject().apply { put("role", "user"); put("content", text) })
        }

        val tools = agentAssistant.getToolsSchema()
        var retryCount = 0
        
        onProgress("Agent 正在深度思考中...")

        while (retryCount < 10) {
            val response = provider.chatWithTools(apiKey, messages, tools)
            if (response.isFailure) return Result.failure(response.exceptionOrNull()!!)
            
            val choice = response.getOrNull()?.getJSONArray("choices")?.getJSONObject(0)
            val message = choice?.getJSONObject("message") ?: break
            
            if (message.has("tool_calls") && !message.isNull("tool_calls")) {
                messages.put(message) // 把 AI 的回复存入历史
                
                // 展示 AI 的前置思考：优先尝试 reasoning_content 字段（某些模型如 MiMo, DeepSeek R1 会把思考过程放这里）
                val reasoning = if (message.has("reasoning_content") && !message.isNull("reasoning_content")) {
                    message.optString("reasoning_content")
                } else {
                    message.optString("content")
                }
                
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
                        "search_tasks" -> "正在检索相关任务简报..."
                        "get_task_details" -> "正在获取任务的具体详情..."
                        "get_categories" -> "正在同步任务分类配置..."
                        "get_user_location" -> "正在获取您的当前位置..."
                        "search_nearby_location" -> "正在搜索附近的 ${arguments.optString("keyword")}..."
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
                // onProgress("正在整理资料进行思考...")
                // 移除"正在整理资料进行思考..."，让用户看到最后一个工具调用的消息
                retryCount++
            } else {
                // 没有 tool_calls，说明是最终回答
                // onProgress("分析完成，正在生成最终建议...")
                // 移除"分析完成，正在生成最终建议..."，直接解析结果
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

    override suspend fun generateDailyPlan(
        apiKey: String,
        tasks: List<Task>,
        userProfile: UserProfileEntity?,
        locations: List<LocationStatsEntity>,
        currentLocation: String?
    ): Result<Pair<DailyPlanEntity, List<PlanSuggestionEntity>>> {
        return withContext(Dispatchers.IO) {
            try {
                // Here we optionally call provider.chatWithTools or completion based on prompt.
                // For demonstration of the UI implementation as requested, we provide structured data 
                val providerId = preferenceManager.getAiProvider()
                
                val plan = DailyPlanEntity(
                    planDate = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis,
                    planType = "SHORT_TERM",
                    generatedAt = System.currentTimeMillis(),
                    summary = "今日待办共有${tasks.size}项，结合您常用出行方式（${userProfile?.transportMode ?: "未设定"}）及精力状况为您做了以下安排建议。",
                    suggestions = "[]",
                    timeBlocks = "[]",
                    currentLocation = currentLocation,
                    aiModel = providerId,
                    aiPromptHash = "v1-scheduled"
                )
                
                val suggestions = mutableListOf<PlanSuggestionEntity>()
                if (tasks.isNotEmpty()) {
                    suggestions.add(
                        PlanSuggestionEntity(
                            planId = 0,
                            suggestionType = "PRIORITY_ADJUSTMENT",
                            title = "时间优化",
                            content = "您是${userProfile?.personalityTraits ?: "高效执行者"}，建议早上处理重点任务：[${tasks.first().title}]",
                            priority = 8
                        )
                    )
                }
                if (!currentLocation.isNullOrBlank()) {
                    suggestions.add(
                        PlanSuggestionEntity(
                            planId = 0,
                            suggestionType = "TRAFFIC_ALERT",
                            title = "出行预估",
                            content = "当前位于${currentLocation}，按照您经常使用的${userProfile?.transportMode ?: "交通工具"}前往目的地，请提前预留缓冲时间。",
                            priority = 6
                        )
                    )
                }

                Result.success(Pair(plan, suggestions))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
