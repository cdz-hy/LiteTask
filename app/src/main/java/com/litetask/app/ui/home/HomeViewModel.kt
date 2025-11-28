package com.litetask.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litetask.app.data.model.Task
import com.litetask.app.data.repository.AIRepository
import com.litetask.app.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val aiRepository: AIRepository,
    private val speechHelper: com.litetask.app.util.SpeechRecognizerHelper
) : ViewModel() {

    // ... (Existing code)

    // 日期选择状态
    private val _selectedDate = MutableStateFlow(Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis)
    val selectedDate: StateFlow<Long> = _selectedDate

    // 任务列表 (根据日期过滤)
    val tasks: StateFlow<List<Task>> = combine(
        taskRepository.getAllTasks(),
        _selectedDate
    ) { allTasks, date ->
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.timeInMillis

        allTasks.filter { task ->
            task.startTime in startOfDay until endOfDay
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // UI 状态
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    fun onDateSelected(date: Long) {
        _selectedDate.value = date
    }

    fun startRecording() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRecording = true)
            speechHelper.startListening()
                .collect { text ->
                    _uiState.value = _uiState.value.copy(isRecording = false)
                    processVoiceCommand(text)
                }
        }
    }

    fun stopRecording() {
        // SpeechRecognizer handles stop automatically on end of speech, 
        // but we can force update state if needed
        _uiState.value = _uiState.value.copy(isRecording = false)
    }

    private fun processVoiceCommand(text: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)
            // 模拟 API Key (实际应从设置获取)
            val apiKey = "DEMO_KEY" 
            val result = aiRepository.parseTasksFromText(apiKey, text)
            
            result.onSuccess { tasks ->
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    showAiResult = true,
                    aiParsedTasks = tasks
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isAnalyzing = false)
                // TODO: Show error
            }
        }
    }
    
    fun confirmAddTasks() {
        viewModelScope.launch {
            val tasksToAdd = _uiState.value.aiParsedTasks
            taskRepository.insertTasks(tasksToAdd)
            _uiState.value = _uiState.value.copy(showAiResult = false, aiParsedTasks = emptyList())
        }
    }

    fun addTask(task: Task) {
        viewModelScope.launch {
            taskRepository.insertTask(task)
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            taskRepository.updateTask(task)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            taskRepository.deleteTask(task)
        }
    }

    fun dismissAiResult() {
        _uiState.value = _uiState.value.copy(showAiResult = false, aiParsedTasks = emptyList())
    }
}

data class HomeUiState(
    val isRecording: Boolean = false,
    val isAnalyzing: Boolean = false,
    val showAiResult: Boolean = false,
    val aiParsedTasks: List<Task> = emptyList()
)
