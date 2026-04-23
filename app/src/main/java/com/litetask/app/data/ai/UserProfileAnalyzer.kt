package com.litetask.app.data.ai

import com.litetask.app.data.local.TaskDao
import com.litetask.app.data.local.UserLocationDao
import com.litetask.app.data.local.AIHistoryDao
import com.litetask.app.data.local.CategoryDao
import com.litetask.app.data.local.TaskComponentDao
import com.litetask.app.data.local.UserProfileDao
import com.litetask.app.data.model.UserProfileHistoryEntity
import com.litetask.app.data.model.ComponentType
import com.litetask.app.data.local.PreferenceManager
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.json.JSONArray
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 分析步骤状态
 */
sealed class AnalysisStep {
    data class Thinking(val message: String) : AnalysisStep()
    data class CallingTool(val toolName: String, val description: String) : AnalysisStep()
    data class ToolResult(val toolName: String, val summary: String) : AnalysisStep()
    data class Reasoning(val content: String) : AnalysisStep()
    data class Complete(val result: UserProfileHistoryEntity) : AnalysisStep()
    data class Error(val message: String) : AnalysisStep()
}

@Singleton
class UserProfileAnalyzer @Inject constructor(
    private val aiProviderFactory: AIProviderFactory,
    private val preferenceManager: PreferenceManager,
    private val taskDao: TaskDao,
    private val userLocationDao: UserLocationDao,
    private val aiHistoryDao: AIHistoryDao,
    private val categoryDao: CategoryDao,
    private val taskComponentDao: TaskComponentDao,
    private val userProfileDao: UserProfileDao
) {
    
    /**
     * 分析用户画像 (优化版 Function Calling)
     */
    suspend fun analyzeUserProfile(
        onStep: (AnalysisStep) -> Unit = {}
    ): Result<UserProfileHistoryEntity> {
        val apiKey = preferenceManager.getApiKey()
        if (apiKey.isNullOrBlank()) {
            return Result.failure(Exception("未设置 API Key，请在设置中配置"))
        }
        
        if (!preferenceManager.isAiAgentEnabled()) {
            return Result.failure(Exception("AI Agent 未开启，请先在设置中启用 AI Agent 助手。"))
        }

        val providerId = preferenceManager.getAiProvider()
        val provider = aiProviderFactory.getProvider(providerId)

        return try {
            onStep(AnalysisStep.Thinking("初始化用户画像分析引擎..."))
            
            // 预先计算客观数据
            val allTasks = taskDao.getAllTasks().first()
            val completed = allTasks.count { it.isDone }
            val delayed = allTasks.count { it.isExpired || (!it.isDone && it.deadline < System.currentTimeMillis()) }
            val completionRate = if (allTasks.isNotEmpty()) completed.toDouble() / allTasks.size else 0.0
            val delayedRate = if (allTasks.isNotEmpty()) delayed.toDouble() / allTasks.size else 0.0

            val categories = categoryDao.getAllCategories().first()
            val categoryCounts = allTasks.groupBy { it.categoryId }.mapValues { it.value.size }
            val topCategoryId = categoryCounts.maxByOrNull { it.value }?.key
            val categoryFocus = categories.find { it.id == topCategoryId }?.name ?: "无明显重心"

            val allAiHistories = aiHistoryDao.getAllHistory()
            val voiceCount = allAiHistories.count { it.sourceType == com.litetask.app.data.model.AIHistorySource.VOICE }
            val textCount = allAiHistories.count { it.sourceType == com.litetask.app.data.model.AIHistorySource.TEXT }
            val inputPreference = if (voiceCount > textCount) "语音" else "文字"
            
            // 获取上一次画像作为基准
            val previousProfile = userProfileDao.getLatestUserProfile()
            val previousContext = if (previousProfile != null) {
                """
                上一期画像快照（作为演进基准）：
                - 身份: ${previousProfile.identity}
                - 行业: ${previousProfile.industry}
                - 性格: ${previousProfile.personality}
                - 拖延指数: ${previousProfile.delayIndex}/5
                - 规划能力: ${previousProfile.planAbility}/5
                - 压力承受度: ${previousProfile.stressLevel}/5
                """.trimIndent()
            } else {
                "这是首次分析，无历史画像基准。"
            }
            
            val systemPrompt = """
你是一个专业的用户画像推演 Agent 系统。你可以调用多个内置工具实时探索用户的行为轨迹、任务文本、时间模式等。
请结合工具获取到的原始数据，对用户进行深度心理与行为模式推理。

# 当前已知的客观数据
- 任务完成率: ${(completionRate * 100).toInt()}%
- 任务拖延/延期率: ${(delayedRate * 100).toInt()}%
- 核心关注领域: $categoryFocus
- 输入偏好: $inputPreference

$previousContext

# 分析维度与推理思路 (Chain of Thought)
1. **信息收集**: 优先调用工具获取用户近期的任务内容、分类分布、活跃时间和AI交互历史。
2. **身份 & 行业推理**: 深入分析任务文本中的专有名词、高频动作（如"早八"、"备课"、"发版"、"接孩子"），推断出极其具体的用户身份和行业。
3. **行为模式分析**: 
   - **出行偏好**: 结合常去地点、路线规划策略或任务文本提取交通方式。
   - **执行节奏**: 根据完成时间与截止时间的差值，判断用户是"提前规划"、"稳步推进"还是"极限踩点"。
4. **心理与能力评估**:
   - **规划能力**: 分析复杂任务是否被合理拆解（子任务使用情况）。
   - **压力承受度**: 结合近期任务密度、"紧急"任务占比，评估用户当前的压力状态。
   - **拖延指数**: 综合延期率和执行节奏给出评分（1-5级，1为极度自律，5为严重拖延）。
5. **AI 互动定制**:
   - **AI语气偏好**: 根据用户的压力水平和拖延情况定制最合适的陪伴语气（例如：高压期需要"温和鼓励"，严重拖延需要"严厉监督"，高效期适用"极简干练"）。
   - **综合性格**: 用简练、生动的一句话总结用户的核心特质（如"追求完美的实干派"、"随性自由的夜猫子"）。

# 工具调用核心原则
- **拒绝主观臆断**: 所有的推断必须有工具返回的数据支撑。
- **按需调用**: 遇到信息不足的维度，务必调用对应工具补充数据，不要为了调用而调用。

# 最终输出格式约束
当你完成了所有必要的推理，必须返回一个纯粹的 JSON 对象，不要包含任何额外的解释文本或 Markdown 标记（如 ```json）。JSON 结构必须严格如下：
{
  "identity": "具体的用户身份（如：考研学生、前端工程师、全职妈妈）",
  "industry": "具体的行业领域（如：高等教育、互联网、家庭管理）", 
  "travelPreference": "高频出行方式（如：地铁通勤、自驾）",
  "delayIndex": 3, // 1-5整数，1最自律，5最拖延
  "planAbility": 4, // 1-5整数，1规划极差，5规划极强
  "stressLevel": 3, // 1-5整数，1轻松，5压力极大
  "executionRhythm": "核心执行习惯（如：极限踩点、步步为营、随性处理）",
  "aiTone": "最适合当前状态的AI语气设定（如：温柔鼓励、严厉鞭策、幽默调侃、极简理性）",
  "personality": "综合性格特征描述（一句话，精准生动）"
}
            """.trimIndent()
            
            val messages = JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                put(JSONObject().apply { put("role", "user"); put("content", "开始分析我的画像，调用你需要的工具并给出最终结论 JSON。") })
            }
            
            val tools = buildToolDefinitions()
            
            var retryCount = 0
            val maxRetries = 15
            var finalJson: String? = null
            
            while (retryCount < maxRetries) {
                val response = provider.chatWithTools(apiKey, messages, tools)
                if (response.isFailure) return Result.failure(response.exceptionOrNull()!!)
                
                val choice = response.getOrNull()?.getJSONArray("choices")?.getJSONObject(0)
                val message = choice?.getJSONObject("message") ?: break
                
                if (message.has("tool_calls") && !message.isNull("tool_calls")) {
                    messages.put(message)
                    
                    // 提取推理内容
                    val reasoning = if (message.has("reasoning_content") && !message.isNull("reasoning_content")) {
                        message.optString("reasoning_content")
                    } else {
                        message.optString("content")
                    }
                    if (reasoning.isNotBlank()) {
                        onStep(AnalysisStep.Reasoning(reasoning))
                    }
                    
                    val toolCalls = message.getJSONArray("tool_calls")
                    for (i in 0 until toolCalls.length()) {
                        val call = toolCalls.getJSONObject(i)
                        val toolName = call.getJSONObject("function").getString("name")
                        val argsStr = call.getJSONObject("function").optString("arguments", "{}")
                        val args = try { JSONObject(argsStr) } catch (e: Exception) { JSONObject() }
                        
                        val toolDesc = getToolDescription(toolName)
                        onStep(AnalysisStep.CallingTool(toolName, toolDesc))
                        
                        val resultStr = executeToolFunction(toolName, args, allTasks)
                        
                        onStep(AnalysisStep.ToolResult(toolName, getSummary(resultStr)))
                        
                        messages.put(JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", call.getString("id"))
                            put("content", resultStr)
                        })
                    }
                    retryCount++
                } else {
                    finalJson = message.getString("content")
                    break
                }
            }
            
            if (finalJson != null) {
                onStep(AnalysisStep.Thinking("生成最终画像..."))
                val entity = parseAnalysisResult(
                    finalJson,
                    completionRate = completionRate,
                    delayedRate = delayedRate,
                    inputPreference = inputPreference,
                    categoryFocus = categoryFocus
                ).getOrThrow()
                onStep(AnalysisStep.Complete(entity))
                Result.success(entity)
            } else {
                Result.failure(Exception("AI画像推演未能返回有效结果（可能超过最大轮次）"))
            }

        } catch (e: Exception) {
            onStep(AnalysisStep.Error(e.message ?: "未知错误"))
            Result.failure(Exception("用户画像分析失败: ${e.message}", e))
        }
    }
    
    /**
     * 构建工具定义
     */
    private fun buildToolDefinitions(): JSONArray {
        return JSONArray().apply {
            // 1. 获取近期任务样本
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_recent_tasks")
                    put("description", "获取用户近期任务的标题和描述，用于分析职业身份、行业领域和性格特征。返回最近20条任务的文本内容。")
                    put("parameters", JSONObject("""{"type": "object", "properties": {"limit": {"type": "integer", "description": "获取任务数量，默认20"}}}"""))
                })
            })
            
            // 2. 获取任务时间统计
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_task_timing_stats")
                    put("description", "获取任务的完成时间与截止时间对比数据，用于分析执行节奏（提前完成 vs 极限踩点）。")
                    put("parameters", JSONObject("""{"type": "object", "properties": {"days": {"type": "integer", "description": "分析最近N天的任务，默认30"}}}"""))
                })
            })
            
            // 3. 获取子任务使用统计
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_subtask_usage")
                    put("description", "获取用户使用子任务的频率和完成情况，用于评估规划能力。")
                    put("parameters", JSONObject("""{"type": "object", "properties": {}}"""))
                })
            })
            
            // 4. 获取分类分布
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_category_distribution")
                    put("description", "获取各分类（特别是紧急类）的任务数量分布，用于评估压力承受度。")
                    put("parameters", JSONObject("""{"type": "object", "properties": {"days": {"type": "integer", "description": "分析最近N天，默认30"}}}"""))
                })
            })
            
            // 5. 获取路线规划偏好
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_route_preferences")
                    put("description", "获取用户在高德路线规划中的策略偏好（速度优先/距离优先），用于推断出行偏好。")
                    put("parameters", JSONObject("""{"type": "object", "properties": {}}"""))
                })
            })
            
            // 6. 获取活跃时间模式
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_time_activity_pattern")
                    put("description", "获取用户创建和完成任务的时间分布（按小时统计），用于分析作息规律。")
                    put("parameters", JSONObject("""{"type": "object", "properties": {}}"""))
                })
            })
            
            // 7. 获取地点足迹
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_location_footprints")
                    put("description", "获取用户的常在地和常去地统计，用于推断生活范围和出行模式。")
                    put("parameters", JSONObject("""{"type": "object", "properties": {}}"""))
                })
            })
            
            // 8. 获取上一期画像
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_previous_profile")
                    put("description", "获取上一次分析的用户画像，作为本次分析的演进基准，确保画像平滑变化。")
                    put("parameters", JSONObject("""{"type": "object", "properties": {}}"""))
                })
            })
            
            // 9. 获取AI交互历史
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_ai_interaction_history")
                    put("description", "获取用户最近与AI交互的原始文本（如语音输入），用于分析用户当前的语言表达风格和情绪状态。")
                    put("parameters", JSONObject("""{"type": "object", "properties": {"limit": {"type": "integer", "description": "获取条数，默认10"}}}"""))
                })
            })
        }
    }
    
    /**
     * 执行工具函数
     */
    private suspend fun executeToolFunction(
        toolName: String,
        args: JSONObject,
        allTasks: List<com.litetask.app.data.model.Task>
    ): String {
        return when (toolName) {
            "get_recent_tasks" -> {
                val limit = args.optInt("limit", 20)
                handleRecentTasks(allTasks, limit)
            }
            "get_task_timing_stats" -> {
                val days = args.optInt("days", 30)
                handleTaskTimingStats(allTasks, days)
            }
            "get_subtask_usage" -> handleSubtaskUsage()
            "get_category_distribution" -> {
                val days = args.optInt("days", 30)
                handleCategoryDistribution(allTasks, days)
            }
            "get_route_preferences" -> handleRoutePreferences()
            "get_time_activity_pattern" -> handleTimeActivity(allTasks)
            "get_location_footprints" -> handleLocationFootprints()
            "get_previous_profile" -> handlePreviousProfile()
            "get_ai_interaction_history" -> {
                val limit = args.optInt("limit", 10)
                handleAiInteractionHistory(limit)
            }
            else -> "未知工具: $toolName"
        }
    }
    
    /**
     * 获取工具描述
     */
    private fun getToolDescription(toolName: String): String {
        return when (toolName) {
            "get_recent_tasks" -> "获取近期任务文本"
            "get_task_timing_stats" -> "分析任务时间模式"
            "get_subtask_usage" -> "统计子任务使用情况"
            "get_category_distribution" -> "分析任务分类分布"
            "get_route_preferences" -> "获取路线规划偏好"
            "get_time_activity_pattern" -> "分析活跃时间分布"
            "get_location_footprints" -> "获取地点活动足迹"
            "get_previous_profile" -> "读取历史画像基准"
            "get_ai_interaction_history" -> "获取AI交互日志"
            else -> toolName
        }
    }
    
    /**
     * 生成工具结果摘要
     */
    private fun getSummary(result: String): String {
        return when {
            result.length <= 50 -> result
            result.contains("暂无") -> result.take(30)
            else -> result.take(50) + "..."
        }
    }
    
    // ==================== 工具处理函数 ====================
    
    private suspend fun handleRecentTasks(tasks: List<com.litetask.app.data.model.Task>, limit: Int = 20): String {
        return if (tasks.isEmpty()) {
            "近期暂无任务数据。"
        } else {
            val categories = categoryDao.getAllCategories().first()
            val samples = tasks.sortedByDescending { it.createdAt }.take(limit).joinToString("\n") { task ->
                val catName = categories.find { it.id == task.categoryId }?.name ?: "未知"
                "【${task.title}】(分类:$catName)${if (task.description.isNullOrBlank()) "" else " - ${task.description.take(30)}"}" 
            }
            "用户最近${limit}条任务:\n$samples"
        }
    }
    
    private fun handleTaskTimingStats(tasks: List<com.litetask.app.data.model.Task>, days: Int): String {
        val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        val recentCompleted = tasks.filter { 
            it.isDone && it.completedAt != null && it.completedAt >= cutoffTime && it.deadline > 0
        }
        
        val recentOverdue = tasks.filter {
            !it.isDone && (it.isExpired || (it.deadline in 1 until System.currentTimeMillis()))
        }
        
        if (recentCompleted.isEmpty() && recentOverdue.isEmpty()) {
            return "最近${days}天无有效的时间统计数据。"
        }
        
        val timingData = recentCompleted.map { task ->
            val diff = task.completedAt!! - task.deadline
            val hours = diff / (1000 * 60 * 60)
            when {
                hours < -24 -> "提前1天以上"
                hours < -2 -> "提前数小时"
                hours < 2 -> "准时完成"
                hours < 24 -> "延后数小时"
                else -> "延后1天以上"
            }
        }
        
        val distribution = timingData.groupingBy { it }.eachCount()
        return """
最近${days}天完成任务时间分布:
${if (distribution.isEmpty()) "无完成且有截止时间的任务" else distribution.entries.joinToString("\n") { "- ${it.key}: ${it.value}次" }}

当前未完成的严重逾期任务数: ${recentOverdue.size}
        """.trimIndent()
    }
    
    private suspend fun handleSubtaskUsage(): String {
        val allSubTasks = taskDao.getAllSubTasks()
        val allTasks = taskDao.getAllTasks().first()
        val tasksWithSubtasks = allTasks.count { task ->
            allSubTasks.any { it.taskId == task.id }
        }
        
        if (allSubTasks.isEmpty()) {
            return "用户暂未使用子任务功能。"
        }
        
        val completedSubtasks = allSubTasks.count { it.isCompleted }
        val completionRate = if (allSubTasks.isNotEmpty()) {
            (completedSubtasks.toFloat() / allSubTasks.size * 100).toInt()
        } else 0
        
        return """
子任务使用统计:
- 使用子任务的主任务数: $tasksWithSubtasks / ${allTasks.size}
- 子任务总数: ${allSubTasks.size}
- 已完成子任务: $completedSubtasks (${completionRate}%)
        """.trimIndent()
    }
    
    private suspend fun handleCategoryDistribution(tasks: List<com.litetask.app.data.model.Task>, days: Int): String {
        val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        val recentTasks = tasks.filter { it.createdAt >= cutoffTime }
        
        if (recentTasks.isEmpty()) {
            return "最近${days}天无任务数据。"
        }
        
        val categories = categoryDao.getAllCategories().first()
        val distribution = recentTasks.groupBy { it.categoryId }.mapValues { it.value.size }
        
        val result = StringBuilder("最近${days}天任务分类分布:\n")
        distribution.entries.sortedByDescending { it.value }.forEach { (catId, count) ->
            val catName = categories.find { it.id == catId }?.name ?: "未知"
            val percentage = (count.toFloat() / recentTasks.size * 100).toInt()
            result.append("- $catName: $count 个 (${percentage}%)\n")
        }
        
        return result.toString().trim()
    }
    
    private suspend fun handleRoutePreferences(): String {
        val allComponents = taskDao.getAllTasks().first().flatMap { task ->
            taskComponentDao.getComponentsByTaskIdSync(task.id)
        }
        
        val routeComponents = allComponents.filter { it.type == ComponentType.AMAP_ROUTE }
        
        if (routeComponents.isEmpty()) {
            return "用户暂未使用路线规划功能。"
        }
        
        val strategies = routeComponents.mapNotNull { component ->
            try {
                val json = JSONObject(component.dataJson)
                json.optInt("strategy", 0)
            } catch (e: Exception) { null }
        }
        
        val strategyCount = strategies.groupingBy { it }.eachCount()
        val result = StringBuilder("路线规划策略偏好:\n")
        strategyCount.forEach { (strategy, count) ->
            val name = when (strategy) {
                0 -> "速度优先"
                2 -> "距离优先"
                else -> "其他"
            }
            result.append("- $name: $count 次\n")
        }
        
        return result.toString().trim()
    }
    
    private fun handleTimeActivity(tasks: List<com.litetask.app.data.model.Task>): String {
        val creations = tasks.groupBy { 
            val cal = Calendar.getInstance().apply { timeInMillis = it.createdAt }
            cal.get(Calendar.HOUR_OF_DAY)
        }.mapValues { it.value.size }
        val completions = tasks.filter { it.isDone && it.completedAt != null }.groupBy {
            val cal = Calendar.getInstance().apply { timeInMillis = it.completedAt!! }
            cal.get(Calendar.HOUR_OF_DAY)
        }.mapValues { it.value.size }
        
        val topCreationHours = creations.entries.sortedByDescending { it.value }.take(3)
        val topCompletionHours = completions.entries.sortedByDescending { it.value }.take(3)
        
        return """
活跃时间分布:
- 创建任务高峰: ${topCreationHours.joinToString(", ") { "${it.key}时(${it.value}次)" }}
- 完成任务高峰: ${topCompletionHours.joinToString(", ") { "${it.key}时(${it.value}次)" }}
        """.trimIndent()
    }
    
    private suspend fun handleLocationFootprints(): String {
        val locations = userLocationDao.getAllLocationsFlow().first()
        return if (locations.isEmpty()) {
            "暂无地点活动记录。"
        } else {
            val topOrigins = locations.sortedByDescending { it.originCount }.take(3)
            val topDests = locations.sortedByDescending { it.destinationCount }.take(3)
            """
地点活动足迹:
- 常在地(出发): ${topOrigins.joinToString(", ") { "${it.name}(${it.originCount}次)" }}
- 常去地(目的): ${topDests.joinToString(", ") { "${it.name}(${it.destinationCount}次)" }}
            """.trimIndent()
        }
    }
    
    private suspend fun handlePreviousProfile(): String {
        val previous = userProfileDao.getLatestUserProfile()
        return if (previous == null) {
            "这是首次画像分析，无历史基准。"
        } else {
            """
上一期画像快照:
- 身份: ${previous.identity}
- 行业: ${previous.industry}
- 性格: ${previous.personality}
- 执行节奏: ${previous.executionRhythm}
- 拖延指数: ${previous.delayIndex}/5
- 规划能力: ${previous.planAbility}/5
- 压力承受度: ${previous.stressLevel}/5
- AI语气偏好: ${previous.aiTone}
            """.trimIndent()
        }
    }
    
    private suspend fun handleAiInteractionHistory(limit: Int): String {
        val histories = aiHistoryDao.getAllHistory()
        if (histories.isEmpty()) return "无AI交互记录。"
        val samples = histories.sortedByDescending { it.timestamp }.take(limit).joinToString("\n") {
            "- [${if (it.sourceType == com.litetask.app.data.model.AIHistorySource.VOICE) "语音" else "文字"}] ${it.content.take(50)}"
        }
        return "用户最近的AI交互记录:\n$samples"
    }
    
    private fun parseAnalysisResult(
        analysisResult: String,
        completionRate: Double,
        delayedRate: Double,
        inputPreference: String,
        categoryFocus: String
    ): Result<UserProfileHistoryEntity> {
        return try {
            val jsonStart = analysisResult.indexOf("{")
            val jsonEnd = analysisResult.lastIndexOf("}") + 1
            if (jsonStart == -1 || jsonEnd <= jsonStart) {
                throw Exception("AI返回结果格式错误，未找到有效JSON")
            }
            val jsonString = analysisResult.substring(jsonStart, jsonEnd)
            val json = org.json.JSONObject(jsonString)
            
            val profile = UserProfileHistoryEntity(
                id = 0,
                createdAt = System.currentTimeMillis(),
                completionRate = completionRate,
                delayedRate = delayedRate,
                inputPreference = inputPreference,
                categoryFocus = categoryFocus,
                
                identity = json.optString("identity", "未知"),
                industry = json.optString("industry", "未知"),
                travelPreference = json.optString("travelPreference", "无"),
                delayIndex = json.optInt("delayIndex", 1).coerceIn(1, 5),
                planAbility = json.optInt("planAbility", 1).coerceIn(1, 5),
                stressLevel = json.optInt("stressLevel", 1).coerceIn(1, 5),
                executionRhythm = json.optString("executionRhythm", "无"),
                aiTone = json.optString("aiTone", "默认"),
                personality = json.optString("personality", "无")
            )
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(Exception("解析失败: ${e.message}"))
        }
    }
}