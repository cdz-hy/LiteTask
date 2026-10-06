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
import com.litetask.app.data.local.PreferenceManager
import com.litetask.app.reminder.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

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
        const val ACTION_ITEM_CLICK = "com.litetask.app.widget.ACTION_ITEM_CLICK"
        const val ACTION_CYCLE_FILTER = "com.litetask.app.widget.ACTION_CYCLE_FILTER"
        const val ACTION_REFRESH = "com.litetask.app.widget.ACTION_REFRESH_TASK_LIST"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_CLICK_TYPE = "extra_click_type"
        const val CLICK_TYPE_TOGGLE = "toggle"
        const val CLICK_TYPE_OPEN_DETAIL = "open_detail"
        
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
        
        /**
         * 快速刷新（仅更新UI状态，不重新加载数据）
         * 用于撤销操作等需要即时反馈的场景
         */
        fun quickRefreshAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, TaskListWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            // 强制触发 onDataSetChanged，但由于缓存机制，只会更新状态不会重新查询数据库
            appWidgetIds.forEach { widgetId ->
                appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.task_list)
            }
            
            Log.d("TaskListWidget", "quickRefreshAllWidgets called, ${appWidgetIds.size} widgets")
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
                    val pendingResult = goAsync()
                    handleToggleTask(context, taskId, pendingResult)
                }
            }
            ACTION_ITEM_CLICK -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                val clickType = intent.getStringExtra(EXTRA_CLICK_TYPE)
                if (taskId != -1L) {
                    if (clickType == CLICK_TYPE_OPEN_DETAIL) {
                        val openDetailIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra(NotificationHelper.EXTRA_FROM_NOTIFICATION, true)
                            putExtra(NotificationHelper.EXTRA_TASK_ID, taskId)
                        }
                        context.startActivity(openDetailIntent)
                    } else {
                        val pendingResult = goAsync()
                        handleToggleTask(context, taskId, pendingResult)
                    }
                }
            }
            ACTION_CYCLE_FILTER -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    cycleWidgetFilter(context, appWidgetId)
                }
            }
            ACTION_REFRESH -> {
                refreshAllWidgets(context)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val prefs = PreferenceManager(context.applicationContext)
        appWidgetIds.forEach { widgetId ->
            prefs.clearWidgetFilter(widgetId)
        }
    }

    private fun cycleWidgetFilter(context: Context, appWidgetId: Int) {
        val prefs = PreferenceManager(context.applicationContext)
        val categories = runBlocking(Dispatchers.IO) {
            try {
                AppDatabase.getInstance(context).categoryDao().getAllCategoriesSync()
            } catch (e: Exception) {
                emptyList()
            }
        }
        val states = buildList {
            add(PreferenceManager.WIDGET_FILTER_ALL)
            add(PreferenceManager.WIDGET_FILTER_TODAY)
            categories.forEach { add("${PreferenceManager.WIDGET_FILTER_CAT_PREFIX}${it.id}") }
        }
        val current = prefs.getWidgetFilter(appWidgetId)
        val currentIdx = states.indexOf(current)
        val nextFilter = if (currentIdx < 0) states.first() else states[(currentIdx + 1) % states.size]
        prefs.setWidgetFilter(appWidgetId, nextFilter)
        updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
    }

    private fun resolveFilterLabel(context: Context, appWidgetId: Int): String {
        val prefs = PreferenceManager(context.applicationContext)
        val filter = prefs.getWidgetFilter(appWidgetId)
        return when {
            filter == PreferenceManager.WIDGET_FILTER_ALL -> context.getString(R.string.widget_filter_all)
            filter == PreferenceManager.WIDGET_FILTER_TODAY -> context.getString(R.string.widget_filter_today)
            filter.startsWith(PreferenceManager.WIDGET_FILTER_CAT_PREFIX) -> {
                val catId = filter.removePrefix(PreferenceManager.WIDGET_FILTER_CAT_PREFIX).toLongOrNull()
                val categoryName = if (catId != null) {
                    runBlocking(Dispatchers.IO) {
                        try {
                            AppDatabase.getInstance(context).categoryDao().getCategoryById(catId)?.name
                        } catch (_: Exception) {
                            null
                        }
                    }
                } else null
                if (categoryName != null) {
                    categoryName
                } else {
                    prefs.setWidgetFilter(appWidgetId, PreferenceManager.WIDGET_FILTER_ALL)
                    context.getString(R.string.widget_filter_all)
                }
            }
            else -> context.getString(R.string.widget_filter_all)
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

        // 设置筛选胶囊文字与点击切换逻辑
        views.setTextViewText(R.id.btn_widget_filter, resolveFilterLabel(context, appWidgetId))
        val filterIntent = Intent(context, TaskListWidgetProvider::class.java).apply {
            action = ACTION_CYCLE_FILTER
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val filterPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId * 10 + 1,
            filterIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_filter, filterPendingIntent)
        
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
        
        // 设置列表项点击模板（区分为勾选完成与打开详情）
        val itemClickIntent = Intent(context, TaskListWidgetProvider::class.java).apply {
            action = ACTION_ITEM_CLICK
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val itemClickPendingIntent = PendingIntent.getBroadcast(
            context, appWidgetId * 10 + 2, itemClickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        views.setPendingIntentTemplate(R.id.task_list, itemClickPendingIntent)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
        
        // 触发数据刷新，确保系统自动更新时也会重新查询数据库
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.task_list)
    }
    
    private fun handleToggleTask(context: Context, taskId: Long, pendingResult: PendingResult) {
        // 使用新线程 + runBlocking，确保在 App 进程被杀后也能正常执行
        thread {
            try {
                Log.d("TaskListWidget", "handleToggleTask: taskId=$taskId, isJustCompleted=${TaskListWidgetService.isJustCompleted(taskId)}")
                
                // 检查是否是刚完成的任务（需要撤销）
                if (TaskListWidgetService.isJustCompleted(taskId)) {
                    // 撤销完成操作 - 优化流程：立即更新UI，然后执行数据库操作
                    
                    // 1. 立即移除缓存并刷新UI（提供即时反馈）
                    TaskListWidgetService.undoTaskCompletion(taskId)
                    Handler(Looper.getMainLooper()).post {
                        quickRefreshAllWidgets(context)
                        Toast.makeText(
                            context,
                            context.getString(R.string.widget_task_undone),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    
                    // 2. 然后执行数据库操作
                    val success = runBlocking {
                        WidgetDataProvider.markTaskUndone(context, taskId)
                    }
                    
                    if (success) {
                        Log.d("TaskListWidget", "Task undone: $taskId")
                        // 数据库操作成功，刷新其他小组件
                        Handler(Looper.getMainLooper()).post {
                            GanttWidgetProvider.refreshAllWidgets(context)
                            DeadlineWidgetProvider.refreshAllWidgets(context)
                        }
                    } else {
                        // 数据库操作失败，恢复缓存状态
                        Log.e("TaskListWidget", "Failed to undo task: $taskId")
                        val task = runBlocking { WidgetDataProvider.getTaskById(context, taskId) }
                        if (task != null) {
                            TaskListWidgetService.markTaskAsJustCompleted(task)
                        }
                        Handler(Looper.getMainLooper()).post {
                            quickRefreshAllWidgets(context)
                            Toast.makeText(
                                context,
                                context.getString(R.string.widget_task_complete_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    // 标记完成操作
                    val task = runBlocking { WidgetDataProvider.getTaskById(context, taskId) }
                    
                    Log.d("TaskListWidget", "Task found: ${task?.title}, isDone=${task?.isDone}")
                    
                    if (task != null && !task.isDone) {
                        // 1. 先缓存任务数据，立即刷新显示完成状态
                        TaskListWidgetService.markTaskAsJustCompleted(task)
                        Handler(Looper.getMainLooper()).post {
                            quickRefreshAllWidgets(context)
                            Toast.makeText(
                                context,
                                context.getString(R.string.widget_task_completed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        
                        // 2. 数据库操作（异步执行，不阻塞UI）
                        val success = runBlocking {
                            WidgetDataProvider.markTaskDone(context, taskId)
                        }
                        
                        if (success) {
                            Log.d("TaskListWidget", "Task marked done: $taskId")
                            
                            // 刷新其他小组件
                            Handler(Looper.getMainLooper()).post {
                                GanttWidgetProvider.refreshAllWidgets(context)
                                DeadlineWidgetProvider.refreshAllWidgets(context)
                            }
                            
                            // 等待2秒容错时间后移除已完成任务（在 pendingResult.finish() 前等待，防止进程被提前回收）
                            Thread.sleep(2000)
                            Handler(Looper.getMainLooper()).post {
                                refreshAllWidgets(context)
                                GanttWidgetProvider.refreshAllWidgets(context)
                                DeadlineWidgetProvider.refreshAllWidgets(context)
                            }
                        } else {
                            // 数据库操作失败，移除缓存恢复未完成状态
                            TaskListWidgetService.undoTaskCompletion(taskId)
                            
                            Log.e("TaskListWidget", "Failed to mark task done: $taskId")
                            Handler(Looper.getMainLooper()).post {
                                quickRefreshAllWidgets(context)
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
