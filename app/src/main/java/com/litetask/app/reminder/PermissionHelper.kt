package com.litetask.app.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import java.lang.reflect.Method

/**
 * 权限检查辅助类
 * 
 * 用于检查和引导用户开启提醒相关权限
 * 支持小米、VIVO、OPPO 等厂商的特殊权限检测
 */
object PermissionHelper {
    
    private const val TAG = "PermissionHelper"
    
    // 各厂商自启动设置页面的 ComponentName
    private val AUTO_START_INTENTS = listOf(
        // 小米
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        // 华为
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
        // OPPO
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
        // VIVO
        Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
        // 三星
        Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
        // 一加
        Intent().setComponent(ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),
        // 魅族
        Intent().setComponent(ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity")),
        // 联想
        Intent().setComponent(ComponentName("com.lenovo.security", "com.lenovo.security.purebackground.PureBackgroundActivity")),
    )

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
     * 获取电池优化设置页面的 Intent
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
    
    /**
     * 检查是否有后台弹出界面权限
     * 支持小米、VIVO、OPPO 等厂商的特殊检测
     */
    fun hasBackgroundActivityPermission(context: Context): Boolean {
        // Android 10 (API 29) 及以上需要检查
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true
        }
        
        // 尝试各厂商的特殊检测方法
        return when {
            isMiui() -> checkMiuiBackgroundPermission(context)
            isVivo() -> checkVivoBackgroundPermission(context)
            isOppo() -> checkOppoBackgroundPermission(context)
            else -> {
                // 其他厂商：尝试通用检测
                checkGenericBackgroundPermission(context)
            }
        }
    }
    
    /**
     * 检查是否有锁屏显示权限
     * 支持小米、VIVO 等厂商的特殊检测
     */
    fun hasLockScreenPermission(context: Context): Boolean {
        // 尝试各厂商的特殊检测方法
        return when {
            isMiui() -> checkMiuiLockScreenPermission(context)
            isVivo() -> checkVivoLockScreenPermission(context)
            isOppo() -> checkOppoLockScreenPermission(context)
            else -> {
                // 其他厂商：假设已授权（通过 Manifest 配置）
                true
            }
        }
    }
    
    // ==================== 厂商检测 ====================
    
    /**
     * 检测是否为小米设备
     */
    private fun isMiui(): Boolean {
        return !getSystemProperty("ro.miui.ui.version.name").isNullOrEmpty()
    }
    
    /**
     * 检测是否为 VIVO 设备
     */
    private fun isVivo(): Boolean {
        return Build.MANUFACTURER.equals("vivo", ignoreCase = true) ||
               Build.BRAND.equals("vivo", ignoreCase = true)
    }
    
    /**
     * 检测是否为 OPPO 设备
     */
    private fun isOppo(): Boolean {
        return Build.MANUFACTURER.equals("oppo", ignoreCase = true) ||
               Build.BRAND.equals("oppo", ignoreCase = true) ||
               Build.MANUFACTURER.equals("realme", ignoreCase = true) ||
               Build.BRAND.equals("realme", ignoreCase = true)
    }
    
    /**
     * 获取系统属性
     */
    private fun getSystemProperty(key: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, key) as? String
        } catch (e: Exception) {
            null
        }
    }
    
    // ==================== 小米权限检测 ====================
    
    /**
     * 小米后台弹出界面权限检测
     */
    private fun checkMiuiBackgroundPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return true
        }
        
        return try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            if (ops == null) {
                Log.w(TAG, "AppOpsManager is null")
                return false
            }
            
            val op = 10021 // MIUI 后台弹出界面权限 OP code
            val method: Method = ops.javaClass.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            val result = method.invoke(ops, op, android.os.Process.myUid(), context.packageName) as? Int
            
            val granted = result == AppOpsManager.MODE_ALLOWED
            Log.d(TAG, "MIUI background permission: $granted")
            granted
        } catch (e: Exception) {
            Log.e(TAG, "Check MIUI background permission failed: ${e.message}")
            false
        }
    }
    
    /**
     * 小米锁屏显示权限检测
     */
    private fun checkMiuiLockScreenPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return true
        }
        
        return try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            if (ops == null) {
                Log.w(TAG, "AppOpsManager is null")
                return false
            }
            
            val op = 10020 // MIUI 锁屏显示权限 OP code
            val method: Method = ops.javaClass.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            val result = method.invoke(ops, op, android.os.Process.myUid(), context.packageName) as? Int
            
            val granted = result == AppOpsManager.MODE_ALLOWED
            Log.d(TAG, "MIUI lock screen permission: $granted")
            granted
        } catch (e: Exception) {
            Log.e(TAG, "Check MIUI lock screen permission failed: ${e.message}")
            false
        }
    }
    
    // ==================== VIVO 权限检测 ====================
    
    /**
     * VIVO 后台弹出界面权限检测
     * @return true 表示已授权，false 表示未授权
     */
    private fun checkVivoBackgroundPermission(context: Context): Boolean {
        return try {
            val uri = Uri.parse("content://com.vivo.permissionmanager.provider.permission/start_bg_activity")
            val selection = "pkgname = ?"
            val selectionArgs = arrayOf(context.packageName)
            
            val cursor: Cursor? = context.contentResolver.query(uri, null, selection, selectionArgs, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val currentState = it.getInt(it.getColumnIndex("currentstate"))
                    val granted = currentState == 0 // 0 表示已开启，1 表示未开启
                    Log.d(TAG, "VIVO background permission: $granted (state=$currentState)")
                    return granted
                }
            }
            
            Log.w(TAG, "VIVO background permission: no data found")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Check VIVO background permission failed: ${e.message}")
            false
        }
    }
    
    /**
     * VIVO 锁屏显示权限检测
     * @return true 表示已授权，false 表示未授权
     */
    private fun checkVivoLockScreenPermission(context: Context): Boolean {
        return try {
            val uri = Uri.parse("content://com.vivo.permissionmanager.provider.permission/control_locked_screen_action")
            val selection = "pkgname = ?"
            val selectionArgs = arrayOf(context.packageName)
            
            val cursor: Cursor? = context.contentResolver.query(uri, null, selection, selectionArgs, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val currentState = it.getInt(it.getColumnIndex("currentstate"))
                    val granted = currentState == 0 // 0 表示已开启，1 表示未开启
                    Log.d(TAG, "VIVO lock screen permission: $granted (state=$currentState)")
                    return granted
                }
            }
            
            Log.w(TAG, "VIVO lock screen permission: no data found")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Check VIVO lock screen permission failed: ${e.message}")
            false
        }
    }
    
    // ==================== OPPO 权限检测 ====================
    
    /**
     * OPPO 后台弹出界面权限检测
     * OPPO 使用悬浮窗权限来控制后台弹出
     */
    private fun checkOppoBackgroundPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val granted = Settings.canDrawOverlays(context)
            Log.d(TAG, "OPPO background permission (overlay): $granted")
            granted
        } else {
            true
        }
    }
    
    /**
     * OPPO 锁屏显示权限检测
     * OPPO 也使用悬浮窗权限来控制锁屏显示
     */
    private fun checkOppoLockScreenPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val granted = Settings.canDrawOverlays(context)
            Log.d(TAG, "OPPO lock screen permission (overlay): $granted")
            granted
        } else {
            true
        }
    }
    
    // ==================== 通用权限检测 ====================
    
    /**
     * 通用后台弹出界面权限检测
     * 对于不支持特殊检测的设备，假设已授权
     */
    private fun checkGenericBackgroundPermission(context: Context): Boolean {
        // 对于原生 Android 和其他厂商，通常不需要额外权限
        // 只要有通知权限和精确闹钟权限即可
        Log.d(TAG, "Generic background permission: assumed granted")
        return true
    }
    
    // ==================== 悬浮窗权限检测（可选，用于增强提醒效果）====================
    
    /**
     * 检查是否有悬浮窗权限
     * 注意：此权限不是必需的，但可以增强亮屏时的提醒效果
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val granted = Settings.canDrawOverlays(context)
            Log.d(TAG, "Overlay permission: $granted")
            granted
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
    
    // ==================== 设置页面跳转 ====================
    
    /**
     * 获取后台弹出界面权限设置页面的 Intent
     */
    fun getBackgroundActivitySettingsIntent(context: Context): Intent {
        return when {
            isMiui() -> getMiuiPermissionSettingsIntent(context)
            isVivo() -> getVivoPermissionSettingsIntent(context)
            isOppo() -> getOppoPermissionSettingsIntent(context)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }
    
    /**
     * 获取锁屏显示权限设置页面的 Intent
     */
    fun getLockScreenSettingsIntent(context: Context): Intent {
        return when {
            isMiui() -> getMiuiPermissionSettingsIntent(context)
            isVivo() -> getVivoPermissionSettingsIntent(context)
            isOppo() -> getOppoPermissionSettingsIntent(context)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }
    
    /**
     * 小米权限设置页面
     */
    private fun getMiuiPermissionSettingsIntent(context: Context): Intent {
        return try {
            // 尝试跳转到小米权限管理页面
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (e: Exception) {
            // 失败则跳转到应用详情
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }
    
    /**
     * VIVO 权限设置页面
     */
    private fun getVivoPermissionSettingsIntent(context: Context): Intent {
        return try {
            // 尝试跳转到 VIVO 权限管理页面
            Intent().apply {
                setClassName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity"
                )
                putExtra("packagename", context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (e: Exception) {
            // 失败则跳转到应用详情
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }
    
    /**
     * OPPO 权限设置页面
     */
    private fun getOppoPermissionSettingsIntent(context: Context): Intent {
        return try {
            // OPPO 使用悬浮窗权限设置
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
        } catch (e: Exception) {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }
    
    // ==================== 原有方法保持不变 ====================
    
    fun getAutoStartSettingsIntent(context: Context): Intent {
        // 尝试各厂商的自启动设置页面
        for (intent in AUTO_START_INTENTS) {
            if (isIntentAvailable(context, intent)) {
                return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        // 都不可用时，返回应用详情页面
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
    
    /**
     * 检查 Intent 是否可用
     */
    private fun isIntentAvailable(context: Context, intent: Intent): Boolean {
        return try {
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检查是否有可用的自启动设置页面
     */
    fun hasAutoStartSettings(context: Context): Boolean {
        return AUTO_START_INTENTS.any { isIntentAvailable(context, it) }
    }

    /**
     * 检查权限状态
     */
    fun checkPermissions(context: Context): PermissionStatus {
        val hasAlarmPermission = canScheduleExactAlarms(context)
        val hasNotificationPermission = hasNotificationPermission(context)

        return when {
            !hasAlarmPermission && !hasNotificationPermission -> PermissionStatus.BOTH_MISSING
            !hasAlarmPermission -> PermissionStatus.ALARM_MISSING
            !hasNotificationPermission -> PermissionStatus.NOTIFICATION_MISSING
            else -> PermissionStatus.ALL_GRANTED
        }
    }

    enum class PermissionStatus {
        ALL_GRANTED,
        ALARM_MISSING,
        NOTIFICATION_MISSING,
        BOTH_MISSING
    }
}
