package com.litetask.app.data.speech

/**
 * 语音识别服务提供商接口
 * 使用策略模式，支持不同的语音识别服务
 */
interface SpeechProvider {
    /**
     * 获取提供商ID
     */
    fun getProviderId(): String
    
    /**
     * 获取提供商显示名称
     */
    fun getProviderName(): String
    
    /**
     * 获取需要的凭证字段列表
     * @return 字段列表，每个字段包含 (字段ID, 显示名称, 是否必填)
     */
    fun getRequiredCredentials(): List<CredentialField>
    
    /**
     * 验证凭证是否有效
     * @param credentials 凭证Map，key为字段ID
     * @return 验证结果
     */
    suspend fun validateCredentials(credentials: Map<String, String>): Result<Boolean>
    
    /**
     * 获取提供商描述信息
     */
    fun getDescription(): String
}

/**
 * 凭证字段定义
 */
data class CredentialField(
    val id: String,           // 字段ID，如 "appId", "apiKey"
    val displayName: String,  // 显示名称，如 "App ID", "API Key"
    val isRequired: Boolean = true,
    val isSecret: Boolean = false,  // 是否为敏感信息（密码显示）
    val hint: String = ""     // 输入提示
)
