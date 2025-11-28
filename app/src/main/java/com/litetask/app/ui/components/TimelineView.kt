package com.litetask.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineView(
    tasks: List<Task>,
    onTaskClick: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredTasks = tasks.filter { 
        it.title.contains(searchQuery, ignoreCase = true) || 
        (it.description?.contains(searchQuery, ignoreCase = true) == true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF2F6FC))
            .padding(horizontal = 16.dp)
    ) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜索任务...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            singleLine = true
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 80.dp) // Space for FAB
                .verticalScroll(rememberScrollState())
        ) {
            filteredTasks.forEach { task ->
                TaskItem(task = task, onClick = { onTaskClick(task) })
            }
        }
    }
}

@Composable
fun TaskItem(task: Task, onClick: () -> Unit) {
    val isUrgent = task.type == TaskType.URGENT
    val isDone = task.isDone
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(
                if (isDone) Color.White.copy(alpha = 0.6f) else Color.White,
                MaterialTheme.shapes.large
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Left Indicator
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
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isDone) Color(0xFF747775) else Color(0xFF1F1F1F),
                    textDecoration = if (isDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                )
                
                // Time and Location
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = Color(0xFF747775)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${formatTimelineTime(task.startTime)} - ${formatTimelineTime(task.deadline)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isDone) Color(0xFF747775) else Color(0xFF444746)
                    )
                }

                // Deadline Tag
                if (task.deadline > 0 && !isDone) {
                    val isUrgentDeadline = task.deadline < System.currentTimeMillis() + 24 * 60 * 60 * 1000
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .background(
                                if (isUrgentDeadline) Color(0xFFFFD8E4) else Color(0xFFD3E3FD),
                                MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isUrgentDeadline) "今天截止" else "截止: ${formatDate(task.deadline)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isUrgentDeadline) Color(0xFF31111D) else Color(0xFF041E49)
                        )
                    }
                }
            }
            
            // Right Status Icon
            if (isDone) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF146C2E), // Green
                    modifier = Modifier.size(20.dp)
                )
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

fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MM-dd", Locale.getDefault()).format(timestamp)
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun TimelineViewPreview() {
    val sampleTasks = listOf(
        Task(
            id = 1,
            title = "完成项目报告",
            description = "准备季度总结报告",
            type = TaskType.WORK,
            startTime = System.currentTimeMillis(),
            deadline = System.currentTimeMillis() + 2 * 60 * 60 * 1000,
            isDone = false
        ),
        Task(
            id = 2,
            title = "健身锻炼",
            description = "跑步30分钟",
            type = TaskType.LIFE,
            startTime = System.currentTimeMillis() + 3 * 60 * 60 * 1000,
            deadline = System.currentTimeMillis() + 4 * 60 * 60 * 1000,
            isDone = true
        ),
        Task(
            id = 3,
            title = "紧急会议",
            description = "客户需求讨论",
            type = TaskType.URGENT,
            startTime = System.currentTimeMillis(),
            deadline = System.currentTimeMillis() + 1 * 60 * 60 * 1000,
            isDone = false
        )
    )
    
    MaterialTheme {
        TimelineView(
            tasks = sampleTasks,
            onTaskClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TaskItemPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .background(Color(0xFFF2F6FC))
                .padding(16.dp)
        ) {
            TaskItem(
                task = Task(
                    id = 1,
                    title = "完成项目报告",
                    description = "准备季度总结",
                    type = TaskType.WORK,
                    startTime = System.currentTimeMillis(),
                    deadline = System.currentTimeMillis() + 2 * 60 * 60 * 1000,
                    isDone = false
                ),
                onClick = {}
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            TaskItem(
                task = Task(
                    id = 2,
                    title = "紧急任务",
                    description = "需要立即处理",
                    type = TaskType.URGENT,
                    startTime = System.currentTimeMillis(),
                    deadline = System.currentTimeMillis() + 30 * 60 * 1000,
                    isDone = false
                ),
                onClick = {}
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            TaskItem(
                task = Task(
                    id = 3,
                    title = "已完成任务",
                    description = "这个任务已经完成了",
                    type = TaskType.LIFE,
                    startTime = System.currentTimeMillis() - 60 * 60 * 1000,
                    deadline = System.currentTimeMillis(),
                    isDone = true
                ),
                onClick = {}
            )
        }
    }
}