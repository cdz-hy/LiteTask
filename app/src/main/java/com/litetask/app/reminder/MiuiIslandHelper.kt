package com.litetask.app.reminder

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import com.litetask.app.R
import com.litetask.app.data.model.TaskType
import org.json.JSONObject

/**
 * 小米灵动岛/超级岛参数构建器
 *
 * 职责：
 * - 检测设备是否支持灵动岛
 * - 构建小米专用的 miui.focus.param JSON
 * - 装饰通知对象，注入岛参数
 *
 * 注意：此类仅负责参数构建，不负责通知发送
 */
object MiuiIslandHelper {

    private const val TAG = "MiuiIslandHelper"

    // 小米岛通知 Bundle Keys
    private const val KEY_FOCUS_PARAM = "miui.focus.param"
    private const val KEY_FOCUS_PICS = "miui.focus.pics"

    // 图片资源引用 Key
    private const val PIC_KEY_ICON = "island_icon"

    /**
     * 检测是否支持灵动岛
     *
     * 条件（宽松模式）：
     * 1. 小米设备
     * 2. Android 13+ (MIUI 14+ / HyperOS 通常支持)
     * 3. 尝试检测焦点通知权限
     *
     * 注意：即使检测失败也会尝试注入参数，让系统自己决定是否展示
     */
    fun isIslandSupported(context: Context): Boolean {
        if (!DeviceHelper.isXiaomiDevice()) {
            Log.d(TAG, "Not Xiaomi device")
            return false
        }

        // 宽松检测：Android 13+ 的小米设备大概率支持
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "Android 13+ Xiaomi device, assuming island support")
            // 尝试检测，但不作为硬性条件
            val hasIslandFeature = DeviceHelper.isSystemPropertyEnabled("persist.sys.feature.island")
            val protocolVersion = getProtocolVersion(context)
            val hasFocus = hasFocusPermission(context)
            Log.d(TAG, "Island feature=$hasIslandFeature, protocol=$protocolVersion, focusPerm=$hasFocus")
            return true // 宽松返回 true，让系统自己决定
        }

        // Android 12 及以下需要严格检测
        if (!DeviceHelper.isSystemPropertyEnabled("persist.sys.feature.island")) {
            Log.d(TAG, "Island feature not enabled")
            return false
        }
        if (getProtocolVersion(context) < 3) {
            Log.d(TAG, "Protocol version too low")
            return false
        }
        return hasFocusPermission(context)
    }

    /** 获取协议版本 */
    private fun getProtocolVersion(context: Context): Int {
        return try {
            Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0)
        } catch (e: Exception) {
            0
        }
    }

    /** 检查焦点通知权限 */
    private fun hasFocusPermission(context: Context): Boolean {
        return try {
            val uri = Uri.parse("content://miui.statusbar.notification.public")
            val extras = Bundle().apply { putString("package", context.packageName) }
            val result = context.contentResolver.call(uri, "canShowFocus", null, extras)
            val canShow = result?.getBoolean("canShowFocus", false) ?: false
            Log.d(TAG, "Focus permission check: canShowFocus=$canShow")
            canShow
        } catch (e: Exception) {
            Log.w(TAG, "Focus permission check failed: ${e.message}")
            // 宽松判断：Android 13+ 小米设备假设支持，让系统自己决定
            val fallback = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            Log.d(TAG, "Using fallback: $fallback")
            fallback
        }
    }

    /**
     * 检测是否为小米 HyperOS（澎湃OS）
     * HyperOS 对灵动岛有更好的支持
     */
    fun isHyperOS(): Boolean {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            val osName = method.invoke(null, "ro.mi.os.version.name", "") as String
            osName.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }


    /**
     * 装饰通知，注入灵动岛参数
     *
     * @return true=注入成功, false=不支持或失败
     */
    fun decorateNotification(
        context: Context,
        notification: Notification,
        title: String,
        content: String,
        taskType: TaskType,
        isDeadline: Boolean
    ): Boolean {
        if (!isIslandSupported(context)) {
            Log.d(TAG, "Island not supported, skip decoration")
            return false
        }

        return try {
            val extras = notification.extras ?: Bundle()

            // 1. 图片资源 - 使用 Bitmap 方式确保兼容性
            val icon = createIconBitmap(context)
            val pics = Bundle().apply {
                if (icon != null) {
                    putParcelable(PIC_KEY_ICON, Icon.createWithBitmap(icon))
                    Log.d(TAG, "Icon bitmap created: ${icon.width}x${icon.height}")
                } else {
                    // 降级使用资源引用
                    putParcelable(PIC_KEY_ICON, Icon.createWithResource(context, R.drawable.ic_notification))
                    Log.d(TAG, "Using resource icon fallback")
                }
            }
            extras.putBundle(KEY_FOCUS_PICS, pics)

            // 2. JSON 参数
            val jsonParam = buildIslandJson(title, content, taskType, isDeadline)
            extras.putString(KEY_FOCUS_PARAM, jsonParam)
            Log.d(TAG, "JSON param: $jsonParam")

            // 3. 合并到通知
            notification.extras.putAll(extras)
            Log.i(TAG, "★ Island params injected successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Decoration failed: ${e.message}", e)
            false
        }
    }

    /**
     * 创建图标 Bitmap
     * 小米灵动岛对图片有严格要求，使用 Bitmap 更可靠
     */
    private fun createIconBitmap(context: Context): android.graphics.Bitmap? {
        return try {
            val drawable = androidx.core.content.ContextCompat.getDrawable(
                context, R.drawable.ic_notification
            ) ?: return null

            // 创建合适大小的 Bitmap (48dp 是推荐尺寸)
            val size = (48 * context.resources.displayMetrics.density).toInt()
            val bitmap = android.graphics.Bitmap.createBitmap(
                size, size, android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create icon bitmap: ${e.message}")
            null
        }
    }

    /**
     * 构建灵动岛 JSON 参数
     *
     * 关键点：
     * 1. business 必须使用 "normal"，自定义业务标识会导致鉴权失败
     * 2. pic 字段必须与 Bundle 中的 key 完全一致
     * 3. baseInfo 是必须的降级方案
     */
    private fun buildIslandJson(
        title: String,
        content: String,
        taskType: TaskType,
        isDeadline: Boolean
    ): String {
        val typeLabel = getTaskTypeLabel(taskType)
        val displayContent = "$typeLabel · $content"

        return JSONObject().apply {
            put("param_v2", JSONObject().apply {
                // 协议版本
                put("protocol", 1)
                // 关键：使用通用业务标识，避免鉴权失败
                put("business", "normal")
                // 启用浮动展示
                put("enableFloat", true)
                // 允许更新
                put("updatable", true)

                // 岛布局配置
                put("param_island", JSONObject().apply {
                    // islandProperty: 1=常驻, 2=临时
                    put("islandProperty", if (isDeadline) 1 else 2)

                    // 大岛（展开状态）
                    put("bigIslandArea", JSONObject().apply {
                        put("imageTextInfoLeft", JSONObject().apply {
                            put("type", 1)
                            put("picInfo", JSONObject().apply {
                                put("type", 1)
                                // 关键：这个 key 必须与 Bundle 中的 key 完全一致
                                put("pic", PIC_KEY_ICON)
                            })
                            put("textInfo", JSONObject().apply {
                                put("title", title)
                                put("content", displayContent)
                                put("useHighLight", isDeadline || taskType == TaskType.URGENT)
                            })
                        })
                    })

                    // 小岛（胶囊状态）
                    put("smallIslandArea", JSONObject().apply {
                        put("picInfo", JSONObject().apply {
                            put("type", 1)
                            put("pic", PIC_KEY_ICON)
                        })
                        // 小岛也可以显示简短文字
                        put("textInfo", JSONObject().apply {
                            put("content", typeLabel)
                        })
                    })
                })

                // baseInfo 是必须的，鉴权失败或岛不可用时的降级方案
                put("baseInfo", JSONObject().apply {
                    put("title", title)
                    put("content", displayContent)
                    // type: 1=紧急/重要, 2=普通提醒
                    put("type", if (isDeadline || taskType == TaskType.URGENT) 1 else 2)
                    // 图标引用
                    put("pic", PIC_KEY_ICON)
                })

                // 允许系统更新岛内容
                put("allowUpdate", true)
            })
        }.toString()
    }

    private fun getTaskTypeLabel(taskType: TaskType): String = when (taskType) {
        TaskType.URGENT -> "紧急任务"
        TaskType.WORK -> "工作任务"
        TaskType.STUDY -> "学习任务"
        TaskType.LIFE -> "生活任务"
    }
}
