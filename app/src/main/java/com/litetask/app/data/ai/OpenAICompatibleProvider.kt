package com.litetask.app.data.ai

import com.litetask.app.data.local.PreferenceManager
import java.net.URI
import javax.inject.Inject

/**
 * 通用 OpenAI 兼容接口提供方实现，支持任意兼容 Chat Completions 协议的自定义服务端点（如中转、代理、vLLM、Ollama 等）。
 */
class OpenAICompatibleProvider @Inject constructor(
    private val preferenceManager: PreferenceManager
) : BaseOpenAICompatibleProvider() {

    override val providerErrorLabel: String = "OpenAI 兼容接口"
    override val fallbackChatTestOnModelsUnsupported: Boolean = true

    override fun resolveChatCompletionsUrl(): String {
        return normalizeChatCompletionsUrl(preferenceManager.getCustomBaseUrl())
    }

    override fun resolveModelsUrl(): String {
        return normalizeModelsUrl(preferenceManager.getCustomBaseUrl())
    }

    override fun getProviderName(): String = "OpenAI 兼容接口"

    companion object {
        private val CHAT_COMPLETIONS_SUFFIX = Regex("/chat/completions$", RegexOption.IGNORE_CASE)

        fun normalizeChatCompletionsUrl(rawBaseUrl: String): String {
            val base = normalizeBaseInput(rawBaseUrl)
            if (CHAT_COMPLETIONS_SUFFIX.containsMatchIn(base)) {
                return base
            }
            return "${resolveVersionedBase(base)}/chat/completions"
        }

        fun normalizeModelsUrl(rawBaseUrl: String): String {
            val base = normalizeBaseInput(rawBaseUrl)
            if (CHAT_COMPLETIONS_SUFFIX.containsMatchIn(base)) {
                return base.replace(CHAT_COMPLETIONS_SUFFIX, "").trimEnd('/') + "/models"
            }
            return "${resolveVersionedBase(base)}/models"
        }

        private fun normalizeBaseInput(rawBaseUrl: String): String {
            var url = rawBaseUrl.trim().trimEnd('/')
            if (url.isBlank()) {
                throw IllegalArgumentException("请先填写 Base URL")
            }
            if (!url.startsWith("http://", ignoreCase = true) &&
                !url.startsWith("https://", ignoreCase = true)
            ) {
                url = "https://$url"
            }
            return url.trimEnd('/')
        }

        private fun resolveVersionedBase(normalizedUrl: String): String {
            val path = try {
                URI(normalizedUrl).path?.trim('/') ?: ""
            } catch (_: Exception) {
                ""
            }
            return if (path.isEmpty()) "$normalizedUrl/v1" else normalizedUrl
        }
    }
}
