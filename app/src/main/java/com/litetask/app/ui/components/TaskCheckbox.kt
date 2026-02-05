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
import androidx.compose.material3.Surface
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
    // 稳定的颜色状态
    val stableCheckColor = remember(checkColor, isGrayedOut) {
        if (isGrayedOut) checkColor.copy(alpha = 0.6f) else checkColor
    }
    
    // 缩放动效
    val scale by animateFloatAsState(
        targetValue = if (isDone) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "checkbox_scale"
    )

    // 背景色
    val backgroundColor by animateColorAsState(
        targetValue = if (isDone) stableCheckColor else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "checkbox_background"
    )

    // 边框色
    val borderColor by animateColorAsState(
        targetValue = if (isDone) stableCheckColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "checkbox_border"
    )

    // 勾选项图标颜色
    val iconColor = if (isGrayedOut) Color.LightGray else MaterialTheme.colorScheme.onPrimary

    // 使用 Surface 替代 Box 组合，强制裁剪
    Surface(
        onClick = { onCheckedChange(!isDone) },
        shape = CircleShape,
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor).takeIf { !isDone },
        modifier = modifier
            .size(size)
            .scale(scale)
            .then(if (isGrayedOut) Modifier.graphicsLayer(alpha = 0.7f) else Modifier)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isDone,
                enter = androidx.compose.animation.scaleIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
                exit = androidx.compose.animation.scaleOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(size * 0.7f)
                )
            }
        }
    }
}
