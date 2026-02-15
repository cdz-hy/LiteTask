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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.litetask.app.data.model.Category
import com.litetask.app.data.repository.CategoryRepository

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val preferenceManager: PreferenceManager,
    private val aiProviderFactory: AIProviderFactory,
    private val speechProviderFactory: SpeechProviderFactory,
    private val categoryRepository: CategoryRepository
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
    
    fun isAiDestinationEnabled(): Boolean = preferenceManager.isAiDestinationEnabled()
    fun setAiDestinationEnabled(enabled: Boolean) = preferenceManager.setAiDestinationEnabled(enabled)

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
    
    // ========== 提醒方式配置 ==========
    // (Existing reminder logic below...)
    
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

    // ========== 高德地图配置 ==========
    
    private val _amapConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val amapConnectionState: StateFlow<ConnectionState> = _amapConnectionState.asStateFlow()

    fun getAMapKey(): String? = preferenceManager.getAMapKey()
    fun saveAMapKey(key: String) = preferenceManager.saveAMapKey(key)

    fun resetAMapConnectionState() {
        _amapConnectionState.value = ConnectionState.Idle
    }

    fun testAMapConnection(key: String) {
        if (key.isBlank()) {
            _amapConnectionState.value = ConnectionState.Error("请输入高德地图 Key")
            return
        }

        viewModelScope.launch {
            _amapConnectionState.value = ConnectionState.Testing
            try {
                val encodedAddress = java.net.URLEncoder.encode("北京", "UTF-8")
                val urlString = "https://restapi.amap.com/v3/geocode/geo?address=$encodedAddress&output=JSON&key=$key"
                
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val url = java.net.URL(urlString)
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    if (connection.responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val json = org.json.JSONObject(response)
                        if (json.optString("status") == "1") {
                            _amapConnectionState.value = ConnectionState.Success
                        } else {
                            val info = json.optString("info")
                            _amapConnectionState.value = ConnectionState.Error(info.ifBlank { "验证失败" })
                        }
                    } else {
                        _amapConnectionState.value = ConnectionState.Error("HTTP Error: ${connection.responseCode}")
                    }
                }
            } catch (e: Exception) {
                _amapConnectionState.value = ConnectionState.Error(e.message ?: "连接异常")
            }
        }
    }
    
    // ========== 提醒配置 ==========
    
    /**
     * 获取提醒铃声是否启用
     */
    fun isReminderSoundEnabled(): Boolean = preferenceManager.isReminderSoundEnabled()
    
    /**
     * 设置提醒铃声是否启用
     */
    fun setReminderSoundEnabled(enabled: Boolean) = preferenceManager.setReminderSoundEnabled(enabled)
    
    /**
     * 获取提醒震动是否启用
     */
    fun isReminderVibrationEnabled(): Boolean = preferenceManager.isReminderVibrationEnabled()
    
    /**
     * 设置提醒震动是否启用
     */
    fun setReminderVibrationEnabled(enabled: Boolean) = preferenceManager.setReminderVibrationEnabled(enabled)
    
    // ========== 用户偏好配置 ==========
    
    /**
     * 获取默认 FAB 操作
     */
    fun getDefaultFabAction(): String = preferenceManager.getDefaultFabAction()
    
    /**
     * 设置默认 FAB 操作
     */
    fun setDefaultFabAction(action: String) = preferenceManager.setDefaultFabAction(action)
    
    /**
     * 获取默认首页视图
     */
    fun getDefaultHomeView(): String = preferenceManager.getDefaultHomeView()
    
    /**
     * 设置默认首页视图
     */
    fun setDefaultHomeView(view: String) = preferenceManager.setDefaultHomeView(view)
    
    // ========== 分类管理 ==========
    
    val categories: Flow<List<Category>> = categoryRepository.getAllCategories()
    
    fun addCategory(category: Category) {
        viewModelScope.launch {
            categoryRepository.insertCategory(category)
        }
    }
    
    fun updateCategory(category: Category) {
        viewModelScope.launch {
            categoryRepository.updateCategory(category)
        }
    }
    
    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            categoryRepository.deleteCategory(category)
        }
    }
}