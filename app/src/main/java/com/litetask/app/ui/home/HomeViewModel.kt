package com.litetask.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.SubTask
import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.data.repository.TaskRepositoryImpl
import com.litetask.app.data.repository.AIRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val taskRepository: TaskRepositoryImpl,
    private val aiRepository: AIRepository,
    private val speechHelper: com.litetask.app.util.SpeechRecognizerHelper
) : ViewModel() {

    // 懒更新策略：在 ViewModel 初始化时自动标记过期任务
    init {
        refreshTasks()
    }

    // 日期选择状态
    private val _selectedDate = MutableStateFlow(Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis)
    val selectedDate: StateFlow<Long> = _selectedDate

    // 任务列表 (显示所有与选定日期有时间重叠的任务)
    val tasks: StateFlow<List<Task>> = combine(
        taskRepository.getAllTasks(),
        _selectedDate
    ) { allTasks, date ->
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.timeInMillis

        // 修复：显示所有与当天有重叠的任务
        // 任务重叠条件：任务开始时间 < 当天结束时间 AND 任务结束时间 > 当天开始时间
        allTasks.filter { task ->
            task.startTime < endOfDay && task.deadline > startOfDay
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 1. 所有未完成任务（包括置顶和非置顶）
    private val activeFlow = taskRepository.getActiveTasks()

    // 2. 历史数据 (手动分页管理)
    private val _historyList = MutableStateFlow<List<TaskDetailComposite>>(emptyList())
    private var historyPage = 0
    private val pageSize = 20
    private var isHistoryExhausted = false
    private val _isLoadingHistory = MutableStateFlow(false)

    // 3. 合并后的 UI 列表流
    val timelineItems: StateFlow<List<TimelineItem>> = combine(
        activeFlow,
        _historyList,
        _isLoadingHistory
    ) { active, history, isLoading ->
        val items = mutableListOf<TimelineItem>()

        // A. 所有未完成任务（置顶的在前）
        items.addAll(active.map { TimelineItem.TaskItem(it) })

        // B. 历史区域分隔线
        if (history.isNotEmpty()) {
            items.add(TimelineItem.HistoryHeader)
            items.addAll(history.map { TimelineItem.TaskItem(it) })
        }

        if (isLoading) {
            items.add(TimelineItem.Loading)
        }

        items.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI 状态
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState
    
    // 刷新状态
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    fun onDateSelected(date: Long) {
        _selectedDate.value = date
    }

    fun loadMoreHistory() {
        if (_isLoadingHistory.value || isHistoryExhausted) return

        viewModelScope.launch {
            _isLoadingHistory.value = true
            val newItems = taskRepository.getHistoryTasks(pageSize, historyPage * pageSize)
            if (newItems.isEmpty()) {
                isHistoryExhausted = true
            } else {
                _historyList.value += newItems
                historyPage++
            }
            _isLoadingHistory.value = false
        }
    }

    fun getTaskDetail(taskId: Long, onResult: (TaskDetailComposite) -> Unit) {
        viewModelScope.launch {
            taskRepository.getTaskDetail(taskId).take(1).collect { 
                onResult(it)
            }
        }
    }
    
    fun getTaskDetailFlow(taskId: Long): Flow<TaskDetailComposite> {
        return taskRepository.getTaskDetail(taskId)
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
            
            // 如果任务状态从已完成变为未完成，需要从历史列表中移除
            if (!task.isDone) {
                _historyList.value = _historyList.value.filter { it.task.id != task.id }
            }
            // 如果任务状态从未完成变为已完成，需要重新加载历史列表
            else {
                // 重新加载历史列表以包含新完成的任务
                val newHistory = taskRepository.getHistoryTasks(pageSize * (historyPage + 1), 0)
                _historyList.value = newHistory
            }
        }
    }

    fun updateTaskWithSubTasks(task: Task, subTasks: List<SubTask>) {
        viewModelScope.launch {
            taskRepository.updateTaskWithSubTasks(task, subTasks)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            taskRepository.deleteTask(task)
        }
    }

    fun updateSubTaskStatus(subTaskId: Long, completed: Boolean) {
        viewModelScope.launch {
            taskRepository.updateSubTaskStatus(subTaskId, completed)
        }
    }
    
    fun addSubTask(taskId: Long, content: String) {
        viewModelScope.launch {
            val newSub = SubTask(taskId = taskId, content = content, isCompleted = false)
            taskRepository.insertSubTask(newSub)
        }
    }

    fun deleteSubTask(subTask: SubTask) {
        viewModelScope.launch {
            taskRepository.deleteSubTask(subTask)
        }
    }

    fun dismissAiResult() {
        _uiState.value = _uiState.value.copy(showAiResult = false, aiParsedTasks = emptyList())
    }
    
    /**
     * 懒更新策略：刷新任务列表
     * 
     * 执行流程：
     * 1. 先自动标记所有已过期的任务为完成状态
     * 2. 然后刷新任务列表（由于使用 Flow，数据会自动更新）
     * 
     * 调用时机：
     * - ViewModel 初始化时（用户打开 App）
     * - 用户手动刷新时
     * - 从后台返回前台时
     */
    fun refreshTasks() {
        viewModelScope.launch {
            try {
                // 1. 执行自动更新逻辑：标记所有过期任务
                val now = System.currentTimeMillis()
                val updatedCount = taskRepository.autoMarkOverdueTasksAsDone(now)
                
                // 2. 由于使用了 Flow，任务列表会自动更新
                // 可以在这里添加日志或通知用户
                if (updatedCount > 0) {
                    // TODO: 可选 - 显示提示信息，如 "已自动完成 $updatedCount 个过期任务"
                }
            } catch (e: Exception) {
                // 处理异常，但不影响正常使用
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 下拉刷新：重新加载所有数据
     * 
     * 执行流程：
     * 1. 标记过期任务为已完成
     * 2. 重置历史任务分页
     * 3. 重新加载最近20条已完成任务
     */
    fun onRefresh() {
        if (_isRefreshing.value) return
        
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // 1. 标记过期任务
                val now = System.currentTimeMillis()
                taskRepository.autoMarkOverdueTasksAsDone(now)
                
                // 2. 重置历史任务分页
                historyPage = 0
                isHistoryExhausted = false
                _historyList.value = emptyList()
                
                // 3. 重新加载最近20条已完成任务
                val newHistory = taskRepository.getHistoryTasks(pageSize, 0)
                _historyList.value = newHistory
                if (newHistory.isNotEmpty()) {
                    historyPage = 1
                }
                
                // 延迟一下让动画更自然
                kotlinx.coroutines.delay(300)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

data class HomeUiState(
    val isRecording: Boolean = false,
    val isAnalyzing: Boolean = false,
    val showAiResult: Boolean = false,
    val aiParsedTasks: List<Task> = emptyList()
)