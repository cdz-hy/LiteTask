package com.litetask.app.data.speech

import kotlinx.coroutines.flow.Flow

/**
 * 语音识别服务提供商接口
 * 使用策略模式，支持不同的语音识别服务
 */
interface SpeechProvider {
    fun getProviderId(): String
    fun getProviderName(): String
    fun getRequiredCredentials(): List<CredentialField>
    suspend fun validateCredentials(credentials: Map<String, String>): Result<Boolean>
    fun getDescription(): String
    
    /**
     * 开始实时语音识别
     * @param credentials 凭证信息
     * @return 识别结果流
     */
    fun startRecognition(credentials: Map<String, String>): Flow<SpeechRecognitionResult>
    
    /**
     * 停止识别并释放资源
     */
    fun stopRecognition()
}

/**
 * 语音识别结果
 */
sealed class SpeechRecognitionResult {
    object Started : SpeechRecognitionResult()
    data class PartialResult(val text: String) : SpeechRecognitionResult()
    data class FinalResult(val text: String) : SpeechRecognitionResult()
    data class Error(val code: String, val message: String) : SpeechRecognitionResult()
}

/**
 * 凭证字段定义
 */
data class CredentialField(
    val id: String,
    val displayName: String,
    val isRequired: Boolean = true,
    val isSecret: Boolean = false,
    val hint: String = ""
)
