package com.litetask.app.data.ai

import org.json.JSONObject
import javax.inject.Inject

/**
 * 小米 MIMO AI 提供商实现
 */
class XiaoMiProvider @Inject constructor() : BaseOpenAICompatibleProvider() {

    override val providerErrorLabel: String = "小米 MIMO"
    override val maxTokensParamName: String = "max_completion_tokens"

    override fun resolveChatCompletionsUrl(): String = CHAT_URL

    override fun resolveModelsUrl(): String = MODELS_URL

    override fun decorateRequestBody(body: JSONObject) {
        if (body.has("tools")) {
            body.put("tool_choice", "auto")
        }
    }

    override fun getProviderName(): String = "小米 MIMO"

    companion object {
        private const val CHAT_URL = "https://api.xiaomimimo.com/v1/chat/completions"
        private const val MODELS_URL = "https://api.xiaomimimo.com/v1/models"
    }
}
