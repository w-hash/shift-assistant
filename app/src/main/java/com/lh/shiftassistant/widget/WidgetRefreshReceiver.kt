package com.lh.shiftassistant.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 跨天/重启/改时区后刷新小组件:
 * ACTION_DATE_CHANGED / TIME_SET / TIMEZONE_CHANGED / BOOT_COMPLETED
 */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ShiftWidget().updateAll(context.applicationContext)
            } catch (_: Exception) {
                // 刷新失败不影响接收器
            } finally {
                pendingResult.finish()
            }
        }
    }
}
