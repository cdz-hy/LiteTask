package com.litetask.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.data.model.TaskType
import com.litetask.app.data.model.Category
import com.litetask.app.data.repository.CategoryRepository
import com.litetask.app.data.repository.TaskRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val taskRepository: TaskRepositoryImpl,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedTypes = MutableStateFlow<Set<TaskType>>(emptySet())
    val selectedTypes: StateFlow<Set<TaskType>> = _selectedTypes

    private val _selectedCategoryIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedCategoryIds: StateFlow<Set<Long>> = _selectedCategoryIds

    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _dateRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val dateRange: StateFlow<Pair<Long, Long>?> = _dateRange

    // 搜索结果
    val searchResults: StateFlow<List<TaskDetailComposite>> = combine(
        _searchQuery,
        _selectedTypes,
        _selectedCategoryIds,
        _dateRange
    ) { query, types, categoryIds, range ->
        Quadruple(query, types, categoryIds, range)
    }.flatMapLatest { (query, types, categoryIds, range) ->
        if (query.isEmpty() && types.isEmpty() && categoryIds.isEmpty() && range == null) {
            flowOf(emptyList())
        } else {
            taskRepository.searchTasks(
                query = query,
                types = types.toList(),
                categoryIds = categoryIds.toList(),
                startDate = range?.first,
                endDate = range?.second
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleTypeFilter(type: TaskType) {
        _selectedTypes.value = if (type in _selectedTypes.value) {
            _selectedTypes.value - type
        } else {
            _selectedTypes.value + type
        }
    }

    fun toggleCategoryFilter(categoryId: Long) {
        _selectedCategoryIds.value = if (categoryId in _selectedCategoryIds.value) {
            _selectedCategoryIds.value - categoryId
        } else {
            _selectedCategoryIds.value + categoryId
        }
    }

    fun setDateRange(start: Long, end: Long) {
        _dateRange.value = Pair(start, end)
    }

    fun clearDateRange() {
        _dateRange.value = null
    }

    fun clearAllFilters() {
        _selectedTypes.value = emptySet()
        _selectedCategoryIds.value = emptySet()
        _dateRange.value = null
    }

    fun toggleTaskDone(task: com.litetask.app.data.model.Task) {
        viewModelScope.launch {
            if (!task.isDone) {
                taskRepository.markTaskDone(task)
            } else {
                taskRepository.markTaskUndone(task)
            }
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
