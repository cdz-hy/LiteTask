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

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val application: Application,
    private val taskRepository: TaskRepositoryImpl,
    private val aiRepository: AIRepository,
    private val speechHelper: com.litetask.app.util.SpeechRecognizerHelper,
    private val preferenceManager: com.litetask.app.data.local.PreferenceManager
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
    // 注意：这里我们使用 TaskDetailComposite 来获取子任务信息
    val tasks: StateFlow<List<TaskDetailComposite>> = combine(
        taskRepository.getActiveTasks(),
        _selectedDate
    ) { allTasks, date ->
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.timeInMillis

        // 修复：显示所有与当天有重叠的任务
        // 任务重叠条件：任务开始时间 < 当天结束时间 AND 任务结束时间 > 当天开始时间
        allTasks.filter { composite ->
            composite.task.startTime < endOfDay && composite.task.deadline > startOfDay
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

    fun addTask(task: Task) {
        viewModelScope.launch {
            taskRepository.insertTask(task)
        }
    }
    
    /**
     * 添加任务并设置提醒
     */
    fun addTaskWithReminders(task: Task, reminderConfigs: List<com.litetask.app.data.model.ReminderConfig>) {
        viewModelScope.launch {
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
    
    /**
     * 更新任务并更新提醒
     */
    fun updateTaskWithReminders(task: Task, reminderConfigs: List<com.litetask.app.data.model.ReminderConfig>) {
        viewModelScope.launch {
            val reminders = reminderConfigs.mapNotNull { config ->
                val triggerAt = config.calculateTriggerTime(task.startTime, task.deadline)
                if (triggerAt > 0 && config.type != com.litetask.app.data.model.ReminderType.NONE) {
                    com.litetask.app.data.model.Reminder(
                        taskId = task.id,
                        triggerAt = triggerAt,
                        label = config.generateLabel()
                    )
                } else null
            }
            taskRepository.updateTaskWithReminders(task, reminders)
            
            // 处理历史列表更新
            if (!task.isDone) {
                _historyList.value = _historyList.value.filter { it.task.id != task.id }
            } else {
                val newHistory = taskRepository.getHistoryTasks(pageSize * (historyPage + 1), 0)
                _historyList.value = newHistory
            }
        }
    }
    
    /**
     * 获取任务的提醒列表
     */
    fun getRemindersForTask(taskId: Long): kotlinx.coroutines.flow.Flow<List<com.litetask.app.data.model.Reminder>> {
        return taskRepository.getRemindersByTaskId(taskId)
    }
    
    /**
     * 同步获取任务的提醒列表
     */
    suspend fun getRemindersForTaskSync(taskId: Long): List<com.litetask.app.data.model.Reminder> {
        return taskRepository.getRemindersByTaskIdSync(taskId)
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
                
                // 2. 重新加载最近20条已完成任务（不先清空，避免闪烁）
                val newHistory = taskRepository.getHistoryTasks(pageSize, 0)
                
                // 3. 重置历史任务分页并更新列表（一次性操作）
                historyPage = if (newHistory.isNotEmpty()) 1 else 0
                isHistoryExhausted = false
                _historyList.value = newHistory
                
                // 延迟一下让动画更自然
                kotlinx.coroutines.delay(300)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
    
    /**
     * 静默刷新：用于视图切换时自动加载数据，不显示刷新动画
     * 
     * 执行流程：
     * 1. 重置历史任务分页
     * 2. 重新加载最近20条已完成任务
     * 
     * 注意：不执行过期任务的懒更新，避免不必要的数据库操作
     */
    fun silentRefresh() {
        viewModelScope.launch {
            try {
                // 重新加载最近20条已完成任务
                val newHistory = taskRepository.getHistoryTasks(pageSize, 0)
                
                // 重置历史任务分页并更新列表
                historyPage = if (newHistory.isNotEmpty()) 1 else 0
                isHistoryExhausted = false
                _historyList.value = newHistory
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 初始化 Timeline 数据：首次进入 Timeline 视图时调用
     * 
     * 执行流程：
     * 1. 如果历史列表为空，自动加载第一页历史任务
     * 2. 避免重复加载
     */
    fun initTimelineData() {
        // 只在历史列表为空且未加载时才加载
        if (_historyList.value.isEmpty() && !_isLoadingHistory.value && historyPage == 0) {
            loadMoreHistory()
        }
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