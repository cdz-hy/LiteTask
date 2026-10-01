package com.litetask.app.data.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.litetask.app.data.model.DailyScheduleAdviceEntity
import com.litetask.app.data.model.FocusSessionEntity
import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.data.repository.AMapWeatherReport
import com.litetask.app.util.ExternalCalendarEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 纯 Kotlin 日程上下文构建器：
 * 1. TimeSlotCalculator：综合系统日历忙碌日程与短窗口任务，过滤全天事件/长跨度DDL后提取今明可用空闲块与时间冲突。
 * 2. FocusEnergyCalculator：基于近30天专注记录与任务完成时段计算四时段精力节律与黄金专注窗口。
 * 3. PreviousAdvice & CalendarEvent 摘要格式化，均不依赖 Android Context，支持纯 JVM 单测。
 */
internal object ScheduleContextBuilder {

    private const val ALL_DAY_THRESHOLD_MS = 18L * 3600_000L
    private const val MIN_BUSY_TASK_MS = 15L * 60_000L
    private const val MAX_BUSY_TASK_MS = 8L * 3600_000L
    private const val MIN_CONFLICT_OVERLAP_MS = 5L * 60_000L
    private const val MIN_FREE_SLOT_MS = 30L * 60_000L
    private const val DUPLICATE_MIRROR_TOLERANCE_MS = 120_000L

    private val TIME_ONLY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val MONTH_DAY_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)

    data class TimeSlotBlock(
        val start: Long,
        val end: Long,
        val title: String,
        val source: String, // CALENDAR | TASK
        val taskId: Long? = null
    )

    data class TimeSlotRange(
        val start: Long,
        val end: Long,
        val durationMinutes: Long
    )

    data class ScheduleConflict(
        val firstTitle: String,
        val secondTitle: String,
        val overlapStart: Long,
        val overlapEnd: Long,
        val overlapMinutes: Long
    )

    data class TimeSlotAnalysis(
        val busyBlocks: List<TimeSlotBlock>,
        val freeSlotsToday: List<TimeSlotRange>,
        val freeSlotsTomorrow: List<TimeSlotRange>,
        val conflicts: List<ScheduleConflict>,
        val longWindowTaskCount: Int
    ) {
        fun toJson(zoneId: ZoneId = ZoneId.systemDefault()): JsonObject = JsonObject().apply {
            add("free_slots_today", freeSlotsToday.toJsonArray(zoneId))
            add("free_slots_tomorrow", freeSlotsTomorrow.toJsonArray(zoneId))
            add("busy_blocks", JsonArray().apply {
                busyBlocks.forEach { block ->
                    add(JsonObject().apply {
                        addProperty("source", block.source)
                        addProperty("title", block.title)
                        if (block.taskId != null) addProperty("task_id", block.taskId)
                        addProperty("start", formatMonthDayTime(block.start, zoneId))
                        addProperty("end", formatMonthDayTime(block.end, zoneId))
                    })
                }
            })
            add("conflicts", JsonArray().apply {
                conflicts.forEach { c ->
                    add(JsonObject().apply {
                        addProperty("first", c.firstTitle)
                        addProperty("second", c.secondTitle)
                        addProperty("overlap_window", "${formatMonthDayTime(c.overlapStart, zoneId)}-${formatTimeOnly(c.overlapEnd, zoneId)}")
                        addProperty("overlap_minutes", c.overlapMinutes)
                    })
                }
            })
            addProperty("flexible_long_ddl_task_count", longWindowTaskCount)
        }

        fun toPromptSummary(zoneId: ZoneId = ZoneId.systemDefault()): String {
            val todaySlots = if (freeSlotsToday.isNotEmpty()) {
                freeSlotsToday.joinToString("、") { "${formatTimeOnly(it.start, zoneId)}-${formatTimeOnly(it.end, zoneId)}(${it.durationMinutes}分)" }
            } else {
                "今日白天已无 >=30分钟 连续空闲"
            }
            val tomorrowSlots = if (freeSlotsTomorrow.isNotEmpty()) {
                tomorrowSummary(freeSlotsTomorrow, zoneId)
            } else {
                "明日白天已满"
            }
            val conflictPart = if (conflicts.isNotEmpty()) {
                val cText = conflicts.take(3).joinToString("；") {
                    "「${it.firstTitle}」与「${it.secondTitle}」在 ${formatMonthDayTime(it.overlapStart, zoneId)} 重叠${it.overlapMinutes}分钟"
                }
                "\n日程时间冲突预警: $cText"
            } else ""
            val flexPart = if (longWindowTaskCount > 0) {
                "\n备注: 另有${longWindowTaskCount}项长跨度(>8小时)截止窗口任务不占用固定时间块，可穿插安排进空闲段。"
            } else ""
            return "今日剩余可用时段(08:00-22:30): $todaySlots\n明日可用时段: $tomorrowSlots$conflictPart$flexPart"
        }

        private fun tomorrowSummary(slots: List<TimeSlotRange>, zoneId: ZoneId): String =
            slots.joinToString("、") { "${formatTimeOnly(it.start, zoneId)}-${formatTimeOnly(it.end, zoneId)}(${it.durationMinutes}分)" }

        private fun List<TimeSlotRange>.toJsonArray(zoneId: ZoneId): JsonArray {
            val array = JsonArray()
            for (slot in this) {
                array.add(JsonObject().apply {
                    addProperty("start", ScheduleAdvicePromptSupport.formatDateTime(slot.start, zoneId))
                    addProperty("end", ScheduleAdvicePromptSupport.formatDateTime(slot.end, zoneId))
                    addProperty("duration_minutes", slot.durationMinutes)
                })
            }
            return array
        }
    }

    data class EnergyBucketStats(
        val id: String,
        val label: String,
        val sessionCount: Int,
        val focusMinutes: Long,
        val completedTaskCount: Int
    ) {
        val score: Long get() = focusMinutes + completedTaskCount * 20L
    }

    data class FocusEnergyAnalysis(
        val buckets: List<EnergyBucketStats>,
        val goldenWindowLabel: String?,
        val totalSessions: Int,
        val totalFocusMinutes: Long,
        val avgSessionMinutes: Double,
        val totalCompletedWithTimestamp: Int
    ) {
        fun toJson(): JsonObject = JsonObject().apply {
            if (goldenWindowLabel != null) addProperty("golden_window", goldenWindowLabel)
            addProperty("total_sessions_30d", totalSessions)
            addProperty("total_focus_minutes_30d", totalFocusMinutes)
            addProperty("avg_session_minutes", String.format(Locale.US, "%.1f", avgSessionMinutes).toDouble())
            addProperty("completed_tasks_with_time_30d", totalCompletedWithTimestamp)
            add("time_buckets", JsonArray().apply {
                buckets.forEach { b ->
                    add(JsonObject().apply {
                        addProperty("bucket", b.label)
                        addProperty("focus_sessions", b.sessionCount)
                        addProperty("focus_minutes", b.focusMinutes)
                        addProperty("completed_tasks", b.completedTaskCount)
                    })
                }
            })
        }

        fun toPromptSummary(): String {
            if (goldenWindowLabel == null) {
                return "近30天暂无足够的专注与按时段完成样本，可按通用高难任务放上午的原则试排。"
            }
            val detail = buckets.filter { it.score > 0L }
                .joinToString("，") { "${it.label}(专注${it.focusMinutes}分/完成${it.completedTaskCount}件)" }
            val avgPart = if (totalSessions > 0) {
                "单次专注均值${String.format(Locale.US, "%.1f", avgSessionMinutes)}分钟；"
            } else ""
            return "黄金高效时段: $goldenWindowLabel（${avgPart}时段分布: $detail）。"
        }
    }

    fun filterBusyCalendarEvents(
        events: List<ExternalCalendarEvent>,
        rangeStartMillis: Long,
        rangeEndMillis: Long
    ): List<ExternalCalendarEvent> {
        return events
            .asSequence()
            .filter { e ->
                val duration = e.deadline - e.startTime
                duration in 1L until ALL_DAY_THRESHOLD_MS &&
                    e.deadline > rangeStartMillis &&
                    e.startTime < rangeEndMillis
            }
            .sortedWith(compareBy<ExternalCalendarEvent> { it.startTime }.thenBy { it.deadline })
            .distinctBy { Triple(it.startTime, it.deadline, it.title.trim()) }
            .toList()
    }

    fun formatCalendarEventsForPrompt(
        events: List<ExternalCalendarEvent>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (events.isEmpty()) return ""
        return events.take(15).joinToString("\n") { e ->
            val locPart = e.location?.trim()?.takeIf { it.isNotEmpty() }?.let { " @ $it" } ?: ""
            "- ${formatMonthDayTime(e.startTime, zoneId)}~${formatTimeOnly(e.deadline, zoneId)} ${e.title}$locPart"
        }
    }

    fun calculateTimeSlots(
        nowMillis: Long,
        composites: List<TaskDetailComposite>,
        externalEvents: List<ExternalCalendarEvent>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): TimeSlotAnalysis {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val tomorrow = today.plusDays(1)

        val todayActiveStart = max(nowMillis, today.atTime(8, 0).atZone(zoneId).toInstant().toEpochMilli())
        val todayActiveEnd = today.atTime(22, 30).atZone(zoneId).toInstant().toEpochMilli()
        val tomorrowActiveStart = tomorrow.atTime(8, 0).atZone(zoneId).toInstant().toEpochMilli()
        val tomorrowActiveEnd = tomorrow.atTime(22, 30).atZone(zoneId).toInstant().toEpochMilli()

        val horizonStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val horizonEnd = tomorrow.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val busyBlocks = mutableListOf<TimeSlotBlock>()
        filterBusyCalendarEvents(externalEvents, horizonStart, horizonEnd).forEach { ev ->
            busyBlocks += TimeSlotBlock(
                start = ev.startTime,
                end = ev.deadline,
                title = ev.title.ifBlank { "系统日历日程" },
                source = "CALENDAR"
            )
        }

        var longWindowTaskCount = 0
        composites.forEach { composite ->
            val t = composite.task
            if (t.isDone || t.startTime <= 0L || t.deadline <= t.startTime) return@forEach
            val span = t.deadline - t.startTime
            if (span in MIN_BUSY_TASK_MS..MAX_BUSY_TASK_MS) {
                if (t.deadline > horizonStart && t.startTime < horizonEnd) {
                    busyBlocks += TimeSlotBlock(
                        start = t.startTime,
                        end = t.deadline,
                        title = t.title,
                        source = "TASK",
                        taskId = t.id
                    )
                }
            } else if (span > MAX_BUSY_TASK_MS && t.deadline > nowMillis) {
                longWindowTaskCount++
            }
        }

        val sortedBlocks = busyBlocks.sortedWith(compareBy<TimeSlotBlock> { it.start }.thenBy { it.end })
        val conflicts = detectConflicts(sortedBlocks)

        val freeToday = if (todayActiveStart < todayActiveEnd) {
            extractFreeSlots(todayActiveStart, todayActiveEnd, sortedBlocks)
        } else emptyList()

        val freeTomorrow = extractFreeSlots(tomorrowActiveStart, tomorrowActiveEnd, sortedBlocks)

        return TimeSlotAnalysis(
            busyBlocks = sortedBlocks,
            freeSlotsToday = freeToday,
            freeSlotsTomorrow = freeTomorrow,
            conflicts = conflicts,
            longWindowTaskCount = longWindowTaskCount
        )
    }

    private fun detectConflicts(sortedBlocks: List<TimeSlotBlock>): List<ScheduleConflict> {
        val result = mutableListOf<ScheduleConflict>()
        for (i in sortedBlocks.indices) {
            val a = sortedBlocks[i]
            for (j in (i + 1) until sortedBlocks.size) {
                val b = sortedBlocks[j]
                if (b.start >= a.end) break
                val overlapStart = max(a.start, b.start)
                val overlapEnd = min(a.end, b.end)
                val overlapMs = overlapEnd - overlapStart
                if (overlapMs < MIN_CONFLICT_OVERLAP_MS) continue

                val isLikelyMirroredCopy = a.source != b.source &&
                    a.title.trim().equals(b.title.trim(), ignoreCase = true) &&
                    abs(a.start - b.start) <= DUPLICATE_MIRROR_TOLERANCE_MS &&
                    abs(a.end - b.end) <= DUPLICATE_MIRROR_TOLERANCE_MS
                if (isLikelyMirroredCopy) continue

                result += ScheduleConflict(
                    firstTitle = a.title,
                    secondTitle = b.title,
                    overlapStart = overlapStart,
                    overlapEnd = overlapEnd,
                    overlapMinutes = overlapMs / 60_000L
                )
            }
        }
        return result
    }

    private fun extractFreeSlots(
        windowStart: Long,
        windowEnd: Long,
        sortedBusyBlocks: List<TimeSlotBlock>
    ): List<TimeSlotRange> {
        if (windowStart >= windowEnd) return emptyList()
        val clamped = sortedBusyBlocks.mapNotNull { b ->
            val s = max(windowStart, b.start)
            val e = min(windowEnd, b.end)
            if (e > s) s to e else null
        }.sortedBy { it.first }

        val free = mutableListOf<TimeSlotRange>()
        var cursor = windowStart
        for ((busyStart, busyEnd) in clamped) {
            if (busyStart - cursor >= MIN_FREE_SLOT_MS) {
                free += TimeSlotRange(
                    start = cursor,
                    end = busyStart,
                    durationMinutes = (busyStart - cursor) / 60_000L
                )
            }
            if (busyEnd > cursor) {
                cursor = busyEnd
            }
        }
        if (windowEnd - cursor >= MIN_FREE_SLOT_MS) {
            free += TimeSlotRange(
                start = cursor,
                end = windowEnd,
                durationMinutes = (windowEnd - cursor) / 60_000L
            )
        }
        return free
    }

    fun analyzeFocusEnergyPattern(
        sessions: List<FocusSessionEntity>,
        completedTasks: List<TaskDetailComposite>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): FocusEnergyAnalysis {
        val bucketDefs = listOf(
            "MORNING" to "上午 06:00-12:00",
            "AFTERNOON" to "下午 12:00-18:00",
            "EVENING" to "晚间 18:00-23:00",
            "LATE_NIGHT" to "深夜 23:00-06:00"
        )
        val counts = mutableMapOf<String, Int>()
        val focusSec = mutableMapOf<String, Long>()
        val doneCounts = mutableMapOf<String, Int>()

        var validSessionCount = 0
        var totalFocusSeconds = 0L
        sessions.forEach { s ->
            if (s.actualSeconds <= 0L || s.startedAt <= 0L) return@forEach
            val key = hourToBucketId(Instant.ofEpochMilli(s.startedAt).atZone(zoneId).hour)
            counts[key] = (counts[key] ?: 0) + 1
            focusSec[key] = (focusSec[key] ?: 0L) + s.actualSeconds
            validSessionCount++
            totalFocusSeconds += s.actualSeconds
        }

        var completedWithTs = 0
        completedTasks.forEach { c ->
            val completedAt = c.task.completedAt ?: return@forEach
            if (completedAt <= 0L) return@forEach
            val key = hourToBucketId(Instant.ofEpochMilli(completedAt).atZone(zoneId).hour)
            doneCounts[key] = (doneCounts[key] ?: 0) + 1
            completedWithTs++
        }

        val stats = bucketDefs.map { (id, label) ->
            EnergyBucketStats(
                id = id,
                label = label,
                sessionCount = counts[id] ?: 0,
                focusMinutes = (focusSec[id] ?: 0L) / 60L,
                completedTaskCount = doneCounts[id] ?: 0
            )
        }

        val bestBucket = stats.maxByOrNull { it.score }?.takeIf { it.score > 0L }
        val totalFocusMin = totalFocusSeconds / 60L
        val avgMin = if (validSessionCount > 0) totalFocusMin.toDouble() / validSessionCount else 0.0

        return FocusEnergyAnalysis(
            buckets = stats,
            goldenWindowLabel = bestBucket?.label,
            totalSessions = validSessionCount,
            totalFocusMinutes = totalFocusMin,
            avgSessionMinutes = avgMin,
            totalCompletedWithTimestamp = completedWithTs
        )
    }

    private fun hourToBucketId(hour: Int): String = when (hour) {
        in 6..11 -> "MORNING"
        in 12..17 -> "AFTERNOON"
        in 18..22 -> "EVENING"
        else -> "LATE_NIGHT"
    }

    fun formatWeatherForecastText(report: AMapWeatherReport): String {
        val header = listOfNotNull(
            report.city.takeIf { it.isNotBlank() },
            report.reportTime.takeIf { it.isNotBlank() }?.let { "发布:$it" }
        ).joinToString(" ").ifBlank { "天气预报" }

        val days = report.forecasts.take(3).joinToString("；") { day ->
            val weatherDesc = if (day.dayWeather == day.nightWeather || day.nightWeather.isBlank()) {
                day.dayWeather
            } else {
                "${day.dayWeather}转${day.nightWeather}"
            }
            "${day.date}: $weatherDesc ${day.nightTemp}~${day.dayTemp}°C(${day.dayWind}风)"
        }
        return "$header -> $days"
    }

    fun buildPreviousAdviceSummaryJson(
        advices: List<DailyScheduleAdviceEntity>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (advices.isEmpty()) return "暂无历史日程建议记录。"
        val arr = JsonArray()
        advices.take(2).forEach { entity ->
            arr.add(JsonObject().apply {
                addProperty("created_at", ScheduleAdvicePromptSupport.formatDateTime(entity.createdAt, zoneId))
                add("short_term_titles", extractAdviceTopics(entity.shortTermJson, "task_title"))
                add("long_term_topics", extractAdviceTopics(entity.longTermJson, "topic"))
            })
        }
        return arr.toString()
    }

    private fun extractAdviceTopics(jsonArrayStr: String, key: String): JsonArray {
        val result = JsonArray()
        runCatching {
            val parsed = JsonParser.parseString(jsonArrayStr)
            if (parsed.isJsonArray) {
                parsed.asJsonArray.forEach { el ->
                    if (el.isJsonObject) {
                        val obj = el.asJsonObject
                        val title = obj.get(key)?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
                        val action = obj.get("action_type")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
                        if (title.isNotEmpty()) {
                            result.add(if (action.isNotEmpty()) "$title[$action]" else title)
                        }
                    }
                }
            }
        }
        return result
    }

    private fun formatTimeOnly(epochMillis: Long, zoneId: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(TIME_ONLY_FMT)

    private fun formatMonthDayTime(epochMillis: Long, zoneId: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(MONTH_DAY_TIME_FMT)
}
