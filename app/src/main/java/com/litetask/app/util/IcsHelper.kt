package com.litetask.app.util

import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.data.model.sortedByOrder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ParsedIcsEvent(
    val uid: String?,
    val title: String,
    val description: String?,
    val startTime: Long,
    val deadline: Long,
    val categoryName: String?,
    val isDone: Boolean,
    val reminderTriggerTimes: List<Long>
)

/**
 * RFC 5545 (.ics / iCalendar) 序列化与解析工具。
 */
object IcsHelper {

    private const val PRODID = "-//LiteTask//Task Manager//CN"
    private const val DEFAULT_DURATION_MS = 60L * 60 * 1000

    private sealed class TriggerSpec {
        data class Absolute(val timestampMillis: Long) : TriggerSpec()
        data class Relative(val offsetMillis: Long, val fromEnd: Boolean) : TriggerSpec()
    }

    fun exportTasksToIcs(
        tasksWithDetails: List<TaskDetailComposite>,
        includeFinished: Boolean = true,
        includeReminders: Boolean = true
    ): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:$PRODID\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")
        sb.append("METHOD:PUBLISH\r\n")
        sb.append("X-WR-CALNAME:LiteTask 任务导出\r\n")

        tasksWithDetails.forEach { detail ->
            val task = detail.task
            if (!includeFinished && (task.isDone || task.isExpired)) {
                return@forEach
            }
            val safeStart = task.startTime.takeIf { it > 0L } ?: System.currentTimeMillis()
            val safeEnd = maxOf(task.deadline, safeStart + 60_000L)

            sb.append("BEGIN:VEVENT\r\n")
            appendFoldedProperty(sb, "UID", "litetask-${task.id}@litetask.app")
            appendFoldedProperty(sb, "DTSTAMP", formatUtcDateTime(task.createdAt.takeIf { it > 0L } ?: safeStart))
            appendFoldedProperty(sb, "DTSTART", formatUtcDateTime(safeStart))
            appendFoldedProperty(sb, "DTEND", formatUtcDateTime(safeEnd))
            appendFoldedProperty(sb, "SUMMARY", escapeText(task.title))

            val fullDescription = buildString {
                if (!task.description.isNullOrBlank()) {
                    append(task.description.trim())
                }
                if (detail.subTasks.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append("子任务:\n")
                    detail.subTasks.sortedByOrder().forEach { sub ->
                        val mark = if (sub.isCompleted) "[x]" else "[ ]"
                        append("- $mark ${sub.content}\n")
                    }
                }
            }.trimEnd()
            if (fullDescription.isNotBlank()) {
                appendFoldedProperty(sb, "DESCRIPTION", escapeText(fullDescription))
            }

            detail.category?.name?.takeIf { it.isNotBlank() }?.let { catName ->
                appendFoldedProperty(sb, "CATEGORIES", escapeText(catName))
            }
            appendFoldedProperty(sb, "STATUS", if (task.isDone) "COMPLETED" else "CONFIRMED")

            if (includeReminders) {
                detail.reminders.filter { it.triggerAt > 0L }.forEach { reminder ->
                    sb.append("BEGIN:VALARM\r\n")
                    sb.append("ACTION:DISPLAY\r\n")
                    appendFoldedProperty(
                        sb,
                        "DESCRIPTION",
                        escapeText(reminder.label?.takeIf { it.isNotBlank() } ?: task.title)
                    )
                    val diffFromStartSec = (reminder.triggerAt - safeStart) / 1000L
                    val diffFromEndSec = (reminder.triggerAt - safeEnd) / 1000L
                    when {
                        diffFromStartSec <= 0L -> {
                            sb.append("TRIGGER:${formatIsoDurationSeconds(-diffFromStartSec, negative = true)}\r\n")
                        }
                        diffFromEndSec <= 0L -> {
                            sb.append("TRIGGER;RELATED=END:${formatIsoDurationSeconds(-diffFromEndSec, negative = true)}\r\n")
                        }
                        else -> {
                            sb.append("TRIGGER;VALUE=DATE-TIME:${formatUtcDateTime(reminder.triggerAt)}\r\n")
                        }
                    }
                    sb.append("END:VALARM\r\n")
                }
            }

            sb.append("END:VEVENT\r\n")
        }

        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    fun parseIcs(icsContent: String): List<ParsedIcsEvent> {
        val unfolded = icsContent
            .replace("\r\n ", "")
            .replace("\r\n\t", "")
            .replace("\n ", "")
            .replace("\n\t", "")

        val lines = unfolded.split("\r\n", "\n")
        val results = mutableListOf<ParsedIcsEvent>()

        var inEvent = false
        var inAlarm = false

        var uid: String? = null
        var summary: String? = null
        var description: String? = null
        var dtStartMillis: Long? = null
        var dtStartAllDay = false
        var dtEndMillis: Long? = null
        var dtEndAllDay = false
        var durationMillis: Long? = null
        var categoryName: String? = null
        var status: String? = null
        val triggerSpecs = mutableListOf<TriggerSpec>()

        fun resetEventState() {
            uid = null
            summary = null
            description = null
            dtStartMillis = null
            dtStartAllDay = false
            dtEndMillis = null
            dtEndAllDay = false
            durationMillis = null
            categoryName = null
            status = null
            triggerSpecs.clear()
        }

        for (rawLine in lines) {
            val line = rawLine.trimEnd()
            if (line.isBlank()) continue

            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> {
                    inEvent = true
                    inAlarm = false
                    resetEventState()
                }
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    if (inEvent) {
                        val title = summary?.trim().orEmpty()
                        if (title.isNotEmpty()) {
                            val now = System.currentTimeMillis()
                            val start = dtStartMillis ?: now
                            val end = resolveEndTime(
                                startMillis = start,
                                rawEndMillis = dtEndMillis,
                                isStartAllDay = dtStartAllDay,
                                isEndAllDay = dtEndAllDay,
                                durationMillis = durationMillis
                            )
                            val triggers = triggerSpecs.mapNotNull { spec ->
                                when (spec) {
                                    is TriggerSpec.Absolute -> spec.timestampMillis
                                    is TriggerSpec.Relative -> (if (spec.fromEnd) end else start) + spec.offsetMillis
                                }.takeIf { it > 0L }
                            }.distinct()

                            results.add(
                                ParsedIcsEvent(
                                    uid = uid,
                                    title = title,
                                    description = description?.takeIf { it.isNotBlank() },
                                    startTime = start,
                                    deadline = end,
                                    categoryName = categoryName?.takeIf { it.isNotBlank() },
                                    isDone = status.equals("COMPLETED", ignoreCase = true),
                                    reminderTriggerTimes = triggers
                                )
                            )
                        }
                    }
                    inEvent = false
                    inAlarm = false
                }
                inEvent && line.equals("BEGIN:VALARM", ignoreCase = true) -> {
                    inAlarm = true
                }
                inEvent && line.equals("END:VALARM", ignoreCase = true) -> {
                    inAlarm = false
                }
                inEvent && inAlarm -> {
                    val (propName, params, value) = splitProperty(line) ?: continue
                    if (propName == "TRIGGER") {
                        parseTrigger(params, value)?.let { triggerSpecs.add(it) }
                    }
                }
                inEvent -> {
                    val (propName, params, value) = splitProperty(line) ?: continue
                    when (propName) {
                        "UID" -> uid = value.trim()
                        "SUMMARY" -> summary = unescapeText(value)
                        "DESCRIPTION" -> description = unescapeText(value)
                        "CATEGORIES" -> {
                            categoryName = unescapeText(value)
                                .split(",")
                                .firstOrNull { it.isNotBlank() }
                                ?.trim()
                        }
                        "STATUS" -> status = value.trim()
                        "DTSTART" -> {
                            val parsed = parseIcsDateTime(params, value)
                            if (parsed != null) {
                                dtStartMillis = parsed.first
                                dtStartAllDay = parsed.second
                            }
                        }
                        "DTEND" -> {
                            val parsed = parseIcsDateTime(params, value)
                            if (parsed != null) {
                                dtEndMillis = parsed.first
                                dtEndAllDay = parsed.second
                            }
                        }
                        "DURATION" -> durationMillis = parseIsoDurationMillis(value.trim())
                    }
                }
            }
        }

        return results
    }

    private fun resolveEndTime(
        startMillis: Long,
        rawEndMillis: Long?,
        isStartAllDay: Boolean,
        isEndAllDay: Boolean,
        durationMillis: Long?
    ): Long {
        val candidate = when {
            rawEndMillis != null -> {
                if (isEndAllDay && rawEndMillis > startMillis) rawEndMillis - 1000L
                else rawEndMillis
            }
            durationMillis != null && durationMillis > 0L -> startMillis + durationMillis
            isStartAllDay -> startMillis + (24L * 60 * 60 * 1000) - 1000L
            else -> startMillis + DEFAULT_DURATION_MS
        }
        return if (candidate > startMillis) candidate else startMillis + DEFAULT_DURATION_MS
    }

    private fun splitProperty(line: String): Triple<String, Map<String, String>, String>? {
        var inQuotes = false
        var colonIndex = -1
        for (i in line.indices) {
            val ch = line[i]
            if (ch == '"') inQuotes = !inQuotes
            else if (ch == ':' && !inQuotes) {
                colonIndex = i
                break
            }
        }
        if (colonIndex <= 0) return null

        val header = line.substring(0, colonIndex)
        val value = line.substring(colonIndex + 1)
        val segments = header.split(';')
        val propName = segments.first().uppercase(Locale.ROOT).trim()
        val params = mutableMapOf<String, String>()
        segments.drop(1).forEach { seg ->
            val eqIdx = seg.indexOf('=')
            if (eqIdx > 0) {
                val key = seg.substring(0, eqIdx).uppercase(Locale.ROOT).trim()
                val pVal = seg.substring(eqIdx + 1).trim().trim('"')
                params[key] = pVal
            }
        }
        return Triple(propName, params, value)
    }

    private fun parseIcsDateTime(params: Map<String, String>, rawValue: String): Pair<Long, Boolean>? {
        val value = rawValue.trim()
        val isDateOnly = params["VALUE"].equals("DATE", ignoreCase = true) ||
            Regex("^\\d{8}$").matches(value)
        if (isDateOnly) {
            val clean = value.take(8)
            val sdf = SimpleDateFormat("yyyyMMdd", Locale.ROOT).apply {
                timeZone = resolveTimeZone(params["TZID"])
            }
            val date = runCatching { sdf.parse(clean) }.getOrNull() ?: return null
            return date.time to true
        }

        val isUtc = value.endsWith("Z", ignoreCase = true)
        val clean = value.removeSuffix("Z").removeSuffix("z")
        val tz = if (isUtc) TimeZone.getTimeZone("UTC") else resolveTimeZone(params["TZID"])
        val pattern = when {
            clean.length >= 15 && clean.contains('T') -> "yyyyMMdd'T'HHmmss"
            clean.length >= 13 && clean.contains('T') -> "yyyyMMdd'T'HHmm"
            else -> return null
        }
        val sdf = SimpleDateFormat(pattern, Locale.ROOT).apply {
            timeZone = tz
        }
        val parsed = runCatching { sdf.parse(clean.take(pattern.replace("'", "").length)) }.getOrNull()
            ?: return null
        return parsed.time to false
    }

    private fun resolveTimeZone(tzId: String?): TimeZone {
        if (tzId.isNullOrBlank()) return TimeZone.getDefault()
        val normalized = when (tzId) {
            "China Standard Time" -> "Asia/Shanghai"
            "Tokyo Standard Time" -> "Asia/Tokyo"
            "UTC", "Etc/UTC" -> "UTC"
            else -> tzId
        }
        val tz = TimeZone.getTimeZone(normalized)
        return if (tz.id == "GMT" && !normalized.equals("GMT", ignoreCase = true) && !normalized.equals("UTC", ignoreCase = true)) {
            TimeZone.getDefault()
        } else {
            tz
        }
    }

    private fun parseTrigger(params: Map<String, String>, value: String): TriggerSpec? {
        if (params["VALUE"].equals("DATE-TIME", ignoreCase = true) || value.trim().matches(Regex("^\\d{8}T\\d{6}Z?$"))) {
            val parsed = parseIcsDateTime(params, value) ?: return null
            return TriggerSpec.Absolute(parsed.first)
        }
        val offsetMillis = parseIsoDurationMillis(value.trim()) ?: return null
        val fromEnd = params["RELATED"].equals("END", ignoreCase = true)
        return TriggerSpec.Relative(offsetMillis = offsetMillis, fromEnd = fromEnd)
    }

    private fun parseIsoDurationMillis(raw: String): Long? {
        val trimmed = raw.trim().uppercase(Locale.ROOT)
        if (!trimmed.contains('P')) return null
        val negative = trimmed.startsWith("-")
        val body = trimmed.removePrefix("-").removePrefix("+")
        val regex = Regex("^P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$")
        val match = regex.matchEntire(body) ?: return null
        val weeks = match.groupValues[1].toLongOrNull() ?: 0L
        val days = match.groupValues[2].toLongOrNull() ?: 0L
        val hours = match.groupValues[3].toLongOrNull() ?: 0L
        val minutes = match.groupValues[4].toLongOrNull() ?: 0L
        val seconds = match.groupValues[5].toLongOrNull() ?: 0L
        val totalSeconds = weeks * 7 * 86400 + days * 86400 + hours * 3600 + minutes * 60 + seconds
        val millis = totalSeconds * 1000L
        return if (negative) -millis else millis
    }

    private fun formatIsoDurationSeconds(totalSeconds: Long, negative: Boolean): String {
        val safeSec = totalSeconds.coerceAtLeast(0L)
        val days = safeSec / 86400L
        val remAfterDays = safeSec % 86400L
        val hours = remAfterDays / 3600L
        val remAfterHours = remAfterDays % 3600L
        val minutes = remAfterHours / 60L
        val seconds = remAfterHours % 60L

        val sb = StringBuilder()
        if (negative) sb.append('-')
        sb.append('P')
        if (days > 0L) sb.append("${days}D")
        if (hours > 0L || minutes > 0L || seconds > 0L || days == 0L) {
            sb.append('T')
            if (hours > 0L) sb.append("${hours}H")
            if (minutes > 0L) sb.append("${minutes}M")
            if (seconds > 0L || (days == 0L && hours == 0L && minutes == 0L)) sb.append("${seconds}S")
        }
        return sb.toString()
    }

    private fun formatUtcDateTime(millis: Long): String {
        val sdf = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(millis))
    }

    private fun appendFoldedProperty(sb: StringBuilder, key: String, value: String) {
        val fullLine = "$key:$value"
        if (fullLine.length <= 72) {
            sb.append(fullLine).append("\r\n")
            return
        }
        var index = 0
        var firstChunk = true
        while (index < fullLine.length) {
            val chunkSize = if (firstChunk) 72 else 71
            val end = minOf(index + chunkSize, fullLine.length)
            if (!firstChunk) sb.append(' ')
            sb.append(fullLine, index, end).append("\r\n")
            index = end
            firstChunk = false
        }
    }

    private fun escapeText(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace(";", "\\;")
            .replace(",", "\\,")
    }

    private fun unescapeText(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == '\\' && i + 1 < text.length) {
                when (val next = text[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    '\\', ';', ',' -> sb.append(next)
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }
}
