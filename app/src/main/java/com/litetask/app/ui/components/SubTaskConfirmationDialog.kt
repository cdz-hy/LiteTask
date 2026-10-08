package com.litetask.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import org.burnoutcrew.reorderable.*
import java.util.UUID

// 带唯一 ID 的 UI 草稿项，保证拖拽重排时 Key 稳定、动画不闪烁
data class UiSubTask(
    val id: String = UUID.randomUUID().toString(),
    val content: String
)

private val QUICK_REFINE_OPTIONS = listOf(
    "再细化一点" to "请把偏笼统的步骤再拆分细化，写出具体做法",
    "精简为3步" to "请合并精简成 3 个核心步骤即可",
    "换一版思路" to "请换一个更高效的执行推进思路重新生成"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SubTaskConfirmationDialog(
    task: Task,
    subTasks: List<String>,
    existingSubTasks: List<SubTask> = emptyList(),
    initialMode: SubTaskGenerationMode = SubTaskGenerationMode.APPEND_NEXT,
    isRefining: Boolean = false,
    refinementError: String? = null,
    onRefine: (List<String>, String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
    onConfirm: (List<String>, Boolean) -> Unit
) {
    // 随新生成的 subTasks 重置可编辑草稿，支持确认弹窗内原位微调重生成
    var editableSubTasks by remember(subTasks) {
        mutableStateOf(subTasks.map { UiSubTask(content = it) })
    }

    var editingId by remember(subTasks) { mutableStateOf<String?>(null) }
    var editingText by remember(subTasks) { mutableStateOf("") }
    var newStepDraft by remember { mutableStateOf("") }
    var refineInput by remember { mutableStateOf("") }

    val existingUncompletedCount = remember(existingSubTasks) {
        existingSubTasks.count { !it.isCompleted }
    }
    var replaceUncompleted by remember(task.id, initialMode, existingUncompletedCount) {
        mutableStateOf(existingUncompletedCount > 0 && initialMode == SubTaskGenerationMode.REFINE_ALL)
    }

    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun commitActiveEditIfAny() {
        val currentId = editingId ?: return
        if (editingText.isNotBlank()) {
            val trimmed = editingText.trim()
            editableSubTasks = editableSubTasks.map {
                if (it.id == currentId) it.copy(content = trimmed) else it
            }
        }
        editingId = null
        editingText = ""
    }

    // 拖拽状态（限定仅在 subtask item 之间移动，排除末尾手动补加输入行）
    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            if (from.index in editableSubTasks.indices && to.index in editableSubTasks.indices) {
                editableSubTasks = editableSubTasks.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                }
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        },
        canDragOver = { draggedOver, _ ->
            draggedOver.index in editableSubTasks.indices
        },
        onDragEnd = { _, _ ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    )

    Dialog(
        onDismissRequest = { if (!isRefining) onDismiss() },
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
                // --- 1. 顶部关闭按钮 ---
                IconButton(
                    onClick = { if (!isRefining) onDismiss() },
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

                // --- 2. 核心内容区域 ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .padding(horizontal = 24.dp)
                        .padding(top = 64.dp, bottom = if (existingUncompletedCount > 0) 144.dp else 104.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (editableSubTasks.isNotEmpty())
                            "AI 为你拆解了 ${editableSubTasks.size} 个子任务"
                        else
                            "未生成子任务",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (editableSubTasks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "按住左侧手柄排序 · 点击文字多行修改 · 支持手动补充或 AI 微调",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center
                        )
                    }

                    // 微调重生成工具条
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(QUICK_REFINE_OPTIONS) { (label, prompt) ->
                                    SuggestionChip(
                                        onClick = {
                                            if (!isRefining) {
                                                commitActiveEditIfAny()
                                                onRefine(editableSubTasks.map { it.content }, prompt)
                                            }
                                        },
                                        enabled = !isRefining,
                                        label = {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = refineInput,
                                    onValueChange = { refineInput = it },
                                    placeholder = {
                                        Text(
                                            text = "输入微调意见（如：第2步再细一点）",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    enabled = !isRefining,
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(
                                        onSend = {
                                            if (!isRefining && refineInput.isNotBlank()) {
                                                keyboardController?.hide()
                                                commitActiveEditIfAny()
                                                val feedback = refineInput.trim()
                                                refineInput = ""
                                                onRefine(editableSubTasks.map { it.content }, feedback)
                                            }
                                        }
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalIconButton(
                                    onClick = {
                                        if (!isRefining && refineInput.isNotBlank()) {
                                            keyboardController?.hide()
                                            commitActiveEditIfAny()
                                            val feedback = refineInput.trim()
                                            refineInput = ""
                                            onRefine(editableSubTasks.map { it.content }, feedback)
                                        }
                                    },
                                    enabled = !isRefining && refineInput.isNotBlank(),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    if (isRefining) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "AI 微调重生成",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            if (isRefining) {
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            if (!refinementError.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = refinementError,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // --- 列表或空状态视图 ---
                    if (editableSubTasks.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistRemove,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "当前列表为空，可手动补加一步或上方重新生成",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            state = reorderableState.listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .reorderable(reorderableState),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                    val modifier = if (isDragging) {
                                        Modifier
                                    } else {
                                        Modifier.animateItemPlacement(
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )
                                    }

                                    EditableSubTaskItem(
                                        modifier = modifier,
                                        index = index,
                                        subTask = item.content,
                                        isEditing = editingId == item.id,
                                        editingText = editingText,
                                        isDragging = isDragging,
                                        onStartEdit = {
                                            commitActiveEditIfAny()
                                            editingId = item.id
                                            editingText = item.content
                                        },
                                        onTextChange = { editingText = it },
                                        onConfirmEdit = {
                                            keyboardController?.hide()
                                            commitActiveEditIfAny()
                                        },
                                        onCancelEdit = {
                                            keyboardController?.hide()
                                            editingId = null
                                            editingText = ""
                                        },
                                        onDelete = {
                                            editableSubTasks = editableSubTasks.filterNot { it.id == item.id }
                                            if (editingId == item.id) {
                                                editingId = null
                                                editingText = ""
                                            }
                                        },
                                        reorderableState = reorderableState
                                    )
                                }
                            }

                            // 末尾支持手动补加一步
                            item(key = "manual_add_step") {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = newStepDraft,
                                            onValueChange = { newStepDraft = it },
                                            placeholder = {
                                                Text(
                                                    text = "+ 手动补充遗漏步骤...",
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyMedium,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color.Transparent,
                                                unfocusedBorderColor = Color.Transparent,
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent
                                            ),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(
                                                onDone = {
                                                    val trimmed = newStepDraft.trim()
                                                    if (trimmed.isNotEmpty()) {
                                                        commitActiveEditIfAny()
                                                        editableSubTasks = editableSubTasks + UiSubTask(content = trimmed)
                                                        newStepDraft = ""
                                                    }
                                                }
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                val trimmed = newStepDraft.trim()
                                                if (trimmed.isNotEmpty()) {
                                                    commitActiveEditIfAny()
                                                    editableSubTasks = editableSubTasks + UiSubTask(content = trimmed)
                                                    newStepDraft = ""
                                                }
                                            },
                                            enabled = newStepDraft.isNotBlank()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AddCircle,
                                                contentDescription = "手动添加步骤",
                                                tint = if (newStepDraft.isNotBlank()) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- 3. 底部按钮区（已有待办时支持选择追加或替换） ---
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 1.dp,
                    shadowElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        if (existingUncompletedCount > 0) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ImportStrategyPill(
                                    text = "追加到现有末尾",
                                    selected = !replaceUncompleted,
                                    modifier = Modifier.weight(1f),
                                    onClick = { replaceUncompleted = false }
                                )
                                ImportStrategyPill(
                                    text = "替换原有 $existingUncompletedCount 条待办",
                                    selected = replaceUncompleted,
                                    modifier = Modifier.weight(1f),
                                    onClick = { replaceUncompleted = true }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(
                                onClick = { if (!isRefining) onDismiss() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = CircleShape
                            ) {
                                Text(text = "取消", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            }

                            val validCount = editableSubTasks.count { it.content.isNotBlank() }
                            Button(
                                onClick = {
                                    commitActiveEditIfAny()
                                    val finalSteps = editableSubTasks.map { it.content.trim() }.filter { it.isNotEmpty() }
                                    if (finalSteps.isNotEmpty()) {
                                        onConfirm(finalSteps, replaceUncompleted)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.25f)
                                    .height(52.dp),
                                enabled = validCount > 0 && !isRefining,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                ),
                                shape = CircleShape
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (validCount > 0) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = when {
                                            validCount == 0 -> "列表为空"
                                            replaceUncompleted -> "替换并写入 $validCount 个"
                                            else -> "添加 $validCount 个"
                                        },
                                        fontSize = 15.sp,
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
private fun ImportStrategyPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun EditableSubTaskItem(
    modifier: Modifier = Modifier,
    index: Int,
    subTask: String,
    isEditing: Boolean,
    editingText: String,
    isDragging: Boolean,
    onStartEdit: () -> Unit,
    onTextChange: (String) -> Unit,
    onConfirmEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: () -> Unit,
    reorderableState: ReorderableLazyListState
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    val elevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        label = "elevation"
    )

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        label = "scale"
    )

    val backgroundColor = if (isDragging) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation.toPx()
            },
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = elevation
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 拖拽手柄（直接按住拖动排序）
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "拖拽排序",
                tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier
                    .size(24.dp)
                    .then(
                        if (!isEditing) Modifier.detectReorder(reorderableState) else Modifier
                    )
            )

            Spacer(modifier = Modifier.width(10.dp))

            // 序号
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(
                        if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDragging) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            if (isEditing) {
                OutlinedTextField(
                    value = editingText,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    singleLine = false,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            onConfirmEdit()
                        }
                    )
                )

                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onConfirmEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Check, "保存", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onCancelEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "取消", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            } else {
                Text(
                    text = subTask,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            indication = rememberRipple(),
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onStartEdit() }
                        .padding(vertical = 2.dp)
                )

                if (!isDragging) {
                    IconButton(onClick = onStartEdit, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Edit, "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error.copy(0.75f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
