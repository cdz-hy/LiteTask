package com.litetask.app.reminder

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.litetask.app.data.model.TaskType

/**
 * 通知分发器 - 核心调度中心
 *
 * 职责：根据设备类型和权限状态，选择最佳通知展示方式
 *
 * 分发策略：
 * - 小米设备 → 焦点通知/灵动岛 → 失败降级到系统通知
 * - 其他设备 → 悬浮窗通知 → 失败降级到系统通知
 *
 * 设计原则：
 * - 单一职责：只负责分发决策，不负责具体实现
 * - 确保送达：任何情况下都保证通知能够送达用户
 */
object NotificationDispatcher {

    private const val TAG = "NotificationDispatcher"

    /** 通知展示结果 */
    data class DispatchResult(
        val success: Boolean,
        val mode: DisplayMode,
        val fallbackUsed: Boolean = false
    )

    /** 展示模式 */
    enum class DisplayMode {
        XIAOMI_ISLAND,      // 小米焦点通知/灵动岛
        FLOATING_WINDOW,    // 悬浮窗通知
        SYSTEM_NOTIFICATION // 系统通知（兜底）
    }

    /**
     * 分发提醒通知
     *
     * @return 分发结果，包含使用的模式和是否降级
     */
    fun dispatch(
        context: Context,
        taskId: Long,
        taskTitle: String,
        reminderText: String,
        taskType: TaskType,
        isDeadline: Boolean
    ): DispatchResult {
        val isXiaomi = DeviceHelper.isXiaomiDevice()
        Log.i(TAG, "★ Dispatch: isXiaomi=$isXiaomi, task=$taskTitle")

        return if (isXiaomi) {
            dispatchForXiaomi(context, taskId, taskTitle, reminderText, taskType, isDeadline)
        } else {
            dispatchForOther(context, taskId, taskTitle, reminderText, taskType, isDeadline)
        }
    }


    /**
     * 小米设备分发逻辑
     *
     * 优先级：焦点通知 → 系统通知
     */
    private fun dispatchForXiaomi(
        context: Context,
        taskId: Long,
        taskTitle: String,
        reminderText: String,
        taskType: TaskType,
        isDeadline: Boolean
    ): DispatchResult {
        Log.i(TAG, "★ Xiaomi device detected, trying Focus Notification")

        // 尝试焦点通知
        val focusSuccess = tryFocusNotification(
            context, taskId, taskTitle, reminderText, taskType, isDeadline
        )

        if (focusSuccess) {
            Log.i(TAG, "★ Focus Notification success")
            return DispatchResult(true, DisplayMode.XIAOMI_ISLAND)
        }

        // 降级到系统通知
        Log.w(TAG, "★ Focus Notification failed, fallback to system notification")
        showSystemNotification(context, taskId, taskTitle, reminderText, taskType)
        return DispatchResult(true, DisplayMode.SYSTEM_NOTIFICATION, fallbackUsed = true)
    }

    /**
     * 非小米设备分发逻辑
     *
     * 优先级：悬浮窗 → 系统通知
     */
    private fun dispatchForOther(
        context: Context,
        taskId: Long,
        taskTitle: String,
        reminderText: String,
        taskType: TaskType,
        isDeadline: Boolean
    ): DispatchResult {
        Log.i(TAG, "★ Non-Xiaomi device, trying Floating Window")

        // 检查悬浮窗条件
        if (canShowFloatingWindow(context)) {
            val floatSuccess = tryFloatingWindow(
                context, taskId, taskTitle, reminderText, taskType, isDeadline
            )

            if (floatSuccess) {
                // 悬浮窗成功，同时发送静默系统通知作为记录
                showSystemNotification(context, taskId, taskTitle, reminderText, taskType)
                Log.i(TAG, "★ Floating Window success")
                return DispatchResult(true, DisplayMode.FLOATING_WINDOW)
            }
        }

        // 降级到系统通知
        Log.w(TAG, "★ Floating Window unavailable, fallback to system notification")
        showSystemNotification(context, taskId, taskTitle, reminderText, taskType)
        return DispatchResult(true, DisplayMode.SYSTEM_NOTIFICATION, fallbackUsed = true)
    }

    /**
     * 尝试显示焦点通知
     */
    private fun tryFocusNotification(
        context: Context,
        taskId: Long,
        taskTitle: String,
        reminderText: String,
        taskType: TaskType,
        isDeadline: Boolean
    ): Boolean {
        return try {
            FocusNotificationService.start(
                context = context,
                taskId = taskId,
                taskTitle = taskTitle,
                reminderLabel = reminderText,
                taskType = taskType,
                isDeadline = isDeadline
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Focus notification failed: ${e.message}")
            false
        }
    }

    /**
     * 尝试显示悬浮窗
     */
    private fun tryFloatingWindow(
        context: Context,
        taskId: Long,
        taskTitle: String,
        reminderText: String,
        taskType: TaskType,
        isDeadline: Boolean
    ): Boolean {
        return try {
            FloatingReminderService.show(
                context = context,
                taskId = taskId,
                taskTitle = taskTitle,
                reminderText = reminderText,
                taskType = taskType,
                isDeadline = isDeadline
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Floating window failed: ${e.message}")
            false
        }
    }

    /**
     * 显示系统通知（兜底方案）
     */
    private fun showSystemNotification(
        context: Context,
        taskId: Long,
        taskTitle: String,
        reminderText: String,
        taskType: TaskType
    ) {
        NotificationHelper.showReminderNotification(
            context, taskId, taskTitle, reminderText, taskType
        )
    }

    /**
     * 检查是否可以显示悬浮窗
     */
    private fun canShowFloatingWindow(context: Context): Boolean {
        // 1. 权限检查
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                Log.d(TAG, "No overlay permission")
                return false
            }
        }

        // 2. 屏幕状态
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            Log.d(TAG, "Screen is off")
            return false
        }

        // 3. 锁屏状态
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isKeyguardLocked) {
            Log.d(TAG, "Device is locked")
            return false
        }

        return true
    }
}
