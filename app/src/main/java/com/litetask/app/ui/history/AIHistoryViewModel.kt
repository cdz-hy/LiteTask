package com.litetask.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litetask.app.data.local.DailyScheduleAdviceDao
import com.litetask.app.data.model.AIHistory
import com.litetask.app.data.model.AIHistorySource
import com.litetask.app.data.model.DailyScheduleAdviceEntity
import com.litetask.app.data.repository.AIHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AIHistoryFilter {
    ALL, VOICE, TEXT, SUBTASK, FAILED
}

@HiltViewModel
class AIHistoryViewModel @Inject constructor(
    private val aiHistoryRepository: AIHistoryRepository,
    private val dailyScheduleAdviceDao: DailyScheduleAdviceDao
) : ViewModel() {

    val allHistoryItems: StateFlow<List<AIHistory>> = aiHistoryRepository.getAllHistoryFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedFilter = MutableStateFlow(AIHistoryFilter.ALL)
    val selectedFilter: StateFlow<AIHistoryFilter> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredHistoryItems: StateFlow<List<AIHistory>> = combine(
        allHistoryItems,
        _selectedFilter,
        _searchQuery
    ) { items, filter, query ->
        filterAiHistory(items, filter, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val failedHistoryCount: StateFlow<Int> = allHistoryItems
        .map { items -> items.count { !it.isSuccess } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val adviceHistoryItems: StateFlow<List<DailyScheduleAdviceEntity>> =
        dailyScheduleAdviceDao.getAllFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: AIHistoryFilter) {
        _selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteHistory(history: AIHistory) {
        viewModelScope.launch {
            aiHistoryRepository.deleteHistory(history)
        }
    }

    fun clearFailedHistory() {
        viewModelScope.launch {
            aiHistoryRepository.clearFailedHistory()
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            aiHistoryRepository.clearAllHistory()
        }
    }

    fun deleteAdvice(id: Long) {
        viewModelScope.launch {
            dailyScheduleAdviceDao.deleteById(id)
        }
    }

    fun clearAllAdvice() {
        viewModelScope.launch {
            dailyScheduleAdviceDao.deleteAll()
        }
    }

    companion object {
        internal fun filterAiHistory(
            items: List<AIHistory>,
            filter: AIHistoryFilter,
            query: String
        ): List<AIHistory> {
            val trimmedQuery = query.trim()
            return items.filter { item ->
                val matchesFilter = when (filter) {
                    AIHistoryFilter.ALL -> true
                    AIHistoryFilter.VOICE -> item.sourceType == AIHistorySource.VOICE
                    AIHistoryFilter.TEXT -> item.sourceType == AIHistorySource.TEXT
                    AIHistoryFilter.SUBTASK -> item.sourceType == AIHistorySource.SUBTASK
                    AIHistoryFilter.FAILED -> !item.isSuccess
                }
                if (!matchesFilter) return@filter false
                if (trimmedQuery.isEmpty()) return@filter true

                item.content.contains(trimmedQuery, ignoreCase = true) ||
                    (item.targetTitle?.contains(trimmedQuery, ignoreCase = true) == true) ||
                    (item.errorMessage?.contains(trimmedQuery, ignoreCase = true) == true)
            }
        }
    }
}
