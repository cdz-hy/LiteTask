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
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material3.*
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
    viewModel: HomeViewModel = hiltViewModel()
) {
    // ViewModel 数据流
    val todayTasks by viewModel.tasks.collectAsState()  // 当天的任务（用于日期筛选视图）
    val timelineItems by viewModel.timelineItems.collectAsState()  // Timeline 数据（未完成 + 已加载的已完成）
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var currentView by remember { mutableStateOf("timeline") } // timeline, gantt, deadline
    
    // 从 timelineItems 中提取所有任务（用于甘特视图和截止视图）
    val allLoadedTasks = remember(timelineItems) {
        timelineItems.filterIsInstance<com.litetask.app.ui.home.TimelineItem.TaskItem>()
            .map { it.composite }
    }
    
    // 甘特视图专用：筛选3天内的任务
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
        
        // 筛选：任务的某部分在这3天内
        allLoadedTasks.filter { composite ->
            val taskStart = composite.task.startTime
            val taskEnd = composite.task.deadline
            // 任务重叠条件：开始时间 < 3天结束 AND 结束时间 > 今天开始
            taskStart < endOfThreeDays && taskEnd > startOfToday
        }
    }
    
    // 监听视图切换，进入甘特视图时静默刷新（加载最近20条已完成任务，不显示刷新动画）
    LaunchedEffect(currentView) {
        if (currentView == "gantt") {
            viewModel.silentRefresh()
        }
    }
    
    // 下拉刷新状态
    val pullToRefreshState = rememberPullToRefreshState()
    
    // 监听刷新状态
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            pullToRefreshState.startRefresh()
        } else {
            pullToRefreshState.endRefresh()
        }
    }
    
    // 监听下拉动作
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) {
            viewModel.onRefresh()
        }
    }
    
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedTask by remember { mutableStateOf<Task?>(null) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var selectedTaskId by remember { mutableStateOf<Long?>(null) }
    
    // 实时订阅选中任务的详情（包括子任务）
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

    // 录音相关状态
    val context = LocalContext.current
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording()
        }
    }

    Scaffold(
        containerColor = Color(0xFFF8F9FA),
        topBar = {
            Column(modifier = Modifier.background(Color(0xFFF8F9FA))) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = SimpleDateFormat(stringResource(R.string.date_format), Locale.getDefault()).format(Date()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF666666)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.size(4.dp).background(Color(0xFFCCCCCC), CircleShape))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.tasks_count, allLoadedTasks.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { /* TODO: Menu */ }) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    actions = {
                        Box(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE3F2FD))
                                .clickable { /* Profile */ },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "L",
                                color = Color(0xFF1976D2),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8F9FA))
                )
                
                // View Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(Color(0xFFEEF2F6), CircleShape)
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
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Manual Add Button (Small)
                FloatingActionButton(
                    onClick = { showAddTaskDialog = true },
                    containerColor = Primary,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Task", modifier = Modifier.size(24.dp))
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
                             Toast.makeText(context, "已完成的任务不能置顶", Toast.LENGTH_SHORT).show()
                         } else if (it.deadline < System.currentTimeMillis()) {
                             Toast.makeText(context, "已过期的任务不能置顶", Toast.LENGTH_SHORT).show()
                         } else {
                             viewModel.updateTask(it.copy(isPinned = !it.isPinned))
                         }
                    },
                    onEditClick = { 
                        taskToEdit = it
                        showEditDialog = true
                    },
                    onLoadMore = { viewModel.loadMoreHistory() },
                    onSearchClick = { onNavigateToSearch() }
                )
                "gantt" -> GanttView(
                    taskComposites = ganttTasks,
                    onTaskClick = { selectedTaskId = it.id }
                )
                "deadline" -> DeadlineView(
                    tasks = allLoadedTasks.map { it.task },
                    onTaskClick = { selectedTaskId = it.id }
                )
            }
            
            // --- 美化后的下拉刷新指示器 ---
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // 关键: zIndex 确保指示器浮在列表内容之上，不会被遮挡
                    .zIndex(1f),
                // 美化: 使用 LiteTask 风格的 PrimaryContainer 浅蓝色背景
                containerColor = Color(0xFFD3E3FD), 
                // 美化: 使用深蓝色图标，增加对比度
                contentColor = Color(0xFF041E49),
                // 美化: 显式指定圆形，并添加轻微阴影效果(Shadow已由组件默认处理，但颜色决定了质感)
                shape = CircleShape
            )
        }

        // Dialogs
        if (showAddTaskDialog) {
            AddTaskDialog(
                onDismiss = { showAddTaskDialog = false },
                onConfirm = { task ->
                    viewModel.addTask(task)
                    showAddTaskDialog = false
                }
            )
        }

        if (showEditDialog) {
            AddTaskDialog(
                initialTask = taskToEdit,
                onDismiss = { 
                    showEditDialog = false
                    taskToEdit = null
                },
                onConfirm = { task ->
                    viewModel.updateTask(task)
                    showEditDialog = false
                    taskToEdit = null
                }
            )
        }

        // 详情 Sheet 连接
        selectedTaskComposite?.let { composite ->
            TaskDetailSheet(
                task = composite.task,
                subTasks = composite.subTasks,
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

        // Voice Recording Dialog
        if (uiState.isRecording) {
            VoiceRecorderDialog(
                onDismiss = { viewModel.stopRecording() }
            )
        }

        // AI Result Sheet
        if (uiState.showAiResult) {
            TaskConfirmationSheet(
                tasks = uiState.aiParsedTasks,
                onDismiss = { viewModel.dismissAiResult() },
                onConfirm = { viewModel.confirmAddTasks() }
            )
        }
    }
}

@Composable
fun ViewOption(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) Color.White else Color.Transparent
    val contentColor = if (isSelected) Primary else Color(0xFF444746)
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