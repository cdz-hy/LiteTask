package com.litetask.app.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {
    private const val FILE_NAME = "secure_app_prefs"
    private const val KEY_API_TOKEN = "user_ai_api_token"

    private fun getPrefs(context: Context) = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // 如果设备不支持加密SP，回退策略需根据业务决定
        null 
    }

    fun saveApiKey(context: Context, token: String) {
        getPrefs(context)?.edit()?.putString(KEY_API_TOKEN, token)?.apply()
    }

    fun getApiKey(context: Context): String? {
        return getPrefs(context)?.getString(KEY_API_TOKEN, null)
    }
}