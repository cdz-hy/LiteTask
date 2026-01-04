package com.litetask.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 小组件刷新广播接收器
 * 
 * 监听系统广播，在以下情况自动刷新小组件：
 * - 设备开机完成
 * - 时区变化
 * - 日期变化（午夜）
 * 
 * 这确保小组件在系统状态变化后显示正确的数据
 */
class WidgetRefreshReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                // 刷新所有小组件
                WidgetUpdateHelper.refreshAllWidgets(context)
            }
        }
    }
}
