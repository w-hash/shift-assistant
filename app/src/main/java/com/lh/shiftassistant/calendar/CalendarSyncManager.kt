package com.lh.shiftassistant.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.lh.shiftassistant.data.Defaults
import com.lh.shiftassistant.data.ShiftRepository
import com.lh.shiftassistant.settings.SettingsRepository
import com.lh.shiftassistant.util.TimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 班表 → 系统日历单向同步。
 *
 * 背景:realme UI 7 桌面无第三方小组件添加入口,改为把班次写入系统日历,
 * 由日历应用的桌面「日程」卡片展示今天及未来的班次。
 *
 * 设计:
 * - 独立本地日历账户「班表助手」(ACCOUNT_TYPE = com.lh.shiftassistant.calendar)
 * - 事件归属标记 CUSTOM_APP_PACKAGE = 应用包名,便于批量更新/删除
 * - 同步范围:今天及未来;历史事件自动清理
 * - 对齐算法:班表(今天起) ↔ 日历现有事件逐日比对,增/改/删
 * - 事件颜色跟随班次颜色(API 27+)
 */
class CalendarSyncManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val repository: ShiftRepository
) {
    private val resolver = context.contentResolver
    private val zone: ZoneId = ZoneId.systemDefault()

    private var pendingJob: Job? = null

    companion object {
        const val ACCOUNT_TYPE = "com.lh.shiftassistant.calendar"
        const val ACCOUNT_NAME = "班表助手"
        const val CALENDAR_NAME = "班表助手"
        const val APP_PACKAGE = "com.lh.shiftassistant"
    }

    fun hasPermissions(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** 数据写入后的防抖同步(2 秒合并);开关关闭时不动作 */
    fun requestSync() {
        scope.launch {
            if (!settings.calendarSyncEnabledOnce()) return@launch
            pendingJob?.cancel()
            pendingJob = launch {
                delay(2000)
                runCatching { align() }
            }
        }
    }

    /** 手动全量对齐;返回同步的班次事件数 */
    suspend fun syncNow(): Int = try {
        align().also { android.util.Log.i("CalSync", "syncNow -> $it events") }
    } catch (e: Exception) {
        android.util.Log.e("CalSync", "syncNow failed", e)
        0
    }

    /** 删除本应用写入日历的全部事件(覆盖所有同名日历);返回删除数 */
    suspend fun clearAll(): Int {
        if (!hasPermissions()) return 0
        var deleted = 0
        findCalendarIds().forEach { calendarId ->
            deleted += resolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.CUSTOM_APP_PACKAGE}=?",
                arrayOf(calendarId.toString(), APP_PACKAGE)
            )
        }
        return deleted
    }

    /** 关闭开关并清除事件 */
    suspend fun disableAndClear() {
        settings.setCalendarSyncEnabled(false)
        clearAll()
    }

    // ---- 内部 ----

    private suspend fun align(): Int {
        if (!hasPermissions()) return 0
        var calendarIds = findCalendarIds()
        if (calendarIds.isEmpty()) {
            ensureCalendar()?.let { calendarIds = listOf(it) }
        }
        if (calendarIds.isEmpty()) return 0
        val calendarId = calendarIds.first()

        val today = LocalDate.now()
        val entries = repository.getEntriesFrom(Defaults.PERSON_ID, today)
            .filter { it.shiftTypeId != null }
        val shifts = repository.getShifts().associateBy { it.id }
        val locations = repository.getLocations().associateBy { it.id }

        // 目标事件(按开始日期)。
        // 注:ColorOS 桌面「日程」卡片只显示全天事件(实测),因此班次统一写成全天事件,
        // 时间信息保留在标题里(如"中班 08:00-16:00")。
        val targets = entries.mapNotNull { entry ->
            val shift = entry.shiftTypeId?.let { shifts[it] } ?: return@mapNotNull null
            val descParts = buildList {
                entry.locationId?.let { id ->
                    locations[id]?.let { add("地点:${it.name}") }
                }
                entry.note?.takeIf { it.isNotBlank() }?.let { add("备注:$it") }
            }
            EventTarget(
                date = entry.date,
                title = "${shift.name} ${TimeFormat.formatMinute(shift.startMinute)}-${TimeFormat.formatMinute(shift.endMinute)}",
                description = descParts.joinToString("\n").ifEmpty { null },
                dtStart = toMillis(entry.date, 0),
                dtEnd = toMillis(entry.date.plusDays(1), 0),
                allDay = true,
                color = shift.color
            )
        }
        val targetDates = targets.map { it.date }.toSet()

        // 现有本应用事件(覆盖所有同名日历,防历史重复日历残留)
        val existing = calendarIds.flatMap { queryEvents(it) }

        // 删除:历史事件 + 班表已无对应班次的日期(休/删除班次)
        val toDelete = existing.filter { it.date.isBefore(today) || it.date !in targetDates }
        toDelete.forEach {
            resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, it.id), null, null)
        }

        // 逐日重写:先删当天旧事件,再插入最新事件(保证与班表一致)
        var synced = 0
        targets.forEach { target ->
            existing.filter { it.date == target.date }.forEach {
                resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, it.id), null, null)
            }
            insertEvent(calendarId, target)
            synced++
        }
        return synced
    }

    private fun queryEvents(calendarId: Long): List<ExistingEvent> {
        val result = mutableListOf<ExistingEvent>()
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.DTSTART),
            "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.CUSTOM_APP_PACKAGE}=?",
            arrayOf(calendarId.toString(), APP_PACKAGE),
            null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val dtIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            while (cursor.moveToNext()) {
                val millis = cursor.getLong(dtIdx)
                val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                result.add(ExistingEvent(cursor.getLong(idIdx), date))
            }
        }
        return result
    }

    private fun insertEvent(calendarId: Long, target: EventTarget) {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, target.title)
            put(CalendarContract.Events.DTSTART, target.dtStart)
            put(CalendarContract.Events.DTEND, target.dtEnd)
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            put(CalendarContract.Events.CUSTOM_APP_PACKAGE, APP_PACKAGE)
            if (target.allDay) put(CalendarContract.Events.ALL_DAY, 1)
            target.description?.let { put(CalendarContract.Events.DESCRIPTION, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                put(CalendarContract.Events.EVENT_COLOR, target.color)
            }
        }
        resolver.insert(CalendarContract.Events.CONTENT_URI, values)
    }

    private fun findCalendarId(): Long? {
        return findCalendarIds().firstOrNull()
    }

    private fun findCalendarIds(): List<Long> {
        val ids = mutableListOf<Long>()
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_TYPE}=? AND ${CalendarContract.Calendars.ACCOUNT_NAME}=?",
            arrayOf(ACCOUNT_TYPE, ACCOUNT_NAME),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)))
            }
        }
        return ids
    }

    private fun ensureCalendar(): Long? {
        findCalendarId()?.let { return it }
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            put(CalendarContract.Calendars.NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF1565C0.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        val uri = resolver.insert(
            CalendarContract.Calendars.CONTENT_URI
                .buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
                .build(),
            values
        )
        return uri?.lastPathSegment?.toLongOrNull()
    }

    private fun toMillis(date: LocalDate, minute: Int): Long =
        date.atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli()

    private data class ExistingEvent(val id: Long, val date: LocalDate)

    private data class EventTarget(
        val date: LocalDate,
        val title: String,
        val description: String?,
        val dtStart: Long,
        val dtEnd: Long,
        val allDay: Boolean,
        val color: Int
    )
}

