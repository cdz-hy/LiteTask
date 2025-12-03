package com.litetask.app.data.repository

import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskType
import com.litetask.app.data.remote.ChatCompletionRequest
import com.litetask.app.data.remote.Message
import com.litetask.app.data.remote.OpenAIService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

interface AIRepository {
    suspend fun parseTasksFromText(apiKey: String, text: String): Result<List<Task>>
}

@Singleton
class AIRepositoryImpl @Inject constructor(
    private val preferenceManager: com.litetask.app.data.local.PreferenceManager,
    private val aiProviderFactory: com.litetask.app.data.ai.AIProviderFactory
) : AIRepository {

    override suspend fun parseTasksFromText(apiKey: String, text: String): Result<List<Task>> {
        // 优先使用传入的 Key，如果为空则使用存储的 Key
        val finalKey = if (apiKey.isNotBlank() && apiKey != "DEMO_KEY") {
            apiKey
        } else {
            preferenceManager.getApiKey()
        }
        
        if (finalKey.isNullOrBlank()) {
            return Result.failure(Exception("未设置 API Key，请在设置中配置"))
        }
        
        // 获取用户配置的 AI 提供商
        val providerId = preferenceManager.getAiProvider()
        
        return withContext(Dispatchers.IO) {
            try {
                // 使用工厂获取对应的 AI 提供商
                val provider = aiProviderFactory.getProvider(providerId)
                
                // 调用提供商的解析方法
                provider.parseTasksFromText(finalKey, text)
            } catch (e: Exception) {
                Result.failure(Exception("AI 解析失败: ${e.message}", e))
            }
        }
    }
}
