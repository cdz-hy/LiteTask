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
    
    LaunchedEffect(currentView) {
        onViewChanged(currentView)
    }
    
    val allLoadedTasks = remember(timelineItems) {
        timelineItems.filterIsInstance<com.litetask.app.ui.home.TimelineItem.TaskItem>()
            .map { it.composite }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.size(4.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.tasks_count, allLoadedTasks.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { /* Profile */ },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "L",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
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
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FloatingActionButton(
                    onClick = { showAddTaskDialog = true },
                    containerColor = Primary,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_task), modifier = Modifier.size(24.dp))
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
                    onTaskClick = { selectedTaskId = it.id },
                    onNavigateToFullscreen = { viewMode -> onNavigateToGanttFullscreen(viewMode) }
                )
                "deadline" -> DeadlineView(
                    tasks = allLoadedTasks,
                    onTaskClick = { task -> selectedTaskId = task.id },
                    onDeleteClick = { task -> viewModel.deleteTask(task) },
                    onEditClick = { task -> 
                        taskToEdit = task
                        showEditDialog = true
                    },
                    onPinClick = { task -> 
                        if (task.isDone) {
                            Toast.makeText(context, "已完成的任务不能置顶", Toast.LENGTH_SHORT).show()
                        } else if (task.deadline < System.currentTimeMillis()) {
                            Toast.makeText(context, "已过期的任务不能置顶", Toast.LENGTH_SHORT).show()
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

        if (uiState.isRecording) {
            VoiceRecorderDialog(
                onDismiss = { viewModel.stopRecording() }
            )
        }

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