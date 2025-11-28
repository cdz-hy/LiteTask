package com.litetask.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.litetask.app.data.model.Task
import com.litetask.app.ui.components.*
import com.litetask.app.ui.theme.Primary
import com.litetask.app.ui.theme.OnPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var selectedTask by remember { mutableStateOf<Task?>(null) }

    // 录音相关状态
    val context = LocalContext.current
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording()
        }
    }

    Scaffold(
        containerColor = Color(0xFFF2F6FC),
        topBar = {
            Column(modifier = Modifier.background(Color(0xFFF2F6FC))) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "LiteTask",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF1F1F1F)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = SimpleDateFormat("MM月dd日", Locale.getDefault()).format(Date()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF444746)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.size(4.dp).background(Color(0xFF9CA3AF), CircleShape))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "${tasks.size} 个任务",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF444746)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { /* TODO: Menu */ }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color(0xFF1F1F1F))
                        }
                    },
                    actions = {
                        Box(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFC2E7FF))
                                .clickable { /* Profile */ },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "L",
                                color = Color(0xFF001D35),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF2F6FC))
                )
                
                // View Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(Color(0xFFEEF2F6), CircleShape)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ViewOption(
                        text = "列表",
                        icon = Icons.Default.List,
                        isSelected = currentView == "timeline",
                        onClick = { currentView = "timeline" },
                        modifier = Modifier.weight(1f)
                    )
                    ViewOption(
                        text = "甘特",
                        icon = Icons.Default.ViewTimeline, // Using ViewTimeline as Gantt icon proxy
                        isSelected = currentView == "gantt",
                        onClick = { currentView = "gantt" },
                        modifier = Modifier.weight(1f)
                    )
                    ViewOption(
                        text = "截止",
                        icon = Icons.Default.Flag,
                        isSelected = currentView == "deadline",
                        onClick = { currentView = "deadline" },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Manual Add Button (Small)
                SmallFloatingActionButton(
                    onClick = { showAddTaskDialog = true },
                    containerColor = Color(0xFFC2E7FF),
                    contentColor = Color(0xFF001D35),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Task")
                }

                // Voice Add Button (Large)
                ExtendedFloatingActionButton(
                    onClick = { 
                        permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    },
                    icon = { Icon(Icons.Default.Mic, contentDescription = "Voice") },
                    text = { Text("语音安排") },
                    containerColor = Primary,
                    contentColor = OnPrimary,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(64.dp)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            when (currentView) {
                "timeline" -> TimelineView(
                    tasks = tasks,
                    onTaskClick = { selectedTask = it }
                )
                "gantt" -> GanttView(
                    tasks = tasks,
                    onTaskClick = { selectedTask = it }
                )
                "deadline" -> DeadlineView(
                    tasks = tasks,
                    onTaskClick = { selectedTask = it }
                )
            }
        }

        // Dialogs
        if (showAddTaskDialog) {
            AddTaskDialog(
                onDismiss = { showAddTaskDialog = false },
                onConfirm = { task ->
                    viewModel.addTask(task)
                    showAddTaskDialog = false
                }
            )
        }

        selectedTask?.let { task ->
            TaskDetailSheet(
                task = task,
                onDismiss = { selectedTask = null },
                onDelete = { 
                    viewModel.deleteTask(it)
                    selectedTask = null
                },
                onUpdate = {
                    viewModel.updateTask(it)
                    selectedTask = null // Close sheet after update? Or keep open? Let's close for now.
                }
            )
        }

        // Voice Recording Dialog
        if (uiState.isRecording) {
            VoiceRecorderDialog(
                onDismiss = { viewModel.stopRecording() }
            )
        }

        // AI Result Sheet
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
fun ViewOption(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) Color.White else Color.Transparent
    val contentColor = if (isSelected) Primary else Color(0xFF444746)
    val shadowElevation = if (isSelected) 2.dp else 0.dp

    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = CircleShape,
        color = backgroundColor,
        shadowElevation = shadowElevation
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}