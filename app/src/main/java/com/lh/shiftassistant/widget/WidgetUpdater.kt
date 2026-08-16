package com.lh.shiftassistant.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 触发桌面小组件刷新(任何数据写入后调用) */
class WidgetUpdater(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refreshAll() {
        val appContext = context.applicationContext
        scope.launch {
            try {
                ShiftWidget().updateAll(appContext)
            } catch (_: Exception) {
                // 忽略刷新失败
            }
        }
    }
}
