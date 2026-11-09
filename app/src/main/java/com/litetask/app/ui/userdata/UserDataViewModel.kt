package com.litetask.app.ui.userdata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litetask.app.data.local.FocusSessionDao
import com.litetask.app.data.local.TaskDao
import com.litetask.app.data.local.UserProfileDao
import com.litetask.app.data.model.FocusSessionEntity
import com.litetask.app.data.model.UserProfileHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.litetask.app.data.model.UserLocationEntity

/**
 * 分类统计数据
 */
data class CategoryStats(
    val categoryId: Long,
    val categoryName: String,
    val categoryColor: String,
    val taskCount: Int,
    val percentage: Float
)

/**
 * 每日完成趋势数据点
 */
data class DailyCompletionData(
    val date: String,  // 格式：MM/dd
    val completedCount: Int
)

/**
 * 地点统计数据
 */
data class LocationStats(
    val topOrigins: List<UserLocationEntity> = emptyList(),      // 常在地（出发地）
    val topDestinations: List<UserLocationEntity> = emptyList(), // 常去地（目的地）
    val totalLocations: Int = 0
)

/**
 * 单个任务专注排行项
 */
data class FocusTaskStat(
    val taskId: Long,
    val taskTitle: String,
    val focusSeconds: Long
)

/**
 * 专注计时统计数据
 */
data class FocusStats(
    val todayFocusSeconds: Long = 0L,
    val weekFocusSeconds: Long = 0L,
    val totalFocusSeconds: Long = 0L,
    val sessionCount: Int = 0,
    val topFocusTasks: List<FocusTaskStat> = emptyList(),
    val recentSessions: List<FocusSessionEntity> = emptyList()
)

/**
 * 用户数据统计状态
 */
data class TaskStatistics(
    val totalTasks: Int = 0,
    val completedTasks: Int = 0,
    val activeTasks: Int = 0,
    val totalSubTasks: Int = 0,
    val completedSubTasks: Int = 0,
    val onTimeCompletedTasks: Int = 0,
    val completionRate: Float = 0f,
    val onTimeRate: Float = 0f,
    val aiAnalysisCount: Int = 0,
    val categoryStats: List<CategoryStats> = emptyList(),
    val monthlyTrend: List<DailyCompletionData> = emptyList(),
    val locationStats: LocationStats = LocationStats(),
    val focusStats: FocusStats = FocusStats()
)

/**
 * 用户数据界面状态
 */
data class UserDataUiState(
    val statistics: TaskStatistics = TaskStatistics(),
    val userProfile: UserProfileHistoryEntity? = null,
    val profileHistory: List<UserProfileHistoryEntity> = emptyList(),
    val locations: List<UserLocationEntity> = emptyList(),
    val isAnalyzing: Boolean = false,
    val agentStatus: String = "",
    val agentLogs: List<String> = emptyList(),
    val showAnalysisOverlay: Boolean = false,
    val previousProfileBeforeAnalysis: UserProfileHistoryEntity? = null,
    val completedAnalysisResult: UserProfileHistoryEntity? = null,
    val analysisError: String? = null,
    val error: String? = null
)

@HiltViewModel
class UserDataViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val focusSessionDao: FocusSessionDao,
    private val userProfileDao: UserProfileDao,
    private val userLocationDao: com.litetask.app.data.local.UserLocationDao,
    private val categoryDao: com.litetask.app.data.local.CategoryDao,
    private val aiHistoryDao: com.litetask.app.data.local.AIHistoryDao,
    private val userProfileAnalyzer: com.litetask.app.data.ai.UserProfileAnalyzer,
    private val preferenceManager: com.litetask.app.data.local.PreferenceManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(UserDataUiState())
    val uiState: StateFlow<UserDataUiState> = _uiState.asStateFlow()
    
    private var analysisJob: kotlinx.coroutines.Job? = null
    
    init {
        loadData()
    }
    
    /**
     * 加载数据
     */
    private fun loadData() {
        viewModelScope.launch {
            class PrimaryData(
                val tasks: List<com.litetask.app.data.model.Task>,
                val prof: UserProfileHistoryEntity?,
                val history: List<UserProfileHistoryEntity>,
                val cats: List<com.litetask.app.data.model.Category>,
                val locs: List<UserLocationEntity>
            )
            combine(
                combine(
                    taskDao.getAllTasks(),
                    userProfileDao.getUserProfileFlow(),
                    userProfileDao.getAllUserProfileHistoryFlow(),
                    categoryDao.getAllCategories(),
                    userLocationDao.getAllLocationsFlow()
                ) { allTasks, profile, history, categories, locations ->
                    PrimaryData(allTasks, profile, history, categories, locations)
                },
                focusSessionDao.getAllSessionsFlow()
            ) { data, focusSessions ->
                data to focusSessions
            }.collect { (data, focusSessions) ->
                val allTasks = data.tasks
                val profile = data.prof
                val profileHistory = data.history
                val categories = data.cats
                val locations = data.locs

                val allSubTasks = taskDao.getAllSubTasks()
                val aiAnalysisCount = aiHistoryDao.getHistoryCount()

                val completedTasks = allTasks.count { it.isDone }
                val activeTasks = allTasks.count { !it.isDone && !it.isExpired }
                val completedSubTasks = allSubTasks.count { it.isCompleted }

                val onTimeCompletedTasks = allTasks.count { task ->
                    task.isDone && task.completedAt != null &&
                        (task.deadline <= 0 || task.completedAt <= task.deadline)
                }

                val completionRate = if (allTasks.isNotEmpty()) {
                    completedTasks.toFloat() / allTasks.size
                } else 0f

                val onTimeRate = if (completedTasks > 0) {
                    onTimeCompletedTasks.toFloat() / completedTasks
                } else 0f

                val categoryStats = calculateCategoryStats(allTasks, categories)
                val monthlyTrend = calculateMonthlyTrend(allTasks)
                val locationStats = calculateLocationStats(locations)
                val focusStats = calculateFocusStats(allTasks, focusSessions)

                val statistics = TaskStatistics(
                    totalTasks = allTasks.size,
                    completedTasks = completedTasks,
                    activeTasks = activeTasks,
                    totalSubTasks = allSubTasks.size,
                    completedSubTasks = completedSubTasks,
                    onTimeCompletedTasks = onTimeCompletedTasks,
                    completionRate = completionRate,
                    onTimeRate = onTimeRate,
                    aiAnalysisCount = aiAnalysisCount,
                    categoryStats = categoryStats,
                    monthlyTrend = monthlyTrend,
                    locationStats = locationStats,
                    focusStats = focusStats
                )

                _uiState.update { currentState ->
                    currentState.copy(
                        statistics = statistics,
                        userProfile = profile,
                        profileHistory = profileHistory,
                        locations = locations.sortedByDescending { it.originCount + it.destinationCount }
                    )
                }
            }
        }
    }
    
    private fun calculateCategoryStats(
        tasks: List<com.litetask.app.data.model.Task>,
        categories: List<com.litetask.app.data.model.Category>
    ): List<CategoryStats> {
        if (tasks.isEmpty()) return emptyList()
        val categoryMap = tasks.groupBy { it.categoryId }
        
        return categories.mapNotNull { category ->
            val tasksInCategory = categoryMap[category.id] ?: emptyList()
            if (tasksInCategory.isEmpty()) return@mapNotNull null
            CategoryStats(
                categoryId = category.id,
                categoryName = category.name,
                categoryColor = category.colorHex,
                taskCount = tasksInCategory.size,
                percentage = tasksInCategory.size.toFloat() / tasks.size
            )
        }.sortedByDescending { it.taskCount }
    }
    
    private fun calculateMonthlyTrend(tasks: List<com.litetask.app.data.model.Task>): List<DailyCompletionData> {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, -29)
        }
        val thirtyDaysAgo = calendar.timeInMillis
        val recentCompletedTasks = tasks.filter { task ->
            task.isDone && task.completedAt != null && task.completedAt >= thirtyDaysAgo
        }
        val dateFormat = java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault())
        val dailyMap = linkedMapOf<String, Int>()
        
        calendar.timeInMillis = thirtyDaysAgo
        repeat(30) {
            dailyMap[dateFormat.format(calendar.time)] = 0
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        
        recentCompletedTasks.forEach { task ->
            task.completedAt?.let {
                val dateStr = dateFormat.format(java.util.Date(it))
                if (dailyMap.containsKey(dateStr)) {
                    dailyMap[dateStr] = (dailyMap[dateStr] ?: 0) + 1
                }
            }
        }
        
        return dailyMap.map { DailyCompletionData(it.key, it.value) }
    }
    
    /**
     * 计算地点统计数据
     */
    private fun calculateLocationStats(locations: List<UserLocationEntity>): LocationStats {
        if (locations.isEmpty()) return LocationStats()

        // 按出发次数排序，取前5个常在地
        val topOrigins = locations
            .filter { it.originCount > 0 }
            .sortedWith(compareByDescending<UserLocationEntity> { it.originCount }.thenByDescending { it.lastVisitedAt })
            .take(5)

        // 按目的地次数排序，取前5个常去地
        val topDestinations = locations
            .filter { it.destinationCount > 0 }
            .sortedWith(compareByDescending<UserLocationEntity> { it.destinationCount }.thenByDescending { it.lastVisitedAt })
            .take(5)

        return LocationStats(
            topOrigins = topOrigins,
            topDestinations = topDestinations,
            totalLocations = locations.size
        )
    }

    /**
     * 计算真实专注统计数据
     */
    private fun calculateFocusStats(
        tasks: List<com.litetask.app.data.model.Task>,
        sessions: List<FocusSessionEntity>
    ): FocusStats {
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val weekStart = todayStart - 6L * 24 * 60 * 60 * 1000

        val todayFocusSeconds = sessions.filter { it.startedAt >= todayStart }.sumOf { it.actualSeconds }
        val weekFocusSeconds = sessions.filter { it.startedAt >= weekStart }.sumOf { it.actualSeconds }
        val totalFocusSeconds = sessions.sumOf { it.actualSeconds }

        val topFocusTasks = tasks
            .filter { it.totalFocusSeconds > 0L }
            .sortedByDescending { it.totalFocusSeconds }
            .take(5)
            .map { FocusTaskStat(it.id, it.title, it.totalFocusSeconds) }

        return FocusStats(
            todayFocusSeconds = todayFocusSeconds,
            weekFocusSeconds = weekFocusSeconds,
            totalFocusSeconds = totalFocusSeconds,
            sessionCount = sessions.size,
            topFocusTasks = topFocusTasks,
            recentSessions = sessions.take(5)
        )
    }

    /**
     * 手动更新用户画像（保留或填充客观统计字段，避免手动保存把完成率重置为 0%）
     */
    fun updateUserProfile(profile: UserProfileHistoryEntity) {
        viewModelScope.launch {
            try {
                val existing = _uiState.value.userProfile
                val stats = _uiState.value.statistics
                val preservedCompletionRate = existing?.completionRate ?: stats.completionRate.toDouble()
                val preservedDelayedRate = existing?.delayedRate ?: run {
                    val allTasks = taskDao.getAllTasks().first()
                    val now = System.currentTimeMillis()
                    val cutoff30d = now - 30L * 24 * 60 * 60 * 1000
                    val recentPool = allTasks.filter {
                        it.createdAt >= cutoff30d || it.deadline >= cutoff30d || (it.completedAt ?: 0L) >= cutoff30d
                    }.ifEmpty { allTasks }
                    if (recentPool.isEmpty()) 0.0
                    else recentPool.count {
                        it.isExpired ||
                            (!it.isDone && it.deadline in 1 until now) ||
                            (it.isDone && it.completedAt != null && it.deadline > 0 && it.completedAt > it.deadline)
                    }.toDouble() / recentPool.size
                }
                val preservedFocus = existing?.categoryFocus
                    ?.takeIf { it.isNotBlank() }
                    ?: stats.categoryStats.firstOrNull()?.categoryName
                    ?: "无明显重心"
                val preservedInputPref = existing?.inputPreference
                    ?.takeIf { it.isNotBlank() && it != "手动编辑" }
                    ?: "无 AI 记录（手动为主）"

                userProfileDao.insert(
                    profile.copy(
                        id = 0,
                        createdAt = System.currentTimeMillis(),
                        completionRate = preservedCompletionRate,
                        delayedRate = preservedDelayedRate,
                        categoryFocus = preservedFocus,
                        inputPreference = preservedInputPref,
                        source = UserProfileHistoryEntity.SOURCE_MANUAL
                    )
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun updateLocation(locationId: Long, newName: String, locationType: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        val safeType = if (locationType in UserLocationEntity.ALL_TYPES) {
            locationType
        } else {
            UserLocationEntity.TYPE_FREQUENT
        }
        viewModelScope.launch {
            try {
                userLocationDao.updateNameAndType(locationId, trimmed, safeType)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun mergeLocations(sourceId: Long, targetId: Long) {
        if (sourceId == targetId) return
        viewModelScope.launch {
            try {
                userLocationDao.mergeLocations(sourceId, targetId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deleteLocation(locationId: Long) {
        viewModelScope.launch {
            try {
                userLocationDao.deleteById(locationId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearAllLocations() {
        viewModelScope.launch {
            try {
                userLocationDao.deleteAll()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deleteProfileSnapshot(snapshotId: Long) {
        viewModelScope.launch {
            try {
                userProfileDao.deleteById(snapshotId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearUserProfileHistory() {
        viewModelScope.launch {
            try {
                userProfileDao.deleteAll()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    private fun appendAgentLog(line: String) {
        val cleanLine = line.trim()
        if (cleanLine.isEmpty()) return
        val currentLogs = _uiState.value.agentLogs
        if (currentLogs.lastOrNull() != cleanLine) {
            _uiState.value = _uiState.value.copy(agentLogs = currentLogs + cleanLine)
        }
    }

    fun triggerAnalysis() {
        if (_uiState.value.isAnalyzing) return
        analysisJob = viewModelScope.launch {
            val configError = preferenceManager.getAiConfigError()
            if (configError != null) {
                _uiState.value = _uiState.value.copy(error = configError)
                return@launch
            }

            val prevSnapshot = _uiState.value.userProfile
            _uiState.value = _uiState.value.copy(
                isAnalyzing = true,
                showAnalysisOverlay = true,
                previousProfileBeforeAnalysis = prevSnapshot,
                completedAnalysisResult = null,
                analysisError = null,
                error = null,
                agentStatus = "正在准备画像分析数据...",
                agentLogs = emptyList()
            )

            try {
                // 调用AI分析，包含实时步骤回调
                val result = userProfileAnalyzer.analyzeUserProfile { step ->
                    when (step) {
                        is com.litetask.app.data.ai.AnalysisStep.Thinking -> {
                            _uiState.value = _uiState.value.copy(agentStatus = step.message)
                            appendAgentLog(step.message)
                        }
                        is com.litetask.app.data.ai.AnalysisStep.CallingTool -> {
                            _uiState.value = _uiState.value.copy(agentStatus = step.description)
                            appendAgentLog(step.description)
                        }
                        is com.litetask.app.data.ai.AnalysisStep.ToolResult -> {
                            appendAgentLog("✓ ${step.summary}")
                        }
                        is com.litetask.app.data.ai.AnalysisStep.Reasoning -> {
                            _uiState.value = _uiState.value.copy(agentStatus = "正在综合推理个人画像...")
                            appendAgentLog(step.content)
                        }
                        is com.litetask.app.data.ai.AnalysisStep.Complete -> {
                            _uiState.value = _uiState.value.copy(agentStatus = "画像分析完成")
                            appendAgentLog("✓ 画像分析完成，已同步最新结果")
                        }
                        is com.litetask.app.data.ai.AnalysisStep.Error -> {
                            _uiState.value = _uiState.value.copy(agentStatus = "分析中断")
                            appendAgentLog("× ${step.message}")
                        }
                    }
                }

                if (result.isSuccess) {
                    val profile = result.getOrNull()!!
                    userProfileDao.insert(profile)
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        agentStatus = "画像分析完成",
                        completedAnalysisResult = profile,
                        analysisError = null
                    )
                } else {
                    val errMsg = result.exceptionOrNull()?.message ?: "AI分析失败"
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        agentStatus = "分析失败",
                        analysisError = errMsg
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 忽略主动取消产生的异常
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    agentStatus = "分析失败",
                    analysisError = e.message ?: "AI分析失败"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isAnalyzing = false)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun dismissAnalysisOverlay() {
        if (_uiState.value.isAnalyzing) {
            cancelAnalysis()
            return
        }
        _uiState.value = _uiState.value.copy(
            showAnalysisOverlay = false,
            completedAnalysisResult = null,
            analysisError = null,
            agentStatus = "",
            agentLogs = emptyList()
        )
    }

    /**
     * 中断并取消当前的画像分析
     */
    fun cancelAnalysis() {
        analysisJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isAnalyzing = false,
            showAnalysisOverlay = false,
            completedAnalysisResult = null,
            analysisError = null,
            agentStatus = "",
            agentLogs = emptyList()
        )
    }
}
