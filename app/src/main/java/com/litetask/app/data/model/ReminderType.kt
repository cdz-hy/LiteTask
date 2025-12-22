package com.litetask.app.data.model

/**
 * 提醒类型枚举
 * 用于表示预设的提醒时间点
 */
enum class ReminderType {
    NONE,                    // 不提醒
    AT_START,                // 任务开始时
    BEFORE_START_1H,         // 开始前1小时
    BEFORE_START_1D,         // 开始前1天
    BEFORE_END_1H,           // 结束前1小时
    BEFORE_END_1D,           // 结束前1天
    CUSTOM                   // 自定义时间
}

/**
 * 自定义提醒的时间单位
 */
enum class ReminderTimeUnit {
    MINUTES,
    HOURS,
    DAYS
}

/**
 * 自定义提醒的基准时间
 */
enum class ReminderBaseTime {
    BEFORE_START,  // 开始前
    BEFORE_END     // 结束前
}

/**
 * 提醒配置数据类
 * 用于在UI层传递提醒设置
 */
data class ReminderConfig(
    val type: ReminderType,
    val customValue: Int = 0,           // 自定义时间值
    val customUnit: ReminderTimeUnit = ReminderTimeUnit.HOURS,
    val customBase: ReminderBaseTime = ReminderBaseTime.BEFORE_END
) {
    /**
     * 计算实际的提醒时间戳
     */
    fun calculateTriggerTime(startTime: Long, deadline: Long): Long {
        return when (type) {
            ReminderType.NONE -> 0L
            ReminderType.AT_START -> startTime
            ReminderType.BEFORE_START_1H -> startTime - 60 * 60 * 1000
            ReminderType.BEFORE_START_1D -> startTime - 24 * 60 * 60 * 1000
            ReminderType.BEFORE_END_1H -> deadline - 60 * 60 * 1000
            ReminderType.BEFORE_END_1D -> deadline - 24 * 60 * 60 * 1000
            ReminderType.CUSTOM -> {
                val offsetMillis = when (customUnit) {
                    ReminderTimeUnit.MINUTES -> customValue * 60 * 1000L
                    ReminderTimeUnit.HOURS -> customValue * 60 * 60 * 1000L
                    ReminderTimeUnit.DAYS -> customValue * 24 * 60 * 60 * 1000L
                }
                when (customBase) {
                    ReminderBaseTime.BEFORE_START -> startTime - offsetMillis
                    ReminderBaseTime.BEFORE_END -> deadline - offsetMillis
                }
            }
        }
    }

    /**
     * 生成提醒标签
     */
    fun generateLabel(): String {
        return when (type) {
            ReminderType.NONE -> ""
            ReminderType.AT_START -> "任务开始"
            ReminderType.BEFORE_START_1H -> "开始前1小时"
            ReminderType.BEFORE_START_1D -> "开始前1天"
            ReminderType.BEFORE_END_1H -> "截止前1小时"
            ReminderType.BEFORE_END_1D -> "截止前1天"
            ReminderType.CUSTOM -> {
                val unitStr = when (customUnit) {
                    ReminderTimeUnit.MINUTES -> "分钟"
                    ReminderTimeUnit.HOURS -> "小时"
                    ReminderTimeUnit.DAYS -> "天"
                }
                val baseStr = when (customBase) {
                    ReminderBaseTime.BEFORE_START -> "开始前"
                    ReminderBaseTime.BEFORE_END -> "截止前"
                }
                "$baseStr$customValue$unitStr"
            }
        }
    }
}
