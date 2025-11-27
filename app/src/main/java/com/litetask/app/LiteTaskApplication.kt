package com.litetask.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * LiteTask 应用入口
 * 必须添加 @HiltAndroidApp 注解以启用 Hilt 依赖注入
 */
@HiltAndroidApp
class LiteTaskApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化操作
    }
}
