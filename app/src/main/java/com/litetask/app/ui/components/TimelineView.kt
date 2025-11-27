package com.litetask.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskType
import java.util.Calendar

@Composable
fun TimelineView(
    tasks: List<Task>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .background(Color(0xFFF2F6FC))
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            tasks.forEach { task ->
                TaskItem(task = task)
            }
        }
    }
}

@Composable
fun TaskItem(task: Task) {
    val isUrgent = task.type == TaskType.DEV // 示例紧急任务类型
    val isDone = task.isDone
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(
                if (isDone) Color(0xFFEEF2F6) else Color.White,
                MaterialTheme.shapes.large
            )
            .padding(16.dp)
    ) {
        Column {
            // 左侧时间轴指示器
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .background(
                        if (isUrgent) Color(0xFFF43F5E) else Color(0xFF0B57D0),
                        MaterialTheme.shapes.small
                    )
            )
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isDone) Color(0xFF747775) else Color(0xFF1F1F1F),
                )
                
                // 时间和位置信息
                Text(
                    text = "${formatTimelineTime(task.startTime)} - ${formatTimelineTime(task.endTime)}" + 
                           if (task.location != null) " • ${task.location}" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isDone) Color(0xFF747775) else Color(0xFF444746),
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                // 截止日期和完成状态
                if (task.endTime > 0) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                    ) {
                        if (isDone) {
                            // 完成状态
                            Text(
                                text = "已完成",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF22C55E),
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            // 截止信息
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isUrgent) Color(0xFFFFD8E4) else Color(0xFFD3E3FD),
                                        MaterialTheme.shapes.small
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "今天截止",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUrgent) Color(0xFF31111D) else Color(0xFF041E49)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatTimelineTime(timestamp: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    return String.format("%02d:%02d", hour, minute)
}