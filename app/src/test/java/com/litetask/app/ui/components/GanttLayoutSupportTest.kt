package com.litetask.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class GanttLayoutSupportTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun shortTasksScaleProportionally_andNeverExceedDayMidnightBoundary() {
        val startOfView = epoch(2026, 11, 16, 0, 0)
        val endOfViewThreeDay = startOfView + 3L * GanttLayoutSupport.MS_PER_DAY
        val density = 3f
        val dayWidth3DayPx = 220f * density // 660 px/day -> 27.5 px/hour
        val hairlinePx = 3.5f * density
        val fullCardMinPx = 104f * density

        // 1h task at 20:00 - 21:00 on 11/16 ("理发")
        val haircut = GanttLayoutSupport.computeBarGeometry(
            taskStartMs = epoch(2026, 11, 16, 20, 0),
            taskDeadlineMs = epoch(2026, 11, 16, 21, 0),
            startOfViewMs = startOfView,
            endOfViewMs = endOfViewThreeDay,
            dayWidthPx = dayWidth3DayPx,
            minHairlineWidthPx = hairlinePx,
            fullCardMinWidthPx = fullCardMinPx
        )
        // 3h task at 10:00 - 13:00 on 11/16
        val threeHour = GanttLayoutSupport.computeBarGeometry(
            taskStartMs = epoch(2026, 11, 16, 10, 0),
            taskDeadlineMs = epoch(2026, 11, 16, 13, 0),
            startOfViewMs = startOfView,
            endOfViewMs = endOfViewThreeDay,
            dayWidthPx = dayWidth3DayPx,
            minHairlineWidthPx = hairlinePx,
            fullCardMinWidthPx = fullCardMinPx
        )

        assertEquals(GanttLayoutSupport.BarRenderMode.PROPORTIONAL_BAR, haircut.mode)
        assertTrue("1h width (${haircut.barWidthPx}) should match ~27-28px", haircut.barWidthPx in 26..29)
        assertTrue("3h width (${threeHour.barWidthPx}) should be ~3x 1h width", threeHour.barWidthPx in 80..85)
        // Must end cleanly around 21:00 (577.5px), nowhere near 11/17 midnight (660px)!
        assertTrue("barEnd=${haircut.barEndPx} must stay strictly before midnight 660", haircut.barEndPx < 600)

        // Check ONE_MONTH (80dp/day -> 240px/day -> 10px/hour)
        val dayWidthMonthPx = 80f * density
        val viewStartMonth = startOfView - 5L * GanttLayoutSupport.MS_PER_DAY // 11/16 is day index 5 (1200..1440px)
        val endOfViewMonth = viewStartMonth + 30L * GanttLayoutSupport.MS_PER_DAY
        val oneHourMonth = GanttLayoutSupport.computeBarGeometry(
            taskStartMs = epoch(2026, 11, 16, 20, 0),
            taskDeadlineMs = epoch(2026, 11, 16, 21, 0),
            startOfViewMs = viewStartMonth,
            endOfViewMs = endOfViewMonth,
            dayWidthPx = dayWidthMonthPx,
            minHairlineWidthPx = hairlinePx,
            fullCardMinWidthPx = fullCardMinPx
        )
        val sixHourMonth = GanttLayoutSupport.computeBarGeometry(
            taskStartMs = epoch(2026, 11, 16, 10, 0),
            taskDeadlineMs = epoch(2026, 11, 16, 16, 0),
            startOfViewMs = viewStartMonth,
            endOfViewMs = endOfViewMonth,
            dayWidthPx = dayWidthMonthPx,
            minHairlineWidthPx = hairlinePx,
            fullCardMinWidthPx = fullCardMinPx
        )
        assertEquals(11, oneHourMonth.barWidthPx) // ~10.5px hairline floor vs 6h = 60px
        assertEquals(60, sixHourMonth.barWidthPx)
        assertTrue("Evening 20:00-21:00 in month view must stay <= 11/17 boundary (1440)", oneHourMonth.barEndPx <= 1440)
    }

    @Test
    fun nearMidnightUltraShortTask_nudgesLeftInsteadOfCrossingMidnight() {
        val startOfView = epoch(2026, 11, 16, 0, 0)
        val endOfView = startOfView + 30L * GanttLayoutSupport.MS_PER_DAY
        val dayWidthMonthPx = 240f // 80dp @ 3x -> 10px/hour -> 10min = 1.67px < hairline 10.5px
        val hairlinePx = 10.5f

        val lateShort = GanttLayoutSupport.computeBarGeometry(
            taskStartMs = epoch(2026, 11, 16, 23, 50),
            taskDeadlineMs = epoch(2026, 11, 16, 23, 59),
            startOfViewMs = startOfView,
            endOfViewMs = endOfView,
            dayWidthPx = dayWidthMonthPx,
            minHairlineWidthPx = hairlinePx,
            fullCardMinWidthPx = 312f
        )

        assertTrue(
            "End (${lateShort.barEndPx}) must never cross 11/17 midnight at 240px",
            lateShort.barEndPx <= 240
        )
        assertTrue(lateShort.barWidthPx >= 9)
    }

    @Test
    fun externalLabelPlacement_flipsLeftForEveningTasks_andRightForMorningTasks() {
        val dayWidthPx = 660f // 220dp @ 3x
        val totalViewWidth = 1980
        val labelWidth = 240
        val gap = 18

        // Morning 08:00 - 09:00 -> right space abundant -> place RIGHT
        val morning = GanttLayoutSupport.BarGeometry(220, 28, GanttLayoutSupport.BarRenderMode.PROPORTIONAL_BAR)
        val morningPlacement = GanttLayoutSupport.computeExternalLabelPlacement(
            geometry = morning,
            labelWidthPx = labelWidth,
            labelGapPx = gap,
            dayWidthPx = dayWidthPx,
            totalViewWidthPx = totalViewWidth
        )
        assertFalse(morningPlacement.placeLeft)
        assertEquals(220 + 28 + gap, morningPlacement.xPx)

        // Evening 20:00 - 21:00 -> near 660px midnight -> place LEFT inside same day
        val evening = GanttLayoutSupport.BarGeometry(550, 28, GanttLayoutSupport.BarRenderMode.PROPORTIONAL_BAR)
        val eveningPlacement = GanttLayoutSupport.computeExternalLabelPlacement(
            geometry = evening,
            labelWidthPx = labelWidth,
            labelGapPx = gap,
            dayWidthPx = dayWidthPx,
            totalViewWidthPx = totalViewWidth
        )
        assertTrue(eveningPlacement.placeLeft)
        assertEquals(550 - gap - labelWidth, eveningPlacement.xPx)
    }

    @Test
    fun longMultiDayTask_clipsStartAndEndAndUsesFullCardMode() {
        val startOfView = epoch(2026, 11, 16, 0, 0)
        val endOfView = startOfView + 3L * GanttLayoutSupport.MS_PER_DAY
        val dayWidthPx = 660f

        val crossView = GanttLayoutSupport.computeBarGeometry(
            taskStartMs = epoch(2026, 11, 15, 12, 0),
            taskDeadlineMs = epoch(2026, 11, 20, 12, 0),
            startOfViewMs = startOfView,
            endOfViewMs = endOfView,
            dayWidthPx = dayWidthPx,
            minHairlineWidthPx = 10.5f,
            fullCardMinWidthPx = 312f
        )

        assertEquals(0, crossView.barStartPx)
        assertEquals(1980, crossView.barWidthPx)
        assertEquals(GanttLayoutSupport.BarRenderMode.FULL_CARD, crossView.mode)
    }

    @Test
    fun oneDayTaskInMonthView_usesFullCardMode_andCrossMidnightShortBarHandlesPlacement() {
        val density = 3f
        val startOfView = epoch(2026, 11, 15, 0, 0)
        val endOfView = startOfView + 30L * GanttLayoutSupport.MS_PER_DAY
        val monthDayWidthPx = 80f * density // 240 px/day
        val compactCardMinPx = 44f * density // 132 px (~13.2h in month view)

        // ~1 day task in month view (e.g. 11/15 19:20 - 11/16 19:20 -> 240px >= 132px) renders title inside card
        val oneDayMonth = GanttLayoutSupport.computeBarGeometry(
            taskStartMs = epoch(2026, 11, 15, 19, 20),
            taskDeadlineMs = epoch(2026, 11, 16, 19, 20),
            startOfViewMs = startOfView,
            endOfViewMs = endOfView,
            dayWidthPx = monthDayWidthPx,
            minHairlineWidthPx = 3.5f * density,
            fullCardMinWidthPx = compactCardMinPx
        )
        assertEquals(GanttLayoutSupport.BarRenderMode.FULL_CARD, oneDayMonth.mode)
        assertEquals(240, oneDayMonth.barWidthPx)

        // Ultra-short cross-midnight bar (23:00-02:00 on 11/15->11/16 -> 30px < 132px) checks left space against start day
        val lateCrossMidnight = GanttLayoutSupport.computeBarGeometry(
            taskStartMs = epoch(2026, 11, 15, 23, 0),
            taskDeadlineMs = epoch(2026, 11, 16, 2, 0),
            startOfViewMs = startOfView,
            endOfViewMs = endOfView,
            dayWidthPx = monthDayWidthPx,
            minHairlineWidthPx = 3.5f * density,
            fullCardMinWidthPx = compactCardMinPx
        )
        assertEquals(GanttLayoutSupport.BarRenderMode.PROPORTIONAL_BAR, lateCrossMidnight.mode)
        val placement = GanttLayoutSupport.computeExternalLabelPlacement(
            geometry = lateCrossMidnight,
            labelWidthPx = 120,
            labelGapPx = 12,
            dayWidthPx = monthDayWidthPx,
            totalViewWidthPx = 7200
        )
        assertEquals(lateCrossMidnight.barEndPx + 12, placement.xPx)
    }
}
