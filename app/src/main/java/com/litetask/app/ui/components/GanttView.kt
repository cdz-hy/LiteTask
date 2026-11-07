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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
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
import kotlin.math.roundToInt

enum class GanttViewMode {
    TODAY,    // 今日视图
    THREE_DAY, // 3日视图
    SEVEN_DAY, // 7日视图
    ONE_MONTH  // 1月视图
}

@Composable
fun GanttView(
    taskComposites: List<TaskDetailComposite>,
    onTaskClick: (Task) -> Unit,
    onNavigateToFullscreen: (GanttViewMode) -> Unit,
    defaultViewMode: GanttViewMode = GanttViewMode.THREE_DAY,
    modifier: Modifier = Modifier,
    categories: List<Category> = emptyList()
) {
    var viewMode by remember { mutableStateOf(defaultViewMode) }
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
        GanttViewMode.ONE_MONTH -> Triple(80.dp, 30, -5) // 1月视图：前5天+今天+后24天
    }
    
    val totalWidth = dayWidth * daysToShow
    
    // 计算视图的起始和结束时间
    val startOfView = startOfToday + (startOffset * 24 * 60 * 60 * 1000L)
    val endOfView = startOfView + (daysToShow * 24 * 60 * 60 * 1000L)

    var selectedCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedFallbackType by rememberSaveable { mutableStateOf<TaskType?>(null) }
    val hasActiveFilter = selectedCategoryId != null || selectedFallbackType != null

    // Filter tasks that overlap with the view (支持按图例分类筛选)
    val visibleTasks = taskComposites.filter { composite ->
        val taskStart = composite.task.startTime
        val taskEnd = composite.task.deadline
        val inRange = taskStart < endOfView && taskEnd > startOfView
        if (!inRange) return@filter false
        when {
            selectedCategoryId != null -> composite.category?.id == selectedCategoryId
            selectedFallbackType != null -> composite.task.type == selectedFallbackType
            else -> true
        }
    }.sortedBy { it.task.startTime }

    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val dayWidthPx = with(density) { dayWidth.toPx() }
    val todayScrollX = remember(viewMode, dayWidthPx, startOfToday, now) {
        when (viewMode) {
            GanttViewMode.TODAY -> {
                val msPerDay = 24f * 60 * 60 * 1000f
                val nowRatio = ((now - startOfToday).toFloat() / msPerDay).coerceIn(0f, 1f)
                max(0, ((nowRatio * dayWidthPx) - (dayWidthPx * 0.15f)).toInt())
            }
            GanttViewMode.SEVEN_DAY -> (2 * dayWidthPx).toInt()
            GanttViewMode.ONE_MONTH -> (5 * dayWidthPx).toInt()
            GanttViewMode.THREE_DAY -> 0
        }
    }

    // 视图初始滚动到今天的位置
    LaunchedEffect(viewMode, todayScrollX) {
        horizontalScrollState.scrollTo(todayScrollX)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        // 1. 顶部工具栏（模式切换 + 定位今天 + 全屏入口 + 可点按分类筛选图例）
        GanttHeader(
            viewMode = viewMode,
            onViewModeChange = { viewMode = it },
            categories = categories,
            onScrollToToday = {
                coroutineScope.launch {
                    horizontalScrollState.animateScrollTo(todayScrollX)
                }
            },
            onNavigateToFullscreen = { onNavigateToFullscreen(viewMode) },
            selectedCategoryId = selectedCategoryId,
            onSelectCategory = { id ->
                selectedFallbackType = null
                selectedCategoryId = if (selectedCategoryId == id) null else id
            },
            selectedTaskType = selectedFallbackType,
            onSelectTaskType = { type ->
                selectedCategoryId = null
                selectedFallbackType = if (selectedFallbackType == type) null else type
            }
        )

        // 2. Scrollable Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (visibleTasks.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EmptyStateView(
                        icon = Icons.Default.DateRange,
                        title = if (hasActiveFilter) "该分类下暂无日程" else stringResource(R.string.no_tasks_yet),
                        subtitle = if (hasActiveFilter) "可点击上方高亮图例或下方按钮恢复全部任务" else stringResource(R.string.no_tasks_hint)
                    )
                    if (hasActiveFilter) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = {
                                selectedCategoryId = null
                                selectedFallbackType = null
                            }
                        ) {
                            Text("显示全部分类")
                        }
                    }
                }
            } else {
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
                            endOfView = endOfView,
                            dayWidth = dayWidth,
                            onTaskClick = onTaskClick
                        )
                    }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GanttHeader(
    viewMode: GanttViewMode,
    onViewModeChange: (GanttViewMode) -> Unit,
    categories: List<Category> = emptyList(),
    onScrollToToday: (() -> Unit)? = null,
    onNavigateToFullscreen: (() -> Unit)? = null,
    selectedCategoryId: Long? = null,
    onSelectCategory: ((Long?) -> Unit)? = null,
    selectedTaskType: TaskType? = null,
    onSelectTaskType: ((TaskType?) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val extendedColors = LocalExtendedColors.current
    val hasActiveFilter = selectedCategoryId != null || selectedTaskType != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：视图模式选择器 + 「今日」快捷回定位按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                                    GanttViewMode.ONE_MONTH -> stringResource(R.string.one_month_view)
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
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.one_month_view), style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                onViewModeChange(GanttViewMode.ONE_MONTH)
                                expanded = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        )
                    }
                }

                if (onScrollToToday != null) {
                    Surface(
                        onClick = onScrollToToday,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.today),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // 右侧：全屏按钮（彻底避免底部双 FAB 冲突）
            if (onNavigateToFullscreen != null) {
                Surface(
                    onClick = onNavigateToFullscreen,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = stringResource(R.string.fullscreen),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "全屏",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 第二行：图例即轻量分类筛选条（点击筛选对应分类，再次点击取消）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (categories.isNotEmpty()) {
                categories.forEach { category ->
                    val isSelected = selectedCategoryId == category.id
                    LegendItem(
                        color = ColorUtils.parseColor(category.colorHex),
                        label = category.name,
                        selected = isSelected,
                        isFilterActive = hasActiveFilter,
                        onClick = onSelectCategory?.let { cb -> { cb(category.id) } }
                    )
                }
            } else {
                val fallbackTypes = listOf(
                    TaskType.WORK to Pair(extendedColors.ganttWork, stringResource(R.string.task_type_work)),
                    TaskType.LIFE to Pair(extendedColors.ganttLife, stringResource(R.string.task_type_life)),
                    TaskType.STUDY to Pair(extendedColors.ganttStudy, stringResource(R.string.task_type_study)),
                    TaskType.URGENT to Pair(extendedColors.ganttUrgent, stringResource(R.string.task_type_urgent))
                )
                fallbackTypes.forEach { (type, pair) ->
                    val isSelected = selectedTaskType == type
                    LegendItem(
                        color = pair.first,
                        label = pair.second,
                        selected = isSelected,
                        isFilterActive = hasActiveFilter,
                        onClick = onSelectTaskType?.let { cb -> { cb(type) } }
                    )
                }
            }
        }
    }
}

@Composable
fun LegendItem(
    color: Color,
    label: String,
    selected: Boolean = true,
    isFilterActive: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val contentAlpha = if (isFilterActive && !selected) 0.45f else 1f
    val chipModifier = if (onClick != null) {
        val bg = if (selected && isFilterActive) {
            color.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
        }
        val borderColor = if (selected && isFilterActive) color.copy(alpha = 0.7f) else Color.Transparent
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    } else {
        Modifier
    }

    Row(
        modifier = chipModifier.alpha(contentAlpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            fontWeight = if (selected && isFilterActive) FontWeight.Bold else FontWeight.Medium,
            color = if (selected && isFilterActive) color else LocalExtendedColors.current.textSecondary
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
            GanttViewMode.ONE_MONTH -> listOf(0)         // 1月视图：只显示天分隔线
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
                        GanttViewMode.ONE_MONTH -> listOf() // 不显示小时
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
    endOfView: Long = startOfView + GanttLayoutSupport.MS_PER_DAY * 30L,
    onTaskClick: (Task) -> Unit
) {
    GanttTasksLayout(
        taskComposites = taskComposites,
        startOfView = startOfView,
        endOfView = endOfView,
        dayWidth = dayWidth,
        enabled = enabled,
        onTaskClick = onTaskClick
    )
}

@Composable
fun GanttTasksLayout(
    taskComposites: List<TaskDetailComposite>,
    startOfView: Long,
    endOfView: Long = startOfView + GanttLayoutSupport.MS_PER_DAY * 30L,
    dayWidth: Dp,
    enabled: Boolean = true,
    onTaskClick: (Task) -> Unit
) {
    val density = LocalDensity.current
    val dayWidthPx = with(density) { dayWidth.toPx() }
    val minHairlinePx = with(density) { 3.5.dp.toPx() }
    val fullCardMinWidthPx = with(density) { 44.dp.toPx() }
    val thinBarMaxWidthPx = with(density) { 10.dp.toPx() }
    val labelGapPx = with(density) { 4.dp.roundToPx() }
    val rowSlotHeightDp = 56.dp

    val geometries = remember(taskComposites, startOfView, endOfView, dayWidthPx, minHairlinePx, fullCardMinWidthPx) {
        taskComposites.map { composite ->
            GanttLayoutSupport.computeBarGeometry(
                taskStartMs = composite.task.startTime,
                taskDeadlineMs = composite.task.deadline,
                startOfViewMs = startOfView,
                endOfViewMs = endOfView,
                dayWidthPx = dayWidthPx,
                minHairlineWidthPx = minHairlinePx,
                fullCardMinWidthPx = fullCardMinWidthPx
            )
        }
    }

    Layout(
        content = {
            taskComposites.forEachIndexed { index, composite ->
                val geom = geometries[index]
                val barIsFullCard = geom.mode == GanttLayoutSupport.BarRenderMode.FULL_CARD
                val barIsThinMarker = geom.barWidthPx < thinBarMaxWidthPx
                val barWidthDp = with(density) { geom.barWidthPx.toDp() }
                if (barIsFullCard) {
                    GanttTaskCard(
                        composite = composite,
                        cardWidthDp = barWidthDp,
                        enabled = enabled,
                        onClick = { onTaskClick(composite.task) }
                    )
                    // Full card already carries title/time inside; empty spacer slot keeps 2-per-task indexing
                    Spacer(modifier = Modifier.size(0.dp))
                } else {
                    GanttProportionalTaskBar(
                        composite = composite,
                        isThinMarker = barIsThinMarker,
                        enabled = enabled,
                        onClick = { onTaskClick(composite.task) }
                    )
                    GanttExternalTaskLabel(
                        composite = composite,
                        alignRight = GanttLayoutSupport.shouldPreferLeftLabel(
                            geometry = geom,
                            dayWidthPx = dayWidthPx,
                            estimatedThresholdPx = with(density) { 84.dp.toPx() }
                        ),
                        enabled = enabled,
                        onClick = { onTaskClick(composite.task) }
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val totalHeight = (40 + taskComposites.size * 70).dp.roundToPx()
        val labelMaxWidthPx = 112.dp.roundToPx()
        val rowHeightPx = rowSlotHeightDp.roundToPx()
        val totalViewWidthPx = if (endOfView > startOfView) {
            (((endOfView - startOfView).toDouble() * dayWidthPx) / GanttLayoutSupport.MS_PER_DAY).roundToInt()
                .coerceAtLeast(constraints.maxWidth)
        } else {
            constraints.maxWidth
        }

        val barPlaceables = ArrayList<androidx.compose.ui.layout.Placeable>(taskComposites.size)
        val labelPlaceables = ArrayList<androidx.compose.ui.layout.Placeable?>(taskComposites.size)

        for (i in taskComposites.indices) {
            val geom = geometries[i]
            val barMeasurable = measurables[i * 2]
            val labelMeasurable = measurables[i * 2 + 1]

            barPlaceables.add(
                barMeasurable.measure(
                    androidx.compose.ui.unit.Constraints.fixed(geom.barWidthPx, rowHeightPx)
                )
            )
            if (geom.mode == GanttLayoutSupport.BarRenderMode.FULL_CARD) {
                labelPlaceables.add(
                    labelMeasurable.measure(androidx.compose.ui.unit.Constraints.fixed(0, 0))
                )
            } else {
                labelPlaceables.add(
                    labelMeasurable.measure(
                        androidx.compose.ui.unit.Constraints(
                            minWidth = 0,
                            maxWidth = labelMaxWidthPx,
                            minHeight = 0,
                            maxHeight = rowHeightPx
                        )
                    )
                )
            }
        }

        layout(totalViewWidthPx, totalHeight) {
            for (i in taskComposites.indices) {
                val geom = geometries[i]
                val yPos = (40 + i * 70).dp.roundToPx()
                val barPlaceable = barPlaceables[i]
                barPlaceable.place(x = geom.barStartPx, y = yPos)

                val labelPlaceable = labelPlaceables[i]
                if (geom.mode == GanttLayoutSupport.BarRenderMode.PROPORTIONAL_BAR && labelPlaceable != null) {
                    val placement = GanttLayoutSupport.computeExternalLabelPlacement(
                        geometry = geom,
                        labelWidthPx = labelPlaceable.width,
                        labelGapPx = labelGapPx,
                        dayWidthPx = dayWidthPx,
                        totalViewWidthPx = totalViewWidthPx
                    )
                    val labelY = yPos + ((rowHeightPx - labelPlaceable.height) / 2).coerceAtLeast(0)
                    labelPlaceable.place(x = placement.xPx, y = labelY)
                }
            }
        }
    }
}

@Composable
private fun resolveGanttTaskColors(composite: TaskDetailComposite): Quad<Color, Color, Color, Color> {
    val task = composite.task
    val extendedColors = LocalExtendedColors.current
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

    return when {
        task.isDone -> Quad(
            extendedColors.ganttDoneBackground,
            extendedColors.ganttDoneText,
            extendedColors.ganttDoneText,
            extendedColors.ganttDoneText
        )
        task.isExpired -> Quad(
            baseSurface,
            baseColor,
            baseColor.copy(alpha = 0.72f),
            baseColor.copy(alpha = 0.72f)
        )
        else -> Quad(baseSurface, baseColor, baseColor, baseColor)
    }
}

/**
 * 窄时长任务的精确比例时间条：长度严格对应实际起止区间 [startTime, deadline]。
 */
@Composable
private fun GanttProportionalTaskBar(
    composite: TaskDetailComposite,
    isThinMarker: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val task = composite.task
    val subTasks = composite.subTasks
    val hasSubtasks = subTasks.isNotEmpty()
    val progress = if (hasSubtasks) {
        subTasks.count { it.isCompleted }.toFloat() / subTasks.size.toFloat()
    } else 0f
    val (bg, border, _, fill) = resolveGanttTaskColors(composite)

    // 保留整行 56dp 触控热区，视觉上居中绘制精致的时间条（避免画成“空心大白框 + 文字掉在外面”）
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isThinMarker) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            task.isDone -> fill.copy(alpha = 0.45f)
                            task.isExpired -> fill.copy(alpha = 0.65f)
                            else -> fill.copy(alpha = 0.9f)
                        }
                    )
            )
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp),
                shape = RoundedCornerShape(5.dp),
                color = if (task.isDone) bg else fill.copy(alpha = 0.22f),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, border)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (hasSubtasks) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(fill.copy(alpha = 0.28f))
                                .align(Alignment.CenterStart)
                        )
                        Box(
                            modifier = Modifier
                                .height(2.dp)
                                .fillMaxWidth(progress)
                                .background(fill)
                                .align(Alignment.BottomStart)
                        )
                    }
                    if (task.isExpired && !task.isDone) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Gray.copy(alpha = 0.16f))
                        )
                    }
                }
            }
        }
    }
}

/**
 * 窄时间条旁的无边框可读标签：完整展示标题与起止时间，避免短任务名/时间被省略成 `...` 也不额外撑宽时间条。
 */
@Composable
private fun GanttExternalTaskLabel(
    composite: TaskDetailComposite,
    alignRight: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val task = composite.task
    val subTasks = composite.subTasks
    val hasSubtasks = subTasks.isNotEmpty()
    val progress = if (hasSubtasks) {
        (subTasks.count { it.isCompleted } * 100 / subTasks.size)
    } else 0
    val (_, _, text, _) = resolveGanttTaskColors(composite)
    val timeStr = remember(task.startTime, task.deadline) {
        formatTaskTimeRange(task.startTime, task.deadline)
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = task.title,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = text.copy(alpha = 0.65f),
                modifier = Modifier.size(9.dp)
            )
            Text(
                text = if (hasSubtasks) "$timeStr · $progress%" else timeStr,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.5.sp,
                color = text.copy(alpha = 0.85f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun GanttTaskCard(
    composite: TaskDetailComposite,
    cardWidthDp: Dp = 120.dp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val task = composite.task
    val subTasks = composite.subTasks
    // Calculate Progress
    val hasSubtasks = subTasks.isNotEmpty()
    val progress = if (hasSubtasks) {
        val completed = subTasks.count { it.isCompleted }
        (completed.toFloat() / subTasks.size.toFloat())
    } else {
        0f
    }

    val (bg, border, text, fill) = resolveGanttTaskColors(composite)
    val isUltraCompact = cardWidthDp < 60.dp
    val isCompact = cardWidthDp < 104.dp

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
                    .padding(
                        horizontal = if (isUltraCompact) 4.dp else 6.dp,
                        vertical = if (isCompact) 5.dp else 6.dp
                    ),
                verticalArrangement = if (isUltraCompact) Arrangement.Center else Arrangement.SpaceBetween
            ) {
                // Title
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = if (isUltraCompact) 10.5.sp else if (isCompact) 11.5.sp else 12.sp,
                    lineHeight = if (isUltraCompact) 13.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = text,
                    maxLines = if (isUltraCompact) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!isUltraCompact) {
                    // Bottom Row: Progress % or Icon + Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            if (!hasSubtasks && !isCompact) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = text.copy(alpha = 0.6f),
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            val timeStr = if (isCompact) {
                                formatCompactTaskTimeRange(task.startTime, task.deadline)
                            } else {
                                formatTaskTimeRange(task.startTime, task.deadline)
                            }
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = text.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (hasSubtasks && !isCompact) {
                            Spacer(modifier = Modifier.width(4.dp))
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

private fun formatCompactTaskTimeRange(start: Long, end: Long): String {
    val startCal = Calendar.getInstance().apply { timeInMillis = start }
    val endCal = Calendar.getInstance().apply { timeInMillis = end }

    val sameDay = startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR) &&
        startCal.get(Calendar.DAY_OF_YEAR) == endCal.get(Calendar.DAY_OF_YEAR)

    return if (sameDay) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        "${timeFormat.format(Date(start))}-${timeFormat.format(Date(end))}"
    } else {
        val dateFormat = SimpleDateFormat("M/d", Locale.getDefault())
        "${dateFormat.format(Date(start))}-${dateFormat.format(Date(end))}"
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