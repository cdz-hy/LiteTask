package com.litetask.app.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardVoice
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language

// AGSL 着色器 - 用于融合效果，避免黑边
@Language("AGSL")
private const val GooeyShaderSource = """
    uniform shader composable;
    uniform float visibility;
    
    half4 main(float2 coord) {
        half4 color = composable.eval(coord);
        // 计算新的 Alpha 值
        float newAlpha = smoothstep(visibility - 0.02, visibility + 0.02, color.a);
        // 避免黑边：当原始 alpha 很小时，保持颜色但用新 alpha
        // 通过除以原始 alpha 来恢复预乘前的颜色，然后用新 alpha 重新预乘
        if (color.a > 0.001) {
            color.rgb = color.rgb / color.a * newAlpha;
        }
        color.a = newAlpha;
        return color;
    }
"""

// 菜单项数据增强：支持自定义颜色
private data class FabMenuItem(
    val icon: ImageVector,
    val label: String,
    val containerColor: Color, // 按钮背景色
    val contentColor: Color,   // 图标颜色
    val onClick: () -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GooeyExpandableFab(
    onVoiceClick: () -> Unit,
    onTextInputClick: () -> Unit,
    onManualInputClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    // 定义颜色：使用 MD3 的 Container 颜色，更加柔和且区分度高
    val colorScheme = MaterialTheme.colorScheme

    val menuItems = remember(colorScheme) {
        listOf(
            // 顶部按钮：文字分析 (使用 Tertiary 色调)
            FabMenuItem(
                icon = Icons.Rounded.Edit,
                label = "文字分析",
                containerColor = colorScheme.tertiaryContainer,
                contentColor = colorScheme.onTertiaryContainer,
                onClick = onTextInputClick
            ),
            // 中间按钮：手动添加 (使用 Secondary 色调)
            FabMenuItem(
                icon = Icons.Rounded.Add,
                label = "手动添加",
                containerColor = colorScheme.secondaryContainer,
                contentColor = colorScheme.onSecondaryContainer,
                onClick = onManualInputClick
            )
        )
    }

    // 主按钮颜色
    val mainContainerColor = colorScheme.primaryContainer
    val mainContentColor = colorScheme.onPrimaryContainer

    // 旋转逻辑修正：
    // 之前旋转45度会导致 Close(X) 变成 Add(+)。
    // 现在旋转 180度，只是自旋一圈，保持 X 的形状，且动效更丰富。
    val mainRotation by animateFloatAsState(
        targetValue = if (isExpanded) -180f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "rotation"
    )

    // 呼吸动画 (仅在未展开时)
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    val actualScale = if (isExpanded) 1f else breathingScale

    // 计算容器高度：根据 item 数量动态预留空间
    // 每个 Item 占用空间约为 70-80dp，加上主按钮
    val containerHeight = 260.dp

    Box(
        modifier = modifier
            .width(88.dp)
            .height(containerHeight)
            .offset(x = 12.dp) // 向右偏移一点
            .padding(bottom = 13.dp), // 底部留出空间，避免融合效果粘连到屏幕底部
        contentAlignment = Alignment.BottomCenter
    ) {
        // ==================== 底层：Gooey 融合动画层 ====================
        // 注意：为了融合效果，底层的 bubble 颜色需要和上层按钮尽量一致

        // 传递给 Gooey 层的参数
        val gooeyParams = Triple(isExpanded, menuItems, actualScale)

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                GooeyLayerApi33(gooeyParams.first, gooeyParams.second, gooeyParams.third, mainContainerColor)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                GooeyLayerApi31(gooeyParams.first, gooeyParams.second, gooeyParams.third, mainContainerColor)
            }
            else -> {
                FallbackLayer(gooeyParams.first, gooeyParams.second, gooeyParams.third, mainContainerColor)
            }
        }

        // ==================== 顶层：交互与视觉层 (清晰层) ====================
        InteractiveLayer(
            isExpanded = isExpanded,
            menuItems = menuItems,
            mainScale = actualScale,
            mainRotation = mainRotation,
            mainContainerColor = mainContainerColor,
            mainContentColor = mainContentColor,
            onMainClick = {
                if (isExpanded) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    isExpanded = false
                } else {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onVoiceClick()
                }
            },
            onMainLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                isExpanded = !isExpanded
            },
            onMenuItemClick = { item ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                isExpanded = false
                item.onClick()
            }
        )
    }
}


// ==================== Android 13+ Gooey ====================

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun GooeyLayerApi33(
    isExpanded: Boolean,
    menuItems: List<FabMenuItem>,
    mainScale: Float,
    mainColor: Color
) {
    val runtimeShader = remember { RuntimeShader(GooeyShaderSource) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // 使用 CLAMP 模式避免边缘黑色，减小模糊半径避免过度扩散
                val blur = RenderEffect.createBlurEffect(33f, 33f, Shader.TileMode.CLAMP)
                runtimeShader.setFloatUniform("visibility", 0.35f) // 稍微提高阈值，减少扩散
                val shader = RenderEffect.createRuntimeShaderEffect(runtimeShader, "composable")
                renderEffect = RenderEffect.createChainEffect(shader, blur).asComposeRenderEffect()
                // 裁剪到容器范围，避免扩散到底部
                clip = true
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        // 渲染子菜单的气泡 (作为融合背景)
        menuItems.forEachIndexed { index, item ->
            // 这里背景层使用 Item 自己的颜色，以便在融合分离时颜色过渡更自然
            GooeyBubble(isExpanded, index, item.containerColor, 56.dp)
        }
        // 主按钮背景
        Box(
            modifier = Modifier
                .scale(mainScale)
                .size(64.dp)
                .background(mainColor, CircleShape)
        )
    }
}

// ==================== Android 12 Gooey ====================

@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun GooeyLayerApi31(
    isExpanded: Boolean,
    menuItems: List<FabMenuItem>,
    mainScale: Float,
    mainColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // 使用 CLAMP 模式避免边缘黑色，减小模糊半径
                val blur = RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
                // 调整矩阵：增强 Alpha 对比度，同时保持 RGB 通道
                // 稍微提升 RGB 亮度来补偿边缘变暗
                val matrix = android.graphics.ColorMatrix(floatArrayOf(
                    1.1f, 0f, 0f, 0f, 10f,
                    0f, 1.1f, 0f, 0f, 10f,
                    0f, 0f, 1.1f, 0f, 10f,
                    0f, 0f, 0f, 28f, -14f // 稍微提高 Alpha 对比度
                ))
                renderEffect = RenderEffect.createChainEffect(
                    RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(matrix)),
                    blur
                ).asComposeRenderEffect()
                // 裁剪到容器范围，避免扩散到底部
                clip = true
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        menuItems.forEachIndexed { index, item ->
            GooeyBubble(isExpanded, index, item.containerColor, 56.dp)
        }
        Box(
            modifier = Modifier
                .scale(mainScale)
                .size(64.dp)
                .background(mainColor, CircleShape)
        )
    }
}

// ==================== 低版本回退 (无融合效果) ====================

@Composable
private fun FallbackLayer(
    isExpanded: Boolean,
    menuItems: List<FabMenuItem>,
    mainScale: Float,
    mainColor: Color
) {
    // 低版本不渲染背景层，直接靠 InteractiveLayer 显示
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 空实现，因为低版本无法实现好看的 Gooey，直接显示顶层按钮即可
    }
}

// ==================== 通用气泡逻辑 ====================

@Composable
private fun GooeyBubble(
    isExpanded: Boolean,
    index: Int,
    color: Color,
    size: Dp
) {
    // 修正：使用 DP 进行间距计算，更加均匀
    // 基础偏移 80dp (主按钮上方)，每个后续按钮增加 72dp
    val baseOffset = 80f
    val itemSpacing = 72f
    val targetOffset = baseOffset + (index * itemSpacing)

    val scope = rememberCoroutineScope()

    val offsetAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(0.2f) } // 初始大小调小一点，藏在主按钮后面

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            // 展开动画：弹簧效果
            scope.launch {
                // 错峰发射
                delay(index * 40L)
                offsetAnim.animateTo(
                    targetOffset,
                    spring(dampingRatio = 0.5f, stiffness = 300f)
                )
            }
            scope.launch {
                delay(index * 40L)
                scaleAnim.animateTo(
                    1f,
                    spring(dampingRatio = 0.6f, stiffness = 200f)
                )
            }
        } else {
            // 收起动画：快速回弹
            scope.launch {
                delay((1 - index) * 30L) // 反向延迟
                offsetAnim.animateTo(
                    0f,
                    spring(dampingRatio = 0.6f, stiffness = 400f)
                )
            }
            scope.launch {
                delay((1 - index) * 30L)
                scaleAnim.animateTo(
                    0.2f, // 收缩到很小
                    spring(dampingRatio = 0.6f, stiffness = 400f)
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .offset(y = (-offsetAnim.value).dp)
            .scale(scaleAnim.value)
            .size(size)
            .background(color, CircleShape)
    )
}


// ==================== 顶层交互层 (UI展示层) ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InteractiveLayer(
    isExpanded: Boolean,
    menuItems: List<FabMenuItem>,
    mainScale: Float,
    mainRotation: Float,
    mainContainerColor: Color,
    mainContentColor: Color,
    onMainClick: () -> Unit,
    onMainLongClick: () -> Unit,
    onMenuItemClick: (FabMenuItem) -> Unit
) {
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // --- 子菜单按钮 ---
        menuItems.forEachIndexed { index, item ->
            // 必须与 GooeyBubble 的逻辑完全一致
            val baseOffset = 80f
            val itemSpacing = 72f
            val targetOffset = baseOffset + (index * itemSpacing)

            val offsetAnim = remember { Animatable(0f) }
            val scaleAnim = remember { Animatable(0f) }
            val alphaAnim = remember { Animatable(0f) }

            LaunchedEffect(isExpanded) {
                if (isExpanded) {
                    val delayMs = index * 40L
                    scope.launch {
                        delay(delayMs)
                        offsetAnim.animateTo(targetOffset, spring(dampingRatio = 0.5f, stiffness = 300f))
                    }
                    scope.launch {
                        delay(delayMs)
                        scaleAnim.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 200f))
                    }
                    scope.launch {
                        delay(delayMs)
                        alphaAnim.animateTo(1f, tween(150))
                    }
                } else {
                    val delayMs = (1 - index) * 30L
                    scope.launch {
                        delay(delayMs)
                        offsetAnim.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 400f))
                    }
                    scope.launch {
                        delay(delayMs)
                        scaleAnim.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 400f))
                    }
                    scope.launch {
                        delay(delayMs)
                        alphaAnim.animateTo(0f, tween(100))
                    }
                }
            }

            Surface(
                onClick = { onMenuItemClick(item) },
                modifier = Modifier
                    .offset(y = (-offsetAnim.value).dp)
                    .scale(scaleAnim.value)
                    .graphicsLayer { alpha = alphaAnim.value } // 整体透明度动画
                    .size(56.dp)
                    // 使用按钮对应颜色的半透明边框，增加渐变效果
                    .border(1.2.dp, item.contentColor.copy(alpha = 0.3f), CircleShape),
                shape = CircleShape,
                // 使用 Item 自己的颜色，并增加一点点透明度打造“透气感”
                color = item.containerColor.copy(alpha = 0.95f),
                contentColor = item.contentColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // --- 主按钮 ---
        Surface(
            modifier = Modifier
                .scale(mainScale)
                .size(64.dp)
                // 使用主按钮对应颜色的半透明边框，增加渐变效果
                .border(1.2.dp, mainContentColor.copy(alpha = 0.3f), CircleShape),
            shape = CircleShape,
            // 适配 Gooey 效果：
            // Android 12+ 依赖底层渲染颜色，顶层可以稍微透明一点点增强层次感
            // 或者直接使用不透明颜色，确保图标清晰
            color = mainContainerColor,
            contentColor = mainContentColor
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(bounded = false, radius = 32.dp, color = mainContentColor),
                        onClick = onMainClick,
                        onLongClick = onMainLongClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isExpanded,
                    transitionSpec = {
                        (fadeIn(tween(200)) + scaleIn(initialScale = 0.8f, animationSpec = tween(200)))
                            .togetherWith(fadeOut(tween(200)) + scaleOut(targetScale = 0.8f, animationSpec = tween(200)))
                    },
                    label = "icon"
                ) { expanded ->
                    Icon(
                        // 明确指定 Close 图标
                        imageVector = if (expanded) Icons.Rounded.Close else Icons.Rounded.Mic,
                        contentDescription = if (expanded) "收起" else "语音输入",
                        // 旋转应用在 Icon 上
                        modifier = Modifier
                            .size(32.dp)
                            .rotate(if (expanded) mainRotation else 0f)
                    )
                }
            }
        }
    }
}