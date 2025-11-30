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
import com.litetask.app.ui.theme.LiteTaskColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// --- 1. 颜色系统定义 (完全不透明色值) ---
private object TaskColors {
    // 主色 (用于文字、图标、进度条)
    @Composable
    fun Work() = LiteTaskColors.workTask()
    
    @Composable
    fun Life() = LiteTaskColors.lifeTask()
    
    @Composable
    fun Study() = LiteTaskColors.studyTask()
    
    @Composable
    fun Urgent() = LiteTaskColors.urgentTask()
    
    @Composable
    fun Health() = LiteTaskColors.healthTask()
    
    @Composable
    fun Dev() = LiteTaskColors.devTask()

    // 浅色背景 (用于置顶卡片背景 - 对应 Tailwind 的 xx-50 色阶，不透明)
    @Composable
    fun WorkSurface() = LiteTaskColors.workTaskSurface()
    
    @Composable
    fun LifeSurface() = LiteTaskColors.lifeTaskSurface()
    
    @Composable
    fun StudySurface() = LiteTaskColors.studyTaskSurface()
    
    @Composable
    fun UrgentSurface() = LiteTaskColors.urgentTaskSurface()
    
    @Composable
    fun HealthSurface() = LiteTaskColors.healthTaskSurface()
    
    @Composable
    fun DevSurface() = LiteTaskColors.devTaskSurface()

    @Composable
    fun getPrimary(type: TaskType): Color = when (type) {
        TaskType.WORK -> Work()
        TaskType.LIFE -> Life()
        TaskType.URGENT -> Urgent()
        TaskType.STUDY -> Study()
        TaskType.HEALTH -> Health()
        TaskType.DEV -> Dev()
    }

    @Composable
    fun getSurface(type: TaskType): Color = when (type) {
        TaskType.WORK -> WorkSurface()
        TaskType.LIFE -> LifeSurface()
        TaskType.URGENT -> UrgentSurface()
        TaskType.STUDY -> StudySurface()
        TaskType.HEALTH -> HealthSurface()
        TaskType.DEV -> DevSurface()
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
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF2F6FC))
            .padding(horizontal = 16.dp)
    ) {
        // 搜索框 (占位)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .height(48.dp)
                .shadow(elevation = 1.dp, shape = CircleShape)
                .background(Color.White, CircleShape)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.search_hint), color = Color.Gray, fontSize = 14.sp)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
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
                        // 简约的历史分割线
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color.LightGray.copy(alpha = 0.5f))
                            Text(
                                stringResource(R.string.archived),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color.LightGray.copy(alpha = 0.5f))
                        }
                    }
                    is TimelineItem.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                    is TimelineItem.PinnedHeader -> {
                        // 置顶任务头部，可以显示置顶任务数量等信息
                        // 当前版本暂不显示额外内容，但保留空处理以避免编译错误
                    }
                }
            }
        }
    }
}

/**
 * 核心卡片组件：完全复刻 HTML 原型
 */
@Composable
fun HtmlStyleTaskCard(
    composite: TaskDetailComposite,
    onClick: () -> Unit
) {
    val task = composite.task
    val subTasks = composite.subTasks

    val isDone = task.isDone
    val isPinned = task.isPinned

    // 获取颜色配置
    val primaryColor = if (isDone) Color.Gray else TaskColors.getPrimary(task.type)
    val surfaceColor = if (isDone) Color.White else TaskColors.getSurface(task.type)

    // --- 样式逻辑 (核心修改) ---
    // 1. 背景色：置顶任务使用浅色实心背景(不透明)，普通任务纯白
    val containerColor = if (isPinned && !isDone) surfaceColor else Color.White

    // 2. 边框：置顶任务有浅色边框，普通任务无
    val borderStroke = if (isPinned && !isDone) {
        androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.2f))
    } else null

    // 3. 阴影：置顶任务阴影稍重
    val elevation = if (isPinned && !isDone) 2.dp else 0.5.dp

    // 进度计算
    val totalSub = subTasks.size
    val completedSub = subTasks.count { it.isCompleted }
    val progress = if (totalSub > 0) completedSub.toFloat() / totalSub else 0f

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp), // MD3 大圆角
        color = containerColor,
        shadowElevation = elevation,
        border = borderStroke,
        modifier = Modifier.fillMaxWidth()
    ) {
        // 使用 IntrinsicSize.Min 确保左侧色条高度填满
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {

            // --- 左侧色条 (改进样式) ---
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)) // 右侧圆角
                    .background(primaryColor.copy(alpha = if (isDone) 0.3f else 0.8f)) // 调整透明度
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
                        color = if (isDone) Color.Gray else Color(0xFF1F1F1F),
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
                            tint = TaskColors.Life(),
                            modifier = Modifier.size(20.dp)
                        )
                    } else if (isPinned) {
                        // 置顶图标：带背景的旋转图钉 (复刻 HTML)
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.6f), CircleShape) // 微弱背景增加对比
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
                    val infoColor = Color(0xFF444746).copy(alpha = if (isDone) 0.5f else 0.8f)

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
                                color = if (isVeryUrgent) Color(0xFFFFE4E6) else Color(0xFFE0F2FE),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (timeLeft < 0) "已逾期" else "24h内",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = if (isVeryUrgent) Color(0xFFBE123C) else Color(0xFF0369A1)
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
                                .background(Color(0xFFF5F7FA), RoundedCornerShape(6.dp)) // 灰色底块
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .fillMaxWidth()
                        ) {
                            // 小圆点装饰
                            Box(modifier = Modifier.size(6.dp).background(Color.Gray.copy(alpha=0.5f), CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Next: ${nextAction.content}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
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
        // 优化灵敏度：将触发阈值从10f增加到30f，避免误触
        if (offsetX.value < -30f) {
            Row(
                modifier = Modifier
                    .width(actionWidth)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp), // 上下留白
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 只有未完成且未过期的任务才能置顶
                if (canPinTask) {
                    ActionIcon(
                        icon = if (task.isPinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
                        color = Color(0xFFEAB308),
                        onClick = { scope.launch { offsetX.animateTo(0f); onPin() } }
                    )
                }
                
                ActionIcon(
                    icon = Icons.Default.Edit,
                    color = Color(0xFF0B57D0),
                    onClick = { scope.launch { offsetX.animateTo(0f); onEdit() } }
                )
                ActionIcon(
                    icon = Icons.Default.Delete,
                    color = Color(0xFFF43F5E),
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
                        // 优化滑动释放逻辑：增加速度阈值，让用户更容易控制
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

private fun getTaskTypeName(type: TaskType): String {
    return when (type) {
        TaskType.WORK -> "工作"
        TaskType.LIFE -> "生活"
        TaskType.URGENT -> "紧急"
        TaskType.STUDY -> "学习"
        TaskType.HEALTH -> "健康"
        TaskType.DEV -> "开发"
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
    } else "无截止"

    return "$startStr - $endStr"
}