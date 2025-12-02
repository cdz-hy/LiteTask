package com.litetask.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun VoiceRecorderDialog(
    onDismiss: () -> Unit,
    onFinish: () -> Unit = {},
    recognizedText: String = "",
    recordingDuration: Long = 0L,
    isPlaying: Boolean = false
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0F1115),
                                Color(0xFF181B24),
                                Color(0xFF1E2330)
                            )
                        )
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 核心动画区域
                    Box(contentAlignment = Alignment.Center) {
                        if (!isPlaying) {
                            RippleEffect()
                        }
                        MicrophoneButton(isPlaying = isPlaying)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // 录音时长显示
                    val minutes = (recordingDuration / 1000) / 60
                    val seconds = (recordingDuration / 1000) % 60
                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isPlaying) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isPlaying) {
                        AudioWaveformBar()
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isPlaying) "正在回放..." else "正在录音...",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 识别结果显示区域
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 200.dp)
                            .padding(horizontal = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (recognizedText.isNotBlank()) {
                                Text(
                                    text = recognizedText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Text(
                                    text = "请说出您要添加的任务...\n例如：\"帮我添加明天下午3点开会\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // 顶部关闭按钮
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 24.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 底部操作区
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isPlaying) {
                        Button(
                            onClick = onFinish,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "说完了",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    } else {
                        // 回放中显示提示
                        Text(
                            text = "回放结束后将自动处理...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 麦克风核心组件
 * 使用 Material3 的 Tonal 颜色风格
 */
@Composable
fun MicrophoneButton(isPlaying: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition(label = "icon_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPlaying) 1f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_scale"
    )

    val backgroundColor = if (isPlaying) {
        Brush.linearGradient(
            colors = listOf(Color(0xFF4CAF50), Color(0xFF81C784))
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary
            )
        )
    }

    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(scale)
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                spotColor = if (isPlaying) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
            )
            .background(
                brush = backgroundColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Mic,
            contentDescription = if (isPlaying) "Playing" else "Microphone",
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
    }
}

/**
 * 扩散的波纹效果
 * 优化：透明度随扩散逐渐消失，而非单纯的缩放
 */
@Composable
fun RippleEffect() {
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    
    // 创建两个错开的波纹，增加层次感
    RippleCircle(infiniteTransition, delay = 0)
    RippleCircle(infiniteTransition, delay = 1000)
}

@Composable
fun RippleCircle(transition: InfiniteTransition, delay: Int) {
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = delay, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )

    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = delay, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(scale)
            .alpha(alpha)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                shape = CircleShape
            )
    )
}

/**
 * 模拟声波条形图
 * 这是一个纯视觉效果，模拟语音输入时的动态反馈
 */
@Composable
fun AudioWaveformBar() {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(24.dp)
    ) {
        // 生成 5 个随机跳动的条
        repeat(5) { index ->
            // 每个条使用不同的随机动画参数，看起来更自然
            val duration = remember { Random.nextInt(300, 600) }
            val delay = remember { Random.nextInt(0, 200) }
            
            val heightPercent by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(duration, delayMillis = delay, easing = FastOutLinearInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(heightPercent)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index == 2) MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                    )
            )
        }
    }
}

// Helper modifier for shadow (Material 3 style)
fun Modifier.shadow(
    elevation: Dp,
    shape: androidx.compose.ui.graphics.Shape,
    spotColor: Color
) = this.graphicsLayer {
    this.shadowElevation = elevation.toPx()
    this.shape = shape
    this.spotShadowColor = spotColor
    this.ambientShadowColor = spotColor
}

@Preview(showBackground = true)
@Composable
fun VoiceRecorderDialogPreview() {
    // 强制深色模式预览，因为这是录音界面的常见风格
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFA8C7FA),
            onPrimary = Color(0xFF00315F),
            primaryContainer = Color(0xFF004786),
            onPrimaryContainer = Color(0xFFD6E3FF),
            surface = Color(0xFF1F1F1F),
            onSurface = Color(0xFFE3E3E3)
        )
    ) {
        VoiceRecorderDialog(onDismiss = {})
    }
}