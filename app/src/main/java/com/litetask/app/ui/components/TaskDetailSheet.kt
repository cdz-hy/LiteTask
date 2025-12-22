package com.litetask.app.ui.components

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litetask.app.R
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.SubTask
import com.litetask.app.data.model.Reminder
import com.litetask.app.data.model.TaskType
import com.litetask.app.ui.theme.LocalExtendedColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 获取任务类型主题色
 */
@Composable
private fun getTaskThemeColor(type: TaskType): Color {
    val extendedColors = LocalExtendedColors.current
    return when (type) {
        TaskType.WORK -> extendedColors.workTask
        TaskType.LIFE -> extendedColors.lifeTask
        TaskType.STUDY -> extendedColors.studyTask
        TaskType.URGENT -> extendedColors.urgentTask
    }
}

/**
 * 获取任务类型表面色
 */
@Composable
private fun getTaskSurfaceColor(type: TaskType): Color {
    val extendedColors = LocalExtendedColors.current
    return when (type) {
        TaskType.WORK -> extendedColors.workTaskSurface
        TaskType.LIFE -> extendedColors.lifeTaskSurface
        TaskType.STUDY -> extendedColors.studyTaskSurface
        TaskType.URGENT -> extendedColors.urgentTaskSurface
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(
    task: Task,
    subTasks: List<SubTask>,
    reminders: List<Reminder> = emptyList(),
    onDismiss: () -> Unit,
    onDelete: (Task) -> Unit,
    onUpdateTask: (Task) -> Unit,
    onUpdateSubTask: (SubTask, Boolean) -> Unit,
    onAddSubTask: (String) -> Unit,
    onDeleteSubTask: (SubTask) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val extendedColors = LocalExtendedColors.current

    var newSubTaskText by remember { mutableStateOf("") }
    var isFullScreen by remember { mutableStateOf(false) }

    // 获取当前任务的主题色
    val themeColor = if (task.isDone) extendedColors.ganttDoneText else getTaskThemeColor(task.type)
    val surfaceColor = if (task.isDone) MaterialTheme.colorScheme.surfaceVariant else getTaskSurfaceColor(task.type)

    fun closeSheetWithAnimation() {
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = extendedColors.cardBackground,
        dragHandle = null,
        windowInsets = WindowInsets.ime
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = 300f
                    )
                )
                .then(
                    if (isFullScreen) {
                        Modifier
                            .fillMaxHeight()
                            .statusBarsPadding() // 避免覆盖状态栏
                    } else {
                        Modifier.wrapContentHeight()
                    }
                )
        ) {
            // --- 顶部手势与 Header ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { /* 惯性处理 */ }
                        ) { change, dragAmount ->
                            change.consume()
                            val sensitivity = 8f
                            if (dragAmount < -sensitivity) {
                                if (!isFullScreen) isFullScreen = true
                            } else if (dragAmount > sensitivity) {
                                if (isFullScreen) {
                                    isFullScreen = false
                                } else {
                                    closeSheetWithAnimation()
                                }
                            }
                        }
                    }
            ) {
                // Header Content
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    // Drag Handle
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .background(extendedColors.divider, RoundedCornerShape(2.dp))
                        )
                    }

                    // Title & Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // Task Type Badge
                            Surface(
                                color = surfaceColor,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Text(
                                    text = getTaskTypeName(task.type),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = themeColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (task.isDone) Color.Gray else MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (task.isDone) TextDecoration.LineThrough else null
                            )
                        }

                        IconButton(
                            onClick = { closeSheetWithAnimation() },
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = extendedColors.textTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // --- Scrollable Content ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // 1. Time Info Card (Visualized)
                TimeDisplayCard(
                    startTime = task.startTime,
                    deadline = task.deadline,
                    themeColor = themeColor,
                    isDone = task.isDone
                )

                // 2. Reminders (Compact)
                if (reminders.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CompactReminderRow(
                        reminders = reminders,
                        startTime = task.startTime,
                        deadline = task.deadline,
                        themeColor = themeColor
                    )
                }

                // 3. Description
                if (!task.description.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            Icon(
                                Icons.Outlined.Description,
                                contentDescription = null,
                                tint = extendedColors.textTertiary,
                                modifier = Modifier.size(18.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = task.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 4. Subtasks Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.subtasks_steps),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (subTasks.isNotEmpty()) {
                        val completed = subTasks.count { it.isCompleted }
                        Text(
                            text = "$completed/${subTasks.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 5. Subtasks List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isFullScreen) {
                                Modifier.weight(1f)
                            } else {
                                Modifier
                                    .heightIn(max = 240.dp)
                                    .weight(1f, fill = false)
                            }
                        )
                ) {
                    if (subTasks.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.no_subtasks_hint), // 需确保 strings.xml 有此资源或使用硬编码
                                style = MaterialTheme.typography.bodyMedium,
                                color = extendedColors.textTertiary.copy(alpha = 0.6f),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    } else {
                        items(items = subTasks.sortedBy { it.id }, key = { it.id }) { subTask ->
                            SubTaskItem(
                                subTask = subTask,
                                themeColor = themeColor,
                                onToggleComplete = { onUpdateSubTask(subTask, it) },
                                onDelete = { onDeleteSubTask(subTask) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 6. Add Subtask Input
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = newSubTaskText,
                            onValueChange = { newSubTaskText = it },
                            placeholder = { Text(stringResource(R.string.add_subtask), color = extendedColors.textTertiary) },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true
                        )
                        IconButton(
                            onClick = {
                                if (newSubTaskText.isNotBlank()) {
                                    onAddSubTask(newSubTaskText)
                                    newSubTaskText = ""
                                }
                            },
                            enabled = newSubTaskText.isNotBlank()
                        ) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = if (newSubTaskText.isNotBlank()) themeColor else extendedColors.textTertiary,
                                modifier = Modifier
                                    .background(
                                        if (newSubTaskText.isNotBlank()) themeColor.copy(alpha = 0.1f) else Color.Transparent,
                                        CircleShape
                                    )
                                    .padding(8.dp)
                                    .size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 7. Bottom Actions
                Row(
                    modifier = Modifier.padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Delete Button
                    OutlinedButton(
                        onClick = {
                            onDelete(task)
                            closeSheetWithAnimation()
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                            containerColor = Color.Transparent
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_task))
                    }

                    // Complete/Reopen Button
                    if (!task.isDone || task.deadline >= System.currentTimeMillis()) {
                        Button(
                            onClick = {
                                if (task.isDone && task.deadline < System.currentTimeMillis()) {
                                    Toast.makeText(context, R.string.task_cannot_undone_expired, Toast.LENGTH_SHORT).show()
                                } else {
                                    val updatedTask = if (!task.isDone) {
                                        task.copy(isDone = true, isPinned = false)
                                    } else {
                                        task.copy(isDone = false)
                                    }
                                    onUpdateTask(updatedTask)
                                    closeSheetWithAnimation()
                                }
                            },
                            modifier = Modifier.weight(1.5f).height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (task.isDone) extendedColors.textSecondary else themeColor
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Icon(
                                if (task.isDone) Icons.Default.Refresh else Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (task.isDone) stringResource(R.string.mark_undone) else stringResource(R.string.mark_done),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 美化的时间显示卡片
 * 显示：开始时间 -> 持续时间 -> 截止时间
 */
@Composable
private fun TimeDisplayCard(
    startTime: Long,
    deadline: Long,
    themeColor: Color,
    isDone: Boolean
) {
    val durationMillis = deadline - startTime
    val days = TimeUnit.MILLISECONDS.toDays(durationMillis)
    val hours = TimeUnit.MILLISECONDS.toHours(durationMillis) % 24
    val extendedColors = LocalExtendedColors.current

    // 计算任务状态
    val now = System.currentTimeMillis()
    val isExpired = now > deadline
    val notStarted = now < startTime

    val statusColor = when {
        isDone -> extendedColors.ganttDoneText
        isExpired -> extendedColors.deadlineUrgent
        notStarted -> extendedColors.textTertiary
        else -> themeColor
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = extendedColors.cardBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, extendedColors.divider),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Start
            TimeColumn(
                label = stringResource(R.string.start_time),
                time = startTime,
                isPrimary = false
            )

            // Visual Connector
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            ) {
                // Duration Label
                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (days > 0) "${days}d ${hours}h" else "${hours}h",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Arrow Line
                Box(contentAlignment = Alignment.Center) {
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                    Icon(
                        Icons.Default.ArrowRightAlt,
                        contentDescription = null,
                        tint = statusColor
                    )
                }
            }

            // End
            TimeColumn(
                label = stringResource(R.string.deadline),
                time = deadline,
                isPrimary = true,
                isUrgent = !isDone && !isExpired && (deadline - now < 24 * 60 * 60 * 1000)
            )
        }
    }
}

@Composable
private fun TimeColumn(
    label: String,
    time: Long,
    isPrimary: Boolean,
    isUrgent: Boolean = false
) {
    val extendedColors = LocalExtendedColors.current
    
    Column(horizontalAlignment = if (isPrimary) Alignment.End else Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isUrgent) {
                Icon(
                    Icons.Outlined.Timer,
                    contentDescription = null,
                    tint = extendedColors.deadlineUrgent,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isUrgent) extendedColors.deadlineUrgent else extendedColors.textTertiary
            )
        }
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time)),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (isUrgent) extendedColors.deadlineUrgent else extendedColors.textPrimary
        )
        Text(
            text = SimpleDateFormat("MM/dd EEE", Locale.getDefault()).format(Date(time)),
            style = MaterialTheme.typography.bodySmall,
            color = if (isUrgent) extendedColors.deadlineUrgent else extendedColors.textTertiary
        )
    }
}

/**
 * 紧凑的横向滚动提醒列表
 */
@Composable
private fun CompactReminderRow(
    reminders: List<Reminder>,
    startTime: Long,
    deadline: Long,
    themeColor: Color
) {
    val extendedColors = LocalExtendedColors.current
    
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Alarm,
                contentDescription = null,
                tint = extendedColors.textTertiary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.task_reminders),
                style = MaterialTheme.typography.labelMedium,
                color = extendedColors.textTertiary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(reminders.sortedBy { it.triggerAt }) { reminder ->
                val isFired = reminder.isFired
                val isPast = reminder.triggerAt < System.currentTimeMillis()

                // Chip Style
                val bgColor = when {
                    isFired -> MaterialTheme.colorScheme.surfaceVariant
                    isPast -> extendedColors.deadlineUrgentSurface
                    else -> themeColor.copy(alpha = 0.08f)
                }

                val contentColor = when {
                    isFired -> extendedColors.textTertiary
                    isPast -> extendedColors.deadlineUrgent
                    else -> themeColor
                }

                val label = getReminderLabel(reminder, startTime, deadline)
                val timeStr = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(reminder.triggerAt))

                Surface(
                    color = bgColor,
                    shape = RoundedCornerShape(8.dp),
                    border = if (isPast && !isFired) androidx.compose.foundation.BorderStroke(1.dp, contentColor.copy(alpha = 0.3f)) else null
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isFired) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = contentColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 美化的子任务项
 */
@Composable
private fun SubTaskItem(
    subTask: SubTask,
    themeColor: Color,
    onToggleComplete: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var isDeleting by remember { mutableStateOf(false) }
    val extendedColors = LocalExtendedColors.current

    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDeleting) 0f else 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
        label = "alpha"
    )

    LaunchedEffect(isDeleting) {
        if (isDeleting) {
            kotlinx.coroutines.delay(300)
            onDelete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .then(if(isDeleting) Modifier.height(0.dp) else Modifier.wrapContentHeight())
    ) {
        Surface(
            color = extendedColors.cardBackground,
            modifier = Modifier.fillMaxWidth().clickable { onToggleComplete(!subTask.isCompleted) },
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Custom Checkbox
                IconButton(onClick = { onToggleComplete(!subTask.isCompleted) }) {
                    Icon(
                        if (subTask.isCompleted) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = if (subTask.isCompleted) extendedColors.textTertiary else themeColor
                    )
                }

                Text(
                    text = subTask.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (subTask.isCompleted) extendedColors.textTertiary else extendedColors.textPrimary,
                    textDecoration = if (subTask.isCompleted) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { isDeleting = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.delete),
                        tint = extendedColors.divider,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// --- 以下辅助函数保持原逻辑，确保功能不丢失 ---

@Composable
private fun getTaskTypeName(type: TaskType): String {
    return when (type) {
        TaskType.WORK -> stringResource(R.string.task_type_work)
        TaskType.LIFE -> stringResource(R.string.task_type_life)
        TaskType.URGENT -> stringResource(R.string.task_type_urgent)
        TaskType.STUDY -> stringResource(R.string.task_type_study)
    }
}

/**
 * 根据提醒时间生成标签 (逻辑复用原文件)
 */
@Composable
private fun getReminderLabel(reminder: Reminder, startTime: Long, deadline: Long): String {
    val triggerAt = reminder.triggerAt
    if (!reminder.label.isNullOrEmpty()) return reminder.label

    return when {
        triggerAt == startTime -> stringResource(R.string.reminder_at_start)
        triggerAt == startTime - 60 * 60 * 1000 -> stringResource(R.string.reminder_before_start_1h)
        triggerAt == startTime - 24 * 60 * 60 * 1000 -> stringResource(R.string.reminder_before_start_1d)
        triggerAt == deadline - 60 * 60 * 1000 -> stringResource(R.string.reminder_before_end_1h)
        triggerAt == deadline - 24 * 60 * 60 * 1000 -> stringResource(R.string.reminder_before_end_1d)
        else -> {
            val diffFromStart = startTime - triggerAt
            val diffFromEnd = deadline - triggerAt
            when {
                diffFromStart > 0 -> formatTimeDiff(diffFromStart, true)
                diffFromEnd > 0 -> formatTimeDiff(diffFromEnd, false)
                else -> stringResource(R.string.reminder_custom)
            }
        }
    }
}

@Composable
private fun formatTimeDiff(diffMillis: Long, isBeforeStart: Boolean): String {
    val minutes = diffMillis / (60 * 1000)
    val hours = diffMillis / (60 * 60 * 1000)
    val days = diffMillis / (24 * 60 * 60 * 1000)

    val baseStr = if (isBeforeStart) stringResource(R.string.reminder_before_start) else stringResource(R.string.reminder_before_end)

    return when {
        days > 0 -> "$baseStr${days}${stringResource(R.string.reminder_unit_days)}"
        hours > 0 -> "$baseStr${hours}${stringResource(R.string.reminder_unit_hours)}"
        else -> "$baseStr${minutes}${stringResource(R.string.reminder_unit_minutes)}"
    }
}
