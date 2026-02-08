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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.litetask.app.R
import java.util.Locale

@Composable
fun VoiceRecorderDialog(
    onDismiss: () -> Unit,
    onStopRecording: () -> Unit = {}, // 停止录音，进入确认状态
    onFinish: (String) -> Unit = {}, // 确认添加，传递编辑后的文本
    recognizedText: String = "",
    recordingDuration: Long = 0L,
    isPlaying: Boolean = false, // 复用作为 "AI 分析中" 的状态
    isRecording: Boolean = true, // 是否正在录音
    speechSourceName: String = "Android STT" // 语音识别源名称
) {
    // 可编辑的文本状态
    var editableText by remember { mutableStateOf(recognizedText) }
    var isEditing by remember { mutableStateOf(false) }
    
    // 当 recognizedText 更新时同步到 editableText
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
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 1. 顶部关闭按钮
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 24.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
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
                                "Listening" -> stringResource(R.string.voice_listening)
                                "Recognizing" -> stringResource(R.string.voice_recognizing)
                                "Analyzing" -> stringResource(R.string.voice_analyzing)
                                else -> stringResource(R.string.voice_result)
                            },
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 计时器 (录音时显示)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isRecording && !isPlaying,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formatDuration(recordingDuration),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // 语音识别源显示
                            Text(
                                text = stringResource(R.string.voice_source_hint, speechSourceName),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
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
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 4.dp,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                                else -> {
                                    ActiveListeningVisualizer(MaterialTheme.colorScheme.primary)
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
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.voice_realtime_recognition),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = recognizedText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
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
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                    .padding(12.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // 可编辑文本框
                            OutlinedTextField(
                                value = editableText,
                                onValueChange = { 
                                    editableText = it
                                    isEditing = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 240.dp),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 24.sp
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                shape = RoundedCornerShape(24.dp),
                                placeholder = {
                                    Text(
                                        stringResource(R.string.voice_edit_placeholder),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.voice_edit_hint),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // 录音中的空状态提示
                    if (recognizedText.isEmpty() && isRecording && !isPlaying) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.voice_example_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
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
                            modifier = Modifier.padding(top = 24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                                    .padding(16.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = stringResource(R.string.voice_no_recognition),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = stringResource(R.string.voice_check_microphone),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }


                // 3. 底部操作按钮区域
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 32.dp, end = 32.dp, bottom = 48.dp)
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
                                    .graphicsLayer {
                                        shadowElevation = 4.dp.toPx()
                                        shape = CircleShape
                                        spotShadowColor = Color.Black
                                        ambientShadowColor = Color.Black
                                    },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = CircleShape
                            ) {
                                Text(
                                    stringResource(R.string.voice_confirm_add),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        // 录音结束但没有识别到文字：显示关闭按钮
                        !isRecording && editableText.isEmpty() -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.voice_no_content),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    shape = CircleShape
                                ) {
                                    Text(stringResource(R.string.voice_close))
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
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = CircleShape
                            ) {
                                Icon(Icons.Filled.GraphicEq, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.voice_finish),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 动态声波组件 ---
@Composable
fun ActiveListeningVisualizer(primaryAccent: Color) {
    Box(contentAlignment = Alignment.Center) {
        // 背景光晕
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryAccent.copy(alpha = 0.15f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        // 动态条形波纹
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(64.dp)
        ) {
            repeat(5) { index ->
                VoiceBar(index, primaryAccent)
            }
        }
    }
}

@Composable
fun VoiceBar(index: Int, primaryAccent: Color) {
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
            .width(6.dp)
            .fillMaxHeight(heightScale)
            .clip(RoundedCornerShape(3.dp))
            .background(primaryAccent.copy(alpha = alpha))
    )
}

// --- 辅助函数 ---

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000) / 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

@Preview(showBackground = true)
@Composable
fun PreviewRecorderListening() {
    VoiceRecorderDialog(
        onDismiss = {},
        recordingDuration = 12000L,
        isRecording = true,
        speechSourceName = "Android STT"
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewRecorderResult() {
    VoiceRecorderDialog(
        onDismiss = {},
        recognizedText = "明天下午三点开会讨论项目进度",
        isRecording = false,
        speechSourceName = "讯飞语音转写"
    )
}
