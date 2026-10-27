package com.litetask.app.data.repository

import android.content.Context
import android.util.Log
import com.litetask.app.data.local.CategoryDao
import com.litetask.app.data.local.PreferenceManager
import com.litetask.app.data.local.TaskDao
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.SubTask
import com.litetask.app.data.model.Reminder
import com.litetask.app.data.model.TaskComponent
import com.litetask.app.data.model.TaskComponentEntity
import com.litetask.app.data.model.TaskComponentRegistry
import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.data.model.TaskType
import com.litetask.app.data.model.toEntity
import com.litetask.app.data.model.toTaskComponentsSafe
import com.litetask.app.reminder.ReminderScheduler
import com.litetask.app.util.CalendarSyncHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TaskRepository"

@Singleton
class TaskRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: TaskDao,
    private val categoryDao: CategoryDao,
    private val taskComponentDao: com.litetask.app.data.local.TaskComponentDao,
    private val reminderScheduler: ReminderScheduler,
    private val preferenceManager: PreferenceManager
) {
    private val autoSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun triggerCalendarAutoSync(taskId: Long) {
        if (taskId <= 0L || !preferenceManager.isCalendarAutoPushEnabled()) return
        if (!CalendarSyncHelper.hasCalendarPermissions(context)) return
        autoSyncScope.launch {
            runCatching {
                val detail = taskDao.getTaskDetailCompositeSync(taskId) ?: return@runCatching
                CalendarSyncHelper.syncSingleTask(
                    context = context,
                    detail = detail,
                    targetCalendarId = preferenceManager.getCalendarTargetId(),
                    includeFinished = preferenceManager.isCalendarPushIncludeDone(),
                    includeReminders = preferenceManager.isCalendarSyncReminders()
                )
            }
        }
    }

    private fun triggerCalendarDeleteSync(taskId: Long) {
        if (taskId <= 0L || !preferenceManager.isCalendarAutoPushEnabled()) return
        if (!CalendarSyncHelper.hasCalendarPermissions(context)) return
        autoSyncScope.launch {
            runCatching {
                CalendarSyncHelper.deleteSyncedTaskEvent(context, taskId)
            }
        }
    }
    // 拆分流
    fun getPinnedTasks() = taskDao.getPinnedTasks()
    fun getActiveNonPinnedTasks() = taskDao.getActiveNonPinnedTasks()
    fun getActiveTasks() = taskDao.getActiveTasks()
    
    // 获取所有需要在首页显示的任务
    fun getAllDisplayTasks() = taskDao.getAllDisplayTasks()
    
    // 分页加载历史
    suspend fun getHistoryTasks(limit: Int, offset: Int) = taskDao.getHistoryTasks(limit, offset)

    fun getTaskDetail(id: Long) = taskDao.getTaskDetail(id)

    suspend fun insertTask(task: Task): Long {
        val id = taskDao.insertTask(task)
        triggerCalendarAutoSync(id)
        return id
    }
    suspend fun insertSubTask(subTask: SubTask) {
        taskDao.insertSubTask(subTask)
        triggerCalendarAutoSync(subTask.taskId)
    }

    suspend fun addSubTask(taskId: Long, content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        taskDao.addSubTaskAtEnd(taskId, trimmed)
        triggerCalendarAutoSync(taskId)
    }

    suspend fun insertSubTasksBatch(
        taskId: Long,
        contents: List<String>,
        replaceUncompleted: Boolean = false
    ) {
        val cleaned = contents.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return
        taskDao.insertSubTasksBatch(taskId, cleaned, replaceUncompleted)
        triggerCalendarAutoSync(taskId)
    }
    
    suspend fun updateTask(task: Task, syncToCalendar: Boolean = true) {
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
        if (syncToCalendar) {
            triggerCalendarAutoSync(taskToUpdate.id)
        }
    }
    suspend fun deleteTask(task: Task) {
        taskDao.deleteTask(task)
        triggerCalendarDeleteSync(task.id)
    }
    suspend fun updateSubTaskStatus(id: Long, completed: Boolean) = taskDao.updateSubTaskStatus(id, completed)

    suspend fun updateSubTaskContent(taskId: Long, subTaskId: Long, content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        taskDao.updateSubTaskContent(subTaskId, trimmed)
        triggerCalendarAutoSync(taskId)
    }

    suspend fun reorderSubTasks(taskId: Long, orderedSubTasks: List<SubTask>) {
        if (orderedSubTasks.isEmpty()) return
        taskDao.reorderSubTasks(taskId, orderedSubTasks.map { it.id })
        triggerCalendarAutoSync(taskId)
    }

    suspend fun deleteSubTask(subTask: SubTask) {
        taskDao.deleteSubTask(subTask)
        triggerCalendarAutoSync(subTask.taskId)
    }

    suspend fun updateTaskWithSubTasks(task: Task, subTasks: List<SubTask>) {
        taskDao.updateTaskWithSubTasks(task, subTasks)
        triggerCalendarAutoSync(task.id)
    }

    suspend fun insertTaskWithRemindersAndReturnId(
        task: Task,
        reminders: List<com.litetask.app.data.model.Reminder>,
        syncToCalendar: Boolean = true
    ): Long {
        val taskId = taskDao.insertTask(task)
        if (reminders.isNotEmpty()) {
            val remindersWithId = reminders.map { it.copy(taskId = taskId) }
            taskDao.insertReminders(remindersWithId)
            
            // 调度提醒
            val category = categoryDao.getCategoryById(task.categoryId)
            remindersWithId.forEach { reminder ->
                reminderScheduler.scheduleReminderWithTaskInfo(
                    reminder,
                    task.title,
                    task.type.name,
                    category?.name,
                    category?.colorHex
                )
            }
        }
        if (syncToCalendar) {
            triggerCalendarAutoSync(taskId)
        }
        return taskId
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
    suspend fun reactivateExpiredTask(taskId: Long, newDeadline: Long) {
        taskDao.reactivateExpiredTask(taskId, newDeadline)
        triggerCalendarAutoSync(taskId)
    }
    
    // 搜索
    // 搜索
    fun searchTasks(
        query: String,
        types: List<TaskType>,
        categoryIds: List<Long>,
        startDate: Long?,
        endDate: Long?
    ) = taskDao.searchTasks(
        query = query,
        types = types.map { it.name },
        categoryIds = categoryIds,
        startDate = startDate,
        endDate = endDate
    ).map { list ->
        val trimmed = query.trim()
        if (trimmed.isEmpty()) list else list.filter { TaskComponentRegistry.matchesSearchQuery(it, trimmed) }
    }
    
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
     * 为指定任务新增单条提醒并注册系统闹钟（90 秒内防重）。
     */
    suspend fun addAndScheduleReminder(taskId: Long, triggerAt: Long, label: String): Boolean {
        if (taskId <= 0L || triggerAt <= System.currentTimeMillis()) return false
        val task = taskDao.getTaskById(taskId) ?: return false
        if (task.isDone) return false
        val existing = taskDao.getRemindersByTaskIdSync(taskId)
        if (existing.any { kotlin.math.abs(it.triggerAt - triggerAt) < 90_000L }) {
            return false
        }
        val insertedId = taskDao.insertReminder(
            Reminder(
                taskId = taskId,
                triggerAt = triggerAt,
                label = label
            )
        )
        val savedReminder = Reminder(
            id = insertedId,
            taskId = taskId,
            triggerAt = triggerAt,
            label = label
        )
        val category = categoryDao.getCategoryById(task.categoryId)
        reminderScheduler.scheduleReminderWithTaskInfo(
            reminder = savedReminder,
            taskTitle = task.title,
            taskType = @Suppress("DEPRECATION") task.type.name,
            categoryName = category?.name,
            categoryColor = category?.colorHex
        )
        triggerCalendarAutoSync(taskId)
        return true
    }
    
    // --- 组件相关操作 ---
    fun getComponentsByTaskId(taskId: Long) = taskComponentDao.getComponentsByTaskId(taskId)

    suspend fun getComponentsByTaskIdSync(taskId: Long) = taskComponentDao.getComponentsByTaskIdSync(taskId)

    fun triggerCalendarAutoSyncForTask(taskId: Long) = triggerCalendarAutoSync(taskId)

    suspend fun insertComponent(
        component: TaskComponentEntity,
        syncCalendar: Boolean = true
    ): Long {
        val safeEntity = if (component.id > 0L) component else component.copy(id = 0L)
        val id = taskComponentDao.insertComponent(safeEntity)
        if (syncCalendar) {
            triggerCalendarAutoSync(safeEntity.taskId)
        }
        return id
    }

    suspend fun updateComponent(
        component: TaskComponentEntity,
        syncCalendar: Boolean = true
    ) {
        if (component.id <= 0L) return
        taskComponentDao.updateComponent(component)
        if (syncCalendar) {
            triggerCalendarAutoSync(component.taskId)
        }
    }

    suspend fun deleteComponent(
        component: TaskComponentEntity,
        syncCalendar: Boolean = true
    ) {
        taskComponentDao.deleteComponent(component)
        if (syncCalendar) {
            triggerCalendarAutoSync(component.taskId)
        }
    }

    suspend fun isManagedFileStillReferenced(managedPath: String?): Boolean {
        val target = managedPath?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val attachmentReferenced = taskComponentDao
            .getComponentsByTypeSync(com.litetask.app.data.model.ComponentType.FILE_ATTACHMENT)
            .any { entity ->
                val comp = entity.toTaskComponent()
                comp is TaskComponent.FileComponent && comp.data.managedFilePath == target
            }
        if (attachmentReferenced) return true
        return taskComponentDao
            .getComponentsByTypeSync(com.litetask.app.data.model.ComponentType.VOICE_NOTE)
            .any { entity ->
                val comp = entity.toTaskComponent()
                comp is TaskComponent.VoiceNoteComponent && comp.data.filePath == target
            }
    }

    /**
     * 增量同步任务下的组件集合（保留未更改记录的 id 与 createdAt），返回同步前的旧组件列表。
     */
    suspend fun syncTaskComponents(
        taskId: Long,
        newComponents: List<TaskComponent>,
        syncCalendar: Boolean = true
    ): List<TaskComponent> {
        val oldEntities = taskComponentDao.getComponentsByTaskIdSync(taskId)
        val oldComponents = oldEntities.toTaskComponentsSafe()
        val diff = TaskComponentRegistry.computeIncrementalDiff(oldEntities, newComponents, taskId)

        diff.toDelete.forEach { taskComponentDao.deleteComponent(it) }
        diff.toUpdate.forEach { taskComponentDao.updateComponent(it) }
        diff.toInsert.forEach { taskComponentDao.insertComponent(it) }

        if (syncCalendar) {
            triggerCalendarAutoSync(taskId)
        }
        return oldComponents
    }

    suspend fun insertTaskWithComponentsAndReminders(
        task: Task,
        reminders: List<Reminder>,
        components: List<TaskComponent>
    ): Long {
        val newTaskId = insertTaskWithRemindersAndReturnId(
            task = task,
            reminders = reminders,
            syncToCalendar = false
        )
        components.forEach { comp ->
            taskComponentDao.insertComponent(comp.toEntity(newTaskId))
        }
        triggerCalendarAutoSync(newTaskId)
        return newTaskId
    }
    
    /**
     * 更新任务及其提醒
     * 
     * 流程：
     * 1. 取消该任务的所有旧闹钟
     * 2. 更新数据库中的任务和提醒
     * 3. 如果任务未完成，注册新的闹钟（带任务信息）
     */
    suspend fun updateTaskWithReminders(
        task: Task,
        reminders: List<Reminder>,
        syncToCalendar: Boolean = true
    ) {
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
            
            // 获取分类信息
            val category = categoryDao.getCategoryById(task.categoryId)
            
            // 注册新闹钟（带任务信息）
            val savedReminders = taskDao.getRemindersByTaskIdSync(task.id)
            savedReminders.forEach { reminder ->
                reminderScheduler.scheduleReminderWithTaskInfo(
                    reminder = reminder,
                    taskTitle = task.title,
                    taskType = @Suppress("DEPRECATION") task.type.name,
                    categoryName = category?.name,
                    categoryColor = category?.colorHex
                )
            }
        }
        if (syncToCalendar) {
            triggerCalendarAutoSync(task.id)
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
    suspend fun insertTaskWithReminders(
        task: Task,
        reminders: List<Reminder>,
        syncToCalendar: Boolean = true
    ): Long {
        Log.i(TAG, "★ insertTaskWithReminders: task=${task.title}, reminders=${reminders.size}")
        
        // 1. 插入任务
        val taskId = taskDao.insertTask(task)
        Log.d(TAG, "Task inserted with id=$taskId")
        
        if (reminders.isNotEmpty()) {
            // 2. 插入提醒
            val newReminders = reminders.map { it.copy(taskId = taskId, id = 0) }
            taskDao.insertReminders(newReminders)
            
            // 获取分类信息
            val category = categoryDao.getCategoryById(task.categoryId)
            
            // 3. 注册闹钟（带任务信息）
            val savedReminders = taskDao.getRemindersByTaskIdSync(taskId)
            Log.d(TAG, "Scheduling ${savedReminders.size} reminders with task info")
            
            savedReminders.forEach { reminder ->
                // 使用新方法，将任务信息直接放入 Intent
                val success = reminderScheduler.scheduleReminderWithTaskInfo(
                    reminder = reminder,
                    taskTitle = task.title,
                    taskType = @Suppress("DEPRECATION") task.type.name,
                    categoryName = category?.name,
                    categoryColor = category?.colorHex
                )
                Log.i(TAG, "★ Schedule result: $success for reminder ${reminder.id}")
            }
        }
        
        return taskId.also {
            if (syncToCalendar) {
                triggerCalendarAutoSync(it)
            }
        }
    }
    
    /**
     * 删除任务（同时取消相关闹钟）
     */
    suspend fun deleteTaskWithReminders(task: Task) {
        val attachedComponents = runCatching {
            taskComponentDao.getComponentsByTaskIdSync(task.id).toTaskComponentsSafe()
        }.getOrDefault(emptyList())

        // 取消闹钟
        val reminders = taskDao.getRemindersByTaskIdSync(task.id)
        if (reminders.isNotEmpty()) {
            reminderScheduler.cancelRemindersForTask(task.id, reminders.map { it.id })
        }
        
        // 删除任务（提醒与组件行会通过外键级联删除）
        taskDao.deleteTask(task)
        triggerCalendarDeleteSync(task.id)

        attachedComponents.forEach { comp ->
            when (comp) {
                is TaskComponent.FileComponent -> {
                    if (!isManagedFileStillReferenced(comp.data.managedFilePath)) {
                        com.litetask.app.util.AttachmentStorageHelper.deleteManagedFileIfOwned(context, comp.data)
                    }
                }
                is TaskComponent.VoiceNoteComponent -> {
                    if (!isManagedFileStillReferenced(comp.data.filePath)) {
                        com.litetask.app.util.AttachmentStorageHelper.deleteManagedVoiceNoteIfOwned(context, comp.data)
                    }
                }
                else -> Unit
            }
        }
    }
    
    /**
     * 标记任务完成（同时取消相关闹钟、记录完成时间，并在命中周期规则时幂等生成下一期任务）
     */
    suspend fun markTaskDone(task: Task): com.litetask.app.data.model.RecurrenceEngine.RecurrenceSpawnResult? {
        // 取消闹钟
        val reminders = taskDao.getRemindersByTaskIdSync(task.id)
        if (reminders.isNotEmpty()) {
            reminderScheduler.cancelRemindersForTask(task.id, reminders.map { it.id })
        }
        
        // 更新任务状态，记录完成时间
        taskDao.markTaskCompleted(task.id, System.currentTimeMillis())
        triggerCalendarAutoSync(task.id)

        val freshTask = taskDao.getTaskById(task.id) ?: task
        val spawned = RecurrenceSpawnHelper.spawnNextOccurrenceIfEligible(
            taskDao = taskDao,
            taskComponentDao = taskComponentDao,
            categoryDao = categoryDao,
            reminderScheduler = reminderScheduler,
            sourceTask = freshTask
        )
        if (spawned != null) {
            triggerCalendarAutoSync(spawned.nextTask.id)
        }
        return spawned
    }

    suspend fun undoGeneratedRecurrence(spawn: com.litetask.app.data.model.RecurrenceEngine.RecurrenceSpawnResult) {
        val targetTask = taskDao.getTaskById(spawn.nextTask.id)
        if (targetTask != null) {
            deleteTaskWithReminders(targetTask)
        }
        RecurrenceSpawnHelper.clearGeneratedNextTaskLink(
            taskComponentDao = taskComponentDao,
            sourceTaskId = spawn.sourceTaskId,
            expectedNextTaskId = spawn.nextTask.id
        )
    }
    
    /**
     * 标记任务未完成（清除完成时间）
     */
    suspend fun markTaskUndone(task: Task) {
        taskDao.markTaskUncompleted(task.id)
        triggerCalendarAutoSync(task.id)
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
     * 更新任务状态（处理完成状态变化时的时间记录，并在完成时按需触发周期实例生成）
     */
    suspend fun updateTaskWithStatusTracking(
        oldTask: Task,
        newTask: Task
    ): com.litetask.app.data.model.RecurrenceEngine.RecurrenceSpawnResult? {
        var spawnedResult: com.litetask.app.data.model.RecurrenceEngine.RecurrenceSpawnResult? = null
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
                val freshTask = taskDao.getTaskById(oldTask.id) ?: newTask
                spawnedResult = RecurrenceSpawnHelper.spawnNextOccurrenceIfEligible(
                    taskDao = taskDao,
                    taskComponentDao = taskComponentDao,
                    categoryDao = categoryDao,
                    reminderScheduler = reminderScheduler,
                    sourceTask = freshTask
                )
                if (spawnedResult != null) {
                    triggerCalendarAutoSync(spawnedResult.nextTask.id)
                }
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
                    
                    val category = categoryDao.getCategoryById(newTask.categoryId)
                    
                    pendingReminders.forEach { reminder ->
                        reminderScheduler.scheduleReminderWithTaskInfo(
                            reminder = reminder,
                            taskTitle = newTask.title,
                            taskType = @Suppress("DEPRECATION") newTask.type.name,
                            categoryName = category?.name,
                            categoryColor = category?.colorHex
                        )
                    }
                }
            }
            // 其他情况
            else -> {
                taskDao.updateTask(newTask)
            }
        }
        triggerCalendarAutoSync(newTask.id)
        return spawnedResult
    }

    // --- Agent 模式支持 ---
    
    suspend fun getRecentTasksWithLimit(status: String, requestedLimit: Int = 10): List<TaskDetailComposite> {
        val (isDone, isExpired) = when (status.lowercase()) {
            "completed" -> true to false
            "expired" -> false to true
            else -> false to false // incomplete
        }
        
        val totalCount = taskDao.getTaskCountByStatus(isDone, isExpired)
        if (totalCount == 0) return emptyList()
        
        // 动态计算最大值：不能超过 80%，最多 50 个
        val maxAllowed = (totalCount * 0.8).toInt().coerceIn(1, 50)
        
        // 将 Agent 传来的 requestedLimit 限制在这两个值之间
        val finalLimit = requestedLimit.coerceIn(1, maxAllowed)
        
        return taskDao.getRecentTasksByStatus(isDone, isExpired, finalLimit)
    }
    
    suspend fun getTaskByIdSync(taskId: Long): TaskDetailComposite? {
        return taskDao.getTaskDetailCompositeSync(taskId)
    }

    suspend fun searchTasksSync(query: String): List<TaskDetailComposite> {
        val raw = taskDao.searchTasksSync(query)
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) raw else raw.filter { TaskComponentRegistry.matchesSearchQuery(it, trimmed) }
    }
}