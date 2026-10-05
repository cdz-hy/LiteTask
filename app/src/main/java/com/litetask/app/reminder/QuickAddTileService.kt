package com.litetask.app.reminder

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.litetask.app.MainActivity
import com.litetask.app.data.local.PreferenceManager

/**
 * Android 下拉控制中心（Quick Settings）快速记事磁贴服务。
 *
 * 根据用户在设置中配置的默认操作（语音 / 文字 / 手动），一键唤醒 LiteTask 开始记录。
 */
class QuickAddTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val defaultAction = PreferenceManager(applicationContext).getDefaultFabAction()
            tile.subtitle = when (defaultAction) {
                "voice" -> "语音添加"
                "text" -> "文字分析"
                else -> "新建任务"
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("action", "add_task")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                1001,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launchIntent)
        }
    }
}
