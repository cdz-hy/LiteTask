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
import androidx.compose.ui.res.colorResource
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
    // 注意：recognizedText 来自 Provider，已经是完整的累积文本，不需要在这里拼接
    // 只在用户开始编辑后才停止同步，避免覆盖用户的修改
    var editableText by remember { mutableStateOf(recognizedText) }
    var isEditing by remember { mutableStateOf(false) }
    
    // 颜色资源
    val bgTop = colorResource(R.color.voice_recorder_bg_top)
    val bgBottom = colorResource(R.color.voice_recorder_bg_bottom)
    val primaryAccent = colorResource(R.color.voice_recorder_primary)
    val textPrimary = colorResource(R.color.voice_recorder_text_primary)
    val textSecondary = colorResource(R.color.voice_recorder_text_secondary)
    val successColor = colorResource(R.color.voice_recorder_success)
    val errorColor = colorResource(R.color.voice_recorder_error)
    val buttonBg = colorResource(R.color.voice_recorder_button_bg)
    
    // 当 recognizedText 更新时同步到 editableText
    // Provider 返回的是完整文本，直接替换即可，不要拼接
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
                        colors = listOf(bgTop, bgBottom)
                    )
                )
        ) {
            // 1. 顶部关闭按钮
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = dimensionResource(R.dimen.voice_recorder_close_button_top),
                        end = dimensionResource(R.dimen.voice_recorder_close_button_end)
                    )
                    .background(colorResource(R.color.white_transparent_10), CircleShape)
                    .size(dimensionResource(R.dimen.voice_recorder_close_button_size))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = textPrimary,
                    modifier = Modifier.size(dimensionResource(R.dimen.voice_recorder_close_icon_size))
                )
            }

            // 2. 核心内容区域
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = dimensionResource(R.dimen.voice_recorder_content_padding)),
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
                            letterSpacing = dimensionResource(R.dimen.voice_recorder_title_letter_spacing).value.sp
                        ),
                        color = textPrimary,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.voice_recorder_title_spacing)))

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
                            color = textSecondary.copy(alpha = 0.8f)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 语音识别源显示
                        Text(
                            text = stringResource(R.string.voice_source_hint, speechSourceName),
                            style = MaterialTheme.typography.labelSmall,
                            color = textSecondary.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.voice_recorder_section_spacing)))


                // 核心可视化区域
                androidx.compose.animation.AnimatedVisibility(
                    visible = isRecording || isPlaying,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(dimensionResource(R.dimen.voice_recorder_visualizer_size))
                    ) {
                        when {
                            isPlaying -> {
                                CircularProgressIndicator(
                                    color = primaryAccent,
                                    strokeWidth = dimensionResource(R.dimen.voice_recorder_progress_stroke),
                                    modifier = Modifier.size(dimensionResource(R.dimen.voice_recorder_progress_size))
                                )
                            }
                            else -> {
                                ActiveListeningVisualizer(primaryAccent)
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
                        color = colorResource(R.color.white_transparent_8),
                        shape = RoundedCornerShape(dimensionResource(R.dimen.voice_recorder_card_corner)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = dimensionResource(R.dimen.voice_recorder_card_vertical_padding))
                    ) {
                        Column(
                            modifier = Modifier.padding(dimensionResource(R.dimen.voice_recorder_card_padding)),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.voice_realtime_recognition),
                                style = MaterialTheme.typography.labelSmall,
                                color = primaryAccent
                            )
                            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_small)))
                            Text(
                                text = recognizedText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textPrimary,
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
                            tint = successColor,
                            modifier = Modifier
                                .size(dimensionResource(R.dimen.voice_recorder_icon_size))
                                .background(successColor.copy(alpha = 0.2f), CircleShape)
                                .padding(dimensionResource(R.dimen.voice_recorder_icon_padding))
                        )
                        
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.voice_recorder_icon_spacing)))
                        
                        // 可编辑文本框
                        OutlinedTextField(
                            value = editableText,
                            onValueChange = { 
                                editableText = it
                                isEditing = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(
                                    min = dimensionResource(R.dimen.voice_recorder_text_field_min_height),
                                    max = dimensionResource(R.dimen.voice_recorder_text_field_max_height)
                                ),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = textPrimary,
                                lineHeight = dimensionResource(R.dimen.voice_recorder_text_line_height).value.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryAccent,
                                unfocusedBorderColor = colorResource(R.color.white_transparent_20),
                                cursorColor = primaryAccent,
                                focusedContainerColor = colorResource(R.color.white_transparent_5),
                                unfocusedContainerColor = colorResource(R.color.white_transparent_5)
                            ),
                            shape = RoundedCornerShape(dimensionResource(R.dimen.voice_recorder_card_corner)),
                            placeholder = {
                                Text(
                                    stringResource(R.string.voice_edit_placeholder),
                                    color = textSecondary.copy(alpha = 0.5f)
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.voice_recorder_hint_spacing)))

                        Text(
                            text = stringResource(R.string.voice_edit_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = textSecondary.copy(alpha = 0.5f)
                        )
                    }
                }

                // 录音中的空状态提示
                if (recognizedText.isEmpty() && isRecording && !isPlaying) {
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.voice_recorder_example_spacing)))
                    Text(
                        text = stringResource(R.string.voice_example_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSecondary.copy(alpha = 0.4f),
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
                        modifier = Modifier.padding(top = dimensionResource(R.dimen.voice_recorder_error_top_padding))
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = errorColor,
                            modifier = Modifier
                                .size(dimensionResource(R.dimen.voice_recorder_error_icon_size))
                                .background(errorColor.copy(alpha = 0.1f), CircleShape)
                                .padding(dimensionResource(R.dimen.voice_recorder_error_icon_padding))
                        )
                        
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.voice_recorder_error_spacing)))
                        
                        Text(
                            text = stringResource(R.string.voice_no_recognition),
                            style = MaterialTheme.typography.titleMedium,
                            color = textPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.voice_recorder_error_message_spacing)))
                        
                        Text(
                            text = stringResource(R.string.voice_check_microphone),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textSecondary.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }


            // 3. 底部操作按钮区域
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = dimensionResource(R.dimen.voice_recorder_button_bottom),
                        start = dimensionResource(R.dimen.voice_recorder_button_horizontal),
                        end = dimensionResource(R.dimen.voice_recorder_button_horizontal)
                    )
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
                                .height(dimensionResource(R.dimen.voice_recorder_button_height))
                                .glowShadow(primaryAccent),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryAccent,
                                contentColor = buttonBg
                            ),
                            shape = CircleShape
                        ) {
                            Text(
                                stringResource(R.string.voice_confirm_add),
                                fontSize = dimensionResource(R.dimen.voice_recorder_button_text_size).value.sp,
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
                                color = errorColor,
                                modifier = Modifier.padding(bottom = dimensionResource(R.dimen.voice_recorder_button_message_spacing))
                            )
                            
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(dimensionResource(R.dimen.voice_recorder_button_height_small)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = textSecondary
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
                                .height(dimensionResource(R.dimen.voice_recorder_button_height)),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorResource(R.color.white_transparent_15),
                                contentColor = textPrimary
                            ),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Filled.GraphicEq, null)
                            Spacer(modifier = Modifier.width(dimensionResource(R.dimen.voice_recorder_button_spacing)))
                            Text(
                                stringResource(R.string.voice_finish),
                                fontSize = dimensionResource(R.dimen.voice_recorder_button_text_size_small).value.sp,
                                fontWeight = FontWeight.Medium
                            )
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
    val glowSize = dimensionResource(R.dimen.voice_recorder_visualizer_glow_size)
    val barSpacing = dimensionResource(R.dimen.voice_recorder_visualizer_bar_spacing)
    val barHeight = dimensionResource(R.dimen.voice_recorder_visualizer_bar_height)
    
    Box(contentAlignment = Alignment.Center) {
        // 背景光晕
        Box(
            modifier = Modifier
                .size(glowSize)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryAccent.copy(alpha = 0.2f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        // 动态条形波纹
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(barSpacing),
            modifier = Modifier.height(barHeight)
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
    val barWidth = dimensionResource(R.dimen.voice_recorder_visualizer_bar_width)
    val barCorner = dimensionResource(R.dimen.voice_recorder_visualizer_bar_corner)

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
            .width(barWidth)
            .fillMaxHeight(heightScale)
            .clip(RoundedCornerShape(barCorner))
            .background(primaryAccent.copy(alpha = alpha))
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
