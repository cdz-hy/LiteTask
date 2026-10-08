package com.litetask.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litetask.app.data.model.Task
import com.litetask.app.util.FocusMode
import com.litetask.app.util.FocusStatus
import com.litetask.app.util.FocusTimerState
import java.util.Locale

private val POMODORO_PRESETS = listOf(15, 25, 45, 60)

fun formatFocusClock(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val hours = safe / 3600L
    val mins = (safe % 3600L) / 60L
    val secs = safe % 60L
    return if (hours > 0L) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, mins, secs)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }
}

fun formatFocusDurationShort(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    if (safe < 60L) return "${safe}秒"
    val hours = safe / 3600L
    val mins = (safe % 3600L) / 60L
    return when {
        hours > 0L && mins > 0L -> "${hours}小时${mins}分钟"
        hours > 0L -> "${hours}小时"
        else -> "${mins}分钟"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusTimerSheet(
    timerState: FocusTimerState,
    targetTask: Task?,
    onDismiss: () -> Unit,
    onStart: (task: Task?, mode: FocusMode, plannedMinutes: Int) -> Unit,
    onRestart: (mode: FocusMode, plannedMinutes: Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinishAndSave: () -> Unit,
    onAbandon: () -> Unit,
    onResetToIdle: () -> Unit
) {
    var selectedMode by remember(timerState.status) {
        mutableStateOf(
            if (timerState.status == FocusStatus.IDLE) FocusMode.POMODORO else timerState.mode
        )
    }
    var selectedMinutes by remember { mutableIntStateOf(25) }

    val displayMode = if (timerState.status == FocusStatus.IDLE) selectedMode else timerState.mode
    val displayTitle = when (timerState.status) {
        FocusStatus.IDLE -> targetTask?.title ?: "自由专注"
        else -> timerState.taskTitle.ifBlank { "自由专注" }
    }

    val ringProgressTarget = when (timerState.status) {
        FocusStatus.IDLE -> if (displayMode == FocusMode.POMODORO) 1f else 0f
        FocusStatus.FINISHED -> 1f
        else -> timerState.progress
    }
    val animatedProgress by animateFloatAsState(
        targetValue = ringProgressTarget,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "focusProgress"
    )

    val ringColor by animateColorAsState(
        targetValue = when (timerState.status) {
            FocusStatus.FINISHED -> Color(0xFF146C2E)
            FocusStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        },
        label = "focusRingColor"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部任务标题栏
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
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "专注计时",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (timerState.status == FocusStatus.IDLE && targetTask != null && targetTask.totalFocusSeconds > 0L) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "已累计 ${formatFocusDurationShort(targetTask.totalFocusSeconds)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 仅空闲状态下允许切换模式与时长预设
            AnimatedVisibility(visible = timerState.status == FocusStatus.IDLE) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedMode == FocusMode.POMODORO,
                            onClick = { selectedMode = FocusMode.POMODORO },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("番茄钟")
                        }
                        SegmentedButton(
                            selected = selectedMode == FocusMode.STOPWATCH,
                            onClick = { selectedMode = FocusMode.STOPWATCH },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("正向计时")
                        }
                    }

                    if (selectedMode == FocusMode.POMODORO) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                        ) {
                            POMODORO_PRESETS.forEach { minutes ->
                                FilterChip(
                                    selected = selectedMinutes == minutes,
                                    onClick = { selectedMinutes = minutes },
                                    label = { Text("${minutes}分钟") }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // 中央圆环表盘
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(220.dp)) {
                    val strokeWidth = 12.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                    val arcSize = Size(diameter, diameter)

                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val mainTimeText = when (timerState.status) {
                        FocusStatus.IDLE -> if (displayMode == FocusMode.POMODORO) {
                            formatFocusClock(selectedMinutes * 60L)
                        } else {
                            "00:00"
                        }
                        FocusStatus.FINISHED -> "00:00"
                        else -> if (displayMode == FocusMode.POMODORO) {
                            formatFocusClock(timerState.remainingSeconds)
                        } else {
                            formatFocusClock(timerState.elapsedSeconds)
                        }
                    }

                    val subStatusText = when (timerState.status) {
                        FocusStatus.IDLE -> if (displayMode == FocusMode.POMODORO) "准备开始" else "自由记录专注"
                        FocusStatus.RUNNING -> if (displayMode == FocusMode.POMODORO) {
                            "已专注 ${formatFocusClock(timerState.elapsedSeconds)}"
                        } else {
                            "专注计时中"
                        }
                        FocusStatus.PAUSED -> "已暂停 · ${formatFocusClock(timerState.elapsedSeconds)}"
                        FocusStatus.FINISHED -> "专注完成！+${formatFocusDurationShort(timerState.plannedSeconds)}"
                    }

                    Text(
                        text = mainTimeText,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = subStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (timerState.status == FocusStatus.FINISHED) {
                            Color(0xFF146C2E)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 底部控制按钮组
            when (timerState.status) {
                FocusStatus.IDLE -> {
                    Button(
                        onClick = { onStart(targetTask, selectedMode, selectedMinutes) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("开始专注", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }

                FocusStatus.RUNNING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onPause,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Pause, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("暂停")
                        }
                        OutlinedButton(
                            onClick = onFinishAndSave,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("完成并保存")
                        }
                    }
                }

                FocusStatus.PAUSED -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onResume,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("继续")
                            }
                            FilledTonalButton(
                                onClick = onFinishAndSave,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("完成保存")
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = onAbandon,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("放弃本次专注")
                        }
                    }
                }

                FocusStatus.FINISHED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onResetToIdle()
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("完成收起")
                        }
                        Button(
                            onClick = {
                                val nextMinutes = (timerState.plannedSeconds / 60L).toInt().coerceAtLeast(15)
                                if (targetTask != null) {
                                    onStart(targetTask, FocusMode.POMODORO, nextMinutes)
                                } else {
                                    onRestart(FocusMode.POMODORO, nextMinutes)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("再来一轮")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 首页顶部悬浮专注状态栏，实时显示进行中/已暂停/刚完成的专注计时。
 */
@Composable
fun ActiveFocusBanner(
    timerState: FocusTimerState,
    onOpenSheet: () -> Unit,
    onTogglePause: () -> Unit,
    onDismissFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "focusPulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "focusDotAlpha"
    )

    val containerColor = when (timerState.status) {
        FocusStatus.FINISHED -> Color(0xFFE7F5EC)
        FocusStatus.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when (timerState.status) {
        FocusStatus.FINISHED -> Color(0xFF146C2E)
        FocusStatus.PAUSED -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        onClick = onOpenSheet,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.16f)),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when (timerState.status) {
                            FocusStatus.RUNNING -> contentColor.copy(alpha = dotAlpha)
                            else -> contentColor.copy(alpha = 0.7f)
                        }
                    )
            )
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timerState.taskTitle.ifBlank { "自由专注" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when (timerState.status) {
                        FocusStatus.FINISHED -> "番茄钟已完成！点击查看"
                        FocusStatus.PAUSED -> "已暂停 · 点击继续"
                        else -> if (timerState.mode == FocusMode.POMODORO) "番茄专注中" else "正向计时中"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }

            if (timerState.status != FocusStatus.FINISHED) {
                val timeStr = if (timerState.mode == FocusMode.POMODORO) {
                    formatFocusClock(timerState.remainingSeconds)
                } else {
                    formatFocusClock(timerState.elapsedSeconds)
                }
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onTogglePause,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (timerState.status == FocusStatus.RUNNING) {
                            Icons.Rounded.Pause
                        } else {
                            Icons.Rounded.PlayArrow
                        },
                        contentDescription = if (timerState.status == FocusStatus.RUNNING) "暂停" else "继续",
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onDismissFinished,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
