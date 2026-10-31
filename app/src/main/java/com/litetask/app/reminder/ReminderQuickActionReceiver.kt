package com.litetask.app.reminder

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * 接收系统通知栏操作按钮的广播（如「复制号牌/密码」），避免在 Android 12+ 上受后台启动 Activity 限制。
 */
class ReminderQuickActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_COPY_TEXT = "com.litetask.app.action.REMINDER_COPY_TEXT"
        const val EXTRA_COPY_LABEL = "extra_copy_label"
        const val EXTRA_COPY_TEXT = "extra_copy_text"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COPY_TEXT) return
        val copyText = intent.getStringExtra(EXTRA_COPY_TEXT)?.takeIf { it.isNotBlank() } ?: return
        val label = intent.getStringExtra(EXTRA_COPY_LABEL)?.takeIf { it.isNotBlank() } ?: "LiteTask"

        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, copyText))

        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, "已复制：$copyText", Toast.LENGTH_SHORT).show()
        }
    }
}
