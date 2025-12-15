package com.litetask.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.litetask.app.R
import com.litetask.app.data.model.ReminderBaseTime
import com.litetask.app.data.model.ReminderConfig
import com.litetask.app.data.model.ReminderTimeUnit
import com.litetask.app.data.model.ReminderType
import com.litetask.app.ui.theme.Primary

/**
 * 提醒选择器组件
 * 支持多选预设提醒和自定义提醒
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSelector(
    selectedReminders: List<ReminderConfig>,
    onRemindersChanged: (List<ReminderConfig>) -> Unit,
    startTime: Long,
    deadline: Long,
    modifier: Modifier = Modifier
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    
    // 预设提醒选项
    val presetOptions = listOf(
        ReminderType.AT_START to stringResource(R.string.reminder_at_start),
        ReminderType.BEFORE_START_1H to stringResource(R.string.reminder_before_start_1h),
        ReminderType.BEFORE_START_1D to stringResource(R.string.reminder_before_start_1d),
        ReminderType.BEFORE_END_1H to stringResource(R.string.reminder_before_end_1h),
        ReminderType.BEFORE_END_1D to stringResource(R.string.reminder_before_end_1d)
    )
    
    Column(modifier = modifier.fillMaxWidth()) {
        // 标题
        Text(
            text = stringResource(R.string.reminder_settings),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1F1F1F),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        // 预设选项网格
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 第一行：开始时、开始前1h、开始前1d
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetOptions.take(3).forEach { (type, label) ->
                    val isSelected = selectedReminders.any { it.type == type }
                    ReminderChip(
                        label = label,
                        isSelected = isSelected,
                        onClick = {
                            val newList = if (isSelected) {
                                selectedReminders.filter { it.type != type }
                            } else {
                                selectedReminders + ReminderConfig(type)
                            }
                            onRemindersChanged(newList)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // 第二行：截止前1h、截止前1d
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetOptions.drop(3).forEach { (type, label) ->
                    val isSelected = selectedReminders.any { it.type == type }
                    ReminderChip(
                        label = label,
                        isSelected = isSelected,
                        onClick = {
                            val newList = if (isSelected) {
                                selectedReminders.filter { it.type != type }
                            } else {
                                selectedReminders + ReminderConfig(type)
                            }
                            onRemindersChanged(newList)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                // 添加自定义按钮
                ReminderChip(
                    label = stringResource(R.string.reminder_custom_add),
                    isSelected = false,
                    isAddButton = true,
                    onClick = { showCustomDialog = true },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // 显示已添加的自定义提醒
        val customReminders = selectedReminders.filter { it.type == ReminderType.CUSTOM }
        if (customReminders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                customReminders.forEach { config ->
                    CustomReminderItem(
                        config = config,
                        onDelete = {
                            onRemindersChanged(selectedReminders - config)
                        }
                    )
                }
            }
        }
        
        // 已选提醒数量提示
        if (selectedReminders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.reminder_selected_count, selectedReminders.size),
                style = MaterialTheme.typography.bodySmall,
                color = Primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
    
    // 自定义提醒对话框
    if (showCustomDialog) {
        CustomReminderDialog(
            onDismiss = { showCustomDialog = false },
            onConfirm = { config ->
                onRemindersChanged(selectedReminders + config)
                showCustomDialog = false
            },
            startTime = startTime,
            deadline = deadline
        )
    }
}

@Composable
private fun ReminderChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAddButton: Boolean = false
) {
    val backgroundColor = when {
        isAddButton -> Color(0xFFF0F4F8)
        isSelected -> Primary
        else -> Color(0xFFF8F9FA)
    }
    val contentColor = when {
        isAddButton -> Primary
        isSelected -> Color.White
        else -> Color(0xFF444746)
    }
    val borderColor = when {
        isAddButton -> Primary.copy(alpha = 0.5f)
        isSelected -> Primary
        else -> Color.Transparent
    }
    
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isAddButton) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            } else if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CustomReminderItem(
    config: ReminderConfig,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Primary.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Notifications,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = config.generateLabel(),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF1F1F1F),
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.delete),
                tint = Color(0xFF747775),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomReminderDialog(
    onDismiss: () -> Unit,
    onConfirm: (ReminderConfig) -> Unit,
    startTime: Long,
    deadline: Long
) {
    var value by remember { mutableStateOf("1") }
    var selectedUnit by remember { mutableStateOf(ReminderTimeUnit.HOURS) }
    var selectedBase by remember { mutableStateOf(ReminderBaseTime.BEFORE_END) }
    var showUnitMenu by remember { mutableStateOf(false) }
    
    val unitOptions = listOf(
        ReminderTimeUnit.MINUTES to stringResource(R.string.reminder_unit_minutes),
        ReminderTimeUnit.HOURS to stringResource(R.string.reminder_unit_hours),
        ReminderTimeUnit.DAYS to stringResource(R.string.reminder_unit_days)
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.reminder_custom),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 基准时间选择
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedBase == ReminderBaseTime.BEFORE_START,
                        onClick = { selectedBase = ReminderBaseTime.BEFORE_START },
                        label = { Text(stringResource(R.string.reminder_before_start)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedBase == ReminderBaseTime.BEFORE_END,
                        onClick = { selectedBase = ReminderBaseTime.BEFORE_END },
                        label = { Text(stringResource(R.string.reminder_before_end)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // 时间值和单位
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                value = newValue
                            }
                        },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary
                        )
                    )
                    
                    ExposedDropdownMenuBox(
                        expanded = showUnitMenu,
                        onExpandedChange = { showUnitMenu = it },
                        modifier = Modifier.weight(1.5f)
                    ) {
                        OutlinedTextField(
                            value = unitOptions.find { it.first == selectedUnit }?.second ?: "",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = showUnitMenu)
                            },
                            modifier = Modifier.menuAnchor(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = showUnitMenu,
                            onDismissRequest = { showUnitMenu = false }
                        ) {
                            unitOptions.forEach { (unit, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        selectedUnit = unit
                                        showUnitMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val intValue = value.toIntOrNull() ?: 1
                    if (intValue > 0) {
                        onConfirm(
                            ReminderConfig(
                                type = ReminderType.CUSTOM,
                                customValue = intValue,
                                customUnit = selectedUnit,
                                customBase = selectedBase
                            )
                        )
                    }
                },
                enabled = value.isNotEmpty() && (value.toIntOrNull() ?: 0) > 0
            ) {
                Text(stringResource(R.string.confirm), color = Primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        containerColor = Color.White
    )
}
