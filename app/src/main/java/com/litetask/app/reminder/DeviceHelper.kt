package com.litetask.app.reminder

import android.os.Build
import android.util.Log
import java.lang.reflect.Method

/**
 * 设备检测工具类
 *
 * 职责：检测设备品牌、系统版本等信息
 *
 * 支持检测：
 * - 小米/Redmi/POCO 设备
 * - MIUI/HyperOS 版本
 * - 系统特性支持情况
 */
object DeviceHelper {

    private const val TAG = "DeviceHelper"

    /** 是否是小米系设备 */
    fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("xiaomi") ||
                brand.contains("xiaomi") ||
                manufacturer.contains("redmi") ||
                brand.contains("redmi") ||
                manufacturer.contains("poco") ||
                brand.contains("poco")
    }

    /** 获取 MIUI 版本号 (如 14, 15) */
    fun getMiuiVersion(): Int {
        return try {
            val versionName = getSystemProperty("ro.miui.ui.version.name") ?: return 0
            Log.d(TAG, "MIUI version name: $versionName")
            // 解析 "V14" -> 14, "V140" -> 14
            val numStr = versionName.replace(Regex("[^0-9]"), "")
            when {
                numStr.length >= 2 -> numStr.take(2).toIntOrNull() ?: 0
                numStr.isNotEmpty() -> numStr.toIntOrNull() ?: 0
                else -> 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get MIUI version: ${e.message}")
            0
        }
    }

    /** 获取 HyperOS 版本号 (如 1, 2) */
    fun getHyperOsVersion(): Int {
        return try {
            val versionName = getSystemProperty("ro.mi.os.version.name") ?: return 0
            Log.d(TAG, "HyperOS version name: $versionName")
            // 解析 "OS1.0" -> 1
            versionName.replace(Regex("[^0-9]"), "").take(1).toIntOrNull() ?: 0
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get HyperOS version: ${e.message}")
            0
        }
    }

    /** 是否支持焦点通知 (MIUI 14+ 或 HyperOS 1+) */
    fun supportsFocusNotification(): Boolean {
        if (!isXiaomiDevice()) return false
        val miui = getMiuiVersion()
        val hyperOs = getHyperOsVersion()
        return miui >= 14 || hyperOs >= 1
    }

    /** 检查系统属性是否为 true */
    fun isSystemPropertyEnabled(key: String): Boolean {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method: Method = clazz.getDeclaredMethod(
                "getBoolean",
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            method.invoke(null, key, false) as Boolean
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check property $key: ${e.message}")
            false
        }
    }

    /** 获取系统属性值 */
    private fun getSystemProperty(key: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, key) as? String
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get property $key: ${e.message}")
            null
        }
    }

    /** 获取设备信息摘要（用于日志） */
    fun getDeviceSummary(): String {
        return buildString {
            append("Brand=${Build.BRAND}, ")
            append("Model=${Build.MODEL}, ")
            append("SDK=${Build.VERSION.SDK_INT}")
            if (isXiaomiDevice()) {
                append(", MIUI=${getMiuiVersion()}")
                append(", HyperOS=${getHyperOsVersion()}")
            }
        }
    }
}
