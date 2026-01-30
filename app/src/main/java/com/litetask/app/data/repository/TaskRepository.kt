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
    
    // 分页加载历史
    suspend fun getHistoryTasks(limit: Int, offset: Int) = taskDao.getHistoryTasks(limit, offset)

    fun getTaskDetail(id: Long) = taskDao.getTaskDetail(id)

    suspend fun insertTask(task: Task) = taskDao.insertTask(task)
    suspend fun insertSubTask(subTask: SubTask) = taskDao.insertSubTask(subTask)
    
    suspend fun updateTask(task: Task) = taskDao.updateTask(task)
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
     * 自动标记过期任务（不再自动完成，而是标记为过期状态）
     */
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
        taskDao.updateTask(task)
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
            // 从完成变为未完成：清除完成时间
            oldTask.isDone && !newTask.isDone -> {
                taskDao.updateTask(newTask.copy(completedAt = null))
            }
            // 其他情况：正常更新
            else -> {
                taskDao.updateTask(newTask)
            }
        }
    }
}