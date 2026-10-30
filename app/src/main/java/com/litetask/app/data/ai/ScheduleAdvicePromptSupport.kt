package com.litetask.app.data.ai

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.litetask.app.data.model.ComponentType
import com.litetask.app.data.model.FocusSessionEntity
import com.litetask.app.data.model.TaskComponent
import com.litetask.app.data.model.TaskComponentRegistry
import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.data.model.UserLocationEntity
import com.litetask.app.data.model.UserProfileHistoryEntity
import com.litetask.app.data.model.sortedByOrder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 日程规划建议的纯逻辑支撑模块：
 * 负责线程安全时间格式化、TaskDetailComposite 结构化摘要、Direct/Agent 共用 Prompt 构建以及 JSON 鲁棒解析。
 */
internal object ScheduleAdvicePromptSupport {

    private val FULL_DATETIME_FMT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val SHORT_DATETIME_FMT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)
    private val HEADER_NOW_FMT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE", Locale.CHINESE)

    fun formatDateTime(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        if (epochMillis <= 0L) return ""
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(FULL_DATETIME_FMT)
    }

    fun formatShortDateTime(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        if (epochMillis <= 0L) return ""
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(SHORT_DATETIME_FMT)
    }

    fun formatHeaderNow(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(HEADER_NOW_FMT)
    }

    fun flattenSnippet(raw: String?, maxChars: Int = 60): String {
        val cleaned = raw.orEmpty().replace(Regex("\\s+"), " ").trim()
        if (cleaned.length <= maxChars) return cleaned
        return cleaned.take(maxChars).trimEnd() + "..."
    }

    fun extractRouteDestination(composite: TaskDetailComposite): String? {
        val routeEntity = composite.components.firstOrNull { it.type == ComponentType.AMAP_ROUTE }
            ?: return null
        return runCatching {
            val comp = routeEntity.toTaskComponent() as? TaskComponent.AMapComponent
            comp?.data?.endName?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    fun toTaskJson(
        composite: TaskDetailComposite,
        nowMillis: Long,
        includeDetails: Boolean = false,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): JsonObject {
        val t = composite.task
        val overdue = !t.isDone && t.deadline in 1..nowMillis
        val routeDest = extractRouteDestination(composite)
        return JsonObject().apply {
            addProperty("id", t.id)
            addProperty("title", t.title)
            addProperty("category", composite.category?.name ?: "默认")
            addProperty("start_time", formatDateTime(t.startTime, zoneId))
            addProperty("deadline", formatDateTime(t.deadline, zoneId))
            addProperty("is_pinned", t.isPinned)
            addProperty("is_overdue", overdue)
            if (overdue) {
                val overdueHours = ((nowMillis - t.deadline) / 3600_000L).coerceAtLeast(1L)
                addProperty("overdue_hours", overdueHours)
            }
            if (composite.subTasks.isNotEmpty()) {
                val doneCount = composite.subTasks.count { it.isCompleted }
                addProperty("subtask_progress", "$doneCount/${composite.subTasks.size}")
            }
            addProperty("has_route", routeDest != null)
            if (routeDest != null) {
                addProperty("route_destination", routeDest)
            }
            if (composite.reminders.isNotEmpty()) {
                addProperty("reminder_count", composite.reminders.size)
            }
            if (t.totalFocusSeconds > 0L) {
                addProperty("focused_minutes", t.totalFocusSeconds / 60L)
            }
            if (includeDetails) {
                addProperty("description", t.description)
                addProperty("is_done", t.isDone)
                addProperty("is_expired", t.isExpired)
                if (composite.subTasks.isNotEmpty()) {
                    add("sub_tasks", JsonArray().apply {
                        composite.subTasks.sortedByOrder().forEach { st ->
                            add(JsonObject().apply {
                                addProperty("content", st.content)
                                addProperty("is_completed", st.isCompleted)
                            })
                        }
                    })
                }
                if (composite.reminders.isNotEmpty()) {
                    add("reminders", JsonArray().apply {
                        composite.reminders.forEach { r ->
                            add(JsonObject().apply {
                                addProperty("trigger_at", formatDateTime(r.triggerAt, zoneId))
                                if (!r.label.isNullOrBlank()) addProperty("label", r.label)
                                addProperty("is_fired", r.isFired)
                            })
                        }
                    })
                }
                if (composite.components.isNotEmpty()) {
                    add("components", JsonArray().apply {
                        composite.components.forEach { entity ->
                            runCatching { entity.toTaskComponent() }.getOrNull()?.let { comp ->
                                TaskComponentRegistry.toAiJsonObject(comp)?.let { add(it) }
                            }
                        }
                    })
                }
            } else {
                val snippet = flattenSnippet(t.description)
                if (snippet.isNotEmpty()) {
                    addProperty("description_snippet", snippet)
                }
            }
        }
    }

    fun formatUserProfileJson(
        profile: UserProfileHistoryEntity?,
        anchoredLocations: List<UserLocationEntity>
    ): JsonObject? {
        if (profile == null && anchoredLocations.isEmpty()) return null
        return JsonObject().apply {
            if (profile != null) {
                addProperty("completionRate", profile.completionRate)
                addProperty("delayedRate", profile.delayedRate)
                addProperty("identity", profile.identity ?: "")
                addProperty("industry", profile.industry ?: "")
                addProperty("travelPreference", profile.travelPreference ?: "")
                addProperty("delayIndex", profile.delayIndex)
                addProperty("planAbility", profile.planAbility)
                addProperty("stressLevel", profile.stressLevel)
                addProperty("stressScaleNote", "1轻松~5压力极大")
                addProperty("executionRhythm", profile.executionRhythm ?: "")
                addProperty("aiTone", profile.aiTone ?: "")
                addProperty("personality", profile.personality ?: "")
            }
            if (anchoredLocations.isNotEmpty()) {
                add("commonLocations", JsonArray().apply {
                    anchoredLocations.forEach { loc ->
                        add(JsonObject().apply {
                            addProperty("name", loc.name)
                            addProperty("locationType", loc.locationType)
                            if (loc.address.isNotBlank()) addProperty("address", loc.address)
                            if (loc.longitude != 0.0 || loc.latitude != 0.0) {
                                addProperty("longitude", loc.longitude)
                                addProperty("latitude", loc.latitude)
                            }
                        })
                    }
                })
            }
        }
    }

    fun formatProfileSummaryText(
        profile: UserProfileHistoryEntity?,
        anchoredLocations: List<UserLocationEntity>
    ): String {
        val profilePart = if (profile != null) {
            buildString {
                append("用户画像: 完成率${(profile.completionRate * 100).toInt()}%, 拖延率${(profile.delayedRate * 100).toInt()}%, ")
                append("身份:${profile.identity ?: "未知"}, 行业:${profile.industry ?: "未知"}, ")
                append("出行偏好:${profile.travelPreference ?: "未知"}, 拖延指数:${profile.delayIndex}/5, ")
                append("规划能力:${profile.planAbility}/5, 当前压力:${profile.stressLevel}/5(1轻松~5极大), ")
                append("执行节奏:${profile.executionRhythm ?: "未知"}, 推荐沟通语气:${profile.aiTone ?: "默认"}, ")
                append("特征:${profile.personality ?: "无"}")
            }
        } else {
            "暂无用户画像"
        }

        if (anchoredLocations.isEmpty()) return profilePart
        val locPart = anchoredLocations.joinToString("; ") { loc ->
            val addr = loc.address.takeIf { it.isNotBlank() }?.let { "($it)" } ?: ""
            "${loc.locationType}:${loc.name}$addr"
        }
        return "$profilePart\n常用锚定地点: $locPart"
    }

    fun formatTasksForDirectPrompt(
        upcoming: List<TaskDetailComposite>,
        overdue: List<TaskDetailComposite>,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (upcoming.isEmpty() && overdue.isEmpty()) {
            return "当前没有未来未完成任务或已逾期未处理任务。"
        }
        val sb = StringBuilder()
        if (overdue.isNotEmpty()) {
            sb.appendLine("【已逾期未完成任务（共${overdue.size}条）】")
            overdue.forEach { sb.appendLine(formatSingleCompositeLine(it, nowMillis, isOverdue = true, zoneId = zoneId)) }
        }
        if (upcoming.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.appendLine()
            sb.appendLine("【未来未完成任务（共${upcoming.size}条）】")
            upcoming.forEach { sb.appendLine(formatSingleCompositeLine(it, nowMillis, isOverdue = false, zoneId = zoneId)) }
        }
        return sb.toString().trimEnd()
    }

    private fun formatSingleCompositeLine(
        composite: TaskDetailComposite,
        nowMillis: Long,
        isOverdue: Boolean,
        zoneId: ZoneId
    ): String {
        val t = composite.task
        val cat = composite.category?.name ?: "默认"
        val tags = mutableListOf<String>()
        if (t.isPinned) tags += "置顶"
        if (isOverdue && t.deadline > 0L) {
            val hrs = ((nowMillis - t.deadline) / 3600_000L).coerceAtLeast(1L)
            tags += "已逾期${hrs}小时"
        }
        if (composite.subTasks.isNotEmpty()) {
            tags += "子任务${composite.subTasks.count { it.isCompleted }}/${composite.subTasks.size}"
        }
        extractRouteDestination(composite)?.let { dest ->
            tags += "路线目的地:$dest"
        }
        if (composite.reminders.isNotEmpty()) {
            tags += "提醒${composite.reminders.size}个"
        }
        if (t.totalFocusSeconds > 0L) {
            tags += "已专注${t.totalFocusSeconds / 60L}分"
        }
        val snippet = flattenSnippet(t.description, 45)
        val tagSuffix = if (tags.isNotEmpty()) " [${tags.joinToString(", ")}]" else ""
        val descSuffix = if (snippet.isNotEmpty()) " 备注:$snippet" else ""
        return "- [id=${t.id}] [$cat] ${t.title} (${formatDateTime(t.startTime, zoneId)} ~ ${formatDateTime(t.deadline, zoneId)})$tagSuffix$descSuffix"
    }

    fun formatRecentFocusSummaryText(
        sessions: List<FocusSessionEntity>,
        completedRecent: List<TaskDetailComposite>
    ): String {
        val focusPart = if (sessions.isNotEmpty()) {
            val totalMin = sessions.sumOf { it.actualSeconds } / 60L
            val avgMin = totalMin.toDouble() / sessions.size
            "近14天专注${sessions.size}次，累计${totalMin}分钟，单次均值${String.format(Locale.US, "%.1f", avgMin)}分钟。"
        } else {
            "近14天暂无专注计时记录。"
        }
        val completedPart = if (completedRecent.isNotEmpty()) {
            val sampleTitles = completedRecent.take(5).joinToString("、") { "「${it.task.title}」" }
            "近14天已完成${completedRecent.size}项任务（如$sampleTitles）。"
        } else {
            "近14天暂无已完成任务。"
        }
        return "$focusPart$completedPart"
    }

    fun buildSharedOutputAndConstraintRules(
        currentDate: String,
        toneHint: String?
    ): String {
        val toneLine = if (!toneHint.isNullOrBlank() && toneHint != "默认") {
            "\n6. **沟通语气**: 建议内容请贴合用户偏好语气「$toneHint」，同时保证指令清晰、可直接行动。"
        } else ""

        return """
            # 核心输出规则 (当前时间: $currentDate)
            1. **禁止 Emoji**: 严禁在任何输出字段中使用表情符号。
            2. **隐藏数字 ID**: 严禁在 `content`、`info`、`task_title`、`topic`、`advice_type` 等用户可见文本中写任务 ID，必须使用【任务标题】指代；ID 仅可出现在 `task_id` 与 `related_task_ids` 结构化字段中，且不可编造未出现过的任务 ID。
            3. **关联与数量约束**:
               - 当任务非空时：`short_term` 生成 **2 至 5 条**高价值执行建议（覆盖今日最紧急、重要或存在冲突的任务），`long_term` 生成 **1 至 3 条**规划或习惯/风险建议。
               - 若用户当前没有任何未完成及逾期任务，直接返回 `{"short_term":[],"long_term":[]}`，严禁臆造虚拟任务。
               - 单任务建议设置 `"task_id": 123`；多任务统筹/批量清理类建议将 `"task_id": null` 并填写 `"related_task_ids": [123, 456]`。
            4. **时间合法性**: 若提供 `suggested_start` 或 `suggested_deadline`，格式必须为 `"yyyy-MM-dd HH:mm"`，必须晚于或等于当前时间 `$currentDate`，且 `suggested_start < suggested_deadline`；无需调整时间时省略或置 `null`。
            5. **动作枚举与专注衔接**:
               - `action_type` 仅允许: `DECOMPOSE`(拆解子任务)、`RESCHEDULE`(调整时间)、`ROUTE`(规划出行路线)、`FOCUS`(立即开启专注)、`DETAIL`(查看任务详情)、`NONE`(通用统筹/无需特定按钮)。
               - 当 `action_type` 为 `FOCUS` 时，请补充整数 `"suggested_focus_minutes"`（如 `25`、`45`）。
               - 已完成任务仅作参考，严禁作为待办建议推荐。$toneLine

            # 输出 JSON 结构 (直接返回纯 JSON，严禁使用 Markdown 代码块包裹)
            {
              "short_term": [
                {
                  "task_id": 123,
                  "related_task_ids": [123],
                  "task_title": "任务标题或统筹主题",
                  "advice_type": "执行建议 | 出行规划 | 统筹安排 | 逾期止损",
                  "content": "明确、可执行的具体行动建议",
                  "info": "补充关键信息(如: 预计专注45分钟，避开晚高峰)",
                  "action_type": "DECOMPOSE | RESCHEDULE | ROUTE | FOCUS | DETAIL | NONE",
                  "suggested_start": "yyyy-MM-dd HH:mm",
                  "suggested_deadline": "yyyy-MM-dd HH:mm",
                  "suggested_destination": "目的地名称(仅出行建议提供)",
                  "suggested_focus_minutes": 25
                }
              ],
              "long_term": [
                {
                  "task_id": 123,
                  "related_task_ids": [123],
                  "topic": "关注任务或长期领域",
                  "advice_type": "风险预警 | 长期规划 | 习惯建议",
                  "content": "战略或节奏优化建议",
                  "urgency": "高 | 中 | 低"
                }
              ]
            }
        """.trimIndent()
    }

    fun buildAgentSystemPrompt(
        currentDate: String,
        hasAmapKey: Boolean,
        hasLocationPermission: Boolean = false,
        hasCalendarAccess: Boolean = false,
        toneHint: String? = null,
        extraToolInstructions: String = ""
    ): String {
        val envSteps = buildList {
            if (hasCalendarAccess) {
                add("- 系统日历权限已就绪：可调用 `get_system_calendar_events` 读取外部日程占用，并与 `find_free_time_slots` 交叉排查冲突、识别真实可用空闲块。")
            } else {
                add("- 调用 `find_free_time_slots` 基于已有短时段任务找出今明可用空闲块与潜在时间冲突。")
            }
            if (hasAmapKey) {
                val locNote = if (hasLocationPermission) {
                    "可通过 `get_weather`(支持多日预报)、`geocode_location`、`calculate_route`(支持 `destination_name` 自动解析且可自动兜底当前定位/常用地)与 `search_nearby_location` 核算天气与通勤风险"
                } else {
                    "可通过 `get_weather`(支持地名多日预报)、`geocode_location` 与 `calculate_route`(支持 `destination_name` 与常用出发地兜底) 核算天气与通勤耗时"
                }
                add("- 若任务涉及跨地点出行，优先依据画像 `travelPreference` 选择 `mode`，并$locNote。")
            }
        }.joinToString("\n            ")

        return """
            # Role: LiteTask 首席日程规划官 (当前时间: $currentDate)

            你是一位严谨且注重落地执行力的日程管理专家。

            # 规划原则与工具工作流
            - 先调用 `get_user_profile`、`get_incomplete_tasks` 与 `get_overdue_tasks`，全面掌握用户画像（拖延指数、压力水平 1~5、出行偏好、沟通语气 `aiTone`）、逾期积压及近期未完成任务（列表已内置分类、标签、子任务进度、路线与焦点时长）。
            - 调用 `get_focus_and_energy_pattern` 了解用户近30天专注精力峰值与黄金时段（上午/下午/晚间/深夜），优先把高难度任务安排进高精力空闲时段。
            - 对高优先、即将截止、已逾期或子任务复杂的重点任务，按需调用 `get_task_details` 获取完整备注、子任务与提醒细节。
            - 如需估时或避重复：可调用 `search_task_history` 查询历史同类任务完成表现与真实专注时长，调用 `get_previous_schedule_advice` 避免与近期已给出建议简单重复。
            ${if (envSteps.isNotBlank()) envSteps else ""}${if (extraToolInstructions.isNotBlank()) "\n            $extraToolInstructions" else ""}
            - 已逾期任务若仍可执行应给出止损与 `RESCHEDULE` / `DECOMPOSE` / `FOCUS` 建议，若大概率过时可提示及时清理。

            ${buildSharedOutputAndConstraintRules(currentDate, toneHint)}
        """.trimIndent()
    }

    fun buildDirectUserPrompt(
        currentDate: String,
        profileText: String,
        tasksText: String,
        recentFocusText: String,
        toneHint: String?,
        calendarContextText: String = "",
        freeSlotsText: String = "",
        focusEnergyText: String = "",
        weatherText: String = ""
    ): String {
        val optionalSections = listOfNotNull(
            calendarContextText.takeIf { it.isNotBlank() }?.let { "系统日历占用:\n$it" },
            freeSlotsText.takeIf { it.isNotBlank() }?.let { "今明可用时间与冲突分析:\n$it" },
            focusEnergyText.takeIf { it.isNotBlank() }?.let { "专注精力节律:\n$it" },
            weatherText.takeIf { it.isNotBlank() }?.let { "近期天气:\n$it" }
        ).joinToString("\n\n")

        val extraBlock = if (optionalSections.isNotEmpty()) "\n\n$optionalSections" else ""

        return """
            当前时间: $currentDate
            $profileText
            近期执行概览: $recentFocusText$extraBlock

            待办与逾期清单:
            $tasksText

            ${buildSharedOutputAndConstraintRules(currentDate, toneHint)}
        """.trimIndent()
    }

    fun parseAdviceJson(content: String): Result<Pair<String, String>> {
        return try {
            val cleaned = stripMarkdownFences(content)
            val jsonStr = extractOuterJsonObject(cleaned)
            val root = JsonParser.parseString(jsonStr)
            if (!root.isJsonObject) {
                throw IllegalArgumentException("JSON 根节点必须是对象")
            }
            val json = root.asJsonObject
            if (!json.has("short_term") && !json.has("long_term")) {
                throw IllegalArgumentException("JSON 缺少 short_term 或 long_term 字段")
            }

            val shortTerm = normalizeAdviceArray(json.get("short_term")).toString()
            val longTerm = normalizeAdviceArray(json.get("long_term")).toString()

            Result.success(shortTerm to longTerm)
        } catch (e: Exception) {
            Result.failure(Exception("分析结果解析失败: ${e.message}"))
        }
    }

    private fun stripMarkdownFences(raw: String): String {
        val trimmed = raw.trim()
        val fenceRegex = Regex("^```(?:json)?\\s*([\\s\\S]*?)\\s*```$", RegexOption.IGNORE_CASE)
        val match = fenceRegex.find(trimmed)
        return match?.groupValues?.getOrNull(1)?.trim() ?: trimmed
    }

    private fun extractOuterJsonObject(content: String): String {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) {
            throw IllegalArgumentException("未找到有效 JSON 对象")
        }
        return content.substring(start, end + 1)
    }

    private fun normalizeAdviceArray(raw: JsonElement?): JsonArray {
        return when {
            raw == null || raw.isJsonNull -> JsonArray()
            raw.isJsonArray -> raw.asJsonArray
            raw.isJsonObject -> JsonArray().apply { add(raw.asJsonObject) }
            else -> JsonArray()
        }
    }
}
