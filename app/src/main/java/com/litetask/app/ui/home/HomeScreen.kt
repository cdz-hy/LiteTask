package com.litetask.app.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
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
import com.litetask.app.ui.components.AddTaskDialog
import com.litetask.app.ui.components.DeadlineView
import com.litetask.app.ui.components.GanttView
import com.litetask.app.ui.components.TaskDetailSheet
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
    onNavigateToSearch: () -> Unit,
    onNavigateToGanttFullscreen: (com.litetask.app.ui.components.GanttViewMode) -> Unit,
    initialView: String = "timeline",
    onViewChanged: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    // ViewModel 数据流
    val todayTasks by viewModel.tasks.collectAsState()
    val timelineItems by viewModel.timelineItems.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var currentView by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialView) }

    // 侧边栏状态
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentView) {
        onViewChanged(currentView)
    }

    val allLoadedTasks = remember(timelineItems) {
        timelineItems.filterIsInstance<com.litetask.app.ui.home.TimelineItem.TaskItem>()
            .map { it.composite }
    }
    
    // 未完成任务数量（用于顶部显示）
    val activeTaskCount = remember(allLoadedTasks) {
        allLoadedTasks.count { !it.task.isDone }
    }

    val ganttTasks = remember(allLoadedTasks) {
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis
        val endOfThreeDays = startOfToday + (3 * 24 * 60 * 60 * 1000L)

        allLoadedTasks.filter { composite ->
            val taskStart = composite.task.startTime
            val taskEnd = composite.task.deadline
            taskStart < endOfThreeDays && taskEnd > startOfToday
        }
    }

    LaunchedEffect(currentView) {
        if (currentView == "gantt") {
            viewModel.silentRefresh()
        }
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

    val selectedTaskComposite by produceState<com.litetask.app.data.model.TaskDetailComposite?>(
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

    val context = LocalContext.current
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
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
                GooeyExpandableFab(
                    onVoiceClick = { onVoiceClickAction() },
                    onTextInputClick = { onTextInputClickAction() },
                    onManualInputClick = { showAddTaskDialog = true },
                    // 调整位置，确保展开时不被遮挡
                    modifier = Modifier.padding(bottom = 16.dp, end = 16.dp)
                )
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
                            } else if (it.deadline < System.currentTimeMillis()) {
                                Toast.makeText(context, context.getString(R.string.task_cannot_pin_expired), Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.updateTask(it.copy(isPinned = !it.isPinned))
                            }
                        },
                        onEditClick = {
                            // 只设置 taskToEdit，LaunchedEffect 会负责加载提醒并显示对话框
                            taskToEdit = it
                        },
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
                            } else if (task.deadline < System.currentTimeMillis()) {
                                Toast.makeText(context, context.getString(R.string.task_cannot_pin_expired), Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.updateTask(task.copy(isPinned = !task.isPinned))
                            }
                        }
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

            // 获取选中任务的提醒
            val selectedTaskReminders by produceState<List<com.litetask.app.data.model.Reminder>>(
                initialValue = emptyList(),
                key1 = selectedTaskId
            ) {
                selectedTaskId?.let { taskId ->
                    viewModel.getRemindersForTask(taskId).collect { reminders ->
                        value = reminders
                    }
                } ?: run {
                    value = emptyList()
                }
            }
            
            selectedTaskComposite?.let { composite ->
                TaskDetailSheet(
                    task = composite.task,
                    subTasks = composite.subTasks,
                    reminders = selectedTaskReminders,
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
                    }
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
