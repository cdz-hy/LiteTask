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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.litetask.app.R
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.data.model.TaskType
import com.litetask.app.data.model.Category
import com.litetask.app.ui.util.ColorUtils
import com.litetask.app.ui.theme.LocalExtendedColors
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

enum class GanttViewMode {
    TODAY,    // 今日视图
    THREE_DAY, // 3日视图
    SEVEN_DAY  // 7日视图
}

@Composable
fun GanttView(
    taskComposites: List<TaskDetailComposite>,
    onTaskClick: (Task) -> Unit,
    onNavigateToFullscreen: (GanttViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var viewMode by remember { mutableStateOf(GanttViewMode.THREE_DAY) }
    val extendedColors = LocalExtendedColors.current
    
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
    
    // 根据视图模式配置参数
    val (dayWidth, daysToShow, startOffset) = when (viewMode) {
        GanttViewMode.TODAY -> Triple(800.dp, 1, 0)      // 今日视图：更宽，显示1天
        GanttViewMode.THREE_DAY -> Triple(220.dp, 3, 0)  // 3日视图：今天+未来2天
        GanttViewMode.SEVEN_DAY -> Triple(180.dp, 7, -2) // 7日视图：前2天+今天+后4天
    }
    
    val totalWidth = dayWidth * daysToShow
    
    // 计算视图的起始和结束时间
    val startOfView = startOfToday + (startOffset * 24 * 60 * 60 * 1000L)
    val endOfView = startOfView + (daysToShow * 24 * 60 * 60 * 1000L)

    // Filter tasks that overlap with the view (包含所有状态的任务)
    val visibleTasks = taskComposites.filter { composite ->
        val taskStart = composite.task.startTime
        val taskEnd = composite.task.deadline
        taskStart < endOfView && taskEnd > startOfView
    }.sortedBy { it.task.startTime }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        // 1. Legend Header with View Mode Selector
        GanttHeader(
            viewMode = viewMode,
            onViewModeChange = { viewMode = it }
        )

        // 2. Scrollable Content
        val horizontalScrollState = rememberScrollState()
        val verticalScrollState = rememberScrollState()
        val density = LocalDensity.current
        
        // 7日视图初始滚动到今天的位置（居中）
        LaunchedEffect(viewMode) {
            if (viewMode == GanttViewMode.SEVEN_DAY) {
                val todayOffset = 2 // 今天是第3天（索引2）
                val scrollToX = (todayOffset * with(density) { dayWidth.toPx() }).toInt()
                horizontalScrollState.scrollTo(scrollToX)
            } else {
                horizontalScrollState.scrollTo(0)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (visibleTasks.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.DateRange,
                    title = stringResource(R.string.no_tasks_yet),
                    subtitle = stringResource(R.string.no_tasks_hint),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

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
                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f))
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        for (i in 0 until daysToShow) {
                            val dayCal = Calendar.getInstance()
                            dayCal.timeInMillis = startOfView
                            dayCal.add(Calendar.DAY_OF_YEAR, i)
                            
                            val isToday = dayCal.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR) &&
                                         dayCal.get(Calendar.DAY_OF_YEAR) == Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                            
                            val dateStr = SimpleDateFormat("M/d", Locale.getDefault()).format(dayCal.time)
                            val dayLabel = when {
                                isToday -> stringResource(R.string.today)
                                viewMode == GanttViewMode.TODAY -> SimpleDateFormat("EEEE", Locale.getDefault()).format(dayCal.time)
                                else -> SimpleDateFormat("EEE", Locale.getDefault()).format(dayCal.time)
                            }
                            
                            Box(
                                modifier = Modifier
                                    .width(dayWidth)
                                    .fillMaxHeight()
                                    .background(if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                                    .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = dateStr,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = dayLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
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
                            height = max(500.dp, 40.dp + (visibleTasks.size * 70).dp),
                            startOfView = startOfView,
                            now = now,
                            viewMode = viewMode
                        )

                        // Tasks
                        GanttTasks(
                            taskComposites = visibleTasks,
                            startOfView = startOfView,
                            dayWidth = dayWidth,
                            onTaskClick = onTaskClick
                        )
                    }
                }
            }
            
            // 全屏按钮（左下角悬浮）
            FloatingActionButton(
                onClick = { onNavigateToFullscreen(viewMode) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 2.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = stringResource(R.string.fullscreen),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GanttHeader(
    viewMode: GanttViewMode,
    onViewModeChange: (GanttViewMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val extendedColors = LocalExtendedColors.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：视图模式选择器
        Box {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.height(36.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = extendedColors.ganttWork
                    )
                    Text(
                        text = when (viewMode) {
                            GanttViewMode.TODAY -> stringResource(R.string.today_view)
                            GanttViewMode.THREE_DAY -> stringResource(R.string.three_day_view)
                            GanttViewMode.SEVEN_DAY -> stringResource(R.string.seven_day_view)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = extendedColors.ganttWork
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = extendedColors.ganttWork
                    )
                }
            }
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.today_view), style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        onViewModeChange(GanttViewMode.TODAY)
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.three_day_view), style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        onViewModeChange(GanttViewMode.THREE_DAY)
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.seven_day_view), style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        onViewModeChange(GanttViewMode.SEVEN_DAY)
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                )
            }
        }

        // 右侧：图例
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendItem(color = extendedColors.ganttWork, label = stringResource(R.string.task_type_work))
            LegendItem(color = extendedColors.ganttLife, label = stringResource(R.string.task_type_life))
            LegendItem(color = extendedColors.ganttStudy, label = stringResource(R.string.task_type_study))
            LegendItem(color = extendedColors.ganttUrgent, label = stringResource(R.string.task_type_urgent))
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
            color = LocalExtendedColors.current.textSecondary
        )
    }
}

@Composable
fun GanttGrid(
    daysToShow: Int,
    dayWidth: Dp,
    height: Dp,
    startOfView: Long,
    now: Long,
    viewMode: GanttViewMode
) {
    val density = LocalDensity.current
    val extendedColors = LocalExtendedColors.current
    
    // 在 Canvas 外部获取颜色值
    val gridLineColor = extendedColors.ganttGridLine
    val hourLineColor = extendedColors.ganttHourLine
    val currentTimeColor = extendedColors.ganttCurrentTime
    
    Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(height)) {
        
        val dayWidthPx = dayWidth.toPx()
        
        // 根据视图模式确定时间刻度
        val hourIntervals = when (viewMode) {
            GanttViewMode.TODAY -> (0..23).toList()      // 今日视图：每小时
            GanttViewMode.THREE_DAY -> listOf(0, 6, 12, 18) // 3日视图：6小时间隔
            GanttViewMode.SEVEN_DAY -> listOf(0, 12)     // 7日视图：12小时间隔
        }
        
        // Draw Day Columns and Hour Lines
        for (i in 0 until daysToShow) {
            val xOffset = i * dayWidthPx
            
            // Vertical Day Separators
            drawLine(
                color = gridLineColor,
                start = Offset(xOffset, 0f),
                end = Offset(xOffset, size.height),
                strokeWidth = 2.dp.toPx()
            )
            
            // Hour Lines with Time Labels
            hourIntervals.forEach { h ->
                if (h == 0 && i > 0) return@forEach // 跳过非第一天的0点（已有日分隔线）
                
                val hOffset = xOffset + (h / 24f) * dayWidthPx
                
                // 绘制时间刻度线
                if (h != 0 || i == 0) {
                    drawLine(
                        color = hourLineColor,
                        start = Offset(hOffset, 0f),
                        end = Offset(hOffset, size.height),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = if (h == 0 || h == 12) null else PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    )
                }
            }
        }

        // Current Time Line
        val diffMillis = now - startOfView
        val totalPx = daysToShow * dayWidthPx
        val totalMs = daysToShow * 24 * 60 * 60 * 1000L
        
        if (diffMillis >= 0 && diffMillis <= totalMs) {
            val nowX = (diffMillis.toFloat() / totalMs) * totalPx
            
            drawLine(
                color = currentTimeColor,
                start = Offset(nowX, 0f),
                end = Offset(nowX, size.height),
                strokeWidth = 2.5.dp.toPx()
            )
            
            // 当前时间指示器圆点
            drawCircle(
                color = currentTimeColor,
                radius = 4.dp.toPx(),
                center = Offset(nowX, 8.dp.toPx())
            )
        }
    }
    
    // 时间刻度标签（叠加在Canvas上方）
    Box(modifier = Modifier.fillMaxWidth().height(height)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            for (i in 0 until daysToShow) {
                Box(modifier = Modifier.width(dayWidth)) {
                    val hourIntervals = when (viewMode) {
                        GanttViewMode.TODAY -> (0..23 step 1).toList()
                        GanttViewMode.THREE_DAY -> listOf(0, 6, 12, 18)
                        GanttViewMode.SEVEN_DAY -> listOf(0, 12)
                    }
                    
                    hourIntervals.forEach { h ->
                        if (h == 0 && i > 0) return@forEach
                        
                        val offsetFraction = h / 24f
                        Box(
                            modifier = Modifier
                                .offset(x = dayWidth * offsetFraction - 12.dp, y = 2.dp)
                        ) {
                            Text(
                                text = String.format("%02d", h),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = extendedColors.textTertiary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GanttTasks(
    taskComposites: List<TaskDetailComposite>,
    startOfView: Long,
    dayWidth: Dp,
    enabled: Boolean = true,
    onTaskClick: (Task) -> Unit
) {
    GanttTasksLayout(taskComposites, startOfView, dayWidth, enabled, onTaskClick)
}

@Composable
fun GanttTasksLayout(
    taskComposites: List<TaskDetailComposite>,
    startOfView: Long,
    dayWidth: Dp,
    enabled: Boolean = true,
    onTaskClick: (Task) -> Unit
) {
    val density = LocalDensity.current
    val msPerDay = 24 * 60 * 60 * 1000L
    
    Layout(
        content = {
            taskComposites.forEach { composite ->
                GanttTaskCard(
                    composite = composite, 
                    enabled = enabled,
                    onClick = { onTaskClick(composite.task) }
                )
            }
        }
    ) { measurables, constraints ->
        val dayWidthPx = with(density) { dayWidth.toPx() }
        val msPerPx = msPerDay / dayWidthPx
        
        // 1. Measure children with calculated widths
        val placeables = measurables.mapIndexed { index, measurable ->
            val task = taskComposites[index].task
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
        val totalHeight = (40 + taskComposites.size * 70).dp.roundToPx()
        
        layout(constraints.maxWidth, totalHeight) {
            placeables.forEachIndexed { index, placeable ->
                val task = taskComposites[index].task
                val startOffsetMs = task.startTime - startOfView
                val msPerPxVal = msPerDay / dayWidthPx
                
                var xPos = (startOffsetMs / msPerPxVal).toInt()
                
                // If starts before view, we clamp position to 0 (since we already adjusted width)
                if (xPos < 0) xPos = 0
                
                val yPos = (40 + index * 70).dp.roundToPx()
                placeable.place(x = xPos, y = yPos)
            }
        }
    }
}

@Composable
fun GanttTaskCard(
    composite: TaskDetailComposite,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val task = composite.task
    val subTasks = composite.subTasks
    val extendedColors = LocalExtendedColors.current
    // Calculate Progress
    val hasSubtasks = subTasks.isNotEmpty()
    val progress = if (hasSubtasks) {
        val completed = subTasks.count { it.isCompleted }
        (completed.toFloat() / subTasks.size.toFloat())
    } else {
        0f
    }

    // Determine Colors using theme - 避免卡片半透明，保持实心背景
    // Determine Colors using theme - 避免卡片半透明，保持实心背景
    val category = composite.category
    
    val baseColor = if (category != null) {
        ColorUtils.parseColor(category.colorHex)
    } else {
        when (task.type) {
            TaskType.WORK -> extendedColors.ganttWork
            TaskType.LIFE -> extendedColors.ganttLife
            TaskType.STUDY -> extendedColors.ganttStudy
            TaskType.URGENT -> extendedColors.ganttUrgent
        }
    }
    
    val baseSurface = if (category != null) {
        ColorUtils.getSurfaceColor(baseColor)
    } else {
        when (task.type) {
            TaskType.WORK -> extendedColors.ganttWorkBackground
            TaskType.LIFE -> extendedColors.ganttLifeBackground
            TaskType.STUDY -> extendedColors.ganttStudyBackground
            TaskType.URGENT -> extendedColors.ganttUrgentBackground
        }
    }

    val (bg, border, text, fill) = when {
        task.isDone -> {
            // 已完成任务：灰色显示
            Quad(extendedColors.ganttDoneBackground, extendedColors.ganttDoneText, extendedColors.ganttDoneText, extendedColors.ganttDoneText)
        }
        task.isExpired -> {
            // 已过期任务：使用任务类型颜色但文字透明度降低，背景保持实心
            Quad(baseSurface, baseColor, baseColor.copy(alpha = 0.7f), baseColor.copy(alpha = 0.7f))
        }
        else -> {
            // 未完成任务：正常显示
            Quad(baseSurface, baseColor, baseColor, baseColor)
        }
    }
    
    Surface(
        modifier = Modifier
            .height(56.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = androidx.compose.foundation.BorderStroke(1.dp, border),
        shadowElevation = 2.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Progress Overlay (Background)
            if (hasSubtasks) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(fill.copy(alpha = 0.1f))
                        .align(Alignment.CenterStart)
                )
            }

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
                
                // Bottom Row: Progress % or Icon + Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Time
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!hasSubtasks) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = text.copy(alpha = 0.6f),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        
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
                    
                    // Right: Progress % (if has subtasks)
                    if (hasSubtasks) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = text.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            
            // Progress Bar (Bottom Line)
            if (hasSubtasks) {
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .fillMaxWidth(progress)
                        .background(fill)
                        .align(Alignment.BottomStart)
                )
            }
            
            // 过期任务的灰色半透明遮罩（普通过期和过期置顶统一样式）
            if (task.isExpired && !task.isDone) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.Gray.copy(alpha = 0.2f),
                            RoundedCornerShape(8.dp)
                        )
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
    // Mock Data
    val tasks = listOf(
        TaskDetailComposite(
            task = Task(
                id = 1,
                title = "软件工程大作业 (含子任务)",
                type = TaskType.STUDY,
                startTime = now - 3600000,
                deadline = now + 86400000 // Cross day
            ),
            subTasks = listOf(
                com.litetask.app.data.model.SubTask(taskId = 1, content = "Sub 1", isCompleted = true),
                com.litetask.app.data.model.SubTask(taskId = 1, content = "Sub 2", isCompleted = false)
            ),
            reminders = emptyList(),
            category = null
        ),
        TaskDetailComposite(
            task = Task(
                id = 2,
                title = "前端界面开发",
                type = TaskType.WORK,
                startTime = now + 7200000,
                deadline = now + 18000000
            ),
            subTasks = emptyList(),
            reminders = emptyList(),
            category = null
        ),
        TaskDetailComposite(
            task = Task(
                id = 3,
                title = "已完成任务",
                type = TaskType.LIFE,
                startTime = now + 3600000,
                deadline = now + 7200000,
                isDone = true
            ),
            subTasks = emptyList(),
            reminders = emptyList(),
            category = null
        )
    )
    GanttView(
        taskComposites = tasks,
        onTaskClick = {},
        onNavigateToFullscreen = {}
    )
}