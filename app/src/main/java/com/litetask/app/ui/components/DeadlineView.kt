package com.litetask.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litetask.app.R
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.data.model.TaskType
import com.litetask.app.ui.theme.LiteTaskColors
import com.litetask.app.ui.theme.LocalExtendedColors
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DeadlineView(
    tasks: List<TaskDetailComposite>,
    onTaskClick: (Task) -> Unit,
    onDeleteClick: (Task) -> Unit,
    onPinClick: (Task) -> Unit,
    onEditClick: (Task) -> Unit,
    onToggleDone: (Task) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    
    // Filter only active tasks (not done and not expired) with a deadline, sorted by deadline
    val deadlineTasks = tasks
        .filter { !it.task.isDone && !it.task.isExpired && it.task.deadline > 0 }
        .sortedBy { it.task.deadline }

    // Group tasks
    val urgentTasks = deadlineTasks.filter { (it.task.deadline - now) < 24 * 60 * 60 * 1000 }
    val soonTasks = deadlineTasks.filter { 
        val diff = it.task.deadline - now
        diff >= 24 * 60 * 60 * 1000 && diff < 48 * 60 * 60 * 1000 
    }
    val futureTasks = deadlineTasks.filter { (it.task.deadline - now) >= 48 * 60 * 60 * 1000 }

    Box(modifier = modifier.fillMaxSize()) {
        if (deadlineTasks.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Flag,
                title = stringResource(R.string.no_upcoming_deadlines),
                subtitle = stringResource(R.string.no_tasks_hint),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Urgent Section
            if (urgentTasks.isNotEmpty()) {
                item(key = "urgent_header") {
                    Box(modifier = Modifier.animateItemPlacement()) {
                        DeadlineSectionHeader(
                            title = stringResource(R.string.deadline_urgent),
                            count = urgentTasks.size,
                            color = LiteTaskColors.urgentTask()
                        )
                    }
                }
                items(
                    items = urgentTasks,
                    key = { "urgent_${it.task.id}" }
                ) { item ->
                    Box(modifier = Modifier.animateItemPlacement()) {
                        DeadlineTaskItem(
                            composite = item,
                            isUrgent = true,
                            onTaskClick = onTaskClick,
                            onDeleteClick = onDeleteClick,
                            onPinClick = onPinClick,
                            onEditClick = onEditClick,
                            onToggleDone = { onToggleDone(item.task) }
                        )
                    }
                }
            }

            // Soon Section
            if (soonTasks.isNotEmpty()) {
                item(key = "soon_header") {
                    Box(modifier = Modifier.animateItemPlacement()) {
                        if (urgentTasks.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
                        DeadlineSectionHeader(
                            title = stringResource(R.string.deadline_soon),
                            count = soonTasks.size,
                            color = Color(0xFFEAB308) // Amber/Yellow
                        )
                    }
                }
                items(
                    items = soonTasks,
                    key = { "soon_${it.task.id}" }
                ) { item ->
                    Box(modifier = Modifier.animateItemPlacement()) {
                        DeadlineTaskItem(
                            composite = item,
                            isUrgent = false,
                            isSoon = true,
                            onTaskClick = onTaskClick,
                            onDeleteClick = onDeleteClick,
                            onPinClick = onPinClick,
                            onEditClick = onEditClick,
                            onToggleDone = { onToggleDone(item.task) }
                        )
                    }
                }
            }

            // Future Section
            if (futureTasks.isNotEmpty()) {
                item(key = "future_header") {
                    Box(modifier = Modifier.animateItemPlacement()) {
                        if (urgentTasks.isNotEmpty() || soonTasks.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
                        DeadlineSectionHeader(
                            title = stringResource(R.string.deadline_future),
                            count = futureTasks.size,
                            color = Color(0xFF64748B) // Slate/Gray
                        )
                    }
                }
                items(
                    items = futureTasks,
                    key = { "future_${it.task.id}" }
                ) { item ->
                    Box(modifier = Modifier.animateItemPlacement()) {
                        DeadlineTaskItem(
                            composite = item,
                            isUrgent = false,
                            isSoon = false,
                            onTaskClick = onTaskClick,
                            onDeleteClick = onDeleteClick,
                            onPinClick = onPinClick,
                            onEditClick = onEditClick,
                            onToggleDone = { onToggleDone(item.task) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeadlineSectionHeader(title: String, count: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            color = color.copy(alpha = 0.1f),
            shape = CircleShape
        ) {
            Text(
                text = count.toString(),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider(
            modifier = Modifier.width(100.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
        )
    }
}

@Composable
fun DeadlineTaskItem(
    composite: TaskDetailComposite,
    isUrgent: Boolean,
    isSoon: Boolean = false,
    onTaskClick: (Task) -> Unit,
    onDeleteClick: (Task) -> Unit,
    onPinClick: (Task) -> Unit,
    onEditClick: (Task) -> Unit,
    onToggleDone: () -> Unit = {}
) {
    val task = composite.task
    val now = System.currentTimeMillis()
    val diff = task.deadline - now
    
    // Calculate time display
    val hoursLeft = diff.toDouble() / (1000 * 60 * 60)
    val daysLeft = diff.toDouble() / (1000 * 60 * 60 * 24)
    
    val timeDisplay = if (hoursLeft < 48) {
        String.format("%.1f", max(0.0, hoursLeft))
    } else {
        String.format("%.1f", daysLeft)
    }
    
    val unitDisplay = if (hoursLeft < 48) {
        stringResource(R.string.hours_short) // "Hour" or "小时"
    } else {
        stringResource(R.string.days_short) // "Day" or "天"
    }

    // Colors
    val primaryColor = getTaskColor(task.type)
    val urgentColor = LiteTaskColors.urgentTask()
    val soonColor = Color(0xFFEAB308) // Amber/Yellow for soon tasks
    val extendedColors = LocalExtendedColors.current
    
    // Get appropriate card background based on theme
    // 觉得太亮可以改成 colorScheme.surface
    val cardBackgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest
    
    // Urgent Animation - 修改动画周期为3000毫秒（3秒）
    val infiniteTransition = rememberInfiniteTransition(label = "urgent_pulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000), // 从1000毫秒改为3000毫秒
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_alpha"
    )

    SwipeRevealItem(
        task = task,
        onDelete = { onDeleteClick(task) },
        onEdit = { onEditClick(task) },
        onPin = { onPinClick(task) }
    ) {
        Surface(
            onClick = { onTaskClick(task) },
            shape = RoundedCornerShape(24.dp),
            color = cardBackgroundColor,
            shadowElevation = if (isUrgent) 4.dp else if (isSoon) 2.dp else 1.dp,
            border = if (isUrgent) {
                androidx.compose.foundation.BorderStroke(
                    2.dp, 
                    urgentColor.copy(alpha = borderAlpha)
                )
            } else if (isSoon) {
                androidx.compose.foundation.BorderStroke(
                    1.dp,
                    soonColor.copy(alpha = 0.5f) //淡化一些的橙色边缘
                )
            } else {
                androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Countdown
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(80.dp)
                    ) {
                        Text(
                            text = timeDisplay,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = when {
                                isUrgent -> urgentColor
                                isSoon -> soonColor
                                else -> primaryColor
                            }
                        )
                        Text(
                            text = unitDisplay,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .width(1.dp)
                            .height(40.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    // Right: Info
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            
                            // 中间空白区域的复选框
                            TaskCheckbox(
                                isDone = task.isDone,
                                onCheckedChange = { onToggleDone() },
                                checkColor = when {
                                    isUrgent -> urgentColor
                                    isSoon -> soonColor
                                    else -> primaryColor
                                },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            
                            // Type Badge
                            Surface(
                                color = primaryColor.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = getTaskTypeName(task.type),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = when {
                                    isUrgent -> urgentColor
                                    isSoon -> soonColor
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.deadline_label, formatDeadline(task.deadline)),
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    isUrgent -> urgentColor
                                    isSoon -> soonColor
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (isUrgent) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun getTaskColor(type: TaskType): Color {
    return when (type) {
        TaskType.WORK -> LiteTaskColors.workTask()
        TaskType.LIFE -> LiteTaskColors.lifeTask()
        TaskType.URGENT -> LiteTaskColors.urgentTask()
        TaskType.STUDY -> LiteTaskColors.studyTask()
    }
}

@Composable
private fun getTaskTypeName(type: TaskType): String {
    return when (type) {
        TaskType.WORK -> stringResource(R.string.task_type_work)
        TaskType.LIFE -> stringResource(R.string.task_type_life)
        TaskType.URGENT -> stringResource(R.string.task_type_urgent)
        TaskType.STUDY -> stringResource(R.string.task_type_study)
    }
}

private fun formatDeadline(timestamp: Long): String {
    return SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(timestamp)
}