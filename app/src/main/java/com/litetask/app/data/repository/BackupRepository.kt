package com.litetask.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.litetask.app.data.local.AIHistoryDao
import com.litetask.app.data.local.CategoryDao
import com.litetask.app.data.local.DailyScheduleAdviceDao
import com.litetask.app.data.local.FocusSessionDao
import com.litetask.app.data.local.TaskDao
import com.litetask.app.data.local.TaskComponentDao
import com.litetask.app.data.local.UserLocationDao
import com.litetask.app.data.local.UserProfileDao
import com.litetask.app.data.model.AIHistory
import com.litetask.app.data.model.AIHistorySource
import com.litetask.app.data.model.BackupData
import com.litetask.app.data.model.Category
import com.litetask.app.data.model.ComponentType
import com.litetask.app.data.model.DailyScheduleAdviceEntity
import com.litetask.app.data.model.FileAttachmentData
import com.litetask.app.data.model.FocusSessionEntity
import com.litetask.app.data.model.Reminder
import com.litetask.app.data.model.SubTask
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskComponentEntity
import com.litetask.app.data.model.UserLocationEntity
import com.litetask.app.data.model.UserProfileHistoryEntity
import com.litetask.app.data.model.VoiceNoteData
import com.litetask.app.util.AttachmentStorageHelper
import com.litetask.app.util.BackupArchiveHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: TaskDao,
    private val categoryDao: CategoryDao,
    private val componentDao: TaskComponentDao,
    private val focusSessionDao: FocusSessionDao,
    private val userLocationDao: UserLocationDao,
    private val userProfileDao: UserProfileDao,
    private val aiHistoryDao: AIHistoryDao,
    private val dailyScheduleAdviceDao: DailyScheduleAdviceDao
) {
    private val gson = Gson()

    private suspend fun collectBackupData(): BackupData {
        val allComposites = taskDao.getAllTaskDetailsSync()
        val allCategories = categoryDao.getAllCategoriesSync()
        val allFocusSessions = focusSessionDao.getAllSessionsSync()
        val allLocations = userLocationDao.getAllLocationsSync()
        val allProfileHistory = userProfileDao.getAllUserProfileHistorySync()
        val allAiHistory = aiHistoryDao.getAllHistory()
        val allDailyAdvice = dailyScheduleAdviceDao.getAllSync()

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

        return BackupData(
            tasks = tasks,
            subTasks = subTasks,
            taskComponents = components,
            reminders = reminders,
            categories = allCategories,
            focusSessions = allFocusSessions,
            userLocations = allLocations,
            userProfileHistory = allProfileHistory,
            aiHistory = allAiHistory,
            dailyScheduleAdvice = allDailyAdvice
        )
    }

    suspend fun createBackupJson(): String = withContext(Dispatchers.IO) {
        gson.toJson(collectBackupData())
    }

    suspend fun exportBackupArchive(outputStream: OutputStream) = withContext(Dispatchers.IO) {
        val backupData = collectBackupData()
        val json = gson.toJson(backupData)

        val attachmentFiles = backupData.taskComponents
            .filter { it.type == ComponentType.FILE_ATTACHMENT }
            .mapNotNull { entity ->
                runCatching { gson.fromJson(entity.dataJson, FileAttachmentData::class.java) }
                    .getOrNull()
                    ?.managedFilePath
                    ?.takeIf { it.isNotBlank() }
                    ?.let { File(it) }
                    ?.takeIf { it.exists() && it.isFile && it.canRead() }
            }
            .distinctBy { it.canonicalPath }

        val componentVoiceFiles = backupData.taskComponents
            .filter { it.type == ComponentType.VOICE_NOTE }
            .mapNotNull { entity ->
                runCatching { gson.fromJson(entity.dataJson, VoiceNoteData::class.java) }
                    .getOrNull()
                    ?.filePath
                    ?.takeIf { it.isNotBlank() }
                    ?.let { File(it) }
                    ?.takeIf { it.exists() && it.isFile && it.canRead() }
            }
        val dirVoiceFiles = AttachmentStorageHelper.voiceNotesDir(context)
            .listFiles()
            ?.filter { it.isFile && it.canRead() }
            .orEmpty()
        val voiceFiles = (componentVoiceFiles + dirVoiceFiles)
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }

        outputStream.use { out ->
            BackupArchiveHelper.writeZipArchive(
                outputStream = out,
                backupJson = json,
                attachmentFiles = attachmentFiles,
                voiceNoteFiles = voiceFiles
            )
        }
    }

    suspend fun restoreBackupAuto(inputStream: InputStream): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val archive = inputStream.use { stream ->
                BackupArchiveHelper.readArchiveAuto(
                    inputStream = stream,
                    targetAttachmentsDir = AttachmentStorageHelper.attachmentsDir(context),
                    targetVoiceNotesDir = AttachmentStorageHelper.voiceNotesDir(context)
                )
            }
            restoreBackupInternal(
                json = archive.json,
                extractedAttachments = archive.extractedAttachments,
                extractedVoiceNotes = archive.extractedVoiceNotes
            )
        } catch (e: Exception) {
            RestoreResult.Error(e)
        }
    }

    suspend fun restoreBackup(json: String): RestoreResult = withContext(Dispatchers.IO) {
        restoreBackupInternal(json, emptyMap(), emptyMap())
    }

    private suspend fun restoreBackupInternal(
        json: String,
        extractedAttachments: Map<String, File>,
        extractedVoiceNotes: Map<String, File> = emptyMap()
    ): RestoreResult {
        return try {
            val backupData = gson.fromJson(json, BackupData::class.java)
                ?: return RestoreResult.Error(IllegalArgumentException("备份文件内容无效"))
            
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

            val taskIdMap = mutableMapOf<Long, Long>()

            backupData.tasks.forEach { importedTask ->
                val fingerprint = Triple(importedTask.title, importedTask.startTime, importedTask.type)
                
                if (fingerprint in existingFingerprints) {
                    val matchedExisting = allExistingTasks.find {
                        it.title == importedTask.title &&
                            it.startTime == importedTask.startTime &&
                            it.type == importedTask.type
                    }
                    if (matchedExisting != null) {
                        taskIdMap[importedTask.id] = matchedExisting.id
                    }
                    skippedCount++
                    return@forEach // Skip duplicate
                }

                // Prepare new task
                val fallbackCategoryId = existingCategories.firstOrNull { it.isDefault }?.id
                    ?: existingCategories.firstOrNull()?.id
                    ?: 1L
                val newCategoryId = categoryIdMap[importedTask.categoryId] ?: fallbackCategoryId
                val newTask = importedTask.copy(
                    id = 0,
                    categoryId = newCategoryId
                )
                
                val newTaskId = taskDao.insertTask(newTask)
                taskIdMap[importedTask.id] = newTaskId
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
                    val remapped = BackupArchiveHelper.remapRestoredComponent(
                        entity = comp.copy(id = 0, taskId = newTaskId),
                        extractedAttachments = extractedAttachments,
                        fileUriResolver = { file -> AttachmentStorageHelper.fileProviderUri(context, file).toString() },
                        extractedVoiceNotes = extractedVoiceNotes
                    )
                    componentDao.insertComponent(remapped)
                }

                // Reminders
                val relatedReminders = backupData.reminders.filter { it.taskId == importedTask.id }
                relatedReminders.forEach { reminder ->
                    taskDao.insertReminder(reminder.copy(id = 0, taskId = newTaskId))
                }
            }

            // 3. Restore Focus Sessions (optional for backwards compatibility with v1 backups)
            val existingSessionFingerprints = focusSessionDao.getAllSessionsSync().map {
                Triple(it.startedAt, it.endedAt, it.actualSeconds)
            }.toSet()
            backupData.focusSessions.orEmpty().forEach { session ->
                val fp = Triple(session.startedAt, session.endedAt, session.actualSeconds)
                if (fp !in existingSessionFingerprints) {
                    focusSessionDao.insertSession(
                        session.copy(
                            id = 0,
                            taskId = session.taskId?.let { taskIdMap[it] }
                        )
                    )
                }
            }

            // 4. Restore User Locations (idempotent merge via 300m cluster or exact name fallback)
            val currentLocations = userLocationDao.getAllLocationsSync().toMutableList()
            backupData.userLocations.orEmpty().forEach { rawLoc ->
                // 兼容 v8 及更早版本导出的备份（Gson 对缺省字段可能填 null）
                val importedLoc = rawLoc.copy(
                    locationType = sanitizeImportedString(rawLoc.locationType, UserLocationEntity.TYPE_FREQUENT),
                    address = sanitizeImportedString(rawLoc.address, "")
                )
                val matched = LocationTracker.findMatchingLocation(
                    importedLoc.name,
                    importedLoc.longitude,
                    importedLoc.latitude,
                    currentLocations
                )
                if (matched != null) {
                    val hasMatchedCoords = matched.latitude != 0.0 || matched.longitude != 0.0
                    val merged = matched.copy(
                        latitude = if (hasMatchedCoords) matched.latitude else importedLoc.latitude,
                        longitude = if (hasMatchedCoords) matched.longitude else importedLoc.longitude,
                        locationType = if (
                            matched.locationType == UserLocationEntity.TYPE_FREQUENT &&
                            importedLoc.locationType != UserLocationEntity.TYPE_FREQUENT
                        ) importedLoc.locationType else matched.locationType,
                        address = matched.address.ifBlank { importedLoc.address },
                        originCount = maxOf(matched.originCount, importedLoc.originCount),
                        destinationCount = maxOf(matched.destinationCount, importedLoc.destinationCount),
                        lastVisitedAt = maxOf(matched.lastVisitedAt, importedLoc.lastVisitedAt)
                    )
                    userLocationDao.insertOrUpdate(merged)
                    val idx = currentLocations.indexOfFirst { it.id == matched.id }
                    if (idx >= 0) currentLocations[idx] = merged
                } else {
                    userLocationDao.insertOrUpdate(importedLoc.copy(id = 0))
                    currentLocations.clear()
                    currentLocations.addAll(userLocationDao.getAllLocationsSync())
                }
            }

            // 5. Restore User Profile History (ascending chronological order so latest ID = newest)
            val existingProfileFingerprints = userProfileDao.getAllUserProfileHistorySync().map {
                Triple(it.createdAt, it.identity, it.personality)
            }.toSet()
            backupData.userProfileHistory.orEmpty()
                .sortedBy { it.createdAt }
                .forEach { importedProfile ->
                    val fp = Triple(importedProfile.createdAt, importedProfile.identity, importedProfile.personality)
                    if (fp !in existingProfileFingerprints) {
                        val safeSource = sanitizeImportedString(importedProfile.source, UserProfileHistoryEntity.SOURCE_AI)
                        userProfileDao.insert(importedProfile.copy(id = 0, source = safeSource))
                    }
                }

            // 6. Restore AI History (idempotent merge by timestamp + source + target + content)
            val existingHistoryFingerprints = aiHistoryDao.getAllHistory()
                .map { aiHistoryFingerprint(it) }
                .toHashSet()
            backupData.aiHistory.orEmpty()
                .sortedBy { it.timestamp }
                .forEach { rawHistory ->
                    val sanitized = sanitizeImportedAiHistory(rawHistory)
                    val fp = aiHistoryFingerprint(sanitized)
                    if (existingHistoryFingerprints.add(fp)) {
                        aiHistoryDao.insertHistory(sanitized)
                    }
                }

            // 7. Restore Daily Schedule Advice (idempotent by creation timestamp)
            val existingAdviceTimestamps = dailyScheduleAdviceDao.getAllSync()
                .map { it.createdAt }
                .toHashSet()
            backupData.dailyScheduleAdvice.orEmpty()
                .sortedBy { it.createdAt }
                .forEach { rawAdvice ->
                    val sanitized = sanitizeImportedDailyAdvice(rawAdvice)
                    if (sanitized != null && existingAdviceTimestamps.add(sanitized.createdAt)) {
                        dailyScheduleAdviceDao.insert(sanitized)
                    }
                }
            
            RestoreResult.Success(successCount, skippedCount)
        } catch (e: Exception) {
            RestoreResult.Error(e)
        }
    }

    companion object {
        private fun sanitizeImportedString(value: Any?, defaultValue: String): String {
            return (value as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: defaultValue
        }

        private fun sanitizeImportedNullableString(value: Any?): String? {
            return (value as? String)?.trim()?.takeIf { it.isNotEmpty() }
        }

        /**
         * 规范化导入的 AI 历史记录，防止旧备份或手动编辑 JSON 中缺失字段导致异常
         */
        internal fun sanitizeImportedAiHistory(raw: AIHistory): AIHistory {
            val safeContent = (raw.content as? String)?.trim() ?: ""
            val safeSource = (raw.sourceType as? AIHistorySource) ?: AIHistorySource.TEXT
            return raw.copy(
                id = 0,
                content = safeContent,
                sourceType = safeSource,
                parsedCount = raw.parsedCount.coerceAtLeast(0),
                targetTitle = sanitizeImportedNullableString(raw.targetTitle),
                imageCount = raw.imageCount.coerceAtLeast(0),
                errorMessage = sanitizeImportedNullableString(raw.errorMessage)
            )
        }

        internal fun aiHistoryFingerprint(item: AIHistory): String {
            return "${item.timestamp}|${item.sourceType.name}|${item.targetTitle.orEmpty()}|${item.content}"
        }

        /**
         * 规范化导入的日程建议记录，跳过未完成生成的中间状态
         */
        internal fun sanitizeImportedDailyAdvice(raw: DailyScheduleAdviceEntity): DailyScheduleAdviceEntity? {
            val safeStatus = sanitizeImportedString(raw.generationStatus, "COMPLETED")
            if (safeStatus == "GENERATING") return null
            return raw.copy(
                id = 0,
                shortTermJson = sanitizeImportedString(raw.shortTermJson, "[]"),
                longTermJson = sanitizeImportedString(raw.longTermJson, "[]"),
                generationStatus = safeStatus
            )
        }
    }
}

sealed class RestoreResult {
    data class Success(val importedCount: Int, val skippedCount: Int) : RestoreResult()
    data class Error(val exception: Throwable) : RestoreResult()
}
