package com.litetask.app.ui.components

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.litetask.app.R
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.ui.theme.LocalExtendedColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GanttFullscreenView(
    taskComposites: List<TaskDetailComposite>,
    onTaskClick: (Task) -> Unit,
    initialViewMode: GanttViewMode,
    onBack: () -> Unit
) {
    var viewMode by remember { mutableStateOf(initialViewMode) }
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    
    // 强制横屏显示并设置沉浸式全屏
    DisposableEffect(Unit) {
        val window = activity?.window ?: return@DisposableEffect onDispose {}
        val insetsController = WindowCompat.getInsetsController(window, view)
        
        // 记录原始属性以便恢复
        val originalOrientation = activity.requestedOrientation
        val originalAttributes = window.attributes
        
        // 1. 设置横屏
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        // 2. 启用真正的全屏（允许延伸到刘海屏区域）
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = 
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        // 3. 强制背景和状态栏颜色，防止出现黑边
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.setBackgroundDrawableResource(android.R.color.white)
        
        // 4. 配置 Insets 行为
        WindowCompat.setDecorFitsSystemWindows(window, false)
        insetsController.let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            // 恢复属性
            activity.requestedOrientation = originalOrientation
            
            // 移除全屏 Flag
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            
            // 恢复刘海屏模式
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = 
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
            
            // 恢复系统显示
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            
            // 关键：恢复 DecorFitsSystemWindows，但为了防止位移，需要显式重置窗口颜色
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.statusBarColor = android.graphics.Color.WHITE
            window.navigationBarColor = android.graphics.Color.WHITE
        }
    }
    
    // 处理系统返回键
    BackHandler {
        onBack()
    }
    // 时间配置
    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = now
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val startOfToday = calendar.timeInMillis
    
    // 根据视图模式配置参数（横屏全屏，填满屏幕）
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    val (daysToShow, startOffset) = when (viewMode) {
        GanttViewMode.TODAY -> Pair(1, 0)
        GanttViewMode.THREE_DAY -> Pair(3, 0)
        GanttViewMode.SEVEN_DAY -> Pair(7, -2)
    }
    
    // 横屏全屏：每天的宽度 = 屏幕宽度 / 天数，填满屏幕
    val dayWidth = screenWidth / daysToShow
    
    val startOfView = startOfToday + (startOffset * 24 * 60 * 60 * 1000L)
    val endOfView = startOfView + (daysToShow * 24 * 60 * 60 * 1000L)
    
    val visibleTasks = taskComposites.filter { composite ->
        val taskStart = composite.task.startTime
        val taskEnd = composite.task.deadline
        taskStart < endOfView && taskEnd > startOfView
    }.sortedBy { it.task.startTime }
    
    // 全屏界面（无 TopBar，完全沉浸式）
    val extendedColors = LocalExtendedColors.current
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(extendedColors.cardBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            val verticalScrollState = rememberScrollState()
            
            // 日期头部
            Row(
                modifier = Modifier
                    .height(50.dp)
                    .fillMaxWidth()
                    .background(extendedColors.cardBackground.copy(alpha = 0.95f))
                    .border(0.5.dp, extendedColors.ganttGridLine)
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
                            .background(if (isToday) extendedColors.ganttTodayBackground.copy(alpha = 0.4f) else Color.Transparent)
                            .border(width = 0.5.dp, color = extendedColors.ganttGridLine),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isToday) extendedColors.ganttWork else extendedColors.textSecondary
                            )
                            Text(
                                text = dayLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = if (isToday) extendedColors.ganttWork else extendedColors.textTertiary
                            )
                        }
                    }
                }
            }
            
            // 任务区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(verticalScrollState)
            ) {
                GanttGrid(
                    daysToShow = daysToShow,
                    dayWidth = dayWidth,
                    height = max(500.dp, 40.dp + (visibleTasks.size * 70).dp),
                    startOfView = startOfView,
                    now = now,
                    viewMode = viewMode
                )
                
                GanttTasks(
                    taskComposites = visibleTasks,
                    startOfView = startOfView,
                    dayWidth = dayWidth,
                    enabled = false,
                    onTaskClick = onTaskClick
                )
            }
        }
        
        // 返回按钮（左上角，小巧的 Material Design 3 风格）
        SmallFloatingActionButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            containerColor = extendedColors.cardBackground.copy(alpha = 0.95f),
            contentColor = extendedColors.ganttWork,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 3.dp,
                pressedElevation = 6.dp
            )
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = stringResource(R.string.back),
                modifier = Modifier.size(20.dp)
            )
        }
        
        // 顶部操作栏（右上角：图例 + 视图模式切换器）
        var expanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 图例 (仅在横屏非紧凑模式显示，此处全屏全显示)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .background(extendedColors.cardBackground.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .border(0.5.dp, extendedColors.divider.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            ) {
                LegendItem(color = extendedColors.ganttWork, label = stringResource(R.string.task_type_work))
                LegendItem(color = extendedColors.ganttLife, label = stringResource(R.string.task_type_life))
                LegendItem(color = extendedColors.ganttStudy, label = stringResource(R.string.task_type_study))
                LegendItem(color = extendedColors.ganttUrgent, label = stringResource(R.string.task_type_urgent))
            }

            // 切换器
            Box {
                Surface(
                    onClick = { expanded = true },
                    shape = RoundedCornerShape(12.dp),
                    color = extendedColors.ganttWork.copy(alpha = 0.95f),
                    shadowElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = when (viewMode) {
                                GanttViewMode.TODAY -> stringResource(R.string.today)
                                GanttViewMode.THREE_DAY -> stringResource(R.string.three_day_view)
                                GanttViewMode.SEVEN_DAY -> stringResource(R.string.seven_day_view)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(extendedColors.cardBackground, RoundedCornerShape(12.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.today_view), style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            viewMode = GanttViewMode.TODAY
                            expanded = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.three_day_view), style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            viewMode = GanttViewMode.THREE_DAY
                            expanded = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.seven_day_view), style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            viewMode = GanttViewMode.SEVEN_DAY
                            expanded = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    )
                }
            }
        }
    }
}