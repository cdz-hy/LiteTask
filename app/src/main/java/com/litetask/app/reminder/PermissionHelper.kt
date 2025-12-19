package com.litetask.app.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 权限检查辅助类
 * 
 * 用于检查和引导用户开启提醒相关权限
 * 
 * 小米澎湃OS适配：
 * - 悬浮窗权限（标准Android权限）
 * - 后台弹出界面权限（小米特有，需手动开启）
 */
object PermissionHelper {
    
    private const val TAG = "PermissionHelper"
    
    // 小米后台弹出权限的 AppOps 代码
    private const val OP_BACKGROUND_START_ACTIVITY = 10021

    /**
     * 检查是否有精确闹钟权限
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * 检查是否有通知权限
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * 获取精确闹钟权限设置页面的 Intent
     */
    fun getExactAlarmSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            // 旧版本跳转到应用设置页
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    /**
     * 获取应用通知设置页面的 Intent
     */
    fun getNotificationSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    /**
     * 获取电池优化设置页面的 Intent（用于引导用户关闭省电限制）
     */
    fun getBatteryOptimizationSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }
    
    // ==================== 悬浮窗相关权限 ====================
    
    /**
     * 检查是否有悬浮窗权限
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
    
    /**
     * 获取悬浮窗权限设置页面的 Intent
     */
    fun getOverlaySettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }
    
    // ==================== 小米特有权限 ====================
    
    /**
     * 检查是否是小米/Redmi/POCO设备
     */
    fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
               manufacturer.contains("redmi") || brand.contains("redmi") ||
               manufacturer.contains("poco") || brand.contains("poco")
    }
    
    /**
     * 检查是否支持小米焦点通知（MIUI 14+ / HyperOS）
     */
    fun supportsMiuiFocusNotification(): Boolean {
        if (!isXiaomiDevice()) return false
        
        val miuiVersion = getMiuiVersion()
        val hyperOsVersion = getHyperOsVersion()
        
        Log.d(TAG, "MIUI Version: $miuiVersion, HyperOS Version: $hyperOsVersion")
        
        // HyperOS 1.0+ 或 MIUI 14+ 支持焦点通知
        return hyperOsVersion >= 1 || miuiVersion >= 14
    }
    
    /**
     * 获取 MIUI 版本号
     */
    private fun getMiuiVersion(): Int {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            val version = method.invoke(null, "ro.miui.ui.version.name") as? String
            version?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * 获取 HyperOS 版本号
     */
    private fun getHyperOsVersion(): Int {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            val version = method.invoke(null, "ro.mi.os.version.name") as? String
            version?.replace(Regex("[^0-9]"), "")?.take(1)?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * 检查焦点通知渠道是否启用
     * 
     * 小米设备需要用户手动在通知设置中为渠道开启"焦点通知"权限
     * 路径：设置 -> 应用 -> LiteTask -> 通知管理 -> 焦点通知 -> 开启"焦点通知"
     */
    fun isFocusNotificationEnabled(context: Context): Boolean {
        return NotificationHelper.isFocusChannelEnabled(context)
    }
    
    /**
     * 获取焦点通知渠道设置页面的 Intent
     * 
     * 引导用户开启焦点通知权限
     */
    fun getFocusNotificationSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 直接跳转到焦点通知渠道设置
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.CHANNEL_ID_FOCUS)
            }
        } else {
            // 旧版本跳转到应用通知设置
            getNotificationSettingsIntent(context)
        }
    }
    
    /**
     * 检查小米后台弹出界面权限
     * 
     * 小米澎湃OS/MIUI 特有权限，默认关闭
     * 需要用户手动在"应用详情 -> 权限管理 -> 后台弹出界面"开启
     */
    fun hasXiaomiBackgroundPopupPermission(context: Context): Boolean {
        if (!isXiaomiDevice()) return true
        
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val uid = Process.myUid()
            val packageName = context.packageName
            
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    OP_BACKGROUND_START_ACTIVITY.toString(),
                    uid,
                    packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    OP_BACKGROUND_START_ACTIVITY.toString(),
                    uid,
                    packageName
                )
            }
            
            val allowed = result == AppOpsManager.MODE_ALLOWED
            Log.d(TAG, "Xiaomi background popup permission: $allowed")
            allowed
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check Xiaomi background popup permission: ${e.message}")
            true // 检查失败时假设有权限
        }
    }
    
    /**
     * 获取小米应用权限设置页面的 Intent
     * 用于引导用户开启"后台弹出界面"权限
     */
    fun getXiaomiPermissionSettingsIntent(context: Context): Intent {
        // 尝试直接打开小米权限管理页面
        return try {
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", context.packageName)
            }
        } catch (e: Exception) {
            // 降级到应用详情页
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }
    
    /**
     * 检查焦点通知/灵动岛所需的全部权限
     * 
     * @return 缺失的权限列表
     */
    fun checkFocusNotificationPermissions(context: Context): List<MissingPermission> {
        val missing = mutableListOf<MissingPermission>()
        
        // 1. 通知权限（必需）
        if (!hasNotificationPermission(context)) {
            missing.add(MissingPermission.NOTIFICATION)
        }
        
        // 2. 焦点通知渠道（小米设备必需）
        if (isXiaomiDevice() && supportsMiuiFocusNotification()) {
            if (!isFocusNotificationEnabled(context)) {
                missing.add(MissingPermission.XIAOMI_FOCUS_NOTIFICATION)
            }
        }
        
        return missing
    }
    
    /**
     * 检查悬浮窗通知所需的全部权限
     * 
     * @return 缺失的权限列表
     */
    fun checkFloatingWindowPermissions(context: Context): List<MissingPermission> {
        val missing = mutableListOf<MissingPermission>()
        
        // 1. 悬浮窗权限（必需）
        if (!hasOverlayPermission(context)) {
            missing.add(MissingPermission.OVERLAY)
        }
        
        // 2. 小米后台弹出权限（小米设备悬浮窗需要）
        if (isXiaomiDevice() && !hasXiaomiBackgroundPopupPermission(context)) {
            missing.add(MissingPermission.XIAOMI_BACKGROUND_POPUP)
        }
        
        return missing
    }
    
    /**
     * 缺失的权限类型
     */
    enum class MissingPermission {
        NOTIFICATION,               // 通知权限
        OVERLAY,                    // 悬浮窗权限
        XIAOMI_FOCUS_NOTIFICATION,  // 小米焦点通知权限
        XIAOMI_BACKGROUND_POPUP     // 小米后台弹出界面权限
    }

    /**
     * 检查是否需要显示权限引导
     * 返回需要引导的权限类型
     */
    fun checkPermissions(context: Context): PermissionStatus {
        val hasAlarmPermission = canScheduleExactAlarms(context)
        val hasNotificationPermission = hasNotificationPermission(context)

        return when {
            !hasAlarmPermission && !hasNotificationPermission -> 
                PermissionStatus.BOTH_MISSING
            !hasAlarmPermission -> 
                PermissionStatus.ALARM_MISSING
            !hasNotificationPermission -> 
                PermissionStatus.NOTIFICATION_MISSING
            else -> 
                PermissionStatus.ALL_GRANTED
        }
    }

    enum class PermissionStatus {
        ALL_GRANTED,
        ALARM_MISSING,
        NOTIFICATION_MISSING,
        BOTH_MISSING
    }
}
