package com.litetask.app.data.model

import com.litetask.app.data.ai.AiTaskComponentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class RecurrenceEngineTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun epoch(year: Int, month: Int, day: Int, hour: Int = 9, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun zdt(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(zone)

    @Test
    fun computeNextOccurrence_dailyAndIntervalDays_advancesAndPreservesDuration() {
        val start = epoch(2026, 3, 1, 9, 0)
        val deadline = epoch(2026, 3, 1, 10, 30)

        val dailyNext = RecurrenceEngine.computeNextOccurrence(
            taskStartTimeMs = start,
            taskDeadlineMs = deadline,
            rule = RecurrenceRuleData(freq = "DAILY", interval = 1),
            nowMs = start + 1_000L,
            zoneId = zone
        )
        assertNotNull(dailyNext)
        assertEquals(epoch(2026, 3, 2, 9, 0), dailyNext!!.startTimeMillis)
        assertEquals(epoch(2026, 3, 2, 10, 30), dailyNext.deadlineMillis)

        val intervalNext = RecurrenceEngine.computeNextOccurrence(
            taskStartTimeMs = start,
            taskDeadlineMs = deadline,
            rule = RecurrenceRuleData(freq = "INTERVAL_DAYS", interval = 3),
            nowMs = start + 1_000L,
            zoneId = zone
        )
        assertNotNull(intervalNext)
        assertEquals(epoch(2026, 3, 4, 9, 0), intervalNext!!.startTimeMillis)
    }

    @Test
    fun computeNextOccurrence_weeklyMultiDayWithInterval_rollsOverCorrectly() {
        // 2026-03-02 is Monday; rule is Mon(1) & Thu(4) every 2 weeks
        val monStart = epoch(2026, 3, 2, 14, 0)
        val monDeadline = epoch(2026, 3, 2, 15, 0)
        val rule = RecurrenceRuleData(
            freq = "WEEKLY",
            interval = 2,
            daysOfWeek = listOf(1, 4)
        )

        // Next inside the same week should be Thursday 2026-03-05
        val thuWindow = RecurrenceEngine.computeNextOccurrence(
            taskStartTimeMs = monStart,
            taskDeadlineMs = monDeadline,
            rule = rule,
            nowMs = monStart + 1_000L,
            zoneId = zone
        )
        assertNotNull(thuWindow)
        assertEquals(epoch(2026, 3, 5, 14, 0), thuWindow!!.startTimeMillis)

        // From Thursday 2026-03-05 with interval=2, should jump 2 weeks to Monday 2026-03-16
        val nextCycleMon = RecurrenceEngine.computeNextOccurrence(
            taskStartTimeMs = thuWindow.startTimeMillis,
            taskDeadlineMs = thuWindow.deadlineMillis,
            rule = rule,
            nowMs = thuWindow.startTimeMillis + 1_000L,
            zoneId = zone
        )
        assertNotNull(nextCycleMon)
        assertEquals(epoch(2026, 3, 16, 14, 0), nextCycleMon!!.startTimeMillis)
    }

    @Test
    fun computeNextOccurrence_monthlyAnchor31_clampsToMonthEndAndRestores() {
        val jan31Start = epoch(2026, 1, 31, 8, 0)
        val jan31Deadline = epoch(2026, 1, 31, 9, 0)
        val rule = RecurrenceRuleData(freq = "MONTHLY", interval = 1, dayOfMonth = 31)

        val febWindow = RecurrenceEngine.computeNextOccurrence(
            taskStartTimeMs = jan31Start,
            taskDeadlineMs = jan31Deadline,
            rule = rule,
            nowMs = jan31Start + 1_000L,
            zoneId = zone
        )
        assertNotNull(febWindow)
        val febZdt = zdt(febWindow!!.startTimeMillis)
        assertEquals(2026, febZdt.year)
        assertEquals(2, febZdt.monthValue)
        assertEquals(28, febZdt.dayOfMonth)

        // Moving from Feb 28 with rule still anchored to dayOfMonth=31 restores Mar 31
        val marWindow = RecurrenceEngine.computeNextOccurrence(
            taskStartTimeMs = febWindow.startTimeMillis,
            taskDeadlineMs = febWindow.deadlineMillis,
            rule = rule,
            nowMs = febWindow.startTimeMillis + 1_000L,
            zoneId = zone
        )
        assertNotNull(marWindow)
        val marZdt = zdt(marWindow!!.startTimeMillis)
        assertEquals(3, marZdt.monthValue)
        assertEquals(31, marZdt.dayOfMonth)
    }

    @Test
    fun computeNextOccurrence_yearlyLeapDay_clampsAndRestoresNextLeapYear() {
        val start2024 = epoch(2024, 2, 29, 10, 0)
        val deadline2024 = epoch(2024, 2, 29, 11, 0)
        val rule = RecurrenceRuleData(freq = "YEARLY", interval = 1, dayOfMonth = 29)

        val previews = RecurrenceEngine.previewNextOccurrences(
            taskStartTimeMs = start2024,
            taskDeadlineMs = deadline2024,
            rule = rule,
            count = 4,
            nowMs = start2024 + 1_000L,
            zoneId = zone
        )
        assertEquals(4, previews.size)
        assertEquals(28, zdt(previews[0].startTimeMillis).dayOfMonth) // 2025-02-28
        assertEquals(28, zdt(previews[1].startTimeMillis).dayOfMonth) // 2026-02-28
        assertEquals(28, zdt(previews[2].startTimeMillis).dayOfMonth) // 2027-02-28
        assertEquals(2028, zdt(previews[3].startTimeMillis).year)
        assertEquals(29, zdt(previews[3].startTimeMillis).dayOfMonth) // 2028-02-29 restored!
    }

    @Test
    fun computeNextOccurrence_overdueMultiPeriod_fastForwardsToNextActiveWindow() {
        val oldStart = epoch(2026, 1, 1, 9, 0)
        val oldDeadline = epoch(2026, 1, 1, 10, 0)
        // Overdue by 5+ days -> completed at Jan 6 12:00 -> next un-expired daily window is Jan 7 09:00
        val completedNow = epoch(2026, 1, 6, 12, 0)

        val next = RecurrenceEngine.computeNextOccurrence(
            taskStartTimeMs = oldStart,
            taskDeadlineMs = oldDeadline,
            rule = RecurrenceRuleData(freq = "DAILY", interval = 1),
            nowMs = completedNow,
            zoneId = zone
        )
        assertNotNull(next)
        assertEquals(epoch(2026, 1, 7, 9, 0), next!!.startTimeMillis)
    }

    @Test
    fun endConditions_afterCountUntilDateAndPaused_stopWhenExhausted() {
        val start = epoch(2026, 5, 1, 9, 0)
        val deadline = epoch(2026, 5, 1, 10, 0)

        // AFTER_COUNT = 2 -> generates once, advances to remainingCount = 1, then stops
        val rule2 = RecurrenceRuleData(freq = "DAILY", endType = "AFTER_COUNT", remainingCount = 2)
        assertNotNull(RecurrenceEngine.computeNextOccurrence(start, deadline, rule2, nowMs = start + 1_000L, zoneId = zone))
        val advanced = RecurrenceEngine.advanceRuleForNextOccurrence(rule2)
        assertEquals(1, advanced.remainingCount)
        assertEquals(2, advanced.occurrenceIndex)
        assertNull(advanced.generatedNextTaskId)
        assertNull(RecurrenceEngine.computeNextOccurrence(start, deadline, advanced, nowMs = start + 1_000L, zoneId = zone))

        // UNTIL_DATE blocks occurrence after cutoff
        val untilCutoff = epoch(2026, 5, 1, 23, 59)
        val untilRule = RecurrenceRuleData(freq = "DAILY", endType = "UNTIL_DATE", untilEpochMs = untilCutoff)
        assertNull(RecurrenceEngine.computeNextOccurrence(start, deadline, untilRule, nowMs = start + 1_000L, zoneId = zone))

        // Paused stops occurrence
        val pausedRule = RecurrenceRuleData(freq = "DAILY", isPaused = true)
        assertNull(RecurrenceEngine.computeNextOccurrence(start, deadline, pausedRule, nowMs = start + 1_000L, zoneId = zone))
    }

    @Test
    fun buildTranslatedReminders_andInheritedComponents_applyPolicyAndStripPasses() {
        val oldStart = epoch(2026, 6, 1, 10, 0)
        val oldDeadline = epoch(2026, 6, 1, 14, 0)
        val newStart = epoch(2026, 6, 8, 10, 0)
        val newDeadline = epoch(2026, 6, 8, 14, 0)

        val translated = RecurrenceEngine.buildTranslatedReminders(
            sourceReminders = listOf(
                Reminder(id = 1L, taskId = 10L, triggerAt = oldStart - 15 * 60_000L, label = "开始前15分钟", isFired = true),
                Reminder(id = 2L, taskId = 10L, triggerAt = oldDeadline - 30 * 60_000L, label = "截止前30分钟", isFired = true)
            ),
            oldStartMs = oldStart,
            oldDeadlineMs = oldDeadline,
            newStartMs = newStart,
            newDeadlineMs = newDeadline
        )
        assertEquals(2, translated.size)
        assertEquals(newStart - 15 * 60_000L, translated[0].triggerAt)
        assertEquals(newDeadline - 30 * 60_000L, translated[1].triggerAt)
        assertFalse(translated[0].isFired)
        assertFalse(translated[1].isFired)

        val oldRule = RecurrenceRuleData(freq = "WEEKLY", daysOfWeek = listOf(1), generatedNextTaskId = 99L)
        val updatedRule = RecurrenceEngine.advanceRuleForNextOccurrence(oldRule)
        val sourceComponents = listOf(
            TaskComponent.RecurrenceComponent(id = 5L, taskId = 10L, data = oldRule),
            TaskComponent.AMapComponent(
                id = 6L,
                taskId = 10L,
                data = AMapRouteData(endName = "公司", endAddress = "科技园", endLat = 31.2, endLng = 121.5)
            ),
            TaskComponent.LinkComponent(id = 7L, taskId = 10L, data = LinkBookmarkData(url = "https://example.com")),
            TaskComponent.FileComponent(
                id = 8L,
                taskId = 10L,
                data = FileAttachmentData("checklist.pdf", "content://x", "application/pdf", 1024L, managedFilePath = "attachments/a.pdf", isManagedCopy = true)
            ),
            TaskComponent.AccessPassComponent(
                id = 9L,
                taskId = 10L,
                data = AccessPassData(passType = "PICKUP", title = "菜鸟驿站", primaryCode = "3-1-999")
            ),
            TaskComponent.UnknownComponent(id = 10L, taskId = 10L, rawType = "LEGACY", rawJson = "{}")
        )

        val inherited = RecurrenceEngine.buildInheritedComponents(sourceComponents, updatedRule)
        assertEquals(4, inherited.size)
        assertTrue(inherited.none { it is TaskComponent.AccessPassComponent })
        assertTrue(inherited.none { it is TaskComponent.UnknownComponent })
        val inheritedRec = inherited.filterIsInstance<TaskComponent.RecurrenceComponent>().single()
        assertEquals(0L, inheritedRec.id)
        assertEquals(2, inheritedRec.data.occurrenceIndex)
        assertNull(inheritedRec.data.generatedNextTaskId)
        assertTrue(inherited.any { it is TaskComponent.AMapComponent && it.data.endName == "公司" })
        assertTrue(inherited.any { it is TaskComponent.LinkComponent && it.data.url == "https://example.com" })
        assertTrue(inherited.any { it is TaskComponent.FileComponent && it.data.managedFilePath == "attachments/a.pdf" })
    }

    @Test
    fun extractFallbackAndAiParser_andEntityRoundTrip_work() {
        val base = epoch(2026, 4, 10, 9, 0)

        val workdays = RecurrenceEngine.extractFallbackFromText("每个工作日早上9点站会", base)
        assertNotNull(workdays)
        assertEquals("WEEKLY", workdays!!.freq)
        assertEquals(listOf(1, 2, 3, 4, 5), workdays.daysOfWeek)

        val biWeekly = RecurrenceEngine.extractFallbackFromText("每2周三、五晚8点健身", base)
        assertNotNull(biWeekly)
        assertEquals(2, biWeekly!!.interval)
        assertEquals(listOf(3, 5), biWeekly.daysOfWeek)

        val monthly = RecurrenceEngine.extractFallbackFromText("每月15日还房贷", base)
        assertNotNull(monthly)
        assertEquals("MONTHLY", monthly!!.freq)
        assertEquals(15, monthly.dayOfMonth)

        val every3Days = RecurrenceEngine.extractFallbackFromText("每 3 天给植物浇水", base)
        assertNotNull(every3Days)
        assertEquals("INTERVAL_DAYS", every3Days!!.freq)
        assertEquals(3, every3Days.interval)

        val task = Task(title = "周会", type = TaskType.WORK, startTime = base, deadline = base + 3600_000L)
        AiTaskComponentParser.applyParsedExtras(
            task,
            """
            {
              "recurrence": {
                "freq": "WEEKLY",
                "interval": 2,
                "days_of_week": [1, 4],
                "end_type": "UNTIL_DATE",
                "until_date": "2026-12-31",
                "inherit_subtasks": false
              }
            }
            """.trimIndent()
        )
        val parsed = task.parsedRecurrence
        assertNotNull(parsed)
        assertEquals("WEEKLY", parsed!!.freq)
        assertEquals(2, parsed.interval)
        assertEquals(listOf(1, 4), parsed.daysOfWeek)
        assertEquals("UNTIL_DATE", parsed.endType)
        assertNotNull(parsed.untilEpochMs)
        assertFalse(parsed.inheritSubTasks)

        // Entity <-> Domain Component round trip
        val entity = TaskComponent.RecurrenceComponent(id = 12L, taskId = 88L, data = parsed).toEntity(88L)
        val domain = entity.toTaskComponent() as TaskComponent.RecurrenceComponent
        assertEquals(parsed.freq, domain.data.freq)
        assertEquals(parsed.daysOfWeek, domain.data.daysOfWeek)
        assertEquals(parsed.untilEpochMs, domain.data.untilEpochMs)
        assertFalse(domain.data.inheritSubTasks)
    }
}
