package com.litetask.app.data.local

import androidx.room.*
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.SubTask
import com.litetask.app.data.model.Reminder
import com.litetask.app.data.model.TaskDetailComposite
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    // 1. 置顶任务 (无论是否完成，只要置顶就在最上面)
    // 排序：未完成在前，然后按截止时间
    @Transaction
    @Query("""
        SELECT * FROM tasks 
        WHERE is_pinned = 1 
        ORDER BY is_done ASC, deadline ASC
    """)
    fun getPinnedTasks(): Flow<List<TaskDetailComposite>>

    // 2. 普通进行中任务 (不含置顶)
    // 排序：开始时间早的在前 -> 截止时间早的在前
    @Transaction
    @Query("""
        SELECT * FROM tasks 
        WHERE is_pinned = 0 AND is_done = 0 
        ORDER BY start_time ASC, deadline ASC
    """)
    fun getActiveNonPinnedTasks(): Flow<List<TaskDetailComposite>>

    // 3. 所有未完成任务（包括置顶和非置顶）
    // 按照要求：is_done = 0，置顶优先，然后按开始时间、截止时间排序
    @Transaction
    @Query("""
        SELECT * FROM tasks 
        WHERE is_done = 0 
        ORDER BY is_pinned DESC, start_time ASC, deadline ASC
    """)
    fun getActiveTasks(): Flow<List<TaskDetailComposite>>

    // 4. 历史任务 (已完成) - 分页加载
    // 排序：最近截止/完成的在前 (DESC)
    @Transaction
    @Query("""
        SELECT * FROM tasks 
        WHERE is_done = 1
        ORDER BY deadline DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getHistoryTasks(limit: Int, offset: Int): List<TaskDetailComposite>

    // --- 详情与操作 ---
    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    fun getTaskDetail(taskId: Long): Flow<TaskDetailComposite>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): Task?

    // --- 基础增删改 ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubTask(subTask: SubTask) // 单个插入

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubTasks(subTasks: List<SubTask>)

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)
    
    @Query("UPDATE sub_tasks SET is_completed = :completed WHERE id = :subTaskId")
    suspend fun updateSubTaskStatus(subTaskId: Long, completed: Boolean)
    
    @Delete
    suspend fun deleteSubTask(subTask: SubTask)

    @Query("DELETE FROM sub_tasks WHERE task_id = :taskId")
    suspend fun deleteSubTasksByTaskId(taskId: Long)
    
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
        AND (:startDate IS NULL OR t.start_time >= :startDate)
        AND (:endDate IS NULL OR t.deadline <= :endDate)
        ORDER BY t.is_done ASC, t.deadline ASC
    """)
    fun searchTasks(
        query: String,
        types: List<String>,
        typesEmpty: Boolean,
        startDate: Long?,
        endDate: Long?
    ): Flow<List<TaskDetailComposite>>
    
    // --- 兼容性保留 ---
    @Query("UPDATE tasks SET is_done = 1 WHERE deadline < :currentTime AND is_done = 0")
    suspend fun autoMarkOverdueTasksAsDone(currentTime: Long): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<Task>)
    
    @Query("SELECT * FROM tasks ORDER BY start_time ASC")
    fun getAllTasks(): Flow<List<Task>>
    
    @Query("SELECT * FROM tasks WHERE start_time >= :start AND deadline <= :end ORDER BY deadline ASC")
    fun getTasksInRange(start: Long, end: Long): Flow<List<Task>>
    
    // --- 提醒相关操作 ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<Reminder>)
    
    @Query("SELECT * FROM reminders WHERE task_id = :taskId ORDER BY trigger_at ASC")
    fun getRemindersByTaskId(taskId: Long): Flow<List<Reminder>>
    
    @Query("SELECT * FROM reminders WHERE task_id = :taskId ORDER BY trigger_at ASC")
    suspend fun getRemindersByTaskIdSync(taskId: Long): List<Reminder>
    
    @Query("DELETE FROM reminders WHERE task_id = :taskId")
    suspend fun deleteRemindersByTaskId(taskId: Long)
    
    @Delete
    suspend fun deleteReminder(reminder: Reminder)
    
    @Query("UPDATE reminders SET is_fired = :fired WHERE id = :reminderId")
    suspend fun updateReminderFired(reminderId: Long, fired: Boolean)
    
    @Query("SELECT * FROM reminders WHERE is_fired = 0 AND trigger_at <= :currentTime")
    suspend fun getPendingReminders(currentTime: Long): List<Reminder>
    
    // 获取所有未触发且触发时间在未来的提醒（用于开机恢复）
    @Query("SELECT * FROM reminders WHERE is_fired = 0 AND trigger_at > :currentTime")
    suspend fun getFutureReminders(currentTime: Long): List<Reminder>
    
    // 同步获取任务（用于广播接收器中的校验）
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskByIdSync(id: Long): Task?
    
    // 同步获取提醒
    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderByIdSync(id: Long): Reminder?
}