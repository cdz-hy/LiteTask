package com.litetask.app.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.SubTask
import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.data.repository.TaskRepositoryImpl
import com.litetask.app.data.repository.AIRepository
import com.litetask.app.widget.WidgetUpdateHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import com.litetask.app.R

/**
 * 首页 ViewModel
 * 
 * 数据加载策略：
 * 1. 未完成任务：通过 Room Flow 实时订阅，自动更新
 * 2. 已完成任务：分页加载，每页20条，滑动到底部时加载更多
 * 3. 懒更新：冷启动时自动将截止时间在当前时间之前的任务标记为过期状态
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val application: Application,
    private val taskRepository: TaskRepositoryImpl,
    private val aiRepository: AIRepository,
    private val aiHistoryRepository: com.litetask.app.data.repository.AIHistoryRepository,
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
     * 1. 懒更新：标记过期任务为过期状态 + 标记过期提醒为已触发（IO线程）
     * 
     * 注：主要任务数据通过 getAllDisplayTasks() Flow 自动订阅，无需手动加载
     */
    private fun initializeData() {
        viewModelScope.launch(Dispatchers.IO) {
            // 懒更新 - 标记过期任务和提醒
            markOverdueTasksAsExpired()
        }
    }

    // ==================== 数据流定义 ====================
    
    /** 所有需要显示的任务流（未完成 + 已过期 + 前20条已完成） */
    private val _allDisplayTasks = taskRepository.getAllDisplayTasks()
    
    /** 未完成任务流（实时订阅，自动更新） - 保留用于日历视图等 */
    private val _activeTasks = taskRepository.getActiveTasks()
    
    /** 额外的已完成任务列表（分页加载，用于"查看更多"功能） */
    private val _additionalHistoryTasks = MutableStateFlow<List<TaskDetailComposite>>(emptyList())
    
    /** 历史任务分页状态 */
    private var _historyPage = 1  // 从第1页开始，因为前20条已经在主流中
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
     * 结构：[未完成任务] + [已过期任务] + [已完成任务分隔线] + [前20条已完成任务] + [更多已完成任务] + [加载指示器]
     */
    val timelineItems: StateFlow<List<TimelineItem>> = combine(
        _allDisplayTasks,
        _additionalHistoryTasks,
        _isLoadingHistory
    ) { allTasks, additionalHistory, isLoading ->
        buildList {
            // 分离不同状态的任务
            val activeTasks = allTasks.filter { !it.task.isDone && !it.task.isExpired }
            val expiredTasks = allTasks.filter { !it.task.isDone && it.task.isExpired }
            val completedTasks = allTasks.filter { it.task.isDone }
            
            // 1. 未完成任务（置顶优先，不显示置顶头部）
            addAll(activeTasks.map { TimelineItem.TaskItem(it) })
            
            // 2. 已过期任务
            if (expiredTasks.isNotEmpty()) {
                add(TimelineItem.ExpiredHeader)
                addAll(expiredTasks.map { TimelineItem.TaskItem(it) })
            }
            
            // 3. 已完成任务
            if (completedTasks.isNotEmpty() || additionalHistory.isNotEmpty()) {
                add(TimelineItem.HistoryHeader)
                addAll(completedTasks.map { TimelineItem.TaskItem(it) })
                
                // 核心修复：过滤掉已经在主流（前20条）中出现的已完成任务，避免分页加载时的 ID 重复导致 UI 异常或崩溃
                // 同时确保数据一致性：只有真正的已完成任务才会出现在历史区域
                val completedIds = completedTasks.map { it.task.id }.toSet()
                val distinctAdditional = additionalHistory.filter { 
                    it.task.id !in completedIds && it.task.isDone // 双重检查：确保是已完成任务
                }
                
                addAll(distinctAdditional.map { TimelineItem.TaskItem(it) })
            }
            
            // 4. 加载指示器
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
     * 懒更新：标记过期任务为过期状态 + 标记过期提醒为已触发
     */
    private suspend fun markOverdueTasksAsExpired() {
        try {
            val now = System.currentTimeMillis()
            taskRepository.autoSyncTaskExpiredStatus(now)
            taskRepository.autoMarkOverdueRemindersAsFired(now)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 加载更多已完成任务（分页，超过前20条的部分）
     * 
     * 触发时机：列表滑动到底部
     * 加载策略：每次加载20条，从第21条开始，直到没有更多数据
     */
    fun loadMoreHistory() {
        if (_isLoadingHistory.value || _isHistoryExhausted) return
        
        viewModelScope.launch {
            _isLoadingHistory.value = true
            try {
                val offset = 20 + (_historyPage - 1) * PAGE_SIZE  // 跳过前20条
                val newItems = withContext(Dispatchers.IO) {
                    taskRepository.getHistoryTasks(PAGE_SIZE, offset)
                }
                
                if (newItems.isEmpty()) {
                    _isHistoryExhausted = true
                } else {
                    _additionalHistoryTasks.value = _additionalHistoryTasks.value + newItems
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
     * 1. 懒更新过期任务和提醒
     * 2. 重置额外历史任务分页状态
     * 
     * 注：主要数据通过Flow自动刷新
     */
    fun onRefresh() {
        if (_isRefreshing.value) return
        
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // 懒更新（IO线程）
                withContext(Dispatchers.IO) {
                    markOverdueTasksAsExpired()
                }
                // 重置额外历史任务分页状态
                resetAdditionalHistoryPagination()
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
     * 重置额外历史任务分页状态
     */
    private fun resetAdditionalHistoryPagination() {
        _additionalHistoryTasks.value = emptyList()
        _historyPage = 1
        _isHistoryExhausted = false
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
     * 获取默认 FAB 操作
     */
    fun getDefaultFabAction(): String = preferenceManager.getDefaultFabAction()
    
    /**
     * 获取默认首页视图
     */
    fun getDefaultHomeView(): String = preferenceManager.getDefaultHomeView()
    
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
                // 记录 AI 历史
                viewModelScope.launch {
                    aiHistoryRepository.insertHistory(
                        com.litetask.app.data.model.AIHistory(
                            content = text,
                            sourceType = com.litetask.app.data.model.AIHistorySource.VOICE,
                            parsedCount = tasks.size,
                            isSuccess = true
                        )
                    )
                }
                
                // 无论是否有任务，都显示 TaskConfirmationSheet（空结果会在界面中显示提示）
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    showAiResult = true,
                    aiParsedTasks = tasks,
                    recordingState = com.litetask.app.util.RecordingState.IDLE
                )
            }.onFailure { error ->
                // 记录失败历史
                viewModelScope.launch {
                    aiHistoryRepository.insertHistory(
                        com.litetask.app.data.model.AIHistory(
                            content = text,
                            sourceType = com.litetask.app.data.model.AIHistorySource.VOICE,
                            parsedCount = 0,
                            isSuccess = false
                        )
                    )
                }
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
                // 记录 AI 历史
                viewModelScope.launch {
                    aiHistoryRepository.insertHistory(
                        com.litetask.app.data.model.AIHistory(
                            content = text,
                            sourceType = com.litetask.app.data.model.AIHistorySource.TEXT,
                            parsedCount = tasks.size,
                            isSuccess = true
                        )
                    )
                }
                
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    showAiResult = true,
                    aiParsedTasks = tasks
                )
            }.onFailure { error ->
                // 记录失败历史
                viewModelScope.launch {
                    aiHistoryRepository.insertHistory(
                        com.litetask.app.data.model.AIHistory(
                            content = text,
                            sourceType = com.litetask.app.data.model.AIHistorySource.TEXT,
                            parsedCount = 0,
                            isSuccess = false
                        )
                    )
                }
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
        viewModelScope.launch { 
            taskRepository.insertTask(task)
            WidgetUpdateHelper.refreshAllWidgets(application)
        }
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
            WidgetUpdateHelper.refreshAllWidgets(application)
        }
    }

    fun toggleTaskDone(task: Task) {
        viewModelScope.launch {
            if (!task.isDone) {
                // 未完成 -> 已完成
                taskRepository.markTaskDone(task)
                refreshHistoryAfterCompletion()
            } else {
                // 已完成 -> 未完成：使用状态跟踪更新，确保数据一致性
                taskRepository.updateTaskWithStatusTracking(task, task.copy(isDone = false))
                // 从额外历史列表中移除（如果存在）
                cleanupTaskFromAdditionalHistory(task.id)
            }
            WidgetUpdateHelper.refreshAllWidgets(application)
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            if (task.isDone && task.completedAt == null) {
                // 核心逻辑：只有从未完成变为完成（即 completedAt 为空）时，才触发完成专门处理
                // 这确保了编辑已完成任务时，原始完成时间不会被“现在”覆盖
                taskRepository.markTaskDone(task)
                refreshHistoryAfterCompletion()
            } else {
                // 已完成任务的编辑，或者从未完成到未完成的更新
                taskRepository.updateTask(task)
                // 如果任务从历史列表中重新激活，从额外历史列表移除
                if (!task.isDone) {
                    cleanupTaskFromAdditionalHistory(task.id)
                }
            }
            WidgetUpdateHelper.refreshAllWidgets(application)
        }
    }
    
    /**
     * 更新任务状态（带状态跟踪）
     */
    fun updateTaskWithStatusTracking(oldTask: Task, newTask: Task) {
        viewModelScope.launch {
            taskRepository.updateTaskWithStatusTracking(oldTask, newTask)
            
            // 如果任务状态发生变化，更新UI列表
            when {
                !oldTask.isDone && newTask.isDone -> {
                    // 任务完成，刷新历史列表
                    refreshHistoryAfterCompletion()
                }
                oldTask.isDone && !newTask.isDone -> {
                    // 任务重新激活，从额外历史列表移除
                    cleanupTaskFromAdditionalHistory(newTask.id)
                }
            }
            
            WidgetUpdateHelper.refreshAllWidgets(application)
        }
    }
    
    /**
     * 重新激活过期任务
     */
    fun reactivateExpiredTask(task: Task, newDeadline: Long) {
        viewModelScope.launch {
            taskRepository.reactivateExpiredTask(task.id, newDeadline)
            WidgetUpdateHelper.refreshAllWidgets(application)
        }
    }
    
    /**
     * 获取过期任务列表
     */
    suspend fun getExpiredTasks(limit: Int, offset: Int) = 
        taskRepository.getExpiredTasks(limit, offset)
    
    /** 更新任务并更新提醒 */
    fun updateTaskWithReminders(task: Task, reminderConfigs: List<com.litetask.app.data.model.ReminderConfig>) {
        viewModelScope.launch {
            // 对于已完成任务，只更新任务信息，不处理提醒（已完成任务不需要提醒）
            if (task.isDone) {
                taskRepository.updateTask(task)
                WidgetUpdateHelper.refreshAllWidgets(application)
                return@launch
            }
            
            val reminders = reminderConfigs.mapNotNull { config ->
                val triggerAt = config.calculateTriggerTime(task.startTime, task.deadline)
                if (triggerAt > 0 && config.type != com.litetask.app.data.model.ReminderType.NONE) {
                    com.litetask.app.data.model.Reminder(taskId = task.id, triggerAt = triggerAt, label = config.generateLabel())
                } else null
            }
            taskRepository.updateTaskWithReminders(task, reminders)
            cleanupTaskFromAdditionalHistory(task.id)
            WidgetUpdateHelper.refreshAllWidgets(application)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { 
            taskRepository.deleteTaskWithReminders(task)
            WidgetUpdateHelper.refreshAllWidgets(application)
        }
    }
    
    /** 任务完成后刷新额外历史列表 */
    private suspend fun refreshHistoryAfterCompletion() {
        // 由于主要数据通过Flow自动更新，这里只需要重置额外的历史任务
        resetAdditionalHistoryPagination()
        
        // 延迟一小段时间确保数据库更新完成，避免UI闪烁
        kotlinx.coroutines.delay(80)
    }
    
    /**
     * 从额外历史列表中移除指定任务
     */
    private fun cleanupTaskFromAdditionalHistory(taskId: Long) {
        val current = _additionalHistoryTasks.value
        val filtered = current.filter { it.task.id != taskId }
        if (filtered.size != current.size) {
            _additionalHistoryTasks.value = filtered
        }
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
    
    // ==================== AI 子任务生成 ====================
    
    /**
     * 生成子任务（快速模式）
     */
    fun generateSubTasks(task: Task) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)
            
            val apiKey = preferenceManager.getApiKey()
            if (apiKey.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    showAiError = true,
                    aiErrorMessage = application.getString(R.string.error_api_key_not_set)
                )
                return@launch
            }
            
            try {
                // 获取用户配置的 AI 提供商
                val providerId = preferenceManager.getAiProvider()
                val deepSeekProvider = com.litetask.app.data.ai.DeepSeekProvider()
                val providerFactory = com.litetask.app.data.ai.AIProviderFactory(deepSeekProvider)
                val provider = providerFactory.getProvider(providerId) as? com.litetask.app.data.ai.DeepSeekProvider
                
                if (provider == null) {
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        showAiError = true,
                        aiErrorMessage = "当前 AI 提供商不支持子任务生成"
                    )
                    return@launch
                }
                
                val result = provider.generateSubTasks(apiKey, task)
                
                result.onSuccess { subTasks ->
                    // 记录 AI 历史
                    viewModelScope.launch {
                        aiHistoryRepository.insertHistory(
                            com.litetask.app.data.model.AIHistory(
                                content = "自动拆解: ${task.title}",
                                sourceType = com.litetask.app.data.model.AIHistorySource.SUBTASK,
                                parsedCount = subTasks.size,
                                isSuccess = true
                            )
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        showSubTaskResult = true,
                        generatedSubTasks = subTasks,
                        currentTask = task
                    )
                }.onFailure { error ->
                    // 记录失败历史
                    viewModelScope.launch {
                        aiHistoryRepository.insertHistory(
                            com.litetask.app.data.model.AIHistory(
                                content = "自动拆解失败: ${task.title}",
                                sourceType = com.litetask.app.data.model.AIHistorySource.SUBTASK,
                                parsedCount = 0,
                                isSuccess = false
                            )
                        )
                    }
                    val errorMessage = parseAiError(error.message ?: "")
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        showAiError = true,
                        aiErrorMessage = errorMessage
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    showAiError = true,
                    aiErrorMessage = "子任务生成失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 生成子任务（详细模式）
     */
    fun generateSubTasksWithContext(task: Task, additionalContext: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)
            
            val apiKey = preferenceManager.getApiKey()
            if (apiKey.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    showAiError = true,
                    aiErrorMessage = application.getString(R.string.error_api_key_not_set)
                )
                return@launch
            }
            
            try {
                // 获取用户配置的 AI 提供商
                val providerId = preferenceManager.getAiProvider()
                val deepSeekProvider = com.litetask.app.data.ai.DeepSeekProvider()
                val providerFactory = com.litetask.app.data.ai.AIProviderFactory(deepSeekProvider)
                val provider = providerFactory.getProvider(providerId) as? com.litetask.app.data.ai.DeepSeekProvider
                
                if (provider == null) {
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        showAiError = true,
                        aiErrorMessage = "当前 AI 提供商不支持子任务生成"
                    )
                    return@launch
                }
                
                val result = provider.generateSubTasks(apiKey, task, additionalContext)
                
                result.onSuccess { subTasks ->
                    // 记录 AI 历史
                    viewModelScope.launch {
                        aiHistoryRepository.insertHistory(
                            com.litetask.app.data.model.AIHistory(
                                content = "[${task.title}] 详细输入: $additionalContext",
                                sourceType = com.litetask.app.data.model.AIHistorySource.SUBTASK,
                                parsedCount = subTasks.size,
                                isSuccess = true
                            )
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        showSubTaskResult = true,
                        generatedSubTasks = subTasks,
                        currentTask = task,
                        showSubTaskInput = false
                    )
                }.onFailure { error ->
                    // 记录失败历史
                    viewModelScope.launch {
                        aiHistoryRepository.insertHistory(
                            com.litetask.app.data.model.AIHistory(
                                content = "[${task.title}] 详细输入失败: $additionalContext",
                                sourceType = com.litetask.app.data.model.AIHistorySource.SUBTASK,
                                parsedCount = 0,
                                isSuccess = false
                            )
                        )
                    }
                    val errorMessage = parseAiError(error.message ?: "")
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        showAiError = true,
                        aiErrorMessage = errorMessage,
                        showSubTaskInput = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    showAiError = true,
                    aiErrorMessage = "子任务生成失败: ${e.message}",
                    showSubTaskInput = false
                )
            }
        }
    }
    
    /**
     * 解析 AI 错误信息
     */
    private fun parseAiError(errorMsg: String): String {
        return when {
            errorMsg.contains("API Key 无效") -> 
                application.getString(R.string.error_api_key_invalid)
            errorMsg.contains("未设置 API Key") -> 
                application.getString(R.string.error_api_key_not_set)
            errorMsg.contains("权限不足") -> 
                application.getString(R.string.error_api_key_permission)
            errorMsg.contains("请求过于频繁") -> 
                application.getString(R.string.error_rate_limit)
            errorMsg.contains("无法连接") || errorMsg.contains("网络") -> 
                application.getString(R.string.error_network)
            errorMsg.contains("超时") -> 
                application.getString(R.string.error_timeout)
            errorMsg.contains("未能生成有效的子任务") -> 
                "AI 无法为该任务生成有效的子任务。请尝试：\n• 提供更详细的任务描述\n• 使用长按详细输入模式\n• 稍后重试"
            errorMsg.contains("生成的子任务数量不足") -> 
                "任务可能过于简单或描述不够详细。建议：\n• 确认任务是否需要拆解\n• 提供更多任务背景信息\n• 使用详细输入模式"
            errorMsg.contains("响应格式错误") || errorMsg.contains("响应格式异常") -> 
                "AI 响应格式异常，请稍后重试"
            errorMsg.contains("API 错误") -> 
                "AI 服务出现问题，请稍后重试"
            errorMsg.contains("当前 AI 提供商不支持") -> 
                "当前 AI 提供商不支持子任务生成功能"
            else -> errorMsg.ifEmpty { "AI 分析失败，请稍后重试" }
        }
    }
    
    /**
     * 显示子任务详细输入对话框
     */
    fun showSubTaskInputDialog(task: Task) {
        _uiState.value = _uiState.value.copy(
            showSubTaskInput = true,
            currentTask = task
        )
    }
    
    /**
     * 关闭子任务输入对话框
     */
    fun dismissSubTaskInput() {
        _uiState.value = _uiState.value.copy(showSubTaskInput = false, currentTask = null)
    }
    
    /**
     * 确认添加生成的子任务
     */
    fun confirmAddSubTasks(subTasks: List<String>) {
        val currentTask = _uiState.value.currentTask
        
        if (currentTask != null && subTasks.isNotEmpty()) {
            viewModelScope.launch {
                subTasks.forEach { content ->
                    taskRepository.insertSubTask(
                        SubTask(
                            taskId = currentTask.id,
                            content = content,
                            isCompleted = false
                        )
                    )
                }
            }
        }
        
        _uiState.value = _uiState.value.copy(
            showSubTaskResult = false,
            generatedSubTasks = emptyList(),
            currentTask = null
        )
    }
    
    /**
     * 取消子任务生成结果
     */
    fun dismissSubTaskResult() {
        _uiState.value = _uiState.value.copy(
            showSubTaskResult = false,
            generatedSubTasks = emptyList(),
            currentTask = null
        )
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
    val recordingState: com.litetask.app.util.RecordingState = com.litetask.app.util.RecordingState.IDLE,
    
    // 子任务生成相关
    val showSubTaskInput: Boolean = false,  // 显示子任务详细输入对话框
    val showSubTaskResult: Boolean = false, // 显示子任务生成结果
    val generatedSubTasks: List<String> = emptyList(), // 生成的子任务列表
    val currentTask: Task? = null           // 当前处理的任务
)

// 检查 API Key 结果
sealed class ApiKeyCheckResult {
    object Valid : ApiKeyCheckResult()
    object NotConfigured : ApiKeyCheckResult()
}