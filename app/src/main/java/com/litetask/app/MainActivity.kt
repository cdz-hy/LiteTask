package com.litetask.app

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
    
    // 用于从外部（如通知或小组件）传递的动作或任务ID
    private var pendingTaskId = mutableStateOf<Long?>(null)
    private var pendingAction = mutableStateOf<String?>(null)
    
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
        
        // 处理从通知或小组件点击进入的情况
        handleIntent(intent)
        
        // 请求必要的权限
        requestRequiredPermissions()
        
        setContent {
            LiteTaskTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(
                        initialTaskId = pendingTaskId.value,
                        initialAction = pendingAction.value,
                        onActionHandled = { pendingAction.value = null }
                    )
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
        // 处理 App 已在前台时从外部点击的情况
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        
        // 处理通知点击
        if (intent.getBooleanExtra(NotificationHelper.EXTRA_FROM_NOTIFICATION, false)) {
            pendingTaskId.value = intent.getLongExtra(NotificationHelper.EXTRA_TASK_ID, -1).takeIf { it != -1L }
        }
        
        // 处理动作请求（如来自小组件的 add_task）
        val action = intent.getStringExtra("action")
        if (action != null) {
            pendingAction.value = action
        }
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 当配置变化时（包括夜间模式切换），强制刷新所有widget
        WidgetUpdateHelper.forceRefreshAllWidgets(this)
    }
}

@Composable
fun AppContent(
    initialTaskId: Long? = null,
    initialAction: String? = null,
    onActionHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    var currentHomeView by remember { mutableStateOf("timeline") } // 记住 HomeScreen 的当前视图
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

    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = {
            // 进入动画：从右侧滑入 + 淡入
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300, easing = EaseInOut)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            // 退出动画：向左滑出 + 淡出
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300, easing = EaseInOut)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            // 返回进入动画：从左侧滑入 + 淡入
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300, easing = EaseInOut)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            // 返回退出动画：向右滑出 + 淡出
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300, easing = EaseInOut)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        composable("home") {
            HomeScreenWrapper(
                navController = navController,
                currentHomeView = currentHomeView,
                onViewChanged = { view -> currentHomeView = view },
                hasCheckedPermissions = hasCheckedPermissions,
                onPermissionChecked = { hasCheckedPermissions = true },
                initialAction = initialAction,
                onActionHandled = onActionHandled
            )
        }
        
        composable("settings") {
            com.litetask.app.ui.settings.SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable("search") {
            SearchScreenWrapper(
                navController = navController,
                selectedTaskId = selectedTaskId,
                onSelectedTaskIdChange = { selectedTaskId = it },
                taskToEdit = taskToEdit,
                onTaskToEditChange = { taskToEdit = it },
                showEditDialog = showEditDialog,
                onShowEditDialogChange = { showEditDialog = it }
            )
        }
        
        composable("gantt_fullscreen") {
            GanttFullscreenWrapper(
                navController = navController,
                ganttViewMode = ganttViewMode,
                selectedTaskId = selectedTaskId,
                onSelectedTaskIdChange = { selectedTaskId = it }
            )
        }
    }
}

@Composable
private fun HomeScreenWrapper(
    navController: NavHostController,
    currentHomeView: String,
    onViewChanged: (String) -> Unit,
    hasCheckedPermissions: Boolean,
    onPermissionChecked: () -> Unit,
    initialAction: String? = null,
    onActionHandled: () -> Unit = {}
) {
    val viewModel: HomeViewModel = hiltViewModel()
    
    com.litetask.app.ui.home.HomeScreen(
        onNavigateToAdd = { /* 添加任务功能保持在 HomeScreen 内部 */ },
        onNavigateToSettings = { navController.navigate("settings") },
        onNavigateToSearch = { navController.navigate("search") },
        onNavigateToGanttFullscreen = { viewMode ->
            // 通过 savedStateHandle 传递参数
            navController.currentBackStackEntry?.savedStateHandle?.set("ganttViewMode", viewMode.name)
            navController.navigate("gantt_fullscreen")
        },
        initialView = currentHomeView,
        onViewChanged = onViewChanged,
        shouldCheckPermissions = !hasCheckedPermissions,
        onPermissionChecked = onPermissionChecked,
        viewModel = viewModel,
        initialAction = initialAction,
        onActionHandled = onActionHandled
    )
}

@Composable
private fun SearchScreenWrapper(
    navController: NavHostController,
    selectedTaskId: Long?,
    onSelectedTaskIdChange: (Long?) -> Unit,
    taskToEdit: Task?,
    onTaskToEditChange: (Task?) -> Unit,
    showEditDialog: Boolean,
    onShowEditDialogChange: (Boolean) -> Unit
) {
    val homeViewModel: HomeViewModel = hiltViewModel()
    val context = LocalContext.current
    
    com.litetask.app.ui.search.SearchScreen(
        onBack = { navController.popBackStack() },
        onTaskClick = { task ->
            onSelectedTaskIdChange(task.id)
        },
        onDeleteClick = { task ->
            homeViewModel.deleteTask(task)
            Toast.makeText(context, "任务已删除", Toast.LENGTH_SHORT).show()
        },
        onEditClick = { task ->
            onTaskToEditChange(task)
        },
        onPinClick = { task ->
            if (task.isDone) {
                Toast.makeText(context, "已完成的任务不能置顶", Toast.LENGTH_SHORT).show()
            } else {
                // 允许未完成任务和已过期任务置顶（在各自区域内）
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
                reminders = composite.reminders,
                onDismiss = { onSelectedTaskIdChange(null) },
                onDelete = { 
                    homeViewModel.deleteTask(it)
                    onSelectedTaskIdChange(null)
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
            onShowEditDialogChange(true)
        }
    }
    
    if (showEditDialog && taskToEdit != null && !isLoadingReminders) {
        AddTaskDialog(
            initialTask = taskToEdit,
            initialReminders = editTaskReminders,
            onDismiss = { 
                onShowEditDialogChange(false)
                onTaskToEditChange(null)
                editTaskReminders = emptyList()
            },
            onConfirm = { task ->
                homeViewModel.updateTask(task)
                onShowEditDialogChange(false)
                onTaskToEditChange(null)
                editTaskReminders = emptyList()
                Toast.makeText(context, "任务已更新", Toast.LENGTH_SHORT).show()
            },
            onConfirmWithReminders = { task, reminderConfigs ->
                homeViewModel.updateTaskWithReminders(task, reminderConfigs)
                onShowEditDialogChange(false)
                onTaskToEditChange(null)
                editTaskReminders = emptyList()
                Toast.makeText(context, "任务已更新", Toast.LENGTH_SHORT).show()
            }
        )
    }
    
    // 子任务相关对话框
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

@Composable
private fun GanttFullscreenWrapper(
    navController: NavHostController,
    ganttViewMode: com.litetask.app.ui.components.GanttViewMode,
    selectedTaskId: Long?,
    onSelectedTaskIdChange: (Long?) -> Unit
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val timelineItems by viewModel.timelineItems.collectAsState()
    val context = LocalContext.current
    
    // 从 savedStateHandle 获取传递的参数
    val savedViewMode = navController.currentBackStackEntry?.savedStateHandle?.get<String>("ganttViewMode")
    val actualViewMode = savedViewMode?.let { 
        com.litetask.app.ui.components.GanttViewMode.valueOf(it) 
    } ?: ganttViewMode
    
    // 从 timelineItems 中提取所有任务
    val allLoadedTasks = remember(timelineItems) {
        timelineItems.filterIsInstance<com.litetask.app.ui.home.TimelineItem.TaskItem>()
            .map { it.composite }
    }
    
    com.litetask.app.ui.components.GanttFullscreenView(
        taskComposites = allLoadedTasks,
        onTaskClick = { task ->
            onSelectedTaskIdChange(task.id)
        },
        initialViewMode = actualViewMode,
        onBack = { navController.popBackStack() }
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
        
        val uiState by viewModel.uiState.collectAsState()
        taskComposite?.let { composite ->
            TaskDetailSheet(
                task = composite.task,
                subTasks = composite.subTasks,
                reminders = composite.reminders,
                onDismiss = { onSelectedTaskIdChange(null) },
                onDelete = { 
                    viewModel.deleteTask(it)
                    onSelectedTaskIdChange(null)
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