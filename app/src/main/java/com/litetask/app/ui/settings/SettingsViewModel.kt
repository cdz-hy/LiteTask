package com.litetask.app.ui.settings

import androidx.lifecycle.ViewModel
import com.litetask.app.data.local.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager
) : ViewModel() {

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
}
