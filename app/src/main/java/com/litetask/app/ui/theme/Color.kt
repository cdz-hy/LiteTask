package com.litetask.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

// 其他辅助颜色
val Indigo600 = Color(0xFF4F46E5)
val Indigo100 = Color(0xFFE0E7FF)
val Blue600 = Color(0xFF2563EB)
val Green500 = Color(0xFF22C55E)
val Orange500 = Color(0xFFF97316)
val Purple500 = Color(0xFFA855F7)

/**
 * LiteTask 颜色工具类
 * 
 * 注意：这些颜色仅用于向后兼容，新代码应使用 LocalExtendedColors
 * 以支持夜间模式自动切换
 */
object LiteTaskColors {
    // 任务类型主色 - 亮色模式默认值
    @Composable
    fun workTask() = LocalExtendedColors.current.workTask
    
    @Composable
    fun lifeTask() = LocalExtendedColors.current.lifeTask
    
    @Composable
    fun studyTask() = LocalExtendedColors.current.studyTask
    
    @Composable
    fun urgentTask() = LocalExtendedColors.current.urgentTask
    
    // 任务类型表面色（用于卡片背景）
    @Composable
    fun workTaskSurface() = LocalExtendedColors.current.workTaskSurface
    
    @Composable
    fun lifeTaskSurface() = LocalExtendedColors.current.lifeTaskSurface
    
    @Composable
    fun studyTaskSurface() = LocalExtendedColors.current.studyTaskSurface
    
    @Composable
    fun urgentTaskSurface() = LocalExtendedColors.current.urgentTaskSurface
}
