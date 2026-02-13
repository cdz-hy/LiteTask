package com.litetask.app.data.repository

import android.util.Log
import com.litetask.app.data.local.TaskDao
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.SubTask
import com.litetask.app.data.model.Reminder
import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.data.model.TaskType
import com.litetask.app.reminder.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TaskRepository"

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    private val reminderScheduler: ReminderScheduler
) {
    // 拆分流
    fun getPinnedTasks() = taskDao.getPinnedTasks()
    fun getActiveNonPinnedTasks() = taskDao.getActiveNonPinnedTasks()
    fun getActiveTasks() = taskDao.getActiveTasks()
    
    // 获取所有需要在首页显示的任务
    fun getAllDisplayTasks() = taskDao.getAllDisplayTasks()
    
    // 分页加载历史
    suspend fun getHistoryTasks(limit: Int, offset: Int) = taskDao.getHistoryTasks(limit, offset)

    fun getTaskDetail(id: Long) = taskDao.getTaskDetail(id)

    suspend fun insertTask(task: Task) = taskDao.insertTask(task)
    suspend fun insertSubTask(subTask: SubTask) = taskDao.insertSubTask(subTask)
    
    suspend fun updateTask(task: Task) {
        // 如果截止时间在未来，自动清除过期状态（防止 UI 传回过时的过期标记）
        var taskToUpdate = if (task.isExpired && task.deadline > System.currentTimeMillis()) {
            task.copy(isExpired = false, expiredAt = null)
        } else {
            task
        }
        
        // 核心修正：确保未完成任务的完成时间字段为 null，避免数据不一致
        if (!taskToUpdate.isDone && taskToUpdate.completedAt != null) {
            taskToUpdate = taskToUpdate.copy(completedAt = null)
        }
        
        taskDao.updateTask(taskToUpdate)
    }
    suspend fun deleteTask(task: Task) = taskDao.deleteTask(task)
    suspend fun updateSubTaskStatus(id: Long, completed: Boolean) = taskDao.updateSubTaskStatus(id, completed)
    suspend fun deleteSubTask(subTask: SubTask) = taskDao.deleteSubTask(subTask)

    suspend fun updateTaskWithSubTasks(task: Task, subTasks: List<SubTask>) {
        taskDao.updateTask(task)
        taskDao.deleteSubTasksByTaskId(task.id)
        if (subTasks.isNotEmpty()) {
            val newSubTasks = subTasks.map { it.copy(taskId = task.id, id = 0) } 
            taskDao.insertSubTasks(newSubTasks)
        }
    }
    
    suspend fun insertTasks(tasks: List<Task>) = taskDao.insertTasks(tasks)
    
    /**
     * 同步任务过期状态（双向：标记过期 + 恢复未过期）
     */
    suspend fun autoSyncTaskExpiredStatus(time: Long) = taskDao.autoSyncTaskExpiredStatus(time)
    
    suspend fun autoMarkTasksAsExpired(time: Long) = taskDao.autoMarkTasksAsExpired(time)
    suspend fun autoMarkOverdueRemindersAsFired(time: Long) = taskDao.autoMarkOverdueRemindersAsFired(time)
    
    /**
     * 获取已过期但未完成的任务
     */
    suspend fun getExpiredTasks(limit: Int, offset: Int) = taskDao.getExpiredTasks(limit, offset)
    
    /**
     * 重新激活过期任务
     */
    suspend fun reactivateExpiredTask(taskId: Long, newDeadline: Long) = 
        taskDao.reactivateExpiredTask(taskId, newDeadline)
    
    // 搜索
    fun searchTasks(
        query: String,
        types: List<TaskType>,
        startDate: Long?,
        endDate: Long?
    ) = taskDao.searchTasks(
        query = query,
        types = types.map { it.name },
        typesEmpty = types.isEmpty(),
        startDate = startDate,
        endDate = endDate
    )
    
    // 兼容
    fun getAllTasks() = taskDao.getAllTasks()
    fun getTasksInRange(start: Long, end: Long) = taskDao.getTasksInRange(start, end)
    
    // --- 提醒相关操作 ---
    suspend fun insertReminder(reminder: Reminder) = taskDao.insertReminder(reminder)
    suspend fun insertReminders(reminders: List<Reminder>) = taskDao.insertReminders(reminders)
    fun getRemindersByTaskId(taskId: Long) = taskDao.getRemindersByTaskId(taskId)
    suspend fun getRemindersByTaskIdSync(taskId: Long) = taskDao.getRemindersByTaskIdSync(taskId)
    suspend fun deleteRemindersByTaskId(taskId: Long) = taskDao.deleteRemindersByTaskId(taskId)
    suspend fun deleteReminder(reminder: Reminder) = taskDao.deleteReminder(reminder)
    suspend fun updateReminderFired(reminderId: Long, fired: Boolean) = taskDao.updateReminderFired(reminderId, fired)
    suspend fun getPendingReminders(currentTime: Long) = taskDao.getPendingReminders(currentTime)
    
    /**
     * 更新任务及其提醒
     * 
     * 流程：
     * 1. 取消该任务的所有旧闹钟
     * 2. 更新数据库中的任务和提醒
     * 3. 如果任务未完成，注册新的闹钟（带任务信息）
     */
    suspend fun updateTaskWithReminders(task: Task, reminders: List<Reminder>) {
        Log.i(TAG, "★ updateTaskWithReminders: task=${task.title}, reminders=${reminders.size}")
        
        // 1. 取消旧闹钟
        val oldReminders = taskDao.getRemindersByTaskIdSync(task.id)
        if (oldReminders.isNotEmpty()) {
            reminderScheduler.cancelRemindersForTask(task.id, oldReminders.map { it.id })
        }
        
        // 2. 更新数据库
        // 如果截止时间在未来，自动清除过期状态
        val taskToUpdate = if (task.isExpired && task.deadline > System.currentTimeMillis()) {
            task.copy(isExpired = false, expiredAt = null)
        } else {
            task
        }
        taskDao.updateTask(taskToUpdate)
        taskDao.deleteRemindersByTaskId(task.id)
        
        // 3. 只有任务未完成时才注册新闹钟
        if (reminders.isNotEmpty() && !task.isDone) {
            val newReminders = reminders.map { it.copy(taskId = task.id, id = 0) }
            taskDao.insertReminders(newReminders)
            
            // 注册新闹钟（带任务信息）
            val savedReminders = taskDao.getRemindersByTaskIdSync(task.id)
            savedReminders.forEach { reminder ->
                reminderScheduler.scheduleReminderWithTaskInfo(
                    reminder = reminder,
                    taskTitle = task.title,
                    taskType = task.type.name
                )
            }
        }
    }
    
    /**
     * 插入任务并设置提醒
     * 
     * 流程：
     * 1. 插入任务到数据库，获取 Task ID
     * 2. 插入提醒到数据库
     * 3. 注册闹钟（带任务信息，避免 Receiver 查询数据库）
     */
    suspend fun insertTaskWithReminders(task: Task, reminders: List<Reminder>): Long {
        Log.i(TAG, "★ insertTaskWithReminders: task=${task.title}, reminders=${reminders.size}")
        
        // 1. 插入任务
        val taskId = taskDao.insertTask(task)
        Log.d(TAG, "Task inserted with id=$taskId")
        
        if (reminders.isNotEmpty()) {
            // 2. 插入提醒
            val newReminders = reminders.map { it.copy(taskId = taskId, id = 0) }
            taskDao.insertReminders(newReminders)
            
            // 3. 注册闹钟（带任务信息）
            val savedReminders = taskDao.getRemindersByTaskIdSync(taskId)
            Log.d(TAG, "Scheduling ${savedReminders.size} reminders with task info")
            
            savedReminders.forEach { reminder ->
                // 使用新方法，将任务信息直接放入 Intent
                val success = reminderScheduler.scheduleReminderWithTaskInfo(
                    reminder = reminder,
                    taskTitle = task.title,
                    taskType = task.type.name
                )
                Log.i(TAG, "★ Schedule result: $success for reminder ${reminder.id}")
            }
        }
        
        return taskId
    }
    
    /**
     * 删除任务（同时取消相关闹钟）
     */
    suspend fun deleteTaskWithReminders(task: Task) {
        // 取消闹钟
        val reminders = taskDao.getRemindersByTaskIdSync(task.id)
        if (reminders.isNotEmpty()) {
            reminderScheduler.cancelRemindersForTask(task.id, reminders.map { it.id })
        }
        
        // 删除任务（提醒会通过外键级联删除）
        taskDao.deleteTask(task)
    }
    
    /**
     * 标记任务完成（同时取消相关闹钟并记录完成时间）
     */
    suspend fun markTaskDone(task: Task) {
        // 取消闹钟
        val reminders = taskDao.getRemindersByTaskIdSync(task.id)
        if (reminders.isNotEmpty()) {
            reminderScheduler.cancelRemindersForTask(task.id, reminders.map { it.id })
        }
        
        // 更新任务状态，记录完成时间
        taskDao.markTaskCompleted(task.id, System.currentTimeMillis())
    }
    
    /**
     * 标记任务未完成（清除完成时间）
     */
    suspend fun markTaskUndone(task: Task) {
        taskDao.markTaskUncompleted(task.id)
    }
    
    /**
     * 清理过期的提醒（已过触发时间但未触发的）
     * 
     * 应在应用启动时调用，清理系统中残留的无效 Alarm
     * 例如：用户关机期间错过的提醒
     */
    suspend fun cleanupExpiredReminders() {
        val currentTime = System.currentTimeMillis()
        
        // 查询所有未触发但已过期的提醒
        val expiredReminders = taskDao.getExpiredUnfiredReminders(currentTime)
        
        if (expiredReminders.isEmpty()) {
            Log.d(TAG, "No expired reminders to clean up")
            return
        }
        
        Log.i(TAG, "Cleaning up ${expiredReminders.size} expired reminders")
        
        // 取消系统中的 Alarm
        expiredReminders.forEach { reminder ->
            try {
                reminderScheduler.cancelReminder(reminder)
            } catch (e: Exception) {
                Log.e(TAG, "Error canceling expired reminder ${reminder.id}: ${e.message}")
            }
        }
        
        // 标记为已触发（避免重复处理）
        expiredReminders.forEach { reminder ->
            taskDao.updateReminderFired(reminder.id, true)
        }
        
        Log.i(TAG, "Cleaned up ${expiredReminders.size} expired reminders")
    }
    
    /**
     * 更新任务状态（处理完成状态变化时的时间记录）
     */
    suspend fun updateTaskWithStatusTracking(oldTask: Task, newTask: Task) {
        when {
            // 从未完成变为完成：记录完成时间，取消闹钟
            !oldTask.isDone && newTask.isDone -> {
                val reminders = taskDao.getRemindersByTaskIdSync(oldTask.id)
                if (reminders.isNotEmpty()) {
                    reminderScheduler.cancelRemindersForTask(oldTask.id, reminders.map { it.id })
                }
                taskDao.updateTask(newTask.copy(
                    completedAt = System.currentTimeMillis(),
                    isPinned = false  // 完成时自动取消置顶
                ))
            }
            // 从完成变为未完成：清除完成时间，重新注册闹钟
            oldTask.isDone && !newTask.isDone -> {
                val currentTime = System.currentTimeMillis()
                // 如果已过截止时间，立即标记为过期，确保它出现在过期列表中
                val isNowExpired = newTask.deadline > 0 && newTask.deadline < currentTime
                
                taskDao.updateTask(newTask.copy(
                    completedAt = null,
                    isExpired = isNowExpired,
                    expiredAt = if (isNowExpired) newTask.deadline else null
                ))
                
                // 重新注册未触发的提醒闹钟
                val reminders = taskDao.getRemindersByTaskIdSync(newTask.id)
                val pendingReminders = reminders.filter { !it.isFired && it.triggerAt > currentTime }
                if (pendingReminders.isNotEmpty()) {
                    Log.i(TAG, "Re-scheduling ${pendingReminders.size} reminders for uncompleted task ${newTask.id}")
                    pendingReminders.forEach { reminder ->
                        reminderScheduler.scheduleReminderWithTaskInfo(
                            reminder = reminder,
                            taskTitle = newTask.title,
                            taskType = newTask.type.name
                        )
                    }
                }
            }
            // 其他情况
            else -> {
                taskDao.updateTask(newTask)
            }
        }
    }
}