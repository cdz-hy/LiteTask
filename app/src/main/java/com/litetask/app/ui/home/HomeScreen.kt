package com.litetask.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.litetask.app.ui.components.DeadlineView
import com.litetask.app.ui.components.GanttView
import com.litetask.app.ui.components.TimelineView
import com.litetask.app.ui.components.VoiceRecorderDialog
import com.litetask.app.ui.components.TaskConfirmationSheet
import com.litetask.app.ui.components.WeekCalendar
import com.litetask.app.ui.theme.Primary
import com.litetask.app.ui.theme.OnPrimary
import com.litetask.app.ui.theme.SurfaceContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAdd: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var currentView by remember { mutableStateOf("timeline") } // timeline, gantt, deadline

    // 录音相关状态
    val context = LocalContext.current
    // 简单的权限请求 (实际应处理拒绝情况)
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限授予，开始录音逻辑
            viewModel.startRecording()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "LiteTask",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Normal
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text("11月21日", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                " • ${tasks.size} 个任务",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { /* TODO: 打开菜单 */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    // 用户头像占位符
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFC2E7FF))
                    ) {
                        Text(
                            "L",
                            modifier = Modifier.align(Alignment.Center),
                            color = Color(0xFF001D35),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { 
                    permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                },
                icon = { Icon(Icons.Default.Mic, contentDescription = "Voice") },
                text = { Text("语音安排") },
                containerColor = Primary,
                contentColor = OnPrimary,
                modifier = Modifier.padding(16.dp)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 日历条
            val selectedDate by viewModel.selectedDate.collectAsState()
            WeekCalendar(
                selectedDate = selectedDate,
                onDateSelected = { viewModel.onDateSelected(it) },
                modifier = Modifier
                    .background(Color.White)
                    .padding(bottom = 8.dp)
            )
            
            // 视图切换器
            ViewSwitcher(
                currentView = currentView,
                onViewSelected = { currentView = it }
            )
            
            // 内容区域
            Box(modifier = Modifier.weight(1f)) {
                when (currentView) {
                    "timeline" -> TimelineView(
                        tasks = tasks,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 80.dp) // 为FAB留出空间
                    )
                    "gantt" -> GanttView(
                        tasks = tasks,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 80.dp)
                    )
                    "deadline" -> DeadlineView(
                        tasks = tasks,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 80.dp)
                    )
                }
            }
        }

        // 弹窗层
        if (uiState.isRecording) {
            VoiceRecorderDialog(
                onDismiss = { /* Stop Recording logic if needed, or just dismiss UI */ }
            )
        }

        if (uiState.showAiResult) {
            TaskConfirmationSheet(
                tasks = uiState.aiParsedTasks,
                onDismiss = { viewModel.dismissAiResult() },
                onConfirm = { viewModel.confirmAddTasks() }
            )
        }
    }
}

@Composable
fun ViewSwitcher(
    currentView: String,
    onViewSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(SurfaceContainer, RoundedCornerShape(24.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ViewOption(
            text = "列表",
            isSelected = currentView == "timeline",
            onClick = { onViewSelected("timeline") }
        )
        ViewOption(
            text = "甘特",
            isSelected = currentView == "gantt",
            onClick = { onViewSelected("gantt") }
        )
        ViewOption(
            text = "截止",
            isSelected = currentView == "deadline",
            onClick = { onViewSelected("deadline") }
        )
    }
}

@Composable
fun ViewOption(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .background(if (isSelected) Color.White else Color.Transparent)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}