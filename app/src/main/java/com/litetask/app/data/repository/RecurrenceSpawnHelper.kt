package com.litetask.app.data.repository

import com.litetask.app.data.local.CategoryDao
import com.litetask.app.data.local.TaskComponentDao
import com.litetask.app.data.local.TaskDao
import com.litetask.app.data.model.RecurrenceEngine
import com.litetask.app.data.model.SubTask
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskComponent
import com.litetask.app.data.model.toEntity
import com.litetask.app.data.model.toTaskComponentsSafe
import com.litetask.app.reminder.ReminderScheduler

/**
 * 周期重复任务的落库生成器与撤销清理工具。
 * 供 TaskRepositoryImpl 与 WidgetDataProvider 共享，避免重复逻辑与双份生成。
 */
object RecurrenceSpawnHelper {

    suspend fun spawnNextOccurrenceIfEligible(
        taskDao: TaskDao,
        taskComponentDao: TaskComponentDao,
        categoryDao: CategoryDao,
        reminderScheduler: ReminderScheduler,
        sourceTask: Task
    ): RecurrenceEngine.RecurrenceSpawnResult? {
        val componentEntities = taskComponentDao.getComponentsByTaskIdSync(sourceTask.id)
        if (componentEntities.isEmpty()) return null

        val matched = componentEntities.firstNotNullOfOrNull { entity ->
            val comp = entity.toTaskComponent()
            if (comp is TaskComponent.RecurrenceComponent) entity to comp else null
        } ?: return null

        val ruleEntity = matched.first
        val ruleComp = matched.second
        val rule = ruleComp.data

        // 幂等保护：已生成下一期且目标任务仍在库中时绝不重复创建
        val recordedNextId = rule.generatedNextTaskId
        if (recordedNextId != null && taskDao.getTaskById(recordedNextId) != null) {
            return null
        }

        val now = System.currentTimeMillis()
        val nextWindow = RecurrenceEngine.computeNextOccurrence(
            taskStartTimeMs = sourceTask.startTime,
            taskDeadlineMs = sourceTask.deadline,
            rule = rule,
            nowMs = now
        ) ?: return null

        val nextTaskDraft = Task(
            id = 0L,
            title = sourceTask.title,
            description = sourceTask.description,
            deadline = nextWindow.deadlineMillis,
            startTime = nextWindow.startTimeMillis,
            isDone = false,
            isPinned = false,
            type = @Suppress("DEPRECATION") sourceTask.type,
            categoryId = sourceTask.categoryId,
            isExpired = false,
            expiredAt = null,
            createdAt = now,
            completedAt = null,
            originalVoiceText = sourceTask.originalVoiceText,
            totalFocusSeconds = 0L
        )
        val nextTaskId = taskDao.insertTask(nextTaskDraft)
        val nextTask = nextTaskDraft.copy(id = nextTaskId)

        // 1. 等距平移提醒，并注册尚未过期的系统闹钟
        val sourceReminders = taskDao.getRemindersByTaskIdSync(sourceTask.id)
        val nextReminders = RecurrenceEngine.buildTranslatedReminders(
            sourceReminders = sourceReminders,
            oldStartMs = sourceTask.startTime,
            oldDeadlineMs = sourceTask.deadline,
            newStartMs = nextWindow.startTimeMillis,
            newDeadlineMs = nextWindow.deadlineMillis
        ).map { it.copy(taskId = nextTaskId) }

        if (nextReminders.isNotEmpty()) {
            taskDao.insertReminders(nextReminders)
            val persistedReminders = taskDao.getRemindersByTaskIdSync(nextTaskId)
            val category = categoryDao.getCategoryById(nextTask.categoryId)
            persistedReminders.forEach { reminder ->
                if (reminder.triggerAt > now) {
                    reminderScheduler.scheduleReminderWithTaskInfo(
                        reminder = reminder,
                        taskTitle = nextTask.title,
                        taskType = @Suppress("DEPRECATION") nextTask.type.name,
                        categoryName = category?.name,
                        categoryColor = category?.colorHex
                    )
                }
            }
        }

        // 2. 按白名单继承组件（含递进后的 RECURRENCE_RULE）
        val components = componentEntities.toTaskComponentsSafe()
        val nextRule = RecurrenceEngine.advanceRuleForNextOccurrence(rule)
        val inheritedComponents = RecurrenceEngine.buildInheritedComponents(components, nextRule)
        var nextRuleComponentId = 0L
        inheritedComponents.forEach { comp ->
            val insertedCompId = taskComponentDao.insertComponent(comp.toEntity(nextTaskId, now))
            if (comp is TaskComponent.RecurrenceComponent) {
                nextRuleComponentId = insertedCompId
            }
        }

        // 3. 可选继承子步骤并全部重置为未完成
        if (rule.inheritSubTasks) {
            val sourceSubTasks = taskDao.getSubTasksByTaskIdSync(sourceTask.id)
            if (sourceSubTasks.isNotEmpty()) {
                val clonedSubs = sourceSubTasks.mapIndexed { idx, sub ->
                    SubTask(
                        id = 0L,
                        taskId = nextTaskId,
                        content = sub.content,
                        isCompleted = false,
                        sortOrder = if (sub.sortOrder >= 0) sub.sortOrder else idx
                    )
                }
                taskDao.insertSubTasks(clonedSubs)
            }
        }

        // 4. 将新任务 ID 回写到源任务规则上，提供跨重启与快击复选框的幂等保护
        val updatedSourceRule = ruleComp.copy(data = rule.copy(generatedNextTaskId = nextTaskId))
        taskComponentDao.updateComponent(
            updatedSourceRule.toEntity(taskId = sourceTask.id, createdAt = ruleEntity.createdAt)
        )

        return RecurrenceEngine.RecurrenceSpawnResult(
            sourceTaskId = sourceTask.id,
            sourceRuleComponentId = ruleEntity.id,
            nextTask = nextTask,
            nextRuleComponentId = nextRuleComponentId
        )
    }

    suspend fun clearGeneratedNextTaskLink(
        taskComponentDao: TaskComponentDao,
        sourceTaskId: Long,
        expectedNextTaskId: Long? = null
    ) {
        val entities = taskComponentDao.getComponentsByTaskIdSync(sourceTaskId)
        entities.forEach { entity ->
            val comp = entity.toTaskComponent()
            if (comp is TaskComponent.RecurrenceComponent &&
                (expectedNextTaskId == null || comp.data.generatedNextTaskId == expectedNextTaskId)
            ) {
                val cleared = comp.copy(data = comp.data.copy(generatedNextTaskId = null))
                taskComponentDao.updateComponent(
                    cleared.toEntity(taskId = sourceTaskId, createdAt = entity.createdAt)
                )
            }
        }
    }
}
