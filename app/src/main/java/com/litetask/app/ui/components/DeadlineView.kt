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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.litetask.app.R
import com.litetask.app.data.model.Category
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.data.model.TaskType
import com.litetask.app.ui.search.SearchViewModel
import com.litetask.app.ui.theme.LiteTaskColors
import com.litetask.app.ui.theme.LocalExtendedColors
import com.litetask.app.ui.util.ColorUtils
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DeadlineView(
    tasks: List<TaskDetailComposite>,
    onTaskClick: (Task) -> Unit,
    onDeleteClick: (Task) -> Unit,
    onPinClick: (Task) -> Unit,
    onEditClick: (Task) -> Unit,
    onToggleDone: (Task) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: com.litetask.app.ui.settings.SettingsViewModel = hiltViewModel()
) {
    val now = System.currentTimeMillis()
    
    // 从设置中获取时间范围（小时转毫秒）
    val urgentThreshold = viewModel.getDeadlineUrgentHours() * 60 * 60 * 1000L
    val soonThreshold = viewModel.getDeadlineSoonHours() * 60 * 60 * 1000L
    
    // 区分“已逾期待处理”和“即将到期的活跃任务”，防止逾期未完成任务在紧急视图中消失
    val overdueTasks = tasks
        .filter { !it.task.isDone && it.task.deadline > 0 && (it.task.isExpired || it.task.deadline < now) }
        .sortedByDescending { it.task.deadline }

    val deadlineTasks = tasks
        .filter { !it.task.isDone && !it.task.isExpired && it.task.deadline >= now }
        .sortedBy { it.task.deadline }

    // Group tasks using custom thresholds
    val urgentTasks = deadlineTasks.filter { (it.task.deadline - now) < urgentThreshold }
    val soonTasks = deadlineTasks.filter {
        val diff = it.task.deadline - now
        diff >= urgentThreshold && diff < soonThreshold
    }
    val futureTasks = deadlineTasks.filter { (it.task.deadline - now) >= soonThreshold }
    // 若逾期未完成任务超过 3 条且存在待办截止任务，默认折叠逾期分组，避免把即将到期的任务挤出首屏
    var userOverdueOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val overdueExpanded = userOverdueOverride ?: (overdueTasks.size <= 3 || deadlineTasks.isEmpty())

    Box(modifier = modifier.fillMaxSize()) {
        if (overdueTasks.isEmpty() && deadlineTasks.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Flag,
                title = stringResource(R.string.no_upcoming_deadlines),
                subtitle = stringResource(R.string.no_tasks_hint),
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 已逾期待处理 Section (可折叠)
                if (overdueTasks.isNotEmpty()) {
                    item(key = "overdue_header") {
                        Box(modifier = Modifier.animateItemPlacement()) {
                            DeadlineSectionHeader(
                                title = "已逾期待处理",
                                count = overdueTasks.size,
                                color = MaterialTheme.colorScheme.error,
                                expanded = overdueExpanded,
                                onToggleExpand = { userOverdueOverride = !overdueExpanded }
                            )
                        }
                    }
                    if (overdueExpanded) {
                        items(
                            items = overdueTasks,
                            key = { "overdue_${it.task.id}" }
                        ) { item ->
                            Box(modifier = Modifier.animateItemPlacement()) {
                                DeadlineTaskItem(
                                    composite = item,
                                    isUrgent = false,
                                    isOverdue = true,
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

                // Urgent Section
                if (urgentTasks.isNotEmpty()) {
                    item(key = "urgent_header") {
                        Box(modifier = Modifier.animateItemPlacement()) {
                            if (overdueTasks.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
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
                            if (overdueTasks.isNotEmpty() || urgentTasks.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
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
                            if (overdueTasks.isNotEmpty() || urgentTasks.isNotEmpty() || soonTasks.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
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
}

@Composable
fun DeadlineSectionHeader(
    title: String,
    count: Int,
    color: Color,
    expanded: Boolean? = null,
    onToggleExpand: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onToggleExpand != null) {
                    Modifier.clickable { onToggleExpand() }
                } else {
                    Modifier
                }
            )
            .padding(start = 4.dp, bottom = 4.dp)
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
        Spacer(modifier = Modifier.width(12.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
        )
        if (expanded != null && onToggleExpand != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (expanded) "收起" else "展开",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun DeadlineTaskItem(
    composite: TaskDetailComposite,
    isUrgent: Boolean,
    isSoon: Boolean = false,
    isOverdue: Boolean = false,
    onTaskClick: (Task) -> Unit,
    onDeleteClick: (Task) -> Unit,
    onPinClick: (Task) -> Unit,
    onEditClick: (Task) -> Unit,
    onToggleDone: () -> Unit = {}
) {
    val task = composite.task
    val now = System.currentTimeMillis()
    val diff = task.deadline - now
    val effectiveDiff = if (isOverdue) abs(diff) else max(0L, diff)

    // Calculate time display
    val hoursLeft = effectiveDiff.toDouble() / (1000 * 60 * 60)
    val daysLeft = effectiveDiff.toDouble() / (1000 * 60 * 60 * 24)

    val timeDisplay = when {
        hoursLeft < 48 -> String.format("%.1f", hoursLeft)
        daysLeft < 100 -> String.format("%.1f", daysLeft)
        else -> daysLeft.roundToInt().toString()
    }
    val numberFontSize = when {
        timeDisplay.length >= 5 -> 20.sp
        timeDisplay.length == 4 -> 24.sp
        else -> 28.sp
    }

    val unitDisplay = if (hoursLeft < 48) {
        stringResource(R.string.hours_short) // "Hour" or "小时"
    } else {
        stringResource(R.string.days_short) // "Day" or "天"
    }

    // Colors
    val primaryColor = getTaskColor(task.type, composite.category)
    val urgentColor = LiteTaskColors.urgentTask()
    val soonColor = Color(0xFFEAB308) // Amber/Yellow for soon tasks
    val overdueColor = MaterialTheme.colorScheme.error
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
            } else if (isOverdue) {
                androidx.compose.foundation.BorderStroke(
                    1.dp,
                    overdueColor.copy(alpha = 0.45f)
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
                    // Left: Countdown / Overdue elapsed (等宽数字 + 自适应字号锁定单行居中显示)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(84.dp)
                    ) {
                        if (isOverdue) {
                            Text(
                                text = "已超",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = overdueColor
                            )
                        }
                        Text(
                            text = timeDisplay,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = numberFontSize,
                                fontFeatureSettings = "tnum"
                            ),
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false,
                            color = when {
                                isOverdue -> overdueColor
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
                            .padding(horizontal = 12.dp)
                            .width(1.dp)
                            .height(40.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    // Right: 标题独占整行（最多2行），分类标签下移到元信息行避免挤压标题
                    val accentStatusColor = when {
                        isOverdue -> overdueColor
                        isUrgent -> urgentColor
                        isSoon -> soonColor
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )

                            TaskCheckbox(
                                isDone = task.isDone,
                                onCheckedChange = { onToggleDone() },
                                checkColor = when {
                                    isOverdue -> overdueColor
                                    isUrgent -> urgentColor
                                    isSoon -> soonColor
                                    else -> primaryColor
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                color = primaryColor.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = getTaskTypeName(task.type, composite.category),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor,
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = accentStatusColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.deadline_label, formatDeadline(task.deadline)),
                                style = MaterialTheme.typography.bodySmall,
                                color = accentStatusColor,
                                fontWeight = if (isUrgent || isOverdue) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun getTaskColor(type: TaskType, category: Category? = null): Color {
    if (category != null) {
        return ColorUtils.parseColor(category.colorHex)
    }
    return when (type) {
        TaskType.WORK -> LiteTaskColors.workTask()
        TaskType.LIFE -> LiteTaskColors.lifeTask()
        TaskType.URGENT -> LiteTaskColors.urgentTask()
        TaskType.STUDY -> LiteTaskColors.studyTask()
    }
}

@Composable
private fun getTaskTypeName(type: TaskType, category: Category? = null): String {
    if (category != null) {
        return category.name
    }
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