package com.litetask.app.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litetask.app.data.repository.BackupRepository
import com.litetask.app.data.repository.RestoreResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.io.InputStream
import java.io.OutputStream

@HiltViewModel
class DataBackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState = _uiState.asStateFlow()

    suspend fun exportData(outputStream: OutputStream) {
        _uiState.value = BackupUiState.Loading
        try {
            val json = backupRepository.createBackupJson()
            outputStream.use { 
                it.write(json.toByteArray()) 
            }
            _uiState.value = BackupUiState.Success("数据导出成功")
        } catch (e: Exception) {
            _uiState.value = BackupUiState.Error(e.message ?: "导出失败")
        }
    }

    suspend fun importData(inputStream: InputStream) {
        _uiState.value = BackupUiState.Importing
        try {
            val json = inputStream.bufferedReader().use { it.readText() }
            val result = backupRepository.restoreBackup(json)
            when (result) {
                is RestoreResult.Success -> {
                    _uiState.value = BackupUiState.Success("成功导入 ${result.importedCount} 条数据，跳过 ${result.skippedCount} 条重复数据")
                }
                is RestoreResult.Error -> {
                    _uiState.value = BackupUiState.Error(result.exception.message ?: "导入失败")
                }
            }
        } catch (e: Exception) {
            _uiState.value = BackupUiState.Error("文件读取失败: ${e.message}")
        }
    }
    
    fun resetState() {
        _uiState.value = BackupUiState.Idle
    }
}

sealed class BackupUiState {
    data object Idle : BackupUiState()
    data object Loading : BackupUiState()
    data object Importing : BackupUiState()
    data class Success(val message: String) : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}
