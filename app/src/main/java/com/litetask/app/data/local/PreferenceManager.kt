package com.litetask.app.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("litetask_prefs", Context.MODE_PRIVATE)

    init {
        migrateLegacyApiKeyIfNeeded()
    }

    /**
     * 将旧版单一 api_key 迁移到按服务商隔离存储的 key 中，避免切换服务商后互相覆盖。
     */
    private fun migrateLegacyApiKeyIfNeeded() {
        if (prefs.getBoolean(KEY_API_KEY_MIGRATED_V1, false)) return
        val editor = prefs.edit().putBoolean(KEY_API_KEY_MIGRATED_V1, true)
        val legacyKey = prefs.getString(KEY_API_KEY, null)
        if (!legacyKey.isNullOrBlank()) {
            val legacyProvider = prefs.getString(KEY_AI_PROVIDER, DEFAULT_AI_PROVIDER) ?: DEFAULT_AI_PROVIDER
            val scopedKey = "$KEY_API_KEY_PREFIX$legacyProvider"
            if (!prefs.contains(scopedKey)) {
                editor.putString(scopedKey, legacyKey)
            }
        }
        editor.apply()
    }

    /**
     * 获取高德地图 Key 的 Flow
     */
    val amapKeyFlow: Flow<String?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == KEY_AMAP_KEY) {
                trySend(sharedPreferences.getString(KEY_AMAP_KEY, null))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(prefs.getString(KEY_AMAP_KEY, null))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        // AI 相关
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_KEY_PREFIX = "api_key_"
        private const val KEY_API_KEY_MIGRATED_V1 = "api_key_migrated_v1"
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_CUSTOM_BASE_URL = "custom_base_url"
        private const val KEY_MULTIMODAL_PREFIX = "multimodal_"
        const val DEFAULT_AI_PROVIDER = "deepseek"
        const val DEFAULT_AI_MODEL = "deepseek-flash"
        const val DEFAULT_CUSTOM_BASE_URL = ""
        
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
        
        // 截止视图时间范围配置（单位：小时）
        private const val KEY_DEADLINE_URGENT_HOURS = "deadline_urgent_hours"
        private const val KEY_DEADLINE_SOON_HOURS = "deadline_soon_hours"
        const val DEFAULT_URGENT_HOURS = 24  // 默认24小时内为紧急
        const val DEFAULT_SOON_HOURS = 48    // 默认48小时内为即将截止
        
        // 甘特视图默认时间粒度
        private const val KEY_GANTT_DEFAULT_MODE = "gantt_default_mode"
        const val DEFAULT_GANTT_MODE = "THREE_DAY"  // TODAY, THREE_DAY, SEVEN_DAY, ONE_MONTH
        
        // 高德地图与位置相关
        private const val KEY_AMAP_KEY = "amap_key"
        private const val KEY_LOCATION_FOOTPRINT_ENABLED = "location_footprint_enabled"

        // 桌面小组件筛选
        private const val KEY_WIDGET_FILTER_PREFIX = "widget_filter_"
        const val WIDGET_FILTER_ALL = "ALL"
        const val WIDGET_FILTER_TODAY = "TODAY"
        const val WIDGET_FILTER_CAT_PREFIX = "CAT_"

        // 日历互通与系统日历同步设置
        private const val KEY_CALENDAR_TARGET_ID = "calendar_target_id"
        private const val KEY_CALENDAR_TARGET_NAME = "calendar_target_name"
        private const val KEY_CALENDAR_AUTO_PUSH = "calendar_auto_push"
        private const val KEY_CALENDAR_PUSH_INCLUDE_DONE = "calendar_push_include_done"
        private const val KEY_CALENDAR_PULL_DAYS_AHEAD = "calendar_pull_days_ahead"
        private const val KEY_CALENDAR_SYNC_REMINDERS = "calendar_sync_reminders"
        private const val KEY_ICS_EXPORT_INCLUDE_DONE = "ics_export_include_done"
        private const val KEY_CALENDAR_LAST_PUSH_AT = "calendar_last_push_at"
        private const val KEY_CALENDAR_LAST_PULL_AT = "calendar_last_pull_at"
        private const val KEY_SCHEDULE_ADVICE_USE_CALENDAR = "schedule_advice_use_calendar"
        const val DEFAULT_CALENDAR_TARGET_ID = -1L
        const val DEFAULT_CALENDAR_PULL_DAYS = 30
    }

    // ========== AI 配置 ==========

    fun getApiKey(): String? {
        return getApiKey(getAiProvider())
    }

    fun getApiKey(providerId: String): String? {
        val scoped = prefs.getString("$KEY_API_KEY_PREFIX$providerId", null)
        return scoped ?: if (providerId == getAiProvider()) prefs.getString(KEY_API_KEY, null) else null
    }

    fun saveApiKey(apiKey: String) {
        saveApiKey(getAiProvider(), apiKey)
    }

    fun saveApiKey(providerId: String, apiKey: String) {
        val editor = prefs.edit().putString("$KEY_API_KEY_PREFIX$providerId", apiKey)
        if (providerId == getAiProvider()) {
            editor.putString(KEY_API_KEY, apiKey)
        }
        editor.apply()
    }

    fun getAiProvider(): String {
        return prefs.getString(KEY_AI_PROVIDER, DEFAULT_AI_PROVIDER) ?: DEFAULT_AI_PROVIDER
    }

    fun saveAiProvider(provider: String) {
        prefs.edit().putString(KEY_AI_PROVIDER, provider).apply()
    }

    fun getAiModel(): String {
        return prefs.getString(KEY_AI_MODEL, DEFAULT_AI_MODEL)?.trim() ?: DEFAULT_AI_MODEL
    }

    fun saveAiModel(model: String) {
        prefs.edit().putString(KEY_AI_MODEL, model.trim()).apply()
    }

    fun getCustomBaseUrl(): String {
        return prefs.getString(KEY_CUSTOM_BASE_URL, DEFAULT_CUSTOM_BASE_URL)
            ?.trim()
            ?: ""
    }

    fun saveCustomBaseUrl(baseUrl: String) {
        prefs.edit().putString(KEY_CUSTOM_BASE_URL, baseUrl.trim()).apply()
    }

    /**
     * 校验当前选中的 AI 配置是否完整；可用返回 null，否则返回提示信息。
     */
    fun getAiConfigError(overrideApiKey: String? = null): String? {
        val providerId = getAiProvider()
        val apiKey = overrideApiKey?.takeIf { it.isNotBlank() && it != "DEMO_KEY" }
            ?: getApiKey(providerId).orEmpty()
        if (apiKey.isBlank() && providerId != "custom_openai") {
            return "未设置 API Key，请在设置中配置"
        }
        if (providerId == "custom_openai") {
            if (getCustomBaseUrl().isBlank()) {
                return "请先在设置中填写 OpenAI 兼容接口的 Base URL"
            }
            if (getAiModel().isBlank()) {
                return "请先在设置中填写自定义接口的模型 ID"
            }
        }
        return null
    }

    fun isMultimodalEnabled(
        providerId: String = getAiProvider(),
        modelId: String = getAiModel()
    ): Boolean {
        val key = "${KEY_MULTIMODAL_PREFIX}${providerId}_${modelId}"
        if (prefs.contains(key)) {
            return prefs.getBoolean(key, false)
        }
        return isDefaultMultimodalModel(modelId)
    }

    fun setMultimodalEnabled(providerId: String, modelId: String, enabled: Boolean) {
        val key = "${KEY_MULTIMODAL_PREFIX}${providerId}_${modelId}"
        prefs.edit().putBoolean(key, enabled).apply()
    }

    fun isDefaultMultimodalModel(modelId: String): Boolean {
        val m = modelId.lowercase()
        return m.contains("deepseek-flash") ||
            m.contains("mimo-v2.6") ||
            m.contains("mimo-v2.5") ||
            m.contains("claude-3") ||
            m.contains("claude-sonnet-4") ||
            m.contains("claude-opus-4") ||
            m.contains("gemini") ||
            m.contains("vl") ||
            m.contains("vision") ||
            m.contains("qvq")
    }
    
    // ========== 动态获取的模型 ==========
    fun saveFetchedModels(providerId: String, models: List<Pair<String, String>>) {
        val jsonArray = org.json.JSONArray()
        models.forEach { (id, name) ->
            val obj = org.json.JSONObject()
            obj.put("id", id)
            obj.put("name", name)
            jsonArray.put(obj)
        }
        prefs.edit().putString("fetched_models_$providerId", jsonArray.toString()).apply()
    }
    
    fun clearFetchedModels(providerId: String) {
        prefs.edit().remove("fetched_models_$providerId").apply()
    }

    fun getFetchedModels(providerId: String): List<Pair<String, String>>? {
        val jsonStr = prefs.getString("fetched_models_$providerId", null) ?: return null
        try {
            val jsonArray = org.json.JSONArray(jsonStr)
            val models = mutableListOf<Pair<String, String>>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                models.add(obj.getString("id") to obj.getString("name"))
            }
            return models
        } catch (e: Exception) {
            return null
        }
    }
    
    // ========== 自定义模型 ==========
    fun saveCustomModel(providerId: String, modelId: String?) {
        if (modelId == null) {
            prefs.edit().remove("custom_model_$providerId").apply()
        } else {
            prefs.edit().putString("custom_model_$providerId", modelId).apply()
        }
    }

    fun getCustomModel(providerId: String): String? {
        return prefs.getString("custom_model_$providerId", null)
    }
    
    // ========== AI 智能目的地 ==========
    
    // AI 识别目的地开关 (需配合 AMap Key 使用)
    private val KEY_ENABLE_AI_DESTINATION = "enable_ai_destination"
    
    fun isAiDestinationEnabled(): Boolean {
        // 只有配置了 AMap Key 并且开关打开才生效
        val hasAMapKey = !getAMapKey().isNullOrBlank()
        return hasAMapKey && prefs.getBoolean(KEY_ENABLE_AI_DESTINATION, false)
    }
    
    fun setAiDestinationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_AI_DESTINATION, enabled).apply()
    }
    
    // ========== AI 智能助手 (Agent) ==========
    
    private val KEY_ENABLE_AI_AGENT = "enable_ai_agent"
    
    fun isAiAgentEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENABLE_AI_AGENT, false)
    }
    
    fun setAiAgentEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_AI_AGENT, enabled).apply()
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
        return when (providerId) {
            "xunfei-rtasr" -> {
                val appId = getSpeechCredential(providerId, "appId")
                val apiKey = getSpeechCredential(providerId, "apiKey")
                !appId.isNullOrBlank() && !apiKey.isNullOrBlank()
            }
            "mimo-asr" -> {
                val apiKey = getSpeechCredential(providerId, "apiKey")
                !apiKey.isNullOrBlank()
            }
            else -> false
        }
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
    
    /**
     * 获取紧急任务时间范围（小时）
     */
    fun getDeadlineUrgentHours(): Int {
        return prefs.getInt(KEY_DEADLINE_URGENT_HOURS, DEFAULT_URGENT_HOURS)
    }
    
    /**
     * 设置紧急任务时间范围（小时）
     */
    fun setDeadlineUrgentHours(hours: Int) {
        prefs.edit().putInt(KEY_DEADLINE_URGENT_HOURS, hours).apply()
    }
    
    /**
     * 获取即将截止任务时间范围（小时）
     */
    fun getDeadlineSoonHours(): Int {
        return prefs.getInt(KEY_DEADLINE_SOON_HOURS, DEFAULT_SOON_HOURS)
    }
    
    /**
     * 设置即将截止任务时间范围（小时）
     */
    fun setDeadlineSoonHours(hours: Int) {
        prefs.edit().putInt(KEY_DEADLINE_SOON_HOURS, hours).apply()
    }
    
    /**
     * 获取甘特视图默认时间粒度
     */
    fun getGanttDefaultMode(): String {
        return prefs.getString(KEY_GANTT_DEFAULT_MODE, DEFAULT_GANTT_MODE) ?: DEFAULT_GANTT_MODE
    }
    
    /**
     * 设置甘特视图默认时间粒度
     */
    fun setGanttDefaultMode(mode: String) {
        prefs.edit().putString(KEY_GANTT_DEFAULT_MODE, mode).apply()
    }
    
    // ========== 高德地图配置 ==========
    
    fun getAMapKey(): String? {
        return prefs.getString(KEY_AMAP_KEY, null)
    }

    fun saveAMapKey(key: String) {
        prefs.edit().putString(KEY_AMAP_KEY, key).apply()
    }

    // ========== 桌面小组件筛选配置 ==========

    fun getWidgetFilter(appWidgetId: Int): String {
        if (appWidgetId == -1) return WIDGET_FILTER_ALL
        return prefs.getString("$KEY_WIDGET_FILTER_PREFIX$appWidgetId", WIDGET_FILTER_ALL)
            ?: WIDGET_FILTER_ALL
    }

    fun setWidgetFilter(appWidgetId: Int, filter: String) {
        if (appWidgetId == -1) return
        prefs.edit().putString("$KEY_WIDGET_FILTER_PREFIX$appWidgetId", filter).apply()
    }

    fun clearWidgetFilter(appWidgetId: Int) {
        if (appWidgetId == -1) return
        prefs.edit().remove("$KEY_WIDGET_FILTER_PREFIX$appWidgetId").apply()
    }

    // ========== 日历互通与系统同步配置 ==========

    fun getCalendarTargetId(): Long {
        return prefs.getLong(KEY_CALENDAR_TARGET_ID, DEFAULT_CALENDAR_TARGET_ID)
    }

    fun getCalendarTargetName(): String {
        return prefs.getString(KEY_CALENDAR_TARGET_NAME, "")?.trim().orEmpty()
    }

    fun setCalendarTarget(calendarId: Long, calendarName: String) {
        prefs.edit()
            .putLong(KEY_CALENDAR_TARGET_ID, calendarId)
            .putString(KEY_CALENDAR_TARGET_NAME, calendarName.trim())
            .apply()
    }

    fun isCalendarAutoPushEnabled(): Boolean {
        return prefs.getBoolean(KEY_CALENDAR_AUTO_PUSH, false)
    }

    fun setCalendarAutoPushEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALENDAR_AUTO_PUSH, enabled).apply()
    }

    fun isCalendarPushIncludeDone(): Boolean {
        return prefs.getBoolean(KEY_CALENDAR_PUSH_INCLUDE_DONE, false)
    }

    fun setCalendarPushIncludeDone(include: Boolean) {
        prefs.edit().putBoolean(KEY_CALENDAR_PUSH_INCLUDE_DONE, include).apply()
    }

    fun getCalendarPullDaysAhead(): Int {
        return prefs.getInt(KEY_CALENDAR_PULL_DAYS_AHEAD, DEFAULT_CALENDAR_PULL_DAYS)
            .coerceIn(1, 365)
    }

    fun setCalendarPullDaysAhead(days: Int) {
        prefs.edit().putInt(KEY_CALENDAR_PULL_DAYS_AHEAD, days.coerceIn(1, 365)).apply()
    }

    fun isCalendarSyncReminders(): Boolean {
        return prefs.getBoolean(KEY_CALENDAR_SYNC_REMINDERS, true)
    }

    fun setCalendarSyncReminders(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALENDAR_SYNC_REMINDERS, enabled).apply()
    }

    fun isIcsExportIncludeDone(): Boolean {
        return prefs.getBoolean(KEY_ICS_EXPORT_INCLUDE_DONE, true)
    }

    fun setIcsExportIncludeDone(include: Boolean) {
        prefs.edit().putBoolean(KEY_ICS_EXPORT_INCLUDE_DONE, include).apply()
    }

    fun getCalendarLastPushAt(): Long = prefs.getLong(KEY_CALENDAR_LAST_PUSH_AT, 0L)

    fun setCalendarLastPushAt(timestamp: Long) {
        prefs.edit().putLong(KEY_CALENDAR_LAST_PUSH_AT, timestamp).apply()
    }

    fun getCalendarLastPullAt(): Long = prefs.getLong(KEY_CALENDAR_LAST_PULL_AT, 0L)

    fun setCalendarLastPullAt(timestamp: Long) {
        prefs.edit().putLong(KEY_CALENDAR_LAST_PULL_AT, timestamp).apply()
    }

    /**
     * AI 日程规划是否参考手机系统日历中的忙碌日程（默认开启）
     */
    fun isScheduleAdviceUseCalendarEnabled(): Boolean {
        return prefs.getBoolean(KEY_SCHEDULE_ADVICE_USE_CALENDAR, true)
    }

    fun setScheduleAdviceUseCalendarEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCHEDULE_ADVICE_USE_CALENDAR, enabled).apply()
    }

    // ========== 地点足迹记录偏好 ==========

    fun isLocationFootprintEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOCATION_FOOTPRINT_ENABLED, true)
    }

    fun setLocationFootprintEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCATION_FOOTPRINT_ENABLED, enabled).apply()
    }
}
