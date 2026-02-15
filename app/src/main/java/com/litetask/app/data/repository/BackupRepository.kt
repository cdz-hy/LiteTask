package com.litetask.app.data.repository

import com.google.gson.Gson
import com.litetask.app.data.local.CategoryDao
import com.litetask.app.data.local.TaskDao
import com.litetask.app.data.local.TaskComponentDao
import com.litetask.app.data.model.BackupData
import com.litetask.app.data.model.Category
import com.litetask.app.data.model.Reminder
import com.litetask.app.data.model.SubTask
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskComponentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val categoryDao: CategoryDao,
    private val componentDao: TaskComponentDao
) {
    private val gson = Gson()

    suspend fun createBackupJson(): String = withContext(Dispatchers.IO) {
        val allComposites = taskDao.getAllTaskDetailsSync()
        val allCategories = categoryDao.getAllCategoriesSync()

        val tasks = mutableListOf<Task>()
        val subTasks = mutableListOf<SubTask>()
        val components = mutableListOf<TaskComponentEntity>()
        val reminders = mutableListOf<Reminder>()

        allComposites.forEach { composite ->
            tasks.add(composite.task)
            subTasks.addAll(composite.subTasks)
            components.addAll(composite.components)
            reminders.addAll(composite.reminders)
        }

        val backupData = BackupData(
            tasks = tasks,
            subTasks = subTasks,
            taskComponents = components,
            reminders = reminders,
            categories = allCategories
        )
        
        gson.toJson(backupData)
    }

    suspend fun restoreBackup(json: String): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val backupData = gson.fromJson(json, BackupData::class.java)
            
            // 1. Restore Categories
            val categoryIdMap = mutableMapOf<Long, Long>() // Old ID -> New ID
            val existingCategories = categoryDao.getAllCategoriesSync()
            
            backupData.categories.forEach { importedCategory ->
                val existing = existingCategories.find { it.name == importedCategory.name }
                if (existing != null) {
                    categoryIdMap[importedCategory.id] = existing.id
                } else {
                    val newId = categoryDao.insertCategory(importedCategory.copy(id = 0))
                    categoryIdMap[importedCategory.id] = newId
                }
            }

            // 2. Restore Tasks
            var successCount = 0
            var skippedCount = 0
            val allExistingTasks = try {
                taskDao.getAllTaskDetailsSync().map { it.task }
            } catch (e: Exception) {
                emptyList()
            }
            
            // 为了查重，建立指纹集
            // 指纹: Title + StartTime + Type
            val existingFingerprints = allExistingTasks.map { 
                Triple(it.title, it.startTime, it.type) 
            }.toSet()

            backupData.tasks.forEach { importedTask ->
                val fingerprint = Triple(importedTask.title, importedTask.startTime, importedTask.type)
                
                if (fingerprint in existingFingerprints) {
                    skippedCount++
                    return@forEach // Skip duplicate
                }

                // Prepare new task
                val newCategoryId = categoryIdMap[importedTask.categoryId] ?: 1L // Default to 1 if map fails
                val newTask = importedTask.copy(
                    id = 0,
                    categoryId = newCategoryId
                )
                
                val newTaskId = taskDao.insertTask(newTask)
                successCount++

                // Restore associated data
                
                // SubTasks
                val relatedSubTasks = backupData.subTasks.filter { it.taskId == importedTask.id }
                relatedSubTasks.forEach { subTask ->
                    taskDao.insertSubTask(subTask.copy(id = 0, taskId = newTaskId))
                }

                // Components
                val relatedComponents = backupData.taskComponents.filter { it.taskId == importedTask.id }
                relatedComponents.forEach { comp ->
                    componentDao.insertComponent(comp.copy(taskId = newTaskId))
                }

                // Reminders
                val relatedReminders = backupData.reminders.filter { it.taskId == importedTask.id }
                relatedReminders.forEach { reminder ->
                    taskDao.insertReminder(reminder.copy(id = 0, taskId = newTaskId))
                }
            }
            
            RestoreResult.Success(successCount, skippedCount)
        } catch (e: Exception) {
            RestoreResult.Error(e)
        }
    }
}

sealed class RestoreResult {
    data class Success(val importedCount: Int, val skippedCount: Int) : RestoreResult()
    data class Error(val exception: Throwable) : RestoreResult()
}
