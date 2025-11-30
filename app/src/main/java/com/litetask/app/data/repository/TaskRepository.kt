package com.litetask.app.data.repository

import com.litetask.app.data.local.TaskDao
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.SubTask
import com.litetask.app.data.model.TaskDetailComposite
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao
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
    suspend fun autoMarkOverdueTasksAsDone(time: Long) = taskDao.autoMarkOverdueTasksAsDone(time)
    
    // 兼容
    fun getAllTasks() = taskDao.getAllTasks()
    fun getTasksInRange(start: Long, end: Long) = taskDao.getTasksInRange(start, end)
}