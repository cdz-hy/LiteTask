package com.litetask.app.ui.backup

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.litetask.app.ui.components.CalendarSyncGuideDialog
import com.litetask.app.ui.components.CalendarSyncSettingsPanel
import com.litetask.app.ui.components.formatSyncTime
import com.litetask.app.ui.components.rememberCalendarPermissionRequester
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataBackupScreen(
    onNavigateBack: () -> Unit,
    viewModel: DataBackupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val calendarState by viewModel.calendarSyncStore.state.collectAsState()
    val scope = rememberCoroutineScope()

    // 防抖状态
    var isNavigating by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }
    var showSyncSettingsSheet by remember { mutableStateOf(false) }

    val runWithCalendarPermission = rememberCalendarPermissionRequester(viewModel.calendarSyncStore)
    
    // 导出文件选择器（.zip 完整包，包含数据库与本地受管附件）
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.let { outputStream ->
                        viewModel.exportData(outputStream)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 导入文件选择器
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.openInputStream(uri)?.let { inputStream ->
                        viewModel.importData(inputStream)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 导出 ICS 日历文件选择器
    val exportIcsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.let { outputStream ->
                        viewModel.exportIcs(outputStream)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "ICS 导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 导入 ICS 日历文件选择器
    val importIcsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.openInputStream(uri)?.let { inputStream ->
                        viewModel.importIcs(inputStream)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "ICS 导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 处理 UI 状态变化
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is BackupUiState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            is BackupUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据管理", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!isNavigating) {
                                isNavigating = true
                                onNavigateBack()
                            }
                        },
                        enabled = !isNavigating
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSyncSettingsSheet = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "日历同步设置")
                    }
                    IconButton(onClick = { showGuideDialog = true }) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "日历互通说明")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "备份与恢复",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                BackupActionCard(
                    title = "导出完整数据备份 (.zip)",
                    description = "把全部任务、受管文件附件、分类、专注统计、常去地点、个人画像、AI 历史与日程建议打包为本地压缩包（.zip），换机也不丢失附件。",
                    icon = Icons.Default.CloudUpload,
                    onClick = {
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                        exportLauncher.launch("LiteTask_Backup_$timestamp.zip")
                    }
                )
            }

            item {
                BackupActionCard(
                    title = "从备份文件恢复",
                    description = "支持新版 .zip 完整备份包（自动还原附件文件）与旧版 .json 备份文件，已有任务和常去地点会自动合并去重。",
                    icon = Icons.Default.CloudDownload,
                    onClick = {
                        importLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "*/*"))
                    }
                )
            }

            item {
                val targetCalendarSummary = remember(
                    calendarState.targetCalendarId,
                    calendarState.targetCalendarName,
                    calendarState.writableCalendars
                ) {
                    if (calendarState.targetCalendarId <= 0L) {
                        "自动选择主日历（无账户则创建 LiteTask）"
                    } else {
                        calendarState.writableCalendars
                            .firstOrNull { it.id == calendarState.targetCalendarId }
                            ?.let { "${it.displayName} (${it.accountName})" }
                            ?: calendarState.targetCalendarName.ifBlank { "自动选择主日历" }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "日历互通与当前策略",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Surface(
                                onClick = {
                                    if (!calendarState.hasCalendarPermission) {
                                        runWithCalendarPermission {}
                                    } else {
                                        showSyncSettingsSheet = true
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (calendarState.hasCalendarPermission) {
                                    Color(0xFF4CAF50).copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (calendarState.hasCalendarPermission) {
                                            Icons.Default.CheckCircle
                                        } else {
                                            Icons.Default.WarningAmber
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (calendarState.hasCalendarPermission) {
                                            Color(0xFF2E7D32)
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (calendarState.hasCalendarPermission) "日历已授权" else "待授权限",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (calendarState.hasCalendarPermission) {
                                            Color(0xFF2E7D32)
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        }
                                    )
                                }
                            }
                        }

                        Text(
                            text = "目标系统日历：$targetCalendarSummary",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "同步策略：自动推送 ${if (calendarState.autoPushEnabled) "已开启" else "未开启"} · " +
                                "导入未来 ${calendarState.pullDaysAhead} 天 · " +
                                "同步提醒 ${if (calendarState.syncReminders) "开启" else "关闭"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "最近同步：推送 ${formatSyncTime(calendarState.lastPushAt)} · 导入 ${formatSyncTime(calendarState.lastPullAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                        ) {
                            OutlinedButton(onClick = { showGuideDialog = true }) {
                                Text("完整说明")
                            }
                            FilledTonalButton(onClick = { showSyncSettingsSheet = true }) {
                                Text("同步设置")
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "通用日历文件导入与导出",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                BackupActionCard(
                    title = "导出为通用日历文件 (.ics)",
                    description = "生成苹果日历、Google 日历、Outlook 等都能打开的日程文件（${if (calendarState.icsExportIncludeDone) "包含已完成/已过期任务" else "只导进行中任务"}，${if (calendarState.syncReminders) "附带提醒时间" else "不附带提醒"}）。",
                    icon = Icons.Default.CalendarMonth,
                    onClick = {
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                        exportIcsLauncher.launch("LiteTask_Schedule_$timestamp.ics")
                    }
                )
            }

            item {
                BackupActionCard(
                    title = "从日历文件导入日程",
                    description = "从其他日历软件导出的文件导入日程并自动恢复提醒；软件里已有同时间同名任务会自动跳过。",
                    icon = Icons.Default.FileDownload,
                    onClick = {
                        importIcsLauncher.launch(arrayOf("text/calendar", "application/ics", "*/*"))
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "手机系统日历双向同步",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                val pushTargetLabel = remember(calendarState.targetCalendarId, calendarState.targetCalendarName, calendarState.writableCalendars) {
                    if (calendarState.targetCalendarId <= 0L) {
                        "默认手机日历"
                    } else {
                        calendarState.writableCalendars
                            .firstOrNull { it.id == calendarState.targetCalendarId }
                            ?.displayName
                            ?: calendarState.targetCalendarName.ifBlank { "默认手机日历" }
                    }
                }
                BackupActionCard(
                    title = "同步任务到手机日历",
                    description = "把${if (calendarState.pushIncludeDone) "全部任务（已完成会自动标出）" else "进行中任务"}写入「$pushTargetLabel」" +
                        "（修改后再次同步会直接更新原条目${if (calendarState.syncReminders) "，附带系统提醒" else ""}）。",
                    icon = Icons.Default.Sync,
                    onClick = {
                        runWithCalendarPermission {
                            scope.launch { viewModel.pushToSystemCalendar() }
                        }
                    }
                )
            }

            item {
                BackupActionCard(
                    title = "从系统日历导入日程",
                    description = "读取系统日历未来 ${calendarState.pullDaysAhead} 天内的外部日程并导入 LiteTask（自动过滤本应用同步事件并防重）。",
                    icon = Icons.Default.EventAvailable,
                    onClick = {
                        runWithCalendarPermission {
                            scope.launch { viewModel.importFromSystemCalendar() }
                        }
                    }
                )
            }
            
            if (uiState is BackupUiState.Loading || uiState is BackupUiState.Importing) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    if (showSyncSettingsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSyncSettingsSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "日历互通与同步设置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                CalendarSyncSettingsPanel(
                    store = viewModel.calendarSyncStore,
                    onRequestCalendarPermission = { runWithCalendarPermission {} },
                    onOpenGuide = {
                        showSyncSettingsSheet = false
                        showGuideDialog = true
                    }
                )
            }
        }
    }

    if (showGuideDialog) {
        CalendarSyncGuideDialog(onDismiss = { showGuideDialog = false })
    }
}

@Composable
fun BackupActionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
