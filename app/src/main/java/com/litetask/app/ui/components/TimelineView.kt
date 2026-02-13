package com.litetask.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.litetask.app.ui.util.ColorUtils
import com.litetask.app.data.model.Category
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.litetask.app.data.model.ComponentType
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/** 获取任务类型主色 */
/** 获取任务类型主色 */
@Composable
private fun getTaskPrimaryColor(type: TaskType, category: Category? = null): Color {
    if (category != null) {
        return ColorUtils.parseColor(category.colorHex)
    }
    val extendedColors = LocalExtendedColors.current
    return when (type) {
        TaskType.WORK -> extendedColors.workTask
        TaskType.LIFE -> extendedColors.lifeTask
        TaskType.STUDY -> extendedColors.studyTask
        TaskType.URGENT -> extendedColors.urgentTask
    }
}

/** 获取任务类型表面色 */
@Composable
private fun getTaskSurfaceColor(type: TaskType, category: Category? = null): Color {
    if (category != null) {
        val primary = ColorUtils.parseColor(category.colorHex)
        return ColorUtils.getSurfaceColor(primary)
    }
    val extendedColors = LocalExtendedColors.current
    return when (type) {
        TaskType.WORK -> extendedColors.workTaskSurface
        TaskType.LIFE -> extendedColors.lifeTaskSurface
        TaskType.STUDY -> extendedColors.studyTaskSurface
        TaskType.URGENT -> extendedColors.urgentTaskSurface
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TimelineView(
    items: List<TimelineItem>,
    onTaskClick: (Task) -> Unit,
    onDeleteClick: (Task) -> Unit,
    onPinClick: (Task) -> Unit,
    onEditClick: (Task) -> Unit,
    onToggleDone: (Task) -> Unit = {},
    onLoadMore: () -> Unit,
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val extendedColors = LocalExtendedColors.current

    // 去重过滤：防止任务重复显示
    val filteredItems = remember(items) {
        val seenTaskIds = mutableSetOf<Long>()
        items.filter { item ->
            when (item) {
                is TimelineItem.TaskItem -> {
                    val taskId = item.composite.task.id
                    if (taskId in seenTaskIds) {
                        false
                    } else {
                        seenTaskIds.add(taskId)
                        true
                    }
                }
                else -> true
            }
        }
    }

    // 分页加载触发检测
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf false
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index >= totalItems - 8
        }
    }

    // 防抖加载更多
    var lastLoadMoreTime by remember { mutableStateOf(0L) }
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastLoadMoreTime > 500) {
                lastLoadMoreTime = currentTime
                onLoadMore()
            }
        }
    }

    // 搜索栏显示控制
    val showSearchBar by remember {
        derivedStateOf {
            val firstVisibleIndex = listState.firstVisibleItemIndex
            val firstVisibleOffset = listState.firstVisibleItemScrollOffset
            firstVisibleIndex == 0 && firstVisibleOffset < 50
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

        // 空状态
        if (filteredItems.isEmpty()) {
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
            items(
                items = filteredItems,
                key = { item ->
                    when (item) {
                        is TimelineItem.TaskItem -> {
                            // 包含状态信息的稳定 key
                            val task = item.composite.task
                            "task_${task.id}_${task.isDone}_${task.isExpired}_${task.isPinned}"
                        }
                        is TimelineItem.ExpiredHeader -> "expired_header"
                        is TimelineItem.HistoryHeader -> "archived_header"
                        is TimelineItem.Loading -> "loading"
                    }
                }
            ) { item ->
                // 稳定的动画参数
                Box(modifier = Modifier.animateItemPlacement(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )) {
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
                                    onClick = { onTaskClick(item.composite.task) },
                                    onToggleDone = { onToggleDone(item.composite.task) }
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
                        is TimelineItem.ExpiredHeader -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = extendedColors.deadlineUrgent.copy(alpha = 0.3f)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.expired_tasks_header),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extendedColors.deadlineUrgent
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = extendedColors.deadlineUrgent.copy(alpha = 0.3f)
                                )
                            }
                        }
                        is TimelineItem.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Material Design 3 风格任务卡片 */
@Composable
fun HtmlStyleTaskCard(
    composite: TaskDetailComposite,
    onClick: () -> Unit,
    onToggleDone: () -> Unit = {}
) {
    val task = composite.task
    val subTasks = composite.subTasks
    val category = composite.category
    val extendedColors = LocalExtendedColors.current

    val isDone = task.isDone
    val isPinned = task.isPinned
    val isExpired = task.isExpired

    // 颜色状态计算
    val basePrimaryColor = getTaskPrimaryColor(task.type, category)
    val baseSurfaceColor = getTaskSurfaceColor(task.type, category)

    val primaryColor = when {
        isDone -> extendedColors.textTertiary
        isExpired -> basePrimaryColor.copy(alpha = 0.5f)
        else -> basePrimaryColor
    }
    
    val surfaceColor = when {
        isDone -> extendedColors.cardBackground
        isExpired -> baseSurfaceColor.copy(alpha = 0.3f)
        else -> baseSurfaceColor
    }


    // 原本任务卡片配色
//    val containerColor = when {
//        isPinned && !isDone && !isExpired -> surfaceColor
//        isPinned && !isDone && isExpired -> extendedColors.cardBackground
//        else -> extendedColors.cardBackground
//    }
//
//    val elevation = when {
//        isPinned && !isDone && !isExpired -> 2.dp
//        isPinned && !isDone && isExpired -> 1.5.dp
//        else -> 0.5.dp
//    }

    val containerColor = when {
        // 如果觉得未完成任务卡片太亮了，后期可以改成surfaceContainerMedium
        isPinned && !isDone && !isExpired -> MaterialTheme.colorScheme.surfaceContainerLowest// 置顶未完成：使用最低容器色（最亮/纯白）
        isPinned && !isDone && isExpired -> MaterialTheme.colorScheme.surfaceContainerHigh
        !isDone && !isExpired -> MaterialTheme.colorScheme.surfaceContainerLowest // 普通未完成：使用最低容器色（最亮/纯白）
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val elevation = when {
        isPinned && !isDone && !isExpired -> 3.dp // 置顶任务更高的阴影
        isPinned && !isDone && isExpired -> 2.dp
        !isDone && !isExpired -> 0.5.dp // 普通任务保持轻微阴影
        else -> 0.dp
    }

    val borderStroke = when {
        isPinned && !isDone && !isExpired -> androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.2f))
        isPinned && !isDone && isExpired -> androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f))
        else -> null
    }

    // 动画颜色转换
    val animatedPrimaryColor by animateColorAsState(
        targetValue = primaryColor,
        animationSpec = tween(300),
        label = "primary_color"
    )
    
    val animatedContainerColor by animateColorAsState(
        targetValue = containerColor,
        animationSpec = tween(300),
        label = "container_color"
    )

    val animatedElevation by animateDpAsState(
        targetValue = elevation,
        animationSpec = tween(300),
        label = "elevation"
    )

    // 进度计算
    val totalSub = subTasks.size
    val completedSub = subTasks.count { it.isCompleted }
    val progress = if (totalSub > 0) completedSub.toFloat() / totalSub else 0f

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = animatedContainerColor,
        shadowElevation = animatedElevation,
        border = borderStroke, // 边框逻辑由于涉及 null 比较，暂时保持原样或稍后优化
        modifier = Modifier.fillMaxWidth()
    ) {
        Box {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {

                // 左侧色条
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                        .background(animatedPrimaryColor.copy(alpha = if (isDone) 0.3f else if (isExpired) 0.4f else 0.8f))
                )

                // 右侧内容
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
                        // 标题
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isPinned) FontWeight.Bold else FontWeight.SemiBold,
                            color = when {
                                isDone -> extendedColors.textTertiary
                                isExpired -> extendedColors.textPrimary.copy(alpha = 0.6f)
                                else -> extendedColors.textPrimary
                            }.let { targetColor ->
                                animateColorAsState(targetColor, tween(300)).value
                            },
                            textDecoration = if (isDone) TextDecoration.LineThrough else null,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )

                        // 复选框
                        val checkboxColor = if (isDone) {
                            extendedColors.textTertiary
                        } else {
                            basePrimaryColor
                        }
                        
                        TaskCheckbox(
                            isDone = isDone,
                            onCheckedChange = { onToggleDone() },
                            checkColor = checkboxColor,
                            isGrayedOut = isDone,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // 置顶图标或类型标签
                        if (isPinned && !isDone) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = animatedPrimaryColor,
                                modifier = Modifier.size(16.dp).rotate(45f)
                            )
                        } else {
                            Surface(
                                color = animatedPrimaryColor.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = category?.name ?: getTaskTypeName(task.type),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = animatedPrimaryColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 信息行：时间和状态标签
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val infoColor = when {
                            isDone -> extendedColors.textSecondary.copy(alpha = 0.5f)
                            isExpired -> extendedColors.textSecondary.copy(alpha = 0.5f)
                            else -> extendedColors.textSecondary.copy(alpha = 0.8f)
                        }
                        
                        val animatedInfoColor by animateColorAsState(infoColor, tween(300), label = "info_color")

                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = animatedInfoColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatSmartTime(task.startTime, task.deadline),
                            style = MaterialTheme.typography.bodySmall,
                            color = animatedInfoColor
                        )

                        // 过期标签
                        if (isExpired && !isDone) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = extendedColors.deadlineUrgentSurface.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.expired_tasks_header),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = extendedColors.deadlineUrgent.copy(alpha = 0.9f)
                                )
                            }
                        } else if (!isDone && task.deadline > 0) {
                            // 紧急标签
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

                    // 子任务进度 (仅未完成且有子任务时)
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
                    }

                    // Task Components Icons
                    if (composite.components.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(), 
                            verticalAlignment = Alignment.CenterVertically, 
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            composite.components.forEach { component ->
                                val (icon, label) = when (component.type) {
                                    ComponentType.AMAP_ROUTE -> Icons.Default.Place to "Route"
                                    ComponentType.FILE_ATTACHMENT -> Icons.Default.AttachFile to "File"
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(
                                            color = extendedColors.textSecondary.copy(alpha = 0.05f),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = extendedColors.textSecondary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extendedColors.textSecondary.copy(alpha = 0.7f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }


                        // 下一步行动
                        val nextAction = subTasks.firstOrNull { !it.isCompleted }
                        if (nextAction != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(
                                            alpha = if (isExpired) 0.3f else 0.5f
                                        ), 
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            if (isExpired) 
                                                extendedColors.textTertiary.copy(alpha = 0.5f)
                                            else 
                                                extendedColors.textTertiary, 
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${stringResource(R.string.next_action_prefix)} ${nextAction.content}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isExpired) 
                                        extendedColors.textSecondary.copy(alpha = 0.5f)
                                    else 
                                        extendedColors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            
            // 状态遮罩
            when {
                // 过期置顶任务遮罩
                isExpired && !isDone && isPinned -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                primaryColor.copy(alpha = 0.08f),
                                RoundedCornerShape(24.dp)
                            )
                    )
                }
                // 过期任务遮罩
                isExpired && !isDone -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Color.Gray.copy(alpha = 0.2f),
                                RoundedCornerShape(24.dp)
                            )
                    )
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
    val isTaskCompleted = task.isDone
    val canPinTask = !isTaskCompleted
    
    // 操作按钮配置
    val actionCount = if (canPinTask) 3 else 2
    val actionWidth = (180 * actionCount / 3).dp
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    
    // 删除动画状态
    var isDismissed by remember { mutableStateOf(false) }
    val animationDuration = 400

    AnimatedVisibility(
        visible = !isDismissed,
        exit = shrinkVertically(
            animationSpec = tween(animationDuration, easing = FastOutSlowInEasing)
        ) + fadeOut(
            animationSpec = tween(animationDuration / 2)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
        // 背景操作层
        if (offsetX.value < -30f) {
            var isDeleteConfirming by remember { mutableStateOf(false) }
            
            // 自动重置确认状态
            LaunchedEffect(isDeleteConfirming) {
                if (isDeleteConfirming) {
                    delay(3000)
                    isDeleteConfirming = false
                }
            }
            // 当滑动关闭时也重置状态
            LaunchedEffect(offsetX.value) {
                if (offsetX.value > -10f) {
                    isDeleteConfirming = false
                }
            }

            Row(
                modifier = Modifier
                    .width(actionWidth)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 置顶按钮
                if (canPinTask) {
                    ActionIcon(
                        icon = if (task.isPinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
                        color = extendedColors.deadlineSoon,
                        onClick = { scope.launch { offsetX.animateTo(0f); onPin() } }
                    )
                }
                
                // 编辑按钮
                ActionIcon(
                    icon = Icons.Default.Edit,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { scope.launch { offsetX.animateTo(0f); onEdit() } }
                )
                
                // 删除按钮
                ActionIcon(
                    icon = if (isDeleteConfirming) Icons.Default.DeleteForever else Icons.Default.Delete,
                    color = if (isDeleteConfirming) Color.White else extendedColors.deadlineUrgent,
                    containerColor = if (isDeleteConfirming) extendedColors.deadlineUrgent else extendedColors.deadlineUrgent.copy(alpha = 0.1f),
                    onClick = { 
                        if (isDeleteConfirming) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch { 
                                offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                isDismissed = true
                                delay(animationDuration.toLong())
                                onDelete() 
                            }
                        } else {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isDeleteConfirming = true
                        }
                    }
                )
            }
        }

        // 任务内容
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
}

@Composable
fun ActionIcon(
    icon: ImageVector, 
    color: Color, 
    containerColor: Color = color.copy(alpha = 0.1f),
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .background(containerColor, CircleShape)
            .size(48.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
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