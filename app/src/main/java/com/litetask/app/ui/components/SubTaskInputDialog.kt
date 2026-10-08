package com.litetask.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.litetask.app.R
import com.litetask.app.data.ai.SubTaskGenerationMode
import com.litetask.app.data.model.SubTask
import com.litetask.app.data.model.Task

private data class QuickDecomposeChip(
    val label: String,
    val prompt: String
)

private val QUICK_DECOMPOSE_CHIPS = listOf(
    QuickDecomposeChip("按阶段拆解", "请按「前期准备 → 核心执行 → 核对收尾」分阶段拆解。"),
    QuickDecomposeChip("细化下一步", "请重点将接下来最先需要动手的步骤细化到可直接执行的动作。"),
    QuickDecomposeChip("精简为3~4步", "请高度提炼，生成 3~4 个最关键的里程碑步骤即可。"),
    QuickDecomposeChip("含行前/提交检查", "请加入前置准备清单与交付/截止前自查确认步骤。")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubTaskInputDialog(
    task: Task,
    existingSubTasks: List<SubTask> = emptyList(),
    initialMode: SubTaskGenerationMode = SubTaskGenerationMode.APPEND_NEXT,
    onDismiss: () -> Unit,
    onAnalyze: (String, List<android.net.Uri>, SubTaskGenerationMode) -> Unit,
    isAnalyzing: Boolean = false,
    agentStatus: String = "",
    agentLogs: List<String> = emptyList(),
    isMultimodalModel: Boolean = false
) {
    var inputText by remember(task.id) { mutableStateOf("") }
    var selectedMode by remember(task.id, initialMode) { mutableStateOf(initialMode) }
    val maxCharCount = 500
    val scrollState = rememberScrollState()

    var selectedImageUris by remember(task.id) { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val pickMedia = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val validUris = uris.filter { uri ->
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                val sizeIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.SIZE)
                cursor?.moveToFirst()
                val size = sizeIndex?.let { cursor.getLong(it) } ?: 0L
                cursor?.close()
                if (size > 5 * 1024 * 1024) {
                    android.widget.Toast.makeText(context, "图片大小不能超过 5MB", android.widget.Toast.LENGTH_SHORT).show()
                    false
                } else {
                    true
                }
            }
            val remainingSlots = 5 - selectedImageUris.size
            if (remainingSlots > 0) {
                selectedImageUris = selectedImageUris + validUris.take(remainingSlots)
            }
        }
    }

    val existingDoneCount = remember(existingSubTasks) { existingSubTasks.count { it.isCompleted } }
    val existingPendingCount = existingSubTasks.size - existingDoneCount

    Dialog(
        onDismissRequest = { if (!isAnalyzing) onDismiss() },
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
                // 1. 核心内容区域（支持小屏/键盘弹出时滚动）
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .imePadding()
                        .padding(horizontal = 28.dp)
                        .padding(top = 72.dp, bottom = 124.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // AI 图标
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AISparkle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 标题
                    Text(
                        text = "AI 子任务拆解",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "可直接一键拆解，也可点击意图标签、输入要求或上传参考图",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 任务信息卡片（含当前子任务进度摘要）
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "当前任务",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (existingSubTasks.isNotEmpty()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "已有 ${existingSubTasks.size} 步（已完成 $existingDoneCount / 待办 $existingPendingCount）",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!task.description.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = task.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 已有子任务时提供明确的补充/重规划模式切换
                    if (existingSubTasks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ModeOptionChip(
                                label = "基于已有步骤补充后续",
                                selected = selectedMode == SubTaskGenerationMode.APPEND_NEXT,
                                enabled = !isAnalyzing,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedMode = SubTaskGenerationMode.APPEND_NEXT }
                            )
                            ModeOptionChip(
                                label = "重新规划待办步骤",
                                selected = selectedMode == SubTaskGenerationMode.REFINE_ALL,
                                enabled = !isAnalyzing,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedMode = SubTaskGenerationMode.REFINE_ALL }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 快捷意图胶囊
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QUICK_DECOMPOSE_CHIPS.forEach { chip ->
                            val isSelected = inputText.contains(chip.prompt)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (!isAnalyzing) {
                                        inputText = if (isSelected) {
                                            inputText.replace(chip.prompt, "").replace(Regex("\\n{2,}"), "\n").trim()
                                        } else {
                                            val joined = if (inputText.isBlank()) {
                                                chip.prompt
                                            } else {
                                                "${inputText.trimEnd()}\n${chip.prompt}"
                                            }
                                            joined.take(maxCharCount)
                                        }
                                    }
                                },
                                label = {
                                    Text(
                                        text = chip.label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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
                                .height(180.dp),
                            enabled = !isAnalyzing,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 23.sp
                            ),
                            placeholder = {
                                Text(
                                    text = "可选：填写背景细节、限制条件、期望粒度或需要避开的事项（留空也可直接智能拆解）",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    fontSize = 14.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
                                disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )

                        // 左下角：图片预览
                        if (selectedImageUris.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 14.dp, bottom = 10.dp)
                                    .fillMaxWidth(0.6f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(selectedImageUris.size) { index ->
                                    val uri = selectedImageUris[index]
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        coil.compose.AsyncImage(
                                            model = uri,
                                            contentDescription = "Selected Image",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                        IconButton(
                                            onClick = {
                                                selectedImageUris = selectedImageUris.toMutableList().apply { removeAt(index) }
                                            },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(20.dp)
                                                .padding(2.dp)
                                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove Image",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 右下角：字符计数与图片上传
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 12.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isMultimodalModel && selectedImageUris.size < 5) {
                                IconButton(
                                    onClick = {
                                        pickMedia.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                    enabled = !isAnalyzing,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = "Upload Image",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            Text(
                                text = "${inputText.length}/$maxCharCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }

                        // Agent 思考过程覆盖层
                        AgentThinkingOverlay(
                            visible = isAnalyzing,
                            status = agentStatus,
                            logs = agentLogs,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }

                // 2. 顶部关闭按钮
                IconButton(
                    onClick = { if (!isAnalyzing) onDismiss() },
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

                // 3. 底部分析按钮
                val hasCustomGuidance = inputText.isNotBlank() || selectedImageUris.isNotEmpty()
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(
                            bottom = 32.dp,
                            start = 28.dp,
                            end = 28.dp
                        )
                        .fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (!isAnalyzing) {
                                onAnalyze(inputText.trim(), selectedImageUris, selectedMode)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .graphicsLayer {
                                shadowElevation = 4.dp.toPx()
                                shape = CircleShape
                                spotShadowColor = Color.Black
                                ambientShadowColor = Color.Black
                            },
                        enabled = !isAnalyzing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                        ),
                        shape = CircleShape,
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 2.dp
                        )
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
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "AI 拆解中...",
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
                                        text = if (hasCustomGuidance) "按要求生成子任务" else "一键智能拆解",
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
}

@Composable
private fun ModeOptionChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
        )
    }
}