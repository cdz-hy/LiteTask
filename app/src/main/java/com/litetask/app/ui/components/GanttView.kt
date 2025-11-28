package com.litetask.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskType
import com.litetask.app.ui.theme.Primary
import java.util.Calendar

@Composable
fun GanttView(
    tasks: List<Task>,
    onTaskClick: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    val startHour = 8
    val endHour = 24
    val hourWidth = 100.dp
    
    // Convert times to milliseconds for calculation
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, startHour)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    val startTimeMillis = calendar.timeInMillis
    
    calendar.set(Calendar.HOUR_OF_DAY, endHour)
    val endTimeMillis = calendar.timeInMillis
    val totalDurationMillis = endTimeMillis - startTimeMillis

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White, MaterialTheme.shapes.large)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.BarChart,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "今日时间分布",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F1F1F)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Now: ${formatGanttTime(System.currentTimeMillis())}",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF041E49),
                modifier = Modifier
                    .background(Color(0xFFD3E3FD), MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        
        // Gantt Chart Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp) // Fixed height for now
        ) {
            // Grid Lines
            Canvas(modifier = Modifier.fillMaxSize()) {
                val height = size.height
                
                for (i in 0..(endHour - startHour)) {
                    val x = i * hourWidth.toPx()
                    drawLine(
                        color = Color(0xFFC4C7C5),
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }
                
                // Current Time Line
                val currentCal = Calendar.getInstance()
                val currentHourVal = currentCal.get(Calendar.HOUR_OF_DAY) + currentCal.get(Calendar.MINUTE) / 60f
                if (currentHourVal in startHour.toFloat()..endHour.toFloat()) {
                    val currentX = (currentHourVal - startHour) * hourWidth.toPx()
                    drawLine(
                        color = Color(0xFFF43F5E),
                        start = Offset(currentX, 0f),
                        end = Offset(currentX, height),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawCircle(
                        color = Color(0xFFF43F5E),
                        radius = 6.dp.toPx(),
                        center = Offset(currentX, 0f)
                    )
                }
            }
            
            // Tasks
            tasks.forEachIndexed { index, task ->
                val taskDurationMillis = task.deadline - task.startTime
                if (taskDurationMillis > 0) {
                    val offsetPercent = ((task.startTime - startTimeMillis).toFloat() / totalDurationMillis)
                    val widthPercent = (taskDurationMillis.toFloat() / totalDurationMillis)
                    
                    // Only draw if within range
                    if (offsetPercent >= 0 && offsetPercent + widthPercent <= 1) {
                         val (bgColor, textColor) = when (task.type) {
                            TaskType.WORK -> Color(0xFFC2E7FF) to Color(0xFF001D35)
                            TaskType.LIFE -> Color(0xFFE7F3E8) to Color(0xFF144419)
                            TaskType.DEV -> Color(0xFFFFD8E4) to Color(0xFF31111D)
                            TaskType.HEALTH -> Color(0xFFFFEDD5) to Color(0xFF9A3412)
                            else -> Color(0xFFE7F3E8) to Color(0xFF144419)
                        }

                        Box(
                            modifier = Modifier
                                .offset(x = (hourWidth * (endHour - startHour) * offsetPercent), y = 40.dp + (index * 70).dp)
                                .width(hourWidth * (endHour - startHour) * widthPercent)
                                .height(60.dp)
                                .background(bgColor, MaterialTheme.shapes.medium)
                                .clickable { onTaskClick(task) }
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${formatGanttTime(task.startTime)}-${formatGanttTime(task.deadline)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatGanttTime(timestamp: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    return String.format("%02d:%02d", hour, minute)
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun GanttViewPreview() {
    val sampleTasks = listOf(
        Task(
            id = 1,
            title = "项目会议",
            description = "讨论Q4规划",
            type = TaskType.WORK,
            startTime = System.currentTimeMillis(),
            deadline = System.currentTimeMillis() + 2 * 60 * 60 * 1000,
            isDone = false
        ),
        Task(
            id = 2,
            title = "健身锻炼",
            description = "跑步",
            type = TaskType.LIFE,
            startTime = System.currentTimeMillis() + 3 * 60 * 60 * 1000,
            deadline = System.currentTimeMillis() + 4 * 60 * 60 * 1000,
            isDone = false
        )
    )
    
    MaterialTheme {
        GanttView(
            tasks = sampleTasks,
            onTaskClick = {}
        )
    }
}