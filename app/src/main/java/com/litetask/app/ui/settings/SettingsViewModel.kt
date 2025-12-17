package com.litetask.app.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litetask.app.R
import com.litetask.app.data.ai.AIProviderFactory
import com.litetask.app.data.local.PreferenceManager
import com.litetask.app.data.speech.SpeechProviderFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val preferenceManager: PreferenceManager,
    private val aiProviderFactory: AIProviderFactory,
    private val speechProviderFactory: SpeechProviderFactory
) : ViewModel() {

    // ========== 通用状态 ==========
    
    sealed class ConnectionState {
        object Idle : ConnectionState()
        object Testing : ConnectionState()
        object Success : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    // ========== AI 配置 ==========
    
    private val _aiConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val aiConnectionState: StateFlow<ConnectionState> = _aiConnectionState.asStateFlow()
    
    // 兼容旧代码
    val connectionState: StateFlow<ConnectionState> = _aiConnectionState

    fun getApiKey(): String? = preferenceManager.getApiKey()
    fun saveApiKey(key: String) = preferenceManager.saveApiKey(key)
    fun getAiProvider(): String = preferenceManager.getAiProvider()
    fun saveAiProvider(provider: String) = preferenceManager.saveAiProvider(provider)

    fun resetConnectionState() {
        _aiConnectionState.value = ConnectionState.Idle
    }

    fun testConnection(apiKey: String, providerId: String) {
        if (apiKey.isBlank()) {
            _aiConnectionState.value = ConnectionState.Error(application.getString(R.string.please_enter_api_key))
            return
        }

        viewModelScope.launch {
            _aiConnectionState.value = ConnectionState.Testing
            val provider = aiProviderFactory.getProvider(providerId)
            val result = provider.testConnection(apiKey)
            
            result.onSuccess {
                _aiConnectionState.value = ConnectionState.Success
            }.onFailure {
                _aiConnectionState.value = ConnectionState.Error(it.message ?: application.getString(R.string.error_connection_failed))
            }
        }
    }
    
    // ========== 语音识别配置 ==========
    
    private val _speechConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val speechConnectionState: StateFlow<ConnectionState> = _speechConnectionState.asStateFlow()
    
    /**
     * 获取当前语音识别服务提供商
     */
    fun getSpeechProvider(): String = preferenceManager.getSpeechProvider()
    
    /**
     * 保存语音识别服务提供商
     */
    fun saveSpeechProvider(provider: String) = preferenceManager.saveSpeechProvider(provider)
    
    /**
     * 获取语音识别服务的凭证
     */
    fun getSpeechCredential(providerId: String, credentialId: String): String? {
        return preferenceManager.getSpeechCredential(providerId, credentialId)
    }
    
    /**
     * 获取语音识别服务的所有凭证
     */
    fun getSpeechCredentials(providerId: String): Map<String, String> {
        val provider = speechProviderFactory.getProvider(providerId)
        val credentialIds = provider.getRequiredCredentials().map { it.id }
        return preferenceManager.getSpeechCredentials(providerId, credentialIds)
    }
    
    /**
     * 保存语音识别服务的凭证
     */
    fun saveSpeechCredentials(providerId: String, credentials: Map<String, String>) {
        preferenceManager.saveSpeechCredentials(providerId, credentials)
    }
    
    /**
     * 获取支持的语音识别服务列表
     */
    fun getSupportedSpeechProviders(): List<Pair<String, String>> {
        return speechProviderFactory.getSupportedProviders()
    }
    
    /**
     * 获取语音识别服务需要的凭证字段
     */
    fun getSpeechCredentialFields(providerId: String): List<com.litetask.app.data.speech.CredentialField> {
        return speechProviderFactory.getProvider(providerId).getRequiredCredentials()
    }
    
    /**
     * 重置语音识别测试状态
     */
    fun resetSpeechConnectionState() {
        _speechConnectionState.value = ConnectionState.Idle
    }
    
    /**
     * 测试语音识别服务连接
     */
    fun testSpeechConnection(providerId: String, credentials: Map<String, String>) {
        viewModelScope.launch {
            _speechConnectionState.value = ConnectionState.Testing
            val provider = speechProviderFactory.getProvider(providerId)
            val result = provider.validateCredentials(credentials)
            
            result.onSuccess {
                _speechConnectionState.value = ConnectionState.Success
            }.onFailure {
                _speechConnectionState.value = ConnectionState.Error(it.message ?: application.getString(R.string.error_connection_failed))
            }
        }
    }
}