package com.litetask.app.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.SubTask
import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.data.model.sortedByOrder
import com.litetask.app.data.ai.SubTaskGenerationMode
import com.litetask.app.data.ai.SubTaskGenerationSupport
import com.litetask.app.data.repository.TaskRepositoryImpl
import com.litetask.app.data.repository.AIRepository
import com.litetask.app.data.model.Category
import com.litetask.app.data.model.TaskComponent
import com.litetask.app.data.model.toEntity
import com.litetask.app.data.repository.CategoryRepository
import com.litetask.app.widget.WidgetUpdateHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlinx.coroutines.flow.update
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
    private val categoryRepository: CategoryRepository,
    private val aiHistoryRepository: com.litetask.app.data.repository.AIHistoryRepository,
    private val speechHelper: com.litetask.app.util.SpeechRecognizerHelper,
    private val preferenceManager: com.litetask.app.data.local.PreferenceManager,
    private val aMapRepository: com.litetask.app.data.repository.AMapRepository,
    private val locationTracker: com.litetask.app.data.repository.LocationTracker,
    private val scheduleAdviceDao: com.litetask.app.data.local.DailyScheduleAdviceDao,
    private val scheduleAdviceAssistant: com.litetask.app.data.ai.DailyScheduleAdviceAssistant,
    private val focusTimerManager: com.litetask.app.util.FocusTimerManager
) : ViewModel() {

    val focusTimerState: StateFlow<com.litetask.app.util.FocusTimerState> = focusTimerManager.timerState

    fun openFocusSheet(task: Task? = null) {
        _uiState.update { it.copy(showFocusSheet = true, focusTargetTask = task) }
    }

    fun dismissFocusSheet() {
        _uiState.update { it.copy(showFocusSheet = false) }
    }

    fun startFocus(task: Task?, mode: com.litetask.app.util.FocusMode, plannedMinutes: Int) {
        focusTimerManager.start(task, mode, plannedMinutes)
    }

    fun restartFocus(mode: com.litetask.app.util.FocusMode, plannedMinutes: Int) {
        focusTimerManager.restartCurrent(mode, plannedMinutes)
    }

    fun pauseFocus() = focusTimerManager.pause()

    fun resumeFocus() = focusTimerManager.resume()

    fun finishFocusAndSave() = focusTimerManager.finishAndSave()

    fun abandonFocus() = focusTimerManager.abandon()

    fun resetFocusToIdle() = focusTimerManager.resetToIdle()

    // ==================== 数据加载配置 ====================
    
    companion object {
        /** 历史任务每页加载数量 */
        private const val PAGE_SIZE = 20
        private const val STALE_ADVICE_CUTOFF_MS = 5 * 60_000L
    }

    private var scheduleAdviceJob: Job? = null

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

            // 清理因异常中断遗留的生成中状态记录，并加载最近一次成功的日程建议
            scheduleAdviceDao.failStaleGenerating(System.currentTimeMillis() - STALE_ADVICE_CUTOFF_MS)
            val latestAdvice = scheduleAdviceDao.getLatestCompleted()
            _uiState.update {
                it.copy(
                    currentAdvice = latestAdvice,
                    scheduleAdviceError = null
                )
            }

            // 懒更新 - 触发每日智能规划建议
            triggerDailyAnalysisIfNeeded()
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

    /** 所有分类列表 */
    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    // ==================== 高德地图配置 ====================
    
    val amapKey: StateFlow<String?> = preferenceManager.amapKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), preferenceManager.getAMapKey())

    fun updateAMapKey(key: String) {
        preferenceManager.saveAMapKey(key)
    }
    
    /**
     * 简单地理编码实现 (IO线程)
     */
    suspend fun geocodeLocation(address: String): com.litetask.app.data.model.AMapRouteData? {
        return aMapRepository.geocodeLocation(address)
    }

    /**
     * 搜索地点建议 (IO线程)
     */
    suspend fun searchLocations(keyword: String): List<com.litetask.app.data.model.AMapRouteData> {
        return aMapRepository.searchLocations(keyword)
    }
    
    /**
     * 获取实况天气 (IO线程)
     */
    suspend fun getWeatherForAdcode(adcode: String): Pair<String, String>? {
        return aMapRepository.fetchWeather(adcode)
    }

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
    
    private suspend fun triggerDailyAnalysisIfNeeded() {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val existing = scheduleAdviceDao.getLatestCompleted()
        if (existing != null && existing.createdAt >= todayStart) return

        launchDailyScheduleAdviceGeneration(force = false)
    }

    fun triggerDailyAnalysis() {
        launchDailyScheduleAdviceGeneration(force = true)
    }

    private fun launchDailyScheduleAdviceGeneration(force: Boolean) {
        val previousJob = scheduleAdviceJob
        if (previousJob?.isActive == true && !force) return

        scheduleAdviceJob = viewModelScope.launch {
            if (force) {
                previousJob?.cancelAndJoin()
            }
            withContext(Dispatchers.IO) {
                generateDailyScheduleAdvice()
            }
        }
    }

    private suspend fun generateDailyScheduleAdvice() {
        var placeholderId = 0L
        val fallbackAdvice = _uiState.value.currentAdvice ?: scheduleAdviceDao.getLatestCompleted()
        try {
            placeholderId = scheduleAdviceDao.insert(
                com.litetask.app.data.model.DailyScheduleAdviceEntity(
                    shortTermJson = "{}", longTermJson = "{}", generationStatus = "GENERATING"
                )
            )
            _uiState.update {
                it.copy(
                    isGeneratingScheduleAdvice = true,
                    scheduleAdviceError = null,
                    scheduleAdviceLogs = emptyList()
                )
            }

            val isAgent = preferenceManager.isAiAgentEnabled()
            val result = if (isAgent) {
                scheduleAdviceAssistant.generateAdvice { msg ->
                    _uiState.update { state ->
                        state.copy(scheduleAdviceLogs = state.scheduleAdviceLogs + msg)
                    }
                }
            } else {
                scheduleAdviceAssistant.generateDirectAdvice { msg ->
                    _uiState.update { state ->
                        state.copy(scheduleAdviceLogs = state.scheduleAdviceLogs + msg)
                    }
                }
            }

            result.fold(
                onSuccess = { entity ->
                    val updated = entity.copy(id = placeholderId, isRead = false)
                    scheduleAdviceDao.insert(updated)
                    _uiState.update {
                        it.copy(
                            isGeneratingScheduleAdvice = false,
                            currentAdvice = updated,
                            scheduleAdviceError = null
                        )
                    }
                },
                onFailure = { e ->
                    if (placeholderId > 0) {
                        scheduleAdviceDao.updateGenerationStatus(placeholderId, "FAILED")
                    }
                    val errorMsg = e.message ?: "生成失败"
                    _uiState.update {
                        it.copy(
                            isGeneratingScheduleAdvice = false,
                            currentAdvice = it.currentAdvice ?: fallbackAdvice,
                            scheduleAdviceError = errorMsg,
                            scheduleAdviceLogs = it.scheduleAdviceLogs + "生成失败: $errorMsg"
                        )
                    }
                }
            )
        } catch (e: CancellationException) {
            if (placeholderId > 0) {
                withContext(NonCancellable) {
                    try { scheduleAdviceDao.updateGenerationStatus(placeholderId, "FAILED") } catch (_: Exception) {}
                }
            }
            throw e
        } catch (e: Exception) {
            if (placeholderId > 0) {
                withContext(NonCancellable) {
                    try { scheduleAdviceDao.updateGenerationStatus(placeholderId, "FAILED") } catch (_: Exception) {}
                }
            }
            val errorMsg = e.message ?: "未知错误"
            _uiState.update {
                it.copy(
                    isGeneratingScheduleAdvice = false,
                    currentAdvice = it.currentAdvice ?: fallbackAdvice,
                    scheduleAdviceError = errorMsg,
                    scheduleAdviceLogs = it.scheduleAdviceLogs + "生成失败: $errorMsg"
                )
            }
        }
    }

    fun toggleScheduleAdviceSheet(show: Boolean) {
        _uiState.update { it.copy(showScheduleAdviceSheet = show) }
        if (show) {
            viewModelScope.launch {
                val advice = _uiState.value.currentAdvice
                if (advice != null && !advice.isRead) {
                    scheduleAdviceDao.markAsRead(advice.id)
                    _uiState.update {
                        if (it.currentAdvice?.id == advice.id) {
                            it.copy(currentAdvice = advice.copy(isRead = true))
                        } else it
                    }
                }
            }
        }
    }

    fun loadLatestScheduleAdvice() {
        viewModelScope.launch(Dispatchers.IO) {
            val advice = scheduleAdviceDao.getLatestCompleted()
            _uiState.update { it.copy(currentAdvice = advice) }
        }
    }

    fun refreshScheduleAdvice() {
        launchDailyScheduleAdviceGeneration(force = true)
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
        return if (preferenceManager.getAiConfigError() != null) {
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
     * 获取甘特视图默认时间粒度
     */
    fun getGanttDefaultMode(): String = preferenceManager.getGanttDefaultMode()
    
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
            _uiState.value = _uiState.value.copy(
                isAnalyzing = true,
                agentStatus = "正在理解您的语音...",
                agentLogs = listOf("语音识别完成: $text")
            )
            
            val result = aiRepository.parseTasksFromText("", text) { progress ->
                _uiState.value = _uiState.value.copy(
                    agentStatus = progress,
                    agentLogs = _uiState.value.agentLogs + progress
                )
            }
            
            result.onSuccess { tasks ->
                val tasksWithLocations = mutableMapOf<Int, List<com.litetask.app.data.model.TaskComponent>>()
                
                // 检查是否开启了 AI 目的地识别
                if (preferenceManager.isAiDestinationEnabled()) {
                    val amapKey = preferenceManager.getAMapKey()
                    if (!amapKey.isNullOrBlank()) {
                        tasks.forEachIndexed { index, task ->
                            val destination = task.parsedDestination
                            if (!destination.isNullOrBlank()) {
                                try {
                                    // 搜索目的地
                                    val results = aMapRepository.searchLocations(destination)
                                    if (results.isNotEmpty()) {
                                        // 取第一个结果
                                        val firstResult = results[0]
                                        // 创建组件
                                        val component = com.litetask.app.data.model.TaskComponent.AMapComponent(
                                            id = -(System.currentTimeMillis() + index), // 临时 ID
                                            taskId = 0,
                                            data = firstResult
                                        )
                                        tasksWithLocations[index] = listOf(component)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    // 忽略单个搜索错误，不影响整体流程
                                }
                            }
                        }
                    }
                }

                // 记录 AI 历史
                recordAiHistory(
                    content = text,
                    sourceType = com.litetask.app.data.model.AIHistorySource.VOICE,
                    parsedCount = tasks.size,
                    isSuccess = true
                )
                
                // 无论是否有任务，都显示 TaskConfirmationSheet（空结果会在界面中显示提示）
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    showAiResult = true,
                    aiParsedTasks = tasks,
                    aiParsedComponents = tasksWithLocations,
                    recordingState = com.litetask.app.util.RecordingState.IDLE
                )
            }.onFailure { error ->
                val errorMessage = parseAiError(error.message ?: "")
                recordAiHistory(
                    content = text,
                    sourceType = com.litetask.app.data.model.AIHistorySource.VOICE,
                    parsedCount = 0,
                    isSuccess = false,
                    errorMessage = errorMessage
                )
                
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
    fun analyzeTextInput(text: String, imageUris: List<android.net.Uri> = emptyList()) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAnalyzing = true,
                agentStatus = "正在准备分析...",
                agentLogs = listOf("文本输入: $text" + (if (imageUris.isNotEmpty()) " (含${imageUris.size}张图片)" else ""))
            )
            
            val imageBases64 = imageUris.mapNotNull { uriToBase64(application, it) }.takeIf { it.isNotEmpty() }
            
            val result = aiRepository.parseTasksFromText("", text, imageBases64) { progress ->
                _uiState.value = _uiState.value.copy(
                    agentStatus = progress,
                    agentLogs = _uiState.value.agentLogs + progress
                )
            }
                        result.onSuccess { tasks ->
                    // 目的地解析逻辑
                    val tasksWithLocations = mutableMapOf<Int, List<com.litetask.app.data.model.TaskComponent>>()
                    
                    if (preferenceManager.isAiDestinationEnabled()) {
                        val amapKey = preferenceManager.getAMapKey()
                        if (!amapKey.isNullOrBlank()) {
                            // 实际执行搜索
                            tasks.forEachIndexed { index, task ->
                                val destination = task.parsedDestination
                                if (!destination.isNullOrBlank()) {
                                    try {
                                        val results = aMapRepository.searchLocations(destination)
                                        if (results.isNotEmpty()) {
                                            val firstResult = results[0]
                                            val component = com.litetask.app.data.model.TaskComponent.AMapComponent(
                                                id = -(System.currentTimeMillis() + index * 10), 
                                                taskId = 0,
                                                data = firstResult
                                            )
                                            tasksWithLocations[index] = listOf(component)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    }

                    // 记录成功历史
                    recordAiHistory(
                        content = text,
                        sourceType = com.litetask.app.data.model.AIHistorySource.TEXT,
                        parsedCount = tasks.size,
                        isSuccess = true,
                        imageCount = imageUris.size
                    )

                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        showAiResult = true,
                        aiParsedTasks = tasks,
                        aiParsedComponents = tasksWithLocations,
                        agentStatus = "",
                        agentLogs = emptyList()
                    )
                }.onFailure { error ->
                val errorMessage = parseAiError(error.message ?: "")
                recordAiHistory(
                    content = text,
                    sourceType = com.litetask.app.data.model.AIHistorySource.TEXT,
                    parsedCount = 0,
                    isSuccess = false,
                    imageCount = imageUris.size,
                    errorMessage = errorMessage
                )
                
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
                viewModelScope.launch(Dispatchers.IO) {
                    locationTracker.commitPendingOrigin()
                    locationTracker.saveDestinationFromTasks(tasksToAdd)
                }
            }
            _uiState.value = _uiState.value.copy(showAiResult = false, aiParsedTasks = emptyList())
        }
    }
    
    /**
     * 确认添加任务（带提醒）
     */
    fun confirmAddTasksWithReminders(
        tasks: List<Task>, 
        taskReminders: Map<Int, List<com.litetask.app.data.model.ReminderConfig>>,
        taskComponents: Map<Int, List<com.litetask.app.data.model.TaskComponent>>
    ) {
        viewModelScope.launch {
            if (tasks.isNotEmpty()) {
                tasks.forEachIndexed { index, task ->
                    val reminderConfigs = taskReminders[index] ?: emptyList()
                    val components = taskComponents[index] ?: emptyList()
                    
                    // 1. 计算提醒
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
                    
                    // 2. 插入或更新任务和提醒
                    val finalTaskId = if (task.id != 0L) {
                        if (taskReminders.containsKey(index) && !task.isDone) {
                            taskRepository.updateTaskWithReminders(task, reminders)
                        } else {
                            // Agent 修改已有任务但未在确认面板中显式调整提醒时，保留原有提醒不被清空
                            taskRepository.updateTask(task)
                        }
                        cleanupTaskFromAdditionalHistory(task.id)
                        task.id
                    } else {
                        taskRepository.insertTaskWithReminders(task, reminders)
                    }
                    
                    // 3. 插入组件（更新已有任务时按 type + payload 去重，避免重复追加）
                    val existingKeys = if (task.id != 0L) {
                        taskRepository.getComponentsByTaskId(finalTaskId)
                            .first()
                            .map { it.type to it.dataJson }
                            .toMutableSet()
                    } else {
                        mutableSetOf()
                    }
                    components.forEach { component ->
                        val entity = when (component) {
                            is com.litetask.app.data.model.TaskComponent.AMapComponent -> 
                                component.toEntity(finalTaskId)
                            is com.litetask.app.data.model.TaskComponent.FileComponent -> 
                                component.toEntity(finalTaskId)
                        }
                        if (existingKeys.add(entity.type to entity.dataJson)) {
                            taskRepository.insertComponent(entity)
                        }
                    }
                }
            }
            _uiState.value = _uiState.value.copy(showAiResult = false, aiParsedTasks = emptyList(), aiParsedComponents = emptyMap())
            WidgetUpdateHelper.refreshAllWidgets(application)
            if (tasks.isNotEmpty()) {
                viewModelScope.launch(Dispatchers.IO) {
                    locationTracker.commitPendingOrigin()
                    tasks.forEachIndexed { index, task ->
                        val amapComps = taskComponents[index]
                            ?.filterIsInstance<TaskComponent.AMapComponent>()
                            .orEmpty()
                        if (amapComps.isNotEmpty()) {
                            locationTracker.saveDestinationsFromComponents(amapComps)
                        } else {
                            locationTracker.saveDestinationFromTasks(listOf(task))
                        }
                    }
                }
            }
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
            val reindexedComponents = _uiState.value.aiParsedComponents.entries
                .mapNotNull { (k, v) ->
                    when {
                        k < index -> k to v
                        k > index -> (k - 1) to v
                        else -> null
                    }
                }
                .toMap()
            _uiState.value = _uiState.value.copy(
                aiParsedTasks = currentTasks,
                aiParsedComponents = reindexedComponents
            )
        }
    }

    // ==================== 任务 CRUD 操作 ====================
    
    fun addTask(task: Task) {
        viewModelScope.launch { 
            taskRepository.insertTask(task)
            WidgetUpdateHelper.refreshAllWidgets(application)
        }
    }
    
    /** 添加任务（带组件和提醒） */
    fun addTaskWithComponents(
        task: Task, 
        reminderConfigs: List<com.litetask.app.data.model.ReminderConfig>,
        components: List<com.litetask.app.data.model.TaskComponent>
    ) {
        viewModelScope.launch {
            // 1. 插入任务并获取 ID (Repository 需要修改以返回新 ID，目前暂不支持，先用 work-around)
            // 由于 Room 的 insertTask 返回 Row ID，我们需要 Repository 返回它
            // 临时方案：先插入任务，再查询最新插入的任务 ID (存在并发风险，单人使用尚可)
            
            // 更稳健的方案：修改 TaskRepository.insertTaskWithReminders 返回 taskId
            // 这里假设我们稍后会修改 Repository，先写逻辑
            
            // 计算提醒
            val reminders = reminderConfigs.mapNotNull { config ->
                val triggerAt = config.calculateTriggerTime(task.startTime, task.deadline)
                if (triggerAt > 0 && config.type != com.litetask.app.data.model.ReminderType.NONE) {
                    com.litetask.app.data.model.Reminder(taskId = 0, triggerAt = triggerAt, label = config.generateLabel())
                } else null
            }
            
            // 插入任务和提醒
            val newTaskId = taskRepository.insertTaskWithRemindersAndReturnId(task, reminders)
            
            // 2. 插入组件
            components.forEach { component ->
                val entity = when (component) {
                    is com.litetask.app.data.model.TaskComponent.AMapComponent -> 
                        component.toEntity(newTaskId)
                    is com.litetask.app.data.model.TaskComponent.FileComponent -> 
                        component.toEntity(newTaskId)
                }
                taskRepository.insertComponent(entity)
            }
            
            WidgetUpdateHelper.refreshAllWidgets(application)
            if (components.any { it is com.litetask.app.data.model.TaskComponent.AMapComponent }) {
                viewModelScope.launch(Dispatchers.IO) {
                    locationTracker.saveDestinationsFromComponents(components)
                }
            }
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
    
    /** 更新任务并更新提醒（保留已有组件不被清空） */
    fun updateTaskWithReminders(task: Task, reminderConfigs: List<com.litetask.app.data.model.ReminderConfig>) {
        viewModelScope.launch {
            if (task.isDone) {
                taskRepository.updateTask(task)
                WidgetUpdateHelper.refreshAllWidgets(application)
                return@launch
            }
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
            cleanupTaskFromAdditionalHistory(task.id)
            WidgetUpdateHelper.refreshAllWidgets(application)
        }
    }

    /** 更新任务（带组件和提醒） */
    fun updateTaskWithComponents(
        task: Task, 
        reminderConfigs: List<com.litetask.app.data.model.ReminderConfig>,
        components: List<com.litetask.app.data.model.TaskComponent>
    ) {
        viewModelScope.launch {
            // 对于已完成任务，只更新任务信息，不处理提醒（已完成任务不需要提醒）
            if (task.isDone) {
                taskRepository.updateTask(task)
                // 更新组件 (即使已完成也允许编辑组件)
                updateComponentsForTask(task.id, components)
                
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
            
            // 更新组件
            updateComponentsForTask(task.id, components)
            
            cleanupTaskFromAdditionalHistory(task.id)
            WidgetUpdateHelper.refreshAllWidgets(application)
        }
    }
    
    private suspend fun updateComponentsForTask(taskId: Long, newComponents: List<com.litetask.app.data.model.TaskComponent>) {
        // 1. 获取现有组件
        val oldComponents = taskRepository.getComponentsByTaskId(taskId).first()
        val oldRouteKeys = oldComponents
            .filterIsInstance<com.litetask.app.data.model.TaskComponent.AMapComponent>()
            .map { Triple(it.data.endName, it.data.endLat, it.data.endLng) }
            .toSet()
        
        // 2. 删除所有现有组件
        oldComponents.forEach { taskRepository.deleteComponent(it) }
        
        // 3. 插入新组件
        newComponents.forEach { component ->
             val entity = when (component) {
                is com.litetask.app.data.model.TaskComponent.AMapComponent -> 
                    component.toEntity(taskId)
                is com.litetask.app.data.model.TaskComponent.FileComponent -> 
                    component.toEntity(taskId)
            }
            taskRepository.insertComponent(entity)
        }

        // 4. 仅针对新增或变更的高德目的地组件同步写入足迹（覆盖编辑任务及采纳建议路线）
        val addedOrChangedRoutes = newComponents
            .filterIsInstance<com.litetask.app.data.model.TaskComponent.AMapComponent>()
            .filter { Triple(it.data.endName, it.data.endLat, it.data.endLng) !in oldRouteKeys }
        if (addedOrChangedRoutes.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                locationTracker.saveDestinationsFromComponents(addedOrChangedRoutes)
            }
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
            taskRepository.addSubTask(taskId, content)
        }
    }
    
    fun updateSubTaskStatus(subTaskId: Long, completed: Boolean) {
        viewModelScope.launch { taskRepository.updateSubTaskStatus(subTaskId, completed) }
    }

    fun updateSubTaskContent(taskId: Long, subTaskId: Long, content: String) {
        viewModelScope.launch {
            taskRepository.updateSubTaskContent(taskId, subTaskId, content)
        }
    }

    fun reorderSubTasks(taskId: Long, orderedSubTasks: List<SubTask>) {
        viewModelScope.launch {
            taskRepository.reorderSubTasks(taskId, orderedSubTasks)
        }
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
        locationTracker.clearPendingOrigin()
        _uiState.value = _uiState.value.copy(showAiResult = false, aiParsedTasks = emptyList(), aiParsedComponents = emptyMap())
    }
    
    // ==================== AI 子任务生成 ====================

    /**
     * 生成子任务（支持补充上下文、模式切换与多模态图片）
     */
    fun generateSubTasksWithContext(
        task: Task,
        additionalContext: String = "",
        mode: SubTaskGenerationMode = SubTaskGenerationMode.APPEND_NEXT,
        imageUris: List<android.net.Uri> = emptyList()
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAnalyzing = true,
                agentStatus = "正在准备任务上下文...",
                agentLogs = listOf(
                    "主任务: ${task.title}" +
                        (if (imageUris.isNotEmpty()) " (含${imageUris.size}张参考图片)" else "")
                ),
                subTaskGenerationMode = mode,
                subTaskRefinementError = null
            )

            val configError = preferenceManager.getAiConfigError()
            if (configError != null) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    showAiError = true,
                    aiErrorMessage = configError,
                    showSubTaskInput = false
                )
                return@launch
            }

            try {
                val imageBases64 = imageUris.mapNotNull { uriToBase64(application, it) }.takeIf { it.isNotEmpty() }

                val result = aiRepository.generateSubTasks(
                    task = task,
                    additionalContext = additionalContext,
                    imageBases64 = imageBases64,
                    mode = mode
                ) { progress ->
                    _uiState.value = _uiState.value.copy(
                        agentStatus = progress,
                        agentLogs = _uiState.value.agentLogs + progress
                    )
                }

                result.onSuccess { subTasks ->
                    recordAiHistory(
                        content = SubTaskGenerationSupport.buildSubTaskHistorySummary(
                            mode = mode,
                            additionalContext = additionalContext,
                            refinementFeedback = "",
                            generatedSteps = subTasks
                        ),
                        sourceType = com.litetask.app.data.model.AIHistorySource.SUBTASK,
                        parsedCount = subTasks.size,
                        isSuccess = true,
                        targetTitle = task.title,
                        imageCount = imageUris.size
                    )

                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        showSubTaskResult = true,
                        generatedSubTasks = subTasks,
                        currentTask = task,
                        showSubTaskInput = false,
                        subTaskGenerationMode = mode,
                        lastSubTaskAdditionalContext = additionalContext,
                        lastSubTaskImageBases64 = imageBases64,
                        isRefiningSubTasks = false,
                        subTaskRefinementError = null
                    )
                }.onFailure { error ->
                    val errorMessage = parseAiError(error.message ?: "")
                    recordAiHistory(
                        content = additionalContext,
                        sourceType = com.litetask.app.data.model.AIHistorySource.SUBTASK,
                        parsedCount = 0,
                        isSuccess = false,
                        targetTitle = task.title,
                        imageCount = imageUris.size,
                        errorMessage = errorMessage
                    )
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        showAiError = true,
                        aiErrorMessage = errorMessage,
                        showSubTaskInput = false
                    )
                }
            } catch (e: Exception) {
                val errorMessage = parseAiError(e.message ?: "")
                recordAiHistory(
                    content = additionalContext,
                    sourceType = com.litetask.app.data.model.AIHistorySource.SUBTASK,
                    parsedCount = 0,
                    isSuccess = false,
                    targetTitle = task.title,
                    imageCount = imageUris.size,
                    errorMessage = errorMessage
                )
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    showAiError = true,
                    aiErrorMessage = "子任务生成失败: ${e.message ?: errorMessage}",
                    showSubTaskInput = false
                )
            }
        }
    }

    /**
     * 在结果确认弹窗内按微调意见重新生成一版子任务（保持上下文与图片不丢失）
     */
    fun refineGeneratedSubTasks(currentDraftSteps: List<String>, feedback: String) {
        val state = _uiState.value
        val task = state.currentTask ?: return
        if (state.isRefiningSubTasks) return
        val trimmedFeedback = feedback.trim().ifEmpty { "请重新生成一版视角更清晰的执行步骤" }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRefiningSubTasks = true,
                subTaskRefinementError = null
            )
            try {
                val result = aiRepository.generateSubTasks(
                    task = task,
                    additionalContext = state.lastSubTaskAdditionalContext,
                    imageBases64 = state.lastSubTaskImageBases64,
                    mode = state.subTaskGenerationMode,
                    previousGeneratedSteps = currentDraftSteps,
                    refinementFeedback = trimmedFeedback
                )
                result.onSuccess { refinedSteps ->
                    recordAiHistory(
                        content = SubTaskGenerationSupport.buildSubTaskHistorySummary(
                            mode = state.subTaskGenerationMode,
                            additionalContext = state.lastSubTaskAdditionalContext,
                            refinementFeedback = trimmedFeedback,
                            generatedSteps = refinedSteps
                        ),
                        sourceType = com.litetask.app.data.model.AIHistorySource.SUBTASK,
                        parsedCount = refinedSteps.size,
                        isSuccess = true,
                        targetTitle = task.title,
                        imageCount = state.lastSubTaskImageBases64?.size ?: 0
                    )
                    _uiState.value = _uiState.value.copy(
                        isRefiningSubTasks = false,
                        generatedSubTasks = refinedSteps,
                        subTaskRefinementError = null
                    )
                }.onFailure { error ->
                    val errMsg = parseAiError(error.message ?: "")
                    recordAiHistory(
                        content = trimmedFeedback,
                        sourceType = com.litetask.app.data.model.AIHistorySource.SUBTASK,
                        parsedCount = 0,
                        isSuccess = false,
                        targetTitle = task.title,
                        imageCount = state.lastSubTaskImageBases64?.size ?: 0,
                        errorMessage = errMsg
                    )
                    _uiState.value = _uiState.value.copy(
                        isRefiningSubTasks = false,
                        subTaskRefinementError = errMsg
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefiningSubTasks = false,
                    subTaskRefinementError = parseAiError(e.message ?: "")
                )
            }
        }
    }
    
    /**
     * 统一记录 AI 解析与拆解历史，保持用户原始输入纯净并保留结构化上下文
     */
    private fun recordAiHistory(
        content: String,
        sourceType: com.litetask.app.data.model.AIHistorySource,
        parsedCount: Int,
        isSuccess: Boolean,
        targetTitle: String? = null,
        imageCount: Int = 0,
        errorMessage: String? = null
    ) {
        viewModelScope.launch {
            aiHistoryRepository.insertHistory(
                com.litetask.app.data.model.AIHistory(
                    content = content.trim(),
                    sourceType = sourceType,
                    parsedCount = parsedCount.coerceAtLeast(0),
                    isSuccess = isSuccess,
                    targetTitle = targetTitle?.trim()?.takeIf { it.isNotEmpty() },
                    imageCount = imageCount.coerceAtLeast(0),
                    errorMessage = errorMessage?.trim()?.takeIf { it.isNotEmpty() }
                )
            )
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
            else -> errorMsg.ifEmpty { application.getString(R.string.error_ai_analysis) }
        }
    }
    
    /**
     * 显示子任务详细输入对话框，并异步同步该任务的已有子任务用于展示和模式切换
     */
    fun showSubTaskInputDialog(task: Task) {
        _uiState.value = _uiState.value.copy(
            showSubTaskInput = true,
            currentTask = task,
            currentTaskSubTasks = emptyList(),
            subTaskGenerationMode = SubTaskGenerationMode.APPEND_NEXT,
            subTaskRefinementError = null
        )
        viewModelScope.launch {
            val detail = taskRepository.getTaskByIdSync(task.id)
            if (_uiState.value.currentTask?.id == task.id) {
                _uiState.value = _uiState.value.copy(
                    currentTaskSubTasks = detail?.subTasks?.sortedByOrder().orEmpty()
                )
            }
        }
    }
    
    /**
     * 关闭子任务输入对话框
     */
    fun dismissSubTaskInput() {
        _uiState.value = _uiState.value.copy(
            showSubTaskInput = false,
            currentTask = null,
            currentTaskSubTasks = emptyList(),
            lastSubTaskAdditionalContext = "",
            lastSubTaskImageBases64 = null
        )
    }
    
    /**
     * 确认添加生成的子任务
     */
    fun confirmAddSubTasks(subTasks: List<String>, replaceUncompleted: Boolean = false) {
        val currentTask = _uiState.value.currentTask
        
        if (currentTask != null && subTasks.isNotEmpty()) {
            viewModelScope.launch {
                taskRepository.insertSubTasksBatch(
                    taskId = currentTask.id,
                    contents = subTasks,
                    replaceUncompleted = replaceUncompleted
                )
            }
        }
        
        _uiState.value = _uiState.value.copy(
            showSubTaskResult = false,
            generatedSubTasks = emptyList(),
            currentTask = null,
            currentTaskSubTasks = emptyList(),
            lastSubTaskAdditionalContext = "",
            lastSubTaskImageBases64 = null,
            isRefiningSubTasks = false,
            subTaskRefinementError = null
        )
    }
    
    fun dismissSubTaskResult() {
        _uiState.value = _uiState.value.copy(
            showSubTaskResult = false,
            generatedSubTasks = emptyList(),
            currentTask = null,
            currentTaskSubTasks = emptyList(),
            lastSubTaskAdditionalContext = "",
            lastSubTaskImageBases64 = null,
            isRefiningSubTasks = false,
            subTaskRefinementError = null
        )
    }

    suspend fun getComponentsForTaskSync(taskId: Long): List<com.litetask.app.data.model.TaskComponent> {
        return taskRepository.getComponentsByTaskId(taskId).map { list -> list.map { it.toTaskComponent() } }.first()
    }

    /**
     * 删除任务组件
     */
    fun deleteComponent(component: com.litetask.app.data.model.TaskComponent) {
        viewModelScope.launch {
            taskRepository.deleteComponent(component.toEntity(component.taskId))
        }
    }

    /**
     * 判断当前模型是否支持多模态（图片）输入
     */
    fun isMultimodalModel(): Boolean {
        return preferenceManager.isMultimodalEnabled()
    }

    /**
     * 将图片 URI 转为 Base64 并进行采样与压缩（IO 线程执行，限制在约 1MB~5MB 内）
     */
    suspend fun uriToBase64(context: android.content.Context, uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        try {
            val boundsOptions = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return@withContext null
            }

            val maxDimension = 1600
            var sampleSize = 1
            while (
                boundsOptions.outWidth / sampleSize > maxDimension ||
                boundsOptions.outHeight / sampleSize > maxDimension
            ) {
                sampleSize *= 2
            }

            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            try {
                val out = java.io.ByteArrayOutputStream()
                val targetBytes = 1024 * 1024
                val maxBytes = 5 * 1024 * 1024
                val qualities = intArrayOf(85, 70, 55, 40)
                var compressed: ByteArray? = null
                for (q in qualities) {
                    out.reset()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, q, out)
                    val candidate = out.toByteArray()
                    compressed = candidate
                    if (candidate.size <= targetBytes) break
                }
                val finalBytes = compressed?.takeIf { it.size <= maxBytes } ?: return@withContext null
                android.util.Base64.encodeToString(finalBytes, android.util.Base64.NO_WRAP)
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
    val aiParsedComponents: Map<Int, List<com.litetask.app.data.model.TaskComponent>> = emptyMap(), // AI 解析生成的组件（如地图）
    val recognizedText: String = "",        // 实时识别的文字
    val finalRecognizedText: String = "",   // 最终识别结果
    val recordingState: com.litetask.app.util.RecordingState = com.litetask.app.util.RecordingState.IDLE,
    
    // 子任务生成相关
    val showSubTaskInput: Boolean = false,  // 显示子任务详细输入对话框
    val showSubTaskResult: Boolean = false, // 显示子任务生成结果
    val generatedSubTasks: List<String> = emptyList(), // 生成的子任务列表
    val currentTask: Task? = null,          // 当前处理的任务
    val currentTaskSubTasks: List<SubTask> = emptyList(), // 当前任务已有子任务（用于模式与去重对比）
    val subTaskGenerationMode: SubTaskGenerationMode = SubTaskGenerationMode.APPEND_NEXT,
    val lastSubTaskAdditionalContext: String = "",
    val lastSubTaskImageBases64: List<String>? = null,
    val isRefiningSubTasks: Boolean = false,
    val subTaskRefinementError: String? = null,

    // Agent 思考过程相关
    val agentStatus: String = "",           // 当前 Agent 正在执行的操作
    val agentLogs: List<String> = emptyList(), // Agent 历史操作日志
    
    // 日程建议相关
    val isGeneratingScheduleAdvice: Boolean = false,
    val currentAdvice: com.litetask.app.data.model.DailyScheduleAdviceEntity? = null,
    val scheduleAdviceLogs: List<String> = emptyList(),
    val scheduleAdviceError: String? = null,
    val showScheduleAdviceSheet: Boolean = false,

    // 专注计时弹窗状态
    val showFocusSheet: Boolean = false,
    val focusTargetTask: Task? = null
)

// 检查 API Key 结果
sealed class ApiKeyCheckResult {
    object Valid : ApiKeyCheckResult()
    object NotConfigured : ApiKeyCheckResult()
}