package com.litetask.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litetask.app.R
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.data.model.TaskType
import com.litetask.app.ui.home.TimelineItem
import com.litetask.app.ui.theme.LocalExtendedColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * 获取任务类型对应的主色
 */
@Composable
private fun getTaskPrimaryColor(type: TaskType): Color {
    val extendedColors = LocalExtendedColors.current
    return when (type) {
        TaskType.WORK -> extendedColors.workTask
        TaskType.LIFE -> extendedColors.lifeTask
        TaskType.STUDY -> extendedColors.studyTask
        TaskType.URGENT -> extendedColors.urgentTask
    }
}

/**
 * 获取任务类型对应的表面色
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

@Composable
fun TimelineView(
    items: List<TimelineItem>,
    onTaskClick: (Task) -> Unit,
    onDeleteClick: (Task) -> Unit,
    onPinClick: (Task) -> Unit,
    onEditClick: (Task) -> Unit,
    onLoadMore: () -> Unit,
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val extendedColors = LocalExtendedColors.current

    // 滚动监听加载更多
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf false
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index >= totalItems - 5
        }
    }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom) onLoadMore()
    }

    // 搜索栏显示/隐藏逻辑
    val showSearchBar by remember {
        derivedStateOf {
            val firstVisibleIndex = listState.firstVisibleItemIndex
            val firstVisibleOffset = listState.firstVisibleItemScrollOffset
            firstVisibleIndex == 0 && firstVisibleOffset < 100
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 搜索框 (可点击，带滚动隐藏动画)
        androidx.compose.animation.AnimatedVisibility(
            visible = showSearchBar,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
        ) {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(24.dp))
                    .clickable { onSearchClick() },
                placeholder = { 
                    Text(
                        stringResource(R.string.search_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ) 
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                enabled = false,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        // 空状态提示
        if (items.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Inbox,
                title = stringResource(R.string.no_tasks_yet),
                subtitle = stringResource(R.string.no_tasks_hint)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item ->
                when (item) {
                    is TimelineItem.TaskItem -> {
                        SwipeRevealItem(
                            task = item.composite.task,
                            onDelete = { onDeleteClick(item.composite.task) },
                            onEdit = { onEditClick(item.composite.task) },
                            onPin = { onPinClick(item.composite.task) }
                        ) {
                            HtmlStyleTaskCard(
                                composite = item.composite,
                                onClick = { onTaskClick(item.composite.task) }
                            )
                        }
                    }
                    is TimelineItem.HistoryHeader -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = extendedColors.divider.copy(alpha = 0.5f)
                            )
                            Text(
                                stringResource(R.string.archived),
                                style = MaterialTheme.typography.labelSmall,
                                color = extendedColors.textTertiary,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = extendedColors.divider.copy(alpha = 0.5f)
                            )
                        }
                    }
                    is TimelineItem.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp), 
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is TimelineItem.PinnedHeader -> {
                        // 置顶任务头部，当前版本暂不显示额外内容
                    }
                }
            }
        }
    }
}

/**
 * 核心卡片组件：Material Design 3 风格，支持暗色模式
 */
@Composable
fun HtmlStyleTaskCard(
    composite: TaskDetailComposite,
    onClick: () -> Unit
) {
    val task = composite.task
    val subTasks = composite.subTasks
    val extendedColors = LocalExtendedColors.current

    val isDone = task.isDone
    val isPinned = task.isPinned

    // 获取颜色配置
    val primaryColor = if (isDone) extendedColors.textTertiary else getTaskPrimaryColor(task.type)
    val surfaceColor = if (isDone) extendedColors.cardBackground else getTaskSurfaceColor(task.type)

    // 背景色：置顶任务使用浅色实心背景，普通任务使用卡片背景色
    val containerColor = if (isPinned && !isDone) surfaceColor else extendedColors.cardBackground

    // 边框：置顶任务有浅色边框
    val borderStroke = if (isPinned && !isDone) {
        androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.2f))
    } else null

    // 阴影：置顶任务阴影稍重
    val elevation = if (isPinned && !isDone) 2.dp else 0.5.dp

    // 进度计算
    val totalSub = subTasks.size
    val completedSub = subTasks.count { it.isCompleted }
    val progress = if (totalSub > 0) completedSub.toFloat() / totalSub else 0f

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        shadowElevation = elevation,
        border = borderStroke,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {

            // 左侧色条
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(primaryColor.copy(alpha = if (isDone) 0.3f else 0.8f))
            )

            // 右侧内容
            Column(
                modifier = Modifier
                    .padding(start = 14.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
                    .weight(1f)
            ) {
                // 1. 标题行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isPinned) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isDone) extendedColors.textTertiary else extendedColors.textPrimary,
                        textDecoration = if (isDone) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )

                    // 状态图标
                    if (isDone) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Done",
                            tint = extendedColors.lifeTask,
                            modifier = Modifier.size(20.dp)
                        )
                    } else if (isPinned) {
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), 
                                    CircleShape
                                )
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = primaryColor,
                                modifier = Modifier.size(14.dp).rotate(45f)
                            )
                        }
                    } else {
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
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 2. 信息行：时间 + 地点 + 截止
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val infoColor = extendedColors.textSecondary.copy(alpha = if (isDone) 0.5f else 0.8f)

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
                    if (!isDone && task.deadline > 0) {
                        val timeLeft = task.deadline - System.currentTimeMillis()
                        if (timeLeft in 0..(24 * 3600 * 1000)) {
                            Spacer(modifier = Modifier.width(8.dp))
                            val isVeryUrgent = timeLeft < (3 * 3600 * 1000)
                            Surface(
                                color = if (isVeryUrgent) 
                                    extendedColors.urgentTaskSurface 
                                else 
                                    extendedColors.workTaskSurface,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (timeLeft < 0) stringResource(R.string.overdue) else stringResource(R.string.within_24h),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = if (isVeryUrgent) 
                                        extendedColors.urgentTask 
                                    else 
                                        extendedColors.workTask
                                )
                            }
                        }
                    }
                }

                // 3. 子任务与进度 (仅未完成且有子任务时)
                if (subTasks.isNotEmpty() && !isDone) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // 进度条
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = primaryColor,
                            trackColor = primaryColor.copy(alpha = 0.15f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                    }

                    // Next Action
                    val nextAction = subTasks.firstOrNull { !it.isCompleted }
                    if (nextAction != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), 
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(extendedColors.textTertiary, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${stringResource(R.string.next_action_prefix)} ${nextAction.content}",
                                style = MaterialTheme.typography.bodySmall,
                                color = extendedColors.textSecondary,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeRevealItem(
    task: Task,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val extendedColors = LocalExtendedColors.current
    val isTaskExpired = task.deadline < System.currentTimeMillis()
    val isTaskCompleted = task.isDone
    val canPinTask = !isTaskExpired && !isTaskCompleted
    
    // 根据是否可以置顶任务调整操作按钮数量和宽度
    val actionCount = if (canPinTask) 3 else 2
    val actionWidth = (180 * actionCount / 3).dp
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
                // 只有未完成且未过期的任务才能置顶
                if (canPinTask) {
                    ActionIcon(
                        icon = if (task.isPinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
                        color = extendedColors.deadlineSoon,
                        onClick = { scope.launch { offsetX.animateTo(0f); onPin() } }
                    )
                }
                
                ActionIcon(
                    icon = Icons.Default.Edit,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { scope.launch { offsetX.animateTo(0f); onEdit() } }
                )
                ActionIcon(
                    icon = Icons.Default.Delete,
                    color = extendedColors.deadlineUrgent,
                    onClick = { scope.launch { offsetX.animateTo(0f); onDelete() } }
                )
            }
        }

        // 前景内容层
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
            content()
        }
    }
}

@Composable
fun ActionIcon(icon: ImageVector, color: Color, onClick: () -> Unit) {
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