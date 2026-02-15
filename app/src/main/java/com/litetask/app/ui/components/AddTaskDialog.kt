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
import com.litetask.app.data.model.Reminder
import com.litetask.app.data.model.ReminderConfig
import com.litetask.app.data.model.ReminderType
import com.litetask.app.data.model.ReminderTimeUnit
import com.litetask.app.data.model.ReminderBaseTime
import com.litetask.app.ui.theme.LocalExtendedColors
import com.litetask.app.data.model.Category
import com.litetask.app.ui.util.ColorUtils
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    initialTask: Task? = null,
    initialReminders: List<Reminder> = emptyList(),
    initialComponents: List<com.litetask.app.data.model.TaskComponent> = emptyList(),
    availableCategories: List<Category> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (Task) -> Unit,
    onConfirmWithReminders: ((Task, List<ReminderConfig>) -> Unit)? = null,
    onConfirmWithComponents: ((Task, List<ReminderConfig>, List<com.litetask.app.data.model.TaskComponent>) -> Unit)? = null,
    amapKey: String? = null,
    onGeocode: (suspend (String) -> com.litetask.app.data.model.AMapRouteData?)? = null,
    onSearchLocations: (suspend (String) -> List<com.litetask.app.data.model.AMapRouteData>)? = null,
    onGetWeather: (suspend (String) -> Pair<String, String>?)? = null
) {
    val extendedColors = LocalExtendedColors.current
    var title by remember { mutableStateOf(initialTask?.title ?: "") }
    var description by remember { mutableStateOf(initialTask?.description ?: "") }
    
    // 初始化选中的分类ID
    var selectedCategoryId by remember { 
        mutableStateOf(
            initialTask?.categoryId ?: 
            availableCategories.find { it.isDefault }?.id ?: 
            availableCategories.firstOrNull()?.id ?: 
            1L 
        ) 
    }
    var isPinned by remember { mutableStateOf(initialTask?.isPinned ?: false) }
    
    // 组件状态
    var components by remember { mutableStateOf(initialComponents) }

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

    // 提醒设置 - 从已有提醒转换为ReminderConfig
    var selectedReminders by remember { 
        mutableStateOf(
            convertRemindersToConfigs(initialReminders, initialStart, initialDead)
        ) 
    }

    // 对于已完成的任务，即使isPinned为true，我们也应该在UI上显示为false
    val isTaskDone = initialTask?.isDone ?: false
    var showAdvanced by remember { 
        mutableStateOf(
            if (isTaskDone) false else (initialTask?.isPinned == true || initialReminders.isNotEmpty() || initialComponents.isNotEmpty())
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
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
                                color = extendedColors.textSecondary
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .background(extendedColors.cardBackground, RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = extendedColors.textSecondary
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
                            Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
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
                            Icon(Icons.Default.Description, contentDescription = null, tint = extendedColors.textSecondary)
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Category Selection
                    Text(
                        text = stringResource(R.string.task_type),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = extendedColors.textPrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableCategories) { category ->
                            val isSelected = selectedCategoryId == category.id
                            val categoryColor = ColorUtils.parseColor(category.colorHex)
                            val contentColor = ColorUtils.getSurfaceColor(categoryColor)
                            
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategoryId = category.id },
                                label = { 
                                    Text(
                                        category.name,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ) 
                                },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = categoryColor,
                                    selectedLabelColor = contentColor,
                                    selectedLeadingIconColor = contentColor,
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    labelColor = extendedColors.textPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    selected = isSelected,
                                    enabled = true,
                                    borderColor = if (isSelected) Color.Transparent else extendedColors.divider,
                                    borderWidth = 1.dp
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
                        color = extendedColors.textPrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DateTimePickerCard(
                            label = stringResource(R.string.start_date),
                            icon = Icons.Default.CalendarToday,
                            value = formatDateForDialog(startDate),
                            onClick = { showStartDatePicker = true },
                            modifier = Modifier.weight(1.6f)
                        )
                        DateTimePickerCard(
                            label = stringResource(R.string.start_time_label),
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
                        color = extendedColors.textPrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val isDeadlineWithin24Hours = deadlineMillis > System.currentTimeMillis() && 
                                                    deadlineMillis < System.currentTimeMillis() + 24 * 60 * 60 * 1000
                        
                        DateTimePickerCard(
                            label = stringResource(R.string.deadline_date),
                            icon = Icons.Default.Event,
                            value = formatDateForDialog(deadlineDate),
                            onClick = { showDeadlineDatePicker = true },
                            modifier = Modifier.weight(1.6f),
                            isUrgent = isDeadlineWithin24Hours
                        )
                        DateTimePickerCard(
                            label = stringResource(R.string.deadline_time),
                            icon = Icons.Default.Flag,
                            value = String.format("%02d:%02d", deadlineHour, deadlineMinute),
                            onClick = { showDeadlineTimePicker = true },
                            modifier = Modifier.weight(1f),
                            isUrgent = isDeadlineWithin24Hours
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Duration Info
                    val durationHours = ((deadlineMillis - startTimeMillis) / (1000.0 * 60 * 60)).coerceAtLeast(0.0)
                    val durationDays = (durationHours / 24).toInt()
                    val remainingHours = durationHours % 24
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (deadlineMillis <= startTimeMillis) 
                                extendedColors.deadlineUrgentSurface else MaterialTheme.colorScheme.surfaceVariant
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
                                tint = if (deadlineMillis <= startTimeMillis) extendedColors.deadlineUrgent else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (deadlineMillis <= startTimeMillis) {
                                    stringResource(R.string.warning_deadline_before_start)
                                } else if (durationDays > 0) {
                                    stringResource(R.string.duration_info, durationDays.toString(), String.format("%.1f", remainingHours))
                                } else {
                                    stringResource(R.string.duration_info_hours, String.format("%.1f", durationHours))
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (deadlineMillis <= startTimeMillis) extendedColors.deadlineUrgent else extendedColors.textSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 组件列表区域 (显示在折叠选项之前，方便查看)
                    if (components.isNotEmpty()) {
                        Text(
                            text = "任务组件",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = extendedColors.textPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                            
                        // 显示已添加的组件
                            TaskComponentList(
                                components = components,
                                onRemove = { component ->
                                    components = components.filter { it != component }
                                },
                                amapKey = amapKey,
                                onGetWeather = onGetWeather
                            )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }

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
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // 组件添加栏
                            Text(
                                text = "添加扩展组件",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1F1F1F),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            TaskComponentAddBar(
                                onAddAMap = { data ->
                                    val newId = -(System.currentTimeMillis()) // 临时 ID，负数表示未保存
                                    val component = com.litetask.app.data.model.TaskComponent.AMapComponent(
                                        id = newId,
                                        taskId = initialTask?.id ?: 0,
                                        data = data
                                    )
                                    components = components + component
                                },
                                onAddFile = { data ->
                                    val newId = -(System.currentTimeMillis())
                                    val component = com.litetask.app.data.model.TaskComponent.FileComponent(
                                        id = newId,
                                        taskId = initialTask?.id ?: 0,
                                        data = data
                                    )
                                    components = components + component
                                },
                                amapKey = amapKey,
                                onGeocode = onGeocode,
                                onSearchLocations = onSearchLocations
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // 提醒设置
                            ReminderSelector(
                                selectedReminders = selectedReminders,
                                onRemindersChanged = { selectedReminders = it },
                                startTime = startTimeMillis,
                                deadline = deadlineMillis
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // 置顶设置
                            val pinEnabled = !isTaskDone  // 只有已完成任务不能置顶
                            val displayIsPinned = if (isTaskDone) false else isPinned
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = pinEnabled) { isPinned = !isPinned }
                                    .background(if (displayIsPinned && pinEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = if (displayIsPinned && pinEnabled) MaterialTheme.colorScheme.primary else extendedColors.textSecondary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.pin_task),
                                        fontWeight = FontWeight.Medium,
                                        color = extendedColors.textPrimary
                                    )
                                    Text(
                                        stringResource(R.string.task_fixed_top),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = extendedColors.textTertiary
                                    )
                                    if (isTaskDone) {
                                        Text(
                                            stringResource(R.string.task_cannot_pin_completed),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = extendedColors.deadlineUrgent
                                        )
                                    }
                                }
                                Switch(
                                    checked = displayIsPinned && pinEnabled,
                                    enabled = pinEnabled,
                                    onCheckedChange = { isPinned = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
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
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                                val effectiveIsPinned = if (isTaskDone) false else isPinned
                                
                                val newTask = Task(
                                    id = initialTask?.id ?: 0,
                                    title = title,
                                    description = description.ifBlank { null },
                                    categoryId = selectedCategoryId,
                                    type = TaskType.WORK, // Deprecated, placeholder
                                    startTime = startTimeMillis,
                                    deadline = deadlineMillis,
                                    isPinned = effectiveIsPinned,
                                    isDone = initialTask?.isDone ?: false,
                                    // 保留过期状态相关字段，避免编辑时被重置
                                    isExpired = initialTask?.isExpired ?: false,
                                    expiredAt = initialTask?.expiredAt,
                                    createdAt = initialTask?.createdAt ?: System.currentTimeMillis(),
                                    completedAt = initialTask?.completedAt,
                                    originalVoiceText = initialTask?.originalVoiceText
                                )
                                
                                // 优先使用新的带组件回调
                                if (onConfirmWithComponents != null) {
                                    onConfirmWithComponents(newTask, selectedReminders, components)
                                } else if (onConfirmWithReminders != null) {
                                    onConfirmWithReminders(newTask, selectedReminders)
                                } else {
                                    onConfirm(newTask)
                                }
                            }
                        },
                        enabled = title.isNotBlank() && deadlineMillis > startTimeMillis,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
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

/**
 * 将已有的Reminder列表转换为ReminderConfig列表
 * 用于编辑任务时预填充提醒设置
 */
private fun convertRemindersToConfigs(
    reminders: List<Reminder>,
    startTime: Long,
    deadline: Long
): List<ReminderConfig> {
    return reminders.mapNotNull { reminder ->
        val triggerAt = reminder.triggerAt
        
        // 尝试匹配预设类型
        when {
            triggerAt == startTime -> ReminderConfig(ReminderType.AT_START)
            triggerAt == startTime - 60 * 60 * 1000 -> ReminderConfig(ReminderType.BEFORE_START_1H)
            triggerAt == startTime - 24 * 60 * 60 * 1000 -> ReminderConfig(ReminderType.BEFORE_START_1D)
            triggerAt == deadline - 60 * 60 * 1000 -> ReminderConfig(ReminderType.BEFORE_END_1H)
            triggerAt == deadline - 24 * 60 * 60 * 1000 -> ReminderConfig(ReminderType.BEFORE_END_1D)
            else -> {
                // 尝试解析自定义提醒
                val diffFromStart = startTime - triggerAt
                val diffFromEnd = deadline - triggerAt
                
                when {
                    diffFromStart > 0 -> {
                        // 开始前的提醒
                        val (value, unit) = parseTimeDiff(diffFromStart)
                        if (value > 0) {
                            ReminderConfig(
                                type = ReminderType.CUSTOM,
                                customValue = value,
                                customUnit = unit,
                                customBase = ReminderBaseTime.BEFORE_START
                            )
                        } else null
                    }
                    diffFromEnd > 0 -> {
                        // 截止前的提醒
                        val (value, unit) = parseTimeDiff(diffFromEnd)
                        if (value > 0) {
                            ReminderConfig(
                                type = ReminderType.CUSTOM,
                                customValue = value,
                                customUnit = unit,
                                customBase = ReminderBaseTime.BEFORE_END
                            )
                        } else null
                    }
                    else -> null
                }
            }
        }
    }
}

/**
 * 解析时间差为值和单位
 */
private fun parseTimeDiff(diffMillis: Long): Pair<Int, ReminderTimeUnit> {
    val minutes = diffMillis / (60 * 1000)
    val hours = diffMillis / (60 * 60 * 1000)
    val days = diffMillis / (24 * 60 * 60 * 1000)
    
    return when {
        days > 0 && diffMillis % (24 * 60 * 60 * 1000) == 0L -> Pair(days.toInt(), ReminderTimeUnit.DAYS)
        hours > 0 && diffMillis % (60 * 60 * 1000) == 0L -> Pair(hours.toInt(), ReminderTimeUnit.HOURS)
        else -> Pair(minutes.toInt(), ReminderTimeUnit.MINUTES)
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
                Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        colors = DatePickerDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        DatePicker(
            state = datePickerState,
            colors = DatePickerDefaults.colors(
                selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                todayContentColor = MaterialTheme.colorScheme.primary,
                todayDateBorderColor = MaterialTheme.colorScheme.primary
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
                Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        text = {
            TimePicker(
                state = timePickerState,
                colors = TimePickerDefaults.colors(
                    clockDialColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    selectorColor = MaterialTheme.colorScheme.primary,
                    timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary,
                    timeSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
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
    val extendedColors = LocalExtendedColors.current
    
    Card(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUrgent) extendedColors.deadlineUrgentSurface else MaterialTheme.colorScheme.surfaceVariant
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
                tint = if (isUrgent) extendedColors.deadlineUrgent else MaterialTheme.colorScheme.primary,
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
                    color = extendedColors.textTertiary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isUrgent) extendedColors.deadlineUrgent else extendedColors.textPrimary,
                    maxLines = 2,
                    lineHeight = 20.sp
                )
            }
        }
    }
}


@Composable
private fun formatDateForDialog(timestamp: Long): String {
    val sdf = SimpleDateFormat(stringResource(R.string.full_date_format), Locale.getDefault())
    return sdf.format(Date(timestamp))
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
                label = stringResource(R.string.start_date),
                icon = Icons.Default.CalendarToday,
                value = "2025年11月28日 周四",
                onClick = {}
            )
            Spacer(modifier = Modifier.height(8.dp))
            DateTimePickerCard(
                label = stringResource(R.string.deadline_time),
                icon = Icons.Default.Flag,
                value = "23:59",
                onClick = {},
                isUrgent = true
            )
        }
    }
}
