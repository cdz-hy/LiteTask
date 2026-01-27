package com.litetask.app.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("litetask_prefs", Context.MODE_PRIVATE)

    companion object {
        // AI 相关
        private const val KEY_API_KEY = "api_key"
        private const val KEY_AI_PROVIDER = "ai_provider"
        const val DEFAULT_AI_PROVIDER = "deepseek-v3.2"
        
        // 语音识别相关
        private const val KEY_SPEECH_PROVIDER = "speech_provider"
        private const val KEY_SPEECH_CREDENTIALS_PREFIX = "speech_cred_"
        const val DEFAULT_SPEECH_PROVIDER = "xunfei-rtasr"
        
        // 提醒相关
        private const val KEY_REMINDER_SOUND_ENABLED = "reminder_sound_enabled"
        private const val KEY_REMINDER_VIBRATION_ENABLED = "reminder_vibration_enabled"
        
        // 用户偏好相关
        private const val KEY_DEFAULT_FAB_ACTION = "default_fab_action"
        private const val KEY_DEFAULT_HOME_VIEW = "default_home_view"
        const val DEFAULT_FAB_ACTION = "voice"  // voice, text, manual
        const val DEFAULT_HOME_VIEW = "timeline"  // timeline, gantt, deadline
    }

    // ========== AI 配置 ==========
    
    fun getApiKey(): String? {
        return prefs.getString(KEY_API_KEY, null)
    }

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }
    
    fun getAiProvider(): String {
        return prefs.getString(KEY_AI_PROVIDER, DEFAULT_AI_PROVIDER) ?: DEFAULT_AI_PROVIDER
    }
    
    fun saveAiProvider(provider: String) {
        prefs.edit().putString(KEY_AI_PROVIDER, provider).apply()
    }
    
    // ========== 语音识别配置 ==========
    
    /**
     * 获取当前选择的语音识别服务提供商
     */
    fun getSpeechProvider(): String {
        return prefs.getString(KEY_SPEECH_PROVIDER, DEFAULT_SPEECH_PROVIDER) ?: DEFAULT_SPEECH_PROVIDER
    }
    
    /**
     * 保存语音识别服务提供商
     */
    fun saveSpeechProvider(provider: String) {
        prefs.edit().putString(KEY_SPEECH_PROVIDER, provider).apply()
    }
    
    /**
     * 获取语音识别服务的凭证
     * @param providerId 提供商ID
     * @param credentialId 凭证字段ID
     */
    fun getSpeechCredential(providerId: String, credentialId: String): String? {
        return prefs.getString("${KEY_SPEECH_CREDENTIALS_PREFIX}${providerId}_$credentialId", null)
    }
    
    /**
     * 保存语音识别服务的凭证
     * @param providerId 提供商ID
     * @param credentialId 凭证字段ID
     * @param value 凭证值
     */
    fun saveSpeechCredential(providerId: String, credentialId: String, value: String) {
        prefs.edit().putString("${KEY_SPEECH_CREDENTIALS_PREFIX}${providerId}_$credentialId", value).apply()
    }
    
    /**
     * 获取语音识别服务的所有凭证
     * @param providerId 提供商ID
     * @param credentialIds 需要获取的凭证字段ID列表
     */
    fun getSpeechCredentials(providerId: String, credentialIds: List<String>): Map<String, String> {
        return credentialIds.associateWith { credentialId ->
            getSpeechCredential(providerId, credentialId) ?: ""
        }
    }
    
    /**
     * 保存语音识别服务的所有凭证
     * @param providerId 提供商ID
     * @param credentials 凭证Map
     */
    fun saveSpeechCredentials(providerId: String, credentials: Map<String, String>) {
        val editor = prefs.edit()
        credentials.forEach { (credentialId, value) ->
            editor.putString("${KEY_SPEECH_CREDENTIALS_PREFIX}${providerId}_$credentialId", value)
        }
        editor.apply()
    }
    
    /**
     * 检查语音识别服务是否已配置
     */
    fun isSpeechConfigured(): Boolean {
        val providerId = getSpeechProvider()
        // 检查讯飞的必要凭证
        if (providerId == DEFAULT_SPEECH_PROVIDER) {
            val appId = getSpeechCredential(providerId, "appId")
            val apiKey = getSpeechCredential(providerId, "apiKey")
            return !appId.isNullOrBlank() && !apiKey.isNullOrBlank()
        }
        return false
    }
    
    // ========== 提醒配置 ==========
    
    /**
     * 获取提醒铃声是否启用（默认开启）
     */
    fun isReminderSoundEnabled(): Boolean {
        return prefs.getBoolean(KEY_REMINDER_SOUND_ENABLED, true)
    }
    
    /**
     * 设置提醒铃声是否启用
     */
    fun setReminderSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER_SOUND_ENABLED, enabled).apply()
    }
    
    /**
     * 获取提醒震动是否启用（默认开启）
     */
    fun isReminderVibrationEnabled(): Boolean {
        return prefs.getBoolean(KEY_REMINDER_VIBRATION_ENABLED, true)
    }
    
    /**
     * 设置提醒震动是否启用
     */
    fun setReminderVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER_VIBRATION_ENABLED, enabled).apply()
    }
    
    // ========== 用户偏好配置 ==========
    
    /**
     * 获取默认 FAB 操作（voice/text/manual）
     */
    fun getDefaultFabAction(): String {
        return prefs.getString(KEY_DEFAULT_FAB_ACTION, DEFAULT_FAB_ACTION) ?: DEFAULT_FAB_ACTION
    }
    
    /**
     * 设置默认 FAB 操作
     */
    fun setDefaultFabAction(action: String) {
        prefs.edit().putString(KEY_DEFAULT_FAB_ACTION, action).apply()
    }
    
    /**
     * 获取默认首页视图（timeline/gantt/deadline）
     */
    fun getDefaultHomeView(): String {
        return prefs.getString(KEY_DEFAULT_HOME_VIEW, DEFAULT_HOME_VIEW) ?: DEFAULT_HOME_VIEW
    }
    
    /**
     * 设置默认首页视图
     */
    fun setDefaultHomeView(view: String) {
        prefs.edit().putString(KEY_DEFAULT_HOME_VIEW, view).apply()
    }
}
