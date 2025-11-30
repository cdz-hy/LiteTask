package com.litetask.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.runtime.Composable

// Material Design 3 标准颜色
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// LiteTask 自定义颜色 (参考原型)
val Primary = Color(0xFF0B57D0)          // Google Blue 主色
val OnPrimary = Color(0xFFFFFFFF)        // 主色上的文字
val PrimaryContainer = Color(0xFFD3E3FD) // 主色容器
val OnPrimaryContainer = Color(0xFF041E49) // 主色容器上的文字

val SecondaryContainer = Color(0xFFC2E7FF) // 次色容器
val OnSecondaryContainer = Color(0xFF001D35) // 次色容器上的文字

val TertiaryContainer = Color(0xFFFFD8E4) // 第三色容器
val OnTertiaryContainer = Color(0xFF31111D) // 第三色容器上的文字

val ErrorContainer = Color(0xFFF9DEDC) // 错误容器
val OnErrorContainer = Color(0xFF410E0B) // 错误容器上的文字

val Surface = Color(0xFFFFFFFF)         // 表面颜色
val SurfaceContainer = Color(0xFFEEF2F6) // 表面容器颜色
val Background = Color(0xFFF2F6FC)      // 背景颜色

val OnSurface = Color(0xFF1F1F1F)       // 表面文字颜色
val OnSurfaceVariant = Color(0xFF444746) // 表面变体文字颜色
val Outline = Color(0xFF747775)         // 轮廓线颜色
val OutlineVariant = Color(0xFFC4C7C5)  // 轮廓线变体颜色

// 任务类型颜色
val WorkTask = Color(0xFF0B57D0)
val LifeTask = Color(0xFF001D35)
val DevTask = Color(0xFF31111D)
val HealthTask = Color(0xFF410E0B)

// 其他辅助颜色
val Indigo600 = Color(0xFF4F46E5)
val Indigo100 = Color(0xFFE0E7FF)
val Blue600 = Color(0xFF2563EB)
val Green500 = Color(0xFF22C55E)
val Orange500 = Color(0xFFF97316)
val Purple500 = Color(0xFFA855F7)

/**
 * 从资源文件获取颜色的 Composable 函数
 */
object LiteTaskColors {
    @Composable
    fun workTask() = Color(0xFF0B57D0)
    
    @Composable
    fun lifeTask() = Color(0xFF146C2E)
    
    @Composable
    fun studyTask() = Color(0xFF65558F)
    
    @Composable
    fun urgentTask() = Color(0xFFB3261E)
    
    @Composable
    fun healthTask() = Color(0xFF006D44)
    
    @Composable
    fun devTask() = Color(0xFFE65100)
    
    // 浅色背景 (用于置顶卡片背景)
    @Composable
    fun workTaskSurface() = Color(0xFFEFF6FF)   // Blue-50
    
    @Composable
    fun lifeTaskSurface() = Color(0xFFF0FDF4)   // Green-50
    
    @Composable
    fun studyTaskSurface() = Color(0xFFF5F3FF)  // Violet-50
    
    @Composable
    fun urgentTaskSurface() = Color(0xFFFEF2F2) // Red-50
    
    @Composable
    fun healthTaskSurface() = Color(0xFFF0FDF4) // Emerald-50
    
    @Composable
    fun devTaskSurface() = Color(0xFFFFF7ED)    // Orange-50
}