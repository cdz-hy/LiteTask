package com.litetask.app.data.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 提供商工厂
 * 根据配置返回对应的 AI 提供商实现
 */
@Singleton
class AIProviderFactory @Inject constructor(
    private val deepSeekProvider: DeepSeekProvider
) {
    /**
     * 根据提供商标识获取对应的 AI 提供商
     * @param providerId 提供商标识（如 "deepseek-v3.2"）
     * @return AI 提供商实例
     */
    fun getProvider(providerId: String): AIProvider {
        return when (providerId.lowercase()) {
            "deepseek-v3.2", "deepseek" -> deepSeekProvider
            // 未来可以添加更多提供商
            // "openai-gpt4" -> openAIProvider
            // "claude" -> claudeProvider
            else -> deepSeekProvider // 默认使用 DeepSeek
        }
    }
    
    /**
     * 获取所有支持的提供商列表
     */
    fun getSupportedProviders(): List<Pair<String, String>> {
        return listOf(
            "deepseek-v3.2" to "DeepSeek V3.2"
            // 未来添加更多
        )
    }
}
