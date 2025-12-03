package com.litetask.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

// --- 视觉风格常量 ---
private val RecorderBackgroundTop = Color(0xFF1A1C1E)
private val RecorderBackgroundBottom = Color(0xFF2F343B)
private val PrimaryAccent = Color(0xFFD0BCFF) // MD3 Primary Tone
private val TextPrimary = Color(0xFFE6E1E5)
private val TextSecondary = Color(0xFFCAC4D0)

@Composable
fun VoiceRecorderDialog(
    onDismiss: () -> Unit,
    onStopRecording: () -> Unit = {}, // 停止录音，进入确认状态
    onFinish: (String) -> Unit = {}, // 确认添加，传递编辑后的文本
    recognizedText: String = "",
    recordingDuration: Long = 0L,
    isPlaying: Boolean = false, // 复用作为 "AI 分析中" 的状态
    isRecording: Boolean = true // 是否正在录音
) {
    // 可编辑的文本状态
    var editableText by remember(recognizedText) { mutableStateOf(recognizedText) }
    var isEditing by remember { mutableStateOf(false) }
    
    // 当 recognizedText 更新时同步
    LaunchedEffect(recognizedText) {
        if (!isEditing) {
            editableText = recognizedText
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(RecorderBackgroundTop, RecorderBackgroundBottom)
                    )
                )
        ) {
            // 1. 顶部关闭按钮
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 24.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 2. 核心内容区域
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 状态标题
                val titleState = when {
                    isPlaying -> "Analyzing"
                    !isRecording && editableText.isNotEmpty() -> "Result"
                    isRecording && recognizedText.isNotEmpty() -> "Recognizing"
                    else -> "Listening"
                }
                
                AnimatedContent(
                    targetState = titleState,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                    },
                    label = "TitleAnimation"
                ) { state ->
                    Text(
                        text = when (state) {
                            "Listening" -> "正在聆听..."
                            "Recognizing" -> "实时识别中..."
                            "Analyzing" -> "AI 正在分析..."
                            else -> "识别结果"
                        },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 计时器 (录音时显示)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isRecording && !isPlaying,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Text(
                        text = formatDuration(recordingDuration),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        color = TextSecondary.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))


                // 核心可视化区域
                androidx.compose.animation.AnimatedVisibility(
                    visible = isRecording || isPlaying,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(160.dp)
                    ) {
                        when {
                            isPlaying -> {
                                CircularProgressIndicator(
                                    color = PrimaryAccent,
                                    strokeWidth = 4.dp,
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                            else -> {
                                ActiveListeningVisualizer()
                            }
                        }
                    }
                }

                // 实时识别文本显示（录音中）
                androidx.compose.animation.AnimatedVisibility(
                    visible = isRecording && recognizedText.isNotEmpty() && !isPlaying,
                    enter = fadeIn() + slideInVertically { 30 },
                    exit = fadeOut()
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "实时识别",
                                style = MaterialTheme.typography.labelSmall,
                                color = PrimaryAccent
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = recognizedText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // 识别完成后的可编辑文本区域
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isRecording && editableText.isNotEmpty() && !isPlaying,
                    enter = fadeIn() + slideInVertically { 50 },
                    exit = fadeOut()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // 完成图标
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color(0xFF81C784),
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color(0xFF81C784).copy(alpha = 0.2f), CircleShape)
                                .padding(12.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // 可编辑文本框
                        OutlinedTextField(
                            value = editableText,
                            onValueChange = { 
                                editableText = it
                                isEditing = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 200.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = TextPrimary,
                                lineHeight = 26.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryAccent,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                cursorColor = PrimaryAccent,
                                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            placeholder = {
                                Text(
                                    "点击编辑识别结果...",
                                    color = TextSecondary.copy(alpha = 0.5f)
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "可点击上方文本框进行修改",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary.copy(alpha = 0.5f)
                        )
                    }
                }

                // 录音中的空状态提示
                if (recognizedText.isEmpty() && isRecording && !isPlaying) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "试试说: \"13号上午十点报告要截止了...\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
                
                // 录音结束但没有识别到文字的空状态
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isRecording && editableText.isEmpty() && !isPlaying,
                    enter = fadeIn() + slideInVertically { 50 },
                    exit = fadeOut()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color(0xFFFF8A80),
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFFFF8A80).copy(alpha = 0.1f), CircleShape)
                                .padding(16.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "未能识别到语音",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "请确保麦克风正常工作，并清晰地说出内容",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }


            // 3. 底部操作按钮区域
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp, start = 32.dp, end = 32.dp)
                    .fillMaxWidth()
            ) {
                when {
                    // AI 分析中：不显示按钮
                    isPlaying -> {}
                    
                    // 识别完成且有文字：显示确认按钮
                    !isRecording && editableText.isNotEmpty() -> {
                        Button(
                            onClick = { onFinish(editableText) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .glowShadow(PrimaryAccent),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryAccent,
                                contentColor = Color(0xFF381E72)
                            ),
                            shape = CircleShape
                        ) {
                            Text("确认添加", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    // 录音结束但没有识别到文字：显示关闭按钮
                    !isRecording && editableText.isEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "未识别到语音内容",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFFF8A80),
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = TextSecondary
                                ),
                                shape = CircleShape
                            ) {
                                Text("关闭")
                            }
                        }
                    }
                    
                    // 录音中：显示完成按钮
                    isRecording -> {
                        Button(
                            onClick = onStopRecording,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.15f),
                                contentColor = TextPrimary
                            ),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Filled.GraphicEq, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("我说完了", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

// --- 动态声波组件 ---
@Composable
fun ActiveListeningVisualizer() {
    Box(contentAlignment = Alignment.Center) {
        // 背景光晕
        Box(
            modifier = Modifier
                .size(140.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(PrimaryAccent.copy(alpha = 0.2f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        // 动态条形波纹
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.height(60.dp)
        ) {
            repeat(5) { index ->
                VoiceBar(index)
            }
        }
    }
}

@Composable
fun VoiceBar(index: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "VoiceBar")

    val duration = remember { 400 + index * 100 }
    val initialDelay = remember { index * 150 }

    val heightScale by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, delayMillis = initialDelay, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "height"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, delayMillis = initialDelay),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .width(8.dp)
            .fillMaxHeight(heightScale)
            .clip(RoundedCornerShape(50))
            .background(PrimaryAccent.copy(alpha = alpha))
    )
}

// --- 辅助函数 ---

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000) / 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

// 自定义发光阴影效果
fun Modifier.glowShadow(color: Color) = this.graphicsLayer {
    shadowElevation = 8.dp.toPx()
    shape = CircleShape
    spotShadowColor = color
    ambientShadowColor = color
}

@Preview(showBackground = true)
@Composable
fun PreviewRecorderListening() {
    VoiceRecorderDialog(
        onDismiss = {},
        recordingDuration = 12000L,
        isRecording = true
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewRecorderResult() {
    VoiceRecorderDialog(
        onDismiss = {},
        recognizedText = "明天下午三点开会讨论项目进度",
        isRecording = false
    )
}
