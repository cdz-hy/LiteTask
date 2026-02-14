package com.litetask.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.litetask.app.R
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskType
import com.litetask.app.ui.theme.LocalExtendedColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// 颜色系统 - 使用主题
private object ConfirmTaskColors {
    @Composable
    fun getPrimary(type: TaskType): Color {
        val extendedColors = LocalExtendedColors.current
        return when (type) {
            TaskType.WORK -> extendedColors.workTask
            TaskType.LIFE -> extendedColors.lifeTask
            TaskType.URGENT -> extendedColors.urgentTask
            TaskType.STUDY -> extendedColors.studyTask
        }
    }

    @Composable
    fun getSurface(type: TaskType): Color {
        val extendedColors = LocalExtendedColors.current
        return when (type) {
            TaskType.WORK -> extendedColors.workTaskSurface
            TaskType.LIFE -> extendedColors.lifeTaskSurface
            TaskType.URGENT -> extendedColors.urgentTaskSurface
            TaskType.STUDY -> extendedColors.studyTaskSurface
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskConfirmationSheet(
    tasks: List<Task>,
    onDismiss: () -> Unit,
    onConfirm: (List<Task>, Map<Int, List<com.litetask.app.data.model.ReminderConfig>>, Map<Int, List<com.litetask.app.data.model.TaskComponent>>) -> Unit,
    onEditTask: (Int, Task) -> Unit = { _, _ -> },
    onDeleteTask: (Int) -> Unit = {},
    amapKey: String? = null,
    onGeocode: (suspend (String) -> com.litetask.app.data.model.AMapRouteData?)? = null,
    onSearchLocations: (suspend (String) -> List<com.litetask.app.data.model.AMapRouteData>)? = null,
    onGetWeather: (suspend (String) -> Pair<String, String>?)? = null
) {
    val extendedColors = LocalExtendedColors.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightDp = configuration.screenHeightDp.dp
    val screenHeightPx = with(density) { screenHeightDp.toPx() }
    
    // 内部管理任务列表状态
    var taskList by remember(tasks) { mutableStateOf(tasks) }
    
    // 存储每个任务的提醒和组件配置
    var taskReminders by remember { mutableStateOf<Map<Int, List<com.litetask.app.data.model.ReminderConfig>>>(emptyMap()) }
    var taskComponents by remember { mutableStateOf<Map<Int, List<com.litetask.app.data.model.TaskComponent>>>(emptyMap()) }
    
    // 编辑对话框状态
    var showEditDialog by remember { mutableStateOf(false) }
    var editingTaskIndex by remember { mutableStateOf(-1) }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    
    // 关闭动画状态
    var isClosing by remember { mutableStateOf(false) }
    
    // 拖拽偏移量（实时跟随手指）
    var dragOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    
    // 计算实际高度：全屏高度 - 拖拽偏移
    val currentHeightPx = (screenHeightPx - dragOffset.coerceAtLeast(0f)).coerceAtLeast(0f)
    val currentHeightDp = with(density) { currentHeightPx.toDp() }
    
    // 关闭动画高度
    val animatedHeight by animateDpAsState(
        targetValue = if (isClosing) 0.dp else currentHeightDp,
        animationSpec = if (isDragging) {
            // 拖拽时不使用动画，直接跟随
            androidx.compose.animation.core.snap()
        } else {
            // 松手后使用弹性动画（弱化弹动，减慢弹回速度）
            androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
            )
        },
        label = "sheetHeight"
    )
    
    // 背景遮罩透明度（跟随拖拽变化）
    val scrimAlpha = when {
        isClosing -> 0f
        isDragging -> (0.5f * (1f - dragOffset / screenHeightPx * 2)).coerceIn(0f, 0.5f)
        else -> 0.5f
    }
    
    // 关闭函数
    fun closeSheet() {
        isClosing = true
    }
    
    // 监听关闭动画完成
    LaunchedEffect(isClosing) {
        if (isClosing) {
            delay(300)
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = { closeSheet() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
            )
            
            // Bottom Sheet 内容
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(animatedHeight)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                color = extendedColors.cardBackground,
                shadowElevation = 16.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 拖拽手柄 - 实时跟随手指
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { 
                                        isDragging = true
                                        dragOffset = 0f 
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        // 下滑超过屏幕30%则关闭，否则回弹
                                        if (dragOffset > screenHeightPx * 0.3f) {
                                            closeSheet()
                                        } else {
                                            dragOffset = 0f
                                        }
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        dragOffset = 0f
                                    },
                                    onDrag = { _, dragAmount ->
                                        // 只允许下滑（正值），限制最大拖拽距离
                                        dragOffset = (dragOffset + dragAmount.y)
                                            .coerceIn(0f, screenHeightPx * 0.65f)
                                    }
                                )
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .background(
                                    extendedColors.divider,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                    
                    // 主内容区域
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        // 标题区域
                        AIResultHeader(taskCount = taskList.size)
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 原始语音文本
                        taskList.firstOrNull()?.originalVoiceText?.let { voiceText ->
                            OriginalVoiceCard(voiceText)
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // 任务卡片列表或空状态
                        if (taskList.isEmpty()) {
                            EmptyTasksState(modifier = Modifier.weight(1f))
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                itemsIndexed(taskList, key = { index, task -> "${task.title}_$index" }) { index, task ->
                                    SwipeableTaskCard(
                                        task = task,
                                        onEdit = {
                                            editingTaskIndex = index
                                            editingTask = task
                                            showEditDialog = true
                                        },
                                        onDelete = {
                                            taskList = taskList.toMutableList().apply { removeAt(index) }
                                            onDeleteTask(index)
                                        }
                                    )
                                }
                            }
                        }

                        // 底部按钮
                        BottomActionBar(
                            taskCount = taskList.size,
                            onDismiss = { closeSheet() },
                            onConfirm = {
                                if (taskList.isNotEmpty()) {
                                    onConfirm(taskList, taskReminders, taskComponents)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    
    // 编辑对话框
    if (showEditDialog && editingTask != null) {
        AddTaskDialog(
            initialTask = editingTask,
            onDismiss = { 
                showEditDialog = false
                editingTask = null
                editingTaskIndex = -1
            },
            onConfirm = { updatedTask ->
                if (editingTaskIndex >= 0 && editingTaskIndex < taskList.size) {
                    taskList = taskList.toMutableList().apply { 
                        set(editingTaskIndex, updatedTask) 
                    }
                    onEditTask(editingTaskIndex, updatedTask)
                }
                showEditDialog = false
                editingTask = null
                editingTaskIndex = -1
            },
            onConfirmWithComponents = { updatedTask, reminders, components ->
                if (editingTaskIndex >= 0 && editingTaskIndex < taskList.size) {
                    taskList = taskList.toMutableList().apply { 
                        set(editingTaskIndex, updatedTask) 
                    }
                    taskReminders = taskReminders.toMutableMap().apply {
                        put(editingTaskIndex, reminders)
                    }
                    taskComponents = taskComponents.toMutableMap().apply {
                        put(editingTaskIndex, components)
                    }
                    onEditTask(editingTaskIndex, updatedTask)
                }
                showEditDialog = false
                editingTask = null
                editingTaskIndex = -1
            },
            amapKey = amapKey,
            onGeocode = onGeocode,
            onSearchLocations = onSearchLocations,
            onGetWeather = onGetWeather
        )
    }
}


@Composable
private fun EmptyTasksState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = Color(0xFFFFF3E0),
            shape = CircleShape,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Outlined.SearchOff,
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.ai_no_task_recognized),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.ai_no_task_info),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 22.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.ai_try_saying),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ai_example_tasks),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun AIResultHeader(taskCount: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = stringResource(R.string.ai_recognition_result),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.parsed_tasks_count, taskCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OriginalVoiceCard(voiceText: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.FormatQuote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = voiceText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SwipeableTaskCard(
    task: Task,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalDensity.current
    val actionWidth = 120.dp
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
    ) {
        // 背景操作层
        if (offsetX.value < -30f) {
            Row(
                modifier = Modifier
                    .width(actionWidth)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SwipeActionIcon(
                    icon = Icons.Default.Edit,
                    color = Color(0xFF0B57D0),
                    onClick = { scope.launch { offsetX.animateTo(0f); onEdit() } }
                )
                SwipeActionIcon(
                    icon = Icons.Default.Delete,
                    color = Color(0xFFF43F5E),
                    onClick = { scope.launch { offsetX.animateTo(0f); onDelete() } }
                )
            }
        }

        // 前景卡片
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            val target = (offsetX.value + delta).coerceIn(-actionWidthPx, 0f)
                            offsetX.snapTo(target)
                        }
                    },
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        val target = if (offsetX.value < -actionWidthPx / 2 || velocity < -1500) {
                            -actionWidthPx
                        } else {
                            0f
                        }
                        scope.launch {
                            offsetX.animateTo(target, initialVelocity = velocity)
                        }
                    }
                )
        ) {
            AITaskCard(task = task)
        }
    }
}

@Composable
private fun SwipeActionIcon(icon: ImageVector, color: Color, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), CircleShape)
            .size(48.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
    }
}


@Composable
private fun AITaskCard(task: Task) {
    val primaryColor = ConfirmTaskColors.getPrimary(task.type)
    val extendedColors = LocalExtendedColors.current

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // 左侧色条
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(primaryColor.copy(alpha = 0.8f))
            )

            Column(
                modifier = Modifier
                    .padding(start = 14.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
                    .weight(1f)
            ) {
                // 标题行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = extendedColors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )

                    // 类型标签
                    Surface(
                        color = primaryColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = getTaskTypeName(task.type),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = primaryColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 描述
                if (!task.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = extendedColors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 时间信息行
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val infoColor = extendedColors.textSecondary.copy(alpha = 0.8f)

                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = infoColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatSmartTime(task.startTime, task.deadline),
                        style = MaterialTheme.typography.bodySmall,
                        color = infoColor
                    )

                    // 紧急标签
                    if (task.deadline > 0) {
                        val timeLeft = task.deadline - System.currentTimeMillis()
                        if (timeLeft in 0..(24 * 3600 * 1000)) {
                            Spacer(modifier = Modifier.width(8.dp))
                            val isVeryUrgent = timeLeft < (3 * 3600 * 1000)
                            Surface(
                                color = if (isVeryUrgent) extendedColors.deadlineUrgentSurface else extendedColors.deadlineSoonSurface,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (timeLeft < 0) stringResource(R.string.overdue) else stringResource(R.string.within_24h),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = if (isVeryUrgent) extendedColors.deadlineUrgent else extendedColors.deadlineSoon
                                )
                            }
                        }
                    }
                }

                // 时长信息
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = extendedColors.textTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val durationHours = calculateDurationInHours(task)
                    Text(
                        text = formatDuration(durationHours),
                        style = MaterialTheme.typography.bodySmall,
                        color = extendedColors.textTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    taskCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(stringResource(R.string.cancel), fontWeight = FontWeight.Medium)
            }
            
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(2f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = taskCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.confirm_add_count, taskCount),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// 辅助函数
@Composable
private fun getTaskTypeName(type: TaskType): String {
    return when (type) {
        TaskType.WORK -> stringResource(R.string.task_type_work)
        TaskType.LIFE -> stringResource(R.string.task_type_life)
        TaskType.URGENT -> stringResource(R.string.task_type_urgent)
        TaskType.STUDY -> stringResource(R.string.task_type_study)
    }
}

private fun formatSmartTime(start: Long, end: Long): String {
    val sdfDate = SimpleDateFormat("MM/dd", Locale.getDefault())
    val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())

    val startStr = "${sdfDate.format(Date(start))} ${sdfTime.format(Date(start))}"
    val endStr = if (end > 0 && end < Long.MAX_VALUE) {
        if (sdfDate.format(Date(start)) == sdfDate.format(Date(end))) {
            sdfTime.format(Date(end))
        } else {
            "${sdfDate.format(Date(end))} ${sdfTime.format(Date(end))}"
        }
    } else ""

    return "$startStr - $endStr"
}

private fun calculateDurationInHours(task: Task): Float {
    val durationMillis = task.deadline - task.startTime
    return durationMillis / (1000f * 60 * 60)
}

@Composable
private fun formatDuration(hours: Float): String {
    return when {
        hours < 1 -> stringResource(R.string.duration_minutes, (hours * 60).toInt())
        hours < 24 -> stringResource(R.string.duration_hours_only, hours.toInt())
        else -> stringResource(R.string.duration_days_hours, (hours / 24).toInt(), (hours % 24).toInt())
    }
}
