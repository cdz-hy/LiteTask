package com.litetask.app.data.ai

import com.google.gson.JsonParser
import com.litetask.app.data.model.SubTask
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.sortedByOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * AI 子任务拆解模式
 */
enum class SubTaskGenerationMode {
    /** 基于已有子任务继续向后补充，禁止生成与已完成/现有待办重复的步骤 */
    APPEND_NEXT,
    /** 参考已完成进度，重新梳理剩余目标的一整套执行步骤 */
    REFINE_ALL
}

/**
 * 传入 AI 模型进行子任务拆解的结构化任务上下文
 */
data class SubTaskGenerationContext(
    val task: Task,
    val categoryName: String? = null,
    val existingSubTasks: List<SubTask> = emptyList(),
    val routeDestination: String? = null,
    val attachmentNames: List<String> = emptyList(),
    val attachmentTextExcerpts: List<Pair<String, String>> = emptyList(),
    val userProfileSummary: String? = null,
    val additionalContext: String = "",
    val mode: SubTaskGenerationMode = SubTaskGenerationMode.APPEND_NEXT,
    val previousGeneratedSteps: List<String> = emptyList(),
    val refinementFeedback: String = "",
    val hasImages: Boolean = false
)

/**
 * 纯 Kotlin 子任务拆解 Prompt 构建、时间窗口评估、JSON 清洗提取与历史去重支撑工具。
 */
internal object SubTaskGenerationSupport {

    private val DATE_TIME_FMT_PATTERN = "yyyy-MM-dd HH:mm"
    private val PUNCTUATION_AND_SPACE_REGEX = Regex("[\\p{P}\\p{S}\\s]+")
    private val LEADING_ENUMERATION_REGEX = Regex(
        "^(?:第\\s*[一二三四五六七八九十百\\d]+\\s*[步条点]\\s*[:：、.]?|Step\\s*\\d+\\s*[:：.]?|\\(?\\d{1,2}\\)?\\s*[.、:：)]|[•·\\-*+]\\s*|\\[[ xX✓√]?]\\s*)",
        RegexOption.IGNORE_CASE
    )
    private val BOILERPLATE_PREFIX_REGEX = Regex(
        "^(?:好的|当然|以下是|为您|已为您|拆解如下|子任务列表|执行步骤|建议步骤).{0,24}[:：]$"
    )

    data class TimeHorizonInfo(
        val totalDurationDesc: String,
        val remainingDesc: String,
        val suggestedStepRange: String
    )

    fun evaluateTimeHorizon(
        startTime: Long,
        deadline: Long,
        nowMillis: Long
    ): TimeHorizonInfo {
        val totalMs = (deadline - startTime).coerceAtLeast(0L)
        val remainingMs = deadline - nowMillis

        val totalDesc = if (totalMs > 0L) formatDurationChinese(totalMs) else "未设置固定跨度"
        val remainingDesc = when {
            deadline <= 0L -> "未设置截止时间"
            remainingMs < 0L -> "已逾期 ${formatDurationChinese(abs(remainingMs))}"
            remainingMs == 0L -> "即将到达截止时间"
            else -> "距截止还有 ${formatDurationChinese(remainingMs)}"
        }

        val suggestedRange = when {
            remainingMs in 1L..(6L * 3600_000L) || totalMs in 1L..(4L * 3600_000L) -> "2~4"
            remainingMs > 72L * 3600_000L || totalMs > 72L * 3600_000L -> "4~8"
            else -> "3~6"
        }

        return TimeHorizonInfo(
            totalDurationDesc = totalDesc,
            remainingDesc = remainingDesc,
            suggestedStepRange = suggestedRange
        )
    }

    fun formatDurationChinese(millis: Long): String {
        val minutes = (millis / 60_000L).coerceAtLeast(1L)
        val hours = minutes / 60L
        val remMinutes = minutes % 60L
        val days = hours / 24L
        val remHours = hours % 24L
        return when {
            days >= 1L && remHours > 0L -> "${days}天${remHours}小时"
            days >= 1L -> "${days}天"
            hours >= 1L && remMinutes >= 15L -> "${hours}小时${remMinutes}分钟"
            hours >= 1L -> "${hours}小时"
            else -> "${minutes}分钟"
        }
    }

    fun buildSystemPrompt(
        context: SubTaskGenerationContext,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val timeFmt = SimpleDateFormat(DATE_TIME_FMT_PATTERN, Locale.getDefault())
        val nowStr = timeFmt.format(Date(nowMillis))
        val task = context.task
        val horizon = evaluateTimeHorizon(task.startTime, task.deadline, nowMillis)

        val orderedExisting = context.existingSubTasks.sortedByOrder()
        val completedExisting = orderedExisting.filter { it.isCompleted }
        val pendingExisting = orderedExisting.filter { !it.isCompleted }

        val existingSection = buildString {
            if (orderedExisting.isEmpty()) {
                append("- 当前暂无子任务。")
            } else {
                if (completedExisting.isNotEmpty()) {
                    appendLine("- 已完成步骤（进度已达成，绝对禁止重复生成相同或同义步骤）:")
                    completedExisting.forEachIndexed { idx, sub ->
                        appendLine("  ${idx + 1}. [✓] ${sub.content}")
                    }
                }
                if (pendingExisting.isNotEmpty()) {
                    appendLine("- 现有待办步骤:")
                    pendingExisting.forEachIndexed { idx, sub ->
                        appendLine("  ${idx + 1}. [ ] ${sub.content}")
                    }
                }
                append("- 本次拆解模式: ")
                when (context.mode) {
                    SubTaskGenerationMode.APPEND_NEXT -> append(
                        "【补充后续步骤】请基于以上已有进度，只产出尚未覆盖的后续推进步骤、前置准备或关键细分动作，严禁与已有步骤重复。"
                    )
                    SubTaskGenerationMode.REFINE_ALL -> append(
                        "【重新规划待办】请尊重已完成步骤的成果（不重复已完成项），围绕剩余目标重新生成一套更完整、按先后排序的待办执行清单。"
                    )
                }
            }
        }.trimEnd()

        val metaExtras = buildString {
            context.categoryName?.takeIf { it.isNotBlank() }?.let {
                appendLine("- 分类: $it")
            }
            if (task.isPinned) {
                appendLine("- 优先级: 用户已置顶（重要任务）")
            }
            context.routeDestination?.takeIf { it.isNotBlank() }?.let {
                appendLine("- 关联地点/路线目的地: $it（若涉及出门办事/现场执行，可自然加入行前准备、路线确认或到场步骤）")
            }
            if (context.attachmentNames.isNotEmpty()) {
                appendLine("- 关联附件: ${context.attachmentNames.joinToString("、")}")
            }
            if (context.attachmentTextExcerpts.isNotEmpty()) {
                appendLine("- 关联文档正文摘要:")
                context.attachmentTextExcerpts.forEach { (name, excerpt) ->
                    appendLine("  【$name】")
                    appendLine(excerpt.trim().prependIndent("  "))
                }
            }
            context.userProfileSummary?.takeIf { it.isNotBlank() }?.let {
                appendLine("- 用户习惯参考: $it")
            }
        }.trimEnd()

        val userInstruction = buildString {
            if (context.additionalContext.isNotBlank()) {
                appendLine("- 用户补充要求（最高优先级遵循）: ${context.additionalContext.trim()}")
            } else {
                appendLine("- 用户补充要求: 未填写额外文本，请综合任务详情、时间紧迫度与上下文主动高质量拆解。")
            }
            if (context.hasImages) {
                append("- 图片/PDF 视觉参考: 用户或任务附件附带了参考图片或 PDF 页面，请结合图片中的清单、日程、表单、题目或细节作为拆解核心依据。")
            }
        }.trimEnd()

        val refinementSection = if (
            context.previousGeneratedSteps.isNotEmpty() &&
            context.refinementFeedback.isNotBlank()
        ) {
            buildString {
                appendLine("\n# Iterative Refinement (基于上一版草稿二次微调)")
                appendLine("上一版生成的步骤:")
                context.previousGeneratedSteps.forEachIndexed { index, step ->
                    appendLine("${index + 1}. $step")
                }
                append("用户微调意见（必须严格据此改写/删减/细化）: ${context.refinementFeedback.trim()}")
            }
        } else {
            ""
        }

        return """
# Role: LiteTask 智能任务拆解专家
# Context: Now = $nowStr
# Goal: 为主任务拆解出建议 ${horizon.suggestedStepRange} 个高可执行性子任务步骤（若用户补充要求或微调意见指定了步数/重点，以用户要求为准），并以纯 JSON 字符串数组返回。

# Task Details
- 标题: ${task.title}
- 描述: ${task.description?.takeIf { it.isNotBlank() } ?: "无"}
- 时间窗口: ${timeFmt.format(Date(task.startTime))} 至 ${timeFmt.format(Date(task.deadline))}
- 跨度与紧迫度: 总跨度 ${horizon.totalDurationDesc}，${horizon.remainingDesc}
${if (metaExtras.isNotBlank()) "$metaExtras\n" else ""}
# Existing SubTasks
$existingSection

# User Instruction
$userInstruction$refinementSection

# Rules
1. **明确可执行**: 每步尽量采用「动作动词 + 具体对象/产物或完成标准」（例如“梳理竞品核心功能并列3条差异对比”），避免“准备材料”“开始开发”“检查一下”这类空洞套话。
2. **篇幅适中**: 每步建议 8~28 个字，既能表达清楚关键动作和交付物，又保持清单利落好读，不要为了凑短而失去信息量。
3. **逻辑严谨**: 按真实执行先后顺序排列；若是长周期或重验收任务，自然覆盖「准备 -> 核心推进 -> 检查/收尾」。
4. **严格去重**: 禁止重复已有已完成步骤；在【补充后续步骤】模式下同时禁止重复现有待办步骤。

# Output Format
仅返回一个合法的纯 JSON 字符串数组，不要包含 Markdown 代码块标记（```）、序号前缀或任何说明文字。
示例: ["第一步具体动作", "第二步具体动作", "第三步具体动作"]
        """.trimIndent()
    }

    fun parseAndCleanSubTasks(
        rawContent: String,
        existingSubTasks: List<SubTask> = emptyList(),
        mode: SubTaskGenerationMode = SubTaskGenerationMode.APPEND_NEXT,
        maxSteps: Int = 12
    ): Result<List<String>> {
        val stripped = stripCodeFences(rawContent).trim()
        if (stripped.isBlank()) {
            return Result.failure(Exception("AI 返回内容为空，未能生成子任务"))
        }

        val rawItems = extractJsonArrayItems(stripped) ?: extractFallbackLineItems(stripped)

        val cleanedCandidates = rawItems
            .mapNotNull { cleanSingleStepText(it) }
            .filter { it.length >= 2 && !BOILERPLATE_PREFIX_REGEX.matches(it) }

        if (cleanedCandidates.isEmpty()) {
            return Result.failure(Exception("未能生成有效的子任务步骤，请补充更多任务描述后重试"))
        }

        val blockedFingerprints = when (mode) {
            SubTaskGenerationMode.APPEND_NEXT -> existingSubTasks
            SubTaskGenerationMode.REFINE_ALL -> existingSubTasks.filter { it.isCompleted }
        }.map { normalizeForDedup(it.content) }.filter { it.isNotEmpty() }.toMutableSet()

        val result = mutableListOf<String>()
        for (candidate in cleanedCandidates) {
            val fingerprint = normalizeForDedup(candidate)
            if (fingerprint.isEmpty()) continue
            if (blockedFingerprints.add(fingerprint)) {
                result.add(candidate)
                if (result.size >= maxSteps) break
            }
        }

        if (result.isEmpty()) {
            return Result.failure(
                Exception("AI 生成的步骤与当前已有子任务高度重复，建议补充更具体的要求或切换为“重新规划待办”")
            )
        }

        return Result.success(result)
    }

    fun buildSubTaskHistorySummary(
        mode: SubTaskGenerationMode,
        additionalContext: String,
        refinementFeedback: String,
        generatedSteps: List<String>
    ): String {
        val modeLabel = when (mode) {
            SubTaskGenerationMode.APPEND_NEXT -> "补充后续步骤"
            SubTaskGenerationMode.REFINE_ALL -> "重新规划待办"
        }
        return buildString {
            append("[$modeLabel]")
            if (additionalContext.isNotBlank()) {
                append(" 补充要求：").append(additionalContext.trim())
            }
            if (refinementFeedback.isNotBlank()) {
                append(" | 微调：").append(refinementFeedback.trim())
            }
            if (generatedSteps.isNotEmpty()) {
                appendLine()
                generatedSteps.forEachIndexed { idx, step ->
                    if (idx > 0) append('\n')
                    append("${idx + 1}. $step")
                }
            }
        }.trim()
    }

    private fun extractJsonArrayItems(text: String): List<String>? {
        val firstBracket = text.indexOf('[')
        val lastBracket = text.lastIndexOf(']')
        if (firstBracket < 0 || lastBracket <= firstBracket) return null
        val jsonSlice = text.substring(firstBracket, lastBracket + 1)
        return try {
            val parsed = JsonParser.parseString(jsonSlice)
            if (!parsed.isJsonArray) return null
            parsed.asJsonArray.mapNotNull { elem ->
                when {
                    elem.isJsonPrimitive -> elem.asString
                    elem.isJsonObject -> {
                        val obj = elem.asJsonObject
                        obj.get("content")?.takeIf { it.isJsonPrimitive }?.asString
                            ?: obj.get("step")?.takeIf { it.isJsonPrimitive }?.asString
                            ?: obj.get("title")?.takeIf { it.isJsonPrimitive }?.asString
                            ?: obj.get("task")?.takeIf { it.isJsonPrimitive }?.asString
                    }
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractFallbackLineItems(text: String): List<String> {
        return text.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() &&
                    line != "[" &&
                    line != "]" &&
                    !line.startsWith("```")
            }
            .map { line -> line.removeSuffix(",").trim() }
    }

    private fun cleanSingleStepText(raw: String): String? {
        var text = raw.trim()
        if (text.isEmpty()) return null
        text = stripEnclosingQuotes(text)
        // 循环剥离可能嵌套的前导标号，如 "1. [ ] 第一步"
        repeat(2) {
            val next = text.replaceFirst(LEADING_ENUMERATION_REGEX, "").trim()
            if (next == text) return@repeat
            text = next
        }
        text = stripEnclosingQuotes(text).replace(Regex("\\s+"), " ").trim()
        return text.takeIf { it.isNotEmpty() }
    }

    private fun stripEnclosingQuotes(input: String): String {
        var s = input.trim()
        if ((s.startsWith("\"") && s.endsWith("\"")) ||
            (s.startsWith("'") && s.endsWith("'")) ||
            (s.startsWith("“") && s.endsWith("”"))
        ) {
            if (s.length >= 2) {
                s = s.substring(1, s.length - 1).trim()
            }
        }
        return s
    }

    private fun normalizeForDedup(input: String): String {
        return input
            .lowercase(Locale.ROOT)
            .replace(PUNCTUATION_AND_SPACE_REGEX, "")
    }

    private fun stripCodeFences(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("```")) {
            val firstNewline = clean.indexOf('\n')
            clean = if (firstNewline != -1) {
                clean.substring(firstNewline + 1)
            } else {
                clean.removePrefix("```json").removePrefix("```")
            }
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```")
        }
        return clean.trim()
    }
}
