package com.lh.shiftassistant

import android.app.Application
import android.content.Context
import com.lh.shiftassistant.backup.BackupManager
import com.lh.shiftassistant.calendar.CalendarSyncManager
import com.lh.shiftassistant.data.AppDatabase
import com.lh.shiftassistant.data.ShiftRepository
import com.lh.shiftassistant.settings.SettingsRepository
import com.lh.shiftassistant.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 手动依赖注入容器(单模块小项目,不引入 Hilt) */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database: AppDatabase = AppDatabase.build(appContext)
    val settingsRepository: SettingsRepository = SettingsRepository(appContext)
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val widgetUpdater: WidgetUpdater = WidgetUpdater(appContext)

    lateinit var calendarSyncManager: CalendarSyncManager

    val shiftRepository: ShiftRepository = ShiftRepository(
        database, settingsRepository, widgetUpdater, appScope
    ) {
        // 任何排班写入后触发日历防抖同步(开关开启时生效)
        if (::calendarSyncManager.isInitialized) calendarSyncManager.requestSync()
    }

    val backupManager: BackupManager = BackupManager(shiftRepository)

    init {
        calendarSyncManager = CalendarSyncManager(appContext, settingsRepository, appScope, shiftRepository)
    }
}

class ShiftAssistantApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 首次启动预置人员与班次
        container.appScope.launch {
            runCatching { container.shiftRepository.ensureDefaults() }
        }
    }
}
