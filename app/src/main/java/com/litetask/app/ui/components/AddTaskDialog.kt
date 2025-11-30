package com.litetask.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.litetask.app.R
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskType
import com.litetask.app.ui.theme.Primary
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    initialTask: Task? = null,
    onDismiss: () -> Unit,
    onConfirm: (Task) -> Unit
) {
    var title by remember { mutableStateOf(initialTask?.title ?: "") }
    var description by remember { mutableStateOf(initialTask?.description ?: "") }
    var selectedType by remember { mutableStateOf(initialTask?.type ?: TaskType.STUDY) }
    var isPinned by remember { mutableStateOf(initialTask?.isPinned ?: false) }
    
    // 时间初始化
    val initialStart = initialTask?.startTime ?: System.currentTimeMillis()
    val initialDead = initialTask?.deadline ?: (System.currentTimeMillis() + 24 * 60 * 60 * 1000)

    val startCal = Calendar.getInstance().apply { timeInMillis = initialStart }
    var startDate by remember { mutableStateOf(startCal.timeInMillis) }
    var startHour by remember { mutableStateOf(startCal.get(Calendar.HOUR_OF_DAY)) }
    var startMinute by remember { mutableStateOf(startCal.get(Calendar.MINUTE)) }

    val deadCal = Calendar.getInstance().apply { timeInMillis = initialDead }
    var deadlineDate by remember { mutableStateOf(deadCal.timeInMillis) }
    var deadlineHour by remember { mutableStateOf(deadCal.get(Calendar.HOUR_OF_DAY)) }
    var deadlineMinute by remember { mutableStateOf(deadCal.get(Calendar.MINUTE)) }

    // 对于已完成的任务，即使isPinned为true，我们也应该在UI上显示为false
    val isTaskDone = initialTask?.isDone ?: false
    var showAdvanced by remember { 
        mutableStateOf(
            if (isTaskDone) false else (initialTask?.isPinned == true)
        ) 
    }

    // Date/Time Picker 状态
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showDeadlineDatePicker by remember { mutableStateOf(false) }
    var showDeadlineTimePicker by remember { mutableStateOf(false) }

    // 计算最终时间戳
    val startTimeMillis = remember(startDate, startHour, startMinute) {
        Calendar.getInstance().apply {
            timeInMillis = startDate
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val deadlineMillis = remember(deadlineDate, deadlineHour, deadlineMinute) {
        Calendar.getInstance().apply {
            timeInMillis = deadlineDate
            set(Calendar.HOUR_OF_DAY, deadlineHour)
            set(Calendar.MINUTE, deadlineMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Primary.copy(alpha = 0.05f))
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (initialTask != null) stringResource(R.string.edit) + stringResource(R.string.task_type) else stringResource(R.string.create_task),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (initialTask != null) stringResource(R.string.confirm_modify) else stringResource(R.string.create_task),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF444746)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White, RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = Color(0xFF444746)
                            )
                        }
                    }
                }

                // Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    // Title Input
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.task_title) + " *") },
                        placeholder = { Text(stringResource(R.string.task_title_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Primary)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            focusedLabelColor = Primary
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Description Input
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.task_description)) },
                        placeholder = { Text(stringResource(R.string.task_description_placeholder)) },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        shape = RoundedCornerShape(16.dp),
                        maxLines = 4,
                        leadingIcon = {
                            Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF444746))
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Task Type Selection
                    Text(
                        text = stringResource(R.string.task_type),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1F1F1F),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TaskType.values().take(4).forEach { type -> // 只取前4个类型
                            val isSelected = selectedType == type
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedType = type },
                                label = { 
                                    Text(
                                        getTaskTypeName(type),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ) 
                                },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Date and Time Section
                    Text(
                        text = stringResource(R.string.start_time),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1F1F1F),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DateTimePickerCard(
                            label = "开始日期",
                            icon = Icons.Default.CalendarToday,
                            value = formatDateForDialog(startDate),
                            onClick = { showStartDatePicker = true },
                            modifier = Modifier.weight(1.6f)
                        )
                        DateTimePickerCard(
                            label = "开始时间",
                            icon = Icons.Default.AccessTime,
                            value = String.format("%02d:%02d", startHour, startMinute),
                            onClick = { showStartTimePicker = true },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.deadline),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1F1F1F),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 修改截止时间卡片变红逻辑：只在截止时间在未来24小时内才变红
                        val isDeadlineWithin24Hours = deadlineMillis > System.currentTimeMillis() && 
                                                    deadlineMillis < System.currentTimeMillis() + 24 * 60 * 60 * 1000
                        
                        DateTimePickerCard(
                            label = "截止日期",
                            icon = Icons.Default.Event,
                            value = formatDateForDialog(deadlineDate),
                            onClick = { showDeadlineDatePicker = true },
                            modifier = Modifier.weight(1.6f),
                            isUrgent = isDeadlineWithin24Hours
                        )
                        DateTimePickerCard(
                            label = "截止时间",
                            icon = Icons.Default.Flag,
                            value = String.format("%02d:%02d", deadlineHour, deadlineMinute),
                            onClick = { showDeadlineTimePicker = true },
                            modifier = Modifier.weight(1f),
                            isUrgent = isDeadlineWithin24Hours
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Duration Info
                    val durationHours = ((deadlineMillis - startTimeMillis) / (1000 * 60 * 60)).coerceAtLeast(0)
                    val durationDays = durationHours / 24
                    val remainingHours = durationHours % 24
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (deadlineMillis <= startTimeMillis) 
                                Color(0xFFFFEBEE) else Color(0xFFF0F4F8)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (deadlineMillis <= startTimeMillis) Icons.Default.Warning else Icons.Default.Timer,
                                contentDescription = null,
                                tint = if (deadlineMillis <= startTimeMillis) Color(0xFFF43F5E) else Primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (deadlineMillis <= startTimeMillis) {
                                    stringResource(R.string.warning_deadline_before_start)
                                } else if (durationDays > 0) {
                                    stringResource(R.string.duration_info, durationDays.toString(), remainingHours.toString())
                                } else {
                                    stringResource(R.string.duration_info_hours, durationHours.toString())
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (deadlineMillis <= startTimeMillis) Color(0xFFF43F5E) else Color(0xFF444746),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Advanced Options Toggle
                    TextButton(
                        onClick = { showAdvanced = !showAdvanced },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (showAdvanced) stringResource(R.string.collapse_advanced_options) else stringResource(R.string.expand_advanced_options))
                    }

                    // Advanced Options
                    AnimatedVisibility(
                        visible = showAdvanced,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // 检查任务是否已过期或已完成
                            val isTaskExpired = deadlineMillis < System.currentTimeMillis()
                            val isTaskDone = initialTask?.isDone ?: false
                            val pinEnabled = !isTaskExpired && !isTaskDone
                            
                            // 确保已完成的任务不能置顶
                            val displayIsPinned = if (isTaskDone) false else isPinned
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = pinEnabled) { isPinned = !isPinned }
                                    .background(if (displayIsPinned && pinEnabled) Primary.copy(alpha = 0.1f) else Color.Transparent)
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = if (displayIsPinned && pinEnabled) Primary else Color(0xFF444746)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.pin_task),
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF1F1F1F)
                                    )
                                    Text(
                                        stringResource(R.string.task_fixed_top),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF747775)
                                    )
                                    // 显示置顶不可用的原因
                                    if (isTaskExpired) {
                                        Text(
                                            stringResource(R.string.task_expired_cannot_pin),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFF43F5E)
                                        )
                                    } else if (isTaskDone) {
                                        Text(
                                            stringResource(R.string.task_cannot_pin_completed),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFF43F5E)
                                        )
                                    }
                                }
                                Switch(
                                    checked = displayIsPinned && pinEnabled,
                                    enabled = pinEnabled,
                                    onCheckedChange = { isPinned = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Primary,
                                        checkedTrackColor = Primary.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8F9FA))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Text(stringResource(R.string.cancel), fontWeight = FontWeight.Medium)
                    }
                    
                    Button(
                        onClick = {
                            if (title.isNotBlank() && deadlineMillis > startTimeMillis) {
                                // 对于已完成的任务，强制将isPinned设置为false
                                val isTaskDone = initialTask?.isDone ?: false
                                val effectiveIsPinned = if (isTaskDone) false else isPinned
                                
                                val newTask = Task(
                                    id = initialTask?.id ?: 0,
                                    title = title,
                                    description = description.ifBlank { null },
                                    type = selectedType,
                                    startTime = startTimeMillis,
                                    deadline = deadlineMillis,
                                    isPinned = effectiveIsPinned,
                                    isDone = initialTask?.isDone ?: false
                                )
                                onConfirm(newTask)
                            }
                        },
                        enabled = title.isNotBlank() && deadlineMillis > startTimeMillis,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            disabledContainerColor = Color(0xFFE0E0E0)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(if (initialTask != null) Icons.Default.Save else Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (initialTask != null) stringResource(R.string.confirm_modify) else stringResource(R.string.create_task), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Date Pickers
    if (showStartDatePicker) {
        MaterialDatePicker(
            initialDate = startDate,
            onDateSelected = { 
                startDate = it
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false }
        )
    }

    if (showDeadlineDatePicker) {
        MaterialDatePicker(
            initialDate = deadlineDate,
            onDateSelected = { 
                deadlineDate = it
                showDeadlineDatePicker = false
            },
            onDismiss = { showDeadlineDatePicker = false }
        )
    }

    // Time Pickers
    if (showStartTimePicker) {
        MaterialTimePicker(
            initialHour = startHour,
            initialMinute = startMinute,
            onTimeSelected = { hour, minute ->
                startHour = hour
                startMinute = minute
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }

    if (showDeadlineTimePicker) {
        MaterialTimePicker(
            initialHour = deadlineHour,
            initialMinute = deadlineMinute,
            onTimeSelected = { hour, minute ->
                deadlineHour = hour
                deadlineMinute = minute
                showDeadlineTimePicker = false
            },
            onDismiss = { showDeadlineTimePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialDatePicker(
    initialDate: Long,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { onDateSelected(it) }
            }) {
                Text("确定", color = Primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        colors = DatePickerDefaults.colors(
            containerColor = Color.White
        )
    ) {
        DatePicker(
            state = datePickerState,
            colors = DatePickerDefaults.colors(
                selectedDayContainerColor = Primary,
                todayContentColor = Primary,
                todayDateBorderColor = Primary
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialTimePicker(
    initialHour: Int,
    initialMinute: Int,
    onTimeSelected: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onTimeSelected(timePickerState.hour, timePickerState.minute)
            }) {
                Text("确定", color = Primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        text = {
            TimePicker(
                state = timePickerState,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Primary.copy(alpha = 0.1f),
                    selectorColor = Primary,
                    timeSelectorSelectedContainerColor = Primary,
                    timeSelectorSelectedContentColor = Color.White
                )
            )
        },
        containerColor = Color.White
    )
}


@Composable
fun DateTimePickerCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isUrgent: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(100.dp), // 固定高度确保所有卡片一致
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUrgent) Color(0xFFFFEBEE) else Color(0xFFF8F9FA)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isUrgent) Color(0xFFF43F5E) else Primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF747775),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isUrgent) Color(0xFFF43F5E) else Color(0xFF1F1F1F),
                    maxLines = 2,
                    lineHeight = 20.sp
                )
            }
        }
    }
}


private fun formatDateForDialog(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy年MM月dd日 E", Locale.CHINESE)
    return sdf.format(timestamp)
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

@Preview(showBackground = true)
@Composable
fun AddTaskDialogPreview() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.5f)
        ) {
            AddTaskDialog(
                onDismiss = {},
                onConfirm = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DateTimePickerCardPreview() {
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            DateTimePickerCard(
                label = "开始日期",
                icon = Icons.Default.CalendarToday,
                value = "2025年11月28日 周四",
                onClick = {}
            )
            Spacer(modifier = Modifier.height(8.dp))
            DateTimePickerCard(
                label = "截止时间",
                icon = Icons.Default.Flag,
                value = "23:59",
                onClick = {},
                isUrgent = true
            )
        }
    }
}
