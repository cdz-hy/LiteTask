package com.litetask.app.data.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 提供商工厂
 * 根据配置返回对应的 AI 提供商实现
 */
@Singleton
class AIProviderFactory @Inject constructor(
    private val deepSeekProvider: DeepSeekProvider,
    private val xiaoMiProvider: XiaoMiProvider
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
        return when (providerId.lowercase()) {
            "deepseek" -> listOf(
                "deepseek-v4-pro" to "DeepSeek V4 Pro",
                "deepseek-v4-flash" to "DeepSeek V4 Flash"
            )
            "xiaomi" -> listOf(
                "mimo-v2.5-pro" to "MIMO v2.5 Pro",
                "mimo-v2.5" to "MIMO v2.5",
                "mimo-v2-flash" to "MIMO v2 Flash",
                "mimo-v2-pro" to "MIMO v2 Pro",
                "mimo-v2-omni" to "MIMO v2 Omni"
            )
            else -> emptyList()
        }
    }
}
