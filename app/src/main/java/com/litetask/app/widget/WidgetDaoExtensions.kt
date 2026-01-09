package com.litetask.app.widget

import com.litetask.app.data.local.TaskDao
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskProgress
import java.util.Calendar

/**
 * TaskDao 扩展函数
 * 为 Widget 提供便捷的数据访问方法
 */

/**
 * 获取今日任务（自动计算今日时间范围）
 */
suspend fun TaskDao.getTodayTasksSync(): List<Task> {
    val (startOfDay, endOfDay) = getTodayRange()
    return getTodayTasksSyncWithRange(startOfDay, endOfDay)
}

/**
 * 获取今日任务进度（自动计算今日时间范围）
 */
suspend fun TaskDao.getTodayTasksProgress(): Pair<Int, Int> {
    val (startOfDay, endOfDay) = getTodayRange()
    val progress = getTodayTasksProgressWithRange(startOfDay, endOfDay)
    return (progress?.done ?: 0) to (progress?.total ?: 0)
}

/**
 * 获取即将截止的任务
 */
suspend fun TaskDao.getUpcomingDeadlinesSync(limit: Int = 10): List<Task> {
    return getUpcomingDeadlinesSyncWithTime(System.currentTimeMillis(), limit)
}

/**
 * 获取最紧急的任务
 */
suspend fun TaskDao.getMostUrgentTaskSync(): Task? {
    return getMostUrgentTaskSyncWithTime(System.currentTimeMillis())
}

/**
 * 获取今日时间范围
 */
private fun getTodayRange(): Pair<Long, Long> {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val startOfDay = calendar.timeInMillis
    
    calendar.add(Calendar.DAY_OF_YEAR, 1)
    val endOfDay = calendar.timeInMillis
    
    return startOfDay to endOfDay
}
