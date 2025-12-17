package com.litetask.app.ui.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.litetask.app.R
import com.litetask.app.data.speech.CredentialField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSave: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // ========== AI 配置状态 ==========
    var apiKey by remember { mutableStateOf("") }
    var selectedAiProvider by remember { mutableStateOf("deepseek-v3.2") }
    var aiProviderExpanded by remember { mutableStateOf(false) }
    val aiConnectionState by viewModel.aiConnectionState.collectAsState()
    
    val aiProviders = listOf("deepseek-v3.2" to "DeepSeek V3.2")
    
    // ========== 语音识别配置状态 ==========
    var selectedSpeechProvider by remember { mutableStateOf("xunfei-rtasr") }
    var speechProviderExpanded by remember { mutableStateOf(false) }
    val speechCredentials = remember { mutableStateMapOf<String, String>() }
    val speechConnectionState by viewModel.speechConnectionState.collectAsState()
    
    val speechProviders = viewModel.getSupportedSpeechProviders()
    val speechCredentialFields = remember(selectedSpeechProvider) {
        viewModel.getSpeechCredentialFields(selectedSpeechProvider)
    }
    
    // 初始化数据
    LaunchedEffect(Unit) {
        apiKey = viewModel.getApiKey() ?: ""
        selectedAiProvider = viewModel.getAiProvider()
        selectedSpeechProvider = viewModel.getSpeechProvider()
        
        // 加载语音识别凭证
        val savedCredentials = viewModel.getSpeechCredentials(selectedSpeechProvider)
        speechCredentials.clear()
        speechCredentials.putAll(savedCredentials)
    }
    
    // 当语音提供商改变时，加载对应的凭证
    LaunchedEffect(selectedSpeechProvider) {
        val savedCredentials = viewModel.getSpeechCredentials(selectedSpeechProvider)
        speechCredentials.clear()
        speechCredentials.putAll(savedCredentials)
        viewModel.resetSpeechConnectionState()
    }

    // 当 AI Key 改变时，重置测试状态
    LaunchedEffect(apiKey, selectedAiProvider) {
        if (aiConnectionState !is SettingsViewModel.ConnectionState.Idle) {
            viewModel.resetConnectionState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ========== AI 配置卡片 ==========
            SettingsCard(
                title = stringResource(R.string.ai_config),
                icon = Icons.Default.SmartToy
            ) {
                // AI 提供商选择
                ExposedDropdownMenuBox(
                    expanded = aiProviderExpanded,
                    onExpandedChange = { aiProviderExpanded = it }
                ) {
                    OutlinedTextField(
                        value = aiProviders.find { it.first == selectedAiProvider }?.second ?: "DeepSeek V3.2",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.ai_provider)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aiProviderExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = aiProviderExpanded,
                        onDismissRequest = { aiProviderExpanded = false }
                    ) {
                        aiProviders.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedAiProvider = value
                                    aiProviderExpanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // API Key 输入框
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.api_key)) },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = aiConnectionState is SettingsViewModel.ConnectionState.Error
                )
                
                // 测试状态显示
                ConnectionStateIndicator(state = aiConnectionState)
                
                // 辅助说明与测试按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.key_stored_locally),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    TextButton(
                        onClick = { viewModel.testConnection(apiKey, selectedAiProvider) },
                        enabled = apiKey.isNotBlank() && aiConnectionState !is SettingsViewModel.ConnectionState.Testing
                    ) {
                        Text(stringResource(R.string.test_connection))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { 
                        if (apiKey.isBlank()) {
                            Toast.makeText(context, context.getString(R.string.please_enter_api_key), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.saveApiKey(apiKey)
                        viewModel.saveAiProvider(selectedAiProvider)
                        Toast.makeText(context, context.getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.save_settings))
                }
            }
            
            // ========== 语音识别配置卡片 ==========
            SettingsCard(
                title = stringResource(R.string.speech_config),
                icon = Icons.Default.Mic
            ) {
                
                // 语音识别说明文字
                Text(
                    text = "此项用于改善语音识别效果，若不设置将调用安卓自带STT",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // 语音识别服务选择
                ExposedDropdownMenuBox(
                    expanded = speechProviderExpanded,
                    onExpandedChange = { speechProviderExpanded = it }
                ) {
                    OutlinedTextField(
                        value = speechProviders.find { it.first == selectedSpeechProvider }?.second ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.speech_provider)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = speechProviderExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = speechProviderExpanded,
                        onDismissRequest = { speechProviderExpanded = false }
                    ) {
                        speechProviders.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedSpeechProvider = value
                                    speechProviderExpanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                // 动态凭证输入字段
                speechCredentialFields.forEach { field ->
                    OutlinedTextField(
                        value = speechCredentials[field.id] ?: "",
                        onValueChange = { speechCredentials[field.id] = it },
                        label = { Text(field.displayName) },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        visualTransformation = if (field.isSecret) PasswordVisualTransformation() else VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = speechConnectionState is SettingsViewModel.ConnectionState.Error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // 测试状态显示
                ConnectionStateIndicator(state = speechConnectionState)
                
                // 辅助说明与测试按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.key_stored_locally),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    TextButton(
                        onClick = { 
                            viewModel.testSpeechConnection(selectedSpeechProvider, speechCredentials.toMap())
                        },
                        enabled = speechCredentialFields.all { field ->
                            !field.isRequired || !speechCredentials[field.id].isNullOrBlank()
                        } && speechConnectionState !is SettingsViewModel.ConnectionState.Testing
                    ) {
                        Text(stringResource(R.string.speech_test_connection))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { 
                        // 检查必填字段
                        val missingFields = speechCredentialFields.filter { field ->
                            field.isRequired && speechCredentials[field.id].isNullOrBlank()
                        }
                        if (missingFields.isNotEmpty()) {
                            Toast.makeText(context, context.getString(R.string.speech_credential_empty), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        viewModel.saveSpeechProvider(selectedSpeechProvider)
                        viewModel.saveSpeechCredentials(selectedSpeechProvider, speechCredentials.toMap())
                        Toast.makeText(context, context.getString(R.string.speech_settings_saved), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.speech_save_settings))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 设置卡片组件
 */
@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            content()
        }
    }
}

/**
 * 连接状态指示器
 */
@Composable
private fun ConnectionStateIndicator(state: SettingsViewModel.ConnectionState) {
    AnimatedVisibility(
        visible = state !is SettingsViewModel.ConnectionState.Idle,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (state) {
                is SettingsViewModel.ConnectionState.Testing -> {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.verifying_connection),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is SettingsViewModel.ConnectionState.Success -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = colorResource(R.color.settings_success),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.connection_success),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorResource(R.color.settings_success)
                    )
                }
                is SettingsViewModel.ConnectionState.Error -> {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {}
            }
        }
    }
}