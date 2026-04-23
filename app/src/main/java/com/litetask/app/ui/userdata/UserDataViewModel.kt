package com.litetask.app.ui.userdata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litetask.app.data.local.TaskDao
import com.litetask.app.data.local.UserProfileDao
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
    val locationStats: LocationStats = LocationStats()
)

/**
 * 用户数据界面状态
 */
data class UserDataUiState(
    val statistics: TaskStatistics = TaskStatistics(),
    val userProfile: UserProfileHistoryEntity? = null,
    val locations: List<UserLocationEntity> = emptyList(),
    val isAnalyzing: Boolean = false,
    val agentStatus: String = "",
    val agentLogs: List<String> = emptyList(),
    val showAnalysisOverlay: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class UserDataViewModel @Inject constructor(
    private val taskDao: TaskDao,
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
            combine(
                taskDao.getAllTasks(),
                userProfileDao.getUserProfileFlow(),
                categoryDao.getAllCategories(),
                userLocationDao.getAllLocationsFlow()
            ) { allTasks, profile, categories, locations ->
                class DataPackage(
                    val tasks: List<com.litetask.app.data.model.Task>,
                    val prof: UserProfileHistoryEntity?,
                    val cats: List<com.litetask.app.data.model.Category>,
                    val locs: List<UserLocationEntity>
                )
                DataPackage(allTasks, profile, categories, locations)
            }.collect { data ->
                val allTasks = data.tasks
                val profile = data.prof
                val categories = data.cats
                val locations = data.locs
                
                val allSubTasks = taskDao.getAllSubTasks()
                val aiAnalysisCount = aiHistoryDao.getHistoryCount()
                
                val completedTasks = allTasks.count { it.isDone }
                val activeTasks = allTasks.count { !it.isDone && !it.isExpired }
                val completedSubTasks = allSubTasks.count { it.isCompleted }
                
                val onTimeCompletedTasks = allTasks.count { task ->
                    task.isDone && task.completedAt != null && task.completedAt <= task.deadline
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
                    locationStats = locationStats
                )
                
                _uiState.update { currentState ->
                    currentState.copy(
                        statistics = statistics,
                        userProfile = profile,
                        locations = locations
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
        val calendar = java.util.Calendar.getInstance()
        val thirtyDaysAgo = calendar.apply { add(java.util.Calendar.DAY_OF_YEAR, -29) }.timeInMillis
        val recentCompletedTasks = tasks.filter { task ->
            task.isDone && task.completedAt != null && task.completedAt >= thirtyDaysAgo
        }
        val dateFormat = java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault())
        val dailyMap = mutableMapOf<String, Int>()
        
        calendar.timeInMillis = thirtyDaysAgo
        repeat(30) {
            dailyMap[dateFormat.format(calendar.time)] = 0
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        
        recentCompletedTasks.forEach { task ->
            task.completedAt?.let {
                val dateStr = dateFormat.format(java.util.Date(it))
                dailyMap[dateStr] = (dailyMap[dateStr] ?: 0) + 1
            }
        }
        
        return dailyMap.map { DailyCompletionData(it.key, it.value) }.sortedBy { 
            val parts = it.date.split("/")
            parts[0].toInt() * 100 + parts[1].toInt()
        }
    }
    
    /**
     * 计算地点统计数据
     */
    private fun calculateLocationStats(locations: List<UserLocationEntity>): LocationStats {
        if (locations.isEmpty()) return LocationStats()
        
        // 按出发次数排序，取前5个常在地
        val topOrigins = locations
            .filter { it.originCount > 0 }
            .sortedByDescending { it.originCount }
            .take(5)
        
        // 按目的地次数排序，取前5个常去地
        val topDestinations = locations
            .filter { it.destinationCount > 0 }
            .sortedByDescending { it.destinationCount }
            .take(5)
        
        return LocationStats(
            topOrigins = topOrigins,
            topDestinations = topDestinations,
            totalLocations = locations.size
        )
    }

    /**
     * 更新用户画像
     */
    fun updateUserProfile(profile: UserProfileHistoryEntity) {
        viewModelScope.launch {
            try {
                // Since it's append-only, we create a new snapshot with the new data
                userProfileDao.insert(profile.copy(id = 0, createdAt = System.currentTimeMillis()))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
    
    fun triggerAnalysis() {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            if (!preferenceManager.isAiAgentEnabled()) {
                _uiState.value = _uiState.value.copy(error = "AI Agent 未开启，请先在设置中启用 AI Agent 助手。")
                return@launch
            }
        
            _uiState.value = _uiState.value.copy(
                isAnalyzing = true,
                showAnalysisOverlay = true,
                error = null,
                agentStatus = "正在初始化画像分析引擎...",
                agentLogs = emptyList()
            )
            
            try {
                // 调用AI分析，包含实时步骤回调
                val result = userProfileAnalyzer.analyzeUserProfile() { step ->
                    when (step) {
                        is com.litetask.app.data.ai.AnalysisStep.Thinking -> {
                            _uiState.value = _uiState.value.copy(
                                agentStatus = step.message,
                                agentLogs = _uiState.value.agentLogs + step.message
                            )
                        }
                        is com.litetask.app.data.ai.AnalysisStep.CallingTool -> {
                            _uiState.value = _uiState.value.copy(
                                agentStatus = "调用工具: ${step.toolName}",
                                agentLogs = _uiState.value.agentLogs + "[工具调用] ${step.toolName}: ${step.description}"
                            )
                        }
                        is com.litetask.app.data.ai.AnalysisStep.ToolResult -> {
                            _uiState.value = _uiState.value.copy(
                                agentLogs = _uiState.value.agentLogs + "[返回结果] ${step.summary}"
                            )
                        }
                        is com.litetask.app.data.ai.AnalysisStep.Reasoning -> {
                            _uiState.value = _uiState.value.copy(
                                agentStatus = "正在分析数据并推理...",
                                agentLogs = _uiState.value.agentLogs + step.content
                            )
                        }
                        is com.litetask.app.data.ai.AnalysisStep.Complete -> {
                            _uiState.value = _uiState.value.copy(
                                agentStatus = "画像分析完成",
                                agentLogs = _uiState.value.agentLogs + "画像分析完成，正在保存结果..."
                            )
                        }
                        is com.litetask.app.data.ai.AnalysisStep.Error -> {
                            _uiState.value = _uiState.value.copy(
                                agentStatus = "分析出错",
                                agentLogs = _uiState.value.agentLogs + "错误: ${step.message}"
                            )
                        }
                    }
                }
                
                if (result.isSuccess) {
                    val profile = result.getOrNull()!!
                    userProfileDao.insert(profile)
                    
                    // 分析完成后，延迟5秒隐藏覆盖层
                    kotlinx.coroutines.delay(5000)
                    _uiState.value = _uiState.value.copy(
                        showAnalysisOverlay = false,
                        agentStatus = "",
                        agentLogs = emptyList()
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = result.exceptionOrNull()?.message ?: "AI分析失败",
                        showAnalysisOverlay = false
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 忽略主动取消产生的异常
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    showAnalysisOverlay = false
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
        _uiState.value = _uiState.value.copy(showAnalysisOverlay = false)
    }

    /**
     * 中断并取消当前的画像分析
     */
    fun cancelAnalysis() {
        analysisJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isAnalyzing = false,
            showAnalysisOverlay = false,
            agentStatus = "分析已取消",
            agentLogs = _uiState.value.agentLogs + "[系统] 用户中断了分析过程"
        )
    }
}
