package com.litetask.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskType
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

// --- Colors from Design ---
private val ColorWork = Color(0xFF0B57D0)
private val ColorLife = Color(0xFF146C2E)
private val ColorStudy = Color(0xFF65558F)
private val ColorUrgent = Color(0xFFB3261E)
private val ColorDone = Color(0xFFE0E0E0)
private val ColorTextDone = Color(0xFF9E9E9E)

private val ColorWorkBg = Color(0xFFEFF6FF) // blue-50
private val ColorLifeBg = Color(0xFFECFDF5) // emerald-50
private val ColorStudyBg = Color(0xFFF5F3FF) // violet-50

private val ColorWorkBorder = Color(0xFFBFDBFE) // blue-200
private val ColorLifeBorder = Color(0xFFA7F3D0) // emerald-200
private val ColorStudyBorder = Color(0xFFDDD6FE) // violet-200

@Composable
fun GanttView(
    tasks: List<Task>,
    onTaskClick: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    val dayWidth = 200.dp
    val daysToShow = 3
    val totalWidth = dayWidth * daysToShow
    
    // Time Configuration
    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = now
    // Reset to start of today (00:00)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val startOfToday = calendar.timeInMillis
    
    // Calculate 3-day range
    val endOfView = startOfToday + (daysToShow * 24 * 60 * 60 * 1000L)

    // Filter tasks that overlap with the 3-day view
    val visibleTasks = tasks.filter { task ->
        val taskStart = task.startTime
        val taskEnd = task.deadline
        // Task overlaps if it starts before view ends AND ends after view starts
        taskStart < endOfView && taskEnd > startOfToday
    }.sortedBy { it.startTime } // Sort by start time for better stacking

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
    ) {
        // 1. Legend Header
        GanttHeader()

        // 2. Scrollable Content
        val horizontalScrollState = rememberScrollState()
        val verticalScrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Horizontal Scroll Container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScrollState)
            ) {
                Column(
                    modifier = Modifier
                        .width(totalWidth)
                        .fillMaxHeight()
                ) {
                    // A. Date Headers (Sticky Vertically)
                    Row(
                        modifier = Modifier
                            .height(50.dp)
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.95f))
                            .border(0.5.dp, Color(0xFFF0F0F0))
                    ) {
                        for (i in 0 until daysToShow) {
                            val dayCal = Calendar.getInstance()
                            dayCal.timeInMillis = startOfToday
                            dayCal.add(Calendar.DAY_OF_YEAR, i)
                            
                            val isToday = i == 0
                            val dateStr = SimpleDateFormat("M/d", Locale.getDefault()).format(dayCal.time)
                            val dayLabel = when(i) {
                                0 -> "Today"
                                1 -> "Tom"
                                else -> "Future"
                            }
                            
                            Box(
                                modifier = Modifier
                                    .width(dayWidth)
                                    .fillMaxHeight()
                                    .background(if (isToday) Color(0xFFEFF6FF).copy(alpha = 0.4f) else Color.Transparent)
                                    .border(width = 0.5.dp, color = Color(0xFFF0F0F0)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = dateStr,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4B5563)
                                    )
                                    Text(
                                        text = dayLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = Color(0xFF9CA3AF)
                                    )
                                }
                            }
                        }
                    }

                    // B. Task Area (Scrolls Vertically)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(verticalScrollState)
                    ) {
                        // Grid Background
                        GanttGrid(
                            daysToShow = daysToShow,
                            dayWidth = dayWidth,
                            height = max(500.dp, 60.dp + (visibleTasks.size * 70).dp),
                            startOfToday = startOfToday,
                            now = now
                        )

                        // Tasks
                        GanttTasks(
                            tasks = visibleTasks,
                            startOfView = startOfToday,
                            dayWidth = dayWidth,
                            onTaskClick = onTaskClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GanttHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF1F2937)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "3日视图",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendItem(color = ColorWork, label = "Work")
            LegendItem(color = ColorLife, label = "Life")
            LegendItem(color = ColorStudy, label = "Study")
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4B5563)
        )
    }
}

@Composable
fun GanttGrid(
    daysToShow: Int,
    dayWidth: Dp,
    height: Dp,
    startOfToday: Long,
    now: Long
) {
    Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(height)) {
        
        val dayWidthPx = dayWidth.toPx()
        
        // Draw Day Columns and Hour Lines
        for (i in 0 until daysToShow) {
            val xOffset = i * dayWidthPx
            
            // Vertical Day Separators
            drawLine(
                color = Color(0xFFF3F4F6),
                start = Offset(xOffset, 0f),
                end = Offset(xOffset, size.height),
                strokeWidth = 1.dp.toPx()
            )
            
            // Hour Dashed Lines (6h, 12h, 18h)
            val hours = listOf(6, 12, 18)
            hours.forEach { h ->
                val hOffset = xOffset + (h / 24f) * dayWidthPx
                drawLine(
                    color = Color(0xFFF3F4F6),
                    start = Offset(hOffset, 40f), // Start a bit lower
                    end = Offset(hOffset, size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }
        }

        // Current Time Line
        val diffMillis = now - startOfToday
        if (diffMillis >= 0) {
            val totalPx = daysToShow * dayWidthPx
            val msPerPx = (daysToShow * 24 * 60 * 60 * 1000L) / totalPx
            val nowX = diffMillis / msPerPx
            
            if (nowX in 0f..totalPx) {
                drawLine(
                    color = Color(0xFFEF4444), // Red-500
                    start = Offset(nowX, 0f),
                    end = Offset(nowX, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}

@Composable
fun GanttTasks(
    tasks: List<Task>,
    startOfView: Long,
    dayWidth: Dp,
    onTaskClick: (Task) -> Unit
) {
    GanttTasksLayout(tasks, startOfView, dayWidth, onTaskClick)
}

@Composable
fun GanttTasksLayout(
    tasks: List<Task>,
    startOfView: Long,
    dayWidth: Dp,
    onTaskClick: (Task) -> Unit
) {
    val density = LocalDensity.current
    val msPerDay = 24 * 60 * 60 * 1000L
    
    Layout(
        content = {
            tasks.forEach { task ->
                GanttTaskCard(task = task, onClick = { onTaskClick(task) })
            }
        }
    ) { measurables, constraints ->
        val dayWidthPx = with(density) { dayWidth.toPx() }
        val msPerPx = msPerDay / dayWidthPx
        
        // 1. Measure children with calculated widths
        val placeables = measurables.mapIndexed { index, measurable ->
            val task = tasks[index]
            val startOffsetMs = task.startTime - startOfView
            val durationMs = task.deadline - task.startTime
            
            val originalXPx = (startOffsetMs / msPerPx).toInt()
            val originalWidthPx = (durationMs / msPerPx).toInt()
            
            // Logic from prototype: if starts before view, clip start and reduce width
            var visibleWidthPx = originalWidthPx
            if (originalXPx < 0) {
                visibleWidthPx = originalWidthPx + originalXPx
            }
            
            // Ensure min width for visibility
            visibleWidthPx = visibleWidthPx.coerceAtLeast(10)
            
            measurable.measure(
                androidx.compose.ui.unit.Constraints.fixedWidth(visibleWidthPx)
            )
        }

        // 2. Place them
        val totalHeight = (60 + tasks.size * 70).dp.roundToPx()
        
        layout(constraints.maxWidth, totalHeight) {
            placeables.forEachIndexed { index, placeable ->
                val task = tasks[index]
                val startOffsetMs = task.startTime - startOfView
                val msPerPxVal = msPerDay / dayWidthPx // Recalculate or capture
                
                var xPos = (startOffsetMs / msPerPxVal).toInt()
                
                // If starts before view, we clamp position to 0 (since we already adjusted width)
                if (xPos < 0) xPos = 0
                
                val yPos = (60 + index * 70).dp.roundToPx()
                placeable.place(x = xPos, y = yPos)
            }
        }
    }
}

@Composable
fun GanttTaskCard(
    task: Task,
    onClick: () -> Unit
) {
    // Determine Colors
    val (bg, border, text, fill) = if (task.isDone) {
        Quad(ColorDone, Color(0xFFBDBDBD), ColorTextDone, Color(0xFFBDBDBD))
    } else {
        when (task.type) {
            TaskType.WORK -> Quad(ColorWorkBg, ColorWorkBorder, ColorWork, ColorWork)
            TaskType.LIFE -> Quad(ColorLifeBg, ColorLifeBorder, ColorLife, ColorLife)
            TaskType.STUDY -> Quad(ColorStudyBg, ColorStudyBorder, ColorStudy, ColorStudy)
            TaskType.URGENT -> Quad(Color(0xFFFEF2F2), Color(0xFFFECACA), ColorUrgent, ColorUrgent)
            else -> Quad(ColorWorkBg, ColorWorkBorder, ColorWork, ColorWork)
        }
    }
    
    Surface(
        modifier = Modifier
            .height(56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = androidx.compose.foundation.BorderStroke(1.dp, border),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Title
            Text(
                text = task.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Time / Deadline Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = text.copy(alpha = 0.6f),
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                
                val timeStr = formatTaskTimeRange(task.startTime, task.deadline)
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = text.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatTaskTimeRange(start: Long, end: Long): String {
    val startCal = Calendar.getInstance().apply { timeInMillis = start }
    val endCal = Calendar.getInstance().apply { timeInMillis = end }
    
    val sameDay = startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR) &&
                  startCal.get(Calendar.DAY_OF_YEAR) == endCal.get(Calendar.DAY_OF_YEAR)
                  
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    
    return if (sameDay) {
        "${timeFormat.format(Date(start))} - ${timeFormat.format(Date(end))}"
    } else {
        "${dateFormat.format(Date(start))} - ${dateFormat.format(Date(end))}"
    }
}

data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Preview(showBackground = true)
@Composable
fun GanttViewPreview() {
    val now = System.currentTimeMillis()
    val tasks = listOf(
        Task(
            id = 1,
            title = "软件工程大作业",
            type = TaskType.STUDY,
            startTime = now - 3600000,
            deadline = now + 86400000 // Cross day
        ),
        Task(
            id = 2,
            title = "前端界面开发",
            type = TaskType.WORK,
            startTime = now + 7200000,
            deadline = now + 18000000
        ),
        Task(
            id = 3,
            title = "已完成任务",
            type = TaskType.LIFE,
            startTime = now + 3600000,
            deadline = now + 7200000,
            isDone = true
        )
    )
    GanttView(tasks = tasks, onTaskClick = {})
}