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
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 班表 → 系统日历单向同步。
 *
 * 会话8 重构,修复两个真机问题:
 *
 * 1. **日期全部提前一天**:ColorOS/realme 日历把全天事件的 DTSTART 毫秒值按 UTC 日期解读,
 *    原"本地时区午夜"写法(如 8月17日 00:00 北京时间 = 8月16日 16:00 UTC)导致所有事件
 *    显示为前一天。修复:全天事件统一按 **UTC 午夜** 写入/读回(全天事件的标准约定),
 *    无论日历按 UTC 还是本地时间显示,日期都正确(北京时间 GMT+8 下不会跨日)。
 *
 * 2. **划掉 App 后整个「班表助手」日历消失**:旧实现自建的"孤儿日历"
 *    (ACCOUNT_TYPE=com.lh.shiftassistant.calendar,无真实同步账户)会被 realme/ColorOS
 *    在 App 被划掉(进程被杀、视为已停止)后清理。修复:不再自建日历,
 *    改为写入**系统自带的可写日历**(设置页可手动选择),事件仍以 CUSTOM_APP_PACKAGE
 *    标记,只增删改本应用写入的事件,绝不触碰用户其他事件;旧自建日历自动迁移清理。
 *
 * 3. **(会话9)事件标题改为「地点 + 时间」**:原为「班次名 + 时间」,桌面日程卡片上
 *    看不出上班地点;现优先用当天所选地点名(未选地点时回退班次名),地点不再重复写进详情。
 */
class CalendarSyncManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val repository: ShiftRepository
) {
    private val resolver = context.contentResolver

    private var pendingJob: Job? = null

    companion object {
        const val ACCOUNT_TYPE = "com.lh.shiftassistant.calendar"
        const val ACCOUNT_NAME = "班表助手"
        const val CALENDAR_NAME = "班表助手"
        const val APP_PACKAGE = "com.lh.shiftassistant"

        /** 全天事件统一用 UTC 午夜(跨显示约定都稳定落在同一天) */
        private val UTC = ZoneOffset.UTC
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

    /** 删除本应用写入日历的全部事件(覆盖所有日历);返回删除数 */
    suspend fun clearAll(): Int {
        if (!hasPermissions()) return 0
        return resolver.delete(
            CalendarContract.Events.CONTENT_URI,
            "${CalendarContract.Events.CUSTOM_APP_PACKAGE}=?",
            arrayOf(APP_PACKAGE)
        )
    }

    /** 关闭开关并清除事件 */
    suspend fun disableAndClear() {
        settings.setCalendarSyncEnabled(false)
        clearAll()
    }

    // ---- 目标日历选择 ----

    /** 当前可选的可写日历列表(供设置页选择同步目标) */
    suspend fun listWritableCalendars(): List<CalendarOption> {
        if (!hasPermissions()) return emptyList()
        val result = mutableListOf<CalendarOption>()
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
            ),
            "${CalendarContract.Calendars.DELETED}=0",
            null,
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val typeIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
            val levelIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
            while (cursor.moveToNext()) {
                // 只列可写日历;排除旧版自建"班表助手"日历
                if (cursor.getInt(levelIdx) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                if (cursor.getString(typeIdx) == ACCOUNT_TYPE) continue
                result.add(
                    CalendarOption(
                        id = cursor.getLong(idIdx),
                        displayName = cursor.getString(nameIdx) ?: "(未命名)",
                        accountName = cursor.getString(accIdx) ?: "",
                        accountType = cursor.getString(typeIdx) ?: ""
                    )
                )
            }
        }
        return result
    }

    /** 当前生效的同步目标日历名(设置页展示用);无可写日历返回 null */
    suspend fun currentTargetName(): String? {
        val id = resolveTargetCalendarId() ?: return null
        return listWritableCalendars().firstOrNull { it.id == id }?.displayName
    }

    /** 用户手动指定同步目标日历;null = 恢复自动选择 */
    suspend fun selectCalendar(id: Long?) {
        settings.setSyncCalendarId(id)
    }

    // ---- 内部 ----

    /**
     * 解析同步目标日历:
     * 1. 设置里用户选定的日历(若仍存在且可写);
     * 2. 否则自动挑选:系统本地日历(ACCOUNT_TYPE=LOCAL)优先,其次名称含"本地/我的/个人",
     *    再任意第一个可写日历,并把选择持久化;
     * 3. 都没有则回退自建日历(旧行为;可能被系统清理,但至少可用)。
     */
    private suspend fun resolveTargetCalendarId(): Long? {
        val writable = listWritableCalendars()
        if (writable.isEmpty()) return ensureCalendar()
        settings.syncCalendarIdOnce()?.let { id ->
            if (writable.any { it.id == id }) return id
        }
        val picked = writable.firstOrNull { it.accountType == "LOCAL" }
            ?: writable.firstOrNull {
                it.displayName.contains("本地") || it.displayName.contains("我的") ||
                    it.displayName.contains("个人")
            }
            ?: writable.first()
        settings.setSyncCalendarId(picked.id)
        return picked.id
    }

    /** 全量对齐:目标日历(今天起)与本应用事件逐日比对,增/改/删;并清理旧自建日历残留 */
    private suspend fun align(): Int {
        if (!hasPermissions()) return 0
        val calendarId = resolveTargetCalendarId() ?: return 0

        // 清理:其他日历中本应用的旧事件(目标切换/旧自建日历残留)+ 旧自建日历空壳行
        cleanupForeignEvents(calendarId)
        removeLegacyCalendarRows(calendarId)

        val today = LocalDate.now()
        val entries = repository.getEntriesFrom(Defaults.PERSON_ID, today)
            .filter { it.shiftTypeId != null }
        val shifts = repository.getShifts().associateBy { it.id }
        val locations = repository.getLocations().associateBy { it.id }

        // 目标事件(按开始日期)。
        // 注:ColorOS 桌面「日程」卡片只显示全天事件(实测),因此班次统一写成全天事件,
        // 时间信息保留在标题里。
        // 会话9:标题由「班次名 时间」改为「地点 时间」(未选地点时回退班次名),
        //       便于在桌面卡片上直接看出上班地点;地点已进标题,故详情不再重复写地点。
        val targets = entries.mapNotNull { entry ->
            val shift = entry.shiftTypeId?.let { shifts[it] } ?: return@mapNotNull null
            val location = entry.locationId?.let { locations[it] }
            val descParts = buildList {
                entry.note?.takeIf { it.isNotBlank() }?.let { add("备注:$it") }
            }
            EventTarget(
                date = entry.date,
                title = TimeFormat.eventTitle(
                    shiftName = shift.name,
                    locationName = location?.name,
                    startMinute = shift.startMinute,
                    endMinute = shift.endMinute
                ),
                description = descParts.joinToString("\n").ifEmpty { null },
                dtStart = toUtcMillis(entry.date),
                dtEnd = toUtcMillis(entry.date.plusDays(1)),
                allDay = true,
                color = shift.color
            )
        }
        val targetDates = targets.map { it.date }.toSet()

        // 现有本应用事件(目标日历内)
        val existing = queryEvents(calendarId)

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

    /** 删除其他日历中本应用写入的旧事件(目标切换 / 旧自建日历残留) */
    private fun cleanupForeignEvents(targetCalendarId: Long) {
        resolver.delete(
            CalendarContract.Events.CONTENT_URI,
            "${CalendarContract.Events.CALENDAR_ID}!=? AND ${CalendarContract.Events.CUSTOM_APP_PACKAGE}=?",
            arrayOf(targetCalendarId.toString(), APP_PACKAGE)
        )
    }

    /**
     * 清理旧版自建"班表助手"日历行(空壳残留);删除失败可忽略。
     * 跳过当前同步目标(回退自建场景下目标本身不能被删)。
     */
    private fun removeLegacyCalendarRows(targetCalendarId: Long) {
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_TYPE}=?",
            arrayOf(ACCOUNT_TYPE),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
                if (id == targetCalendarId) continue
                runCatching {
                    resolver.delete(
                        ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id),
                        null, null
                    )
                }
            }
        }
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
                // 全天事件按 UTC 读回(与写入约定一致)
                val date = Instant.ofEpochMilli(millis).atZone(UTC).toLocalDate()
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
            // 全天事件统一用 UTC 时区(标准约定;ColorOS 按 UTC 解读 DTSTART,故必须 UTC 午夜)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
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

    /** 回退:自建"班表助手"日历(仅当系统无可写日历;可能被 realme/ColorOS 清理) */
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

    /** 全天事件时间:目标日期 00:00(UTC) */
    private fun toUtcMillis(date: LocalDate): Long =
        date.atStartOfDay(UTC).toInstant().toEpochMilli()

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

/** 可写日历选项(设置页选择同步目标用) */
data class CalendarOption(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String
)
