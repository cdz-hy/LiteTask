package com.litetask.app.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.litetask.app.data.model.Category
import com.litetask.app.ui.components.SegmentedTabBar
import com.litetask.app.ui.util.ColorUtils
import androidx.compose.material3.Switch
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
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
import com.litetask.app.ui.components.CalendarSyncGuideDialog
import com.litetask.app.ui.components.CalendarSyncSettingsPanel
import com.litetask.app.ui.components.rememberCalendarPermissionRequester

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToBackup: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val prefsScrollState = rememberScrollState()
    val servicesScrollState = rememberScrollState()
    var selectedSettingsTab by rememberSaveable { mutableStateOf(0) }
    var aiConfigExpanded by remember { mutableStateOf(true) }
    var speechConfigExpanded by remember { mutableStateOf(true) }
    var amapConfigExpanded by remember { mutableStateOf(true) }
    
    // 防抖状态
    var isNavigating by remember { mutableStateOf(false) }
    
    // ========== 加载状态 ==========
    var isDataLoaded by remember { mutableStateOf(false) }
    
    // ========== AI 配置状态 ==========
    var apiKey by remember { mutableStateOf("") }
    var selectedAiProvider by remember { mutableStateOf("deepseek") }
    var selectedAiModel by remember { mutableStateOf("deepseek-flash") }
    var customBaseUrl by remember { mutableStateOf("") }
    var isMultimodalEnabled by remember { mutableStateOf(true) }
    var aiProviderExpanded by remember { mutableStateOf(false) }
    var aiModelExpanded by remember { mutableStateOf(false) }
    var showCustomModelDialog by remember { mutableStateOf(false) }
    var customModelInput by remember { mutableStateOf("") }
    val aiConnectionState by viewModel.aiConnectionState.collectAsState()
    // 触发模型列表刷新的状态
    var modelsRefreshTrigger by remember { mutableStateOf(0) }
    
    // 当连接测试成功时，重新加载模型列表（可能拉取到了新模型）
    LaunchedEffect(aiConnectionState) {
        if (aiConnectionState is SettingsViewModel.ConnectionState.Success) {
            modelsRefreshTrigger++
            if (selectedAiProvider == "custom_openai" && selectedAiModel.isBlank()) {
                selectedAiModel = viewModel.getSupportedAiModels("custom_openai")
                    .firstOrNull()?.first.orEmpty()
            }
        }
    }

    val aiProviders = viewModel.getSupportedAiProviders()
    val aiModels = remember(selectedAiProvider, modelsRefreshTrigger) { viewModel.getSupportedAiModels(selectedAiProvider) }
    
    // ========== 语音识别配置状态 ==========
    var selectedSpeechProvider by remember { mutableStateOf("xunfei-rtasr") }
    var speechProviderExpanded by remember { mutableStateOf(false) }
    val speechCredentials = remember { mutableStateMapOf<String, String>() }
    val speechConnectionState by viewModel.speechConnectionState.collectAsState()
    
    // ========== 高德地图配置状态 ==========
    var amapKey by remember { mutableStateOf("") }
    val amapConnectionState by viewModel.amapConnectionState.collectAsState()
    
    // ========== AI 智能目的地与足迹状态 ==========
    var isAiDestinationEnabled by remember { mutableStateOf(false) }
    var isLocationFootprintEnabled by remember { mutableStateOf(true) }
    var isAiAgentEnabled by remember { mutableStateOf(false) }
    
    // ========== 分类管理状态 ==========
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showCalendarGuide by remember { mutableStateOf(false) }
    val requestCalendarPermission = rememberCalendarPermissionRequester(viewModel.calendarSyncStore)

    // ========== 系统定位权限（按需申请） ==========
    fun checkLocationPerm(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }
    var hasLocationPermission by remember { mutableStateOf(checkLocationPerm()) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = checkLocationPerm()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted || checkLocationPerm()
        if (hasLocationPermission) {
            Toast.makeText(context, "已获取系统定位权限，位置识别与足迹记录已就绪", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "未授予系统定位权限，仍可手动输入目的地", Toast.LENGTH_SHORT).show()
        }
    }
    val requestLocationPermissionIfNeeded = {
        if (!checkLocationPerm()) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
    
    val speechProviders = viewModel.getSupportedSpeechProviders()
    val speechCredentialFields = remember(selectedSpeechProvider) {
        viewModel.getSpeechCredentialFields(selectedSpeechProvider)
    }
    
    // 初始化数据
    LaunchedEffect(Unit) {
        selectedAiProvider = viewModel.getAiProvider()
        selectedAiModel = viewModel.getAiModel()
        apiKey = viewModel.getApiKey(selectedAiProvider) ?: ""
        customBaseUrl = viewModel.getCustomBaseUrl()
        isMultimodalEnabled = viewModel.isMultimodalEnabled(selectedAiProvider, selectedAiModel)
        selectedSpeechProvider = viewModel.getSpeechProvider()
        amapKey = viewModel.getAMapKey() ?: ""
        isAiDestinationEnabled = viewModel.isAiDestinationEnabled()
        isLocationFootprintEnabled = viewModel.isLocationFootprintEnabled()
        isAiAgentEnabled = viewModel.isAiAgentEnabled()
        
        // 加载语音识别凭证
        val savedCredentials = viewModel.getSpeechCredentials(selectedSpeechProvider)
        speechCredentials.clear()
        speechCredentials.putAll(savedCredentials)

        // 若凭证已完整配置，默认折叠输入表单仅保留状态摘要，降低视觉负担
        val initialAiReady = if (selectedAiProvider == "custom_openai") {
            customBaseUrl.isNotBlank() && selectedAiModel.isNotBlank()
        } else {
            apiKey.isNotBlank()
        }
        val initialSpeechFields = viewModel.getSpeechCredentialFields(selectedSpeechProvider)
        val initialSpeechReady = initialSpeechFields.all { field ->
            !field.isRequired || !savedCredentials[field.id].isNullOrBlank()
        }
        val initialAmapReady = amapKey.isNotBlank()
        aiConfigExpanded = !initialAiReady
        speechConfigExpanded = !initialSpeechReady
        amapConfigExpanded = !initialAmapReady
        
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

    // 当 AI Key 或 Provider/Model/BaseURL 改变时，重置测试状态
    LaunchedEffect(apiKey, selectedAiProvider, selectedAiModel, customBaseUrl) {
        if (aiConnectionState !is SettingsViewModel.ConnectionState.Idle) {
            viewModel.resetConnectionState()
        }
    }

    // 当 Provider/Model 改变时，刷新多模态开关状态
    LaunchedEffect(selectedAiProvider, selectedAiModel) {
        if (isDataLoaded) {
            isMultimodalEnabled = viewModel.isMultimodalEnabled(selectedAiProvider, selectedAiModel)
        }
    }

    // 当 AMap Key 改变时，重置测试状态
    LaunchedEffect(amapKey) {
        if (amapConnectionState !is SettingsViewModel.ConnectionState.Idle) {
            viewModel.resetAMapConnectionState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!isNavigating) {
                                isNavigating = true
                                onBack()
                            }
                        },
                        enabled = !isNavigating
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        // 等待数据加载完成后再渲染内容，避免 label 动画
        if (!isDataLoaded) {
            return@Scaffold
        }

        val isAiConfigured = if (selectedAiProvider == "custom_openai") {
            customBaseUrl.isNotBlank() && selectedAiModel.isNotBlank()
        } else {
            apiKey.isNotBlank()
        }
        val isSpeechConfigured = speechCredentialFields.all { field ->
            !field.isRequired || !speechCredentials[field.id].isNullOrBlank()
        }
        val isAmapConfigured = amapKey.isNotBlank()
        val aiProviderName = aiProviders.find { it.first == selectedAiProvider }?.second ?: "AI"
        val speechProviderName = speechProviders.find { it.first == selectedSpeechProvider }?.second ?: "在线语音"

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 顶部两栏分组切换：分离“常规偏好”和“AI 与外部接口服务”
            SegmentedTabBar(
                tabs = listOf("常规偏好", "AI 与接口服务"),
                selectedIndex = selectedSettingsTab,
                onSelectTab = { selectedSettingsTab = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )

            Crossfade(
                targetState = selectedSettingsTab,
                label = "SettingsTabCrossfade",
                modifier = Modifier.weight(1f)
            ) { tabIndex ->
                if (tabIndex == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(prefsScrollState)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ========== 用户偏好设置卡片 ==========
                        UserPreferencesCard(viewModel)

                        // ========== 分类设置卡片 ==========
                        CategorySettingsCard(
                            onManageCategories = { showCategoryDialog = true }
                        )

                        // ========== 提醒方式设置卡片 ==========
                        ReminderMethodsCard(viewModel)

                        // ========== 日历导出与手机日历同步卡片 ==========
                        SettingsCard(
                            title = "日历导出与手机日历同步",
                            icon = Icons.Default.CalendarMonth
                        ) {
                            Text(
                                text = "设置通用日历文件导出范围，以及与手机系统日历双向同步的偏好。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            CalendarSyncSettingsPanel(
                                store = viewModel.calendarSyncStore,
                                onRequestCalendarPermission = { requestCalendarPermission {} },
                                onOpenGuide = { showCalendarGuide = true }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = onNavigateToBackup,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("前往数据管理执行日历文件导入导出 / 立即同步")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(servicesScrollState)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ServiceStatusOverviewStrip(
                            aiReady = isAiConfigured,
                            speechReady = isSpeechConfigured,
                            mapReady = isAmapConfigured
                        )

                        // ========== AI 配置卡片 ==========
                        SettingsCard(
                            title = stringResource(R.string.ai_config),
                            icon = Icons.Default.SmartToy
                        ) {
                            CollapsibleCredentialSection(
                                title = "接口与模型凭证（修改后请点击保存）",
                                summary = if (isAiConfigured) {
                                    "已配置 · $aiProviderName" +
                                        if (selectedAiModel.isNotBlank()) " ($selectedAiModel)" else ""
                                } else {
                                    "未配置 · 点击填写密钥与模型"
                                },
                                configured = isAiConfigured,
                                expanded = aiConfigExpanded,
                                onToggleExpand = { aiConfigExpanded = !aiConfigExpanded }
                            ) {
                                // AI 提供商选择
                                ExposedDropdownMenuBox(
                                    expanded = aiProviderExpanded,
                                    onExpandedChange = { aiProviderExpanded = it },
                                ) {
                                    OutlinedTextField(
                                        value = aiProviderName,
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
                                                    apiKey = viewModel.getApiKey(value) ?: ""
                                                    val newModels = viewModel.getSupportedAiModels(value)
                                                    if (newModels.none { it.first == selectedAiModel }) {
                                                        selectedAiModel = newModels.firstOrNull()?.first ?: ""
                                                    }
                                                    isMultimodalEnabled = viewModel.isMultimodalEnabled(value, selectedAiModel)
                                                }
                                            )
                                        }
                                    }
                                }

                                // 仅当选择 OpenAI 兼容接口时显示 Base URL 输入框
                                AnimatedVisibility(
                                    visible = selectedAiProvider == "custom_openai",
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = customBaseUrl,
                                            onValueChange = { customBaseUrl = it },
                                            label = { Text("接口地址 (Base URL)") },
                                            placeholder = { Text("https://your-endpoint.com/v1") },
                                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                                            supportingText = {
                                                Text("填写服务商提供的请求地址（支持各类第三方中转或本地部署接口）")
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // AI 模型选择：custom_openai 允许直接自由输入，也可通过测试连接拉取列表后选择；官方提供方走预设下拉
                                if (selectedAiProvider == "custom_openai") {
                                    OutlinedTextField(
                                        value = selectedAiModel,
                                        onValueChange = { selectedAiModel = it },
                                        label = { Text("模型名称 (Model ID)") },
                                        placeholder = { Text("输入要使用的模型名称（也可点下方测试连接自动获取）") },
                                        singleLine = true,
                                        trailingIcon = if (aiModels.isNotEmpty()) {
                                            {
                                                Box {
                                                    IconButton(onClick = { aiModelExpanded = true }) {
                                                        Icon(
                                                            imageVector = Icons.Default.ArrowDropDown,
                                                            contentDescription = "选择已拉取的模型"
                                                        )
                                                    }
                                                    DropdownMenu(
                                                        expanded = aiModelExpanded,
                                                        onDismissRequest = { aiModelExpanded = false },
                                                        modifier = Modifier.heightIn(max = 320.dp)
                                                    ) {
                                                        AiModelMenuItems(
                                                            aiModels = aiModels,
                                                            resetLabel = "清空缓存模型列表",
                                                            onSelect = { value ->
                                                                selectedAiModel = value
                                                                aiModelExpanded = false
                                                            },
                                                            onDeleteCustom = { value ->
                                                                viewModel.deleteCustomModel(selectedAiProvider)
                                                                if (selectedAiModel == value) {
                                                                    selectedAiModel = aiModels.firstOrNull { !it.second.endsWith("(自定义)") }?.first ?: ""
                                                                }
                                                                modelsRefreshTrigger++
                                                            },
                                                            onResetModels = {
                                                                viewModel.clearFetchedModels(selectedAiProvider)
                                                                modelsRefreshTrigger++
                                                                aiModelExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        } else null,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    ExposedDropdownMenuBox(
                                        expanded = aiModelExpanded,
                                        onExpandedChange = { aiModelExpanded = it },
                                    ) {
                                        OutlinedTextField(
                                            value = aiModels.find { it.first == selectedAiModel }?.second ?: selectedAiModel,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("AI 模型") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aiModelExpanded) },
                                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                            modifier = Modifier.fillMaxWidth().menuAnchor()
                                        )

                                        ExposedDropdownMenu(
                                            expanded = aiModelExpanded,
                                            onDismissRequest = { aiModelExpanded = false },
                                            modifier = Modifier.heightIn(max = 320.dp)
                                        ) {
                                            AiModelMenuItems(
                                                aiModels = aiModels,
                                                resetLabel = "恢复预设模型列表",
                                                onSelect = { value ->
                                                    selectedAiModel = value
                                                    aiModelExpanded = false
                                                },
                                                onDeleteCustom = { value ->
                                                    viewModel.deleteCustomModel(selectedAiProvider)
                                                    if (selectedAiModel == value) {
                                                        selectedAiModel = aiModels.firstOrNull { !it.second.endsWith("(自定义)") }?.first ?: ""
                                                    }
                                                    modelsRefreshTrigger++
                                                },
                                                onResetModels = {
                                                    viewModel.clearFetchedModels(selectedAiProvider)
                                                    modelsRefreshTrigger++
                                                    aiModelExpanded = false
                                                },
                                                onCustomModelClick = {
                                                    aiModelExpanded = false
                                                    customModelInput = if (aiModels.none { it.first == selectedAiModel }) {
                                                        selectedAiModel
                                                    } else {
                                                        ""
                                                    }
                                                    showCustomModelDialog = true
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
                                        text = "密钥仅保存在本地设备",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    TextButton(
                                        onClick = {
                                            viewModel.testConnection(
                                                apiKey = apiKey,
                                                providerId = selectedAiProvider,
                                                modelId = selectedAiModel,
                                                customBaseUrl = if (selectedAiProvider == "custom_openai") customBaseUrl else null
                                            )
                                        },
                                        enabled = (apiKey.isNotBlank() || selectedAiProvider == "custom_openai") &&
                                            aiConnectionState !is SettingsViewModel.ConnectionState.Testing
                                    ) {
                                        Text(stringResource(R.string.test_connection))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = {
                                        val trimmedModel = selectedAiModel.trim()
                                        val trimmedBaseUrl = customBaseUrl.trim()
                                        if (selectedAiProvider == "custom_openai") {
                                            if (trimmedBaseUrl.isBlank()) {
                                                Toast.makeText(context, "请先填写 Base URL", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (trimmedModel.isBlank()) {
                                                Toast.makeText(context, "请先填写模型 ID", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            viewModel.saveCustomBaseUrl(trimmedBaseUrl)
                                            viewModel.saveCustomModel("custom_openai", trimmedModel)
                                        }
                                        // 保存当前服务商独立 Key 与模型配置
                                        viewModel.saveAiProvider(selectedAiProvider)
                                        viewModel.saveAiModel(trimmedModel)
                                        viewModel.saveApiKey(selectedAiProvider, apiKey)
                                        viewModel.setMultimodalEnabled(selectedAiProvider, trimmedModel, isMultimodalEnabled)
                                        modelsRefreshTrigger++
                                        Toast.makeText(context, context.getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.ai_save_settings))
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))

                            InstantToggleSectionHeader("AI 能力开关（切换后立即生效）")
                            Spacer(modifier = Modifier.height(8.dp))

                            // 多模态视觉输入开关（按当前服务商+模型记忆）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "支持看图识别（上传图片建任务）",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "开启后可在录入任务或拆解子任务时上传截图或照片让 AI 识别",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isMultimodalEnabled,
                                    onCheckedChange = { checked ->
                                        isMultimodalEnabled = checked
                                        viewModel.setMultimodalEnabled(selectedAiProvider, selectedAiModel, checked)
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Agent 模式开关
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.enable_ai_agent),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(R.string.enable_ai_agent_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isAiAgentEnabled,
                                    onCheckedChange = { checked ->
                                        isAiAgentEnabled = checked
                                        viewModel.setAiAgentEnabled(checked)
                                    }
                                )
                            }
                        }

                        // ========== 语音识别配置卡片 ==========
                        SettingsCard(
                            title = stringResource(R.string.speech_config),
                            icon = Icons.Default.Mic
                        ) {
                            Text(
                                text = "配置第三方在线语音接口可明显提高长段语音与口语的识别准确率；未配置时将使用手机系统自带的语音识别",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            CollapsibleCredentialSection(
                                title = "在线语音服务凭证（修改后请点击保存）",
                                summary = if (isSpeechConfigured) {
                                    "已配置 · $speechProviderName"
                                } else {
                                    "未配置 · 默认使用系统语音识别"
                                },
                                configured = isSpeechConfigured,
                                expanded = speechConfigExpanded,
                                onToggleExpand = { speechConfigExpanded = !speechConfigExpanded }
                            ) {
                                // 语音识别服务选择
                                ExposedDropdownMenuBox(
                                    expanded = speechProviderExpanded,
                                    onExpandedChange = { speechProviderExpanded = it },
                                ) {
                                    OutlinedTextField(
                                        value = speechProviderName,
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
                                        text = "密钥仅保存在本地设备",
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
                        }

                        // ========== 高德地图配置卡片 ==========
                        SettingsCard(
                            title = "地图与定位配置",
                            icon = Icons.Default.Map
                        ) {
                            Text(
                                text = "用于开启任务路线规划与地图预览功能",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            CollapsibleCredentialSection(
                                title = "高德地图 Web Key（修改后请点击保存）",
                                summary = if (isAmapConfigured) {
                                    "已配置高德 Web 服务密钥"
                                } else {
                                    "未配置 · 点击填入密钥以启用路线规划"
                                },
                                configured = isAmapConfigured,
                                expanded = amapConfigExpanded,
                                onToggleExpand = { amapConfigExpanded = !amapConfigExpanded }
                            ) {
                                OutlinedTextField(
                                    value = amapKey,
                                    onValueChange = { amapKey = it },
                                    label = { Text("高德开放平台密钥 (Key)") },
                                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("在此粘贴您的高德 Web 服务 Key") },
                                    singleLine = true,
                                    isError = amapConnectionState is SettingsViewModel.ConnectionState.Error
                                )

                                // 测试状态显示
                                ConnectionStateIndicator(state = amapConnectionState)

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "密钥仅保存在本地设备",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Row {
                                        TextButton(
                                            onClick = { viewModel.testAMapConnection(amapKey) },
                                            enabled = amapKey.isNotBlank() && amapConnectionState !is SettingsViewModel.ConnectionState.Testing
                                        ) {
                                            Text("测试连接")
                                        }

                                        TextButton(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.amap.com/dev/key/app"))
                                                context.startActivity(intent)
                                            }
                                        ) {
                                            Text("获取 Key")
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = {
                                        viewModel.saveAMapKey(amapKey)
                                        Toast.makeText(context, "地图设置已保存", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("保存地图设置")
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))

                            InstantToggleSectionHeader("定位与行程开关（切换后立即生效）")
                            Spacer(modifier = Modifier.height(8.dp))

                            // 智能目的地开关
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "识别任务目的地",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (amapKey.isBlank()) "需先配置高德地图密钥" else "自动搜索并挂载任务的地点与路线卡片",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (amapKey.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isAiDestinationEnabled && amapKey.isNotBlank(),
                                    onCheckedChange = { checked ->
                                        if (amapKey.isBlank()) {
                                            Toast.makeText(context, "请先配置高德地图密钥", Toast.LENGTH_SHORT).show()
                                        } else {
                                            isAiDestinationEnabled = checked
                                            viewModel.setAiDestinationEnabled(checked) // 自动保存
                                            if (checked) requestLocationPermissionIfNeeded()
                                        }
                                    },
                                    enabled = amapKey.isNotBlank()
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))

                            // 常去地点足迹记录开关
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "记录常去地点足迹",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "仅在您确认添加带地点的任务时，在本地统计常在地和常去地，用于丰富个人画像与出行建议（可随时在个人数据页查看或清空）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isLocationFootprintEnabled,
                                    onCheckedChange = { checked ->
                                        isLocationFootprintEnabled = checked
                                        viewModel.setLocationFootprintEnabled(checked)
                                        if (checked) requestLocationPermissionIfNeeded()
                                    }
                                )
                            }

                            if ((isAiDestinationEnabled || isLocationFootprintEnabled) && !hasLocationPermission) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = { requestLocationPermissionIfNeeded() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("尚未开启系统定位权限，点击授权以自动识别出发地")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    if (showCategoryDialog) {
        CategoryManagementDialog(
            viewModel = viewModel,
            onDismiss = { showCategoryDialog = false }
        )
    }

    if (showCalendarGuide) {
        CalendarSyncGuideDialog(onDismiss = { showCalendarGuide = false })
    }
    
    // 自定义模型输入对话框
    if (showCustomModelDialog) {
        val providerName = aiProviders.find { it.first == selectedAiProvider }?.second ?: "AI"
        val placeholderText = when (selectedAiProvider) {
            "deepseek" -> "deepseek-v4-pro"
            "xiaomi" -> "mimo-v2.6-pro"
            "custom_openai" -> "请输入服务商提供的模型名称"
            else -> "model-name"
        }
        
        AlertDialog(
            onDismissRequest = { 
                showCustomModelDialog = false
                customModelInput = ""
            },
            title = { Text("自定义模型名称") },
            text = {
                Column {
                    Text(
                        text = "请输入 $providerName 的模型名称",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = customModelInput,
                        onValueChange = { customModelInput = it },
                        label = { Text("模型名称") },
                        placeholder = { Text(placeholderText) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (customModelInput.isNotBlank()) {
                            val newModel = customModelInput.trim()
                            selectedAiModel = newModel
                            viewModel.saveCustomModel(selectedAiProvider, newModel)
                            modelsRefreshTrigger++
                            showCustomModelDialog = false
                            customModelInput = ""
                        }
                    },
                    enabled = customModelInput.isNotBlank()
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCustomModelDialog = false
                        customModelInput = ""
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ColumnScope.AiModelMenuItems(
    aiModels: List<Pair<String, String>>,
    resetLabel: String,
    onSelect: (String) -> Unit,
    onDeleteCustom: (String) -> Unit,
    onResetModels: () -> Unit,
    onCustomModelClick: (() -> Unit)? = null
) {
    aiModels.forEach { (value, label) ->
        DropdownMenuItem(
            text = { Text(label) },
            onClick = { onSelect(value) },
            trailingIcon = if (label.endsWith("(自定义)")) {
                {
                    IconButton(onClick = { onDeleteCustom(value) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除自定义模型",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else null
        )
    }

    HorizontalDivider()

    DropdownMenuItem(
        text = { Text(resetLabel, color = MaterialTheme.colorScheme.primary) },
        onClick = onResetModels,
        leadingIcon = {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    )

    if (onCustomModelClick != null) {
        DropdownMenuItem(
            text = { Text("自定义模型...") },
            onClick = onCustomModelClick
        )
    }
}

/**
 * 用户偏好设置卡片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserPreferencesCard(
    viewModel: SettingsViewModel
) {
    // 默认 FAB 操作
    var selectedFabAction by remember { mutableStateOf(viewModel.getDefaultFabAction()) }
    // 默认首页视图
    var selectedHomeView by remember { mutableStateOf(viewModel.getDefaultHomeView()) }
    // 甘特视图默认时间粒度
    var selectedGanttMode by remember { mutableStateOf(viewModel.getGanttDefaultMode()) }
    // 截止视图时间范围
    var urgentHours by remember { mutableStateOf(viewModel.getDeadlineUrgentHours()) }
    var soonHours by remember { mutableStateOf(viewModel.getDeadlineSoonHours()) }
    var showUrgentDialog by remember { mutableStateOf(false) }
    var showSoonDialog by remember { mutableStateOf(false) }
    
    val fabOptions = listOf(
        Triple("voice", "语音添加", Icons.Default.Mic),
        Triple("text", "文字输入", Icons.Default.Edit),
        Triple("manual", "手动添加", Icons.Default.Add)
    )
    
    val viewOptions = listOf(
        Triple("timeline", "列表", Icons.AutoMirrored.Filled.List),
        Triple("gantt", "甘特图", Icons.Default.ViewTimeline),
        Triple("deadline", "截止日", Icons.Default.Flag)
    )
    
    val ganttModeOptions = listOf(
        Pair("TODAY", "今日"),
        Pair("THREE_DAY", "3日"),
        Pair("SEVEN_DAY", "7日"),
        Pair("ONE_MONTH", "1月")
    )
    
    SettingsCard(
        title = "偏好设置",
        icon = Icons.Default.Tune
    ) {
        // 默认 FAB 操作设置
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "默认添加方式",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "点击右下角按钮时的默认操作",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fabOptions.forEach { (value, label, icon) ->
                    FilterChip(
                        selected = selectedFabAction == value,
                        onClick = {
                            selectedFabAction = value
                            viewModel.setDefaultFabAction(value)
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 默认首页视图设置
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "默认首页视图",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "启动应用时显示的默认视图",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewOptions.forEach { (value, label, icon) ->
                    FilterChip(
                        selected = selectedHomeView == value,
                        onClick = {
                            selectedHomeView = value
                            viewModel.setDefaultHomeView(value)
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 甘特视图默认时间粒度设置
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "时间表视图默认展示天数",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "打开甘特时间表时默认显示的日期跨度",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ganttModeOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = selectedGanttMode == value,
                        onClick = {
                            selectedGanttMode = value
                            viewModel.setGanttDefaultMode(value)
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 截止视图时间范围设置
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "截止紧急度提醒标准",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "设置距离截止多少小时内算作「紧急」或「即将到期」",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 紧急任务时间范围
            Surface(
                onClick = { showUrgentDialog = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "紧急任务",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "距离截止时间",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$urgentHours",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "小时内",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 即将截止任务时间范围
            Surface(
                onClick = { showSoonDialog = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "即将截止",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "距离截止时间",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$soonHours",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "小时内",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    
    // 紧急任务时间设置对话框
    if (showUrgentDialog) {
        DeadlineTimeDialog(
            title = "紧急任务时间范围",
            currentHours = urgentHours,
            maxHours = soonHours - 1, // 必须小于即将截止的时间
            onDismiss = { showUrgentDialog = false },
            onConfirm = { hours ->
                urgentHours = hours
                viewModel.setDeadlineUrgentHours(hours)
                showUrgentDialog = false
            }
        )
    }
    
    // 即将截止任务时间设置对话框
    if (showSoonDialog) {
        DeadlineTimeDialog(
            title = "即将截止时间范围",
            currentHours = soonHours,
            minHours = urgentHours + 1, // 必须大于紧急任务的时间
            onDismiss = { showSoonDialog = false },
            onConfirm = { hours ->
                soonHours = hours
                viewModel.setDeadlineSoonHours(hours)
                showSoonDialog = false
            }
        )
    }
}

/**
 * 截止时间范围设置对话框
 */
@Composable
private fun DeadlineTimeDialog(
    title: String,
    currentHours: Int,
    minHours: Int = 1,
    maxHours: Int = 168, // 默认最大7天
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var hours by remember { mutableStateOf(currentHours.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = "设置时间范围（${minHours}-${maxHours}小时）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                OutlinedTextField(
                    value = hours,
                    onValueChange = { 
                        hours = it
                        errorMessage = null
                    },
                    label = { Text("小时数") },
                    suffix = { Text("小时") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 快捷选项
                Text(
                    text = "快捷选择",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quickOptions = listOf(12, 24, 48, 72).filter { it in minHours..maxHours }
                    quickOptions.forEach { option ->
                        FilterChip(
                            selected = hours == option.toString(),
                            onClick = { 
                                hours = option.toString()
                                errorMessage = null
                            },
                            label = { Text("${option}h") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val hoursInt = hours.toIntOrNull()
                    when {
                        hoursInt == null -> errorMessage = "请输入有效的数字"
                        hoursInt < minHours -> errorMessage = "不能小于 $minHours 小时"
                        hoursInt > maxHours -> errorMessage = "不能大于 $maxHours 小时"
                        else -> onConfirm(hoursInt)
                    }
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 提醒方式设置卡片
 */
@Composable
private fun ReminderMethodsCard(
    viewModel: SettingsViewModel
) {
    // 铃声和震动开关状态
    var soundEnabled by remember { mutableStateOf(viewModel.isReminderSoundEnabled()) }
    var vibrationEnabled by remember { mutableStateOf(viewModel.isReminderVibrationEnabled()) }
    
    SettingsCard(
        title = "提醒方式",
        icon = Icons.Default.Notifications
    ) {
        Text(
            text = "自定义提醒时的声音和震动效果",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        SwitchItem(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
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

@Composable
private fun ServiceStatusOverviewStrip(
    aiReady: Boolean,
    speechReady: Boolean,
    mapReady: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val items = listOf(
            "AI 对话" to aiReady,
            "在线语音" to speechReady,
            "高德地图" to mapReady
        )
        items.forEach { (label, ready) ->
            val tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            val bg = if (ready) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = bg,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (ready) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (ready) FontWeight.SemiBold else FontWeight.Normal,
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsibleCredentialSection(
    title: String,
    summary: String,
    configured: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val bannerContainer = if (configured) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val statusColor = if (configured) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = onToggleExpand,
            shape = RoundedCornerShape(12.dp),
            color = bannerContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (configured) Icons.Default.CheckCircle else Icons.Default.Key,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (expanded) "收起" else "展开修改",
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun InstantToggleSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
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

@Composable
private fun CategorySettingsCard(onManageCategories: () -> Unit) {
    SettingsCard(
        title = "分类管理",
        icon = Icons.Default.Category
    ) {
        Text(
            text = "自定义任务分类名称和颜色",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Button(
            onClick = onManageCategories,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("管理分类")
        }
    }
}

@Composable
private fun CategoryManagementDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val categories by viewModel.categories.collectAsState(initial = emptyList())
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }

    if (deletingCategory != null) {
        val categoryToDelete = deletingCategory!!
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("删除分类") },
            text = { Text("确定要删除分类“${categoryToDelete.name}”吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCategory(categoryToDelete)
                        deletingCategory = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) {
                    Text("取消")
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "分类管理",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Content
                if (isAddingNew || editingCategory != null) {
                    // Editor
                    CategoryEditor(
                        category = editingCategory,
                        onSave = { name, colorHex ->
                            if (isAddingNew) {
                                viewModel.addCategory(Category(name = name, colorHex = colorHex, isDefault = false))
                            } else {
                                editingCategory?.let {
                                    viewModel.updateCategory(it.copy(name = name, colorHex = colorHex))
                                }
                            }
                            isAddingNew = false
                            editingCategory = null
                        },
                        onCancel = {
                            isAddingNew = false
                            editingCategory = null
                        }
                    )
                } else {
                    // List
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { category ->
                            CategoryRow(
                                category = category,
                                onEdit = { editingCategory = category },
                                onDelete = { deletingCategory = category }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { isAddingNew = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("添加新分类")
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val color = ColorUtils.parseColor(category.colorHex)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Color Circle
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (category.isDefault) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "(默认)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        
        Row {
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
            }
            if (!category.isDefault) { // Default category cannot be deleted
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun CategoryEditor(
    category: Category?,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var selectedColor by remember { mutableStateOf(category?.colorHex ?: "#A8D8B9") }
    
    // 简约美观的高对比度色系 (Vibrant & Functional Colors for Task ID)
    val presetColors = listOf(
        "#1976D2", // Blue (Work)
        "#388E3C", // Green (Life)
        "#7B1FA2", // Purple (Study)
        "#D32F2F", // Red (Urgent)
        "#F57C00", // Orange
        "#0097A7", // Cyan
        "#C2185B", // Pink
        "#AFB42B", // Lime
        "#FFA000", // Amber
        "#5D4037", // Brown
        "#0288D1", // Light Blue
        "#00796B", // Teal
        "#689F38", // Light Green
        "#E64A19", // Deep Orange
        "#303F9F", // Indigo
        "#512DA8", // Deep Purple
        "#455A64", // Blue Grey
        "#333130"  // Dark charcoal
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (category == null) "添加分类" else "编辑分类",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("分类名称") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("选择颜色", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        // Color Palette
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(presetColors) { colorHex ->
                val color = ColorUtils.parseColor(colorHex)
                val isSelected = selectedColor.equals(colorHex, ignoreCase = true)
                
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(color, CircleShape)
                        .clickable { selectedColor = colorHex }
                        .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = ColorUtils.getSurfaceColor(color))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("取消")
            }
            Button(
                onClick = { onSave(name, selectedColor) },
                enabled = name.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text("保存")
            }
        }
    }
}