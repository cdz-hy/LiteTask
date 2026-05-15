package com.litetask.app.data.ai

import com.litetask.app.data.local.PreferenceManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 提供商工厂
 * 根据配置返回对应的 AI 提供商实现
 */
@Singleton
class AIProviderFactory @Inject constructor(
    private val deepSeekProvider: DeepSeekProvider,
    private val xiaoMiProvider: XiaoMiProvider,
    private val preferenceManager: PreferenceManager
) {
    /**
     * 根据提供商标识获取对应的 AI 提供商
     * @param providerId 提供商标识（如 "deepseek-v3.2"）
     * @return AI 提供商实例
     */
    fun getProvider(providerId: String): AIProvider {
        return when (providerId.lowercase()) {
            "deepseek" -> deepSeekProvider
            "xiaomi" -> xiaoMiProvider
            else -> deepSeekProvider
        }
    }
    
    /**
     * 获取所有支持的提供商列表
     */
    fun getSupportedProviders(): List<Pair<String, String>> {
        return listOf(
            "deepseek" to "DeepSeek",
            "xiaomi" to "小米 MIMO"
        )
    }

    /**
     * 获取指定提供商支持的模型列表
     */
    fun getSupportedModels(providerId: String): List<Pair<String, String>> {
        val fetchedModels = preferenceManager.getFetchedModels(providerId)
        val defaultModels = when (providerId.lowercase()) {
            "deepseek" -> listOf(
                "deepseek-v4-pro" to "deepseek-v4-pro",
                "deepseek-v4-flash" to "deepseek-v4-flash"
            )
            "xiaomi" -> listOf(
                "mimo-v2.5-pro" to "mimo-v2.5-pro",
                "mimo-v2.5" to "mimo-v2.5",
                "mimo-v2-omni" to "mimo-v2-omni",
                "mimo-v2-flash" to "mimo-v2-flash"
            )
            else -> emptyList()
        }
        
        val modelsToReturn = fetchedModels?.takeIf { it.isNotEmpty() } ?: defaultModels
        
        val result = modelsToReturn.toMutableList()
        
        // 追加自定义模型（如果存在）
        val customModel = preferenceManager.getCustomModel(providerId)
        if (!customModel.isNullOrBlank() && result.none { it.first == customModel }) {
            result.add(customModel to "$customModel (自定义)")
        }
        
        return result
    }
}
