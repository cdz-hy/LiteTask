package com.litetask.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.litetask.app.R
import com.litetask.app.data.model.Task
import com.litetask.app.ui.components.AISparkle

@Composable
fun SubTaskInputDialog(
    task: Task,
    onDismiss: () -> Unit,
    onAnalyze: (String) -> Unit,
    isAnalyzing: Boolean = false
) {
    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val maxCharCount = 300
    
    // 颜色资源 - 复用 VoiceRecorderDialog 的颜色
    val bgTop = colorResource(R.color.voice_recorder_bg_top)
    val bgBottom = colorResource(R.color.voice_recorder_bg_bottom)
    val primaryAccent = colorResource(R.color.voice_recorder_primary)
    val textPrimary = colorResource(R.color.voice_recorder_text_primary)
    val textSecondary = colorResource(R.color.voice_recorder_text_secondary)
    
    // 自动聚焦输入框
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Dialog(
        onDismissRequest = { if (!isAnalyzing) onDismiss() },
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
                onClick = { if (!isAnalyzing) onDismiss() },
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
                    .padding(horizontal = 32.dp)
                    .padding(top = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // AI 图标
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            primaryAccent.copy(alpha = 0.1f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AISparkle,
                        contentDescription = null,
                        tint = primaryAccent,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 任务信息卡片
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colorResource(R.color.white_transparent_10),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "当前任务",
                            style = MaterialTheme.typography.labelMedium,
                            color = textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = textPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        if (!task.description.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = task.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = textSecondary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 标题
                Text(
                    text = "AI 子任务拆解",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 副标题
                Text(
                    text = "描述更多细节，让 AI 为你生成更精准的子任务步骤",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 文本输入框
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { 
                            if (it.length <= maxCharCount) {
                                inputText = it
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .focusRequester(focusRequester),
                        enabled = !isAnalyzing,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = textPrimary,
                            lineHeight = 24.sp
                        ),
                        placeholder = {
                            Text(
                                text = "例如：需要准备哪些材料？分几个阶段完成？有什么注意事项？",
                                color = textSecondary.copy(alpha = 0.4f)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryAccent,
                            unfocusedBorderColor = colorResource(R.color.white_transparent_20),
                            cursorColor = primaryAccent,
                            focusedContainerColor = colorResource(R.color.white_transparent_5),
                            unfocusedContainerColor = colorResource(R.color.white_transparent_5),
                            disabledBorderColor = colorResource(R.color.white_transparent_20),
                            disabledContainerColor = colorResource(R.color.white_transparent_5),
                            disabledTextColor = textPrimary
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )

                    // 字符计数
                    Text(
                        text = "${inputText.length}/$maxCharCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = textSecondary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp)
                    )
                }
            }

            // 3. 底部分析按钮
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = 48.dp,
                        start = 32.dp,
                        end = 32.dp
                    )
                    .fillMaxWidth()
            ) {
                Button(
                    onClick = { 
                        if (!isAnalyzing) {
                            onAnalyze(inputText.trim())
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .graphicsLayer {
                            shadowElevation = 8.dp.toPx()
                            shape = CircleShape
                            spotShadowColor = primaryAccent
                            ambientShadowColor = primaryAccent
                        },
                    enabled = !isAnalyzing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryAccent,
                        contentColor = colorResource(R.color.voice_recorder_button_bg),
                        disabledContainerColor = primaryAccent.copy(alpha = 0.5f),
                        disabledContentColor = colorResource(R.color.voice_recorder_button_bg).copy(alpha = 0.7f)
                    ),
                    shape = CircleShape
                ) {
                    AnimatedContent(
                        targetState = isAnalyzing,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                        },
                        label = "ButtonContent"
                    ) { analyzing ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (analyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = colorResource(R.color.voice_recorder_button_bg),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AI 分析中...",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.AISparkle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "生成子任务",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}