package com.litetask.app.data.local

import androidx.room.*
import com.litetask.app.data.model.TaskComponentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskComponentDao {
    @Query("SELECT * FROM task_components WHERE task_id = :taskId ORDER BY created_at ASC")
    fun getComponentsByTaskId(taskId: Long): Flow<List<TaskComponentEntity>>

    @Query("SELECT * FROM task_components WHERE task_id = :taskId ORDER BY created_at ASC")
    suspend fun getComponentsByTaskIdSync(taskId: Long): List<TaskComponentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComponent(component: TaskComponentEntity): Long

    @Update
    suspend fun updateComponent(component: TaskComponentEntity)

    @Delete
    suspend fun deleteComponent(component: TaskComponentEntity)
    
    @Query("DELETE FROM task_components WHERE task_id = :taskId")
    suspend fun deleteComponentsByTaskId(taskId: Long) // 级联删除辅助
}
