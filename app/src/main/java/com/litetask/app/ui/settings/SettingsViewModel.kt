package com.litetask.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litetask.app.data.ai.AIProviderFactory
import com.litetask.app.data.local.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val aiProviderFactory: AIProviderFactory // 注入工厂
) : ViewModel() {

    // API 测试状态
    sealed class ConnectionState {
        object Idle : ConnectionState()
        object Testing : ConnectionState()
        object Success : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    fun getApiKey(): String? {
        return preferenceManager.getApiKey()
    }

    fun saveApiKey(key: String) {
        preferenceManager.saveApiKey(key)
    }
    
    fun getAiProvider(): String {
        return preferenceManager.getAiProvider()
    }
    
    fun saveAiProvider(provider: String) {
        preferenceManager.saveAiProvider(provider)
    }

    // 重置测试状态（例如当用户修改 Key 时）
    fun resetConnectionState() {
        _connectionState.value = ConnectionState.Idle
    }

    // 测试连接
    fun testConnection(apiKey: String, providerId: String) {
        if (apiKey.isBlank()) {
            _connectionState.value = ConnectionState.Error("请输入 API Key")
            return
        }

        viewModelScope.launch {
            _connectionState.value = ConnectionState.Testing
            val provider = aiProviderFactory.getProvider(providerId)
            val result = provider.testConnection(apiKey)
            
            result.onSuccess {
                _connectionState.value = ConnectionState.Success
            }.onFailure {
                _connectionState.value = ConnectionState.Error(it.message ?: "连接失败")
            }
        }
    }
}