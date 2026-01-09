package com.litetask.app

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.litetask.app.data.model.Task
import com.litetask.app.reminder.FloatingReminderService
import com.litetask.app.reminder.NotificationHelper
import com.litetask.app.reminder.PermissionHelper
import com.litetask.app.ui.components.AddTaskDialog
import com.litetask.app.ui.components.SubTaskInputDialog
import com.litetask.app.ui.components.SubTaskConfirmationDialog
import com.litetask.app.ui.components.TaskDetailSheet
import com.litetask.app.ui.home.HomeViewModel
import com.litetask.app.ui.theme.LiteTaskTheme
import com.litetask.app.widget.WidgetUpdateHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    // 用于传递从通知点击的任务 ID
    private var pendingTaskId: Long? = null
    
    // 通知权限请求
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.d("MainActivity", "Notification permission granted")
        } else {
            android.util.Log.w("MainActivity", "Notification permission denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 处理从通知点击进入的情况
        handleNotificationIntent(intent)
        
        // 请求必要的权限
        requestRequiredPermissions()
        
        setContent {
            LiteTaskTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(initialTaskId = pendingTaskId)
                }
            }
        }
    }
    
    /**
     * 请求提醒功能所需的权限
     * 
     * 注意：主要的权限检查和弹窗引导已移至 HomeScreen
     * 这里只做基础的通知权限请求（Android 13+）
     */
    private fun requestRequiredPermissions() {
        // 请求通知权限 (Android 13+)
        // 其他权限的检查和引导在 HomeScreen 中统一处理
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!PermissionHelper.hasNotificationPermission(this)) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 处理 App 已在前台时从通知点击的情况
        handleNotificationIntent(intent)
    }
    
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(NotificationHelper.EXTRA_FROM_NOTIFICATION, false) == true) {
            pendingTaskId = intent.getLongExtra(NotificationHelper.EXTRA_TASK_ID, -1).takeIf { it != -1L }
        }
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 当配置变化时（包括夜间模式切换），强制刷新所有widget
        WidgetUpdateHelper.forceRefreshAllWidgets(this)
    }
}

@Composable
fun AppContent(initialTaskId: Long? = null) {
    var currentScreen by remember { mutableStateOf("home") }
    var currentHomeView by remember { mutableStateOf("timeline") } // 记住 HomeScreen 的当前视图，初始为列表
    var selectedTaskId by remember { mutableStateOf<Long?>(initialTaskId) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var ganttViewMode by remember { mutableStateOf<com.litetask.app.ui.components.GanttViewMode>(com.litetask.app.ui.components.GanttViewMode.THREE_DAY) }
    // 权限检查标志：只在冷启动时检查一次
    var hasCheckedPermissions by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // 如果有从通知传入的任务 ID，自动打开任务详情
    LaunchedEffect(initialTaskId) {
        if (initialTaskId != null && initialTaskId > 0) {
            selectedTaskId = initialTaskId
        }
    }

    when (currentScreen) {
        "home" -> {
            val viewModel: HomeViewModel = hiltViewModel()
            
            com.litetask.app.ui.home.HomeScreen(
                onNavigateToAdd = { currentScreen = "add_task" },
                onNavigateToSettings = { currentScreen = "settings" },
                onNavigateToSearch = { currentScreen = "search" },
                onNavigateToGanttFullscreen = { viewMode ->
                    ganttViewMode = viewMode
                    currentScreen = "gantt_fullscreen"
                },
                initialView = currentHomeView,
                onViewChanged = { view -> currentHomeView = view },
                shouldCheckPermissions = !hasCheckedPermissions,
                onPermissionChecked = { hasCheckedPermissions = true },
                viewModel = viewModel
            )
        }
        "gantt_fullscreen" -> {
            val viewModel: HomeViewModel = hiltViewModel()
            val timelineItems by viewModel.timelineItems.collectAsState()
            
            // 从 timelineItems 中提取所有任务
            val allLoadedTasks = remember(timelineItems) {
                timelineItems.filterIsInstance<com.litetask.app.ui.home.TimelineItem.TaskItem>()
                    .map { it.composite }
            }
            
            // 处理返回键
            BackHandler {
                currentScreen = "home"
            }
            
            com.litetask.app.ui.components.GanttFullscreenView(
                taskComposites = allLoadedTasks,
                onTaskClick = { task ->
                    selectedTaskId = task.id
                },
                initialViewMode = ganttViewMode,
                onBack = { currentScreen = "home" }
            )
            
            // 任务详情 Sheet
            selectedTaskId?.let { taskId ->
                val taskComposite by produceState<com.litetask.app.data.model.TaskDetailComposite?>(
                    initialValue = null,
                    key1 = taskId
                ) {
                    viewModel.getTaskDetailFlow(taskId).collect { composite ->
                        value = composite
                    }
                }
                
                // 获取任务提醒
                val uiState by viewModel.uiState.collectAsState()
                taskComposite?.let { composite ->
                    TaskDetailSheet(
                        task = composite.task,
                        subTasks = composite.subTasks,
                        reminders = composite.reminders, // 直接使用复合数据中的提醒
                        onDismiss = { selectedTaskId = null },
                        onDelete = { 
                            viewModel.deleteTask(it)
                            selectedTaskId = null
                            Toast.makeText(context, "任务已删除", Toast.LENGTH_SHORT).show()
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
            }
        }
        "settings" -> {
            // 处理返回键
            BackHandler {
                currentScreen = "home"
            }
            
            com.litetask.app.ui.settings.SettingsScreen(
                onBack = { currentScreen = "home" }
            )
        }
        "search" -> {
            val homeViewModel: HomeViewModel = hiltViewModel()
            
            // 处理返回键
            BackHandler {
                currentScreen = "home"
            }
            
            com.litetask.app.ui.search.SearchScreen(
                onBack = { currentScreen = "home" },
                onTaskClick = { task ->
                    selectedTaskId = task.id
                },
                onDeleteClick = { task ->
                    homeViewModel.deleteTask(task)
                    Toast.makeText(context, "任务已删除", Toast.LENGTH_SHORT).show()
                },
                onEditClick = { task ->
                    // 只设置 taskToEdit，LaunchedEffect 会负责加载提醒并显示对话框
                    taskToEdit = task
                },
                onPinClick = { task ->
                    if (task.isDone) {
                        Toast.makeText(context, "已完成的任务不能置顶", Toast.LENGTH_SHORT).show()
                    } else if (task.deadline < System.currentTimeMillis()) {
                        Toast.makeText(context, "已过期的任务不能置顶", Toast.LENGTH_SHORT).show()
                    } else {
                        homeViewModel.updateTask(task.copy(isPinned = !task.isPinned))
                        Toast.makeText(
                            context,
                            if (task.isPinned) "已取消置顶" else "已置顶",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
            
            // 任务详情 Sheet
            selectedTaskId?.let { taskId ->
                val taskComposite by produceState<com.litetask.app.data.model.TaskDetailComposite?>(
                    initialValue = null,
                    key1 = taskId
                ) {
                    homeViewModel.getTaskDetailFlow(taskId).collect { composite ->
                        value = composite
                    }
                }
                
                val uiState by homeViewModel.uiState.collectAsState()
                taskComposite?.let { composite ->
                    TaskDetailSheet(
                        task = composite.task,
                        subTasks = composite.subTasks,
                        reminders = composite.reminders, // 直接使用复合数据中的提醒
                        onDismiss = { selectedTaskId = null },
                        onDelete = { 
                            homeViewModel.deleteTask(it)
                            selectedTaskId = null
                            Toast.makeText(context, "任务已删除", Toast.LENGTH_SHORT).show()
                        },
                        onUpdateTask = { homeViewModel.updateTask(it) },
                        onUpdateSubTask = { sub, isCompleted -> 
                            homeViewModel.updateSubTaskStatus(sub.id, isCompleted)
                        },
                        onAddSubTask = { content ->
                            homeViewModel.addSubTask(composite.task.id, content)
                        },
                        onDeleteSubTask = { subTask ->
                            homeViewModel.deleteSubTask(subTask)
                        },
                        onGenerateSubTasks = {
                            homeViewModel.generateSubTasks(composite.task)
                        },
                        onGenerateSubTasksWithContext = { task ->
                            homeViewModel.showSubTaskInputDialog(task)
                        },
                        isGeneratingSubTasks = uiState.isAnalyzing
                    )
                }
            }
            
            // 编辑对话框 - 需要加载已有提醒
            var editTaskReminders by remember { mutableStateOf<List<com.litetask.app.data.model.Reminder>>(emptyList()) }
            var isLoadingReminders by remember { mutableStateOf(false) }
            
            // 当 taskToEdit 变化时，先加载提醒，加载完成后再显示对话框
            LaunchedEffect(taskToEdit) {
                if (taskToEdit != null && !showEditDialog) {
                    isLoadingReminders = true
                    editTaskReminders = homeViewModel.getRemindersForTaskSync(taskToEdit!!.id)
                    isLoadingReminders = false
                    showEditDialog = true
                }
            }
            
            if (showEditDialog && taskToEdit != null && !isLoadingReminders) {
                AddTaskDialog(
                    initialTask = taskToEdit,
                    initialReminders = editTaskReminders,
                    onDismiss = { 
                        showEditDialog = false
                        taskToEdit = null
                        editTaskReminders = emptyList()
                    },
                    onConfirm = { task ->
                        homeViewModel.updateTask(task)
                        showEditDialog = false
                        taskToEdit = null
                        editTaskReminders = emptyList()
                        Toast.makeText(context, "任务已更新", Toast.LENGTH_SHORT).show()
                    },
                    onConfirmWithReminders = { task, reminderConfigs ->
                        homeViewModel.updateTaskWithReminders(task, reminderConfigs)
                        showEditDialog = false
                        taskToEdit = null
                        editTaskReminders = emptyList()
                        Toast.makeText(context, "任务已更新", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            
            // 子任务详细输入对话框
            val uiState by homeViewModel.uiState.collectAsState()
            if (uiState.showSubTaskInput) {
                val currentTask = uiState.currentTask
                if (currentTask != null) {
                    SubTaskInputDialog(
                        task = currentTask,
                        onDismiss = { homeViewModel.dismissSubTaskInput() },
                        onAnalyze = { context ->
                            homeViewModel.generateSubTasksWithContext(currentTask, context)
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
                        onDismiss = { homeViewModel.dismissSubTaskResult() },
                        onConfirm = { editedSubTasks -> 
                            homeViewModel.confirmAddSubTasks(editedSubTasks)
                        }
                    )
                }
            }
        }
    }
}