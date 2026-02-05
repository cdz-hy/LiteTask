package com.litetask.app.ui.home

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.litetask.app.R
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.reminder.PermissionHelper
import com.litetask.app.ui.components.AddTaskDialog
import com.litetask.app.ui.components.DeadlineView
import com.litetask.app.ui.components.GanttView
import com.litetask.app.ui.components.TaskDetailSheet
import com.litetask.app.ui.components.SubTaskInputDialog
import com.litetask.app.ui.components.SubTaskConfirmationDialog
import com.litetask.app.ui.components.TimelineView
import com.litetask.app.ui.components.VoiceRecorderDialog
import com.litetask.app.ui.components.TaskConfirmationSheet
import com.litetask.app.ui.components.TextInputDialog
import com.litetask.app.ui.components.GooeyExpandableFab
import com.litetask.app.ui.theme.Primary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAdd: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToGanttFullscreen: (com.litetask.app.ui.components.GanttViewMode) -> Unit,
    initialView: String = "timeline",
    onViewChanged: (String) -> Unit = {},
    shouldCheckPermissions: Boolean = true,
    onPermissionChecked: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    initialAction: String? = null,
    onActionHandled: () -> Unit = {}
) {
    // ViewModel 数据流
    val todayTasks by viewModel.tasks.collectAsState()
    val timelineItems by viewModel.timelineItems.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var currentView by androidx.compose.runtime.saveable.rememberSaveable { 
        mutableStateOf(if (initialView == "timeline") viewModel.getDefaultHomeView() else initialView) 
    }

    // 侧边栏状态
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 视图切换时的处理（数据已在 ViewModel 初始化时加载，无需额外操作）
    LaunchedEffect(currentView) {
        onViewChanged(currentView)
    }
    
    // 从 timelineItems 提取任务数据
    val allLoadedTasks = remember(timelineItems) {
        timelineItems.filterIsInstance<TimelineItem.TaskItem>().map { it.composite }
    }
    
    // 未完成任务数量（用于顶部显示）
    val activeTaskCount = remember(allLoadedTasks) {
        allLoadedTasks.count { !it.task.isDone }
    }

    // 甘特图任务（所有任务，让GanttView内部处理筛选和显示）
    val ganttTasks = remember(timelineItems) {
        timelineItems.filterIsInstance<TimelineItem.TaskItem>().map { it.composite }
    }

    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            pullToRefreshState.startRefresh()
        } else {
            pullToRefreshState.endRefresh()
        }
    }

    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) {
            viewModel.onRefresh()
        }
    }

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showTextInputDialog by remember { mutableStateOf(false) }
    var selectedTask by remember { mutableStateOf<Task?>(null) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var selectedTaskId by remember { mutableStateOf<Long?>(null) }
    
    // 统一权限弹窗状态
    var showPermissionDialog by remember { mutableStateOf(false) }
    var missingPermissions by remember { mutableStateOf<List<MissingPermission>>(emptyList()) }

    val context = LocalContext.current
    
    // 通知权限请求（Android 13+ 需要运行时请求）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // 权限请求完成后，检查所有缺失的权限并显示统一弹窗
        val includeAutoStart = shouldPromptAutoStart(context)
        val missing = checkMissingPermissions(context, includeAutoStart)
        if (missing.isNotEmpty()) {
            missingPermissions = missing
            showPermissionDialog = true
            // 如果包含自启动权限，标记已提醒
            if (includeAutoStart) {
                markAutoStartPrompted(context)
            }
        }
        // 标记权限检查已完成
        onPermissionChecked()
    }
    
    // 冷启动时检查权限（只在 shouldCheckPermissions 为 true 时执行）
    LaunchedEffect(shouldCheckPermissions) {
        if (!shouldCheckPermissions) return@LaunchedEffect
        
        // 先请求通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!PermissionHelper.hasNotificationPermission(context)) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return@LaunchedEffect // 等待权限请求结果后再检查其他权限
            }
        }
        
        // 检查所有缺失的权限
        val includeAutoStart = shouldPromptAutoStart(context)
        val missing = checkMissingPermissions(context, includeAutoStart)
        if (missing.isNotEmpty()) {
            missingPermissions = missing
            showPermissionDialog = true
            if (includeAutoStart) {
                markAutoStartPrompted(context)
            }
        }
        // 标记权限检查已完成
        onPermissionChecked()
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // 应用标题
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)
                )

                HorizontalDivider()

                Spacer(modifier = Modifier.height(8.dp))

                // AI 分析历史记录选项
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text(stringResource(R.string.ai_history)) },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                        }
                        onNavigateToHistory()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 设置选项
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.settings)) },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                        }
                        onNavigateToSettings()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = SimpleDateFormat(stringResource(R.string.date_format), Locale.getDefault()).format(Date()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(modifier = Modifier.size(3.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.pending_tasks_count, activeTaskCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch {
                                    drawerState.open()
                                }
                            }) {
                                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu), tint = MaterialTheme.colorScheme.onSurface)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ViewOption(
                            text = stringResource(R.string.view_list),
                            icon = Icons.Default.List,
                            isSelected = currentView == "timeline",
                            onClick = { currentView = "timeline" },
                            modifier = Modifier.weight(1f)
                        )
                        ViewOption(
                            text = stringResource(R.string.view_gantt),
                            icon = Icons.Default.ViewTimeline,
                            isSelected = currentView == "gantt",
                            onClick = { currentView = "gantt" },
                            modifier = Modifier.weight(1f)
                        )
                        ViewOption(
                            text = stringResource(R.string.view_deadline),
                            icon = Icons.Default.Flag,
                            isSelected = currentView == "deadline",
                            onClick = { currentView = "deadline" },
                            modifier = Modifier.weight(1f)
                        )
                    }

                }
            },
            floatingActionButton = {
                // 将点击逻辑提取出来，方便复用
                val onVoiceClickAction = {
                    when (viewModel.checkApiKey()) {
                        is ApiKeyCheckResult.NotConfigured -> {
                            Toast.makeText(context, context.getString(R.string.api_key_not_configured), Toast.LENGTH_LONG).show()
                        }
                        is ApiKeyCheckResult.Valid -> {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                val hasPermission = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                                        android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    viewModel.startRecording()
                                } else {
                                    permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            } else {
                                viewModel.startRecording()
                            }
                        }
                    }
                }

                val onTextInputClickAction = {
                    when (viewModel.checkApiKey()) {
                        is ApiKeyCheckResult.NotConfigured -> {
                            Toast.makeText(context, context.getString(R.string.api_key_not_configured), Toast.LENGTH_LONG).show()
                        }
                        is ApiKeyCheckResult.Valid -> {
                            showTextInputDialog = true
                        }
                    }
                }

                // 使用新的 Gooey 悬浮按钮
                val defaultFabAction = remember { viewModel.getDefaultFabAction() }
                GooeyExpandableFab(
                    onVoiceClick = { onVoiceClickAction() },
                    onTextInputClick = { onTextInputClickAction() },
                    onManualInputClick = { showAddTaskDialog = true },
                    defaultAction = defaultFabAction,
                    // 调整位置，确保展开时不被遮挡
                    modifier = Modifier.padding(bottom = 16.dp, end = 16.dp)
                )

                // 处理从外部（如小组件）传来的动作
                LaunchedEffect(initialAction) {
                    if (initialAction == "add_task") {
                        when (defaultFabAction) {
                            "voice" -> onVoiceClickAction()
                            "text" -> onTextInputClickAction()
                            "manual" -> showAddTaskDialog = true
                        }
                        onActionHandled() // 标记动作已处理
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .nestedScroll(pullToRefreshState.nestedScrollConnection)
            ) {
                when (currentView) {
                    "timeline" -> TimelineView(
                        items = timelineItems,
                        onTaskClick = { task ->
                            selectedTaskId = task.id
                        },
                        onDeleteClick = { viewModel.deleteTask(it) },
                        onPinClick = {
                            if (it.isDone) {
                                Toast.makeText(context, context.getString(R.string.task_cannot_pin_done), Toast.LENGTH_SHORT).show()
                            } else {
                                // 允许未完成任务和已过期任务置顶（在各自区域内）
                                viewModel.updateTask(it.copy(isPinned = !it.isPinned))
                            }
                        },
                        onEditClick = {
                            // 只设置 taskToEdit，LaunchedEffect 会负责加载提醒并显示对话框
                            taskToEdit = it
                        },
                        onToggleDone = { viewModel.toggleTaskDone(it) },
                        onLoadMore = { viewModel.loadMoreHistory() },
                        onSearchClick = { onNavigateToSearch() }
                    )
                    "gantt" -> GanttView(
                        taskComposites = ganttTasks,
                        onTaskClick = { selectedTaskId = it.id },
                        onNavigateToFullscreen = { viewMode -> onNavigateToGanttFullscreen(viewMode) }
                    )
                    "deadline" -> DeadlineView(
                        tasks = allLoadedTasks,
                        onTaskClick = { task -> selectedTaskId = task.id },
                        onDeleteClick = { task -> viewModel.deleteTask(task) },
                        onEditClick = { task ->
                            // 只设置 taskToEdit，LaunchedEffect 会负责加载提醒并显示对话框
                            taskToEdit = task
                        },
                        onPinClick = { task ->
                            if (task.isDone) {
                                Toast.makeText(context, context.getString(R.string.task_cannot_pin_done), Toast.LENGTH_SHORT).show()
                            } else {
                                // 允许未完成任务和已过期任务置顶（在各自区域内）
                                viewModel.updateTask(task.copy(isPinned = !task.isPinned))
                            }
                        },
                        onToggleDone = { viewModel.toggleTaskDone(it) }
                    )
                }

                PullToRefreshContainer(
                    state = pullToRefreshState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(1f),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape
                )
            }

            if (showAddTaskDialog) {
                AddTaskDialog(
                    onDismiss = { showAddTaskDialog = false },
                    onConfirm = { task ->
                        viewModel.addTask(task)
                        showAddTaskDialog = false
                    },
                    onConfirmWithReminders = { task, reminderConfigs ->
                        viewModel.addTaskWithReminders(task, reminderConfigs)
                        showAddTaskDialog = false
                    }
                )
            }
            
            // 文字输入对话框
            if (showTextInputDialog) {
                TextInputDialog(
                    onDismiss = { 
                        if (!uiState.isAnalyzing) {
                            showTextInputDialog = false 
                        }
                    },
                    onAnalyze = { text ->
                        viewModel.analyzeTextInput(text)
                        // 不立即关闭，等分析完成后通过 LaunchedEffect 关闭
                    },
                    isAnalyzing = uiState.isAnalyzing
                )
            }
            
            // 分析完成后关闭文字输入对话框
            LaunchedEffect(uiState.showAiResult, uiState.showAiError) {
                if ((uiState.showAiResult || uiState.showAiError) && showTextInputDialog) {
                    showTextInputDialog = false
                }
            }
            
            // 编辑任务时需要加载已有提醒
            var editTaskReminders by remember { mutableStateOf<List<com.litetask.app.data.model.Reminder>>(emptyList()) }
            var isLoadingReminders by remember { mutableStateOf(false) }
            
            // 当 taskToEdit 变化时，先加载提醒，加载完成后再显示对话框
            LaunchedEffect(taskToEdit) {
                if (taskToEdit != null && !showEditDialog) {
                    isLoadingReminders = true
                    editTaskReminders = viewModel.getRemindersForTaskSync(taskToEdit!!.id)
                    isLoadingReminders = false
                    showEditDialog = true
                }
            }

            if (showEditDialog && !isLoadingReminders) {
                AddTaskDialog(
                    initialTask = taskToEdit,
                    initialReminders = editTaskReminders,
                    onDismiss = {
                        showEditDialog = false
                        taskToEdit = null
                        editTaskReminders = emptyList()
                    },
                    onConfirm = { task ->
                        viewModel.updateTask(task)
                        showEditDialog = false
                        taskToEdit = null
                        editTaskReminders = emptyList()
                    },
                    onConfirmWithReminders = { task, reminderConfigs ->
                        viewModel.updateTaskWithReminders(task, reminderConfigs)
                        showEditDialog = false
                        taskToEdit = null
                        editTaskReminders = emptyList()
                    }
                )
            }

            // 获取选中任务的复合数据（已包含提醒）
            val selectedTaskComposite by produceState<TaskDetailComposite?>(
                initialValue = null,
                key1 = selectedTaskId
            ) {
                selectedTaskId?.let { taskId ->
                    viewModel.getTaskDetailFlow(taskId).collect { composite ->
                        value = composite
                    }
                } ?: run {
                    value = null
                }
            }
            
            selectedTaskComposite?.let { composite ->
                TaskDetailSheet(
                    task = composite.task,
                    subTasks = composite.subTasks,
                    reminders = composite.reminders, // 直接使用复合数据中的提醒
                    onDismiss = { selectedTaskId = null },
                    onDelete = {
                        viewModel.deleteTask(it)
                        selectedTaskId = null
                    },
                    onUpdateTask = { viewModel.updateTask(it) },
                    onUpdateSubTask = { sub, isCompleted ->
                        viewModel.updateSubTaskStatus(sub.id, isCompleted)
                    },
                    onAddSubTask = { content ->
                        viewModel.addSubTask(composite.task.id, content)
                    },
                    onDeleteSubTask = { subTask ->
                        viewModel.deleteSubTask(subTask)
                    },
                    onGenerateSubTasks = {
                        viewModel.generateSubTasks(composite.task)
                    },
                    onGenerateSubTasksWithContext = { task ->
                        viewModel.showSubTaskInputDialog(task)
                    },
                    isGeneratingSubTasks = uiState.isAnalyzing
                )
            }

            val recordingDuration by viewModel.recordingDuration.collectAsState()

            // 显示条件：正在录音、录音结束待确认（无论有无文字）、或 AI 分析中
            val showVoiceDialog = uiState.isRecording ||
                    uiState.recordingState == com.litetask.app.util.RecordingState.PLAYING ||
                    uiState.showVoiceResult // 新增状态：录音结束后显示结果（无论有无文字）

            if (showVoiceDialog) {
                val speechSourceInfo = remember { viewModel.getSpeechSourceInfo() }
                VoiceRecorderDialog(
                    onDismiss = { viewModel.cancelRecording() },
                    onStopRecording = { viewModel.finishRecording() }, // 停止录音，进入确认状态
                    onFinish = { editedText -> viewModel.finishRecordingWithText(editedText) }, // 确认添加
                    recognizedText = uiState.recognizedText,
                    recordingDuration = recordingDuration,
                    isPlaying = uiState.recordingState == com.litetask.app.util.RecordingState.PLAYING,
                    isRecording = uiState.recordingState == com.litetask.app.util.RecordingState.RECORDING,
                    speechSourceName = speechSourceInfo.displayName
                )
            }

            if (uiState.showAiResult) {
                TaskConfirmationSheet(
                    tasks = uiState.aiParsedTasks,
                    onDismiss = { viewModel.dismissAiResult() },
                    onConfirm = { tasks, reminders -> viewModel.confirmAddTasksWithReminders(tasks, reminders) },
                    onEditTask = { index, task -> viewModel.updateAiParsedTask(index, task) },
                    onDeleteTask = { index -> viewModel.deleteAiParsedTask(index) }
                )
            }

            // AI 错误提示对话框
            if (uiState.showAiError) {
                AiErrorDialog(
                    errorMessage = uiState.aiErrorMessage,
                    onDismiss = { viewModel.dismissAiError() },
                    onGoToSettings = {
                        viewModel.dismissAiError()
                        onNavigateToSettings()
                    }
                )
            }
            
            // 子任务详细输入对话框
            if (uiState.showSubTaskInput) {
                val currentTask = uiState.currentTask
                if (currentTask != null) {
                    SubTaskInputDialog(
                        task = currentTask,
                        onDismiss = { viewModel.dismissSubTaskInput() },
                        onAnalyze = { context ->
                            viewModel.generateSubTasksWithContext(currentTask, context)
                        },
                        isAnalyzing = uiState.isAnalyzing
                    )
                }
            }
            
            // 子任务生成结果确认对话框
            if (uiState.showSubTaskResult) {
                val currentTask = uiState.currentTask
                if (currentTask != null) {
                    SubTaskConfirmationDialog(
                        task = currentTask,
                        subTasks = uiState.generatedSubTasks,
                        onDismiss = { viewModel.dismissSubTaskResult() },
                        onConfirm = { editedSubTasks -> 
                            viewModel.confirmAddSubTasks(editedSubTasks)
                        }
                    )
                }
            }
            
            // 语音识别错误提示对话框
            if (uiState.showSpeechError) {
                SpeechErrorDialog(
                    errorMessage = uiState.speechErrorMessage,
                    onDismiss = { viewModel.dismissSpeechError() },
                    onGoToSettings = {
                        viewModel.dismissSpeechError()
                        onNavigateToSettings()
                    }
                )
            }
            
            // 精确闹钟权限引导对话框
            if (showPermissionDialog && missingPermissions.isNotEmpty()) {
                PermissionCheckDialog(
                    missingPermissions = missingPermissions,
                    onDismiss = { showPermissionDialog = false },
                    onGoToSettings = { permission ->
                        context.startActivity(permission.settingsIntent)
                    }
                )
            }
        }
    } // ModalNavigationDrawer 闭合
}

@Composable
private fun AiErrorDialog(
    errorMessage: String,
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.ai_analysis_failed),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            if (errorMessage.contains("API Key") || errorMessage.contains("设置")) {
                TextButton(onClick = onGoToSettings) {
                    Text(stringResource(R.string.go_to_settings))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun SpeechErrorDialog(
    errorMessage: String,
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit
) {
    // 判断是否需要显示"去设置"按钮
    val needSettings = errorMessage.contains("设置") || 
                       errorMessage.contains("API Key") || 
                       errorMessage.contains("App ID") ||
                       errorMessage.contains("未配置") ||
                       errorMessage.contains("权限") ||
                       errorMessage.contains("充值")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.MicOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.speech_error_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            if (needSettings) {
                TextButton(onClick = onGoToSettings) {
                    Text(stringResource(R.string.go_to_settings))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
fun ViewOption(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
    val contentColor = if (isSelected) Primary else MaterialTheme.colorScheme.onSurfaceVariant
    val shadowElevation = if (isSelected) 2.dp else 0.dp

    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = CircleShape,
        color = backgroundColor,
        shadowElevation = shadowElevation
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}


/**
 * 增强版 FAB 按钮组
 * Material Design 3 风格，带有动效和视觉层次
 */
@Composable
private fun EnhancedFabGroup(
    onAddClick: () -> Unit,
    onTextInputClick: () -> Unit,
    onVoiceClick: () -> Unit
) {
    // 语音按钮呼吸动画
    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // 光晕动画
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 手动添加按钮 - 小尺寸
        FloatingActionButton(
            onClick = onAddClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(14.dp),
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 3.dp,
                pressedElevation = 6.dp,
                hoveredElevation = 4.dp
            ),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.add_task),
                modifier = Modifier.size(24.dp)
            )
        }
        
        // 文字输入按钮 - 中等尺寸
        FloatingActionButton(
            onClick = onTextInputClick,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = RoundedCornerShape(16.dp),
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 3.dp,
                pressedElevation = 6.dp,
                hoveredElevation = 4.dp
            ),
            modifier = Modifier.size(52.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.text_add_task),
                modifier = Modifier.size(26.dp)
            )
        }

        // 语音添加按钮 - 主要操作，带光晕效果
        Box(contentAlignment = Alignment.Center) {
            // 外层光晕
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(pulseScale)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Primary.copy(alpha = glowAlpha),
                                Primary.copy(alpha = 0f)
                            )
                        ),
                        shape = CircleShape
                    )
            )
            
            // 主按钮
            LargeFloatingActionButton(
                onClick = onVoiceClick,
                containerColor = Primary,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 12.dp,
                    hoveredElevation = 8.dp
                ),
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = stringResource(R.string.voice_add_task),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * 缺失权限数据类
 */
data class MissingPermission(
    val name: String,
    val description: String,
    val settingsIntent: android.content.Intent
)

private const val PREFS_NAME = "litetask_permission_prefs"
private const val KEY_AUTO_START_PROMPTED = "auto_start_prompted"

/**
 * 检查缺失的权限
 * 
 * @param includeAutoStart 是否包含自启动权限检查（只在首次提醒时包含）
 */
private fun checkMissingPermissions(
    context: android.content.Context,
    includeAutoStart: Boolean = false
): List<MissingPermission> {
    val missing = mutableListOf<MissingPermission>()

    // 通知权限
    if (!PermissionHelper.hasNotificationPermission(context)) {
        missing.add(
            MissingPermission(
                name = "通知权限",
                description = "显示提醒通知",
                settingsIntent = PermissionHelper.getNotificationSettingsIntent(context)
            )
        )
    }

    // 精确闹钟权限 (Android 12+)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (!PermissionHelper.canScheduleExactAlarms(context)) {
            missing.add(
                MissingPermission(
                    name = "精确闹钟权限",
                    description = "设置精确的提醒时间",
                    settingsIntent = PermissionHelper.getExactAlarmSettingsIntent(context)
                )
            )
        }
    }

    // 悬浮窗权限
    if (!PermissionHelper.hasOverlayPermission(context)) {
        missing.add(
            MissingPermission(
                name = "悬浮窗权限",
                description = "显示悬浮提醒弹窗",
                settingsIntent = PermissionHelper.getOverlaySettingsIntent(context)
            )
        )
    }

    // 自启动权限（只在首次且有可用设置页面时提醒）
    if (includeAutoStart && PermissionHelper.hasAutoStartSettings(context)) {
        missing.add(
            MissingPermission(
                name = "自启动权限",
                description = "后台被清理后仍能收到提醒",
                settingsIntent = PermissionHelper.getAutoStartSettingsIntent(context)
            )
        )
    }

    return missing
}

/**
 * 检查是否需要提醒自启动权限（只提醒一次）
 */
private fun shouldPromptAutoStart(context: android.content.Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    return !prefs.getBoolean(KEY_AUTO_START_PROMPTED, false)
}

/**
 * 标记自启动权限已提醒过
 */
private fun markAutoStartPrompted(context: android.content.Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_AUTO_START_PROMPTED, true).apply()
}

/**
 * 统一权限检查弹窗
 */
@Composable
private fun PermissionCheckDialog(
    missingPermissions: List<MissingPermission>,
    onDismiss: () -> Unit,
    onGoToSettings: (MissingPermission) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "需要开启权限",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "为确保提醒功能正常工作，请开启以下权限：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                missingPermissions.forEach { permission ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onGoToSettings(permission) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = permission.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = permission.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "去开启",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
