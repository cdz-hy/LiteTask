package com.litetask.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.litetask.app.data.model.Task
import com.litetask.app.ui.components.AddTaskDialog
import com.litetask.app.ui.components.TaskDetailSheet
import com.litetask.app.ui.home.HomeViewModel
import com.litetask.app.ui.theme.LiteTaskTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LiteTaskTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent()
                }
            }
        }
    }
}

@Composable
fun AppContent() {
    var currentScreen by remember { mutableStateOf("home") }
    var selectedTaskId by remember { mutableStateOf<Long?>(null) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    when (currentScreen) {
        "home" -> {
            val viewModel: HomeViewModel = hiltViewModel()
            
            com.litetask.app.ui.home.HomeScreen(
                onNavigateToAdd = { currentScreen = "add_task" },
                onNavigateToSettings = { currentScreen = "settings" },
                onNavigateToSearch = { currentScreen = "search" },
                viewModel = viewModel
            )
        }
        "settings" -> {
            com.litetask.app.ui.settings.SettingsScreen(
                onBack = { currentScreen = "home" }
            )
        }
        "search" -> {
            val homeViewModel: HomeViewModel = hiltViewModel()
            
            // 处理返回键
            BackHandler {
                currentScreen = "home"
            }
            
            com.litetask.app.ui.search.SearchScreen(
                onBack = { currentScreen = "home" },
                onTaskClick = { task ->
                    selectedTaskId = task.id
                },
                onDeleteClick = { task ->
                    homeViewModel.deleteTask(task)
                    Toast.makeText(context, "任务已删除", Toast.LENGTH_SHORT).show()
                },
                onEditClick = { task ->
                    taskToEdit = task
                    showEditDialog = true
                },
                onPinClick = { task ->
                    if (task.isDone) {
                        Toast.makeText(context, "已完成的任务不能置顶", Toast.LENGTH_SHORT).show()
                    } else if (task.deadline < System.currentTimeMillis()) {
                        Toast.makeText(context, "已过期的任务不能置顶", Toast.LENGTH_SHORT).show()
                    } else {
                        homeViewModel.updateTask(task.copy(isPinned = !task.isPinned))
                        Toast.makeText(
                            context,
                            if (task.isPinned) "已取消置顶" else "已置顶",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
            
            // 任务详情 Sheet
            selectedTaskId?.let { taskId ->
                val taskComposite by produceState<com.litetask.app.data.model.TaskDetailComposite?>(
                    initialValue = null,
                    key1 = taskId
                ) {
                    homeViewModel.getTaskDetailFlow(taskId).collect { composite ->
                        value = composite
                    }
                }
                
                taskComposite?.let { composite ->
                    TaskDetailSheet(
                        task = composite.task,
                        subTasks = composite.subTasks,
                        onDismiss = { selectedTaskId = null },
                        onDelete = { 
                            homeViewModel.deleteTask(it)
                            selectedTaskId = null
                            Toast.makeText(context, "任务已删除", Toast.LENGTH_SHORT).show()
                        },
                        onUpdateTask = { homeViewModel.updateTask(it) },
                        onUpdateSubTask = { sub, isCompleted -> 
                            homeViewModel.updateSubTaskStatus(sub.id, isCompleted)
                        },
                        onAddSubTask = { content ->
                            homeViewModel.addSubTask(composite.task.id, content)
                        },
                        onDeleteSubTask = { subTask ->
                            homeViewModel.deleteSubTask(subTask)
                        }
                    )
                }
            }
            
            // 编辑对话框
            if (showEditDialog && taskToEdit != null) {
                AddTaskDialog(
                    initialTask = taskToEdit,
                    onDismiss = { 
                        showEditDialog = false
                        taskToEdit = null
                    },
                    onConfirm = { task ->
                        homeViewModel.updateTask(task)
                        showEditDialog = false
                        taskToEdit = null
                        Toast.makeText(context, "任务已更新", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}