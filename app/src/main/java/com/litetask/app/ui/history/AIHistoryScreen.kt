package com.litetask.app.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.litetask.app.R
import com.litetask.app.data.model.AIHistory
import com.litetask.app.data.model.AIHistorySource
import com.litetask.app.data.model.DailyScheduleAdviceEntity
import com.litetask.app.ui.components.SegmentedTabBar
import com.litetask.app.ui.theme.LocalExtendedColors
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class AdvicePreviewItem(
    val title: String,
    val type: String,
    val content: String,
    val info: String
)

private data class ParsedAdviceSummary(
    val shortTerm: List<AdvicePreviewItem>,
    val longTerm: List<AdvicePreviewItem>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIHistoryScreen(
    onBack: () -> Unit,
    onReusePrompt: ((String) -> Unit)? = null,
    viewModel: AIHistoryViewModel = hiltViewModel()
) {
    val allHistoryItems by viewModel.allHistoryItems.collectAsState()
    val filteredHistoryItems by viewModel.filteredHistoryItems.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val failedHistoryCount by viewModel.failedHistoryCount.collectAsState()
    val adviceHistoryItems by viewModel.adviceHistoryItems.collectAsState()

    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 任务解析, 1: 日程建议
    var showClearConfirm by remember { mutableStateOf(false) }
    var showClearFailedConfirm by remember { mutableStateOf(false) }
    var showClearAdviceConfirm by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<AIHistory?>(null) }
    var itemToInspect by remember { mutableStateOf<AIHistory?>(null) }
    var adviceToDelete by remember { mutableStateOf<DailyScheduleAdviceEntity?>(null) }
    var adviceToInspect by remember { mutableStateOf<DailyScheduleAdviceEntity?>(null) }

    // 防抖状态
    var isNavigating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.ai_history),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!isNavigating) {
                                isNavigating = true
                                onBack()
                            }
                        },
                        enabled = !isNavigating
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        if (failedHistoryCount > 0) {
                            TextButton(onClick = { showClearFailedConfirm = true }) {
                                Text(
                                    text = stringResource(R.string.ai_history_clear_failed),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                        if (allHistoryItems.isNotEmpty()) {
                            IconButton(onClick = { showClearConfirm = true }) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = stringResource(R.string.ai_history_clear_all),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else if (adviceHistoryItems.isNotEmpty()) {
                        IconButton(onClick = { showClearAdviceConfirm = true }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "清空日程建议历史",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SegmentedTabBar(
                tabs = listOf("任务解析", "日程建议"),
                selectedIndex = selectedTab,
                onSelectTab = { selectedTab = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            if (selectedTab == 0) {
                TaskParseHistoryTab(
                    allCount = allHistoryItems.size,
                    failedCount = failedHistoryCount,
                    filteredItems = filteredHistoryItems,
                    selectedFilter = selectedFilter,
                    searchQuery = searchQuery,
                    onFilterSelected = viewModel::setFilter,
                    onSearchQueryChanged = viewModel::setSearchQuery,
                    onInspectItem = { itemToInspect = it },
                    onCopyText = { text ->
                        copyToClipboard(context, text)
                        Toast.makeText(context, R.string.ai_history_copy_success, Toast.LENGTH_SHORT).show()
                    },
                    onReusePrompt = onReusePrompt,
                    onDeleteItem = { itemToDelete = it }
                )
            } else {
                DailyAdviceHistoryTab(
                    adviceItems = adviceHistoryItems,
                    onInspectAdvice = { adviceToInspect = it },
                    onDeleteAdvice = { adviceToDelete = it }
                )
            }
        }
    }

    // 详情弹窗：查看完整输入/错误说明并支持复制或再次解析
    itemToInspect?.let { item ->
        AIHistoryDetailDialog(
            item = item,
            onDismiss = { itemToInspect = null },
            onCopy = { text ->
                copyToClipboard(context, text)
                Toast.makeText(context, R.string.ai_history_copy_success, Toast.LENGTH_SHORT).show()
            },
            onReusePrompt = onReusePrompt?.let { reuse ->
                if (item.sourceType != AIHistorySource.SUBTASK && item.content.isNotBlank()) {
                    {
                        itemToInspect = null
                        reuse(item.content)
                    }
                } else null
            }
        )
    }

    adviceToInspect?.let { advice ->
        DailyAdviceDetailDialog(
            advice = advice,
            onDismiss = { adviceToInspect = null },
            onCopyAll = { summaryText ->
                copyToClipboard(context, summaryText)
                Toast.makeText(context, R.string.ai_history_copy_success, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 清空任务解析历史确认
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.ai_history_clear_all)) },
            text = { Text(stringResource(R.string.ai_history_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 一键清理失败记录确认
    if (showClearFailedConfirm) {
        AlertDialog(
            onDismissRequest = { showClearFailedConfirm = false },
            title = { Text(stringResource(R.string.ai_history_clear_failed)) },
            text = { Text(stringResource(R.string.ai_history_clear_failed_confirm, failedHistoryCount)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearFailedHistory()
                        if (selectedFilter == AIHistoryFilter.FAILED) {
                            viewModel.setFilter(AIHistoryFilter.ALL)
                        }
                        showClearFailedConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearFailedConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 清空日程建议历史确认
    if (showClearAdviceConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAdviceConfirm = false },
            title = { Text("清空日程建议历史") },
            text = { Text("确认要清空所有历史日程建议吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllAdvice()
                        showClearAdviceConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAdviceConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 单条历史删除确认
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.ai_history_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteHistory(item)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 单条日程建议删除确认
    adviceToDelete?.let { advice ->
        AlertDialog(
            onDismissRequest = { adviceToDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text("确认删除这条历史日程建议吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAdvice(advice.id)
                        adviceToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { adviceToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun TaskParseHistoryTab(
    allCount: Int,
    failedCount: Int,
    filteredItems: List<AIHistory>,
    selectedFilter: AIHistoryFilter,
    searchQuery: String,
    onFilterSelected: (AIHistoryFilter) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onInspectItem: (AIHistory) -> Unit,
    onCopyText: (String) -> Unit,
    onReusePrompt: ((String) -> Unit)?,
    onDeleteItem: (AIHistory) -> Unit
) {
    if (allCount == 0) {
        EmptyHistoryView(
            icon = Icons.Default.History,
            message = stringResource(R.string.ai_history_empty),
            hint = "通过首页语音、文字或子任务拆解调用 AI 后，记录会保存在这里"
        )
        return
    }

    val groupedByDay = remember(filteredItems) {
        filteredItems.groupBy { formatHistoryDayHeader(it.timestamp) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索框
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            placeholder = {
                Text(
                    text = "搜索输入内容、主任务或错误原因",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Close, contentDescription = "清除搜索")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        // 分类与状态筛选栏
        val filterOptions = remember(allCount, failedCount) {
            listOf(
                AIHistoryFilter.ALL to "全部 ($allCount)",
                AIHistoryFilter.VOICE to "语音",
                AIHistoryFilter.TEXT to "文本",
                AIHistoryFilter.SUBTASK to "子任务拆解",
                AIHistoryFilter.FAILED to if (failedCount > 0) "失败 ($failedCount)" else "仅看失败"
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filterOptions, key = { it.first.name }) { (filter, label) ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterSelected(filter) },
                    label = { Text(label) }
                )
            }
        }

        if (filteredItems.isEmpty()) {
            EmptyHistoryView(
                icon = Icons.Default.Search,
                message = stringResource(R.string.ai_history_empty_filtered),
                hint = "可以尝试切换筛选标签或清空搜索关键词"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                groupedByDay.forEach { (dayLabel, dayItems) ->
                    item(key = "header_$dayLabel") {
                        Text(
                            text = dayLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp, start = 4.dp)
                        )
                    }
                    items(
                        items = dayItems,
                        key = { it.id }
                    ) { item ->
                        AIHistoryCard(
                            item = item,
                            onInspect = { onInspectItem(item) },
                            onCopy = {
                                val textToCopy = resolveCopyableText(item)
                                if (textToCopy.isNotEmpty()) {
                                    onCopyText(textToCopy)
                                }
                            },
                            onReusePrompt = onReusePrompt?.let { reuse ->
                                if (item.sourceType != AIHistorySource.SUBTASK && item.content.isNotBlank()) {
                                    { reuse(item.content) }
                                } else null
                            },
                            onDelete = { onDeleteItem(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AIHistoryCard(
    item: AIHistory,
    onInspect: () -> Unit,
    onCopy: () -> Unit,
    onReusePrompt: (() -> Unit)?,
    onDelete: () -> Unit
) {
    val extendedColors = LocalExtendedColors.current
    val (sourceName, sourceIcon, sourceColor) = getSourceDisplayInfo(item.sourceType)
    val copyableText = remember(item.content, item.targetTitle) { resolveCopyableText(item) }

    Card(
        onClick = onInspect,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Source + Time + Image count + Result badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = sourceColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = sourceIcon,
                            contentDescription = null,
                            tint = sourceColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = sourceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = sourceColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = formatClockTime(item.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = extendedColors.textTertiary
                )

                if (item.imageCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${item.imageCount}图",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                ResultStatusBadge(item = item)
            }

            if (!item.targetTitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Flag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "主任务：${item.targetTitle}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val displayBody = resolveHistoryDisplayText(item)
            val isPlaceholder = item.content.isBlank()
            Text(
                text = displayBody,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isPlaceholder) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            if (!item.isSuccess && !item.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "失败原因：${item.errorMessage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "点击查看详情",
                    style = MaterialTheme.typography.labelSmall,
                    color = extendedColors.textTertiary
                )

                Spacer(modifier = Modifier.weight(1f))

                if (onReusePrompt != null) {
                    TextButton(
                        onClick = onReusePrompt,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "重新解析",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                if (copyableText.isNotEmpty()) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "复制",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.delete),
                        tint = extendedColors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultStatusBadge(item: AIHistory) {
    val (bgColor, textColor, label) = when {
        !item.isSuccess -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            stringResource(R.string.ai_history_status_failed)
        )
        item.parsedCount > 0 -> Triple(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.onPrimaryContainer,
            if (item.sourceType == AIHistorySource.SUBTASK) {
                stringResource(R.string.ai_history_parsed_subtasks, item.parsedCount)
            } else {
                stringResource(R.string.ai_history_parsed_tasks, item.parsedCount)
            }
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.ai_history_parsed_zero)
        )
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun AIHistoryDetailDialog(
    item: AIHistory,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onReusePrompt: (() -> Unit)?
) {
    val (sourceName, sourceIcon, sourceColor) = getSourceDisplayInfo(item.sourceType)
    val copyableText = resolveCopyableText(item)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = sourceIcon,
                    contentDescription = null,
                    tint = sourceColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.ai_history_detail),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "$sourceName · ${formatFullDateTime(item.timestamp)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResultStatusBadge(item = item)
                    if (item.imageCount > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "附带 ${item.imageCount} 张图片",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (!item.targetTitle.isNullOrBlank()) {
                    Text(
                        text = "关联主任务：${item.targetTitle}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        Text(
                            text = resolveHistoryDisplayText(item),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (!item.isSuccess && !item.errorMessage.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                text = "失败原因：\n${item.errorMessage}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onReusePrompt != null) {
                    TextButton(onClick = onReusePrompt) {
                        Text(stringResource(R.string.ai_history_reuse_prompt))
                    }
                }
                if (copyableText.isNotEmpty()) {
                    TextButton(onClick = { onCopy(copyableText) }) {
                        Text("复制")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    )
}

@Composable
private fun DailyAdviceHistoryTab(
    adviceItems: List<DailyScheduleAdviceEntity>,
    onInspectAdvice: (DailyScheduleAdviceEntity) -> Unit,
    onDeleteAdvice: (DailyScheduleAdviceEntity) -> Unit
) {
    if (adviceItems.isEmpty()) {
        EmptyHistoryView(
            icon = Icons.Default.AutoAwesome,
            message = "暂无日程建议历史",
            hint = "点击首页右上角智能规划图标，生成的每日执行与规划建议会保存在这里"
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = adviceItems,
            key = { it.id }
        ) { advice ->
            DailyAdviceHistoryCard(
                advice = advice,
                onInspect = { onInspectAdvice(advice) },
                onDelete = { onDeleteAdvice(advice) }
            )
        }
    }
}

@Composable
private fun DailyAdviceHistoryCard(
    advice: DailyScheduleAdviceEntity,
    onInspect: () -> Unit,
    onDelete: () -> Unit
) {
    val extendedColors = LocalExtendedColors.current
    val parsed = remember(advice.id, advice.shortTermJson, advice.longTermJson) {
        parseAdviceSummary(advice)
    }

    Card(
        onClick = onInspect,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "${formatHistoryDayHeader(advice.createdAt)} ${formatClockTime(advice.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                val (statusBg, statusTextColor, statusLabel) = when (advice.generationStatus) {
                    "GENERATING" -> Triple(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        "生成中"
                    )
                    "FAILED" -> Triple(
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer,
                        "生成失败"
                    )
                    else -> Triple(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        "短期 ${parsed.shortTerm.size} 条 · 长期 ${parsed.longTerm.size} 条"
                    )
                }
                Surface(color = statusBg, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusTextColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (parsed.shortTerm.isEmpty() && parsed.longTerm.isEmpty()) {
                Text(
                    text = if (advice.generationStatus == "FAILED") "本次日程建议生成失败" else "暂无具体建议条目",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                parsed.shortTerm.take(2).forEach { entry ->
                    val prefix = entry.title.takeIf { it.isNotBlank() }?.let { "[$it] " } ?: ""
                    Text(
                        text = "• $prefix${entry.content}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                parsed.longTerm.take(1).forEach { entry ->
                    val prefix = entry.title.takeIf { it.isNotBlank() }?.let { "[$it] " } ?: ""
                    Text(
                        text = "• 长期：$prefix${entry.content}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "点击查看完整建议",
                    style = MaterialTheme.typography.labelSmall,
                    color = extendedColors.textTertiary
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.delete),
                        tint = extendedColors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyAdviceDetailDialog(
    advice: DailyScheduleAdviceEntity,
    onDismiss: () -> Unit,
    onCopyAll: (String) -> Unit
) {
    val parsed = remember(advice.id, advice.shortTermJson, advice.longTermJson) {
        parseAdviceSummary(advice)
    }
    val plainSummary = remember(parsed) {
        buildString {
            if (parsed.shortTerm.isNotEmpty()) {
                appendLine("【短期执行建议】")
                parsed.shortTerm.forEach {
                    val title = if (it.title.isNotBlank()) "${it.title}：" else ""
                    appendLine("• $title${it.content}")
                }
            }
            if (parsed.longTerm.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                appendLine("【长期规划策略】")
                parsed.longTerm.forEach {
                    val title = if (it.title.isNotBlank()) "${it.title}：" else ""
                    appendLine("• $title${it.content}")
                }
            }
        }.trim()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "历史日程建议",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatFullDateTime(advice.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (parsed.shortTerm.isNotEmpty()) {
                    Text(
                        text = "短期执行建议",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    parsed.shortTerm.forEach { item ->
                        AdviceDetailSubCard(item = item)
                    }
                }
                if (parsed.longTerm.isNotEmpty()) {
                    Text(
                        text = "长期规划策略",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    parsed.longTerm.forEach { item ->
                        AdviceDetailSubCard(item = item)
                    }
                }
                if (parsed.shortTerm.isEmpty() && parsed.longTerm.isEmpty()) {
                    Text(
                        text = "该记录暂无可展示的建议详情。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Row {
                if (plainSummary.isNotEmpty()) {
                    TextButton(onClick = { onCopyAll(plainSummary) }) {
                        Text("复制全部")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    )
}

@Composable
private fun AdviceDetailSubCard(item: AdvicePreviewItem) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (item.title.isNotBlank() || item.type.isNotBlank()) {
                    Text(
                        text = listOfNotNull(
                            item.title.takeIf { it.isNotBlank() },
                            item.type.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (item.content.isNotBlank()) {
                    Text(
                        text = item.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 19.sp
                    )
                }
                if (item.info.isNotBlank()) {
                    Text(
                        text = item.info,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryView(
    icon: ImageVector,
    message: String,
    hint: String
) {
    val extendedColors = LocalExtendedColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = extendedColors.textTertiary.copy(alpha = 0.35f)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = extendedColors.textTertiary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = extendedColors.textTertiary.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun getSourceDisplayInfo(source: AIHistorySource): Triple<String, ImageVector, Color> {
    val extendedColors = LocalExtendedColors.current
    return when (source) {
        AIHistorySource.VOICE -> Triple(
            stringResource(R.string.ai_history_source_voice),
            Icons.Default.Mic,
            extendedColors.lifeTask
        )
        AIHistorySource.TEXT -> Triple(
            stringResource(R.string.ai_history_source_text),
            Icons.Default.TextFields,
            extendedColors.workTask
        )
        AIHistorySource.SUBTASK -> Triple(
            stringResource(R.string.ai_history_source_subtask),
            Icons.AutoMirrored.Filled.ListAlt,
            extendedColors.studyTask
        )
    }
}

private fun resolveHistoryDisplayText(item: AIHistory): String {
    if (item.content.isNotBlank()) return item.content
    if (item.imageCount > 0) return "仅上传 ${item.imageCount} 张图片进行识图分析"
    if (item.sourceType == AIHistorySource.SUBTASK) return "一键智能拆解当前任务步骤"
    return "（无文本内容）"
}

private fun resolveCopyableText(item: AIHistory): String {
    return item.content.ifBlank { item.targetTitle.orEmpty() }.trim()
}

private fun formatHistoryDayHeader(timestamp: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }

    if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    ) {
        return "今天"
    }

    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    ) {
        return "昨天"
    }

    val pattern = if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR)) "M月d日" else "yyyy年M月d日"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}

private fun formatClockTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun formatFullDateTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

private fun parseAdviceSummary(advice: DailyScheduleAdviceEntity): ParsedAdviceSummary {
    val shortItems = runCatching {
        val array = JSONArray(advice.shortTermJson)
        List(array.length()) { idx ->
            val obj = array.getJSONObject(idx)
            AdvicePreviewItem(
                title = obj.optString("task_title"),
                type = obj.optString("advice_type"),
                content = obj.optString("content"),
                info = obj.optString("info")
            )
        }
    }.getOrDefault(emptyList())

    val longItems = runCatching {
        val array = JSONArray(advice.longTermJson)
        List(array.length()) { idx ->
            val obj = array.getJSONObject(idx)
            val urgency = obj.optString("urgency")
            AdvicePreviewItem(
                title = obj.optString("topic"),
                type = obj.optString("advice_type"),
                content = obj.optString("content"),
                info = if (urgency.isNotBlank()) "优先级: $urgency" else ""
            )
        }
    }.getOrDefault(emptyList())

    return ParsedAdviceSummary(shortTerm = shortItems, longTerm = longItems)
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("AI Content", text)
    clipboard.setPrimaryClip(clip)
}
