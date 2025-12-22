package com.litetask.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 扩展颜色系统 - 用于 Material Design 3 标准颜色之外的自定义颜色
 * 支持亮色/暗色模式自动切换
 */
data class ExtendedColors(
    // 任务类型主色
    val workTask: Color,
    val lifeTask: Color,
    val studyTask: Color,
    val urgentTask: Color,
    
    // 任务类型表面色（卡片背景）
    val workTaskSurface: Color,
    val lifeTaskSurface: Color,
    val studyTaskSurface: Color,
    val urgentTaskSurface: Color,
    
    // 甘特图颜色
    val ganttWork: Color,
    val ganttLife: Color,
    val ganttStudy: Color,
    val ganttUrgent: Color,
    val ganttDoneBackground: Color,
    val ganttDoneText: Color,
    val ganttWorkBackground: Color,
    val ganttLifeBackground: Color,
    val ganttStudyBackground: Color,
    val ganttUrgentBackground: Color,
    val ganttWorkBorder: Color,
    val ganttLifeBorder: Color,
    val ganttStudyBorder: Color,
    val ganttUrgentBorder: Color,
    val ganttGridLine: Color,
    val ganttHourLine: Color,
    val ganttCurrentTime: Color,
    val ganttTodayBackground: Color,
    
    // 截止视图颜色
    val deadlineUrgent: Color,
    val deadlineSoon: Color,
    val deadlineFuture: Color,
    val deadlineUrgentSurface: Color,
    val deadlineSoonSurface: Color,
    val deadlineFutureSurface: Color,
    
    // 通用颜色
    val cardBackground: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color
)

// 亮色模式扩展颜色
private val LightExtendedColors = ExtendedColors(
    // 任务类型主色
    workTask = Color(0xFF0B57D0),
    lifeTask = Color(0xFF146C2E),
    studyTask = Color(0xFF65558F),
    urgentTask = Color(0xFFB3261E),
    
    // 任务类型表面色
    workTaskSurface = Color(0xFFEFF6FF),
    lifeTaskSurface = Color(0xFFECFDF5),
    studyTaskSurface = Color(0xFFF5F3FF),
    urgentTaskSurface = Color(0xFFFEF2F2),
    
    // 甘特图颜色
    ganttWork = Color(0xFF0B57D0),
    ganttLife = Color(0xFF146C2E),
    ganttStudy = Color(0xFF65558F),
    ganttUrgent = Color(0xFFB3261E),
    ganttDoneBackground = Color(0xFFE0E0E0),
    ganttDoneText = Color(0xFF9E9E9E),
    ganttWorkBackground = Color(0xFFEFF6FF),
    ganttLifeBackground = Color(0xFFECFDF5),
    ganttStudyBackground = Color(0xFFF5F3FF),
    ganttUrgentBackground = Color(0xFFFEF2F2),
    ganttWorkBorder = Color(0xFFBFDBFE),
    ganttLifeBorder = Color(0xFFA7F3D0),
    ganttStudyBorder = Color(0xFFDDD6FE),
    ganttUrgentBorder = Color(0xFFFECACA),
    ganttGridLine = Color(0xFFE5E7EB),
    ganttHourLine = Color(0xFFF3F4F6),
    ganttCurrentTime = Color(0xFFEF4444),
    ganttTodayBackground = Color(0xFFEFF6FF),
    
    // 截止视图颜色
    deadlineUrgent = Color(0xFFEF4444),
    deadlineSoon = Color(0xFFFF9800),
    deadlineFuture = Color(0xFF10B981),
    deadlineUrgentSurface = Color(0xFFFEF2F2),
    deadlineSoonSurface = Color(0xFFFFF3E0),
    deadlineFutureSurface = Color(0xFFF0FDF4),
    
    // 通用颜色
    cardBackground = Color(0xFFFFFFFF),
    divider = Color(0xFFE0E0E0),
    textPrimary = Color(0xFF1F1F1F),
    textSecondary = Color(0xFF444746),
    textTertiary = Color(0xFF747775)
)

// 暗色模式扩展颜色
private val DarkExtendedColors = ExtendedColors(
    // 任务类型主色 - 使用更亮的色调
    workTask = Color(0xFFA8C7FA),
    lifeTask = Color(0xFF81C995),
    studyTask = Color(0xFFCFBCFF),
    urgentTask = Color(0xFFF2B8B5),
    
    // 任务类型表面色 - 使用深色背景
    workTaskSurface = Color(0xFF1E3A5F),
    lifeTaskSurface = Color(0xFF1E3B2F),
    studyTaskSurface = Color(0xFF2D2640),
    urgentTaskSurface = Color(0xFF3D2020),
    
    // 甘特图颜色
    ganttWork = Color(0xFFA8C7FA),
    ganttLife = Color(0xFF81C995),
    ganttStudy = Color(0xFFCFBCFF),
    ganttUrgent = Color(0xFFF2B8B5),
    ganttDoneBackground = Color(0xFF3C3C3C),
    ganttDoneText = Color(0xFF8E8E8E),
    ganttWorkBackground = Color(0xFF1E3A5F),
    ganttLifeBackground = Color(0xFF1E3B2F),
    ganttStudyBackground = Color(0xFF2D2640),
    ganttUrgentBackground = Color(0xFF3D2020),
    ganttWorkBorder = Color(0xFF3D5A80),
    ganttLifeBorder = Color(0xFF3D6B50),
    ganttStudyBorder = Color(0xFF4D4670),
    ganttUrgentBorder = Color(0xFF6D4040),
    ganttGridLine = Color(0xFF3C3C3C),
    ganttHourLine = Color(0xFF2C2C2C),
    ganttCurrentTime = Color(0xFFFF6B6B),
    ganttTodayBackground = Color(0xFF1E3A5F),
    
    // 截止视图颜色
    deadlineUrgent = Color(0xFFFF6B6B),
    deadlineSoon = Color(0xFFFFB74D),
    deadlineFuture = Color(0xFF4DB6AC),
    deadlineUrgentSurface = Color(0xFF3D2020),
    deadlineSoonSurface = Color(0xFF3D3020),
    deadlineFutureSurface = Color(0xFF1E3B2F),
    
    // 通用颜色
    cardBackground = Color(0xFF2B2930),
    divider = Color(0xFF49454F),
    textPrimary = Color(0xFFE6E1E5),
    textSecondary = Color(0xFFCAC4D0),
    textTertiary = Color(0xFF938F99)
)

// CompositionLocal 用于提供扩展颜色
val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

// Material Design 3 暗色方案 - 完整定义
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFDEBCDF),
    onTertiary = Color(0xFF402843),
    tertiaryContainer = Color(0xFF583E5B),
    onTertiaryContainer = Color(0xFFFBD7FC),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F)
)

// Material Design 3 亮色方案
private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    surface = Surface,
    background = Background,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant
)

@Composable
fun LiteTaskTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    // 选择扩展颜色
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
