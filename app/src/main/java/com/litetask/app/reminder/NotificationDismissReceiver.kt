package com.litetask.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 通知取消广播接收器
 * 
 * 用于处理用户点击"知道了"按钮取消焦点通知
 */
class NotificationDismissReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "NotificationDismiss"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(NotificationHelper.EXTRA_TASK_ID, -1)
        
        if (taskId != -1L) {
            Log.i(TAG, "Dismissing focus notification for task: $taskId")
            NotificationHelper.cancelFocusNotification(context, taskId)
        }
    }
}
