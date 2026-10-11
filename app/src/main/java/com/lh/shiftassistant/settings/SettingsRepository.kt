package com.lh.shiftassistant.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** 应用设置(DataStore) */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val WIDGET_DAYS = intPreferencesKey("widget_days")
        val WEEK_START = booleanPreferencesKey("week_starts_monday")
        val CALENDAR_SYNC = booleanPreferencesKey("calendar_sync_enabled")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val SYNC_CALENDAR_ID = longPreferencesKey("sync_calendar_id")
    }

    /** 小组件显示天数:4(默认)或 7 */
    val widgetDays: Flow<Int> = context.settingsDataStore.data.map { it[Keys.WIDGET_DAYS] ?: 4 }

    /** 周一为一周起点(本期恒为 true,键值预留) */
    val weekStartsMonday: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.WEEK_START] ?: true }

    /** 是否把班次同步到系统日历(默认关) */
    val calendarSyncEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.CALENDAR_SYNC] ?: false }

    /** 深色主题(手动两档切换,默认浅色) */
    val darkTheme: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.DARK_THEME] ?: false }

    /** 日历同步目标日历 id;空 = 自动选择(会话8:不再自建日历,写入系统自带日历) */
    val syncCalendarId: Flow<Long?> = context.settingsDataStore.data.map { it[Keys.SYNC_CALENDAR_ID] }

    suspend fun setWidgetDays(days: Int) {
        context.settingsDataStore.edit { it[Keys.WIDGET_DAYS] = days }
    }

    suspend fun setCalendarSyncEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.CALENDAR_SYNC] = enabled }
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.settingsDataStore.edit { it[Keys.DARK_THEME] = dark }
    }

    /** 设置同步目标日历;null = 恢复自动选择 */
    suspend fun setSyncCalendarId(id: Long?) {
        context.settingsDataStore.edit { prefs ->
            if (id != null) prefs[Keys.SYNC_CALENDAR_ID] = id else prefs.remove(Keys.SYNC_CALENDAR_ID)
        }
    }

    suspend fun widgetDaysOnce(): Int = widgetDays.first()

    suspend fun calendarSyncEnabledOnce(): Boolean = calendarSyncEnabled.first()

    suspend fun darkThemeOnce(): Boolean = darkTheme.first()

    suspend fun syncCalendarIdOnce(): Long? = syncCalendarId.first()
}
