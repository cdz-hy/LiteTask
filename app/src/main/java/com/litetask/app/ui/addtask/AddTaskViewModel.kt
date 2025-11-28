package com.litetask.app.ui.addtask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskType
import com.litetask.app.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddTaskViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {

    fun saveTask(
        title: String,
        description: String,
        startTime: Long,
        endTime: Long,
        type: TaskType,
        location: String
    ) {
        viewModelScope.launch {
            val task = Task(
                title = title,
                description = description.ifBlank { null },
                startTime = startTime,
                deadline = endTime, // 将 endTime 替换为 deadline
                type = type
                // 移除 location 字段，因为新的 Task 模型中不再包含该字段
            )
            taskRepository.insertTask(task)
        }
    }
}
