package com.litetask.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
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
import com.litetask.app.reminder.FloatingReminderService
import com.litetask.app.reminder.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSave: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // ========== 加载状态 ==========
    var isDataLoaded by remember { mutableStateOf(false) }
    
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
        
        // 标记数据加载完成
        isDataLoaded = true
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
        // 等待数据加载完成后再渲染内容，避免 label 动画
        if (!isDataLoaded) {
            return@Scaffold
        }
        
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
                        // 允许保存空值，空值表示不使用 AI 分析功能
                        viewModel.saveApiKey(apiKey)
                        viewModel.saveAiProvider(selectedAiProvider)
                        Toast.makeText(context, context.getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.ai_save_settings))
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
                        // 允许保存空值，空值表示不使用该服务
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
            
            // ========== 提醒设置卡片 ==========
            ReminderSettingsCard(context)
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 提醒设置卡片
 * 引导用户开启必要的系统权限，确保提醒功能正常工作
 */
@Composable
private fun ReminderSettingsCard(
    context: android.content.Context,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    // 检查各项权限状态
    var hasNotificationPermission by remember { mutableStateOf(true) }
    var hasExactAlarmPermission by remember { mutableStateOf(true) }
    var hasOverlayPermission by remember { mutableStateOf(true) }
    
    // 铃声和震动开关状态
    var soundEnabled by remember { mutableStateOf(viewModel.isReminderSoundEnabled()) }
    var vibrationEnabled by remember { mutableStateOf(viewModel.isReminderVibrationEnabled()) }
    
    // 用于触发权限状态刷新
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // 监听生命周期，当页面重新获得焦点时刷新权限状态
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // 刷新权限状态
    LaunchedEffect(refreshTrigger) {
        hasNotificationPermission = PermissionHelper.hasNotificationPermission(context)
        hasExactAlarmPermission = PermissionHelper.canScheduleExactAlarms(context)
        hasOverlayPermission = FloatingReminderService.canDrawOverlays(context)
    }
    
    SettingsCard(
        title = "提醒设置",
        icon = Icons.Default.Notifications
    ) {
        // 说明文字
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "部分手机系统需要手动开启以下权限，否则应用不在后台时提醒可能无法正常触发",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // 权限状态列表
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 通知权限
            PermissionItem(
                title = "通知权限",
                description = "允许显示提醒通知",
                isGranted = hasNotificationPermission,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }
                }
            )
            
            // 精确闹钟权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PermissionItem(
                    title = "精确闹钟权限",
                    description = "允许设置精确的提醒时间",
                    isGranted = hasExactAlarmPermission,
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                )
            }
            
            // 悬浮窗权限
            PermissionItem(
                title = "悬浮窗权限",
                description = "允许显示悬浮提醒弹窗",
                isGranted = hasOverlayPermission,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 提醒方式设置
        Text(
            text = "提醒方式",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 铃声开关
        SwitchItem(
            icon = Icons.Default.VolumeUp,
            title = "提醒铃声",
            description = "悬浮提醒时播放闹钟铃声",
            checked = soundEnabled,
            onCheckedChange = {
                soundEnabled = it
                viewModel.setReminderSoundEnabled(it)
            }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 震动开关
        SwitchItem(
            icon = Icons.Default.Vibration,
            title = "提醒震动",
            description = "悬浮提醒时震动提示",
            checked = vibrationEnabled,
            onCheckedChange = {
                vibrationEnabled = it
                viewModel.setReminderVibrationEnabled(it)
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 跳转到应用详情设置
        Text(
            text = "如果提醒仍然无法正常工作，请在系统设置中：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "• 开启「自启动」权限\n• 将后台限制设为「无限制」\n• 关闭应用「电池优化」",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = {
                // 跳转到应用详情设置页面
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("打开应用设置")
        }
    }
}

/**
 * 开关项组件
 */
@Composable
private fun SwitchItem(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 权限项组件
 */
@Composable
private fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (isGranted) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "已开启",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(24.dp)
            )
        } else {
            TextButton(onClick = onClick) {
                Text("去开启")
            }
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