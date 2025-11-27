package com.litetask.app.data.repository

import com.litetask.app.data.local.TaskDao
import com.litetask.app.data.model.Task
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface TaskRepository {
    fun getAllTasks(): Flow<List<Task>>
    fun getTasksInRange(start: Long, end: Long): Flow<List<Task>>
    suspend fun getTaskById(id: Long): Task?
    suspend fun insertTask(task: Task)
    suspend fun insertTasks(tasks: List<Task>)
    suspend fun updateTask(task: Task)
    suspend fun deleteTask(task: Task)
}

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao
) : TaskRepository {
    override fun getAllTasks(): Flow<List<Task>> = taskDao.getAllTasks()

    override fun getTasksInRange(start: Long, end: Long): Flow<List<Task>> =
        taskDao.getTasksInRange(start, end)

    override suspend fun getTaskById(id: Long): Task? = taskDao.getTaskById(id)

    override suspend fun insertTask(task: Task) = taskDao.insertTask(task)

    override suspend fun insertTasks(tasks: List<Task>) = taskDao.insertTasks(tasks)

    override suspend fun updateTask(task: Task) = taskDao.updateTask(task)

    override suspend fun deleteTask(task: Task) = taskDao.deleteTask(task)
}
