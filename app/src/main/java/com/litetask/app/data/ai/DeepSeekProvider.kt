package com.litetask.app.data.ai

import org.json.JSONObject
import javax.inject.Inject

/**
 * DeepSeek AI 提供商实现
 */
class DeepSeekProvider @Inject constructor() : BaseOpenAICompatibleProvider() {

    override val providerErrorLabel: String = "DeepSeek"

    override fun resolveChatCompletionsUrl(): String = CHAT_URL

    override fun resolveModelsUrl(): String = MODELS_URL

    override fun decorateRequestBody(body: JSONObject) {
        // 禁用思考模式（非思考模式）
        body.put("extra_body", JSONObject().apply {
            put("thinking", JSONObject().apply {
                put("type", "disabled")
            })
        })
    }

    override fun getProviderName(): String = "DeepSeek"

    companion object {
        private const val CHAT_URL = "https://api.deepseek.com/v1/chat/completions"
        private const val MODELS_URL = "https://api.deepseek.com/models"
    }
}
