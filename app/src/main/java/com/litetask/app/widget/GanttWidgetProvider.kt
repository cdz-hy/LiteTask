package com.litetask.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.litetask.app.MainActivity
import com.litetask.app.R
import com.litetask.app.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * 甘特图小组件 Provider
 * 
 * 功能：
 * - 显示今日任务的进度条
 * - 支持滑动浏览
 * - 显示任务完成进度
 * - 点击跳转到主界面甘特视图
 */
class GanttWidgetProvider : AppWidgetProvider() {
    
    companion object {
        const val ACTION_REFRESH = "com.litetask.app.widget.ACTION_REFRESH_GANTT"
        
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        
        /**
         * 刷新所有甘特图小组件
         */
        fun refreshAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, GanttWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            appWidgetIds.forEach { widgetId ->
                appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.gantt_list)
                // 同时更新进度徽章
                updateProgressBadge(context, appWidgetManager, widgetId)
            }
        }
        
        private fun updateProgressBadge(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            scope.launch(Dispatchers.IO) {
                try {
                    val dao = AppDatabase.getInstance(context).taskDao()
                    val (done, total) = dao.getTodayTasksProgress()
                    
                    val views = RemoteViews(context.packageName, R.layout.widget_gantt)
                    val progressText = context.getString(R.string.widget_progress_format, done, total)
                    views.setTextViewText(R.id.progress_badge, progressText)
                    
                    appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    // 忽略错误
                }
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
        
        if (intent.action == ACTION_REFRESH) {
            refreshAllWidgets(context)
        }
    }
    
    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_gantt)
        
        // 设置 RemoteViewsService
        val serviceIntent = Intent(context, GanttWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.gantt_list, serviceIntent)
        views.setEmptyView(R.id.gantt_list, R.id.empty_view)
        
        // 设置进度徽章
        runBlocking(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getInstance(context).taskDao()
                val (done, total) = dao.getTodayTasksProgress()
                val progressText = context.getString(R.string.widget_progress_format, done, total)
                views.setTextViewText(R.id.progress_badge, progressText)
            } catch (e: Exception) {
                views.setTextViewText(R.id.progress_badge, "0/0 完成")
            }
        }
        
        // 设置点击头部打开主界面甘特视图
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("view", "gantt")
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 2, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_header, openAppPendingIntent)
        
        // 设置列表项点击模板
        val itemClickIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("view", "gantt")
        }
        val itemClickPendingIntent = PendingIntent.getActivity(
            context, 3, itemClickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setPendingIntentTemplate(R.id.gantt_list, itemClickPendingIntent)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
        
        // 触发数据刷新，确保系统自动更新时也会重新查询数据库
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.gantt_list)
    }
    
    override fun onEnabled(context: Context) {
        // 小组件首次添加时
    }
    
    override fun onDisabled(context: Context) {
        // 最后一个小组件被移除时
    }
}
