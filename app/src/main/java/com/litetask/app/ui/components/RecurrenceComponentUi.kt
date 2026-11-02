package com.litetask.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.litetask.app.data.model.RecurrenceEngine
import com.litetask.app.data.model.RecurrenceRuleData
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 周期重复规则展示卡片（详情页 / 新建编辑弹窗统一渲染）。
 */
@Composable
fun RecurrenceRuleCard(
    data: RecurrenceRuleData,
    onEdit: (() -> Unit)? = null,
    onTogglePause: (() -> Unit)? = null,
    onStopFuture: (() -> Unit)? = null,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = remember(data) { RecurrenceEngine.formatRuleSummary(data) }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (data.isPaused) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            } else {
                Color(0xFFF0FDFA)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (data.isPaused) MaterialTheme.colorScheme.outlineVariant else Color(0xFFCCFBF1),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = null,
                            tint = if (data.isPaused) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF0D9488),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "第${data.occurrenceIndex}期",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = buildString {
                            append("完成后自动生成下一期")
                            if (data.inheritSubTasks) append(" · 继承并重置子步骤")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑规则", modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "移除规则", modifier = Modifier.size(18.dp))
                }
            }

            if (onTogglePause != null || onStopFuture != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onTogglePause != null) {
                        OutlinedButton(
                            onClick = onTogglePause,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = if (data.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (data.isPaused) "恢复循环" else "暂停循环",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    if (onStopFuture != null) {
                        TextButton(
                            onClick = onStopFuture,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.StopCircle,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "仅停止未来重复",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 添加 / 编辑周期重复规则对话框（含未来 3 期发生时间实时预览）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddRecurrenceRuleDialog(
    initialData: RecurrenceRuleData? = null,
    anchorStartTimeMs: Long = System.currentTimeMillis(),
    anchorDeadlineMs: Long = anchorStartTimeMs + 3600_000L,
    onDismiss: () -> Unit,
    onConfirm: (RecurrenceRuleData) -> Unit
) {
    val defaultAnchorDay = remember(anchorStartTimeMs) {
        Instant.ofEpochMilli(anchorStartTimeMs).atZone(ZoneId.systemDefault()).dayOfMonth
    }
    val defaultAnchorDow = remember(anchorStartTimeMs) {
        Instant.ofEpochMilli(anchorStartTimeMs).atZone(ZoneId.systemDefault()).dayOfWeek.value
    }

    var freq by remember(initialData) {
        mutableStateOf(RecurrenceEngine.RecurrenceFreq.fromCode(initialData?.freq))
    }
    var interval by remember(initialData) { mutableStateOf((initialData?.interval ?: 1).coerceIn(1, 99)) }
    var daysOfWeek by remember(initialData) {
        mutableStateOf(
            initialData?.daysOfWeek?.takeIf { it.isNotEmpty() }?.toSet() ?: setOf(defaultAnchorDow)
        )
    }
    var dayOfMonth by remember(initialData) {
        mutableStateOf((initialData?.dayOfMonth ?: defaultAnchorDay).coerceIn(1, 31))
    }
    var endType by remember(initialData) {
        mutableStateOf(RecurrenceEngine.RecurrenceEndType.fromCode(initialData?.endType))
    }
    var remainingCount by remember(initialData) {
        mutableStateOf((initialData?.remainingCount ?: 5).coerceIn(1, 999))
    }
    var untilEpochMs by remember(initialData) {
        mutableStateOf(
            initialData?.untilEpochMs
                ?: LocalDate.now().plusMonths(1).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
    }
    var showUntilDatePicker by remember { mutableStateOf(false) }
    var inheritSubTasks by remember(initialData) { mutableStateOf(initialData?.inheritSubTasks ?: true) }

    val draftRule = remember(
        freq, interval, daysOfWeek, dayOfMonth, endType, remainingCount, untilEpochMs, inheritSubTasks, initialData
    ) {
        RecurrenceRuleData(
            freq = freq.code,
            interval = interval.coerceAtLeast(1),
            daysOfWeek = if (freq == RecurrenceEngine.RecurrenceFreq.WEEKLY) daysOfWeek.sorted() else emptyList(),
            dayOfMonth = if (freq == RecurrenceEngine.RecurrenceFreq.MONTHLY) dayOfMonth else null,
            endType = endType.code,
            remainingCount = if (endType == RecurrenceEngine.RecurrenceEndType.AFTER_COUNT) remainingCount else null,
            untilEpochMs = if (endType == RecurrenceEngine.RecurrenceEndType.UNTIL_DATE) untilEpochMs else null,
            inheritSubTasks = inheritSubTasks,
            occurrenceIndex = initialData?.occurrenceIndex ?: 1,
            isPaused = false,
            generatedNextTaskId = initialData?.generatedNextTaskId
        )
    }

    val previews = remember(draftRule, anchorStartTimeMs, anchorDeadlineMs) {
        RecurrenceEngine.previewNextOccurrences(
            taskStartTimeMs = anchorStartTimeMs,
            taskDeadlineMs = anchorDeadlineMs,
            rule = draftRule,
            count = 3,
            nowMs = minOf(System.currentTimeMillis(), anchorStartTimeMs)
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (initialData != null) "编辑重复规则" else "设置周期重复",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "重复频率",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RecurrenceEngine.RecurrenceFreq.entries.forEach { item ->
                            FilterChip(
                                selected = freq == item,
                                onClick = { freq = item },
                                label = { Text(item.label) }
                            )
                        }
                    }

                    // 步长微调（每天/每周/每月/每年/每 N 天）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val unitLabel = when (freq) {
                            RecurrenceEngine.RecurrenceFreq.DAILY,
                            RecurrenceEngine.RecurrenceFreq.INTERVAL_DAYS -> "天"
                            RecurrenceEngine.RecurrenceFreq.WEEKLY -> "周"
                            RecurrenceEngine.RecurrenceFreq.MONTHLY -> "个月"
                            RecurrenceEngine.RecurrenceFreq.YEARLY -> "年"
                        }
                        Text(
                            text = "间隔周期：每 $interval $unitLabel",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (interval > 1) interval-- },
                                enabled = interval > 1
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "减少")
                            }
                            Text(
                                text = interval.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = { if (interval < 99) interval++ },
                                enabled = interval < 99
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "增加")
                            }
                        }
                    }

                    if (freq == RecurrenceEngine.RecurrenceFreq.WEEKLY) {
                        Text(
                            text = "重复星期（支持多选）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (1..7).forEach { dow ->
                                val selected = dow in daysOfWeek
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        daysOfWeek = if (selected && daysOfWeek.size > 1) {
                                            daysOfWeek - dow
                                        } else {
                                            daysOfWeek + dow
                                        }
                                    },
                                    label = { Text(RecurrenceEngine.dayOfWeekShortLabel(dow)) }
                                )
                            }
                        }
                    } else if (freq == RecurrenceEngine.RecurrenceFreq.MONTHLY) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "每月锚定日：第 $dayOfMonth 日（小月自动贴合月末）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { if (dayOfMonth > 1) dayOfMonth-- },
                                    enabled = dayOfMonth > 1
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "减少")
                                }
                                Text(
                                    text = "${dayOfMonth}日",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { if (dayOfMonth < 31) dayOfMonth++ },
                                    enabled = dayOfMonth < 31
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "增加")
                                }
                            }
                        }
                    }

                    Text(
                        text = "结束条件",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RecurrenceEngine.RecurrenceEndType.entries.forEach { item ->
                            FilterChip(
                                selected = endType == item,
                                onClick = { endType = item },
                                label = { Text(item.label) }
                            )
                        }
                    }

                    if (endType == RecurrenceEngine.RecurrenceEndType.AFTER_COUNT) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "剩余执行总次数：$remainingCount 次",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { if (remainingCount > 1) remainingCount-- },
                                    enabled = remainingCount > 1
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "减少")
                                }
                                Text(remainingCount.toString(), fontWeight = FontWeight.Bold)
                                IconButton(onClick = { remainingCount++ }) {
                                    Icon(Icons.Default.Add, contentDescription = "增加")
                                }
                            }
                        }
                    } else if (endType == RecurrenceEngine.RecurrenceEndType.UNTIL_DATE) {
                        OutlinedButton(
                            onClick = { showUntilDatePicker = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val dateStr = Instant.ofEpochMilli(untilEpochMs)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                                .toString()
                            Text("截止日期：$dateStr（点击更改）")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "下期继承子步骤并重置状态",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "同时自动保留路线、链接与附件（跳过一次性取件码/凭证）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = inheritSubTasks,
                            onCheckedChange = { inheritSubTasks = it }
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "接下来 3 期时间预览（${RecurrenceEngine.formatRuleSummary(draftRule)}）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (previews.isEmpty()) {
                                Text(
                                    text = "按当前截止条件，本期已是最后一期",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                previews.forEachIndexed { idx, win ->
                                    Text(
                                        text = "第 ${draftRule.occurrenceIndex + idx + 1} 期：${RecurrenceEngine.formatShortDateTime(win.startTimeMillis)} ~ ${RecurrenceEngine.formatShortDateTime(win.deadlineMillis)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(draftRule) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("保存规则")
                    }
                }
            }
        }
    }

    if (showUntilDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = untilEpochMs)
        DatePickerDialog(
            onDismissRequest = { showUntilDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedUtcMs ->
                            val pickedDate = Instant.ofEpochMilli(selectedUtcMs)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                            untilEpochMs = pickedDate.atTime(23, 59, 59)
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                        }
                        showUntilDatePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showUntilDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * 周期任务完成生成下一期后的底部浮动操作条（支持查看与撤销）。
 */
@Composable
fun RecurrenceSpawnFloatingBanner(
    spawn: RecurrenceEngine.RecurrenceSpawnResult?,
    onViewNext: (Long) -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = spawn != null,
        enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut(),
        modifier = modifier
    ) {
        if (spawn != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.inversePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "已生成下期任务：${spawn.nextTask.title}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "下期开始 ${RecurrenceEngine.formatShortDateTime(spawn.nextTask.startTime)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = { onViewNext(spawn.nextTask.id) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("查看", color = MaterialTheme.colorScheme.inversePrimary)
                    }
                    TextButton(
                        onClick = onUndo,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("撤销", color = MaterialTheme.colorScheme.inversePrimary)
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
