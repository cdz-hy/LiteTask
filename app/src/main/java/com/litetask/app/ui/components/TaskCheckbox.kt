package com.litetask.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * MD3 风格的任务复选框，包含极其丝滑的动效
 *
 * @param isDone 任务是否已完成
 * @param onCheckedChange 点击后的回调
 * @param checkColor 勾选后的颜色（主色）
 * @param size 复选框尺寸
 */
@Composable
fun TaskCheckbox(
    isDone: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    checkColor: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 24.dp,
    isGrayedOut: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 稳定的颜色状态，避免重组时颜色闪烁
    val stableCheckColor = remember(checkColor, isGrayedOut) {
        if (isGrayedOut) checkColor.copy(alpha = 0.6f) else checkColor
    }
    
    // 缩放动效：使用更稳定的动画参数
    val scale by animateFloatAsState(
        targetValue = if (isDone) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "checkbox_scale"
    )

    // 背景色过渡：使用稳定的颜色引用
    val backgroundColor by animateColorAsState(
        targetValue = if (isDone) stableCheckColor else Color.Transparent,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "checkbox_background"
    )

    // 边框色过渡：使用稳定的颜色引用
    val borderColor by animateColorAsState(
        targetValue = if (isDone) stableCheckColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "checkbox_border"
    )

    // 勾选项透明度：简化动画逻辑
    val checkAlpha by animateFloatAsState(
        targetValue = if (isDone) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "check_alpha"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(
                width = if (isDone) 0.dp else 2.dp,
                color = borderColor,
                shape = CircleShape
            )
            .clickable { onCheckedChange(!isDone) }
            .padding(2.dp)
            .then(if (isGrayedOut) Modifier.graphicsLayer(alpha = 0.7f) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        // 简化勾选图标显示逻辑
        androidx.compose.animation.AnimatedVisibility(
            visible = isDone,
            enter = androidx.compose.animation.scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            exit = androidx.compose.animation.scaleOut(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (isGrayedOut) Color.LightGray else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(size * 0.7f)
            )
        }
    }
}
