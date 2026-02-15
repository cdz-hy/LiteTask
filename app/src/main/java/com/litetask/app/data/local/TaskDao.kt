package com.litetask.app.data.local

import androidx.room.*
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.SubTask
import com.litetask.app.data.model.Reminder
import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.data.model.TaskProgress
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TaskDao {

    // 1. 置顶任务 (无论是否完成，只要置顶就在最上面，但排除已过期)
    // 排序：未完成在前，然后按截止时间
    @Transaction
    @Query("""
        SELECT * FROM tasks 
        WHERE is_pinned = 1 AND is_expired = 0
        ORDER BY is_done ASC, deadline ASC
    """)
    abstract fun getPinnedTasks(): Flow<List<TaskDetailComposite>>

    // 2. 普通进行中任务 (不含置顶，排除已过期)
    // 排序：开始时间早的在前 -> 截止时间早的在前
    @Transaction
    @Query("""
        SELECT * FROM tasks 
        WHERE is_pinned = 0 AND is_done = 0 AND is_expired = 0
        ORDER BY start_time ASC, deadline ASC
    """)
    abstract fun getActiveNonPinnedTasks(): Flow<List<TaskDetailComposite>>

    // 3. 所有未完成任务（包括置顶和非置顶，但排除已过期）
    // 按照要求：is_done = 0 AND is_expired = 0，置顶优先，然后按开始时间、截止时间排序
    @Transaction
    @Query("""
        SELECT * FROM tasks 
        WHERE is_done = 0 AND is_expired = 0
        ORDER BY is_pinned DESC, start_time ASC, deadline ASC
    """)
    abstract fun getActiveTasks(): Flow<List<TaskDetailComposite>>
    
    // 获取所有需要在首页显示的任务（未完成 + 已过期 + 前20条已完成）
    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query("""
        SELECT * FROM (
            -- 1. 未完成任务（不限制数量，置顶优先，然后按开始时间、截止时间排序）
            SELECT *, 1 as priority FROM tasks 
            WHERE is_done = 0 AND is_expired = 0
            
            UNION ALL
            
            -- 2. 已过期任务（不限制数量，置顶优先，然后按截止时间倒序排序）
            SELECT *, 2 as priority FROM tasks 
            WHERE is_done = 0 AND is_expired = 1
            
            UNION ALL
            
            -- 3. 前 20 条已完成任务（仅限制此部分的初始显示数量）
            SELECT * FROM (
                SELECT *, 3 as priority FROM tasks 
                WHERE is_done = 1
                ORDER BY deadline DESC
                LIMIT 20
            )
        )
        ORDER BY 
            priority ASC,
            -- 未完成任务排序：置顶优先 -> 开始时间 -> 截止时间
            CASE WHEN priority = 1 THEN is_pinned END DESC,
            CASE WHEN priority = 1 THEN start_time END ASC,
            CASE WHEN priority = 1 THEN deadline END ASC,
            -- 已过期任务排序：置顶优先 -> 截止时间倒序
            CASE WHEN priority = 2 THEN is_pinned END DESC,
            CASE WHEN priority = 2 THEN deadline END DESC,
            -- 已完成任务排序：截止时间倒序
            CASE WHEN priority = 3 THEN deadline END DESC
    """)
    abstract fun getAllDisplayTasks(): Flow<List<TaskDetailComposite>>

    // 4. 历史任务 (已完成) - 分页加载
    // 排序：最近截止/完成的在前 (DESC)
    @Transaction
    @Query("""
        SELECT * FROM tasks 
        WHERE is_done = 1
        ORDER BY deadline DESC
        LIMIT :limit OFFSET :offset
    """)
    abstract suspend fun getHistoryTasks(limit: Int, offset: Int): List<TaskDetailComposite>

    // --- 详情与操作 ---
    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    abstract fun getTaskDetail(taskId: Long): Flow<TaskDetailComposite>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    abstract suspend fun getTaskDetailCompositeSync(taskId: Long): TaskDetailComposite?

    @Query("SELECT * FROM tasks WHERE id = :id")
    abstract suspend fun getTaskById(id: Long): Task?

    // --- 基础增删改 ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTask(task: Task): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSubTask(subTask: SubTask) // 单个插入

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSubTasks(subTasks: List<SubTask>)

    @Update
    abstract suspend fun updateTask(task: Task)

    @Delete
    abstract suspend fun deleteTask(task: Task)
    
    @Query("UPDATE sub_tasks SET is_completed = :completed WHERE id = :subTaskId")
    abstract suspend fun updateSubTaskStatus(subTaskId: Long, completed: Boolean)
    
    @Delete
    abstract suspend fun deleteSubTask(subTask: SubTask)

    @Query("DELETE FROM sub_tasks WHERE task_id = :taskId")
    abstract suspend fun deleteSubTasksByTaskId(taskId: Long)

    @Transaction
    open suspend fun updateTaskWithSubTasks(task: Task, subTasks: List<SubTask>) {
        updateTask(task)
        deleteSubTasksByTaskId(task.id)
        insertSubTasks(subTasks.map { it.copy(taskId = task.id, id = 0) })
    }
    
    // --- 搜索功能 ---
    @Transaction
    @Query("""
        SELECT DISTINCT t.* FROM tasks t
        LEFT JOIN sub_tasks st ON t.id = st.task_id
        WHERE (
            :query = '' OR
            t.title LIKE '%' || :query || '%' OR
            t.description LIKE '%' || :query || '%' OR
            st.content LIKE '%' || :query || '%'
        )
        AND (:typesEmpty = 1 OR t.type IN (:types))
        AND (:categoriesEmpty = 1 OR t.category_id IN (:categoryIds))
        AND (:startDate IS NULL OR t.start_time >= :startDate)
        AND (:endDate IS NULL OR t.deadline <= :endDate)
        ORDER BY t.is_done ASC, t.deadline ASC
    """)
    abstract fun searchTasksInternal(
        query: String,
        types: List<String>,
        typesEmpty: Boolean,
        categoryIds: List<Long>,
        categoriesEmpty: Boolean,
        startDate: Long?,
        endDate: Long?
    ): Flow<List<TaskDetailComposite>>

    fun searchTasks(
        query: String,
        types: List<String>,
        categoryIds: List<Long>,
        startDate: Long?,
        endDate: Long?
    ): Flow<List<TaskDetailComposite>> {
        return searchTasksInternal(
            query,
            types,
            types.isEmpty(),
            categoryIds,
            categoryIds.isEmpty(),
            startDate,
            endDate
        )
    }
    
    // --- 兼容性保留 ---
    
    /**
     * 自动标记过期任务（不再自动完成，而是标记为过期状态）
     * @param currentTime 当前时间戳
     * @return 被标记为过期的任务数量
     */
    @Query("""
        UPDATE tasks SET 
            is_expired = 1,
            expired_at = :currentTime
        WHERE deadline < :currentTime 
        AND is_done = 0 
        AND is_expired = 0
    """)
    abstract suspend fun autoMarkTasksAsExpired(currentTime: Long): Int

    /**
     * 自动恢复被错误标记为过期的任务（例如截止时间被修改到了未来）
     * @param currentTime 当前时间戳
     * @return 被恢复的任务数量
     */
    @Query("""
        UPDATE tasks SET 
            is_expired = 0,
            expired_at = NULL
        WHERE deadline >= :currentTime 
        AND is_done = 0 
        AND is_expired = 1
    """)
    abstract suspend fun autoUnmarkExpiredTasks(currentTime: Long): Int

    /**
     * 同步任务过期状态（双向同步）
     */
    @Transaction
    open suspend fun autoSyncTaskExpiredStatus(currentTime: Long) {
        autoMarkTasksAsExpired(currentTime)
        autoUnmarkExpiredTasks(currentTime)
    }
    
    /**
     * 获取已过期但未完成的任务
     * @param limit 限制数量
     * @param offset 偏移量
     * @return 过期任务列表
     */
    @Transaction
    @Query("""
        SELECT * FROM tasks 
        WHERE is_expired = 1 AND is_done = 0
        ORDER BY deadline DESC
        LIMIT :limit OFFSET :offset
    """)
    abstract suspend fun getExpiredTasks(limit: Int, offset: Int): List<TaskDetailComposite>
    
    /**
     * 重新激活过期任务（清除过期状态，设置新的截止时间）
     * @param taskId 任务ID
     * @param newDeadline 新的截止时间
     */
    @Query("""
        UPDATE tasks SET 
            is_expired = 0,
            expired_at = NULL,
            deadline = :newDeadline
        WHERE id = :taskId
    """)
    abstract suspend fun reactivateExpiredTask(taskId: Long, newDeadline: Long)
    
    /**
     * 标记任务完成（同时记录完成时间）
     * @param taskId 任务ID
     * @param completedAt 完成时间
     */
    @Query("""
        UPDATE tasks SET 
            is_done = 1,
            completed_at = :completedAt,
            is_pinned = 0
        WHERE id = :taskId
    """)
    abstract suspend fun markTaskCompleted(taskId: Long, completedAt: Long)
    
    /**
     * 标记任务未完成（清除完成时间）
     * @param taskId 任务ID
     */
    @Query("""
        UPDATE tasks SET 
            is_done = 0,
            completed_at = NULL
        WHERE id = :taskId
    """)
    abstract suspend fun markTaskUncompleted(taskId: Long)
    
    /** 标记所有过期提醒为已触发 */
    @Query("UPDATE reminders SET is_fired = 1 WHERE trigger_at < :currentTime AND is_fired = 0")
    abstract suspend fun autoMarkOverdueRemindersAsFired(currentTime: Long): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTasks(tasks: List<Task>)
    
    @Query("SELECT * FROM tasks ORDER BY start_time ASC")
    abstract fun getAllTasks(): Flow<List<Task>>
    
    @Query("SELECT * FROM tasks WHERE start_time >= :start AND deadline <= :end ORDER BY deadline ASC")
    abstract fun getTasksInRange(start: Long, end: Long): Flow<List<Task>>
    
    // --- 提醒相关操作 ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertReminder(reminder: Reminder): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertReminders(reminders: List<Reminder>)
    
    @Query("SELECT * FROM reminders WHERE task_id = :taskId ORDER BY trigger_at ASC")
    abstract fun getRemindersByTaskId(taskId: Long): Flow<List<Reminder>>
    
    @Query("SELECT * FROM reminders WHERE task_id = :taskId ORDER BY trigger_at ASC")
    abstract suspend fun getRemindersByTaskIdSync(taskId: Long): List<Reminder>
    
    @Query("DELETE FROM reminders WHERE task_id = :taskId")
    abstract suspend fun deleteRemindersByTaskId(taskId: Long)
    
    @Delete
    abstract suspend fun deleteReminder(reminder: Reminder)
    
    @Query("UPDATE reminders SET is_fired = :fired WHERE id = :reminderId")
    abstract suspend fun updateReminderFired(reminderId: Long, fired: Boolean)
    
    @Query("SELECT * FROM reminders WHERE is_fired = 0 AND trigger_at <= :currentTime")
    abstract suspend fun getPendingReminders(currentTime: Long): List<Reminder>
    
    // 获取所有未触发且触发时间在未来的提醒（用于开机恢复）
    @Query("SELECT * FROM reminders WHERE is_fired = 0 AND trigger_at > :currentTime")
    abstract suspend fun getFutureReminders(currentTime: Long): List<Reminder>
    
    // 获取所有未触发但已过期的提醒（用于清理）
    @Query("SELECT * FROM reminders WHERE is_fired = 0 AND trigger_at < :currentTime")
    abstract suspend fun getExpiredUnfiredReminders(currentTime: Long): List<Reminder>
    
    // 同步获取任务（用于广播接收器中的校验）
    @Query("SELECT * FROM tasks WHERE id = :id")
    abstract suspend fun getTaskByIdSync(id: Long): Task?
    
    // 同步获取提醒
    @Query("SELECT * FROM reminders WHERE id = :id")
    abstract suspend fun getReminderByIdSync(id: Long): Reminder?
    
    // ========== Widget 专用查询方法 ==========
    
    /**
     * 同步获取所有未完成且未过期任务（用于任务列表小组件）
     * 排序：置顶优先 -> 开始时间 -> 截止时间
     */
    @Query("""
        SELECT * FROM tasks 
        WHERE is_done = 0 AND is_expired = 0
        ORDER BY is_pinned DESC, start_time ASC, deadline ASC
    """)
    abstract suspend fun getActiveTasksSync(): List<Task>
    
    /**
     * 同步获取今日任务（用于甘特图小组件，排除已过期）
     * 包括：今天开始的任务 或 今天截止的任务 或 跨越今天的任务
     */
    @Query("""
        SELECT * FROM tasks 
        WHERE is_expired = 0 
        AND (
            (start_time >= :startOfDay AND start_time < :endOfDay)
            OR (deadline >= :startOfDay AND deadline < :endOfDay)
            OR (start_time < :startOfDay AND deadline >= :endOfDay)
        )
        ORDER BY start_time ASC, deadline ASC
    """)
    abstract suspend fun getTodayTasksSyncWithRange(startOfDay: Long, endOfDay: Long): List<Task>
    
    /**
     * 获取今日任务完成进度（排除已过期）
     * @return Pair<已完成数, 总数>
     */
    @Query("""
        SELECT 
            SUM(CASE WHEN is_done = 1 THEN 1 ELSE 0 END) as done,
            COUNT(*) as total
        FROM tasks 
        WHERE is_expired = 0 AND (
            (start_time >= :startOfDay AND start_time < :endOfDay)
            OR (deadline >= :startOfDay AND deadline < :endOfDay)
            OR (start_time < :startOfDay AND deadline >= :endOfDay)
        )
    """)
    abstract suspend fun getTodayTasksProgressWithRange(startOfDay: Long, endOfDay: Long): TaskProgress?
    
    /**
     * 同步获取即将截止的任务（用于截止提醒小组件，排除已过期）
     * 按截止时间排序，最紧急的在前
     */
    @Transaction
    @Query("""
        SELECT * FROM tasks 
        WHERE is_done = 0 AND is_expired = 0 AND deadline > :currentTime
        ORDER BY deadline ASC
        LIMIT :limit
    """)
    abstract suspend fun getUpcomingDeadlineCompositesSyncWithTime(currentTime: Long, limit: Int): List<TaskDetailComposite>

    @Query("""
        SELECT * FROM tasks 
        WHERE is_done = 0 AND is_expired = 0 AND deadline > :currentTime
        ORDER BY deadline ASC
        LIMIT :limit
    """)
    abstract suspend fun getUpcomingDeadlinesSyncWithTime(currentTime: Long, limit: Int): List<Task>
    
    /**
     * 获取最紧急的未完成且未过期任务
     */
    @Query("""
        SELECT * FROM tasks 
        WHERE is_done = 0 AND is_expired = 0 AND deadline > :currentTime
        ORDER BY deadline ASC
        LIMIT 1
    """)
    abstract suspend fun getMostUrgentTaskSyncWithTime(currentTime: Long): Task?
    
    /**
     * 获取所有进行中的任务（包括未完成且未过期，以及已过期但未完成的任务）
     * 用于任务列表小组件
     * 排序要求：置顶未完成 -> 未完成 -> 置顶已过期 -> 已过期
     */
    @Query("""
        SELECT * FROM tasks 
        WHERE is_done = 0
        ORDER BY is_expired ASC, is_pinned DESC, start_time ASC, deadline ASC
        LIMIT 40
    """)
    abstract suspend fun getActiveTasksWithExpiredSync(): List<Task>
    
    /**
     * 获取所有进行中的任务详情（包括未完成且未过期，以及已过期但未完成的任务）
     * 用于任务列表小组件，支持分类颜色显示
     * 排序要求：置顶未完成 -> 未完成 -> 置顶已过期 -> 已过期
     */
    @Transaction
    @Query("""
        SELECT * FROM tasks 
        WHERE is_done = 0
        ORDER BY is_expired ASC, is_pinned DESC, start_time ASC, deadline ASC
        LIMIT 40
    """)
    abstract suspend fun getActiveTaskDetailCompositesWithExpiredSync(): List<TaskDetailComposite>
    
    /**
     * 同步获取今日相关的所有任务（包括已完成的，用于甘特图小组件，排除已过期）
     * 包括：今天开始的任务 或 今天截止的任务 或 跨越今天的任务
     */
    @Transaction
    @Query("""
        SELECT * FROM tasks 
        WHERE (
            (start_time >= :startOfDay AND start_time < :endOfDay)
            OR (deadline >= :startOfDay AND deadline < :endOfDay)
            OR (start_time < :startOfDay AND deadline >= :endOfDay)
        )
        ORDER BY is_done ASC, start_time ASC, deadline ASC
    """)
    abstract suspend fun getTodayAllTaskCompositesSync(startOfDay: Long, endOfDay: Long): List<TaskDetailComposite>

    @Query("""
        SELECT * FROM tasks 
        WHERE (
            (start_time >= :startOfDay AND start_time < :endOfDay)
            OR (deadline >= :startOfDay AND deadline < :endOfDay)
            OR (start_time < :startOfDay AND deadline >= :endOfDay)
        )
        ORDER BY is_done ASC, start_time ASC, deadline ASC
    """)
    abstract suspend fun getTodayAllTasksSync(startOfDay: Long, endOfDay: Long): List<Task>
    
    // 获取所有任务及其详情（用于备份）
    @Transaction
    @Query("SELECT * FROM tasks")
    abstract suspend fun getAllTaskDetailsSync(): List<TaskDetailComposite>
}