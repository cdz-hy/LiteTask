package com.litetask.app.ui.home

import android.app.Application
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
import com.litetask.app.R

/**
 * 首页 ViewModel
 * 
 * 数据加载策略：
 * 1. 未完成任务：通过 Room Flow 实时订阅，自动更新
 * 2. 已完成任务：分页加载，每页20条，滑动到底部时加载更多
 * 3. 懒更新：冷启动时自动将过期任务标记为已完成
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val application: Application,
    private val taskRepository: TaskRepositoryImpl,
    private val aiRepository: AIRepository,
    private val speechHelper: com.litetask.app.util.SpeechRecognizerHelper,
    private val preferenceManager: com.litetask.app.data.local.PreferenceManager
) : ViewModel() {

    // ==================== 数据加载配置 ====================
    
    companion object {
        /** 历史任务每页加载数量 */
        private const val PAGE_SIZE = 20
    }

    // ==================== 初始化 ====================
    
    init {
        initializeData()
    }
    
    /**
     * 初始化数据加载
     * 
     * 执行顺序：
     * 1. 懒更新：标记过期任务为已完成
     * 2. 加载首批历史任务（20条）
     * 
     * 注：未完成任务通过 Flow 自动订阅，无需手动加载
     */
    private fun initializeData() {
        viewModelScope.launch {
            // Step 1: 懒更新 - 标记过期任务
            markOverdueTasksAsDone()
            // Step 2: 加载首批历史任务
            loadMoreHistory()
        }
    }

    // ==================== 数据流定义 ====================
    
    /** 未完成任务流（实时订阅，自动更新） */
    private val _activeTasks = taskRepository.getActiveTasks()
    
    /** 已完成任务列表（分页加载） */
    private val _historyTasks = MutableStateFlow<List<TaskDetailComposite>>(emptyList())
    
    /** 历史任务分页状态 */
    private var _historyPage = 0
    private var _isHistoryExhausted = false
    private val _isLoadingHistory = MutableStateFlow(false)
    val isLoadingHistory: StateFlow<Boolean> = _isLoadingHistory.asStateFlow()
    
    /** 下拉刷新状态 */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    /** UI 状态（录音、AI分析等） */
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * 合并后的 Timeline 列表
     * 
     * 结构：[未完成任务] + [历史分隔线] + [已完成任务] + [加载指示器]
     */
    val timelineItems: StateFlow<List<TimelineItem>> = combine(
        _activeTasks,
        _historyTasks,
        _isLoadingHistory
    ) { active, history, isLoading ->
        buildList {
            // 未完成任务（置顶优先）
            addAll(active.map { TimelineItem.TaskItem(it) })
            // 历史分隔线 + 已完成任务
            if (history.isNotEmpty()) {
                add(TimelineItem.HistoryHeader)
                addAll(history.map { TimelineItem.TaskItem(it) })
            }
            // 加载指示器
            if (isLoading) add(TimelineItem.Loading)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ==================== 日期筛选（用于日历视图） ====================
    
    private val _selectedDate = MutableStateFlow(todayStartMillis())
    val selectedDate: StateFlow<Long> = _selectedDate.asStateFlow()
    
    /** 按日期筛选的任务（与选定日期有时间重叠） */
    val tasks: StateFlow<List<TaskDetailComposite>> = combine(
        _activeTasks,
        _selectedDate
    ) { tasks, date ->
        val dayStart = date
        val dayEnd = date + 24 * 60 * 60 * 1000L
        tasks.filter { it.task.startTime < dayEnd && it.task.deadline > dayStart }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    fun onDateSelected(date: Long) {
        _selectedDate.value = date
    }
    
    private fun todayStartMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    // ==================== 数据加载方法 ====================
    
    /**
     * 懒更新：标记过期任务为已完成
     */
    private suspend fun markOverdueTasksAsDone() {
        try {
            taskRepository.autoMarkOverdueTasksAsDone(System.currentTimeMillis())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 加载更多历史任务（分页）
     * 
     * 触发时机：列表滑动到底部
     * 加载策略：每次加载20条，直到没有更多数据
     */
    fun loadMoreHistory() {
        if (_isLoadingHistory.value || _isHistoryExhausted) return
        
        viewModelScope.launch {
            _isLoadingHistory.value = true
            try {
                val offset = _historyPage * PAGE_SIZE
                val newItems = taskRepository.getHistoryTasks(PAGE_SIZE, offset)
                
                if (newItems.isEmpty()) {
                    _isHistoryExhausted = true
                } else {
                    _historyTasks.value = _historyTasks.value + newItems
                    _historyPage++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingHistory.value = false
            }
        }
    }
    
    /**
     * 下拉刷新
     * 
     * 执行流程：
     * 1. 懒更新过期任务
     * 2. 重置分页状态
     * 3. 重新加载首批历史任务
     */
    fun onRefresh() {
        if (_isRefreshing.value) return
        
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // 懒更新
                markOverdueTasksAsDone()
                // 重新加载历史任务
                val newHistory = taskRepository.getHistoryTasks(PAGE_SIZE, 0)
                resetHistoryPagination(newHistory)
                // 动画延迟
                kotlinx.coroutines.delay(300)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
    
    /**
     * 重置历史任务分页状态
     */
    private fun resetHistoryPagination(newData: List<TaskDetailComposite>) {
        _historyTasks.value = newData
        _historyPage = if (newData.isNotEmpty()) 1 else 0
        _isHistoryExhausted = newData.size < PAGE_SIZE
    }

    // ==================== 任务详情 ====================
    
    fun getTaskDetailFlow(taskId: Long): Flow<TaskDetailComposite> = 
        taskRepository.getTaskDetail(taskId)
    
    fun getTaskDetail(taskId: Long, onResult: (TaskDetailComposite) -> Unit) {
        viewModelScope.launch {
            taskRepository.getTaskDetail(taskId).take(1).collect { onResult(it) }
        }
    }

    private var recordingJob: kotlinx.coroutines.Job? = null
    
    /**
     * 检查 API Key 是否已配置
     */
    fun checkApiKey(): ApiKeyCheckResult {
        val apiKey = preferenceManager.getApiKey()
        return if (apiKey.isNullOrBlank()) {
            ApiKeyCheckResult.NotConfigured
        } else {
            ApiKeyCheckResult.Valid
        }
    }
    
    /**
     * 获取当前语音识别源信息
     */
    fun getSpeechSourceInfo(): com.litetask.app.util.SpeechSourceInfo {
        return speechHelper.getCurrentSourceInfo()
    }
    
    fun startRecording() {
        recordingJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRecording = true, 
                recognizedText = "",
                finalRecognizedText = "",
                recordingState = com.litetask.app.util.RecordingState.RECORDING
            )
            try {
                // 使用录音+实时识别
                speechHelper.startRecognition()
                    .collect { result ->
                        when (result) {
                            is com.litetask.app.util.VoiceRecordResult.RecordingStarted -> {
                                _uiState.value = _uiState.value.copy(
                                    recordingState = com.litetask.app.util.RecordingState.RECORDING
                                )
                            }
                            is com.litetask.app.util.VoiceRecordResult.RecognitionResult -> {
                                _uiState.value = _uiState.value.copy(recognizedText = result.text)
                            }
                            is com.litetask.app.util.VoiceRecordResult.Error -> {
                                // 处理语音识别错误
                                val errorMessage = parseSpeechError(result.message)
                                _uiState.value = _uiState.value.copy(
                                    isRecording = false,
                                    showSpeechError = true,
                                    speechErrorMessage = errorMessage,
                                    recordingState = com.litetask.app.util.RecordingState.IDLE
                                )
                                speechHelper.release()
                            }
                            else -> {}
                        }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程被取消是正常行为（用户按返回键等），不显示错误
                _uiState.value = _uiState.value.copy(
                    isRecording = false,
                    recordingState = com.litetask.app.util.RecordingState.IDLE
                )
            } catch (e: Exception) {
                val errorMessage = parseSpeechError(e.message ?: "")
                _uiState.value = _uiState.value.copy(
                    isRecording = false,
                    showSpeechError = true,
                    speechErrorMessage = errorMessage,
                    recordingState = com.litetask.app.util.RecordingState.IDLE
                )
            }
        }
    }
    
    /**
     * 解析语音识别错误信息
     */
    private fun parseSpeechError(errorMsg: String): String {
        return when {
            // 讯飞错误码
            errorMsg.contains("App ID 不存在") -> 
                application.getString(R.string.error_speech_appid_not_exist)
            errorMsg.contains("App ID 已被禁用") -> 
                application.getString(R.string.error_speech_appid_disabled)
            errorMsg.contains("没有实时语音转写权限") -> 
                application.getString(R.string.error_speech_no_permission)
            errorMsg.contains("签名错误") || errorMsg.contains("API Key") -> 
                application.getString(R.string.error_speech_apikey_invalid)
            errorMsg.contains("签名无效") -> 
                application.getString(R.string.error_speech_signa_invalid)
            errorMsg.contains("时间戳过期") -> 
                application.getString(R.string.error_speech_timestamp_expired)
            errorMsg.contains("转写时长已用完") -> 
                application.getString(R.string.error_speech_quota_exhausted)
            errorMsg.contains("并发数超限") -> 
                application.getString(R.string.error_speech_concurrent_limit)
            errorMsg.contains("凭证未配置") -> 
                application.getString(R.string.error_speech_not_configured)
            // 通用错误
            errorMsg.contains("连接失败") || errorMsg.contains("网络") -> 
                application.getString(R.string.error_speech_network)
            errorMsg.contains("超时") -> 
                application.getString(R.string.error_speech_timeout)
            else -> errorMsg.ifEmpty { application.getString(R.string.error_speech_unknown) }
        }
    }
    
    /**
     * 关闭语音识别错误提示
     */
    fun dismissSpeechError() {
        _uiState.value = _uiState.value.copy(showSpeechError = false, speechErrorMessage = "")
    }
    
    // 录音时长
    val recordingDuration: StateFlow<Long> = speechHelper.recordingDuration

    fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null
        speechHelper.release()
    }
    
    fun finishRecording() {
        // 停止录音，进入编辑确认状态
        recordingJob?.cancel()
        recordingJob = null
        
        // 停止录音但保持界面显示，让用户可以编辑或看到空状态提示
        _uiState.value = _uiState.value.copy(
            isRecording = false,
            recordingState = com.litetask.app.util.RecordingState.IDLE,
            showVoiceResult = true  // 显示结果界面（无论有无识别文字）
        )
    }
    
    fun finishRecordingWithText(editedText: String) {
        // 用户确认后，使用编辑后的文本进行 AI 分析
        if (editedText.isNotBlank()) {
            _uiState.value = _uiState.value.copy(
                isRecording = false,
                showVoiceResult = false,
                recordingState = com.litetask.app.util.RecordingState.PLAYING // 复用作为"分析中"状态
            )
            processVoiceCommand(editedText)
        } else {
            cancelRecording()
        }
    }
    
    fun cancelRecording() {
        recordingJob?.cancel()
        recordingJob = null
        speechHelper.release()
        _uiState.value = _uiState.value.copy(
            isRecording = false,
            showVoiceResult = false,
            recognizedText = "",
            finalRecognizedText = "",
            recordingState = com.litetask.app.util.RecordingState.IDLE
        )
    }

    private fun processVoiceCommand(text: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)
            
            val result = aiRepository.parseTasksFromText("", text) // 空字符串让 Repository 使用存储的 Key
            
            result.onSuccess { tasks ->
                // 无论是否有任务，都显示 TaskConfirmationSheet（空结果会在界面中显示提示）
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    showAiResult = true,
                    aiParsedTasks = tasks,
                    recordingState = com.litetask.app.util.RecordingState.IDLE
                )
            }.onFailure { error ->
                val errorMessage = when {
                    error.message?.contains("API Key 无效") == true -> 
                        application.getString(R.string.error_api_key_invalid)
                    error.message?.contains("未设置 API Key") == true -> 
                        application.getString(R.string.error_api_key_not_set)
                    error.message?.contains("权限不足") == true -> 
                        application.getString(R.string.error_api_key_permission)
                    error.message?.contains("请求过于频繁") == true -> 
                        application.getString(R.string.error_rate_limit)
                    error.message?.contains("无法连接") == true || 
                    error.message?.contains("网络") == true -> 
                        application.getString(R.string.error_network)
                    error.message?.contains("超时") == true -> 
                        application.getString(R.string.error_timeout)
                    else -> error.message ?: application.getString(R.string.error_ai_analysis)
                }
                
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    showAiError = true,
                    aiErrorMessage = errorMessage,
                    recordingState = com.litetask.app.util.RecordingState.IDLE
                )
            }
        }
    }
    
    /**
     * 文字输入分析：直接调用 AI 分析文本
     */
    fun analyzeTextInput(text: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)
            
            val result = aiRepository.parseTasksFromText("", text)
            
            result.onSuccess { tasks ->
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    showAiResult = true,
                    aiParsedTasks = tasks
                )
            }.onFailure { error ->
                val errorMessage = when {
                    error.message?.contains("API Key 无效") == true -> 
                        application.getString(R.string.error_api_key_invalid)
                    error.message?.contains("未设置 API Key") == true -> 
                        application.getString(R.string.error_api_key_not_set)
                    error.message?.contains("权限不足") == true -> 
                        application.getString(R.string.error_api_key_permission)
                    error.message?.contains("请求过于频繁") == true -> 
                        application.getString(R.string.error_rate_limit)
                    error.message?.contains("无法连接") == true || 
                    error.message?.contains("网络") == true -> 
                        application.getString(R.string.error_network)
                    error.message?.contains("超时") == true -> 
                        application.getString(R.string.error_timeout)
                    else -> error.message ?: application.getString(R.string.error_ai_analysis)
                }
                
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    showAiError = true,
                    aiErrorMessage = errorMessage
                )
            }
        }
    }
    
    fun dismissAiError() {
        _uiState.value = _uiState.value.copy(showAiError = false, aiErrorMessage = "")
    }
    
    fun confirmAddTasks() {
        viewModelScope.launch {
            val tasksToAdd = _uiState.value.aiParsedTasks
            if (tasksToAdd.isNotEmpty()) {
                taskRepository.insertTasks(tasksToAdd)
            }
            _uiState.value = _uiState.value.copy(showAiResult = false, aiParsedTasks = emptyList())
        }
    }
    
    /**
     * 确认添加任务（带提醒）
     */
    fun confirmAddTasksWithReminders(
        tasks: List<Task>, 
        taskReminders: Map<Int, List<com.litetask.app.data.model.ReminderConfig>>
    ) {
        viewModelScope.launch {
            if (tasks.isNotEmpty()) {
                tasks.forEachIndexed { index, task ->
                    val reminderConfigs = taskReminders[index] ?: emptyList()
                    if (reminderConfigs.isNotEmpty()) {
                        // 有提醒配置，使用带提醒的插入方法
                        val reminders = reminderConfigs.mapNotNull { config ->
                            val triggerAt = config.calculateTriggerTime(task.startTime, task.deadline)
                            if (triggerAt > 0 && config.type != com.litetask.app.data.model.ReminderType.NONE) {
                                com.litetask.app.data.model.Reminder(
                                    taskId = 0, // 会在插入时更新
                                    triggerAt = triggerAt,
                                    label = config.generateLabel()
                                )
                            } else null
                        }
                        taskRepository.insertTaskWithReminders(task, reminders)
                    } else {
                        // 没有提醒配置，直接插入任务
                        taskRepository.insertTask(task)
                    }
                }
            }
            _uiState.value = _uiState.value.copy(showAiResult = false, aiParsedTasks = emptyList())
        }
    }
    
    fun updateAiParsedTask(index: Int, task: Task) {
        val currentTasks = _uiState.value.aiParsedTasks.toMutableList()
        if (index in currentTasks.indices) {
            currentTasks[index] = task
            _uiState.value = _uiState.value.copy(aiParsedTasks = currentTasks)
        }
    }
    
    fun deleteAiParsedTask(index: Int) {
        val currentTasks = _uiState.value.aiParsedTasks.toMutableList()
        if (index in currentTasks.indices) {
            currentTasks.removeAt(index)
            _uiState.value = _uiState.value.copy(aiParsedTasks = currentTasks)
        }
    }

    // ==================== 任务 CRUD 操作 ====================
    
    fun addTask(task: Task) {
        viewModelScope.launch { taskRepository.insertTask(task) }
    }
    
    /** 添加任务并设置提醒 */
    fun addTaskWithReminders(task: Task, reminderConfigs: List<com.litetask.app.data.model.ReminderConfig>) {
        viewModelScope.launch {
            val reminders = reminderConfigs.mapNotNull { config ->
                val triggerAt = config.calculateTriggerTime(task.startTime, task.deadline)
                if (triggerAt > 0 && config.type != com.litetask.app.data.model.ReminderType.NONE) {
                    com.litetask.app.data.model.Reminder(taskId = 0, triggerAt = triggerAt, label = config.generateLabel())
                } else null
            }
            taskRepository.insertTaskWithReminders(task, reminders)
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            if (task.isDone) {
                // 完成任务：取消闹钟，刷新历史列表
                taskRepository.markTaskDone(task)
                refreshHistoryAfterCompletion()
            } else {
                // 重新激活任务：从历史列表移除
                taskRepository.updateTask(task)
                _historyTasks.value = _historyTasks.value.filter { it.task.id != task.id }
            }
        }
    }
    
    /** 更新任务并更新提醒 */
    fun updateTaskWithReminders(task: Task, reminderConfigs: List<com.litetask.app.data.model.ReminderConfig>) {
        viewModelScope.launch {
            if (task.isDone) {
                taskRepository.markTaskDone(task)
                refreshHistoryAfterCompletion()
                return@launch
            }
            
            val reminders = reminderConfigs.mapNotNull { config ->
                val triggerAt = config.calculateTriggerTime(task.startTime, task.deadline)
                if (triggerAt > 0 && config.type != com.litetask.app.data.model.ReminderType.NONE) {
                    com.litetask.app.data.model.Reminder(taskId = task.id, triggerAt = triggerAt, label = config.generateLabel())
                } else null
            }
            taskRepository.updateTaskWithReminders(task, reminders)
            _historyTasks.value = _historyTasks.value.filter { it.task.id != task.id }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { taskRepository.deleteTaskWithReminders(task) }
    }
    
    /** 任务完成后刷新历史列表 */
    private suspend fun refreshHistoryAfterCompletion() {
        val totalLoaded = (_historyPage + 1) * PAGE_SIZE
        val newHistory = taskRepository.getHistoryTasks(totalLoaded, 0)
        _historyTasks.value = newHistory
    }

    // ==================== 子任务操作 ====================
    
    fun addSubTask(taskId: Long, content: String) {
        viewModelScope.launch {
            taskRepository.insertSubTask(SubTask(taskId = taskId, content = content, isCompleted = false))
        }
    }
    
    fun updateSubTaskStatus(subTaskId: Long, completed: Boolean) {
        viewModelScope.launch { taskRepository.updateSubTaskStatus(subTaskId, completed) }
    }
    
    fun deleteSubTask(subTask: SubTask) {
        viewModelScope.launch { taskRepository.deleteSubTask(subTask) }
    }
    
    fun updateTaskWithSubTasks(task: Task, subTasks: List<SubTask>) {
        viewModelScope.launch { taskRepository.updateTaskWithSubTasks(task, subTasks) }
    }

    // ==================== 提醒相关 ====================
    
    fun getRemindersForTask(taskId: Long): Flow<List<com.litetask.app.data.model.Reminder>> =
        taskRepository.getRemindersByTaskId(taskId)
    
    suspend fun getRemindersForTaskSync(taskId: Long): List<com.litetask.app.data.model.Reminder> =
        taskRepository.getRemindersByTaskIdSync(taskId)

    fun dismissAiResult() {
        _uiState.value = _uiState.value.copy(showAiResult = false, aiParsedTasks = emptyList())
    }
}

data class HomeUiState(
    val isRecording: Boolean = false,
    val isAnalyzing: Boolean = false,
    val showAiResult: Boolean = false,
    val showVoiceResult: Boolean = false,   // 录音结束后显示结果界面（无论有无识别文字）
    val showAiError: Boolean = false,       // 显示 AI 错误界面
    val aiErrorMessage: String = "",        // AI 错误信息
    val showSpeechError: Boolean = false,   // 显示语音识别错误界面
    val speechErrorMessage: String = "",    // 语音识别错误信息
    val aiParsedTasks: List<Task> = emptyList(),
    val recognizedText: String = "",        // 实时识别的文字
    val finalRecognizedText: String = "",   // 最终识别结果
    val recordingState: com.litetask.app.util.RecordingState = com.litetask.app.util.RecordingState.IDLE
)

// 检查 API Key 结果
sealed class ApiKeyCheckResult {
    object Valid : ApiKeyCheckResult()
    object NotConfigured : ApiKeyCheckResult()
}