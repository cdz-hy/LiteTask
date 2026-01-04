package com.litetask.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import com.litetask.app.MainActivity
import com.litetask.app.R
import com.litetask.app.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 任务列表小组件 Provider
 * 
 * 功能：
 * - 显示所有未完成任务
 * - 支持滑动浏览
 * - 点击任务右侧圆形按钮标记完成
 * - 完成后有延迟消失效果（容错时间）
 * - 点击添加按钮跳转到主界面添加任务
 * - 点击任务项跳转到任务详情
 */
class TaskListWidgetProvider : AppWidgetProvider() {
    
    companion object {
        const val ACTION_TOGGLE_TASK = "com.litetask.app.widget.ACTION_TOGGLE_TASK"
        const val ACTION_REFRESH = "com.litetask.app.widget.ACTION_REFRESH_TASK_LIST"
        const val EXTRA_TASK_ID = "extra_task_id"
        
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        
        /**
         * 刷新所有任务列表小组件（强制立即刷新）
         */
        fun refreshAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, TaskListWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            // 通知数据变化，强制刷新列表
            appWidgetIds.forEach { widgetId ->
                appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.task_list)
            }
            
            Log.d("TaskListWidget", "refreshAllWidgets called, ${appWidgetIds.size} widgets")
        }
    }
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_TOGGLE_TASK -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
                if (taskId != -1L) {
                    // 使用 goAsync() 确保在后台被杀死时也能完成操作
                    val pendingResult = goAsync()
                    handleToggleTask(context, taskId, pendingResult)
                }
            }
            ACTION_REFRESH -> {
                refreshAllWidgets(context)
            }
        }
    }
    
    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_task_list)
        
        // 设置 RemoteViewsService
        val serviceIntent = Intent(context, TaskListWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.task_list, serviceIntent)
        views.setEmptyView(R.id.task_list, R.id.empty_view)
        
        // 设置点击头部打开主界面
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_header, openAppPendingIntent)
        
        // 设置添加按钮点击事件
        val addTaskIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "add_task")
        }
        val addTaskPendingIntent = PendingIntent.getActivity(
            context, 1, addTaskIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_add_task, addTaskPendingIntent)
        
        // 设置列表项点击模板（用于标记完成）
        val toggleIntent = Intent(context, TaskListWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE_TASK
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val togglePendingIntent = PendingIntent.getBroadcast(
            context, 0, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        views.setPendingIntentTemplate(R.id.task_list, togglePendingIntent)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
    
    private fun handleToggleTask(context: Context, taskId: Long, pendingResult: PendingResult) {
        scope.launch(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getInstance(context).taskDao()
                
                Log.d("TaskListWidget", "handleToggleTask: taskId=$taskId, isJustCompleted=${TaskListWidgetService.isJustCompleted(taskId)}")
                
                // 检查是否是刚完成的任务（需要撤销）
                if (TaskListWidgetService.isJustCompleted(taskId)) {
                    // 先从缓存中移除，确保下次刷新时显示未完成状态
                    TaskListWidgetService.undoTaskCompletion(taskId)
                    
                    // 立即刷新任务列表小组件（只刷新这一个，响应更快）
                    Handler(Looper.getMainLooper()).post {
                        refreshAllWidgets(context)
                    }
                    
                    // 撤销完成操作（数据库操作在后台进行）
                    val success = WidgetDataProvider.markTaskUndone(context, taskId)
                    if (success) {
                        Log.d("TaskListWidget", "Task undone: $taskId")
                        
                        // 显示撤销提示
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                context,
                                context.getString(R.string.widget_task_undone),
                                Toast.LENGTH_SHORT
                            ).show()
                            // 刷新其他小组件
                            GanttWidgetProvider.refreshAllWidgets(context)
                            DeadlineWidgetProvider.refreshAllWidgets(context)
                        }
                    } else {
                        // 恢复缓存（撤销失败）
                        val task = dao.getTaskById(taskId)
                        if (task != null) {
                            TaskListWidgetService.markTaskAsJustCompleted(task)
                        }
                        
                        Log.e("TaskListWidget", "Failed to undo task: $taskId")
                        Handler(Looper.getMainLooper()).post {
                            refreshAllWidgets(context) // 恢复显示
                            Toast.makeText(
                                context,
                                context.getString(R.string.widget_task_complete_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    // 标记完成操作
                    val task = dao.getTaskById(taskId)
                    
                    Log.d("TaskListWidget", "Task found: ${task?.title}, isDone=${task?.isDone}")
                    
                    if (task != null && !task.isDone) {
                        // 先缓存任务数据，确保刷新时立即显示完成状态
                        TaskListWidgetService.markTaskAsJustCompleted(task)
                        
                        // 立即刷新任务列表小组件（只刷新这一个，响应更快）
                        Handler(Looper.getMainLooper()).post {
                            refreshAllWidgets(context)
                        }
                        
                        // 数据库操作在后台进行
                        val success = WidgetDataProvider.markTaskDone(context, taskId)
                        if (success) {
                            Log.d("TaskListWidget", "Task marked done: $taskId")
                            
                            // 显示完成提示
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.widget_task_completed),
                                    Toast.LENGTH_SHORT
                                ).show()
                                // 刷新其他小组件
                                GanttWidgetProvider.refreshAllWidgets(context)
                                DeadlineWidgetProvider.refreshAllWidgets(context)
                            }
                            
                            // 2秒后再次刷新，将已完成任务从列表移除
                            kotlinx.coroutines.delay(2000)
                            Handler(Looper.getMainLooper()).post {
                                refreshAllWidgets(context)
                                GanttWidgetProvider.refreshAllWidgets(context)
                                DeadlineWidgetProvider.refreshAllWidgets(context)
                            }
                        } else {
                            // 移除缓存（标记失败）
                            TaskListWidgetService.undoTaskCompletion(taskId)
                            
                            Log.e("TaskListWidget", "Failed to mark task done: $taskId")
                            Handler(Looper.getMainLooper()).post {
                                refreshAllWidgets(context) // 恢复显示
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.widget_task_complete_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        Log.e("TaskListWidget", "Task not found or already done: $taskId")
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                context,
                                context.getString(R.string.widget_task_complete_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TaskListWidget", "Error in handleToggleTask", e)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        context.getString(R.string.widget_task_complete_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                // 完成异步操作
                pendingResult.finish()
            }
        }
    }
    
    override fun onEnabled(context: Context) {
        // 小组件首次添加时
    }
    
    override fun onDisabled(context: Context) {
        // 最后一个小组件被移除时
    }
}
