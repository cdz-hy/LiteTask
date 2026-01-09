package com.litetask.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.litetask.app.R
import com.litetask.app.data.model.Task
import org.burnoutcrew.reorderable.*
import java.util.UUID

// 1. 定义一个带唯一 ID 的包装类，确保拖拽时 Key 的稳定性，防止动画闪烁
data class UiSubTask(
    val id: String = UUID.randomUUID().toString(),
    var content: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SubTaskConfirmationDialog(
    task: Task,
    subTasks: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    // 数据状态
    var editableSubTasks by remember {
        mutableStateOf(subTasks.map { UiSubTask(content = it) }.toMutableList())
    }

    // 编辑状态
    var editingId by remember { mutableStateOf<String?>(null) }
    var editingText by remember { mutableStateOf("") }

    val haptic = LocalHapticFeedback.current

    // 颜色资源
    val bgTop = colorResource(R.color.voice_recorder_bg_top)
    val bgBottom = colorResource(R.color.voice_recorder_bg_bottom)
    val primaryAccent = colorResource(R.color.voice_recorder_primary)
    val textPrimary = colorResource(R.color.voice_recorder_text_primary)
    val textSecondary = colorResource(R.color.voice_recorder_text_secondary)

    // 拖拽状态
    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            editableSubTasks = editableSubTasks.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        },
        onDragEnd = { _, _ ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    )

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
            // --- 1. 顶部关闭按钮 ---
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

            // --- 2. 核心内容区域 ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .padding(top = 80.dp, bottom = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // AI 图标
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(primaryAccent.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = primaryAccent,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 标题 (根据数量动态显示)
                Text(
                    text = if (editableSubTasks.isNotEmpty())
                        "AI 为你生成了 ${editableSubTasks.size} 个子任务"
                    else
                        "未生成子任务",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium),
                    color = textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryAccent,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 操作提示 (仅当有任务时显示)
                if (editableSubTasks.isNotEmpty()) {
                    Text(
                        text = "可拖拽排序、点击编辑、长按删除",
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- 列表或空状态视图 ---
                if (editableSubTasks.isEmpty()) {
                    // *** 空状态提示视图 ***
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistRemove, // 或者用 EditOff, Assignment
                            contentDescription = null,
                            tint = textSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "暂无子任务",
                            style = MaterialTheme.typography.bodyLarge,
                            color = textSecondary.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "请点击重试或手动添加", // 根据实际业务逻辑调整文案
                            style = MaterialTheme.typography.bodySmall,
                            color = textSecondary.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    // *** 存在的列表视图 ***
                    LazyColumn(
                        state = reorderableState.listState,
                        modifier = Modifier
                            .weight(1f)
                            .reorderable(reorderableState),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        itemsIndexed(
                            items = editableSubTasks,
                            key = { _, item -> item.id }
                        ) { index, item ->
                            ReorderableItem(
                                reorderableState = reorderableState,
                                key = item.id
                            ) { isDragging ->
                                val modifier = Modifier.animateItemPlacement(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )

                                EditableSubTaskItem(
                                    modifier = modifier,
                                    index = index,
                                    subTask = item.content,
                                    isEditing = editingId == item.id,
                                    editingText = editingText,
                                    isDragging = isDragging,
                                    primaryAccent = primaryAccent,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    onStartEdit = {
                                        editingId = item.id
                                        editingText = item.content
                                    },
                                    onTextChange = { editingText = it },
                                    onConfirmEdit = {
                                        if (editingText.isNotBlank()) {
                                            val newList = editableSubTasks.toMutableList()
                                            val itemIndex = newList.indexOfFirst { it.id == item.id }
                                            if (itemIndex != -1) {
                                                newList[itemIndex] = item.copy(content = editingText.trim())
                                                editableSubTasks = newList
                                            }
                                        }
                                        editingId = null
                                        editingText = ""
                                    },
                                    onCancelEdit = {
                                        editingId = null
                                        editingText = ""
                                    },
                                    onDelete = {
                                        editableSubTasks = editableSubTasks.toMutableList().apply {
                                            removeAt(index)
                                        }
                                        if (editingId == item.id) editingId = null
                                    },
                                    reorderableState = reorderableState
                                )
                            }
                        }
                    }
                }
            }

            // --- 3. 底部按钮 ---
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp, start = 32.dp, end = 32.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 取消按钮
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = textPrimary,
                        containerColor = colorResource(R.color.white_transparent_10)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                colorResource(R.color.white_transparent_20),
                                colorResource(R.color.white_transparent_20)
                            )
                        )
                    ),
                    shape = CircleShape
                ) {
                    Text(text = "取消", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }

                // 确认按钮 (样式与逻辑修复)
                Button(
                    onClick = {
                        if (editableSubTasks.isNotEmpty()) {
                            onConfirm(editableSubTasks.map { it.content }.filter { it.isNotBlank() })
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .graphicsLayer {
                            // 禁用时移除阴影，看起来更扁平
                            shadowElevation = if(editableSubTasks.isNotEmpty()) 8.dp.toPx() else 0f
                            shape = CircleShape
                            spotShadowColor = primaryAccent
                            ambientShadowColor = primaryAccent
                        },
                    enabled = editableSubTasks.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryAccent,
                        contentColor = colorResource(R.color.voice_recorder_button_bg),
                        // *** 关键修复：设置禁用状态的颜色，保证在深色背景下可见 ***
                        disabledContainerColor = primaryAccent.copy(alpha = 0.2f),
                        disabledContentColor = textPrimary.copy(alpha = 0.4f)
                    ),
                    shape = CircleShape
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // 仅当不为空时显示 Check 图标
                        if (editableSubTasks.isNotEmpty()) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Text(
                            text = if (editableSubTasks.isNotEmpty())
                                "添加全部 (${editableSubTasks.size})"
                            else
                                "列表为空",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditableSubTaskItem(
    modifier: Modifier = Modifier,
    index: Int,
    subTask: String,
    isEditing: Boolean,
    editingText: String,
    isDragging: Boolean,
    primaryAccent: Color,
    textPrimary: Color,
    textSecondary: Color,
    onStartEdit: () -> Unit,
    onTextChange: (String) -> Unit,
    onConfirmEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: () -> Unit,
    reorderableState: ReorderableLazyListState
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // 5. 拖拽动效优化：阴影和缩放
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 16.dp else 0.dp, // 拖拽时浮起
        label = "elevation"
    )

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1f,
        label = "scale"
    )

    val backgroundColor = if (isDragging) {
        colorResource(R.color.white_transparent_20)
    } else {
        colorResource(R.color.white_transparent_10)
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        }
    }

    Surface(
        modifier = modifier // 应用传入的 animateItemPlacement modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                // 拖拽时稍微增加 zIndex 确保在顶层
                shadowElevation = elevation.toPx()
            },
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = elevation
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 拖拽手柄
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "拖拽排序",
                tint = if (isDragging) primaryAccent else textSecondary.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(24.dp)
                    // 6. 使用 detectReorderAfterLongPress 启动拖拽
                    .detectReorderAfterLongPress(reorderableState)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 序号：始终显示当前的物理位置索引 (index + 1)
            // 因为列表在 onMove 时已经物理交换了，所以这里的 index 会自动更新，符合"视觉即所得"
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        if (isDragging) primaryAccent else primaryAccent.copy(alpha = 0.2f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDragging) Color.White else primaryAccent,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (isEditing) {
                // ... (编辑模式代码优化) ...
                OutlinedTextField(
                    value = editingText,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryAccent,
                        unfocusedBorderColor = Color.Transparent, // 编辑时隐藏边框更简洁
                        cursorColor = primaryAccent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            onConfirmEdit()
                        }
                    )
                )

                IconButton(onClick = onConfirmEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Check, null, tint = primaryAccent, modifier = Modifier.size(20.dp))
                }
            } else {
                // ... (显示模式代码优化) ...
                Text(
                    text = subTask,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            indication = rememberRipple(),
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onStartEdit() }
                )

                // 只有非拖拽状态下才显示编辑/删除按钮，避免误触
                if (!isDragging) {
                    IconButton(onClick = onStartEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, "编辑", tint = textSecondary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error.copy(0.7f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}