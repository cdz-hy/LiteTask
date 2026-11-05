package com.litetask.app.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.litetask.app.R
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.SubTask
import com.litetask.app.data.model.Reminder
import com.litetask.app.data.model.TaskType
import com.litetask.app.data.model.moveSubTaskItem
import com.litetask.app.data.model.resolveSubTaskMoveIndices
import com.litetask.app.data.model.sortedByOrder
import com.litetask.app.ui.components.AISparkle
import com.litetask.app.ui.theme.LocalExtendedColors
import com.litetask.app.ui.util.ColorUtils
import com.litetask.app.data.model.Category
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 获取任务类型主题色
 */
/**
 * 获取任务类型主题色
 */
@Composable
private fun getTaskThemeColor(type: TaskType, category: Category? = null): Color {
    if (category != null) {
        return ColorUtils.parseColor(category.colorHex)
    }
    val extendedColors = LocalExtendedColors.current
    return when (type) {
        TaskType.WORK -> extendedColors.workTask
        TaskType.LIFE -> extendedColors.lifeTask
        TaskType.STUDY -> extendedColors.studyTask
        TaskType.URGENT -> extendedColors.urgentTask
    }
}

/**
 * 获取任务类型表面色
 */
@Composable
private fun getTaskSurfaceColor(type: TaskType, category: Category? = null): Color {
    if (category != null) {
        val primary = ColorUtils.parseColor(category.colorHex)
        return ColorUtils.getSurfaceColor(primary)
    }
    val extendedColors = LocalExtendedColors.current
    return when (type) {
        TaskType.WORK -> extendedColors.workTaskSurface
        TaskType.LIFE -> extendedColors.lifeTaskSurface
        TaskType.STUDY -> extendedColors.studyTaskSurface
        TaskType.URGENT -> extendedColors.urgentTaskSurface
    }
}

/**
 * Sheet 展开状态
 */
private enum class SheetExpandState {
    HALF,       // 半展开 70%
    FULL        // 全屏（不超过状态栏）
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TaskDetailSheet(
    task: Task,
    subTasks: List<SubTask>,
    reminders: List<Reminder> = emptyList(),
    components: List<com.litetask.app.data.model.TaskComponent> = emptyList(),
    category: Category? = null,
    onDismiss: () -> Unit,
    onDelete: (Task) -> Unit,
    onUpdateTask: (Task) -> Unit,
    onUpdateSubTask: (SubTask, Boolean) -> Unit,
    onUpdateSubTaskContent: (SubTask, String) -> Unit = { _, _ -> },
    onReorderSubTasks: (List<SubTask>) -> Unit = {},
    onAddSubTask: (String) -> Unit,
    onDeleteSubTask: (SubTask) -> Unit,
    onGenerateSubTasksWithContext: (Task) -> Unit = {},
    isGeneratingSubTasks: Boolean = false,
    onAddComponent: ((com.litetask.app.data.model.TaskComponent) -> Unit)? = null,
    onUpdateComponent: ((com.litetask.app.data.model.TaskComponent) -> Unit)? = null,
    onDeleteComponent: (com.litetask.app.data.model.TaskComponent) -> Unit = {},
    amapKey: String? = null,
    onGetWeather: (suspend (String) -> Pair<String, String>?)? = null,
    onGeocode: (suspend (String) -> com.litetask.app.data.model.AMapRouteData?)? = null,
    onSearchLocations: (suspend (String) -> List<com.litetask.app.data.model.AMapRouteData>)? = null,
    amapRouteActions: AMapRouteActions? = null,
    voiceNoteActions: VoiceNoteActions? = null,
    onStartFocus: (() -> Unit)? = null
) {
    val context = LocalContext.current
    DisposableEffect(task.id) {
        onDispose { com.litetask.app.util.VoiceNotePlayerManager.stop() }
    }
    val extendedColors = LocalExtendedColors.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    // 屏幕高度
    val screenHeightDp = configuration.screenHeightDp.dp
    val screenHeightPx = with(density) { screenHeightDp.toPx() }
    val partialHeightPx = screenHeightPx * 0.7f  // 70% 高度
    val fullHeightPx = screenHeightPx  // 全屏高度

    // 展开状态
    var expandState by remember { mutableStateOf(SheetExpandState.HALF) }
    var isClosing by remember { mutableStateOf(false) }
    
    // 拖拽偏移量（实时跟随手指）
    var dragOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    
    // 基准高度（根据展开状态）
    val baseHeightPx = when (expandState) {
        SheetExpandState.HALF -> partialHeightPx
        SheetExpandState.FULL -> fullHeightPx
    }
    
    // 计算实际高度：基准高度 - 拖拽偏移
    val currentHeightPx = if (isClosing) 0f else (baseHeightPx - dragOffset).coerceAtLeast(0f)
    val currentHeightDp = with(density) { currentHeightPx.toDp() }
    
    // 动画高度
    val animatedHeight by animateDpAsState(
        targetValue = currentHeightDp,
        animationSpec = if (isDragging) {
            // 拖拽时不使用动画，直接跟随
            androidx.compose.animation.core.snap()
        } else {
            // 松手后使用弹性动画（弱化弹动，减慢弹回速度）
            androidx.compose.animation.core.spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            )
        },
        label = "sheetHeight"
    )

    // 背景遮罩透明度（跟随拖拽变化）
    val scrimAlpha = when {
        isClosing -> 0f
        isDragging -> (0.5f * (1f - dragOffset / screenHeightPx * 2)).coerceIn(0f, 0.5f)
        else -> 0.5f
    }

    var newSubTaskText by remember { mutableStateOf("") }

    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sortedSubTasks = remember(subTasks) { subTasks.sortedByOrder() }
    var localSubTasks by remember(task.id) { mutableStateOf(sortedSubTasks) }
    var isReorderingSubTasks by remember(task.id) { mutableStateOf(false) }
    var pendingOrderIds by remember(task.id) { mutableStateOf<List<Long>?>(null) }
    var editingSubTaskId by remember(task.id) { mutableStateOf<Long?>(null) }
    var editingSubTaskValue by remember(task.id) { mutableStateOf(TextFieldValue("")) }

    fun commitActiveSubTaskEdit() {
        val activeId = editingSubTaskId ?: return
        val target = localSubTasks.firstOrNull { it.id == activeId }
        val trimmed = editingSubTaskValue.text.trim()
        if (target != null && trimmed.isNotEmpty() && trimmed != target.content) {
            localSubTasks = localSubTasks.map {
                if (it.id == activeId) it.copy(content = trimmed) else it
            }
            onUpdateSubTaskContent(target, trimmed)
        }
        editingSubTaskId = null
        editingSubTaskValue = TextFieldValue("")
    }

    LaunchedEffect(sortedSubTasks, isReorderingSubTasks, pendingOrderIds) {
        if (isReorderingSubTasks) return@LaunchedEffect
        val pending = pendingOrderIds
        if (pending != null) {
            if (sortedSubTasks.map { it.id } == pending) {
                pendingOrderIds = null
                localSubTasks = sortedSubTasks
            }
        } else {
            localSubTasks = sortedSubTasks
        }
        val activeEditId = editingSubTaskId
        if (activeEditId != null && sortedSubTasks.none { it.id == activeEditId }) {
            editingSubTaskId = null
            editingSubTaskValue = TextFieldValue("")
        }
    }

    LaunchedEffect(pendingOrderIds) {
        if (pendingOrderIds != null) {
            delay(1000L)
            if (pendingOrderIds != null) {
                pendingOrderIds = null
                localSubTasks = sortedSubTasks
            }
        }
    }

    val headerItemCount = 1
    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            val move = resolveSubTaskMoveIndices(
                fromLazyIndex = from.index,
                toLazyIndex = to.index,
                headerCount = headerItemCount,
                subTaskCount = localSubTasks.size
            ) ?: return@rememberReorderableLazyListState
            if (!isReorderingSubTasks) {
                commitActiveSubTaskEdit()
                isReorderingSubTasks = true
            }
            localSubTasks = localSubTasks.moveSubTaskItem(move.first, move.second)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        },
        canDragOver = { draggedOver, _ ->
            draggedOver.key?.toString()?.startsWith("subtask_") == true
        },
        onDragEnd = { _, _ ->
            val finalList = localSubTasks
            isReorderingSubTasks = false
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (finalList.map { it.id } != sortedSubTasks.map { it.id }) {
                pendingOrderIds = finalList.map { it.id }
                onReorderSubTasks(finalList)
            }
        }
    )

    LaunchedEffect(editingSubTaskId) {
        val activeId = editingSubTaskId ?: return@LaunchedEffect
        val index = localSubTasks.indexOfFirst { it.id == activeId }
        if (index >= 0) {
            reorderableState.listState.animateScrollToItem(headerItemCount + index)
        }
    }

    val themeColor = if (task.isDone) extendedColors.ganttDoneText else getTaskThemeColor(task.type, category)
    val surfaceColor = if (task.isDone) MaterialTheme.colorScheme.surfaceVariant else getTaskSurfaceColor(task.type, category)

    // 关闭动画
    fun closeSheet() {
        commitActiveSubTaskEdit()
        isClosing = true
    }

    // 监听关闭动画完成
    LaunchedEffect(isClosing) {
        if (isClosing) {
            delay(300)
            onDismiss()
        }
    }

    // 使用 Dialog 确保最上层显示
    Dialog(
        onDismissRequest = { closeSheet() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,  // 全宽
            decorFitsSystemWindows = true     // 让系统处理 insets，避免键盘动画冲突
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // 背景遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(onClick = { closeSheet() })
            )

            // Bottom Sheet 内容
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(animatedHeight)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 16.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 拖拽手柄 - 实时跟随手指
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { 
                                        isDragging = true
                                        dragOffset = 0f 
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        val closeThreshold = screenHeightPx * 0.25f
                                        val expandThreshold = 30f
                                        
                                        when {
                                            // 下滑超过25%则关闭
                                            dragOffset > closeThreshold -> {
                                                closeSheet()
                                            }
                                            // 上滑超过阈值则展开到全屏
                                            dragOffset < -expandThreshold && expandState == SheetExpandState.HALF -> {
                                                expandState = SheetExpandState.FULL
                                                dragOffset = 0f
                                            }
                                            // 下滑超过阈值则收缩到半屏
                                            dragOffset > expandThreshold && expandState == SheetExpandState.FULL -> {
                                                expandState = SheetExpandState.HALF
                                                dragOffset = 0f
                                            }
                                            // 否则回弹
                                            else -> {
                                                dragOffset = 0f
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        dragOffset = 0f
                                    },
                                    onDrag = { _, dragAmount ->
                                        // 限制最大拖拽距离为屏幕65%
                                        dragOffset = (dragOffset + dragAmount.y)
                                            .coerceIn(-screenHeightPx * 0.3f, screenHeightPx * 0.65f)
                                    }
                                )
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .background(extendedColors.divider, RoundedCornerShape(2.dp))
                        )
                    }

                    // --- Header ---
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                // Task Type Badge & Focus Badge
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Surface(
                                        color = surfaceColor,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = category?.name ?: getTaskTypeName(task.type),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = themeColor,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    if (task.totalFocusSeconds > 0L) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Timer,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "已专注 ${formatFocusDurationShort(task.totalFocusSeconds)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (task.isDone) Color.Gray else MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (task.isDone) TextDecoration.LineThrough else null
                                )
                            }

                            IconButton(
                                onClick = { closeSheet() },
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.close),
                                    tint = extendedColors.textTertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // --- Scrollable Content ---
                    LazyColumn(
                        state = reorderableState.listState,
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .reorderable(reorderableState)
                    ) {
                        item(key = "detail_header") {
                            Spacer(modifier = Modifier.height(16.dp))

                            // 1. Time Info Card
                            TimeDisplayCard(
                                startTime = task.startTime,
                                deadline = task.deadline,
                                themeColor = themeColor,
                                isDone = task.isDone
                            )

                            // 1.5 Focus Timer Quick Entry
                            if (!task.isDone && onStartFocus != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    onClick = onStartFocus,
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Outlined.Timer,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "开启专注计时 / 番茄钟",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (task.totalFocusSeconds > 0L) {
                                                    Text(
                                                        text = "累计已专注 ${formatFocusDurationShort(task.totalFocusSeconds)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "开始专注",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. Reminders
                            if (reminders.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                CompactReminderRow(
                                    reminders = reminders,
                                    startTime = task.startTime,
                                    deadline = task.deadline,
                                    themeColor = themeColor
                                )
                            }

                            // 3. Description
                            if (!task.description.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(12.dp)) {
                                        Icon(
                                            Icons.Outlined.Description,
                                            contentDescription = null,
                                            tint = extendedColors.textTertiary,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .padding(top = 2.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = task.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 22.sp
                                        )
                                    }
                                }
                            }

                            // 3.5 Components
                            if (components.isNotEmpty() || onAddComponent != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "扩展组件",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (components.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    TaskComponentList(
                                        components = components,
                                        onRemove = { onDeleteComponent(it) },
                                        amapKey = amapKey,
                                        onGetWeather = onGetWeather,
                                        amapRouteActions = amapRouteActions,
                                        taskId = task.id,
                                        taskStartTimeMillis = task.startTime,
                                        taskDeadlineMillis = task.deadline,
                                        onUpdateAMapComponent = onUpdateComponent?.let { updateFn ->
                                            { comp, updatedData -> updateFn(comp.copy(data = updatedData)) }
                                        },
                                        onUpdateFileComponent = onUpdateComponent?.let { updateFn ->
                                            { comp, updatedData -> updateFn(comp.copy(data = updatedData)) }
                                        },
                                        onUpdateLinkComponent = onUpdateComponent?.let { updateFn ->
                                            { comp, updatedData -> updateFn(comp.copy(data = updatedData)) }
                                        },
                                        onUpdateAccessPassComponent = onUpdateComponent?.let { updateFn ->
                                            { comp, updatedData -> updateFn(comp.copy(data = updatedData)) }
                                        },
                                        onUpdateRecurrenceComponent = onUpdateComponent?.let { updateFn ->
                                            { comp, updatedData -> updateFn(comp.copy(data = updatedData)) }
                                        },
                                        onUpdateVoiceNoteComponent = onUpdateComponent?.let { updateFn ->
                                            { comp, updatedData -> updateFn(comp.copy(data = updatedData)) }
                                        },
                                        voiceNoteActions = voiceNoteActions
                                    )
                                }
                                if (onAddComponent != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val existingRecurrenceComp = components
                                        .filterIsInstance<com.litetask.app.data.model.TaskComponent.RecurrenceComponent>()
                                        .firstOrNull()
                                    TaskComponentAddBar(
                                        onAddAMap = { data ->
                                            onAddComponent(
                                                com.litetask.app.data.model.TaskComponent.AMapComponent(
                                                    id = 0L,
                                                    taskId = task.id,
                                                    data = data
                                                )
                                            )
                                        },
                                        onAddFile = { data ->
                                            onAddComponent(
                                                com.litetask.app.data.model.TaskComponent.FileComponent(
                                                    id = 0L,
                                                    taskId = task.id,
                                                    data = data
                                                )
                                            )
                                        },
                                        onAddLink = { data ->
                                            onAddComponent(
                                                com.litetask.app.data.model.TaskComponent.LinkComponent(
                                                    id = 0L,
                                                    taskId = task.id,
                                                    data = data
                                                )
                                            )
                                        },
                                        onAddAccessPass = { data ->
                                            onAddComponent(
                                                com.litetask.app.data.model.TaskComponent.AccessPassComponent(
                                                    id = 0L,
                                                    taskId = task.id,
                                                    data = data
                                                )
                                            )
                                        },
                                        onAddRecurrence = { data ->
                                            if (existingRecurrenceComp != null && onUpdateComponent != null) {
                                                onUpdateComponent(existingRecurrenceComp.copy(data = data))
                                            } else {
                                                onAddComponent(
                                                    com.litetask.app.data.model.TaskComponent.RecurrenceComponent(
                                                        id = 0L,
                                                        taskId = task.id,
                                                        data = data
                                                    )
                                                )
                                            }
                                        },
                                        onAddVoiceNote = { data ->
                                            onAddComponent(
                                                com.litetask.app.data.model.TaskComponent.VoiceNoteComponent(
                                                    id = 0L,
                                                    taskId = task.id,
                                                    data = data
                                                )
                                            )
                                        },
                                        existingRecurrenceRule = existingRecurrenceComp?.data,
                                        anchorStartTimeMs = task.startTime,
                                        anchorDeadlineMs = task.deadline,
                                        amapKey = amapKey,
                                        onGeocode = onGeocode,
                                        onSearchLocations = onSearchLocations,
                                        amapRouteActions = amapRouteActions,
                                        compact = true
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // 4. Subtasks Header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.subtasks_steps),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (localSubTasks.isNotEmpty()) {
                                    val completed = localSubTasks.count { it.isCompleted }
                                    Text(
                                        text = "$completed/${localSubTasks.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.Gray
                                    )
                                }
                                
                                Spacer(modifier = Modifier.weight(1f))
                                
                                // AI 子任务拆解入口（点击打开带上下文、快捷意图与参考图上传的拆解面板）
                                Surface(
                                    onClick = {
                                        if (!isGeneratingSubTasks) {
                                            commitActiveSubTaskEdit()
                                            onGenerateSubTasksWithContext(task)
                                        }
                                    },
                                    enabled = !isGeneratingSubTasks,
                                    shape = RoundedCornerShape(16.dp),
                                    color = themeColor.copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        if (isGeneratingSubTasks) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                color = themeColor,
                                                strokeWidth = 2.dp
                                            )
                                            Text(
                                                text = "拆解中…",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = themeColor
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.AISparkle,
                                                contentDescription = "AI 拆解子任务",
                                                tint = themeColor,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "AI 拆解",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = themeColor
                                            )
                                        }
                                    }
                                }
                            }

                            if (localSubTasks.isNotEmpty()) {
                                Text(
                                    text = "点圆圈完成 · 点文字修改 · 按住右侧手柄排序",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extendedColors.textTertiary.copy(alpha = 0.85f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // 5. Subtasks List
                        if (localSubTasks.isEmpty()) {
                            item(key = "subtask_empty") {
                                Text(
                                    text = stringResource(R.string.no_subtasks_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = extendedColors.textTertiary.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        } else {
                            items(
                                items = localSubTasks,
                                key = { "subtask_${it.id}" }
                            ) { subTask ->
                                val itemKey = "subtask_${subTask.id}"
                                val isEditingThis = editingSubTaskId == subTask.id
                                ReorderableItem(
                                    reorderableState = reorderableState,
                                    key = itemKey
                                ) { isItemDragging ->
                                    val placementModifier = if (isItemDragging) {
                                        Modifier
                                    } else {
                                        Modifier.animateItemPlacement(
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                    SubTaskItem(
                                        modifier = placementModifier,
                                        subTask = subTask,
                                        themeColor = themeColor,
                                        isDragging = isItemDragging,
                                        canReorder = localSubTasks.size > 1 && editingSubTaskId == null,
                                        isEditing = isEditingThis,
                                        editingValue = if (isEditingThis) editingSubTaskValue else TextFieldValue(""),
                                        dragHandleModifier = if (localSubTasks.size > 1 && editingSubTaskId == null) {
                                            Modifier.detectReorder(reorderableState)
                                        } else {
                                            Modifier
                                        },
                                        onToggleComplete = { completed ->
                                            commitActiveSubTaskEdit()
                                            onUpdateSubTask(subTask, completed)
                                        },
                                        onStartEdit = {
                                            if (!isReorderingSubTasks) {
                                                commitActiveSubTaskEdit()
                                                editingSubTaskId = subTask.id
                                                editingSubTaskValue = TextFieldValue(
                                                    text = subTask.content,
                                                    selection = TextRange(subTask.content.length)
                                                )
                                            }
                                        },
                                        onEditValueChange = { editingSubTaskValue = it },
                                        onCommitEdit = {
                                            keyboardController?.hide()
                                            commitActiveSubTaskEdit()
                                        },
                                        onCancelEdit = {
                                            keyboardController?.hide()
                                            editingSubTaskId = null
                                            editingSubTaskValue = TextFieldValue("")
                                        },
                                        onDelete = {
                                            if (editingSubTaskId == subTask.id) {
                                                editingSubTaskId = null
                                                editingSubTaskValue = TextFieldValue("")
                                            }
                                            onDeleteSubTask(subTask)
                                        }
                                    )
                                }
                            }
                        }

                        item(key = "detail_footer") {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // --- Fixed Bottom Actions ---
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding(),
                        color = MaterialTheme.colorScheme.surfaceContainer, // 使用 MD3 标准容器色
                        tonalElevation = 1.dp, // 使用色调海拔而不是重影
                        shadowElevation = 4.dp // 适度阴影
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 20.dp, top = 12.dp)
                        ) {
                            // Add Subtask Input
                            Surface(
                                shape = RoundedCornerShape(26.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh, // 略深的输入框背景
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextField(
                                        value = newSubTaskText,
                                        onValueChange = { newSubTaskText = it },
                                        placeholder = {
                                            Text(
                                                stringResource(R.string.add_subtask),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            cursorColor = themeColor
                                        ),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                if (newSubTaskText.isNotBlank()) {
                                                    commitActiveSubTaskEdit()
                                                    onAddSubTask(newSubTaskText.trim())
                                                    newSubTaskText = ""
                                                }
                                            }
                                        )
                                    )
                                    IconButton(
                                        onClick = {
                                            if (newSubTaskText.isNotBlank()) {
                                                commitActiveSubTaskEdit()
                                                onAddSubTask(newSubTaskText.trim())
                                                newSubTaskText = ""
                                            }
                                        },
                                        enabled = newSubTaskText.isNotBlank()
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowUpward,
                                            contentDescription = null,
                                            tint = if (newSubTaskText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                            modifier = Modifier
                                                .background(
                                                    if (newSubTaskText.isNotBlank()) themeColor else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    CircleShape
                                                )
                                                .padding(8.dp)
                                                .size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Bottom Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Delete Button
                                OutlinedButton(
                                    onClick = {
                                        onDelete(task)
                                        closeSheet()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.delete_task), style = MaterialTheme.typography.labelLarge)
                                }

                                // Complete/Reopen Button
                                Button(
                                    onClick = {
                                        val updatedTask = if (!task.isDone) {
                                            task.copy(isDone = true, isPinned = false, completedAt = null) 
                                        } else {
                                            task.copy(isDone = false, completedAt = null)
                                        }
                                        onUpdateTask(updatedTask)
                                        closeSheet()
                                    },
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (task.isDone) MaterialTheme.colorScheme.surfaceContainerHighest else themeColor,
                                        contentColor = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                ) {
                                    Icon(
                                        if (task.isDone) Icons.Default.Refresh else Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (task.isDone) stringResource(R.string.mark_undone) else stringResource(R.string.mark_done),
                                        style = MaterialTheme.typography.labelLarge,
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


// ==================== 辅助组件 ====================

@Composable
private fun TimeDisplayCard(
    startTime: Long,
    deadline: Long,
    themeColor: Color,
    isDone: Boolean
) {
    val durationMillis = deadline - startTime
    val days = TimeUnit.MILLISECONDS.toDays(durationMillis)
    val hours = TimeUnit.MILLISECONDS.toHours(durationMillis) % 24
    val extendedColors = LocalExtendedColors.current

    val now = System.currentTimeMillis()
    val isExpired = now > deadline
    val notStarted = now < startTime

    val statusColor = when {
        isDone -> extendedColors.ganttDoneText
        isExpired -> extendedColors.deadlineUrgent
        notStarted -> extendedColors.textTertiary
        else -> themeColor
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, extendedColors.divider),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TimeColumn(
                label = stringResource(R.string.start_time),
                time = startTime,
                isPrimary = false
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (days > 0) "${days}d ${hours}h" else "${hours}h",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Box(contentAlignment = Alignment.Center) {
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                    Icon(
                        Icons.Default.ArrowRightAlt,
                        contentDescription = null,
                        tint = statusColor
                    )
                }
            }

            TimeColumn(
                label = stringResource(R.string.deadline),
                time = deadline,
                isPrimary = true,
                isUrgent = !isDone && !isExpired && (deadline - now < 24 * 60 * 60 * 1000)
            )
        }
    }
}

@Composable
private fun TimeColumn(
    label: String,
    time: Long,
    isPrimary: Boolean,
    isUrgent: Boolean = false
) {
    val extendedColors = LocalExtendedColors.current

    Column(horizontalAlignment = if (isPrimary) Alignment.End else Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isUrgent) {
                Icon(
                    Icons.Outlined.Timer,
                    contentDescription = null,
                    tint = extendedColors.deadlineUrgent,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isUrgent) extendedColors.deadlineUrgent else extendedColors.textTertiary
            )
        }
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time)),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (isUrgent) extendedColors.deadlineUrgent else extendedColors.textPrimary
        )
        Text(
            text = SimpleDateFormat("MM/dd EEE", Locale.getDefault()).format(Date(time)),
            style = MaterialTheme.typography.bodySmall,
            color = if (isUrgent) extendedColors.deadlineUrgent else extendedColors.textTertiary
        )
    }
}

@Composable
private fun CompactReminderRow(
    reminders: List<Reminder>,
    startTime: Long,
    deadline: Long,
    themeColor: Color
) {
    val extendedColors = LocalExtendedColors.current

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Alarm,
                contentDescription = null,
                tint = extendedColors.textTertiary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.task_reminders),
                style = MaterialTheme.typography.labelMedium,
                color = extendedColors.textTertiary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(reminders.sortedBy { it.triggerAt }) { reminder ->
                val isFired = reminder.isFired
                val isPast = reminder.triggerAt < System.currentTimeMillis()

                val bgColor = when {
                    isFired -> MaterialTheme.colorScheme.surfaceVariant
                    isPast -> extendedColors.deadlineUrgentSurface
                    else -> themeColor.copy(alpha = 0.08f)
                }

                val contentColor = when {
                    isFired -> extendedColors.textTertiary
                    isPast -> extendedColors.deadlineUrgent
                    else -> themeColor
                }

                val label = getReminderLabel(reminder, startTime, deadline)
                val timeStr = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(reminder.triggerAt))

                Surface(
                    color = bgColor,
                    shape = RoundedCornerShape(8.dp),
                    border = if (isPast && !isFired) androidx.compose.foundation.BorderStroke(
                        1.dp,
                        contentColor.copy(alpha = 0.3f)
                    ) else null
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isFired) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = contentColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubTaskItem(
    modifier: Modifier = Modifier,
    subTask: SubTask,
    themeColor: Color,
    isDragging: Boolean = false,
    canReorder: Boolean = true,
    isEditing: Boolean = false,
    editingValue: TextFieldValue = TextFieldValue(""),
    dragHandleModifier: Modifier = Modifier,
    onToggleComplete: (Boolean) -> Unit,
    onStartEdit: () -> Unit = {},
    onEditValueChange: (TextFieldValue) -> Unit = {},
    onCommitEdit: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onDelete: () -> Unit
) {
    var isDeleting by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val extendedColors = LocalExtendedColors.current
    val focusRequester = remember { FocusRequester() }

    // 删除动画
    val dismissProgress by animateFloatAsState(
        targetValue = if (isDeleting) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "dismissProgress"
    )

    // 完成状态动画（拖拽时轻微放大提升质感）
    val completionScale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.02f
            subTask.isCompleted -> 0.98f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "completionScale"
    )

    val dragElevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        label = "dragElevation"
    )

    // Checkbox 动画
    val checkboxScale by animateFloatAsState(
        targetValue = if (subTask.isCompleted) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "checkboxScale"
    )

    // 背景色动画
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isDeleting -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            isDragging -> MaterialTheme.colorScheme.surfaceContainerHigh
            isEditing -> MaterialTheme.colorScheme.surfaceContainer
            subTask.isCompleted -> themeColor.copy(alpha = 0.05f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        animationSpec = tween(durationMillis = 220),
        label = "backgroundColor"
    )

    // 左侧指示条颜色
    val indicatorColor by animateColorAsState(
        targetValue = when {
            isDragging -> themeColor
            subTask.isCompleted -> themeColor.copy(alpha = 0.6f)
            else -> themeColor
        },
        animationSpec = tween(durationMillis = 250),
        label = "indicatorColor"
    )

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(isDeleting) {
        if (isDeleting) {
            delay(350)
            onDelete()
        }
    }

    AnimatedVisibility(
        visible = !isDeleting || dismissProgress < 1f,
        modifier = modifier,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut(animationSpec = tween(200)) +
               shrinkVertically(animationSpec = tween(350))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .graphicsLayer {
                    scaleX = completionScale
                    scaleY = completionScale
                    alpha = 1f - dismissProgress
                    translationX = dismissProgress * 100f
                }
        ) {
            Surface(
                color = backgroundColor,
                shape = RoundedCornerShape(14.dp),
                shadowElevation = dragElevation,
                border = if (isEditing || isDragging) {
                    BorderStroke(
                        width = if (isDragging) 1.5.dp else 1.dp,
                        color = themeColor.copy(alpha = if (isDragging) 0.55f else 0.45f)
                    )
                } else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧彩色指示条
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(indicatorColor)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // 自定义 Checkbox（仅点击圆圈切换完成状态）
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .graphicsLayer {
                                scaleX = checkboxScale
                                scaleY = checkboxScale
                            }
                            .clip(CircleShape)
                            .background(
                                if (subTask.isCompleted) themeColor.copy(alpha = 0.1f)
                                else Color.Transparent
                            )
                            .clickable(
                                enabled = !isDragging,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = true, color = themeColor)
                            ) { onToggleComplete(!subTask.isCompleted) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (subTask.isCompleted) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(themeColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "标记未完成",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color.Transparent, CircleShape)
                                    .drawBehind {
                                        drawCircle(
                                            color = themeColor.copy(alpha = 0.55f),
                                            radius = size.minDimension / 2,
                                            style = Stroke(width = 2.dp.toPx())
                                        )
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    if (isEditing) {
                        // 行内多行文本编辑
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            BasicTextField(
                                value = editingValue,
                                onValueChange = onEditValueChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = extendedColors.textPrimary
                                ),
                                cursorBrush = SolidColor(themeColor),
                                maxLines = 5,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = { onCommitEdit() }
                                )
                            )
                            if (editingValue.text.isEmpty()) {
                                Text(
                                    text = "输入子任务内容…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = extendedColors.textTertiary.copy(alpha = 0.5f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = onCommitEdit,
                            enabled = editingValue.text.isNotBlank(),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "保存修改",
                                tint = if (editingValue.text.isNotBlank()) themeColor else extendedColors.textTertiary.copy(alpha = 0.35f),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        IconButton(
                            onClick = onCancelEdit,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "取消修改",
                                tint = extendedColors.textTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        // 中间文字区：点击进入行内多行修改
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    enabled = !isDragging,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(bounded = true, color = themeColor)
                                ) { onStartEdit() }
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = subTask.content,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (subTask.isCompleted) FontWeight.Normal else FontWeight.Medium,
                                color = if (subTask.isCompleted) extendedColors.textTertiary else extendedColors.textPrimary,
                                textDecoration = if (subTask.isCompleted) TextDecoration.LineThrough else null
                            )

                            if (subTask.isCompleted) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "✓ " + stringResource(R.string.mark_done),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = themeColor.copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // 内侧：删除按钮（2秒二次确认）
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (showDeleteConfirm) MaterialTheme.colorScheme.errorContainer
                                    else Color.Transparent
                                )
                                .clickable(enabled = !isDragging) {
                                    if (showDeleteConfirm) {
                                        isDeleting = true
                                    } else {
                                        showDeleteConfirm = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (showDeleteConfirm) Icons.Default.DeleteForever else Icons.Default.Close,
                                contentDescription = stringResource(R.string.delete),
                                tint = if (showDeleteConfirm) MaterialTheme.colorScheme.error else extendedColors.divider,
                                modifier = Modifier.size(if (showDeleteConfirm) 19.dp else 15.dp)
                            )
                        }

                        // 最右侧：拖拽手柄（按住直接上下拖排，避免与滚动或删除误触）
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .then(dragHandleModifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "拖拽排序",
                                tint = when {
                                    isDragging -> themeColor
                                    canReorder -> extendedColors.textTertiary.copy(alpha = 0.65f)
                                    else -> extendedColors.divider.copy(alpha = 0.35f)
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(showDeleteConfirm) {
        if (showDeleteConfirm) {
            delay(2000)
            showDeleteConfirm = false
        }
    }
}

@Composable
private fun getTaskTypeName(type: TaskType): String {
    return when (type) {
        TaskType.WORK -> stringResource(R.string.task_type_work)
        TaskType.LIFE -> stringResource(R.string.task_type_life)
        TaskType.URGENT -> stringResource(R.string.task_type_urgent)
        TaskType.STUDY -> stringResource(R.string.task_type_study)
    }
}

@Composable
private fun getReminderLabel(reminder: Reminder, startTime: Long, deadline: Long): String {
    val triggerAt = reminder.triggerAt
    if (!reminder.label.isNullOrEmpty()) return reminder.label

    return when {
        triggerAt == startTime -> stringResource(R.string.reminder_at_start)
        triggerAt == startTime - 60 * 60 * 1000 -> stringResource(R.string.reminder_before_start_1h)
        triggerAt == startTime - 24 * 60 * 60 * 1000 -> stringResource(R.string.reminder_before_start_1d)
        triggerAt == deadline - 60 * 60 * 1000 -> stringResource(R.string.reminder_before_end_1h)
        triggerAt == deadline - 24 * 60 * 60 * 1000 -> stringResource(R.string.reminder_before_end_1d)
        else -> {
            val diffFromStart = startTime - triggerAt
            val diffFromEnd = deadline - triggerAt
            when {
                diffFromStart > 0 -> formatTimeDiff(diffFromStart, true)
                diffFromEnd > 0 -> formatTimeDiff(diffFromEnd, false)
                else -> stringResource(R.string.reminder_custom)
            }
        }
    }
}

@Composable
private fun formatTimeDiff(diffMillis: Long, isBeforeStart: Boolean): String {
    val minutes = diffMillis / (60 * 1000)
    val hours = diffMillis / (60 * 60 * 1000)
    val days = diffMillis / (24 * 60 * 60 * 1000)

    val baseStr = if (isBeforeStart) stringResource(R.string.reminder_before_start) else stringResource(R.string.reminder_before_end)

    return when {
        days > 0 -> "$baseStr${days}${stringResource(R.string.reminder_unit_days)}"
        hours > 0 -> "$baseStr${hours}${stringResource(R.string.reminder_unit_hours)}"
        else -> "$baseStr${minutes}${stringResource(R.string.reminder_unit_minutes)}"
    }
}
