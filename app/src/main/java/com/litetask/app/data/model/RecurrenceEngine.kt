package com.litetask.app.data.model

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 周期重复任务的纯 Kotlin 调度计算引擎（使用 java.time，确保夏令时/闰年/大小月月末零漂移，且可 100% JVM 单测）。
 */
object RecurrenceEngine {

    enum class RecurrenceFreq(val code: String, val label: String) {
        DAILY("DAILY", "每天"),
        WEEKLY("WEEKLY", "每周"),
        MONTHLY("MONTHLY", "每月"),
        YEARLY("YEARLY", "每年"),
        INTERVAL_DAYS("INTERVAL_DAYS", "每 N 天");

        companion object {
            fun fromCode(raw: String?): RecurrenceFreq =
                entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: WEEKLY
        }
    }

    enum class RecurrenceEndType(val code: String, val label: String) {
        NEVER("NEVER", "永不结束"),
        AFTER_COUNT("AFTER_COUNT", "限定次数"),
        UNTIL_DATE("UNTIL_DATE", "截至日期");

        companion object {
            fun fromCode(raw: String?): RecurrenceEndType =
                entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: NEVER
        }
    }

    data class OccurrenceWindow(
        val startTimeMillis: Long,
        val deadlineMillis: Long
    )

    data class RecurrenceSpawnResult(
        val sourceTaskId: Long,
        val sourceRuleComponentId: Long,
        val nextTask: Task,
        val nextRuleComponentId: Long
    )

    private val shortTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault())

    /**
     * 基于当前任务窗口与重复规则计算下一期时间窗口。
     * - 若已暂停 / 剩余次数<=1 / 超出结束日期，返回 null；
     * - 若当前任务已逾期跨越多周期，自动快进跳过已过期周期直到下一未过期窗口。
     */
    fun computeNextOccurrence(
        taskStartTimeMs: Long,
        taskDeadlineMs: Long,
        rule: RecurrenceRuleData,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): OccurrenceWindow? {
        if (rule.isPaused) return null

        val endType = RecurrenceEndType.fromCode(rule.endType)
        if (endType == RecurrenceEndType.AFTER_COUNT && (rule.remainingCount ?: 0) <= 1) {
            return null
        }

        val effectiveStartMs = if (taskStartTimeMs > 0L) taskStartTimeMs else taskDeadlineMs
        val durationMs = (taskDeadlineMs - effectiveStartMs).coerceAtLeast(0L)
        val anchorDay = rule.dayOfMonth ?: Instant.ofEpochMilli(effectiveStartMs).atZone(zoneId).dayOfMonth

        var nextStartZdt = advanceOneStep(
            current = Instant.ofEpochMilli(effectiveStartMs).atZone(zoneId),
            rule = rule,
            originalAnchorDay = anchorDay
        )

        var safety = 0
        while (nextStartZdt.toInstant().toEpochMilli() + durationMs <= nowMs && safety < 1000) {
            nextStartZdt = advanceOneStep(
                current = nextStartZdt,
                rule = rule,
                originalAnchorDay = anchorDay
            )
            safety++
        }

        val nextStartMs = nextStartZdt.toInstant().toEpochMilli()
        val nextDeadlineMs = nextStartMs + durationMs

        if (endType == RecurrenceEndType.UNTIL_DATE && rule.untilEpochMs != null) {
            if (nextStartMs > rule.untilEpochMs) return null
        }

        return OccurrenceWindow(
            startTimeMillis = nextStartMs,
            deadlineMillis = nextDeadlineMs
        )
    }

    /**
     * 预览未来最多 N 期发生时间（用于设置面板实时预览）。
     */
    fun previewNextOccurrences(
        taskStartTimeMs: Long,
        taskDeadlineMs: Long,
        rule: RecurrenceRuleData,
        count: Int = 3,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<OccurrenceWindow> {
        if (rule.isPaused || count <= 0) return emptyList()
        val result = mutableListOf<OccurrenceWindow>()
        var cursorStart = taskStartTimeMs
        var cursorDeadline = taskDeadlineMs
        var cursorRule = rule
        var cursorNow = nowMs

        repeat(count) {
            val next = computeNextOccurrence(
                taskStartTimeMs = cursorStart,
                taskDeadlineMs = cursorDeadline,
                rule = cursorRule,
                nowMs = cursorNow,
                zoneId = zoneId
            ) ?: return result
            result.add(next)
            cursorStart = next.startTimeMillis
            cursorDeadline = next.deadlineMillis
            cursorRule = advanceRuleForNextOccurrence(cursorRule)
            cursorNow = next.startTimeMillis
        }
        return result
    }

    /**
     * 为下一期任务生成递进后的重复规则副本。
     */
    fun advanceRuleForNextOccurrence(rule: RecurrenceRuleData): RecurrenceRuleData {
        val endType = RecurrenceEndType.fromCode(rule.endType)
        val updatedRemaining = if (endType == RecurrenceEndType.AFTER_COUNT) {
            ((rule.remainingCount ?: 1) - 1).coerceAtLeast(1)
        } else {
            rule.remainingCount
        }
        return rule.copy(
            remainingCount = updatedRemaining,
            occurrenceIndex = rule.occurrenceIndex + 1,
            generatedNextTaskId = null
        )
    }

    /**
     * 按旧任务与新任务时间锚点等距平移所有未触发提醒。
     */
    fun buildTranslatedReminders(
        sourceReminders: List<Reminder>,
        oldStartMs: Long,
        oldDeadlineMs: Long,
        newStartMs: Long,
        newDeadlineMs: Long
    ): List<Reminder> {
        if (sourceReminders.isEmpty()) return emptyList()
        val oldEffectiveStart = if (oldStartMs > 0L) oldStartMs else oldDeadlineMs
        val midpoint = oldEffectiveStart + (oldDeadlineMs - oldEffectiveStart).coerceAtLeast(0L) / 2L
        return sourceReminders.map { r ->
            val anchorDeadline = (r.label?.contains("截止") == true) ||
                (oldDeadlineMs > oldEffectiveStart && r.triggerAt >= midpoint)
            val newTriggerAt = if (anchorDeadline) {
                val offsetBeforeDeadline = oldDeadlineMs - r.triggerAt
                newDeadlineMs - offsetBeforeDeadline
            } else {
                val offsetFromStart = r.triggerAt - oldEffectiveStart
                newStartMs + offsetFromStart
            }
            Reminder(
                id = 0L,
                taskId = 0L,
                triggerAt = newTriggerAt,
                label = r.label,
                isFired = false
            )
        }
    }

    /**
     * 组件继承白名单策略：
     * - RECURRENCE_RULE: 保留（递进后）
     * - AMAP_ROUTE / LINK_BOOKMARK / FILE_ATTACHMENT: 保留通用参考组件
     * - ACCESS_PASS / VOICE_NOTE / UNKNOWN: 不继承一次性凭据与语音备忘
     */
    fun buildInheritedComponents(
        sourceComponents: List<TaskComponent>,
        updatedRule: RecurrenceRuleData
    ): List<TaskComponent> {
        val inherited = mutableListOf<TaskComponent>()
        var replacedRule = false
        sourceComponents.forEach { comp ->
            when (comp) {
                is TaskComponent.RecurrenceComponent -> {
                    if (!replacedRule) {
                        inherited.add(
                            TaskComponent.RecurrenceComponent(
                                id = 0L,
                                taskId = 0L,
                                data = updatedRule
                            )
                        )
                        replacedRule = true
                    }
                }
                is TaskComponent.AMapComponent -> {
                    inherited.add(comp.copy(id = 0L, taskId = 0L))
                }
                is TaskComponent.LinkComponent -> {
                    inherited.add(comp.copy(id = 0L, taskId = 0L))
                }
                is TaskComponent.FileComponent -> {
                    inherited.add(comp.copy(id = 0L, taskId = 0L))
                }
                is TaskComponent.AccessPassComponent,
                is TaskComponent.VoiceNoteComponent,
                is TaskComponent.UnknownComponent -> Unit
            }
        }
        if (!replacedRule) {
            inherited.add(
                0,
                TaskComponent.RecurrenceComponent(id = 0L, taskId = 0L, data = updatedRule)
            )
        }
        return inherited
    }

    fun formatRuleSummary(rule: RecurrenceRuleData): String {
        val freq = RecurrenceFreq.fromCode(rule.freq)
        val interval = rule.interval.coerceAtLeast(1)
        val base = when (freq) {
            RecurrenceFreq.DAILY -> if (interval == 1) "每天" else "每 $interval 天"
            RecurrenceFreq.INTERVAL_DAYS -> "每 $interval 天"
            RecurrenceFreq.WEEKLY -> {
                val days = rule.daysOfWeek.filter { it in 1..7 }.distinct().sorted()
                val weekPrefix = if (interval == 1) "每周" else "每 $interval 周"
                if (days.isEmpty()) {
                    weekPrefix
                } else {
                    "$weekPrefix ${days.joinToString("、") { dayOfWeekShortLabel(it) }}"
                }
            }
            RecurrenceFreq.MONTHLY -> {
                val monthPrefix = if (interval == 1) "每月" else "每 $interval 个月"
                val dayPart = rule.dayOfMonth?.let { "${it}日" }.orEmpty()
                "$monthPrefix$dayPart"
            }
            RecurrenceFreq.YEARLY -> if (interval == 1) "每年" else "每 $interval 年"
        }

        val suffix = when (RecurrenceEndType.fromCode(rule.endType)) {
            RecurrenceEndType.AFTER_COUNT -> rule.remainingCount?.let { " · 剩${it}次" }.orEmpty()
            RecurrenceEndType.UNTIL_DATE -> rule.untilEpochMs?.let {
                " · 截至${formatDateOnly(it)}"
            }.orEmpty()
            RecurrenceEndType.NEVER -> ""
        }
        return if (rule.isPaused) "$base (已暂停)$suffix" else "$base$suffix"
    }

    fun dayOfWeekShortLabel(isoDay: Int): String = when (isoDay) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        7 -> "周日"
        else -> ""
    }

    fun formatShortDateTime(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).format(shortTimeFormatter)

    private fun formatDateOnly(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate().toString()

    /**
     * 自然语言正则兜底提取常见周期表达（供端侧或轻量模型缺省时补充）。
     */
    fun extractFallbackFromText(rawText: String, baseStartMs: Long = System.currentTimeMillis()): RecurrenceRuleData? {
        val text = rawText.trim()
        if (text.isBlank()) return null

        val intervalDaysMatch = Regex("""每\s*(\d+)\s*天""").find(text)
        if (intervalDaysMatch != null) {
            val n = intervalDaysMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 365) ?: 1
            return RecurrenceRuleData(
                freq = if (n == 1) RecurrenceFreq.DAILY.code else RecurrenceFreq.INTERVAL_DAYS.code,
                interval = n
            )
        }

        if (text.contains("每天") || text.contains("每日")) {
            return RecurrenceRuleData(freq = RecurrenceFreq.DAILY.code, interval = 1)
        }
        if (text.contains("工作日")) {
            return RecurrenceRuleData(
                freq = RecurrenceFreq.WEEKLY.code,
                interval = 1,
                daysOfWeek = listOf(1, 2, 3, 4, 5)
            )
        }

        val weeklyPrefix = Regex("""每\s*(\d+)?\s*周(?:[一二三四五六日天、,，和\s]+)""").find(text)
        if (weeklyPrefix != null || text.contains("每周") || text.contains("每星期")) {
            val interval = Regex("""每\s*(\d+)\s*周""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 52) ?: 1
            val days = parseWeekdaysFromChinese(text)
            return RecurrenceRuleData(
                freq = RecurrenceFreq.WEEKLY.code,
                interval = interval,
                daysOfWeek = days
            )
        }

        val monthlyMatch = Regex("""每\s*(\d+)?\s*个?月\s*(\d{1,2})?\s*[日号]?""").find(text)
        if (monthlyMatch != null) {
            val interval = monthlyMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 24) ?: 1
            val defaultDom = Instant.ofEpochMilli(baseStartMs).atZone(ZoneId.systemDefault()).dayOfMonth
            val dom = monthlyMatch.groupValues[2].toIntOrNull()?.coerceIn(1, 31) ?: defaultDom
            return RecurrenceRuleData(
                freq = RecurrenceFreq.MONTHLY.code,
                interval = interval,
                dayOfMonth = dom
            )
        }

        if (text.contains("每年")) {
            return RecurrenceRuleData(freq = RecurrenceFreq.YEARLY.code, interval = 1)
        }
        return null
    }

    private fun parseWeekdaysFromChinese(text: String): List<Int> {
        val result = linkedSetOf<Int>()
        val window = Regex("""(?:每\s*\d*\s*[周星期]+)([^，。,.\n]{0,16})""").find(text)?.groupValues?.getOrNull(1)
            ?: text
        if (window.contains('一')) result.add(1)
        if (window.contains('二')) result.add(2)
        if (window.contains('三')) result.add(3)
        if (window.contains('四')) result.add(4)
        if (window.contains('五')) result.add(5)
        if (window.contains('六')) result.add(6)
        if (window.contains('日') || window.contains('天')) result.add(7)
        return result.sorted()
    }

    private fun advanceOneStep(
        current: ZonedDateTime,
        rule: RecurrenceRuleData,
        originalAnchorDay: Int
    ): ZonedDateTime {
        val step = rule.interval.coerceAtLeast(1).toLong()
        return when (RecurrenceFreq.fromCode(rule.freq)) {
            RecurrenceFreq.DAILY, RecurrenceFreq.INTERVAL_DAYS -> current.plusDays(step)
            RecurrenceFreq.WEEKLY -> {
                val days = rule.daysOfWeek.filter { it in 1..7 }.distinct().sorted()
                if (days.isEmpty()) {
                    current.plusWeeks(step)
                } else {
                    val curDow = current.dayOfWeek.value
                    val nextInWeek = days.firstOrNull { it > curDow }
                    if (nextInWeek != null) {
                        current.plusDays((nextInWeek - curDow).toLong())
                    } else {
                        val mondayOfCurrentWeek = current.minusDays((curDow - 1).toLong())
                        mondayOfCurrentWeek.plusWeeks(step).plusDays((days.first() - 1).toLong())
                    }
                }
            }
            RecurrenceFreq.MONTHLY -> {
                val anchor = originalAnchorDay.coerceIn(1, 31)
                val targetMonth = current.withDayOfMonth(1).plusMonths(step)
                val maxDay = targetMonth.toLocalDate().lengthOfMonth()
                targetMonth.withDayOfMonth(minOf(anchor, maxDay))
            }
            RecurrenceFreq.YEARLY -> {
                val targetYear = current.withDayOfMonth(1).plusYears(step)
                val maxDay = targetYear.toLocalDate().lengthOfMonth()
                val desiredDay = if (current.monthValue == 2 && originalAnchorDay == 29) {
                    minOf(29, maxDay)
                } else {
                    minOf(current.dayOfMonth, maxDay)
                }
                targetYear.withDayOfMonth(desiredDay)
            }
        }
    }
}
