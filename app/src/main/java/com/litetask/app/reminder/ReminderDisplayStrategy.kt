package com.litetask.app.reminder

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.litetask.app.data.model.TaskType

/**
 * 提醒显示策略管理器
 * 
 * 根据设备状态（屏幕、锁屏）和权限情况，智能选择最佳的提醒显示方式
 * 
 * 统一策略：无论息屏还是亮屏，都使用 ReminderActivity 全屏显示
 */
object ReminderDisplayStrategy {
    
    private const val TAG = "ReminderDisplayStrategy"
    
    /**
     * 设备状态
     */
    data class DeviceState(
        val isScreenOn: Boolean,        // 屏幕是否亮着
        val isLocked: Boolean,          // 是否锁屏
        val hasNotificationPermission: Boolean  // 是否有通知权限
    ) {
        override fun toString(): String {
            return "DeviceState(screen=${if(isScreenOn) "ON" else "OFF"}, " +
                   "lock=${if(isLocked) "LOCKED" else "UNLOCKED"}, " +
                   "notification=${if(hasNotificationPermission) "✓" else "✗"})"
        }
    }
    
    /**
     * 显示策略
     */
    enum class DisplayMethod {
        NOTIFICATION_AND_ACTIVITY,  // 通知 + Activity（统一使用）
        NONE                        // 无法显示（权限不足）
    }
    
    /**
     * 显示决策结果
     */
    data class DisplayDecision(
        val method: DisplayMethod,
        val reason: String,
        val shouldWakeScreen: Boolean,      // 是否需要唤醒屏幕
        val shouldShowOnLockScreen: Boolean // 是否需要在锁屏上显示
    )
    
    /**
     * 获取当前设备状态
     */
    fun getDeviceState(context: Context): DeviceState {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        
        val isScreenOn = powerManager.isInteractive
        val isLocked = keyguardManager.isKeyguardLocked
        val hasNotificationPermission = PermissionHelper.hasNotificationPermission(context)
        
        return DeviceState(isScreenOn, isLocked, hasNotificationPermission)
    }
    
    /**
     * 决定显示策略
     * 
     * 统一策略：
     * 1. 无论息屏还是亮屏，统一使用 Activity 全屏显示
     * 2. 同时发送系统通知作为补充
     * 3. 根据屏幕和锁屏状态调整 Activity 行为
     */
    fun decideDisplayMethod(context: Context): DisplayDecision {
        val state = getDeviceState(context)
        Log.i(TAG, "Device state: $state")
        
        // 场景1: 无通知权限 - 无法显示任何提醒
        if (!state.hasNotificationPermission) {
            Log.w(TAG, "No notification permission, cannot show reminder")
            return DisplayDecision(
                method = DisplayMethod.NONE,
                reason = "缺少通知权限",
                shouldWakeScreen = false,
                shouldShowOnLockScreen = false
            )
        }
        
        // 场景2: 息屏状态 - 需要唤醒并全屏显示
        if (!state.isScreenOn) {
            Log.i(TAG, "Screen OFF → Use Notification + Activity (wake screen)")
            return DisplayDecision(
                method = DisplayMethod.NOTIFICATION_AND_ACTIVITY,
                reason = "息屏状态，需要唤醒屏幕并全屏显示",
                shouldWakeScreen = true,
                shouldShowOnLockScreen = state.isLocked
            )
        }
        
        // 场景3: 锁屏状态（屏幕亮着）- 在锁屏上显示
        if (state.isLocked) {
            Log.i(TAG, "Screen ON + LOCKED → Use Notification + Activity (on lock screen)")
            return DisplayDecision(
                method = DisplayMethod.NOTIFICATION_AND_ACTIVITY,
                reason = "锁屏状态，在锁屏界面上显示",
                shouldWakeScreen = false,
                shouldShowOnLockScreen = true
            )
        }
        
        // 场景4: 正常使用（屏幕亮 + 未锁屏）- 也使用全屏 Activity
        Log.i(TAG, "Screen ON + UNLOCKED → Use Notification + Activity (full screen)")
        return DisplayDecision(
            method = DisplayMethod.NOTIFICATION_AND_ACTIVITY,
            reason = "亮屏未锁状态，全屏显示提醒",
            shouldWakeScreen = false,
            shouldShowOnLockScreen = false
        )
    }
    
    /**
     * 执行显示策略
     */
    fun executeDisplay(
        context: Context,
        taskId: Long,
        taskTitle: String,
        reminderText: String,
        taskType: TaskType,
        isDeadline: Boolean,
        categoryName: String? = null,
        categoryColor: String? = null
    ) {
        val decision = decideDisplayMethod(context)
        Log.i(TAG, "★ Display decision: ${decision.method} - ${decision.reason}")
        
        when (decision.method) {
            DisplayMethod.NONE -> {
                Log.e(TAG, "Cannot display reminder: ${decision.reason}")
            }
            
            DisplayMethod.NOTIFICATION_AND_ACTIVITY -> {
                // 统一策略：通知 + Activity
                // 1. 先发送通知（带全屏意图）
                NotificationHelper.showReminderNotification(
                    context, taskId, taskTitle, reminderText, taskType, categoryName, categoryColor
                )
                
                // 2. 延迟启动 Activity（补充机制）
                // 某些系统可能不会自动触发全屏意图，手动启动确保显示
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        ReminderActivity.start(
                            context, taskId, taskTitle, reminderText, taskType, isDeadline, categoryName, categoryColor
                        )
                        Log.i(TAG, "✓ Activity started")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start Activity: ${e.message}")
                    }
                }, 500)
            }
        }
    }
    
    /**
     * 获取权限缺失提示
     */
    fun getMissingPermissionsMessage(context: Context): String? {
        val state = getDeviceState(context)
        
        return when {
            !state.hasNotificationPermission -> "需要通知权限才能显示提醒"
            else -> null
        }
    }
}
