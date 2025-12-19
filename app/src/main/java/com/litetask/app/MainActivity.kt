package com.litetask.app

import android.Manifest
import android.content.Intent
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
import com.litetask.app.ui.components.TaskDetailSheet
import com.litetask.app.ui.home.HomeViewModel
import com.litetask.app.ui.theme.LiteTaskTheme
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
     */
    private fun requestRequiredPermissions() {
        // 1. 请求通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!PermissionHelper.hasNotificationPermission(this)) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // 2. 检查精确闹钟权限 (Android 12+)
        // 注意：SCHEDULE_EXACT_ALARM 不能通过 requestPermissions 请求，需要引导用户去设置
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!PermissionHelper.canScheduleExactAlarms(this)) {
                // 可以在这里显示一个对话框引导用户去设置
                android.util.Log.w("MainActivity", "Exact alarm permission not granted, reminders may not work")
            }
        }
        
        // 3. 检查悬浮窗权限 (Android 6+)
        // 3. 悬浮窗权限 - 不自动跳转，改为在设置页面引导用户开启
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!FloatingReminderService.canDrawOverlays(this)) {
                android.util.Log.w("MainActivity", "Overlay permission not granted, user can enable in Settings")
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
}

@Composable
fun AppContent(initialTaskId: Long? = null) {
    var currentScreen by remember { mutableStateOf("home") }
    var currentHomeView by remember { mutableStateOf("timeline") } // 记住 HomeScreen 的当前视图，初始为列表
    var selectedTaskId by remember { mutableStateOf<Long?>(initialTaskId) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var ganttViewMode by remember { mutableStateOf<com.litetask.app.ui.components.GanttViewMode>(com.litetask.app.ui.components.GanttViewMode.THREE_DAY) }
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
                val taskReminders by produceState<List<com.litetask.app.data.model.Reminder>>(
                    initialValue = emptyList(),
                    key1 = taskId
                ) {
                    viewModel.getRemindersForTask(taskId).collect { reminders ->
                        value = reminders
                    }
                }
                
                taskComposite?.let { composite ->
                    TaskDetailSheet(
                        task = composite.task,
                        subTasks = composite.subTasks,
                        reminders = taskReminders,
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
                        }
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
                
                // 获取任务提醒
                val taskReminders by produceState<List<com.litetask.app.data.model.Reminder>>(
                    initialValue = emptyList(),
                    key1 = taskId
                ) {
                    homeViewModel.getRemindersForTask(taskId).collect { reminders ->
                        value = reminders
                    }
                }
                
                taskComposite?.let { composite ->
                    TaskDetailSheet(
                        task = composite.task,
                        subTasks = composite.subTasks,
                        reminders = taskReminders,
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
                        }
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
        }
    }
}