package com.litetask.app.widget

import android.content.Context
import android.content.Intent

/**
 * 小组件更新助手
 * 
 * 提供统一的小组件刷新接口，
 * 在任务数据变化时调用以保持小组件数据同步
 */
object WidgetUpdateHelper {
    
    /**
     * 刷新所有小组件
     * 
     * 在以下场景调用：
     * - 任务创建/更新/删除
     * - 任务状态变化（完成/取消完成）
     * - 应用启动时
     */
    fun refreshAllWidgets(context: Context) {
        TaskListWidgetProvider.refreshAllWidgets(context)
        GanttWidgetProvider.refreshAllWidgets(context)
        DeadlineWidgetProvider.refreshAllWidgets(context)
    }
    
    /**
     * 仅刷新任务列表小组件
     */
    fun refreshTaskListWidget(context: Context) {
        TaskListWidgetProvider.refreshAllWidgets(context)
    }
    
    /**
     * 仅刷新甘特图小组件
     */
    fun refreshGanttWidget(context: Context) {
        GanttWidgetProvider.refreshAllWidgets(context)
    }
    
    /**
     * 仅刷新截止提醒小组件
     */
    fun refreshDeadlineWidget(context: Context) {
        DeadlineWidgetProvider.refreshAllWidgets(context)
    }
    
    /**
     * 强制刷新所有小组件（包括清除缓存）
     * 
     * 用于主题切换等需要完全重新渲染的场景
     */
    fun forceRefreshAllWidgets(context: Context) {
        // 先发送广播刷新
        sendRefreshBroadcast(context)
        
        // 再直接调用刷新方法
        refreshAllWidgets(context)
    }
    
    /**
     * 发送广播刷新所有小组件
     * 
     * 用于从非 UI 线程或 Service 中触发刷新
     */
    fun sendRefreshBroadcast(context: Context) {
        context.sendBroadcast(Intent(TaskListWidgetProvider.ACTION_REFRESH))
        context.sendBroadcast(Intent(GanttWidgetProvider.ACTION_REFRESH))
        context.sendBroadcast(Intent(DeadlineWidgetProvider.ACTION_REFRESH))
    }
}
