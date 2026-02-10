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
 */
object ReminderDisplayStrategy {
    
    private const val TAG = "ReminderDisplayStrategy"
    
    /**
     * 设备状态
     */
    data class DeviceState(
        val isScreenOn: Boolean,        // 屏幕是否亮着
        val isLocked: Boolean,          // 是否锁屏
        val hasNotificationPermission: Boolean,  // 是否有通知权限
        val hasOverlayPermission: Boolean        // 是否有悬浮窗权限
    ) {
        override fun toString(): String {
            return "DeviceState(screen=${if(isScreenOn) "ON" else "OFF"}, " +
                   "lock=${if(isLocked) "LOCKED" else "UNLOCKED"}, " +
                   "notification=${if(hasNotificationPermission) "✓" else "✗"}, " +
                   "overlay=${if(hasOverlayPermission) "✓" else "✗"})"
        }
    }
    
    /**
     * 显示策略
     */
    enum class DisplayMethod {
        NOTIFICATION_ONLY,           // 仅系统通知
        ACTIVITY_ONLY,              // 仅全屏 Activity
        NOTIFICATION_AND_ACTIVITY,  // 通知 + Activity（双保险）
        FLOATING_WINDOW,            // 悬浮窗（已废弃，保留备用）
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
        val hasOverlayPermission = hasOverlayPermission(context)
        
        return DeviceState(isScreenOn, isLocked, hasNotificationPermission, hasOverlayPermission)
    }
    
    /**
     * 决定显示策略
     * 
     * 决策逻辑：
     * 1. 优先使用系统通知（最可靠）
     * 2. 息屏/锁屏时使用 Activity 全屏显示（确保用户看到）
     * 3. 正常使用时只显示通知（不打断用户）
     */
    /**
     * 决定显示策略
     * 
     * 核心策略：优先使用强侵入性的“悬浮窗”，弱权限下回退到“系统通知”。
     * 无论何种方式，在息屏/锁屏状态下都必须触发亮屏。
     */
    fun decideDisplayMethod(context: Context): DisplayDecision {
        val state = getDeviceState(context)
        Log.i(TAG, "Device state: $state")

        // 分支 A: 有悬浮窗权限 (优先强提醒)
        if (state.hasOverlayPermission) {
            return if (!state.isScreenOn || state.isLocked) {
                // 息屏或锁屏 -> 唤醒屏幕 + 悬浮窗 (锁屏之上)
                DisplayDecision(
                    method = DisplayMethod.FLOATING_WINDOW,
                    reason = "有悬浮窗权限 + 息屏/锁屏 -> 强唤醒显示",
                    shouldWakeScreen = true,
                    shouldShowOnLockScreen = true
                )
            } else {
                // 亮屏解锁 -> 直接悬浮窗
                DisplayDecision(
                    method = DisplayMethod.FLOATING_WINDOW,
                    reason = "有悬浮窗权限 + 亮屏 -> 直接悬浮",
                    shouldWakeScreen = false,
                    shouldShowOnLockScreen = false
                )
            }
        }

        // 分支 B: 无悬浮窗权限 -> 降级为系统通知
        if (state.hasNotificationPermission) {
            return if (!state.isScreenOn || state.isLocked) {
                // 息屏或锁屏 -> 唤醒屏幕 + 系统通知
                DisplayDecision(
                    method = DisplayMethod.NOTIFICATION_ONLY, // 使用 NOTIFICATION_ONLY 但配合 shouldWakeScreen
                    reason = "无悬浮窗但有通知权限 + 息屏/锁屏 -> 唤醒并通知",
                    shouldWakeScreen = true,
                    shouldShowOnLockScreen = true // 通知自带锁屏显示能力
                )
            } else {
                // 亮屏 -> 普通系统通知
                DisplayDecision(
                    method = DisplayMethod.NOTIFICATION_ONLY,
                    reason = "无悬浮窗但有通知权限 + 亮屏 -> 普通通知",
                    shouldWakeScreen = false,
                    shouldShowOnLockScreen = false
                )
            }
        }

        // 无法提醒
        return DisplayDecision(
            method = DisplayMethod.NONE,
            reason = "权限全无 (无悬浮窗且无通知权限)",
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
        isDeadline: Boolean
    ) {
        val decision = decideDisplayMethod(context)
        Log.i(TAG, "★ Decision: ${decision.method} (${decision.reason})")
        
        // 1. 如果需要唤醒屏幕，先执行唤醒 (针对所有有效策略)
        if (decision.shouldWakeScreen) {
             wakeUpScreen(context)
        }

        when (decision.method) {
            DisplayMethod.FLOATING_WINDOW -> {
                // 修正策略：即便有悬浮窗权限，也优先使用 Activity。
                // 因为 Service 悬浮窗在国产 ROM (MIUI/ColorOS) 上需要额外的“后台弹出”和“锁屏显示”权限，
                // 而 Activity 配合 fullScreenIntent 是官方标准，穿透力更强，兼容性更好。
                
                // 1. 发送全屏通知 (系统级拉起)
                NotificationHelper.showReminderNotification(
                    context, taskId, taskTitle, reminderText, taskType
                )
                
                // 2. 双重保险：延迟手动启动 Activity
                // 给系统全屏意图一点时间生效。如果 500ms 后 Activity 还没起来（比如被系统拦截），
                // 这里的 start 将利用悬浮窗权限强制把 Activity 推到前台。
                // 由于设置了 FLAG_ACTIVITY_CLEAR_TOP，即使重复启动也不会产生多重界面。
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        ReminderActivity.start(
                            context, taskId, taskTitle, reminderText, taskType, isDeadline
                        )
                        Log.i(TAG, "✓ Manual Activity launch triggered (backup)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Manual Activity start failed: ${e.message}")
                    }
                }, 500)
            }
            
            DisplayMethod.NOTIFICATION_ONLY -> {
                // 仅系统通知 (携带高优先级)
                NotificationHelper.showReminderNotification(
                    context, taskId, taskTitle, reminderText, taskType
                )
            }
            
            DisplayMethod.NOTIFICATION_AND_ACTIVITY, DisplayMethod.ACTIVITY_ONLY -> {
                // 标准全屏通知流程
                NotificationHelper.showReminderNotification(
                    context, taskId, taskTitle, reminderText, taskType
                )
                
                // 辅助启动
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        ReminderActivity.start(
                            context, taskId, taskTitle, reminderText, taskType, isDeadline
                        )
                    } catch (e: Exception) { }
                }, 500)
            }
            
            DisplayMethod.NONE -> {
                Log.e(TAG, "Unable to remind: ${decision.reason}")
            }
        }
    }

    private fun wakeUpScreen(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "LiteTask:ScreenWakeUp"
            )
            wakeLock.acquire(3000) // 点亮 3 秒足以唤醒屏幕组件
            Log.i(TAG, "Screen wake-up triggered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wake screen: ${e.message}")
        }
    }
    
    /**
     * 检查悬浮窗权限
     */
    private fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
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
