package com.litetask.app.ui.components

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 甘特图任务条与外部标签的纯 Kotlin 几何测算引擎（毫秒级 Double 精度 + 日边界护栏，100% 可 JVM 单测）。
 */
object GanttLayoutSupport {

    const val MS_PER_DAY: Long = 24L * 60L * 60L * 1000L

    enum class BarRenderMode {
        FULL_CARD,
        PROPORTIONAL_BAR
    }

    data class BarGeometry(
        val barStartPx: Int,
        val barWidthPx: Int,
        val mode: BarRenderMode
    ) {
        val barEndPx: Int get() = barStartPx + barWidthPx
    }

    data class ExternalLabelPlacement(
        val xPx: Int,
        val placeLeft: Boolean
    )

    /**
     * 基于真实起止时间戳按严格比例计算任务条位置与宽度，确保：
     * 1. 不同时长（如 1h / 2h / 6h / 12h）在不同粒度下宽度严格成比例；
     * 2. 短任务不强制撑宽成 44dp 固定块，不会把当日晚间任务误画到次日格子里；
     * 3. 当跨度小于极细可见线宽（如 < 3dp）时，向左微调起点而非向右越过当天的零点分割线。
     */
    fun computeBarGeometry(
        taskStartMs: Long,
        taskDeadlineMs: Long,
        startOfViewMs: Long,
        endOfViewMs: Long,
        dayWidthPx: Float,
        minHairlineWidthPx: Float,
        fullCardMinWidthPx: Float
    ): BarGeometry {
        val safeDayWidthPx = dayWidthPx.coerceAtLeast(1f)
        val safeHairlinePx = minHairlineWidthPx.coerceIn(1f, safeDayWidthPx)
        val safeEndOfViewMs = max(startOfViewMs, endOfViewMs)

        val rawEndMs = max(taskStartMs, taskDeadlineMs)
        val clampedStartMs = max(taskStartMs, startOfViewMs)
        val clampedEndMs = if (safeEndOfViewMs > startOfViewMs) {
            min(rawEndMs, safeEndOfViewMs)
        } else {
            rawEndMs
        }

        val totalViewWidthPx = if (safeEndOfViewMs > startOfViewMs) {
            (((safeEndOfViewMs - startOfViewMs).toDouble() * safeDayWidthPx) / MS_PER_DAY).roundToInt()
                .coerceAtLeast(1)
        } else {
            Int.MAX_VALUE
        }

        if (clampedEndMs <= clampedStartMs) {
            val anchorX = (((clampedStartMs - startOfViewMs).toDouble() * safeDayWidthPx) / MS_PER_DAY)
                .toFloat()
            val w = safeHairlinePx.roundToInt().coerceAtLeast(1)
            val maxStart = (totalViewWidthPx - w).coerceAtLeast(0)
            val x = anchorX.roundToInt().coerceIn(0, maxStart)
            val mode = if (w >= fullCardMinWidthPx) BarRenderMode.FULL_CARD else BarRenderMode.PROPORTIONAL_BAR
            return BarGeometry(barStartPx = x, barWidthPx = w, mode = mode)
        }

        val exactStartPx = ((clampedStartMs - startOfViewMs).toDouble() * safeDayWidthPx) / MS_PER_DAY
        val exactEndPx = ((clampedEndMs - startOfViewMs).toDouble() * safeDayWidthPx) / MS_PER_DAY
        val exactWidthPx = exactEndPx - exactStartPx

        val resolvedStartFloat: Double
        val resolvedWidthFloat: Double

        if (exactWidthPx >= safeHairlinePx) {
            resolvedStartFloat = exactStartPx
            resolvedWidthFloat = exactWidthPx
        } else {
            // 极短任务的可见最小线宽护栏：绝不向右突破所在日列的 24:00 边界或视图右边界
            val endsExactlyAtMidnight = ((clampedEndMs - startOfViewMs) % MS_PER_DAY) == 0L
            val dayColumnRightPx = if (endsExactlyAtMidnight && exactEndPx > 0.0) {
                exactEndPx
            } else {
                (floor(exactEndPx / safeDayWidthPx).toInt() + 1) * safeDayWidthPx.toDouble()
            }
            val rightLimitPx = min(dayColumnRightPx, totalViewWidthPx.toDouble())
            if (exactStartPx + safeHairlinePx <= rightLimitPx) {
                resolvedStartFloat = exactStartPx
                resolvedWidthFloat = safeHairlinePx.toDouble()
            } else {
                val clampedEndEdge = min(exactEndPx, rightLimitPx)
                val dayColumnLeftPx = max(0.0, dayColumnRightPx - safeDayWidthPx.toDouble())
                resolvedStartFloat = max(dayColumnLeftPx, clampedEndEdge - safeHairlinePx)
                resolvedWidthFloat = max(1.0, clampedEndEdge - resolvedStartFloat)
            }
        }

        var barStartPx = resolvedStartFloat.roundToInt().coerceAtLeast(0)
        var barWidthPx = resolvedWidthFloat.roundToInt().coerceAtLeast(1)
        if (totalViewWidthPx < Int.MAX_VALUE) {
            if (barStartPx >= totalViewWidthPx) {
                barStartPx = (totalViewWidthPx - barWidthPx).coerceAtLeast(0)
            }
            if (barStartPx + barWidthPx > totalViewWidthPx) {
                barWidthPx = (totalViewWidthPx - barStartPx).coerceAtLeast(1)
            }
        }

        val mode = if (barWidthPx >= fullCardMinWidthPx) {
            BarRenderMode.FULL_CARD
        } else {
            BarRenderMode.PROPORTIONAL_BAR
        }
        return BarGeometry(
            barStartPx = barStartPx,
            barWidthPx = barWidthPx,
            mode = mode
        )
    }

    /**
     * 在测量阶段即可提前判定的方向提示（用于在 Compose measure 阶段同步决定外部标签左对齐还是右对齐）。
     */
    fun shouldPreferLeftLabel(
        geometry: BarGeometry,
        dayWidthPx: Float,
        estimatedThresholdPx: Float
    ): Boolean {
        val safeDayWidth = dayWidthPx.coerceAtLeast(1f)
        val startDayIndex = floor(geometry.barStartPx / safeDayWidth).toInt().coerceAtLeast(0)
        val endAnchorPx = max(geometry.barStartPx, geometry.barEndPx - 1)
        val endDayIndex = floor(endAnchorPx / safeDayWidth).toInt().coerceAtLeast(startDayIndex)
        val dayStartPx = (startDayIndex * safeDayWidth).roundToInt()
        val dayEndPx = ((endDayIndex + 1) * safeDayWidth).roundToInt()
        val spaceRightInDay = (dayEndPx - geometry.barEndPx).coerceAtLeast(0)
        val spaceLeftInDay = (geometry.barStartPx - dayStartPx).coerceAtLeast(0)
        return spaceRightInDay < estimatedThresholdPx &&
            spaceLeftInDay > spaceRightInDay &&
            geometry.barStartPx >= estimatedThresholdPx * 0.6f
    }

    /**
     * 为窄任务条挑选最不易越界或撞到日分割线的外部文字标签位置：
     * - 优先紧贴条块右侧放置；
     * - 若当前日列右侧剩余空间不足以容纳标签（如晚间 20:00 的任务）或贴近视图右边缘，
     *   且条块左侧空间更充足时，自动左置于条块左侧（右对齐紧贴条块左沿），保证文字与条块都留在当日区间内。
     */
    fun computeExternalLabelPlacement(
        geometry: BarGeometry,
        labelWidthPx: Int,
        labelGapPx: Int,
        dayWidthPx: Float,
        totalViewWidthPx: Int
    ): ExternalLabelPlacement {
        val safeLabelW = labelWidthPx.coerceAtLeast(0)
        val safeGap = labelGapPx.coerceAtLeast(0)
        val safeDayWidth = dayWidthPx.coerceAtLeast(1f)
        val startDayIndex = floor(geometry.barStartPx / safeDayWidth).toInt().coerceAtLeast(0)
        val endAnchorPx = max(geometry.barStartPx, geometry.barEndPx - 1)
        val endDayIndex = floor(endAnchorPx / safeDayWidth).toInt().coerceAtLeast(startDayIndex)
        val dayStartPx = (startDayIndex * safeDayWidth).roundToInt()
        val dayEndPx = ((endDayIndex + 1) * safeDayWidth).roundToInt()

        val spaceRightInDay = (dayEndPx - geometry.barEndPx).coerceAtLeast(0)
        val spaceLeftInDay = (geometry.barStartPx - dayStartPx).coerceAtLeast(0)

        val fitsRightInDay = spaceRightInDay >= (safeGap + safeLabelW)
        val fitsRightInView = (geometry.barEndPx + safeGap + safeLabelW) <= totalViewWidthPx
        val fitsLeftInView = (geometry.barStartPx - safeGap - safeLabelW) >= 0

        val placeLeft = fitsLeftInView && (
            !fitsRightInView || (!fitsRightInDay && spaceLeftInDay > spaceRightInDay)
        )

        val rawX = if (placeLeft) {
            geometry.barStartPx - safeGap - safeLabelW
        } else {
            geometry.barEndPx + safeGap
        }
        val clampedX = rawX.coerceIn(0, (totalViewWidthPx - safeLabelW).coerceAtLeast(0))
        return ExternalLabelPlacement(
            xPx = clampedX,
            placeLeft = placeLeft
        )
    }
}
