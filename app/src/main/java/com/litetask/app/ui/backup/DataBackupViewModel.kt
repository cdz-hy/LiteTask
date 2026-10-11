package com.litetask.app.ui.backup

import android.content.Context
import androidx.lifecycle.ViewModel
import com.litetask.app.data.local.PreferenceManager
import com.litetask.app.data.local.TaskDao
import com.litetask.app.data.model.Reminder
import com.litetask.app.data.model.Task
import com.litetask.app.data.repository.BackupRepository
import com.litetask.app.data.repository.CategoryRepository
import com.litetask.app.data.repository.RestoreResult
import com.litetask.app.data.repository.TaskRepositoryImpl
import com.litetask.app.util.CalendarSyncHelper
import com.litetask.app.util.CalendarSyncPreferencesStore
import com.litetask.app.util.IcsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class DataBackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupRepository: BackupRepository,
    private val taskDao: TaskDao,
    private val taskRepository: TaskRepositoryImpl,
    private val categoryRepository: CategoryRepository,
    private val preferenceManager: PreferenceManager,
    val calendarSyncStore: CalendarSyncPreferencesStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState = _uiState.asStateFlow()

    suspend fun exportData(outputStream: OutputStream) {
        _uiState.value = BackupUiState.Loading
        try {
            val json = backupRepository.createBackupJson()
            outputStream.use { 
                it.write(json.toByteArray()) 
            }
            _uiState.value = BackupUiState.Success("数据导出成功")
        } catch (e: Exception) {
            _uiState.value = BackupUiState.Error(e.message ?: "导出失败")
        }
    }

    suspend fun importData(inputStream: InputStream) {
        _uiState.value = BackupUiState.Importing
        try {
            val json = inputStream.bufferedReader().use { it.readText() }
            val result = backupRepository.restoreBackup(json)
            when (result) {
                is RestoreResult.Success -> {
                    _uiState.value = BackupUiState.Success("成功导入 ${result.importedCount} 条数据，跳过 ${result.skippedCount} 条重复数据")
                }
                is RestoreResult.Error -> {
                    _uiState.value = BackupUiState.Error(result.exception.message ?: "导入失败")
                }
            }
        } catch (e: Exception) {
            _uiState.value = BackupUiState.Error("文件读取失败: ${e.message}")
        }
    }

    suspend fun exportIcs(outputStream: OutputStream) {
        _uiState.value = BackupUiState.Loading
        try {
            val includeFinished = preferenceManager.isIcsExportIncludeDone()
            val includeReminders = preferenceManager.isCalendarSyncReminders()
            val details = withContext(Dispatchers.IO) {
                taskDao.getAllTaskDetailsSync()
            }
            val exportTargets = if (includeFinished) {
                details
            } else {
                details.filter { !it.task.isDone && !it.task.isExpired }
            }
            if (exportTargets.isEmpty()) {
                _uiState.value = BackupUiState.Error("当前筛选条件下没有可导出的任务日程")
                return
            }
            val icsText = withContext(Dispatchers.Default) {
                IcsHelper.exportTasksToIcs(
                    tasksWithDetails = exportTargets,
                    includeFinished = includeFinished,
                    includeReminders = includeReminders
                )
            }
            withContext(Dispatchers.IO) {
                outputStream.use { it.write(icsText.toByteArray(Charsets.UTF_8)) }
            }
            _uiState.value = BackupUiState.Success("已成功导出 ${exportTargets.size} 条任务到通用日历文件")
        } catch (e: Exception) {
            _uiState.value = BackupUiState.Error("日历文件导出失败: ${e.message}")
        }
    }

    suspend fun importIcs(inputStream: InputStream) {
        _uiState.value = BackupUiState.Importing
        try {
            val content = withContext(Dispatchers.IO) {
                inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            if (!content.contains("BEGIN:VEVENT", ignoreCase = true)) {
                _uiState.value = BackupUiState.Error("未在所选文件中找到可导入的日程内容")
                return
            }

            val parsedEvents = withContext(Dispatchers.Default) {
                IcsHelper.parseIcs(content)
            }
            if (parsedEvents.isEmpty()) {
                _uiState.value = BackupUiState.Error("未解析出可导入的日程事件")
                return
            }

            val (imported, skipped) = withContext(Dispatchers.IO) {
                val existingTasks = taskDao.getAllTaskDetailsSync().map { it.task }
                val categories = categoryRepository.getAllCategoriesSync()
                val defaultCatId = categories.firstOrNull()?.id ?: 1L
                val includeReminders = preferenceManager.isCalendarSyncReminders()
                val now = System.currentTimeMillis()
                val mutableTasks = existingTasks.toMutableList()

                var importedCount = 0
                var skippedCount = 0

                parsedEvents.forEach { evt ->
                    if (isDuplicateTask(mutableTasks, evt.title, evt.startTime)) {
                        skippedCount++
                    } else {
                        val matchedCatId = evt.categoryName?.let { catName ->
                            categories.find { it.name.equals(catName, ignoreCase = true) }?.id
                        } ?: defaultCatId

                        val isExpired = !evt.isDone && evt.deadline < now
                        val newTask = Task(
                            title = evt.title,
                            description = evt.description,
                            startTime = evt.startTime,
                            deadline = evt.deadline,
                            isDone = evt.isDone,
                            completedAt = if (evt.isDone) evt.deadline else null,
                            isExpired = isExpired,
                            expiredAt = if (isExpired) evt.deadline else null,
                            categoryId = matchedCatId,
                            originalVoiceText = "ICS导入: ${evt.title}"
                        )
                        val reminders = if (includeReminders && !evt.isDone) {
                            evt.reminderTriggerTimes
                                .filter { it > now }
                                .map { trigger ->
                                    Reminder(
                                        taskId = 0L,
                                        triggerAt = trigger,
                                        label = evt.title
                                    )
                                }
                        } else emptyList()

                        taskRepository.insertTaskWithRemindersAndReturnId(newTask, reminders)
                        mutableTasks.add(newTask)
                        importedCount++
                    }
                }
                importedCount to skippedCount
            }

            calendarSyncStore.markPullCompleted()
            _uiState.value = BackupUiState.Success("ICS 导入完成：新增 $imported 条日程，跳过 $skipped 条重复日程")
        } catch (e: Exception) {
            _uiState.value = BackupUiState.Error("ICS 导入失败: ${e.message}")
        }
    }

    suspend fun pushToSystemCalendar() {
        _uiState.value = BackupUiState.Loading
        try {
            val targetId = preferenceManager.getCalendarTargetId()
            val includeDone = preferenceManager.isCalendarPushIncludeDone()
            val includeReminders = preferenceManager.isCalendarSyncReminders()
            val result = withContext(Dispatchers.IO) {
                val details = taskDao.getAllTaskDetailsSync()
                CalendarSyncHelper.pushTasksToCalendar(
                    context = context,
                    tasksWithDetails = details,
                    targetCalendarId = targetId,
                    includeFinished = includeDone,
                    includeReminders = includeReminders
                )
            }
            result.fold(
                onSuccess = { stats ->
                    calendarSyncStore.markPushCompleted()
                    _uiState.value = BackupUiState.Success(
                        "已同步至系统日历「${stats.calendarName}」：新增 ${stats.insertedCount} 条，更新 ${stats.updatedCount} 条（跳过 ${stats.skippedCount} 条）"
                    )
                },
                onFailure = { err ->
                    _uiState.value = BackupUiState.Error(err.message ?: "推送系统日历失败")
                }
            )
        } catch (e: Exception) {
            _uiState.value = BackupUiState.Error("推送系统日历失败: ${e.message}")
        }
    }

    suspend fun importFromSystemCalendar(daysAhead: Int = preferenceManager.getCalendarPullDaysAhead()) {
        _uiState.value = BackupUiState.Importing
        try {
            val includeReminders = preferenceManager.isCalendarSyncReminders()
            val pullResult = withContext(Dispatchers.IO) {
                CalendarSyncHelper.pullUpcomingEventsFromCalendar(
                    context = context,
                    daysAhead = daysAhead,
                    includeReminders = includeReminders
                )
            }
            val externalEvents = pullResult.getOrElse { err ->
                _uiState.value = BackupUiState.Error(err.message ?: "读取系统日历失败")
                return
            }

            if (externalEvents.isEmpty()) {
                calendarSyncStore.markPullCompleted()
                _uiState.value = BackupUiState.Success("未来 ${daysAhead} 天系统日历中没有可导入的新日程")
                return
            }

            val (imported, skipped) = withContext(Dispatchers.IO) {
                val existingTasks = taskDao.getAllTaskDetailsSync().map { it.task }.toMutableList()
                val categories = categoryRepository.getAllCategoriesSync()
                val defaultCatId = categories.firstOrNull()?.id ?: 1L
                val now = System.currentTimeMillis()

                var importedCount = 0
                var skippedCount = 0

                externalEvents.forEach { evt ->
                    if (isDuplicateTask(existingTasks, evt.title, evt.startTime)) {
                        skippedCount++
                    } else {
                        val combinedDesc = buildString {
                            if (!evt.location.isNullOrBlank()) {
                                append("地点: ").append(evt.location.trim())
                            }
                            if (!evt.description.isNullOrBlank()) {
                                if (isNotEmpty()) append("\n")
                                append(evt.description.trim())
                            }
                        }.takeIf { it.isNotBlank() }

                        val isExpired = evt.deadline < now
                        val newTask = Task(
                            title = evt.title,
                            description = combinedDesc,
                            startTime = evt.startTime,
                            deadline = evt.deadline,
                            isExpired = isExpired,
                            expiredAt = if (isExpired) evt.deadline else null,
                            categoryId = defaultCatId,
                            originalVoiceText = "系统日历导入: ${evt.title}"
                        )
                        val reminders = if (includeReminders) {
                            evt.reminderMinutes
                                .map { mins -> evt.startTime - mins * 60_000L }
                                .filter { it > now }
                                .distinct()
                                .map { trigger ->
                                    Reminder(
                                        taskId = 0L,
                                        triggerAt = trigger,
                                        label = evt.title
                                    )
                                }
                        } else emptyList()

                        taskRepository.insertTaskWithRemindersAndReturnId(
                            task = newTask,
                            reminders = reminders,
                            syncToCalendar = false
                        )
                        existingTasks.add(newTask)
                        importedCount++
                    }
                }
                importedCount to skippedCount
            }

            calendarSyncStore.markPullCompleted()
            _uiState.value = if (imported > 0) {
                BackupUiState.Success("已从系统日历导入 $imported 条未来日程（跳过 $skipped 条重复日程）")
            } else {
                BackupUiState.Success("所有未来系统日程均已在任务列表中（共跳过 $skipped 条）")
            }
        } catch (e: Exception) {
            _uiState.value = BackupUiState.Error("系统日历导入失败: ${e.message}")
        }
    }

    private fun isDuplicateTask(existingTasks: List<Task>, title: String, startTime: Long): Boolean {
        val cleanTitle = title.trim()
        return existingTasks.any { task ->
            task.title.trim().equals(cleanTitle, ignoreCase = true) &&
                abs(task.startTime - startTime) <= 60_000L
        }
    }
    
    fun resetState() {
        _uiState.value = BackupUiState.Idle
    }
}

sealed class BackupUiState {
    data object Idle : BackupUiState()
    data object Loading : BackupUiState()
    data object Importing : BackupUiState()
    data class Success(val message: String) : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}
