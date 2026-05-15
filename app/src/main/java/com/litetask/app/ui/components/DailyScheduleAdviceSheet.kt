package com.litetask.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyScheduleAdviceSheet(
    advice: DailyScheduleAdviceEntity?,
    isGenerating: Boolean,
    agentLogs: List<String>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
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
                    targetState = Triple(isGenerating, advice, agentLogs),
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(200))
                    },
                    contentKey = { (generating, _, _) -> generating }
                ) { (generating, currentAdvice, logs) ->
                    if (generating) {
                        AgentThinkingContent(
                            logs = logs,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp)
                        )
                    } else if (currentAdvice != null) {
                        AdviceList(currentAdvice)
                    } else {
                        EmptyAdviceView(onRefresh)
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
private fun AdviceList(advice: DailyScheduleAdviceEntity) {
    val shortTerm = try { JSONArray(advice.shortTermJson) } catch (e: Exception) { JSONArray() }
    val longTerm = try { JSONArray(advice.longTermJson) } catch (e: Exception) { JSONArray() }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
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
                        accentColor = MaterialTheme.colorScheme.primary
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
                        accentColor = MaterialTheme.colorScheme.secondary
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
                    text = "更新于 ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(advice.createdAt))}",
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

@Composable
private fun AdviceCard(
    title: String,
    type: String,
    content: String,
    info: String,
    icon: ImageVector,
    accentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    ) {
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
                        overflow = TextOverflow.Ellipsis
                    )
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
        }
    }
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
private fun EmptyAdviceView(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "尚未生成日程建议",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "AI 将根据您的任务和画像生成个性化建议",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRefresh,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("立即智能规划")
        }
    }
}
