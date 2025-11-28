package com.litetask.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import androidx.room.Delete
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.SubTask
import com.litetask.app.data.model.Reminder
import com.litetask.app.data.model.TaskDetailComposite
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    // --- 1. 首页核心视图 ---
    // 逻辑：
    // - 只查没做完的 (is_done = 0)
    // - 优先显示置顶的 (is_pinned DESC)
    // - 剩下的按 DDL 由近到远排序 (deadline ASC)，越紧迫的越靠前
    @Query("""
        SELECT * FROM tasks 
        WHERE is_done = 0 
        ORDER BY is_pinned DESC, deadline ASC
    """)
    fun getActiveTasksSortedByDeadline(): Flow<List<Task>>

    // --- 2. 截止日/倒计时视图 ---
    // 逻辑：
    // - 找出所有截止日期在 "未来24小时内" 的极度紧急任务
    @Query("""
        SELECT * FROM tasks 
        WHERE is_done = 0 
        AND deadline <= :limitTime
        ORDER BY deadline ASC
    """)
    fun getUrgentTasks(limitTime: Long): Flow<List<Task>>

    // --- 3. 详情页 (关联查询) ---
    // 一次性查出：任务详情 + 它的所有子任务 + 它的所有提醒
    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    fun getTaskDetail(taskId: Long): Flow<TaskDetailComposite>

    // --- 4. 基础操作 ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long // 返回新 ID

    @Insert
    suspend fun insertSubTasks(subTasks: List<SubTask>)

    @Insert
    suspend fun insertReminders(reminders: List<Reminder>)

    @Update
    suspend fun updateTask(task: Task)
    
    // 勾选/取消勾选子任务
    @Query("UPDATE sub_tasks SET is_completed = :completed WHERE id = :subTaskId")
    suspend fun updateSubTaskStatus(subTaskId: Long, completed: Boolean)
    
    // 为向后兼容保留的基础查询方法
    @Query("SELECT * FROM tasks ORDER BY start_time ASC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE start_time >= :start AND deadline <= :end ORDER BY deadline ASC")
    fun getTasksInRange(start: Long, end: Long): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<Task>)

    @Delete
    suspend fun deleteTask(task: Task)
}