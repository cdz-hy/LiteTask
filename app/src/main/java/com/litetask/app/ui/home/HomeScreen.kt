package com.litetask.app.ui.home

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.litetask.app.R
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.data.model.toTaskComponentsSafe
import com.litetask.app.reminder.PermissionHelper
import com.litetask.app.ui.components.AddTaskDialog
import com.litetask.app.ui.components.DeadlineView
import com.litetask.app.ui.components.GanttView
import com.litetask.app.ui.components.GanttViewMode
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

/**
 * 统一的 高德路线增强动作装配器，供 HomeScreen 与 SearchScreenWrapper 共享，避免重复传递回调。
 */
@Composable
fun rememberAMapRouteActions(viewModel: HomeViewModel): com.litetask.app.ui.components.AMapRouteActions {
    val frequentLocations by viewModel.frequentLocations.collectAsState()
    return remember(frequentLocations, viewModel) {
        com.litetask.app.ui.components.AMapRouteActions(
            frequentLocations = frequentLocations,
            onSearchLocations = { keyword, biasLocation, city ->
                viewModel.searchLocationsWithBias(keyword, biasLocation, city)
            },
            onGeocode = { address -> viewModel.geocodeLocation(address) },
            onGetWeather = { adcode -> viewModel.getWeatherForAdcode(adcode) },
            onGetWeatherForecast = { adcode -> viewModel.getWeatherForecastForAdcode(adcode) },
            onCalculateRoute = { oLat, oLng, dLat, dLng, mode, strategy ->
                viewModel.calculateRoute(oLat, oLng, dLat, dLng, mode, strategy)
            },
            onResolveCurrentLocation = { viewModel.resolveCurrentOriginOption() },
            onCreateDepartureReminder = { taskId, triggerAt, label ->
                viewModel.addDepartureReminder(taskId, triggerAt, label)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAdd: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToHistory: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToUserData: () -> Unit = {},
    onNavigateToSearch: () -> Unit,
    onNavigateToGanttFullscreen: (com.litetask.app.ui.components.GanttViewMode) -> Unit,
    initialView: String = "timeline",
    onViewChanged: (String) -> Unit = {},
    shouldCheckPermissions: Boolean = true,
    onPermissionChecked: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    initialTaskId: Long? = null,
    onTaskHandled: () -> Unit = {},
    initialAction: String? = null,
    onActionHandled: () -> Unit = {},
    initialExternalView: String? = null,
    onViewHandled: () -> Unit = {},
    openFocusTrigger: Boolean = false,
    onFocusTriggerHandled: () -> Unit = {},
    initialSharePayload: com.litetask.app.util.IncomingSharePayload? = null,
    onShareHandled: () -> Unit = {},
    initialAiPrompt: String? = null,
    onAiPromptHandled: () -> Unit = {}
) {
    // ViewModel 数据流
    val todayTasks by viewModel.tasks.collectAsState()
    val amapKey by viewModel.amapKey.collectAsState() // Collect AMap Key
    val amapRouteActions = rememberAMapRouteActions(viewModel)
    val timelineItems by viewModel.timelineItems.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val focusTimerState by viewModel.focusTimerState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var currentView by androidx.compose.runtime.saveable.rememberSaveable { 
        mutableStateOf(if (initialView == "timeline") viewModel.getDefaultHomeView() else initialView) 
    }

    // 转换字符串到 GanttViewMode
    fun stringToGanttViewMode(mode: String): GanttViewMode {
        return when (mode) {
            "TODAY" -> GanttViewMode.TODAY
            "THREE_DAY" -> GanttViewMode.THREE_DAY
            "SEVEN_DAY" -> GanttViewMode.SEVEN_DAY
            "ONE_MONTH" -> GanttViewMode.ONE_MONTH
            else -> GanttViewMode.THREE_DAY
        }
    }

    // 侧边栏状态
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // 防抖状态：防止快速连续点击导致状态混乱
    var isDrawerAnimating by remember { mutableStateOf(false) }

    // 视图切换时的处理（数据已在 ViewModel 初始化时加载，无需额外操作）
    LaunchedEffect(currentView) {
        onViewChanged(currentView)
    }

    // 处理外部（如桌面小组件）传入的视图切换指令
    LaunchedEffect(initialExternalView) {
        if (initialExternalView != null) {
            currentView = initialExternalView
            onViewHandled()
        }
    }

    var selectedTaskId by remember { mutableStateOf<Long?>(null) }

    // 处理外部（通知/小组件任务项）传入的任务详情跳转
    LaunchedEffect(initialTaskId) {
        if (initialTaskId != null && initialTaskId > 0) {
            selectedTaskId = initialTaskId
            onTaskHandled()
        }
    }

    // 处理专注计时通知点击拉起弹窗
    LaunchedEffect(openFocusTrigger) {
        if (openFocusTrigger) {
            viewModel.openFocusSheet(null)
            onFocusTriggerHandled()
        }
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
    var prefilledAddTitle by remember { mutableStateOf<String?>(null) }
    var prefilledAddDescription by remember { mutableStateOf<String?>(null) }
    var prefilledAddComponents by remember { mutableStateOf<List<com.litetask.app.data.model.TaskComponent>>(emptyList()) }
    var activeSharePayload by remember { mutableStateOf<com.litetask.app.util.IncomingSharePayload?>(null) }
    var isImportingShare by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showTextInputDialog by remember { mutableStateOf(false) }
    var initialTextForDialog by remember { mutableStateOf("") }
    var selectedTask by remember { mutableStateOf<Task?>(null) }

    LaunchedEffect(initialSharePayload) {
        if (initialSharePayload != null && !initialSharePayload.isEmpty) {
            activeSharePayload = initialSharePayload
            onShareHandled()
        }
    }

    LaunchedEffect(initialAiPrompt) {
        if (!initialAiPrompt.isNullOrBlank()) {
            initialTextForDialog = initialAiPrompt
            showTextInputDialog = true
            onAiPromptHandled()
        }
    }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var pendingRouteDestination by remember { mutableStateOf<String?>(null) }
    
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

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "常用工具",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp)
                )

                val runDrawerAction: (() -> Unit) -> Unit = { action ->
                    if (!isDrawerAnimating) {
                        isDrawerAnimating = true
                        scope.launch {
                            try {
                                drawerState.close()
                                action()
                            } finally {
                                isDrawerAnimating = false
                            }
                        }
                    }
                }

                // 专注计时 / 番茄钟入口
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Timer, contentDescription = null) },
                    label = { Text("专注计时 / 番茄钟") },
                    selected = focusTimerState.isActive,
                    onClick = {
                        runDrawerAction {
                            viewModel.openFocusSheet(
                                if (focusTimerState.isActive) uiState.focusTargetTask else null
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                // 用户数据选项
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("用户数据与画像") },
                    selected = false,
                    onClick = { runDrawerAction(onNavigateToUserData) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                // AI 分析历史记录选项
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text(stringResource(R.string.ai_history)) },
                    selected = false,
                    onClick = { runDrawerAction(onNavigateToHistory) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Text(
                    text = "设置与管理",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp)
                )

                // 数据管理
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                    label = { Text(stringResource(R.string.data_backup)) },
                    selected = false,
                    onClick = { runDrawerAction(onNavigateToBackup) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                // 设置选项
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.settings)) },
                    selected = false,
                    onClick = { runDrawerAction(onNavigateToSettings) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                // 应用权限选项
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Security, contentDescription = null) },
                    label = { Text("应用权限") },
                    selected = false,
                    onClick = { runDrawerAction(onNavigateToPermissions) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                // 关于选项
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    label = { Text("关于") },
                    selected = false,
                    onClick = { runDrawerAction(onNavigateToAbout) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
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
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                            IconButton(
                                onClick = {
                                    if (!isDrawerAnimating) {
                                        isDrawerAnimating = true
                                        scope.launch {
                                            try {
                                                drawerState.open()
                                            } finally {
                                                isDrawerAnimating = false
                                            }
                                        }
                                    }
                                },
                                enabled = !isDrawerAnimating
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu), tint = MaterialTheme.colorScheme.onSurface)
                            }
                        },
                        actions = {
                            // 快捷搜索（三大核心视图均可直接触达）
                            IconButton(onClick = onNavigateToSearch) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.search_hint),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // 专注计时快捷入口
                            BadgedBox(
                                badge = {
                                    if (focusTimerState.isActive) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(8.dp).offset(x = (-4).dp, y = 4.dp)
                                        )
                                    }
                                }
                            ) {
                                IconButton(
                                    onClick = {
                                        viewModel.openFocusSheet(
                                            if (focusTimerState.isActive) uiState.focusTargetTask else null
                                        )
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = "专注计时",
                                        tint = if (focusTimerState.isActive) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }

                            if (uiState.isGeneratingScheduleAdvice) {
                                val infiniteTransition = rememberInfiniteTransition(label = "schedule")
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 0.4f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "scheduleAlpha"
                                )
                                IconButton(onClick = { viewModel.toggleScheduleAdviceSheet(true) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = "日程建议生成中",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else if (uiState.currentAdvice != null) {
                                BadgedBox(
                                    badge = {
                                        if (uiState.currentAdvice?.isRead == false) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(8.dp).offset(x = (-4).dp, y = 4.dp)
                                            )
                                        }
                                    }
                                ) {
                                    IconButton(onClick = { viewModel.toggleScheduleAdviceSheet(true) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = "日程建议",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )

                    // Animated Segmented Control
                    val options = listOf(
                        Triple("timeline", stringResource(R.string.view_list), Icons.Default.List),
                        Triple("gantt", stringResource(R.string.view_gantt), Icons.Default.ViewTimeline),
                        Triple("deadline", stringResource(R.string.view_deadline), Icons.Default.Flag)
                    )
                    
                    val selectedIndex = remember(currentView) {
                        when(currentView) {
                            "timeline" -> 0
                            "gantt" -> 1
                            "deadline" -> 2
                            else -> 0
                        }
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(4.dp)
                    ) {
                        val segmentWidth = maxWidth / options.size
                        // Animated offset for the indicator
                        val indicatorOffset by animateDpAsState(
                            targetValue = segmentWidth * selectedIndex,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "indicatorOffset"
                        )

                        // Sliding Indicator
                        Box(
                            modifier = Modifier
                                .offset(x = indicatorOffset)
                                .width(segmentWidth)
                                .fillMaxHeight()
                                .shadow(2.dp, CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        )

                        // Text/Icon Content
                        Row(modifier = Modifier.fillMaxSize()) {
                            options.forEachIndexed { index, (id, text, icon) ->
                                val isSelected = index == selectedIndex
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null // No ripple because the indicator slides
                                        ) { 
                                            currentView = id 
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = text,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 进行中的专注计时悬浮横幅
                    AnimatedVisibility(
                        visible = focusTimerState.isActive || focusTimerState.status == com.litetask.app.util.FocusStatus.FINISHED
                    ) {
                        com.litetask.app.ui.components.ActiveFocusBanner(
                            timerState = focusTimerState,
                            onOpenSheet = { viewModel.openFocusSheet(uiState.focusTargetTask) },
                            onTogglePause = {
                                if (focusTimerState.status == com.litetask.app.util.FocusStatus.RUNNING) {
                                    viewModel.pauseFocus()
                                } else {
                                    viewModel.resumeFocus()
                                }
                            },
                            onDismissFinished = { viewModel.resetFocusToIdle() }
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
                            initialTextForDialog = ""
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

                // 处理从外部（如小组件、快捷方式、快速设置磁贴）传来的动作
                LaunchedEffect(initialAction) {
                    if (initialAction != null) {
                        when (initialAction) {
                            "add_task" -> when (defaultFabAction) {
                                "voice" -> onVoiceClickAction()
                                "text" -> onTextInputClickAction()
                                "manual" -> showAddTaskDialog = true
                            }
                            "voice_add" -> onVoiceClickAction()
                            "text_add" -> onTextInputClickAction()
                            "manual_add" -> showAddTaskDialog = true
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
                        onNavigateToFullscreen = { viewMode -> onNavigateToGanttFullscreen(viewMode) },
                        defaultViewMode = stringToGanttViewMode(viewModel.getGanttDefaultMode()),
                        categories = categories
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

                com.litetask.app.ui.components.RecurrenceSpawnFloatingBanner(
                    spawn = uiState.recentRecurrenceSpawn,
                    onViewNext = { nextTaskId ->
                        selectedTaskId = nextTaskId
                        viewModel.dismissRecurrenceSpawnBanner()
                    },
                    onUndo = { viewModel.undoRecentRecurrenceSpawn() },
                    onDismiss = { viewModel.dismissRecurrenceSpawnBanner() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 88.dp)
                        .zIndex(2f)
                )
            }

            if (showAddTaskDialog) {
                AddTaskDialog(
                    initialTitle = prefilledAddTitle,
                    initialDescription = prefilledAddDescription,
                    initialComponents = prefilledAddComponents,
                    onDismiss = {
                        prefilledAddComponents
                            .filterIsInstance<com.litetask.app.data.model.TaskComponent.FileComponent>()
                            .forEach {
                                com.litetask.app.util.AttachmentStorageHelper.deleteManagedFileIfOwned(context, it.data)
                            }
                        prefilledAddTitle = null
                        prefilledAddDescription = null
                        prefilledAddComponents = emptyList()
                        showAddTaskDialog = false
                    },
                    onConfirm = { task ->
                        viewModel.addTask(task)
                        prefilledAddTitle = null
                        prefilledAddDescription = null
                        prefilledAddComponents = emptyList()
                        showAddTaskDialog = false
                    },
                    onConfirmWithReminders = { task, reminderConfigs ->
                        viewModel.addTaskWithReminders(task, reminderConfigs)
                        prefilledAddTitle = null
                        prefilledAddDescription = null
                        prefilledAddComponents = emptyList()
                        showAddTaskDialog = false
                    },
                    onConfirmWithComponents = { task, reminderConfigs, components ->
                        viewModel.addTaskWithComponents(task, reminderConfigs, components)
                        prefilledAddTitle = null
                        prefilledAddDescription = null
                        prefilledAddComponents = emptyList()
                        showAddTaskDialog = false
                    },

                    availableCategories = categories,
                    amapKey = amapKey,
                    onGeocode = { address -> viewModel.geocodeLocation(address) },
                    onSearchLocations = { keyword -> viewModel.searchLocations(keyword) },
                    onGetWeather = { adcode -> viewModel.getWeatherForAdcode(adcode) },
                    amapRouteActions = amapRouteActions,
                    voiceNoteActions = com.litetask.app.ui.components.VoiceNoteActions(
                        onTranscribe = { viewModel.transcribeVoiceNote(it) }
                    )
                )
            }

            activeSharePayload?.let { sharePayload ->
                com.litetask.app.ui.components.ShareImportDialog(
                    payload = sharePayload,
                    activeTasks = remember(allLoadedTasks) {
                        allLoadedTasks.map { it.task }.filter { !it.isDone }
                    },
                    isBusy = isImportingShare,
                    onDismiss = {
                        if (!isImportingShare) activeSharePayload = null
                    },
                    onAiCreate = { combinedText, imageUris, extraUris ->
                        activeSharePayload = null
                        viewModel.analyzeTextInput(
                            text = combinedText,
                            imageUris = imageUris,
                            extraAttachmentUris = extraUris
                        )
                    },
                    onOpenManualAdd = { suggestedTitle, suggestedDesc, links, passes, uris ->
                        isImportingShare = true
                        viewModel.prepareShareForManualAdd(links, passes, uris) { components ->
                            isImportingShare = false
                            activeSharePayload = null
                            prefilledAddTitle = suggestedTitle
                            prefilledAddDescription = suggestedDesc
                            prefilledAddComponents = components
                            showAddTaskDialog = true
                        }
                    },
                    onAttachToTask = { taskId, links, passes, uris ->
                        isImportingShare = true
                        viewModel.importShareToExistingTask(taskId, links, passes, uris) { addedCount ->
                            isImportingShare = false
                            activeSharePayload = null
                            Toast.makeText(
                                context,
                                if (addedCount > 0) "已挂载 $addedCount 项内容到任务" else "未能识别可挂载内容",
                                Toast.LENGTH_SHORT
                            ).show()
                            if (addedCount > 0) selectedTaskId = taskId
                        }
                    }
                )
            }
            
            // 文字输入对话框
            if (showTextInputDialog) {
                TextInputDialog(
                    onDismiss = { 
                        if (!uiState.isAnalyzing) {
                            showTextInputDialog = false 
                            initialTextForDialog = ""
                        }
                    },
                    onAnalyze = { text, uri ->
                        viewModel.analyzeTextInput(text, uri)
                        // 不立即关闭，等分析完成后通过 LaunchedEffect 关闭
                    },
                    isAnalyzing = uiState.isAnalyzing,
                    agentStatus = uiState.agentStatus,
                    agentLogs = uiState.agentLogs,
                    isMultimodalModel = viewModel.isMultimodalModel(),
                    initialText = initialTextForDialog
                )
            }
            
            // 分析完成后关闭文字输入对话框
            LaunchedEffect(uiState.showAiResult, uiState.showAiError) {
                if ((uiState.showAiResult || uiState.showAiError) && showTextInputDialog) {
                    showTextInputDialog = false
                    initialTextForDialog = ""
                }
            }
            
            // 编辑任务时需要加载已有提醒和组件
            var editTaskReminders by remember { mutableStateOf<List<com.litetask.app.data.model.Reminder>>(emptyList()) }
            var editTaskComponents by remember { mutableStateOf<List<com.litetask.app.data.model.TaskComponent>>(emptyList()) }
            var isLoadingTaskData by remember { mutableStateOf(false) }
            
            // 当 taskToEdit 变化时，先加载提醒和组件，加载完成后再显示对话框
            LaunchedEffect(taskToEdit) {
                if (taskToEdit != null && !showEditDialog) {
                    isLoadingTaskData = true
                    val currentTask = taskToEdit!!
                    // 加载提醒和组件
                    val remindersDeferred = viewModel.getRemindersForTaskSync(currentTask.id)
                    val componentsDeferred = viewModel.getComponentsForTaskSync(currentTask.id)

                    val routeDest = pendingRouteDestination
                    pendingRouteDestination = null
                    val finalComponents = if (!routeDest.isNullOrBlank() &&
                        componentsDeferred.none { it is com.litetask.app.data.model.TaskComponent.AMapComponent }
                    ) {
                        val geo = viewModel.geocodeLocation(routeDest)
                            ?: viewModel.searchLocations(routeDest).firstOrNull()
                        if (geo != null) {
                            componentsDeferred + com.litetask.app.data.model.TaskComponent.AMapComponent(
                                id = 0,
                                taskId = currentTask.id,
                                data = geo
                            )
                        } else {
                            componentsDeferred
                        }
                    } else {
                        componentsDeferred
                    }

                    editTaskReminders = remindersDeferred
                    editTaskComponents = finalComponents

                    isLoadingTaskData = false
                    showEditDialog = true
                }
            }

            if (showEditDialog && !isLoadingTaskData) {
                AddTaskDialog(
                    initialTask = taskToEdit,
                    initialReminders = editTaskReminders,
                    initialComponents = editTaskComponents,
                    availableCategories = categories,
                    amapKey = amapKey,
                    onGeocode = { address -> viewModel.geocodeLocation(address) },
                    onSearchLocations = { keyword -> viewModel.searchLocations(keyword) },
                    onGetWeather = { adcode -> viewModel.getWeatherForAdcode(adcode) },
                    amapRouteActions = amapRouteActions,
                    voiceNoteActions = com.litetask.app.ui.components.VoiceNoteActions(
                        onTranscribe = { viewModel.transcribeVoiceNote(it) }
                    ),
                    onDismiss = {
                        showEditDialog = false
                        taskToEdit = null
                        editTaskReminders = emptyList()
                        editTaskComponents = emptyList()
                    },
                    onConfirm = { task ->
                        viewModel.updateTask(task)
                        showEditDialog = false
                        taskToEdit = null
                        editTaskReminders = emptyList()
                        editTaskComponents = emptyList()
                    },
                    onConfirmWithReminders = { task, reminderConfigs ->
                        viewModel.updateTaskWithReminders(task, reminderConfigs)
                        showEditDialog = false
                        taskToEdit = null
                        editTaskReminders = emptyList()
                        editTaskComponents = emptyList()
                    },
                    onConfirmWithComponents = { task, reminderConfigs, components ->
                        viewModel.updateTaskWithComponents(task, reminderConfigs, components)
                        showEditDialog = false
                        taskToEdit = null
                        editTaskReminders = emptyList()
                        editTaskComponents = emptyList()
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
                    components = composite.components.toTaskComponentsSafe(),

                    category = composite.category,
                    amapKey = amapKey,
                    onGetWeather = { adcode -> viewModel.getWeatherForAdcode(adcode) },
                    onGeocode = { address -> viewModel.geocodeLocation(address) },
                    onSearchLocations = { keyword -> viewModel.searchLocations(keyword) },
                    amapRouteActions = amapRouteActions,
                    voiceNoteActions = com.litetask.app.ui.components.VoiceNoteActions(
                        onTranscribe = { viewModel.transcribeVoiceNote(it) },
                        onGenerateSubTasks = { transcript ->
                            viewModel.generateSubTasksWithContext(
                                task = composite.task,
                                additionalContext = "参考语音备忘内容：\n$transcript"
                            )
                        }
                    ),
                    onDismiss = { selectedTaskId = null },
                    onDelete = {
                        viewModel.deleteTask(it)
                        selectedTaskId = null
                    },
                    onUpdateTask = { viewModel.updateTaskWithStatusTracking(composite.task, it) },
                    onUpdateSubTask = { sub, isCompleted ->
                        viewModel.updateSubTaskStatus(sub.id, isCompleted)
                    },
                    onUpdateSubTaskContent = { sub, newContent ->
                        viewModel.updateSubTaskContent(composite.task.id, sub.id, newContent)
                    },
                    onReorderSubTasks = { orderedSubTasks ->
                        viewModel.reorderSubTasks(composite.task.id, orderedSubTasks)
                    },
                    onAddSubTask = { content ->
                        viewModel.addSubTask(composite.task.id, content)
                    },
                    onDeleteSubTask = { subTask ->
                        viewModel.deleteSubTask(subTask)
                    },
                    onGenerateSubTasksWithContext = { task ->
                        viewModel.showSubTaskInputDialog(task)
                    },
                    isGeneratingSubTasks = uiState.isAnalyzing,
                    onAddComponent = { component ->
                        viewModel.addComponentToTask(composite.task.id, component)
                    },
                    onUpdateComponent = { component ->
                        viewModel.updateComponentInTask(component)
                    },
                    onDeleteComponent = { component ->
                        viewModel.deleteComponent(component)
                    },
                    onStartFocus = {
                        selectedTaskId = null
                        viewModel.openFocusSheet(composite.task)
                    }
                )
            }

            if (uiState.showFocusSheet) {
                com.litetask.app.ui.components.FocusTimerSheet(
                    timerState = focusTimerState,
                    targetTask = uiState.focusTargetTask,
                    onDismiss = { viewModel.dismissFocusSheet() },
                    onStart = { task, mode, minutes ->
                        viewModel.startFocus(task, mode, minutes)
                    },
                    onRestart = { mode, minutes ->
                        viewModel.restartFocus(mode, minutes)
                    },
                    onPause = { viewModel.pauseFocus() },
                    onResume = { viewModel.resumeFocus() },
                    onFinishAndSave = {
                        viewModel.finishFocusAndSave()
                        viewModel.dismissFocusSheet()
                    },
                    onAbandon = {
                        viewModel.abandonFocus()
                        viewModel.dismissFocusSheet()
                    },
                    onResetToIdle = { viewModel.resetFocusToIdle() }
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
                    speechSourceName = speechSourceInfo.displayName,
                    agentStatus = uiState.agentStatus,
                    agentLogs = uiState.agentLogs
                )
            }

            if (uiState.showAiResult) {
                TaskConfirmationSheet(
                    tasks = uiState.aiParsedTasks,
                    onDismiss = { viewModel.dismissAiResult() },
                    onConfirm = { tasks, reminders, components -> viewModel.confirmAddTasksWithReminders(tasks, reminders, components) },
                    onEditTask = { index, task -> viewModel.updateAiParsedTask(index, task) },
                    onDeleteTask = { index -> viewModel.deleteAiParsedTask(index) },
                    amapKey = amapKey,
                    onGeocode = { address -> viewModel.geocodeLocation(address) },
                    onSearchLocations = { keyword -> viewModel.searchLocations(keyword) },
                    onGetWeather = { adcode -> viewModel.getWeatherForAdcode(adcode) },
                    amapRouteActions = amapRouteActions,
                    availableCategories = categories,
                    initialComponents = uiState.aiParsedComponents,
                    routeCandidates = uiState.aiRouteCandidates,
                    onSelectRouteCandidate = { index, chosen -> viewModel.selectAiRouteCandidate(index, chosen) }
                )
            }

            if (uiState.showScheduleAdviceSheet) {
                com.litetask.app.ui.components.DailyScheduleAdviceSheet(
                    advice = uiState.currentAdvice,
                    isGenerating = uiState.isGeneratingScheduleAdvice,
                    agentLogs = uiState.scheduleAdviceLogs,
                    errorMessage = uiState.scheduleAdviceError,
                    allTasks = allLoadedTasks,
                    hasAmapKey = !amapKey.isNullOrBlank(),
                    onDismiss = { viewModel.toggleScheduleAdviceSheet(false) },
                    onRefresh = { viewModel.refreshScheduleAdvice() },
                    onOpenTaskDetail = { task ->
                        viewModel.toggleScheduleAdviceSheet(false)
                        selectedTaskId = task.id
                    },
                    onStartFocusTask = { task ->
                        viewModel.toggleScheduleAdviceSheet(false)
                        viewModel.openFocusSheet(task)
                    },
                    onAiDecomposeTask = { task ->
                        viewModel.toggleScheduleAdviceSheet(false)
                        viewModel.showSubTaskInputDialog(task)
                    },
                    onAdjustTaskTime = { task, suggestedStart, suggestedDeadline ->
                        viewModel.toggleScheduleAdviceSheet(false)
                        val oldDuration = (task.deadline - task.startTime).coerceAtLeast(3600_000L)
                        val newStart = suggestedStart ?: task.startTime
                        val newDeadline = when {
                            suggestedDeadline != null && suggestedDeadline >= newStart -> suggestedDeadline
                            suggestedStart != null -> newStart + oldDuration
                            else -> task.deadline
                        }
                        taskToEdit = task.copy(startTime = newStart, deadline = newDeadline)
                    },
                    onAddTaskRoute = { task, suggestedDestination ->
                        viewModel.toggleScheduleAdviceSheet(false)
                        pendingRouteDestination = suggestedDestination
                        taskToEdit = task
                    }
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
                        existingSubTasks = uiState.currentTaskSubTasks,
                        initialMode = uiState.subTaskGenerationMode,
                        onDismiss = { viewModel.dismissSubTaskInput() },
                        onAnalyze = { contextText, imageUri, mode ->
                            viewModel.generateSubTasksWithContext(currentTask, contextText, mode, imageUri)
                        },
                        isAnalyzing = uiState.isAnalyzing,
                        agentStatus = uiState.agentStatus,
                        agentLogs = uiState.agentLogs,
                        isMultimodalModel = viewModel.isMultimodalModel()
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
                        existingSubTasks = uiState.currentTaskSubTasks,
                        initialMode = uiState.subTaskGenerationMode,
                        isRefining = uiState.isRefiningSubTasks,
                        refinementError = uiState.subTaskRefinementError,
                        onRefine = { draftSteps, feedback ->
                            viewModel.refineGeneratedSubTasks(draftSteps, feedback)
                        },
                        onDismiss = { viewModel.dismissSubTaskResult() },
                        onConfirm = { editedSubTasks, replaceUncompleted ->
                            viewModel.confirmAddSubTasks(editedSubTasks, replaceUncompleted)
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
                                MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                            )
                        ),
                        shape = CircleShape
                    )
            )
            
            // 主按钮
            LargeFloatingActionButton(
                onClick = onVoiceClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
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

    // 后台弹出界面权限 (Android 10+)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        if (!PermissionHelper.hasBackgroundActivityPermission(context)) {
            missing.add(
                MissingPermission(
                    name = "后台弹出界面",
                    description = "在后台弹出提醒界面",
                    settingsIntent = PermissionHelper.getBackgroundActivitySettingsIntent(context)
                )
            )
        }
    }

    // 锁屏显示权限（只在未授权时提示）
    if (!PermissionHelper.hasLockScreenPermission(context)) {
        missing.add(
            MissingPermission(
                name = "锁屏显示",
                description = "在锁屏界面显示提醒",
                settingsIntent = PermissionHelper.getLockScreenSettingsIntent(context)
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
