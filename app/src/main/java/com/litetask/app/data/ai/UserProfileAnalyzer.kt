package com.litetask.app.data.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.litetask.app.data.local.AIHistoryDao
import com.litetask.app.data.local.CategoryDao
import com.litetask.app.data.local.FocusSessionDao
import com.litetask.app.data.local.PreferenceManager
import com.litetask.app.data.local.TaskComponentDao
import com.litetask.app.data.local.TaskDao
import com.litetask.app.data.local.UserLocationDao
import com.litetask.app.data.local.UserProfileDao
import com.litetask.app.data.model.AMapRouteSupport
import com.litetask.app.data.model.AMapRouteSupport.resolvedTravelMode
import com.litetask.app.data.model.Category
import com.litetask.app.data.model.ComponentType
import com.litetask.app.data.model.FocusSessionEntity
import com.litetask.app.data.model.LinkAndPassSupport
import com.litetask.app.data.model.RecurrenceEngine
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskComponent
import com.litetask.app.data.model.TaskComponentEntity
import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.data.model.UserProfileHistoryEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale
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
    private val focusSessionDao: FocusSessionDao,
    private val userLocationDao: UserLocationDao,
    private val aiHistoryDao: AIHistoryDao,
    private val categoryDao: CategoryDao,
    private val taskComponentDao: TaskComponentDao,
    private val userProfileDao: UserProfileDao
) {
    
    data class ObjectiveStats(
        val completionRate: Double,
        val delayedRate: Double,
        val categoryFocus: String,
        val inputPreference: String,
        val recentTaskCount: Int = 0
    )

    companion object {
        private const val WINDOW_30D_MS = 30L * 24 * 60 * 60 * 1000L
        private val TRAVEL_KEYWORDS = listOf(
            "去", "出差", "出行", "导航", "路线", "打车", "地铁", "公交", "高铁", "火车", "航班", "机场", "骑行", "自驾", "步行"
        )

        /**
         * 将 AI 历史记录整理为注入画像分析器的摘要文本。
         * 优先选取成功记录；仅当全部失败时才回退展示失败记录，并清晰标注来源类型与附加信息。
         */
        internal fun formatAiHistoryForPrompt(
            histories: List<com.litetask.app.data.model.AIHistory>,
            limit: Int
        ): String {
            if (histories.isEmpty() || limit <= 0) return "无AI交互记录。"
            val sorted = histories.sortedByDescending { it.timestamp }
            val candidates = sorted.filter { it.isSuccess }.ifEmpty { sorted }.take(limit)
            val samples = candidates.joinToString("\n") { item ->
                val sourceLabel = when (item.sourceType) {
                    com.litetask.app.data.model.AIHistorySource.VOICE -> "语音"
                    com.litetask.app.data.model.AIHistorySource.TEXT -> "文字"
                    com.litetask.app.data.model.AIHistorySource.SUBTASK -> "子任务拆解"
                }
                val targetPrefix = item.targetTitle?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let { "目标「${it.take(24)}」 " } ?: ""
                val cleanedContent = item.content.trim().take(50)
                val body = when {
                    cleanedContent.isNotEmpty() && item.imageCount > 0 -> "$cleanedContent (含${item.imageCount}张图)"
                    cleanedContent.isNotEmpty() -> cleanedContent
                    item.imageCount > 0 -> "[纯图片识别(${item.imageCount}张)]"
                    item.targetTitle?.isNotBlank() == true -> "[一键自动拆解]"
                    else -> "[空内容]"
                }
                val statusSuffix = if (!item.isSuccess) " [未成功]" else ""
                "- [$sourceLabel] $targetPrefix$body$statusSuffix"
            }
            return "用户最近的AI交互记录:\n$samples"
        }

        internal fun computeObjectiveStats(
            allTasks: List<com.litetask.app.data.model.Task>,
            categories: List<com.litetask.app.data.model.Category>,
            allAiHistories: List<com.litetask.app.data.model.AIHistory>,
            now: Long = System.currentTimeMillis()
        ): ObjectiveStats {
            val cutoff30d = now - 30L * 24 * 60 * 60 * 1000
            val completed = allTasks.count { it.isDone }
            val recentPool = allTasks.filter {
                it.createdAt >= cutoff30d || it.deadline >= cutoff30d || (it.completedAt ?: 0L) >= cutoff30d
            }.ifEmpty { allTasks }
            val delayed = recentPool.count {
                it.isExpired ||
                    (!it.isDone && it.deadline in 1 until now) ||
                    (it.isDone && it.completedAt != null && it.deadline > 0 && it.completedAt > it.deadline)
            }
            val completionRate = if (allTasks.isNotEmpty()) completed.toDouble() / allTasks.size else 0.0
            val delayedRate = if (recentPool.isNotEmpty()) delayed.toDouble() / recentPool.size else 0.0

            val focusPool = allTasks.filter { it.createdAt >= cutoff30d }.ifEmpty { allTasks }
            val categoryCounts = focusPool.groupBy { it.categoryId }.mapValues { it.value.size }
            val topCategoryId = categoryCounts.maxByOrNull { it.value }?.key
            val categoryFocus = categories.find { it.id == topCategoryId }?.name ?: "无明显重心"

            val voiceCount = allAiHistories.count { it.sourceType == com.litetask.app.data.model.AIHistorySource.VOICE }
            val textCount = allAiHistories.count { it.sourceType == com.litetask.app.data.model.AIHistorySource.TEXT }
            val inputPreference = when {
                voiceCount == 0 && textCount == 0 -> "无 AI 记录（手动为主）"
                voiceCount > textCount -> "语音"
                textCount > voiceCount -> "文字"
                else -> "语音与文字均衡"
            }
            return ObjectiveStats(
                completionRate = completionRate,
                delayedRate = delayedRate,
                categoryFocus = categoryFocus,
                inputPreference = inputPreference,
                recentTaskCount = recentPool.size
            )
        }

        internal fun summarizeRoutePreferences(routeEntities: List<TaskComponentEntity>): String {
            val routes = routeEntities
                .filter { it.type == ComponentType.AMAP_ROUTE }
                .mapNotNull { it.toTaskComponent() as? TaskComponent.AMapComponent }
            if (routes.isEmpty()) return "用户暂未使用路线规划功能。"

            val modeCounts = routes.groupingBy { it.data.resolvedTravelMode() }.eachCount()
            val strategyCounts = routes.groupingBy { it.data.strategy }.eachCount()
            val topDestinations = routes
                .mapNotNull { it.data.endName.trim().takeIf { name -> name.isNotEmpty() } }
                .groupingBy { it }
                .eachCount()
                .entries.sortedByDescending { it.value }
                .take(3)
                .joinToString("、") { "${it.key}(${it.value}次)" }
            val etaMins = routes.mapNotNull { it.data.estimatedDurationSec?.takeIf { sec -> sec > 0 }?.div(60) }

            return buildString {
                appendLine("高德路线与出行统计 (共${routes.size}条):")
                appendLine(
                    "- 出行方式偏好: " + modeCounts.entries.sortedByDescending { it.value }
                        .joinToString(", ") { "${it.key.label} ${it.value}次" }
                )
                append(
                    "- 驾车路径策略: " + strategyCounts.entries.sortedByDescending { it.value }
                        .joinToString(", ") { (strategy, count) ->
                            "${AMapRouteSupport.DrivingStrategy.fromCode(strategy).label} ${count}次"
                        }
                )
                if (topDestinations.isNotEmpty()) {
                    appendLine()
                    append("- 常去目的地: $topDestinations")
                }
                if (etaMins.isNotEmpty()) {
                    appendLine()
                    append("- 预估平均单程耗时: ${etaMins.average().toInt()}分钟")
                }
            }.trim()
        }

        internal fun summarizeComponentHabits(
            composites: List<TaskDetailComposite>
        ): String {
            val allComps = composites.flatMap { comp -> comp.components.map { it.toTaskComponent() } }
            if (allComps.isEmpty()) {
                return "用户暂未使用链接、凭证、附件、周期或语音等扩展组件。"
            }

            val routes = allComps.filterIsInstance<TaskComponent.AMapComponent>()
            val files = allComps.filterIsInstance<TaskComponent.FileComponent>()
            val links = allComps.filterIsInstance<TaskComponent.LinkComponent>()
            val passes = allComps.filterIsInstance<TaskComponent.AccessPassComponent>()
            val recurrences = allComps.filterIsInstance<TaskComponent.RecurrenceComponent>()
            val voices = allComps.filterIsInstance<TaskComponent.VoiceNoteComponent>()

            return buildString {
                appendLine("任务扩展组件分布 (来自${composites.size}个任务):")
                if (recurrences.isNotEmpty()) {
                    val freqSummary = recurrences
                        .groupingBy { RecurrenceEngine.RecurrenceFreq.fromCode(it.data.freq).label }
                        .eachCount()
                        .entries.sortedByDescending { it.value }
                        .joinToString(", ") { "${it.key}${it.value}个" }
                    appendLine("- 周期重复规律: ${recurrences.size}个 ($freqSummary)")
                }
                if (links.isNotEmpty()) {
                    val catSummary = links
                        .groupingBy { LinkAndPassSupport.LinkCategory.fromCode(it.data.linkCategory).label }
                        .eachCount()
                        .entries.sortedByDescending { it.value }
                        .joinToString(", ") { "${it.key}${it.value}个" }
                    val topBrands = links
                        .map { LinkAndPassSupport.resolveBrandMeta(it.data.url, it.data.linkCategory).brandName }
                        .filter { it.isNotBlank() && it != "网页" }
                        .groupingBy { it }
                        .eachCount()
                        .entries.sortedByDescending { it.value }
                        .take(4)
                        .joinToString("、") { it.key }
                    val brandNote = if (topBrands.isNotEmpty()) "，高频来源:$topBrands" else ""
                    appendLine("- 书签/链接: ${links.size}个 ($catSummary$brandNote)")
                }
                if (passes.isNotEmpty()) {
                    val passSummary = passes
                        .groupingBy { LinkAndPassSupport.AccessPassType.fromCode(it.data.passType).label }
                        .eachCount()
                        .entries.sortedByDescending { it.value }
                        .joinToString(", ") { "${it.key}${it.value}个" }
                    appendLine("- 凭证/号牌: ${passes.size}个 ($passSummary)")
                }
                if (files.isNotEmpty()) {
                    val pdfCount = files.count { it.data.mimeType.contains("pdf", true) || it.data.fileName.endsWith(".pdf", true) }
                    val imgCount = files.count { it.data.mimeType.startsWith("image/", true) }
                    val extra = listOfNotNull(
                        pdfCount.takeIf { it > 0 }?.let { "PDF ${it}份" },
                        imgCount.takeIf { it > 0 }?.let { "图片 ${it}份" }
                    ).joinToString("，")
                    val extraNote = if (extra.isNotEmpty()) " ($extra)" else ""
                    appendLine("- 文件附件: ${files.size}份$extraNote")
                }
                if (voices.isNotEmpty()) {
                    val totalMins = voices.sumOf { it.data.durationMs } / 60_000L
                    val transcribedCount = voices.count { !it.data.transcript.isNullOrBlank() }
                    appendLine("- 语音备忘录: ${voices.size}条 (累计约${totalMins}分钟，已转文字${transcribedCount}条)")
                }
                if (routes.isNotEmpty()) {
                    val modeText = routes.groupingBy { it.data.resolvedTravelMode().label }
                        .eachCount()
                        .entries.sortedByDescending { it.value }
                        .joinToString(", ") { "${it.key}${it.value}次" }
                    appendLine("- 路线目的地: ${routes.size}条 ($modeText)")
                }
            }.trim()
        }

        internal fun summarizeTimeAndFocusPattern(
            allTasks: List<Task>,
            focusSessions: List<FocusSessionEntity>,
            now: Long = System.currentTimeMillis(),
            zoneId: ZoneId = ZoneId.systemDefault()
        ): String {
            val cutoff30d = now - WINDOW_30D_MS
            val recentTasks = allTasks.filter {
                it.createdAt >= cutoff30d || (it.completedAt ?: 0L) >= cutoff30d
            }
            val recentOrFallback = recentTasks.ifEmpty { allTasks }

            fun peakHoursText(times: List<Long>): String {
                if (times.isEmpty()) return "暂无数据"
                val counts = times.groupingBy {
                    java.time.Instant.ofEpochMilli(it).atZone(zoneId).hour
                }.eachCount()
                return counts.entries.sortedByDescending { it.value }.take(3)
                    .joinToString(", ") { "${it.key}时(${it.value}次)" }
            }

            val completedRecent = recentOrFallback.filter { it.isDone && it.completedAt != null }
            val weekendCompleted = completedRecent.count {
                val dow = java.time.Instant.ofEpochMilli(it.completedAt!!).atZone(zoneId).dayOfWeek
                dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY
            }
            val validSessions = focusSessions.filter { it.actualSeconds >= 60L }
            val completedComposites = completedRecent.map {
                TaskDetailComposite(it, emptyList(), emptyList(), null)
            }
            val energyPattern = ScheduleContextBuilder.analyzeFocusEnergyPattern(
                sessions = validSessions,
                completedTasks = completedComposites,
                zoneId = zoneId
            )

            return buildString {
                appendLine("近30天作息与精力分布（近30天活跃/新建 ${recentTasks.size} 条任务）:")
                appendLine("- 创建任务高峰: ${peakHoursText(recentOrFallback.map { it.createdAt })}")
                appendLine("- 完成任务高峰: ${peakHoursText(completedRecent.mapNotNull { it.completedAt })}")
                if (completedRecent.isNotEmpty()) {
                    val weekendPct = (weekendCompleted * 100) / completedRecent.size
                    appendLine("- 工作日/周末完成占比: 工作日 ${100 - weekendPct}% vs 周末 ${weekendPct}%")
                }
                appendLine("- 有效专注(>=1分钟) ${validSessions.size} 次")
                append("- ${energyPattern.toPromptSummary().replace("\n", " | ")}")
            }.trim()
        }

        internal fun parseProfileJsonToEntity(
            analysisResult: String,
            completionRate: Double,
            delayedRate: Double,
            inputPreference: String,
            categoryFocus: String,
            createdAt: Long = System.currentTimeMillis()
        ): Result<UserProfileHistoryEntity> {
            return try {
                val cleaned = stripMarkdownFences(analysisResult)
                val jsonStart = cleaned.indexOf("{")
                val jsonEnd = cleaned.lastIndexOf("}") + 1
                if (jsonStart == -1 || jsonEnd <= jsonStart) {
                    throw IllegalArgumentException("AI返回结果格式错误，未找到有效JSON")
                }
                val jsonStr = cleaned.substring(jsonStart, jsonEnd)
                val root = JsonParser.parseString(jsonStr)
                if (!root.isJsonObject) {
                    throw IllegalArgumentException("JSON根节点必须是对象")
                }
                val json = root.asJsonObject

                val profile = UserProfileHistoryEntity(
                    id = 0,
                    createdAt = createdAt,
                    completionRate = completionRate,
                    delayedRate = delayedRate,
                    inputPreference = inputPreference,
                    categoryFocus = categoryFocus,
                    identity = json.optCleanString(listOf("identity"), "待进一步观察"),
                    industry = json.optCleanString(listOf("industry"), "未知领域"),
                    travelPreference = json.optCleanString(listOf("travelPreference", "travel_preference"), "暂无明显偏好"),
                    delayIndex = json.optIntInRange(listOf("delayIndex", "delay_index"), 3, 1, 5),
                    planAbility = json.optIntInRange(listOf("planAbility", "plan_ability"), 3, 1, 5),
                    stressLevel = json.optIntInRange(listOf("stressLevel", "stress_level"), 3, 1, 5),
                    executionRhythm = json.optCleanString(listOf("executionRhythm", "execution_rhythm"), "稳步推进"),
                    aiTone = json.optCleanString(listOf("aiTone", "ai_tone"), "温和鼓励"),
                    personality = json.optCleanString(listOf("personality"), "持续积累数据中的实干探索者"),
                    source = UserProfileHistoryEntity.SOURCE_AI
                )
                Result.success(profile)
            } catch (e: Exception) {
                Result.failure(Exception("解析失败: ${e.message}"))
            }
        }

        private fun stripMarkdownFences(raw: String): String {
            val trimmed = raw.trim()
            val match = Regex("^```(?:json)?\\s*([\\s\\S]*?)\\s*```$", RegexOption.IGNORE_CASE).find(trimmed)
            return match?.groupValues?.getOrNull(1)?.trim() ?: trimmed
        }

        private fun JsonObject.optCleanString(keys: List<String>, fallback: String): String {
            for (key in keys) {
                val el = get(key) ?: continue
                if (el.isJsonNull || !el.isJsonPrimitive) continue
                val raw = runCatching { el.asString }.getOrNull()?.trim().orEmpty()
                if (raw.isNotEmpty() && !raw.equals("null", ignoreCase = true)) return raw
            }
            return fallback
        }

        private fun JsonObject.optIntInRange(keys: List<String>, defaultVal: Int, minVal: Int, maxVal: Int): Int {
            for (key in keys) {
                val el = get(key) ?: continue
                if (el.isJsonNull || !el.isJsonPrimitive) continue
                val value = runCatching { el.asInt }.getOrNull()
                if (value != null) return value.coerceIn(minVal, maxVal)
            }
            return defaultVal
        }
    }

    /**
     * 分析用户画像 (优化版 Function Calling)
     */
    suspend fun analyzeUserProfile(
        onStep: (AnalysisStep) -> Unit = {}
    ): Result<UserProfileHistoryEntity> {
        val providerId = preferenceManager.getAiProvider()
        val apiKey = preferenceManager.getApiKey(providerId).orEmpty()
        preferenceManager.getAiConfigError(apiKey)?.let { errorMsg ->
            return Result.failure(Exception(errorMsg))
        }

        val modelId = preferenceManager.getAiModel()
        val provider = aiProviderFactory.getProvider(providerId)

        return try {
            onStep(AnalysisStep.Thinking("正在汇集任务与行为概览..."))

            val now = System.currentTimeMillis()
            val allComposites = taskDao.getAllTaskDetailsSync()
            val allTasks = if (allComposites.isNotEmpty()) {
                allComposites.map { it.task }
            } else {
                taskDao.getAllTasks().first()
            }
            val categories = categoryDao.getAllCategories().first()
            val allAiHistories = aiHistoryDao.getAllHistory()
            val stats = computeObjectiveStats(allTasks, categories, allAiHistories, now)
            val completionRate = stats.completionRate
            val delayedRate = stats.delayedRate
            val categoryFocus = stats.categoryFocus
            val inputPreference = stats.inputPreference

            val recentFocusSessions = focusSessionDao.getSessionsSinceSync(now - WINDOW_30D_MS)
            val validFocusSessions = recentFocusSessions.filter { it.actualSeconds >= 60L }
            val focusSummaryText = if (validFocusSessions.isNotEmpty()) {
                val totalMin = validFocusSessions.sumOf { it.actualSeconds } / 60L
                val pomodoroCount = validFocusSessions.count { it.mode == "POMODORO" }
                "${validFocusSessions.size}次（共${totalMin}分钟，其中番茄钟${pomodoroCount}次）"
            } else {
                "暂无有效专注计时记录"
            }

            // 判断是否存在位置/出行/扩展组件语境，以按需开放工具
            val savedLocations = userLocationDao.getAllLocationsSync()
            val savedRoutes = taskComponentDao.getComponentsByTypeSync(ComponentType.AMAP_ROUTE)
            val recentTaskTexts = allTasks.sortedByDescending { it.createdAt }.take(20)
                .joinToString(" ") { "${it.title} ${it.description.orEmpty()}" }
            val hasTravelContext = savedLocations.isNotEmpty() ||
                savedRoutes.isNotEmpty() ||
                TRAVEL_KEYWORDS.any { recentTaskTexts.contains(it) }
            val hasComponentData = allComposites.any { it.components.isNotEmpty() }

            val sampleGuardNote = if (allTasks.size < 5 || stats.recentTaskCount < 3) {
                "【低样本提示】当前总任务数 ${allTasks.size} 条、近30天样本 ${stats.recentTaskCount} 条，行为依据较少：各项 1-5 分指标应保守取中性分（2-3 分），避免极端推断，并在性格描述中如实注明“初期数据有限”。"
            } else {
                "总任务数 ${allTasks.size} 条，近30天有效样本 ${stats.recentTaskCount} 条。"
            }

            // 获取上一次画像作为基准
            val previousProfile = userProfileDao.getLatestUserProfile()
            val previousContext = if (previousProfile != null) {
                """
                上一期画像快照（作为演进基准）：
                - 身份: ${previousProfile.identity}
                - 行业: ${previousProfile.industry}
                - 性格: ${previousProfile.personality}
                - 拖延指数: ${previousProfile.delayIndex}/5（1最自律，5最拖延）
                - 规划能力: ${previousProfile.planAbility}/5（1较弱，5极强）
                - 当前压力指数: ${previousProfile.stressLevel}/5（1轻松，5压力极大）
                """.trimIndent()
            } else {
                "这是首次分析，无历史画像基准。"
            }

            val systemPrompt = """
你是一个专业的用户画像推演 Agent 系统。你可以并发调用内置工具实时探索用户的任务文本、扩展组件（链接/凭证/周期/路线/附件/语音）、专注节律、时间分布与交互记录。
请结合工具获取到的客观数据，对用户进行深度心理与行为模式推理。

# 当前已知的客观数据
- 数据样本量: $sampleGuardNote
- 任务总体完成率: ${(completionRate * 100).toInt()}%
- 近30天拖延/延期率: ${(delayedRate * 100).toInt()}%
- 近期核心关注领域: $categoryFocus
- 输入偏好: $inputPreference
- 近30天有效专注记录: $focusSummaryText

$previousContext

# 分析维度与推理思路 (Chain of Thought)
1. **信息收集**: 优先并发调用 `get_recent_tasks`、`get_task_timing_stats`、`get_time_activity_pattern`${if (hasComponentData) "、`get_component_usage`" else ""}，必要时按需补充其他维度工具。
2. **身份 & 行业推理**: 深入分析任务标题、备注专有名词、链接所属领域（如代码库/在线文档/学术资源）及凭证类型（如会议/车票/考场），推断具体身份与行业；依据不足时不强行编造。
3. **行为模式分析**:
   - **出行偏好**: 结合高德路线中的真实出行方式（驾车/公交地铁/骑行/步行）、策略及地点足迹归纳；无出行线索则返回"暂无明显偏好"。
   - **执行节奏**: 综合完成时间与截止时间差值、周期任务规律与专注黄金时段，判断节奏（如"提前规划"、"稳步推进"、"晚间冲刺"、"极限踩点"）。
4. **心理与能力评估**:
   - **规划能力 (1-5)**: 综合复杂任务子步骤拆解率、周期规划与组件化管理习惯评估，1较弱，5极强。
   - **当前压力指数 (1-5)**: 结合近期任务密度、紧急/逾期积压占比及专注负荷评估，1轻松从容，5压力极大。
   - **拖延指数 (1-5)**: 综合近30天延期率和踩点习惯给出评分，1极度自律，5严重拖延。
5. **AI 互动定制**:
   - **AI语气偏好**: 结合压力与拖延定制最合适陪伴语气（如高压期"温和鼓励"，严重拖延"温和督促/严厉监督"，高效期"极简干练"）。
   - **综合性格**: 用简练生动的一句话总结用户的核心行为与思维特质。

# 工具调用核心原则
- **拒绝主观臆断**: 所有推断必须有客观数据支撑；样本少时宁可保守说明，不可硬猜。
- **高效并发调用**: 可同一轮并发请求所需工具，避免多余重复查询。

# 最终输出格式约束
当你完成了所有必要的推理，必须返回一个纯粹的 JSON 对象，不要包含任何额外的解释文本或 Markdown 标记（如 ```json）。所有字段不得为空字符串或 `"null"`：
{
  "identity": "具体的用户身份（如：考研学生、前端工程师、产品经理）",
  "industry": "具体的行业领域（如：高等教育、互联网、科研）",
  "travelPreference": "高频出行方式（如：地铁通勤、骑行优先，无明显线索则填暂无明显偏好）",
  "delayIndex": 3,
  "planAbility": 4,
  "stressLevel": 3,
  "executionRhythm": "核心执行习惯（如：稳步推进、晚间高效、极限踩点）",
  "aiTone": "最适合当前状态的AI语气设定（如：温柔鼓励、幽默陪伴、极简干练）",
  "personality": "综合性格特征描述（一句话，精准生动）"
}
            """.trimIndent()

            val messages = JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                put(JSONObject().apply { put("role", "user"); put("content", "请分析我的行为数据与习惯，调用所需工具并输出最终画像 JSON。") })
            }

            val tools = buildToolDefinitions(hasTravelContext, hasComponentData)

            var retryCount = 0
            val maxRetries = 10
            var finalJson: String? = null

            while (retryCount < maxRetries) {
                val isForcedFinalRound = retryCount == maxRetries - 1
                if (isForcedFinalRound) {
                    onStep(AnalysisStep.Thinking("信息收集完毕，正在收敛最终画像结论..."))
                    messages.put(JSONObject().apply {
                        put("role", "user")
                        put("content", "数据采集已达上限，请立刻结合已有客观数据与工具结果，严格按要求直接输出最终纯 JSON 画像结果。")
                    })
                }

                val response = provider.chatWithTools(
                    apiKey = apiKey,
                    model = modelId,
                    messages = messages,
                    tools = if (isForcedFinalRound) null else tools
                )
                if (response.isFailure) return Result.failure(response.exceptionOrNull()!!)

                val choice = response.getOrNull()?.optJSONArray("choices")?.optJSONObject(0)
                val message = choice?.optJSONObject("message") ?: break

                val toolCalls = if (!isForcedFinalRound && message.has("tool_calls") && !message.isNull("tool_calls")) {
                    message.optJSONArray("tool_calls")
                } else null

                if (toolCalls != null && toolCalls.length() > 0) {
                    messages.put(message)

                    val reasoning = message.optString("reasoning_content", "")
                        .takeIf { it.isNotBlank() && it != "null" }
                        ?: message.optString("content", "")
                            .takeIf { it.isNotBlank() && it != "null" }
                    if (!reasoning.isNullOrBlank()) {
                        onStep(AnalysisStep.Reasoning(reasoning))
                    }

                    coroutineScope {
                        val jobs = (0 until toolCalls.length()).map { i ->
                            val call = toolCalls.getJSONObject(i)
                            val function = call.getJSONObject("function")
                            val toolName = function.getString("name")
                            val argsStr = function.optString("arguments", "{}")
                            val args = try { JSONObject(argsStr) } catch (_: Exception) { JSONObject() }
                            val callId = call.getString("id")

                            onStep(AnalysisStep.CallingTool(toolName, progressLabelForTool(toolName)))

                            async {
                                val resultStr = executeToolFunction(
                                    toolName = toolName,
                                    args = args,
                                    allComposites = allComposites,
                                    allTasks = allTasks,
                                    focusSessions30d = recentFocusSessions,
                                    categories = categories,
                                    allAiHistories = allAiHistories
                                )
                                onStep(AnalysisStep.ToolResult(toolName, summaryLabelForTool(toolName, resultStr)))
                                callId to resultStr
                            }
                        }
                        jobs.awaitAll().forEach { (callId, resultStr) ->
                            messages.put(JSONObject().apply {
                                put("role", "tool")
                                put("tool_call_id", callId)
                                put("content", resultStr)
                            })
                        }
                    }
                    retryCount++
                } else {
                    finalJson = message.optString("content", "")
                        .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    break
                }
            }

            if (finalJson != null) {
                onStep(AnalysisStep.Thinking("正在生成并校验画像结果..."))
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
                Result.failure(Exception("AI画像推演未能返回有效结果，请稍后重试"))
            }

        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            onStep(AnalysisStep.Error(e.message ?: "未知错误"))
            Result.failure(Exception("用户画像分析失败: ${e.message}", e))
        }
    }

    /**
     * 构建工具定义（按需提供地理与组件工具，避免无效调用）
     */
    private fun buildToolDefinitions(
        includeLocationTools: Boolean,
        includeComponentTool: Boolean
    ): JSONArray {
        return JSONArray().apply {
            // 1. 获取近期关键任务样本（含状态与标签）
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_recent_tasks")
                    put("description", "获取近期核心任务列表（优先展示待办/逾期任务，附带分类、子任务与关联组件标签），用于推断用户身份、行业与当前工作生活重心。")
                    put("parameters", JSONObject("""{"type": "object", "properties": {"limit": {"type": "integer", "description": "获取任务数量，1-35，默认25"}}}"""))
                })
            })

            // 2. 获取任务时间统计
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_task_timing_stats")
                    put("description", "获取完成时间与截止时间对比分布、逾期积压数及近30天有效专注统计，用于评估拖延指数与执行节奏。")
                    put("parameters", JSONObject("""{"type": "object", "properties": {"days": {"type": "integer", "description": "分析最近N天，默认30"}}}"""))
                })
            })

            // 3. 获取子任务使用统计
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_subtask_usage")
                    put("description", "获取子任务拆解率及子任务完成情况（区分近30天长任务规划率与全局统计），用于评估规划能力。")
                    put("parameters", JSONObject("""{"type": "object", "properties": {}}"""))
                })
            })

            // 4. 获取分类分布
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_category_distribution")
                    put("description", "获取各分类任务数量及紧急任务占比分布，用于评估近期压力指数与生活重心。")
                    put("parameters", JSONObject("""{"type": "object", "properties": {"days": {"type": "integer", "description": "分析最近N天，默认30"}}}"""))
                })
            })

            if (includeComponentTool) {
                // 5. 获取扩展组件（链接类别、凭证类型、周期规则、文件与语音）分布
                put(JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", "get_component_usage")
                        put("description", "获取任务附加的扩展组件分布统计（周期重复规律、书签品牌分类、凭证类型、PDF/图片附件与语音备忘使用情况），极具身份与规划线索价值。")
                        put("parameters", JSONObject("""{"type": "object", "properties": {}}"""))
                    })
                })
            }

            if (includeLocationTools) {
                // 6. 获取路线规划与出行方式偏好
                put(JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", "get_route_preferences")
                        put("description", "获取高德路线的真实出行方式偏好（驾车/公交地铁/骑行/步行）、路径策略与平均通勤耗时，用于推断出行偏好。")
                        put("parameters", JSONObject("""{"type": "object", "properties": {}}"""))
                    })
                })
            }

            // 7. 获取近30天作息与黄金专注时段
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_time_activity_pattern")
                    put("description", "获取近30天创建/完成任务高峰时段、工作日与周末占比，以及专注记录沉淀出的精力黄金窗口，用于分析作息节律。")
                    put("parameters", JSONObject("""{"type": "object", "properties": {}}"""))
                })
            })

            if (includeLocationTools) {
                // 8. 获取地点足迹
                put(JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", "get_location_footprints")
                        put("description", "获取用户的常驻锚点地点（如家/公司/学校）与常去目的地统计，用于推断生活范围。")
                        put("parameters", JSONObject("""{"type": "object", "properties": {}}"""))
                    })
                })
            }

            // 9. 获取AI交互历史
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_ai_interaction_history")
                    put("description", "获取用户近期与AI交互的原始表达文本（语音/文字/子任务拆解目标），用于观察语言风格与当前心境。")
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
        allComposites: List<TaskDetailComposite>,
        allTasks: List<Task>,
        focusSessions30d: List<FocusSessionEntity>,
        categories: List<Category>,
        allAiHistories: List<com.litetask.app.data.model.AIHistory>
    ): String {
        return when (toolName) {
            "get_recent_tasks" -> {
                val limit = args.optInt("limit", 25).coerceIn(1, 35)
                handleRecentTasks(allComposites, categories, limit)
            }
            "get_task_timing_stats" -> {
                val days = args.optInt("days", 30).coerceIn(1, 90)
                handleTaskTimingStats(allTasks, focusSessions30d, days)
            }
            "get_subtask_usage" -> handleSubtaskUsage(allComposites, allTasks)
            "get_category_distribution" -> {
                val days = args.optInt("days", 30).coerceIn(1, 90)
                handleCategoryDistribution(allTasks, categories, days)
            }
            "get_component_usage" -> summarizeComponentHabits(allComposites)
            "get_route_preferences" -> handleRoutePreferences()
            "get_time_activity_pattern" -> summarizeTimeAndFocusPattern(allTasks, focusSessions30d)
            "get_location_footprints" -> handleLocationFootprints()
            "get_previous_profile" -> "上一期画像基准已在系统上下文中提供。"
            "get_ai_interaction_history" -> {
                val limit = args.optInt("limit", 10).coerceIn(1, 20)
                formatAiHistoryForPrompt(allAiHistories, limit)
            }
            else -> "未知工具: $toolName"
        }
    }

    private fun progressLabelForTool(toolName: String): String = when (toolName) {
        "get_recent_tasks" -> "正在查阅近期任务与关联线索..."
        "get_task_timing_stats" -> "正在分析按时完成率与专注数据..."
        "get_subtask_usage" -> "正在评估子任务规划与拆解习惯..."
        "get_category_distribution" -> "正在统计任务分类分布与紧急度..."
        "get_component_usage" -> "正在扫描周期重复、链接与附件组件习惯..."
        "get_route_preferences" -> "正在分析高德出行方式与通勤偏好..."
        "get_time_activity_pattern" -> "正在分析近30天作息规律与黄金专注时段..."
        "get_location_footprints" -> "正在整理常驻地与高频活动足迹..."
        "get_ai_interaction_history" -> "正在了解近期 AI 交互表达风格..."
        else -> "正在查询分析维度 ($toolName)..."
    }

    private fun summaryLabelForTool(toolName: String, result: String): String = when (toolName) {
        "get_recent_tasks" -> {
            val count = Regex("""(\d+)条""").find(result)?.groupValues?.getOrNull(1)
            if (count != null) "已读取 ${count} 条核心任务样本" else "任务样本获取完成"
        }
        "get_task_timing_stats" -> "执行节奏与专注统计分析完成"
        "get_subtask_usage" -> "子任务规划习惯分析完成"
        "get_category_distribution" -> "分类与负荷分布分析完成"
        "get_component_usage" -> if (result.contains("暂未使用")) "暂无扩展组件使用记录" else "扩展组件使用特征分析完成"
        "get_route_preferences" -> if (result.contains("暂未使用")) "暂无路线出行记录" else "出行方式偏好提取完成"
        "get_time_activity_pattern" -> "作息高峰与精力窗口识别完成"
        "get_location_footprints" -> if (result.contains("暂无")) "暂无常用地点记录" else "高频地点足迹整理完成"
        "get_ai_interaction_history" -> if (result.contains("无AI交互")) "暂无历史 AI 对话样本" else "AI 协作表达特征读取完成"
        else -> "数据维度分析完成"
    }

    // ==================== 工具处理函数 ====================

    private fun handleRecentTasks(
        composites: List<TaskDetailComposite>,
        categories: List<Category>,
        limit: Int
    ): String {
        if (composites.isEmpty()) return "近期暂无任务数据。"
        val now = System.currentTimeMillis()
        // 优先采样活跃/逾期任务 + 近期已完成任务，让模型直观看到当下负担与过往成果
        val prioritized = (
            composites.filter { !it.task.isDone }.sortedByDescending { it.task.isPinned } +
                composites.filter { it.task.isDone }.sortedByDescending { it.task.completedAt ?: it.task.deadline }
            )
            .distinctBy { it.task.id }
            .sortedWith(compareBy<TaskDetailComposite> { it.task.isDone }.thenByDescending { it.task.createdAt })
            .take(limit)

        val catMap = categories.associateBy { it.id }
        val lines = prioritized.joinToString("\n") { comp ->
            val task = comp.task
            val catName = comp.category?.name ?: catMap[task.categoryId]?.name ?: "未分类"
            val tags = mutableListOf<String>()
            when {
                task.isDone -> tags += "已完成"
                task.isExpired || (task.deadline in 1 until now) -> tags += "已逾期"
                task.isPinned -> tags += "置顶"
            }
            if (comp.subTasks.isNotEmpty()) {
                tags += "子任务${comp.subTasks.count { it.isCompleted }}/${comp.subTasks.size}"
            }
            val compTypes = comp.components.map { it.type }.distinct()
            if (ComponentType.RECURRENCE_RULE in compTypes) tags += "周期"
            if (ComponentType.AMAP_ROUTE in compTypes) tags += "含路线"
            if (ComponentType.LINK_BOOKMARK in compTypes) tags += "含链接"
            if (ComponentType.FILE_ATTACHMENT in compTypes) tags += "含附件"
            val tagStr = if (tags.isNotEmpty()) " [${tags.joinToString("·")}]" else ""
            val descStr = task.description?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { " - ${it.take(36)}" } ?: ""
            "【${task.title}】(分类:$catName$tagStr)$descStr"
        }
        return "最近${prioritized.size}条核心任务:\n$lines"
    }

    private suspend fun handleTaskTimingStats(
        tasks: List<Task>,
        focusSessions30d: List<FocusSessionEntity>,
        days: Int
    ): String {
        val now = System.currentTimeMillis()
        val cutoffTime = now - (days * WINDOW_30D_MS / 30L)
        val recentCompleted = tasks.filter {
            it.isDone && it.completedAt != null && it.completedAt >= cutoffTime && it.deadline > 0
        }

        val recentOverdue = tasks.filter {
            !it.isDone && (it.isExpired || (it.deadline in 1 until now))
        }

        val effectiveFocus = if (days == 30) focusSessions30d else focusSessionDao.getSessionsSinceSync(cutoffTime)
        val validFocus = effectiveFocus.filter { it.actualSeconds >= 60L }
        val focusStats = if (validFocus.isNotEmpty()) {
            val totalMin = validFocus.sumOf { it.actualSeconds } / 60L
            val completedPomodoros = validFocus.count { it.mode == "POMODORO" && it.completed }
            val avgMin = totalMin.toDouble() / validFocus.size
            "近${days}天有效专注统计: ${validFocus.size}次专注，累计${totalMin}分钟（单次均值${String.format(Locale.US, "%.1f", avgMin)}分钟），完整完成番茄钟${completedPomodoros}次。"
        } else {
            "近${days}天无有效专注计时记录。"
        }

        if (recentCompleted.isEmpty() && recentOverdue.isEmpty()) {
            return "最近${days}天无有效的完成时间统计数据。\n$focusStats"
        }

        val timingData = recentCompleted.map { task ->
            val diff = task.completedAt!! - task.deadline
            val hours = diff / (1000 * 60 * 60)
            when {
                hours < -24 -> "提前1天以上"
                hours < -2 -> "提前数小时"
                hours < 2 -> "准时踩点完成"
                hours < 24 -> "延后数小时"
                else -> "延后1天以上"
            }
        }

        val distribution = timingData.groupingBy { it }.eachCount()
        return """
最近${days}天完成任务时间分布 (共${recentCompleted.size}条):
${if (distribution.isEmpty()) "无完成且有截止时间的任务" else distribution.entries.joinToString("\n") { "- ${it.key}: ${it.value}次" }}

当前未完成的逾期任务数: ${recentOverdue.size}
$focusStats
        """.trimIndent()
    }

    private fun handleSubtaskUsage(
        allComposites: List<TaskDetailComposite>,
        allTasks: List<Task>
    ): String {
        val withSubtasks = allComposites.filter { it.subTasks.isNotEmpty() }
        if (withSubtasks.isEmpty()) {
            return "用户暂未使用子任务功能。"
        }
        val allSubTasks = withSubtasks.flatMap { it.subTasks }
        val completedSubtasks = allSubTasks.count { it.isCompleted }
        val completionRate = (completedSubtasks * 100) / allSubTasks.size
        // 以跨度>=2小时或含备注的任务作为复杂任务分母参考，避免日常小便签拉低规划评估
        val complexCandidates = allTasks.count {
            (it.deadline - it.startTime) >= 2 * 3600_000L || !it.description.isNullOrBlank()
        }.coerceAtLeast(withSubtasks.size)

        return """
子任务规划统计:
- 使用子任务的主任务数: ${withSubtasks.size} / ${allTasks.size}（中长跨度/复杂任务中占比约 ${(withSubtasks.size * 100) / complexCandidates}%）
- 子任务总步骤数: ${allSubTasks.size} (平均每任务 ${String.format(Locale.US, "%.1f", allSubTasks.size.toDouble() / withSubtasks.size)} 步)
- 已完成子任务: $completedSubtasks (${completionRate}%)
        """.trimIndent()
    }

    private fun handleCategoryDistribution(
        tasks: List<Task>,
        categories: List<Category>,
        days: Int
    ): String {
        val cutoffTime = System.currentTimeMillis() - (days * WINDOW_30D_MS / 30L)
        val recentTasks = tasks.filter { it.createdAt >= cutoffTime }.ifEmpty { tasks }

        if (recentTasks.isEmpty()) {
            return "最近${days}天无任务数据。"
        }

        val catMap = categories.associateBy { it.id }
        val distribution = recentTasks.groupBy { it.categoryId }.mapValues { it.value.size }

        val result = StringBuilder("最近${days}天任务分类分布 (共${recentTasks.size}个):\n")
        distribution.entries.sortedByDescending { it.value }.forEach { (catId, count) ->
            val catName = catMap[catId]?.name ?: "未知"
            val percentage = (count * 100) / recentTasks.size
            result.append("- $catName: $count 个 (${percentage}%)\n")
        }

        return result.toString().trim()
    }

    private suspend fun handleRoutePreferences(): String {
        val routeComponents = taskComponentDao.getComponentsByTypeSync(ComponentType.AMAP_ROUTE)
        return summarizeRoutePreferences(routeComponents)
    }

    private suspend fun handleLocationFootprints(): String {
        val locations = userLocationDao.getAllLocationsSync()
        if (locations.isEmpty()) return "暂无地点活动记录。"

        fun formatLocation(loc: com.litetask.app.data.model.UserLocationEntity, count: Int): String {
            val typeTag = if (loc.locationType != com.litetask.app.data.model.UserLocationEntity.TYPE_FREQUENT) {
                "[${loc.locationType}]"
            } else ""
            val addressPart = loc.address.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            return "$typeTag${loc.name}(${count}次$addressPart)"
        }

        val labeled = locations.filter { it.locationType != com.litetask.app.data.model.UserLocationEntity.TYPE_FREQUENT }
        val topOrigins = locations.filter { it.originCount > 0 }.sortedByDescending { it.originCount }.take(5)
        val topDests = locations.filter { it.destinationCount > 0 }.sortedByDescending { it.destinationCount }.take(5)
        return buildString {
            appendLine("地点活动足迹:")
            if (labeled.isNotEmpty()) {
                appendLine(
                    "- 已标记锚点地点: " + labeled.joinToString(", ") { loc ->
                        val addr = loc.address.takeIf { it.isNotBlank() }?.let { "($it)" }.orEmpty()
                        "[${loc.locationType}]${loc.name}$addr"
                    }
                )
            }
            appendLine("- 常在地(出发): ${if (topOrigins.isEmpty()) "暂无" else topOrigins.joinToString(", ") { formatLocation(it, it.originCount) }}")
            append("- 常去地(目的): ${if (topDests.isEmpty()) "暂无" else topDests.joinToString(", ") { formatLocation(it, it.destinationCount) }}")
        }.trim()
    }

    private fun parseAnalysisResult(
        analysisResult: String,
        completionRate: Double,
        delayedRate: Double,
        inputPreference: String,
        categoryFocus: String
    ): Result<UserProfileHistoryEntity> = parseProfileJsonToEntity(
        analysisResult = analysisResult,
        completionRate = completionRate,
        delayedRate = delayedRate,
        inputPreference = inputPreference,
        categoryFocus = categoryFocus
    )
}