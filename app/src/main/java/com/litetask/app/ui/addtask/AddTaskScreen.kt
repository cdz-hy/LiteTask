package com.litetask.app.ui.addtask

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.litetask.app.data.model.TaskType
import com.litetask.app.ui.theme.Indigo600
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(
    onBack: () -> Unit,
    onSave: () -> Unit,
    viewModel: AddTaskViewModel = hiltViewModel()
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(TaskType.WORK) }
    
    // 简化时间选择，默认当前时间 + 1小时
    val calendar = Calendar.getInstance()
    var startTime by remember { mutableStateOf(calendar.timeInMillis) }
    var endTime by remember { mutableStateOf(calendar.timeInMillis + 3600000) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新建任务") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 标题输入
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("要做什么？") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 类型选择
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TaskType.entries.forEach { type ->
                    TypeChip(
                        type = type,
                        isSelected = type == selectedType,
                        onClick = { selectedType = type }
                    )
                }
            }

            // 时间选择 (简化版 UI)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeInput(
                    icon = Icons.Default.Schedule,
                    label = "开始",
                    time = startTime,
                    modifier = Modifier.weight(1f)
                )
                TimeInput(
                    icon = Icons.Default.Schedule,
                    label = "结束",
                    time = endTime,
                    modifier = Modifier.weight(1f)
                )
            }

            // 地点
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("在哪里？(可选)") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 备注
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("备注") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5
            )

            Spacer(modifier = Modifier.weight(1f))

            // 保存按钮
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        viewModel.saveTask(
                            title = title,
                            description = description,
                            startTime = startTime,
                            endTime = endTime,
                            type = selectedType,
                            location = location
                        )
                        onSave()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("确认创建", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun TypeChip(
    type: TaskType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = when(type) {
        TaskType.WORK -> Color(0xFF4F46E5)
        TaskType.LIFE -> Color(0xFF22C55E)
        TaskType.URGENT -> Color(0xFFF97316) // 修改 DEV 为 URGENT
        TaskType.STUDY -> Color(0xFFA855F7)  // 修改 HEALTH 为 STUDY
        TaskType.HEALTH -> Color(0xFF06B6D4) // 添加 HEALTH 类型
        else -> Color.Gray                   // 添加默认分支
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isSelected) color else color.copy(alpha = 0.1f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = type.name.first().toString(),
                color = if (isSelected) Color.White else color,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = type.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) color else Color.Gray
        )
    }
}

@Composable
fun TimeInput(
    icon: ImageVector,
    label: String,
    time: Long,
    modifier: Modifier = Modifier
) {
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(time)
    
    Row(
        modifier = modifier
            .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(timeStr, style = MaterialTheme.typography.titleMedium)
        }
    }
}
