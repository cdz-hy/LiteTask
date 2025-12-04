package com.litetask.app.data.ai

import com.litetask.app.data.model.Task

/**
 * AI 提供商接口
 * 使用适配器模式，支持不同的 AI 服务提供商
 */
interface AIProvider {
    /**
     * 从文本中解析任务
     * @param apiKey API 密钥
     * @param text 用户输入的文本
     * @return 解析出的任务列表
     */
    suspend fun parseTasksFromText(apiKey: String, text: String): Result<List<Task>>

    /**
     * 测试 API 连通性
     * @param apiKey API 密钥
     * @return true 表示连接成功，false 表示失败
     */
    suspend fun testConnection(apiKey: String): Result<Boolean>
    
    /**
     * 获取提供商名称
     */
    fun getProviderName(): String
}
