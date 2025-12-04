package com.litetask.app.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
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
import com.litetask.app.ui.theme.Primary
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

    Scaffold(
        containerColor = colorResource(R.color.background),
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorResource(R.color.background)
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
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                },
                trailingIcon = {
                    Row {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_all), tint = Color.Gray)
                            }
                        }
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.filter_criteria),
                                tint = if (selectedTypes.isNotEmpty() || dateRange != null) Primary else Color.Gray
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 筛选标签
            if (selectedTypes.isNotEmpty() || dateRange != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedTypes.forEach { type ->
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.toggleTypeFilter(type) },
                            label = { Text(getTaskTypeName(type)) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                    
                    dateRange?.let { range ->
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.clearDateRange() },
                            label = { Text(formatDateRange(range)) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 结果统计
            Text(
                text = stringResource(R.string.search_results_count, searchResults.size),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 8.dp)
            )

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
                                color = colorResource(R.color.on_surface)
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
                                onClick = { onTaskClick(composite.task) }
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
                                color = Color.LightGray.copy(alpha = 0.5f)
                            )
                            Text(
                                stringResource(R.string.done_tasks_count, doneTasks.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color.LightGray.copy(alpha = 0.5f)
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
                                onClick = { onTaskClick(composite.task) }
                            )
                        }
                    }
                }

                // 空状态
                if (searchResults.isEmpty() && searchQuery.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.Gray.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.no_matching_tasks),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Gray
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
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.filter_criteria),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onClearAll) {
                    Text(stringResource(R.string.clear_all))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 任务类型
            Text(
                stringResource(R.string.task_type),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // 使用 FlowRow 实现自动换行
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                searchableTaskTypes.forEach { type ->
                    FilterChip(
                        selected = type in selectedTypes,
                        onClick = { onTypeToggle(type) },
                        label = { 
                            Text(
                                getTaskTypeName(type),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) 
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 日期范围快捷选项
            Text(
                stringResource(R.string.date_range),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

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

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text(stringResource(R.string.apply_filter))
            }
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
    val isSelected = currentRange?.let { it.first == start && it.second == end } ?: false
    
    OutlinedButton(
        onClick = { onSelect(start, end) },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) Primary.copy(alpha = 0.1f) else Color.Transparent,
            contentColor = if (isSelected) Primary else Color.Gray
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) Primary else Color.LightGray
        )
    ) {
        Text(label)
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

// 搜索界面支持的任务类型（排除健康和开发）
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
