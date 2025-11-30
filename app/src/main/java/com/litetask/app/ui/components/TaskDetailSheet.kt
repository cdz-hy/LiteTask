package com.litetask.app.ui.components

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.litetask.app.R
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.SubTask
import com.litetask.app.ui.theme.Primary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(
    task: Task,
    subTasks: List<SubTask>,
    onDismiss: () -> Unit,
    onDelete: (Task) -> Unit,
    onUpdateTask: (Task) -> Unit,
    onUpdateSubTask: (SubTask, Boolean) -> Unit,
    onAddSubTask: (String) -> Unit,
    onDeleteSubTask: (SubTask) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var newSubTaskText by remember { mutableStateOf("") }
    var isFullScreen by remember { mutableStateOf(false) }

    // 优化封装带动画的关闭逻辑
    fun closeSheetWithAnimation() {
        scope.launch {
            sheetState.hide() 
        }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = null,
        // 保持键盘弹出时的处理，WindowInsets.ime 会自动处理底部 Padding
        windowInsets = WindowInsets.ime
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 优化更柔和的展开动画
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = 300f      
                    )
                )
                // 高度逻辑：全屏95%，非全屏自适应
                .then(
                    if (isFullScreen) Modifier.fillMaxHeight(0.95f)
                    else Modifier.wrapContentHeight()
                )
        ) {
            // --- 顶部手势区域 ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { /* 惯性处理可选 */ }
                        ) { change, dragAmount ->
                            change.consume()
                            
                            // --- 核心优化 3：手势阈值判断 ---
                            val sensitivity = 8f 
                            
                            if (dragAmount < -sensitivity) {
                                if (!isFullScreen) isFullScreen = true
                            } else if (dragAmount > sensitivity) {
                                if (isFullScreen) {
                                    isFullScreen = false
                                } else {
                                    closeSheetWithAnimation()
                                }
                            }
                        }
                    }
            ) {
                // 视觉 Handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(Color(0xFFE0E0E0), RoundedCornerShape(2.dp))
                    )
                }

                // Header 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { closeSheetWithAnimation() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Color.Gray)
                    }
                }
            }

            // --- 内容区域 ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp)
            ) {
                // 描述
                if (!task.description.isNullOrEmpty()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // 时间
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF666666)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${formatTime(task.startTime)} - ${formatTime(task.deadline)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF444746)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 子任务标题
                Text(
                    text = stringResource(R.string.subtasks_steps),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF444746)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 子任务列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isFullScreen) {
                                Modifier.weight(1f) // 全屏：占满剩余空间
                            } else {
                                Modifier
                                    .heightIn(max = 240.dp) // 非全屏：限制最大高度
                                    // 关键修改：添加 weight(1f, fill = false)
                                    // fill = false 保证了内容少时高度自适应（不会强行占满）
                                    // 但当键盘弹出导致空间不足时，weight 属性会让它优先收缩，从而保护底部按钮不被挤压
                                    .weight(1f, fill = false) 
                            }
                        )
                ) {
                    items(items = subTasks.sortedBy { it.id }, key = { it.id }) { subTask ->
                        SubTaskItem(
                            subTask = subTask,
                            onToggleComplete = { onUpdateSubTask(subTask, it) },
                            onDelete = { onDeleteSubTask(subTask) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 添加子任务输入框
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newSubTaskText,
                        onValueChange = { newSubTaskText = it },
                        placeholder = { Text(stringResource(R.string.add_subtask)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Color.LightGray,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                    IconButton(
                        onClick = {
                            if (newSubTaskText.isNotBlank()) {
                                onAddSubTask(newSubTaskText)
                                newSubTaskText = ""
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = stringResource(R.string.add_subtask), tint = Primary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 底部按钮区
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(
                        onClick = { 
                            onDelete(task)
                            closeSheetWithAnimation() 
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF43F5E)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF43F5E))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_task))
                    }

                    if (!task.isDone || task.deadline >= System.currentTimeMillis()) {
                        Button(
                            onClick = {
                                if (task.isDone && task.deadline < System.currentTimeMillis()) {
                                    Toast.makeText(context, R.string.task_cannot_undone_expired, Toast.LENGTH_SHORT).show()
                                } else {
                                    val updatedTask = if (!task.isDone) {
                                        task.copy(isDone = true, isPinned = false)
                                    } else {
                                        task.copy(isDone = false)
                                    }
                                    onUpdateTask(updatedTask)
                                    closeSheetWithAnimation()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (task.isDone) Color.Gray else Primary
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (task.isDone) stringResource(R.string.mark_undone) else stringResource(R.string.mark_done))
                        }
                    }
                }
            }
        }
    }
}

// 保持原有的 SubTaskItem 和 formatTime 不变
@Composable
private fun SubTaskItem(
    subTask: SubTask,
    onToggleComplete: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var isDeleting by remember { mutableStateOf(false) }

    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDeleting) 0f else 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
        label = "alpha"
    )

    // 使用 padding 变化代替 offset，通常在列表中表现更稳定
    LaunchedEffect(isDeleting) {
        if (isDeleting) {
            kotlinx.coroutines.delay(300)
            onDelete()
        }
    }

    // 如果正在删除，高度逐渐变为0
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .then(if(isDeleting) Modifier.height(0.dp) else Modifier.wrapContentHeight())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable {
                    onToggleComplete(!subTask.isCompleted)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = subTask.isCompleted,
                onCheckedChange = { onToggleComplete(it) },
                colors = CheckboxDefaults.colors(checkedColor = Primary)
            )
            Text(
                text = subTask.content,
                style = MaterialTheme.typography.bodyLarge,
                color = if (subTask.isCompleted) Color.Gray else Color(0xFF1F1F1F),
                textDecoration = if (subTask.isCompleted) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { isDeleting = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.delete),
                    tint = Color(0xFFE57373),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat(stringResource(R.string.date_time_format), Locale.getDefault()).format(Date(timestamp))
}