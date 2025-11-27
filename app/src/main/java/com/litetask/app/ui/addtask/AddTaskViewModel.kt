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
                endTime = endTime,
                type = type,
                location = location.ifBlank { null }
            )
            taskRepository.insertTask(task)
        }
    }
}
