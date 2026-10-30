package com.litetask.app.data.repository

import android.content.Context
import com.litetask.app.data.ai.ScheduleAdvicePromptSupport
import com.litetask.app.data.ai.SubTaskGenerationContext
import com.litetask.app.data.ai.SubTaskGenerationMode
import com.litetask.app.data.local.UserProfileDao
import com.litetask.app.data.model.Category
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskComponent
import com.litetask.app.data.model.sortedByOrder
import com.litetask.app.util.AttachmentContentReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        imageBases64: List<String>? = null,
        onProgress: (String) -> Unit = {}
    ): Result<List<Task>>

    suspend fun generateSubTasks(
        task: Task,
        additionalContext: String = "",
        imageBases64: List<String>? = null,
        mode: SubTaskGenerationMode = SubTaskGenerationMode.APPEND_NEXT,
        previousGeneratedSteps: List<String> = emptyList(),
        refinementFeedback: String = "",
        onProgress: (String) -> Unit = {}
    ): Result<List<String>>
}

@Singleton
class AIRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManager: com.litetask.app.data.local.PreferenceManager,
    private val aiProviderFactory: com.litetask.app.data.ai.AIProviderFactory,
    private val categoryRepository: CategoryRepository,
    private val taskRepository: TaskRepositoryImpl,
    private val agentAssistant: com.litetask.app.data.ai.AIAgentAssistant,
    private val locationTracker: LocationTracker,
    private val userProfileDao: UserProfileDao
) : AIRepository {

    override suspend fun parseTasksFromText(
        apiKey: String, 
        text: String,
        imageBases64: List<String>?,
        onProgress: (String) -> Unit
    ): Result<List<Task>> {
        val providerId = preferenceManager.getAiProvider()
        val finalKey = if (apiKey.isNotBlank() && apiKey != "DEMO_KEY") {
            apiKey
        } else {
            preferenceManager.getApiKey(providerId).orEmpty()
        }

        preferenceManager.getAiConfigError(finalKey)?.let { errorMsg ->
            return Result.failure(Exception(errorMsg))
        }

        val modelId = preferenceManager.getAiModel()
        val categories = categoryRepository.getAllCategoriesSync()
        val provider = aiProviderFactory.getProvider(providerId)

        return withContext(Dispatchers.IO) {
            try {
                locationTracker.clearPendingOrigin()
                if (preferenceManager.isAiAgentEnabled()) {
                    onProgress("准备启动智能助手深度模式...")
                    runAgentProcess(provider, finalKey, modelId, text, categories, imageBases64, onProgress)
                } else {
                    onProgress("AI 正在分析任务内容...")
                    provider.parseTasksFromText(finalKey, modelId, text, categories, imageBases64)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(Exception("AI 解析失败: ${e.message}", e))
            }
        }
    }

    private suspend fun runAgentProcess(
        provider: com.litetask.app.data.ai.AIProvider,
        apiKey: String,
        modelId: String,
        text: String,
        categories: List<Category>,
        imageBases64: List<String>?,
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
            4. **地理位置**: 仅在任务内容确实包含位移语义（如“去”、“在”、“到”、“附近”）时调用 `get_user_location` 或 `search_nearby_location`。严禁在解析常规静态任务（如“写文档”、“开会”）时盲目调用位置工具。
               - 多个模糊地址时可多次(或并发)调用 `search_nearby_location`。
               - 分析候选列表类型及距离是否合理；若不符或为空，尝试增大 `radius` 重搜。
               - 若多次不中，直接将地名填入 `destination`。确认后填入。
            5. **组件抽取 (按需附加)**:
               - 若出现URL/会议号/取件码/车次票务/核销码，可附加 `links` 与 `passes` 数组。
${com.litetask.app.data.ai.AiTaskComponentParser.PROMPT_COMPONENT_RULES}
            6. **极简调用**: 严禁无目的调用工具。如果当前已有足够信息支持解析，不要为了查询而查询。
            7. **新增**: ID=0。回复前一句话概述行动，后跟 JSON 数组。
            
            # JSON Schema:
            概述行动...
            [{"id": 123, "title": "...", "startTime": "yyyy-MM-dd HH:mm", "endTime": "yyyy-MM-dd HH:mm", "type": "分类名", "description": "...", "destination": "...", ${com.litetask.app.data.ai.AiTaskComponentParser.PROMPT_JSON_FIELDS}}]
        """.trimIndent()

        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
            put(JSONObject().apply {
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
            })
        }

        val tools = agentAssistant.getToolsSchema()
        var retryCount = 0
        
        // 用于追踪出发地信息
        var currentOriginLng: Double? = null
        var currentOriginLat: Double? = null
        var currentOriginName: String? = null
        var currentOriginAddress = ""
        
        onProgress("Agent 正在深度思考中...")

        while (retryCount < 10) {
            val response = provider.chatWithTools(apiKey, modelId, messages, tools)
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
                val originMutex = Mutex()

                // 并行执行所有工具调用
                coroutineScope {
                    val jobs = (0 until toolCalls.length()).map { i ->
                        val call = toolCalls.getJSONObject(i)
                        val function = call.getJSONObject("function")
                        val name = function.getString("name")
                        val arguments = JSONObject(function.getString("arguments"))
                        val callId = call.getString("id")

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

                        async {
                            val result = agentAssistant.handleToolCall(name, arguments)

                            // 如果是获取用户位置，进行逆地理编码并保存出发地（线程安全）
                            if (name == "get_user_location" && result.contains(",")) {
                                originMutex.withLock {
                                    try {
                                        val coords = result.split(",")
                                        if (coords.size == 2) {
                                            val lng = coords[0].toDoubleOrNull()
                                            val lat = coords[1].toDoubleOrNull()
                                            if (lng != null && lat != null) {
                                                currentOriginLng = lng
                                                currentOriginLat = lat
                                                val regeo = locationTracker.reverseGeocodeDetailed(lng, lat)
                                                currentOriginName = regeo.name
                                                currentOriginAddress = regeo.address
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }

                            Pair(callId, result)
                        }
                    }
                    // 等待全部完成，收集结果
                    jobs.forEach { deferred ->
                        val (callId, result) = deferred.await()
                        messages.put(JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", callId)
                            put("content", result)
                        })
                    }
                }
                retryCount++
            } else {
                // 没有 tool_calls，说明是最终回答
                // onProgress("分析完成，正在生成最终建议...")
                // 移除"分析完成，正在生成最终建议..."，直接解析结果
                val finalContent = message.getString("content")
                val tasks = agentAssistant.parseAgentOutput(finalContent, text, categories)
                
                // 暂存出发地，等待用户在确认弹窗点击保存后正式写入足迹，避免误取消污染地点统计
                val savedLng = currentOriginLng
                val savedLat = currentOriginLat
                val savedName = currentOriginName
                if (savedLng != null && savedLat != null && !savedName.isNullOrBlank()) {
                    locationTracker.stagePendingOrigin(savedName, savedLng, savedLat, currentOriginAddress)
                }

                return Result.success(tasks)
            }
        }
        
        return Result.failure(Exception("Agent 思考次数过多或循环调用"))
    }

    override suspend fun generateSubTasks(
        task: Task,
        additionalContext: String,
        imageBases64: List<String>?,
        mode: SubTaskGenerationMode,
        previousGeneratedSteps: List<String>,
        refinementFeedback: String,
        onProgress: (String) -> Unit
    ): Result<List<String>> {
        val providerId = preferenceManager.getAiProvider()
        val apiKey = preferenceManager.getApiKey(providerId).orEmpty()
        preferenceManager.getAiConfigError(apiKey)?.let { errorMsg ->
            return Result.failure(Exception(errorMsg))
        }

        val modelId = preferenceManager.getAiModel()
        val provider = aiProviderFactory.getProvider(providerId)

        return withContext(Dispatchers.IO) {
            try {
                onProgress("正在读取任务详情、已有步骤与关联信息...")
                val detail = taskRepository.getTaskByIdSync(task.id)
                val latestTask = detail?.task ?: task
                val categoryName = detail?.category?.name
                    ?: categoryRepository.getAllCategoriesSync().firstOrNull { it.id == latestTask.categoryId }?.name
                val existingSubTasks = detail?.subTasks?.sortedByOrder().orEmpty()
                val components = detail?.components?.mapNotNull { runCatching { it.toTaskComponent() }.getOrNull() }.orEmpty()

                val routeDestination = components
                    .filterIsInstance<TaskComponent.AMapComponent>()
                    .firstOrNull()
                    ?.data
                    ?.let { route ->
                        val addr = route.endAddress?.takeIf { it.isNotBlank() }
                        if (addr != null) "${route.endName}（$addr）" else route.endName
                    }

                val fileAttachments = components
                    .filterIsInstance<TaskComponent.FileComponent>()
                    .map { it.data }
                val attachmentNames = fileAttachments
                    .map { it.effectiveDisplayName() }
                    .filter { it.isNotBlank() }

                val remainingImageSlots = (3 - (imageBases64?.size ?: 0)).coerceAtLeast(0)
                val (attachmentTextExcerpts, attachmentVisualImages) = if (fileAttachments.isNotEmpty()) {
                    AttachmentContentReader.extractAiContextFromAttachments(
                        context = context,
                        attachments = fileAttachments,
                        includeVisualMedia = preferenceManager.isMultimodalEnabled(),
                        maxTextFiles = 4,
                        maxImages = remainingImageSlots
                    )
                } else {
                    emptyList<Pair<String, String>>() to emptyList<String>()
                }

                val combinedImages = (imageBases64.orEmpty() + attachmentVisualImages)
                    .distinct()
                    .takeIf { it.isNotEmpty() }

                val userProfile = runCatching { userProfileDao.getLatestUserProfile() }.getOrNull()
                val profileSummary = userProfile?.let {
                    ScheduleAdvicePromptSupport.formatProfileSummaryText(it, emptyList())
                }

                val contextSummaryParts = buildList {
                    if (existingSubTasks.isNotEmpty()) {
                        add("${existingSubTasks.size}条已有步骤")
                    }
                    if (!routeDestination.isNullOrBlank()) {
                        add("目的地「$routeDestination」")
                    }
                    if (attachmentTextExcerpts.isNotEmpty()) {
                        add("${attachmentTextExcerpts.size}份附件文档")
                    }
                    if (!combinedImages.isNullOrEmpty()) {
                        add("${combinedImages.size}张参考图/PDF页")
                    }
                }
                if (contextSummaryParts.isNotEmpty()) {
                    onProgress("已整理上下文（${contextSummaryParts.joinToString("、")}）")
                }

                val generationContext = SubTaskGenerationContext(
                    task = latestTask,
                    categoryName = categoryName,
                    existingSubTasks = existingSubTasks,
                    routeDestination = routeDestination,
                    attachmentNames = attachmentNames,
                    attachmentTextExcerpts = attachmentTextExcerpts,
                    userProfileSummary = profileSummary,
                    additionalContext = additionalContext,
                    mode = mode,
                    previousGeneratedSteps = previousGeneratedSteps,
                    refinementFeedback = refinementFeedback,
                    hasImages = !combinedImages.isNullOrEmpty()
                )

                onProgress(
                    if (refinementFeedback.isNotBlank()) {
                        "正在由 ${provider.getProviderName()} 按微调意见优化步骤..."
                    } else {
                        "正在由 ${provider.getProviderName()} 拆解可落地的子任务..."
                    }
                )

                val result = provider.generateSubTasks(apiKey, modelId, generationContext, combinedImages)
                result.onSuccess { steps ->
                    onProgress("拆解与去重校验完成（共 ${steps.size} 个步骤）")
                }
                result
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(Exception("子任务生成失败: ${e.message}", e))
            }
        }
    }

}
