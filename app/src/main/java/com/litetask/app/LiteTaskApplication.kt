package com.litetask.app

import android.app.Application
import android.content.res.Configuration
import android.util.Log
import com.litetask.app.data.local.AppDatabase
import com.litetask.app.reminder.NotificationHelper
import com.litetask.app.reminder.ReminderScheduler
import com.litetask.app.widget.WidgetUpdateHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * LiteTask 应用入口
 * 必须添加 @HiltAndroidApp 注解以启用 Hilt 依赖注入
 */
@HiltAndroidApp
class LiteTaskApplication : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        
        // 创建通知渠道
        NotificationHelper.createNotificationChannel(this)
        
        // 恢复所有待触发的提醒
        // 这是为了应对小米等国产 ROM 在杀后台时清除 AlarmManager 的问题
        restoreReminders()
        
        // 刷新所有小组件
        refreshWidgets()
    }
    
    /**
     * 刷新所有小组件
     * 
     * 应用启动时刷新小组件数据，确保显示最新状态
     */
    private fun refreshWidgets() {
        applicationScope.launch(Dispatchers.Main) {
            try {
                WidgetUpdateHelper.refreshAllWidgets(this@LiteTaskApplication)
                Log.d(TAG, "Widgets refreshed on app start")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing widgets: ${e.message}", e)
            }
        }
    }
    
    /**
     * 恢复所有待触发的提醒
     * 
     * 小米 MIUI 等国产 ROM 在用户手动杀掉后台时会清除 App 注册的所有闹钟，
     * 所以每次 App 启动时都需要重新注册。
     */
    private fun restoreReminders() {
        applicationScope.launch {
            try {
                val database = AppDatabase.getInstance(this@LiteTaskApplication)
                val taskDao = database.taskDao()
                val scheduler = ReminderScheduler(this@LiteTaskApplication)
                
                val now = System.currentTimeMillis()
                
                // 查询所有未触发且触发时间在未来的提醒
                val pendingReminders = taskDao.getFutureReminders(now)
                
                if (pendingReminders.isEmpty()) {
                    Log.d(TAG, "No pending reminders to restore on app start")
                    return@launch
                }
                
                var successCount = 0
                
                for (reminder in pendingReminders) {
                    val task = taskDao.getTaskByIdSync(reminder.taskId)
                    
                    // 跳过已完成或不存在的任务
                    if (task == null || task.isDone) {
                        continue
                    }
                    
                    // 使用带任务信息的方法注册
                    val success = scheduler.scheduleReminderWithTaskInfo(
                        reminder = reminder,
                        taskTitle = task.title,
                        taskType = task.type.name
                    )
                    
                    if (success) {
                        successCount++
                    }
                }
                
                if (successCount > 0) {
                    Log.i(TAG, "★ Restored $successCount/${pendingReminders.size} reminders on app start")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring reminders: ${e.message}", e)
            }
        }
    }
    
    companion object {
        private const val TAG = "LiteTaskApplication"
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 当系统配置变化时（包括夜间模式切换），强制刷新所有widget
        applicationScope.launch(Dispatchers.Main) {
            try {
                WidgetUpdateHelper.forceRefreshAllWidgets(this@LiteTaskApplication)
                Log.d(TAG, "Widgets force refreshed due to configuration change")
            } catch (e: Exception) {
                Log.e(TAG, "Error force refreshing widgets on config change: ${e.message}", e)
            }
        }
    }
}
