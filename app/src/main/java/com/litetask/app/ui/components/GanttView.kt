package com.litetask.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskType
import com.litetask.app.ui.theme.Primary
import java.util.Calendar

@Composable
fun GanttView(
    tasks: List<Task>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val startHour = 8
    val endHour = 24
    val hourWidth = 100.dp // 每小时的宽度
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .background(Color.White)
    ) {
        // 顶部标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
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
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "Now: 15:30",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF041E49),
                modifier = Modifier
                    .background(Color(0xFFD3E3FD), MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        
        // 时间网格和任务条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp) // 固定高度，实际应用中应动态计算
        ) {
            // 绘制时间网格
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                // 绘制垂直时间线
                for (i in 0..(endHour - startHour)) {
                    val x = i * hourWidth.toPx()
                    drawLine(
                        color = Color(0xFFC4C7C5),
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                    
                    // 时间标签
                    if (i < (endHour - startHour)) {
                        // 简化文本绘制
                        // 实际应用中可能需要使用 Compose 的 Text 组件
                    }
                }
                
                // 绘制当前时间红线
                val currentHour = 15.5f // 假设现在是15:30
                val currentX = (currentHour - startHour) * hourWidth.toPx()
                drawLine(
                    color = Color(0xFFF43F5E), // 玫红色
                    start = Offset(currentX, 0f),
                    end = Offset(currentX, height),
                    strokeWidth = 2.dp.toPx()
                )
                
                // 当前时间点圆点
                drawCircle(
                    color = Color(0xFFF43F5E),
                    radius = 6.dp.toPx(),
                    center = Offset(currentX, 0f)
                )
            }
            
            // 绘制任务条
            tasks.forEachIndexed { index, task ->
                val taskStartHour = getHoursFromTimestamp(task.startTime)
                val taskDurationHours = (task.endTime - task.startTime) / (1000.0f * 60 * 60)
                val offset = (taskStartHour - startHour) * hourWidth.value
                val widthDp = taskDurationHours * hourWidth.value
                
                // 根据任务类型设置颜色
                val (bgColor, textColor, borderColor) = when (task.type) {
                    TaskType.WORK -> Triple(
                        Color(0xFFC2E7FF),
                        Color(0xFF001D35),
                        Color(0xFF7FCFFF)
                    )
                    TaskType.LIFE -> Triple(
                        Color(0xFFE7F3E8),
                        Color(0xFF144419),
                        Color(0xFFBDE3C0)
                    )
                    TaskType.DEV -> Triple(
                        Color(0xFFFFD8E4),
                        Color(0xFF31111D),
                        Color(0xFFFFB0C8)
                    )
                    TaskType.HEALTH -> Triple(
                        Color(0xFFFFEDD5),
                        Color(0xFF9A3412),
                        Color(0xFFFECBA1)
                    )
                    else -> Triple(
                        Color(0xFFE7F3E8),
                        Color(0xFF144419),
                        Color(0xFFBDE3C0)
                    )
                }
                
                Box(
                    modifier = Modifier
                        .offset(x = offset.dp, y = 40.dp + (index * 80).dp)
                        .width(widthDp.dp)
                        .height(60.dp)
                        .background(bgColor, MaterialTheme.shapes.medium)
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = "${String.format("%.1f", taskDurationHours)}h (${formatGanttTime(task.startTime)}-${formatGanttTime(task.endTime)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    // 进度条
                    if (!task.isDone) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Color.Black.copy(alpha = 0.1f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(4.dp)
                                    .fillMaxWidth(task.progress / 100f)
                                    .background(Color.Black.copy(alpha = 0.2f))
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getHoursFromTimestamp(timestamp: Long): Float {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    val hours = calendar.get(Calendar.HOUR_OF_DAY)
    val minutes = calendar.get(Calendar.MINUTE)
    return hours + minutes / 60f
}

fun formatGanttTime(timestamp: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    return String.format("%02d:%02d", hour, minute)
}