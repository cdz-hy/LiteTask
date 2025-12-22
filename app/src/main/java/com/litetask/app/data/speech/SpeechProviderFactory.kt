package com.litetask.app.data.speech

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 语音识别服务提供商工厂
 * 根据配置返回对应的语音识别服务实现
 */
@Singleton
class SpeechProviderFactory @Inject constructor(
    private val xunfeiProvider: XunfeiSpeechProvider
) {
    /**
     * 根据提供商标识获取对应的语音识别服务
     * @param providerId 提供商标识
     * @return 语音识别服务实例
     */
    fun getProvider(providerId: String): SpeechProvider {
        return when (providerId.lowercase()) {
            XunfeiSpeechProvider.PROVIDER_ID, "xunfei" -> xunfeiProvider
            // 未来可以添加更多提供商
            // "google-speech" -> googleSpeechProvider
            // "azure-speech" -> azureSpeechProvider
            else -> xunfeiProvider // 默认使用讯飞
        }
    }
    
    /**
     * 获取所有支持的提供商列表
     * @return 提供商列表，每项包含 (providerId, displayName)
     */
    fun getSupportedProviders(): List<Pair<String, String>> {
        return listOf(
            XunfeiSpeechProvider.PROVIDER_ID to xunfeiProvider.getProviderName()
            // 未来添加更多
        )
    }
    
    /**
     * 获取默认提供商ID
     */
    fun getDefaultProviderId(): String = XunfeiSpeechProvider.PROVIDER_ID
}
