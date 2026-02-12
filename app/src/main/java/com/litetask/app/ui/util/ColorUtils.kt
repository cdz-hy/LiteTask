package com.litetask.app.ui.util

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color

object ColorUtils {
    fun parseColor(hex: String, default: Color = Color.Gray): Color {
        return try {
            Color(AndroidColor.parseColor(hex))
        } catch (e: Exception) {
            default
        }
    }

    // 生成背景色 (低饱和度，高亮度)
    fun getSurfaceColor(primary: Color): Color {
        // 简单算法：混合白色
        // 90% 白色 + 10% 主色
        return Color(
            red = 0.9f + primary.red * 0.1f,
            green = 0.9f + primary.green * 0.1f,
            blue = 0.9f + primary.blue * 0.1f,
            alpha = 1f
        )
    }
}
