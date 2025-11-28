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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskType
import com.litetask.app.ui.theme.Primary
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (Task) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(TaskType.WORK) }
    
    // 日期和时间状态 - 分别管理日期和时间
    val calendar = Calendar.getInstance()
    
    // 开始日期和时间
    var startDate by remember { mutableStateOf(calendar.timeInMillis) }
    var startHour by remember { mutableStateOf(calendar.get(Calendar.HOUR_OF_DAY)) }
    var startMinute by remember { mutableStateOf(calendar.get(Calendar.MINUTE)) }
    
    // 截止日期和时间（默认第二天同一时间）
    val deadlineCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    var deadlineDate by remember { mutableStateOf(deadlineCal.timeInMillis) }
    var deadlineHour by remember { mutableStateOf(deadlineCal.get(Calendar.HOUR_OF_DAY)) }
    var deadlineMinute by remember { mutableStateOf(deadlineCal.get(Calendar.MINUTE)) }
    
    var showAdvanced by remember { mutableStateOf(false) }
    var isPinned by remember { mutableStateOf(false) }

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
                                text = "新建任务",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F1F1F)
                            )
                            Text(
                                text = "填写任务详细信息",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF444746),
                                modifier = Modifier.padding(top = 4.dp)
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
                                contentDescription = "关闭",
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
                        label = { Text("任务标题 *") },
                        placeholder = { Text("例如：完成项目报告") },
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
                        label = { Text("任务描述（可选）") },
                        placeholder = { Text("添加更多详细信息...") },
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
                        text = "任务类型",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1F1F1F),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TaskType.values().take(4).forEach { type ->
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
                        text = "开始时间",
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
                            modifier = Modifier.weight(1f)
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
                        text = "截止时间",
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
                            label = "截止日期",
                            icon = Icons.Default.Event,
                            value = formatDateForDialog(deadlineDate),
                            onClick = { showDeadlineDatePicker = true },
                            modifier = Modifier.weight(1f),
                            isUrgent = deadlineMillis < System.currentTimeMillis() + 24 * 60 * 60 * 1000
                        )
                        DateTimePickerCard(
                            label = "截止时间",
                            icon = Icons.Default.Flag,
                            value = String.format("%02d:%02d", deadlineHour, deadlineMinute),
                            onClick = { showDeadlineTimePicker = true },
                            modifier = Modifier.weight(1f),
                            isUrgent = deadlineMillis < System.currentTimeMillis() + 24 * 60 * 60 * 1000
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
                                    "⚠️ 截止时间必须晚于开始时间"
                                } else if (durationDays > 0) {
                                    "预计时长: ${durationDays}天 ${remainingHours}小时"
                                } else {
                                    "预计时长: ${durationHours}小时"
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
                        Text(if (showAdvanced) "收起高级选项" else "展开高级选项")
                    }

                    // Advanced Options
                    AnimatedVisibility(
                        visible = showAdvanced,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { isPinned = !isPinned }
                                    .background(if (isPinned) Primary.copy(alpha = 0.1f) else Color.Transparent)
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = if (isPinned) Primary else Color(0xFF444746)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "置顶任务",
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF1F1F1F)
                                    )
                                    Text(
                                        "将此任务固定在列表顶部",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF747775)
                                    )
                                }
                                Switch(
                                    checked = isPinned,
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
                        Text("取消", fontWeight = FontWeight.Medium)
                    }
                    
                    Button(
                        onClick = {
                            if (title.isNotBlank() && deadlineMillis > startTimeMillis) {
                                onConfirm(
                                    Task(
                                        title = title,
                                        description = description.ifBlank { null },
                                        type = selectedType,
                                        startTime = startTimeMillis,
                                        deadline = deadlineMillis,
                                        isPinned = isPinned
                                    )
                                )
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
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("创建任务", fontWeight = FontWeight.Bold)
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
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUrgent) Color(0xFFFFEBEE) else Color(0xFFF8F9FA)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isUrgent) Color(0xFFF43F5E) else Primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF747775)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isUrgent) Color(0xFFF43F5E) else Color(0xFF1F1F1F)
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
