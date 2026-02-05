package com.litetask.app.data.repository

import com.litetask.app.data.local.AIHistoryDao
import com.litetask.app.data.model.AIHistory
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIHistoryRepository @Inject constructor(
    private val aiHistoryDao: AIHistoryDao
) {
    fun getAllHistoryFlow(): Flow<List<AIHistory>> = aiHistoryDao.getAllHistoryFlow()

    suspend fun insertHistory(history: AIHistory) = aiHistoryDao.insertHistory(history)

    suspend fun deleteHistory(history: AIHistory) = aiHistoryDao.deleteHistory(history)

    suspend fun clearAllHistory() = aiHistoryDao.clearAllHistory()
}
