package com.litetask.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.litetask.app.R
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskType
import com.litetask.app.ui.components.HtmlStyleTaskCard
import com.litetask.app.ui.components.SwipeRevealItem
import com.litetask.app.ui.theme.LocalExtendedColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onTaskClick: (Task) -> Unit,
    onDeleteClick: (Task) -> Unit,
    onEditClick: (Task) -> Unit,
    onPinClick: (Task) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val selectedTypes by viewModel.selectedTypes.collectAsState()
    val dateRange by viewModel.dateRange.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    val extendedColors = LocalExtendedColors.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // 搜索框（与列表视图样式统一）
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(24.dp)),
                placeholder = { 
                    Text(
                        stringResource(R.string.search_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ) 
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    Row {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    Icons.Default.Clear, 
                                    contentDescription = stringResource(R.string.clear_all), 
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.filter_criteria),
                                tint = if (selectedTypes.isNotEmpty() || dateRange != null) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 筛选标签 - 使用 FlowRow 实现自动换行
            if (selectedTypes.isNotEmpty() || dateRange != null) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedTypes.forEach { type ->
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.toggleTypeFilter(type) },
                            label = { Text(getTaskTypeName(type), fontSize = 13.sp) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    
                    dateRange?.let { range ->
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.clearDateRange() },
                            label = { Text(formatDateRange(range), fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 结果统计
            if (searchQuery.isNotEmpty() || selectedTypes.isNotEmpty() || dateRange != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.search_results_count, searchResults.size),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 搜索结果列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 未完成任务
                val activeTasks = searchResults.filter { !it.task.isDone }
                if (activeTasks.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.active_tasks_count, activeTasks.size),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    items(activeTasks, key = { it.task.id }) { composite ->
                        SwipeRevealItem(
                            task = composite.task,
                            onDelete = { 
                                onDeleteClick(composite.task)
                            },
                            onEdit = { 
                                onEditClick(composite.task)
                            },
                            onPin = { 
                                onPinClick(composite.task)
                            }
                        ) {
                            HtmlStyleTaskCard(
                                composite = composite,
                                onClick = { onTaskClick(composite.task) },
                                onToggleDone = { 
                                    // 搜索结果中直接更新状态
                                    viewModel.toggleTaskDone(composite.task) 
                                }
                            )
                        }
                    }
                }

                // 已完成任务
                val doneTasks = searchResults.filter { it.task.isDone }
                if (doneTasks.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = extendedColors.divider.copy(alpha = 0.5f)
                            )
                            Text(
                                stringResource(R.string.done_tasks_count, doneTasks.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = extendedColors.textTertiary,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = extendedColors.divider.copy(alpha = 0.5f)
                            )
                        }
                    }
                    items(doneTasks, key = { it.task.id }) { composite ->
                        SwipeRevealItem(
                            task = composite.task,
                            onDelete = { 
                                onDeleteClick(composite.task)
                            },
                            onEdit = { 
                                onEditClick(composite.task)
                            },
                            onPin = { 
                                onPinClick(composite.task)
                            }
                        ) {
                            HtmlStyleTaskCard(
                                composite = composite,
                                onClick = { onTaskClick(composite.task) },
                                onToggleDone = { 
                                    viewModel.toggleTaskDone(composite.task) 
                                }
                            )
                        }
                    }
                }

                // 空状态
                if (searchResults.isEmpty() && (searchQuery.isNotEmpty() || selectedTypes.isNotEmpty() || dateRange != null)) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 64.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(96.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        Icons.Default.SearchOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                stringResource(R.string.no_matching_tasks),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.try_adjust_filters),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // 筛选面板
    if (showFilterSheet) {
        FilterBottomSheet(
            selectedTypes = selectedTypes,
            dateRange = dateRange,
            onTypeToggle = { viewModel.toggleTypeFilter(it) },
            onDateRangeSelected = { start, end -> viewModel.setDateRange(start, end) },
            onClearAll = { viewModel.clearAllFilters() },
            onDismiss = { showFilterSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    selectedTypes: Set<TaskType>,
    dateRange: Pair<Long, Long>?,
    onTypeToggle: (TaskType) -> Unit,
    onDateRangeSelected: (Long, Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val extendedColors = LocalExtendedColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), 
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.filter_criteria),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                TextButton(
                    onClick = onClearAll,
                    enabled = selectedTypes.isNotEmpty() || dateRange != null
                ) {
                    Text(
                        stringResource(R.string.clear_all),
                        color = if (selectedTypes.isNotEmpty() || dateRange != null) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 任务类型筛选
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            Icons.Default.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.task_type),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (selectedTypes.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${selectedTypes.size}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        searchableTaskTypes.forEach { type ->
                            FilterChip(
                                selected = type in selectedTypes,
                                onClick = { onTypeToggle(type) },
                                label = { 
                                    Text(
                                        getTaskTypeName(type),
                                        fontSize = 14.sp,
                                        fontWeight = if (type in selectedTypes) FontWeight.SemiBold else FontWeight.Normal
                                    ) 
                                },
                                leadingIcon = if (type in selectedTypes) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 日期范围筛选
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.date_range),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (dateRange != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(12.dp)
                                )
                            }
                        }
                    }

                    val calendar = Calendar.getInstance()
                    val today = calendar.timeInMillis
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateRangeOption(stringResource(R.string.today), today, today, dateRange, onDateRangeSelected)
                        
                        calendar.add(Calendar.DAY_OF_YEAR, -7)
                        DateRangeOption(stringResource(R.string.recent_7_days), calendar.timeInMillis, today, dateRange, onDateRangeSelected)
                        
                        calendar.timeInMillis = today
                        calendar.add(Calendar.DAY_OF_YEAR, -30)
                        DateRangeOption(stringResource(R.string.recent_30_days), calendar.timeInMillis, today, dateRange, onDateRangeSelected)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 应用按钮
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.apply_filter),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DateRangeOption(
    label: String,
    start: Long,
    end: Long,
    currentRange: Pair<Long, Long>?,
    onSelect: (Long, Long) -> Unit
) {
    // 使用日期比较（忽略时分秒），避免时间戳精度问题
    val isSelected = currentRange?.let { 
        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        val endCal = Calendar.getInstance().apply { timeInMillis = end }
        val currentStartCal = Calendar.getInstance().apply { timeInMillis = it.first }
        val currentEndCal = Calendar.getInstance().apply { timeInMillis = it.second }
        
        startCal.get(Calendar.YEAR) == currentStartCal.get(Calendar.YEAR) &&
        startCal.get(Calendar.DAY_OF_YEAR) == currentStartCal.get(Calendar.DAY_OF_YEAR) &&
        endCal.get(Calendar.YEAR) == currentEndCal.get(Calendar.YEAR) &&
        endCal.get(Calendar.DAY_OF_YEAR) == currentEndCal.get(Calendar.DAY_OF_YEAR)
    } ?: false
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(start, end) },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surfaceVariant,
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.date_range_selected),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                )
            }
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
        else -> ""
    }
}

// 搜索界面支持的任务类型
private val searchableTaskTypes = listOf(
    TaskType.WORK,
    TaskType.LIFE,
    TaskType.URGENT,
    TaskType.STUDY
)

private fun formatDateRange(range: Pair<Long, Long>): String {
    val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
    return "${sdf.format(Date(range.first))} - ${sdf.format(Date(range.second))}"
}
