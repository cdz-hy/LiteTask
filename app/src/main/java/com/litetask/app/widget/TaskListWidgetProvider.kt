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
import android.widget.RemoteViews
import android.widget.Toast
import com.litetask.app.MainActivity
import com.litetask.app.R
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
         * 刷新所有任务列表小组件
         */
        fun refreshAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, TaskListWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            // 通知数据变化
            appWidgetIds.forEach { widgetId ->
                appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.task_list)
            }
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
                    handleToggleTask(context, taskId)
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
    
    private fun handleToggleTask(context: Context, taskId: Long) {
        scope.launch(Dispatchers.IO) {
            val dao = com.litetask.app.data.local.AppDatabase.getInstance(context).taskDao()
            
            // 检查是否是刚完成的任务（需要撤销）
            if (TaskListWidgetService.isJustCompleted(taskId)) {
                // 撤销完成操作
                val success = WidgetDataProvider.markTaskUndone(context, taskId)
                if (success) {
                    // 从缓存中移除
                    TaskListWidgetService.undoTaskCompletion(taskId)
                    
                    // 显示撤销提示
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context,
                            context.getString(R.string.widget_task_undone),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    
                    // 刷新所有小组件
                    refreshAllWidgets(context)
                    GanttWidgetProvider.refreshAllWidgets(context)
                    DeadlineWidgetProvider.refreshAllWidgets(context)
                } else {
                    Handler(Looper.getMainLooper()).post {
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
                
                if (task != null && !task.isDone) {
                    val success = WidgetDataProvider.markTaskDone(context, taskId)
                    if (success) {
                        // 缓存任务数据用于显示完成状态
                        TaskListWidgetService.markTaskAsJustCompleted(task)
                        
                        // 显示完成提示
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                context,
                                context.getString(R.string.widget_task_completed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        
                        // 立即刷新小组件，显示已完成状态
                        refreshAllWidgets(context)
                        GanttWidgetProvider.refreshAllWidgets(context)
                        DeadlineWidgetProvider.refreshAllWidgets(context)
                        
                        // 2秒后再次刷新，将已完成任务从列表移除
                        kotlinx.coroutines.delay(2000)
                        refreshAllWidgets(context)
                        GanttWidgetProvider.refreshAllWidgets(context)
                        DeadlineWidgetProvider.refreshAllWidgets(context)
                    } else {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                context,
                                context.getString(R.string.widget_task_complete_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context,
                            context.getString(R.string.widget_task_complete_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
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
