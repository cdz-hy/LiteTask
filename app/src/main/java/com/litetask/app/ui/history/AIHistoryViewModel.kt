package com.litetask.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litetask.app.data.model.AIHistory
import com.litetask.app.data.repository.AIHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AIHistoryViewModel @Inject constructor(
    private val aiHistoryRepository: AIHistoryRepository
) : ViewModel() {

    val historyItems: StateFlow<List<AIHistory>> = aiHistoryRepository.getAllHistoryFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteHistory(history: AIHistory) {
        viewModelScope.launch {
            aiHistoryRepository.deleteHistory(history)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            aiHistoryRepository.clearAllHistory()
        }
    }
}
