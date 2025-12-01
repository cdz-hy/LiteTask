package com.litetask.app.ui.components

import android.app.Activity
import android.content.pm.ActivityInfo
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskDetailComposite
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
    
    // 强制横屏显示并设置沉浸式全屏
    DisposableEffect(Unit) {
        // 设置横屏
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        // 使用 WindowCompat 设置沉浸式全屏
        activity?.window?.let { window ->
            // 让内容延伸到系统栏下方
            WindowCompat.setDecorFitsSystemWindows(window, false)
            
            // 获取 WindowInsetsController
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.apply {
                // 隐藏状态栏和导航栏
                hide(WindowInsetsCompat.Type.systemBars())
                // 设置行为：滑动时临时显示
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            
            // 设置系统栏颜色为透明
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        
        onDispose {
            // 恢复竖屏
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            
            // 恢复系统 UI
            activity?.window?.let { window ->
                // 恢复正常布局
                WindowCompat.setDecorFitsSystemWindows(window, true)
                
                // 显示系统栏
                val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                
                // 恢复原始颜色
                window.statusBarColor = android.graphics.Color.WHITE
                window.navigationBarColor = android.graphics.Color.WHITE
            }
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
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
                    .background(Color.White.copy(alpha = 0.95f))
                    .border(0.5.dp, Color(0xFFF0F0F0))
            ) {
                for (i in 0 until daysToShow) {
                    val dayCal = Calendar.getInstance()
                    dayCal.timeInMillis = startOfView
                    dayCal.add(Calendar.DAY_OF_YEAR, i)
                    
                    val isToday = dayCal.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR) &&
                                 dayCal.get(Calendar.DAY_OF_YEAR) == Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                    
                    val dateStr = SimpleDateFormat("M/d", Locale.getDefault()).format(dayCal.time)
                    val dayLabel = when {
                        isToday -> "今天"
                        viewMode == GanttViewMode.TODAY -> SimpleDateFormat("EEEE", Locale.getDefault()).format(dayCal.time)
                        else -> SimpleDateFormat("EEE", Locale.getDefault()).format(dayCal.time)
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
                                color = if (isToday) Color(0xFF0B57D0) else Color(0xFF4B5563)
                            )
                            Text(
                                text = dayLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = if (isToday) Color(0xFF0B57D0) else Color(0xFF9CA3AF)
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
            containerColor = Color.White.copy(alpha = 0.95f),
            contentColor = Color(0xFF0B57D0),
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 3.dp,
                pressedElevation = 6.dp
            )
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "返回",
                modifier = Modifier.size(20.dp)
            )
        }
        
        // 视图模式切换器（右上角）
        var expanded by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0B57D0).copy(alpha = 0.95f),
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = when (viewMode) {
                            GanttViewMode.TODAY -> "今日"
                            GanttViewMode.THREE_DAY -> "3日"
                            GanttViewMode.SEVEN_DAY -> "7日"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                }
            }
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color.White, RoundedCornerShape(12.dp))
            ) {
                DropdownMenuItem(
                    text = { Text("今日视图", style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        viewMode = GanttViewMode.TODAY
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                )
                DropdownMenuItem(
                    text = { Text("3日视图", style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        viewMode = GanttViewMode.THREE_DAY
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                )
                DropdownMenuItem(
                    text = { Text("7日视图", style = MaterialTheme.typography.bodyMedium) },
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
