package com.litetask.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litetask.app.data.model.DailyScheduleAdviceEntity
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskComponent
import com.litetask.app.data.model.TaskDetailComposite
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyScheduleAdviceSheet(
    advice: DailyScheduleAdviceEntity?,
    isGenerating: Boolean,
    agentLogs: List<String>,
    errorMessage: String? = null,
    allTasks: List<TaskDetailComposite> = emptyList(),
    hasAmapKey: Boolean = false,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onOpenTaskDetail: (Task) -> Unit = {},
    onStartFocusTask: (Task) -> Unit = {},
    onAiDecomposeTask: (Task) -> Unit = {},
    onAdjustTaskTime: (Task, Long?, Long?) -> Unit = { _, _, _ -> },
    onAddTaskRoute: (Task, String?) -> Unit = { _, _ -> }
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // 顶部栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "日程规划建议",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!isGenerating && advice != null) {
                            Text(
                                text = "根据您的任务和画像实时生成",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!isGenerating && advice != null) {
                    FilledTonalIconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                AnimatedContent(
                    targetState = Triple(isGenerating, advice, errorMessage),
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(200))
                    },
                    contentKey = { (generating, current, err) -> "$generating-${current?.id}-${err != null}" }
                ) { (generating, currentAdvice, err) ->
                    if (generating) {
                        AgentThinkingContent(
                            logs = agentLogs,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp)
                        )
                    } else if (currentAdvice != null) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (!err.isNullOrBlank()) {
                                RefreshErrorBanner(
                                    errorMessage = err,
                                    onRetry = onRefresh,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }
                            AdviceList(
                                advice = currentAdvice,
                                allTasks = allTasks,
                                hasAmapKey = hasAmapKey,
                                onOpenTaskDetail = onOpenTaskDetail,
                                onStartFocusTask = onStartFocusTask,
                                onAiDecomposeTask = onAiDecomposeTask,
                                onAdjustTaskTime = onAdjustTaskTime,
                                onAddTaskRoute = onAddTaskRoute
                            )
                        }
                    } else {
                        EmptyAdviceView(
                            onRefresh = onRefresh,
                            errorMessage = err
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentThinkingContent(
    logs: List<String>,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ThinkingPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ShimmerAlpha"
    )

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 动态 AI 图标
        Box(
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                },
            contentAlignment = Alignment.Center
        ) {
            // 外层光晕
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = shimmerAlpha * 0.3f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                            )
                        ),
                        shape = CircleShape
                    )
            )
            // 圆环
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = Color.Transparent,
                        shape = CircleShape
                    )
                    .background(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                        ),
                        shape = CircleShape
                    )
            )
            CircularProgressIndicator(
                modifier = Modifier.size(52.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 当前状态
        val latestLog = logs.lastOrNull { it.isNotBlank() } ?: "正在初始化..."
        Text(
            text = latestLog,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "AI 规划顾问正在深度分析中",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 思维日志流
        val listState = rememberLazyListState()

        LaunchedEffect(logs.size) {
            if (logs.isNotEmpty()) {
                listState.animateScrollToItem(logs.size - 1)
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
        ) {
            if (logs.isEmpty()) {
                // 空状态 - 骨架屏效果
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(4) { index ->
                        val width = when (index) {
                            0 -> 0.8f; 1 -> 0.6f; 2 -> 0.9f; else -> 0.5f
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(width)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = shimmerAlpha * 0.15f
                                    )
                                )
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(logs) { index, log ->
                        val isLatest = index == logs.size - 1
                        val alpha = if (isLatest) 1f else {
                            (0.6f - (logs.size - 1 - index) * 0.12f).coerceIn(0.15f, 0.6f)
                        }

                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 时间线圆点
                            Box(
                                modifier = Modifier
                                    .padding(top = 5.dp)
                                    .size(6.dp)
                                    .background(
                                        color = if (isLatest) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdviceList(
    advice: DailyScheduleAdviceEntity,
    allTasks: List<TaskDetailComposite>,
    hasAmapKey: Boolean,
    onOpenTaskDetail: (Task) -> Unit,
    onStartFocusTask: (Task) -> Unit,
    onAiDecomposeTask: (Task) -> Unit,
    onAdjustTaskTime: (Task, Long?, Long?) -> Unit,
    onAddTaskRoute: (Task, String?) -> Unit
) {
    val shortTerm = try { JSONArray(advice.shortTermJson) } catch (e: Exception) { JSONArray() }
    val longTerm = try { JSONArray(advice.longTermJson) } catch (e: Exception) { JSONArray() }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        if (shortTerm.length() == 0 && longTerm.length() == 0) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DoneAll,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "当前暂无待办或逾期任务",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "暂时没有需要调度的事项，可添加新任务或稍后刷新规划",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (shortTerm.length() > 0) {
            item {
                SectionHeader(
                    title = "短期执行建议",
                    icon = Icons.Rounded.Today,
                    color = MaterialTheme.colorScheme.primary,
                    count = shortTerm.length()
                )
            }
            items(shortTerm.length()) { index ->
                val item = shortTerm.getJSONObject(index)
                val matched = remember(item.toString(), allTasks) {
                    matchTaskComposite(item, "task_title", allTasks)
                }
                val relatedComposites = remember(item.toString(), allTasks, matched) {
                    matchRelatedComposites(item, allTasks, excludeTaskId = matched?.task?.id)
                }
                val suggestedStart = remember(item.toString()) {
                    parseSuggestedTime(item.optString("suggested_start", ""))
                }
                val suggestedDeadline = remember(item.toString()) {
                    parseSuggestedTime(item.optString("suggested_deadline", ""))
                }
                val suggestedTimeLabel = remember(suggestedStart, suggestedDeadline) {
                    formatSuggestedWindow(suggestedStart, suggestedDeadline)
                }
                val suggestedDestination = item.optString("suggested_destination", "")
                    .takeIf { it.isNotBlank() && it != "null" }
                val suggestedFocusMinutes = item.optInt("suggested_focus_minutes", 0)
                    .takeIf { it > 0 }
                val actionType = item.optString("action_type", "").uppercase()
                val animDelay = index * 80
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(
                        animationSpec = tween(400, delayMillis = animDelay)
                    ) + slideInVertically(
                        animationSpec = tween(400, delayMillis = animDelay),
                        initialOffsetY = { it / 4 }
                    )
                ) {
                    AdviceCard(
                        title = item.optString("task_title"),
                        type = item.optString("advice_type"),
                        content = item.optString("content"),
                        info = item.optString("info"),
                        icon = getAdviceTypeIcon(item.optString("advice_type")),
                        accentColor = MaterialTheme.colorScheme.primary,
                        matchedComposite = matched,
                        relatedComposites = relatedComposites,
                        actionType = actionType,
                        hasSuggestedTime = suggestedStart != null || suggestedDeadline != null,
                        suggestedTimeLabel = suggestedTimeLabel,
                        suggestedDestination = suggestedDestination,
                        suggestedFocusMinutes = suggestedFocusMinutes,
                        hasAmapKey = hasAmapKey,
                        onOpenDetail = matched?.let { { onOpenTaskDetail(it.task) } },
                        onOpenRelatedTask = onOpenTaskDetail,
                        onStartFocus = matched?.let { { onStartFocusTask(it.task) } },
                        onAiDecompose = matched?.let { { onAiDecomposeTask(it.task) } },
                        onAdjustTime = matched?.let {
                            { onAdjustTaskTime(it.task, suggestedStart, suggestedDeadline) }
                        },
                        onAddRoute = matched?.let {
                            { onAddTaskRoute(it.task, suggestedDestination) }
                        }
                    )
                }
            }
        }

        if (shortTerm.length() > 0 && longTerm.length() > 0) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        if (longTerm.length() > 0) {
            item {
                SectionHeader(
                    title = "长期规划策略",
                    icon = Icons.AutoMirrored.Rounded.EventNote,
                    color = MaterialTheme.colorScheme.secondary,
                    count = longTerm.length()
                )
            }
            items(longTerm.length()) { index ->
                val item = longTerm.getJSONObject(index)
                val matched = remember(item.toString(), allTasks) {
                    matchTaskComposite(item, "topic", allTasks)
                }
                val relatedComposites = remember(item.toString(), allTasks, matched) {
                    matchRelatedComposites(item, allTasks, excludeTaskId = matched?.task?.id)
                }
                val animDelay = (shortTerm.length() + index) * 80
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(
                        animationSpec = tween(400, delayMillis = animDelay)
                    ) + slideInVertically(
                        animationSpec = tween(400, delayMillis = animDelay),
                        initialOffsetY = { it / 4 }
                    )
                ) {
                    AdviceCard(
                        title = item.optString("topic"),
                        type = item.optString("advice_type"),
                        content = item.optString("content"),
                        info = if (item.optString("urgency").isNotBlank()) {
                            "优先级: ${item.optString("urgency")}"
                        } else "",
                        icon = getAdviceTypeIcon(item.optString("advice_type")),
                        accentColor = MaterialTheme.colorScheme.secondary,
                        matchedComposite = matched,
                        relatedComposites = relatedComposites,
                        hasAmapKey = hasAmapKey,
                        onOpenDetail = matched?.let { { onOpenTaskDetail(it.task) } },
                        onOpenRelatedTask = onOpenTaskDetail,
                        onStartFocus = matched?.let { { onStartFocusTask(it.task) } },
                        onAiDecompose = matched?.let { { onAiDecomposeTask(it.task) } },
                        onAdjustTime = matched?.let { { onAdjustTaskTime(it.task, null, null) } },
                        onAddRoute = matched?.let { { onAddTaskRoute(it.task, null) } }
                    )
                }
            }
        }

        // 底部更新时间与留白
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Schedule,
                    null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "更新于 ${formatAdviceShortTime(advice.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    color: Color,
    count: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .padding(bottom = 4.dp)
    ) {
        Surface(
            color = color.copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Surface(
            color = color.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "${count}条",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdviceCard(
    title: String,
    type: String,
    content: String,
    info: String,
    icon: ImageVector,
    accentColor: Color,
    matchedComposite: TaskDetailComposite? = null,
    relatedComposites: List<TaskDetailComposite> = emptyList(),
    actionType: String = "",
    hasSuggestedTime: Boolean = false,
    suggestedTimeLabel: String? = null,
    suggestedDestination: String? = null,
    suggestedFocusMinutes: Int? = null,
    hasAmapKey: Boolean = false,
    onOpenDetail: (() -> Unit)? = null,
    onOpenRelatedTask: ((Task) -> Unit)? = null,
    onStartFocus: (() -> Unit)? = null,
    onAiDecompose: (() -> Unit)? = null,
    onAdjustTime: (() -> Unit)? = null,
    onAddRoute: (() -> Unit)? = null
) {
    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
    val cardBorder = BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    )
    val cardContent: @Composable ColumnScope.() -> Unit = {
        Column(modifier = Modifier.padding(20.dp)) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                icon,
                                null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (matchedComposite != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "查看任务详情",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = accentColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = type,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 内容
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )

            // 补充信息
            if (info.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.06f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        null,
                        modifier = Modifier.size(15.dp),
                        tint = accentColor.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = info,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 建议时段预览条
            if (!suggestedTimeLabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = suggestedTimeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 跨任务统筹关联胶囊（当 related_task_ids 包含关联任务时展示）
            if (relatedComposites.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    relatedComposites.forEach { related ->
                        val suffix = if (related.task.isDone) " (已完成)" else ""
                        AdviceQuickActionChip(
                            label = "${related.task.title}$suffix",
                            icon = if (related.task.isDone) Icons.Rounded.CheckCircle else Icons.Rounded.ChevronRight,
                            emphasized = false,
                            accentColor = accentColor,
                            onClick = { onOpenRelatedTask?.invoke(related.task) }
                        )
                    }
                }
            }

            // 关联单任务状态：已完成则清晰标识，未完成且非 NONE 则提供快捷闭环操作
            if (matchedComposite != null && matchedComposite.task.isDone) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "关联任务已完成",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (matchedComposite != null && !matchedComposite.task.isDone && actionType != "NONE") {
                val hasRouteComponent = matchedComposite.components.any {
                    it.toTaskComponent() is TaskComponent.AMapComponent
                }
                val isRouteRelevant = hasAmapKey && !hasRouteComponent && (
                    actionType == "ROUTE" ||
                        !suggestedDestination.isNullOrBlank() ||
                        type.contains("出行") || type.contains("路线") || type.contains("交通") ||
                        content.contains("前往") || content.contains("出发")
                    )

                Spacer(modifier = Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onStartFocus != null) {
                        val focusLabel = if (suggestedFocusMinutes != null) {
                            "开始专注 ${suggestedFocusMinutes}分"
                        } else {
                            "开始专注"
                        }
                        AdviceQuickActionChip(
                            label = focusLabel,
                            icon = Icons.Rounded.Timer,
                            emphasized = actionType == "FOCUS",
                            accentColor = accentColor,
                            onClick = onStartFocus
                        )
                    }
                    if (onAiDecompose != null) {
                        AdviceQuickActionChip(
                            label = "AI 拆解",
                            icon = Icons.Rounded.AutoAwesome,
                            emphasized = actionType == "DECOMPOSE" || matchedComposite.subTasks.isEmpty(),
                            accentColor = accentColor,
                            onClick = onAiDecompose
                        )
                    }
                    if (onAdjustTime != null) {
                        AdviceQuickActionChip(
                            label = if (hasSuggestedTime) "应用建议时间" else "调整时间",
                            icon = Icons.Rounded.Schedule,
                            emphasized = actionType == "RESCHEDULE" || hasSuggestedTime,
                            accentColor = accentColor,
                            onClick = onAdjustTime
                        )
                    }
                    if (isRouteRelevant && onAddRoute != null) {
                        AdviceQuickActionChip(
                            label = "添加路线",
                            icon = Icons.Rounded.Directions,
                            emphasized = actionType == "ROUTE" || !suggestedDestination.isNullOrBlank(),
                            accentColor = accentColor,
                            onClick = onAddRoute
                        )
                    }
                }
            }
        }
    }

    if (matchedComposite != null && onOpenDetail != null) {
        Card(
            onClick = onOpenDetail,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = cardColors,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = cardBorder,
            content = cardContent
        )
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = cardColors,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = cardBorder,
            content = cardContent
        )
    }
}

@Composable
private fun AdviceQuickActionChip(
    label: String,
    icon: ImageVector,
    emphasized: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (emphasized) accentColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = if (emphasized) accentColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (emphasized) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
                color = if (emphasized) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val SHEET_SHORT_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)

private val SUGGESTED_TIME_PARSERS: List<DateTimeFormatter> = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA),
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.CHINA),
    DateTimeFormatter.ISO_LOCAL_DATE_TIME
)

private fun formatAdviceShortTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(SHEET_SHORT_FMT)
}

private fun formatSuggestedWindow(start: Long?, deadline: Long?): String? {
    return when {
        start != null && deadline != null -> "建议时段: ${formatAdviceShortTime(start)} ~ ${formatAdviceShortTime(deadline)}"
        start != null -> "建议开始: ${formatAdviceShortTime(start)}"
        deadline != null -> "建议截止: ${formatAdviceShortTime(deadline)}"
        else -> null
    }
}

private fun parseRelatedTaskIds(item: JSONObject): List<Long> {
    val arr = item.optJSONArray("related_task_ids") ?: return emptyList()
    val ids = mutableListOf<Long>()
    for (i in 0 until arr.length()) {
        val id = arr.optLong(i, -1L)
        if (id > 0L && !ids.contains(id)) ids.add(id)
    }
    return ids
}

private fun matchRelatedComposites(
    item: JSONObject,
    allTasks: List<TaskDetailComposite>,
    excludeTaskId: Long?
): List<TaskDetailComposite> {
    val ids = parseRelatedTaskIds(item)
    if (ids.isEmpty()) return emptyList()
    val byId = allTasks.associateBy { it.task.id }
    return ids.mapNotNull { id ->
        if (id == excludeTaskId) null else byId[id]
    }
}

private fun matchTaskComposite(
    item: JSONObject,
    titleField: String,
    allTasks: List<TaskDetailComposite>
): TaskDetailComposite? {
    val taskId = item.optLong("task_id", -1L)
    if (taskId > 0) {
        allTasks.find { it.task.id == taskId }?.let { return it }
    }
    // 当项明确声明 task_id 为 null 且提供了多任务 related_task_ids 时，避免模糊匹配误绑定到单个任务
    if (item.isNull("task_id") && parseRelatedTaskIds(item).isNotEmpty()) {
        return null
    }
    val rawTitle = item.optString(titleField, "").trim()
    if (rawTitle.isBlank()) return null
    return allTasks.find { it.task.title.equals(rawTitle, ignoreCase = true) }
        ?: allTasks.firstOrNull {
            it.task.title.isNotBlank() && (
                rawTitle.contains(it.task.title, ignoreCase = true) ||
                    it.task.title.contains(rawTitle, ignoreCase = true)
                )
        }
}

private fun parseSuggestedTime(raw: String?): Long? {
    if (raw.isNullOrBlank() || raw.equals("null", ignoreCase = true)) return null
    val trimmed = raw.trim()
    for (formatter in SUGGESTED_TIME_PARSERS) {
        try {
            val ldt = LocalDateTime.parse(trimmed, formatter)
            return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) {
        }
    }
    return null
}

private fun getAdviceTypeIcon(type: String): ImageVector {
    return when {
        type.contains("出行") || type.contains("路线") || type.contains("交通") -> Icons.Rounded.Directions
        type.contains("执行") || type.contains("建议") -> Icons.Rounded.TaskAlt
        type.contains("统筹") || type.contains("安排") -> Icons.Rounded.Schedule
        type.contains("风险") || type.contains("预警") -> Icons.Rounded.Warning
        type.contains("规划") || type.contains("长期") -> Icons.AutoMirrored.Rounded.TrendingUp
        type.contains("习惯") -> Icons.Rounded.Loop
        type.contains("天气") -> Icons.Rounded.Cloud
        else -> Icons.Rounded.Lightbulb
    }
}

@Composable
private fun RefreshErrorBanner(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "刷新失败，已保留上次建议：$errorMessage",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("重试", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyAdviceView(
    onRefresh: () -> Unit,
    errorMessage: String? = null
) {
    val isError = !errorMessage.isNullOrBlank()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.AutoAwesome,
                    null,
                    modifier = Modifier.size(40.dp),
                    tint = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = if (isError) "日程建议生成失败" else "尚未生成日程建议",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage ?: "AI 将根据您的任务和画像生成个性化建议",
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRefresh,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(
                if (isError) Icons.Rounded.Refresh else Icons.Rounded.AutoAwesome,
                null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isError) "重新生成" else "立即智能规划")
        }
    }
}
