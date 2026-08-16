package com.lh.shiftassistant.data

import androidx.room.withTransaction
import com.lh.shiftassistant.backup.BackupData
import com.lh.shiftassistant.backup.BackupEntry
import com.lh.shiftassistant.backup.BackupLocation
import com.lh.shiftassistant.backup.BackupPerson
import com.lh.shiftassistant.backup.BackupShiftType
import com.lh.shiftassistant.settings.SettingsRepository
import com.lh.shiftassistant.widget.WidgetUpdater
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 数据仓库:班次/地点/排班 CRUD、换班痕迹、小组件数据、备份快照与还原。
 *
 * 响应式设计说明:本机 Room KSP 无法处理 DAO 中的 Flow 返回类型(见 PersonDao 注释),
 * 因此所有读取均为 suspend 一次性查询;任何写入都会递增 [dataVersion],
 * UI 层以 dataVersion 为 key 重新加载数据。
 */
class ShiftRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val widgetUpdater: WidgetUpdater,
    private val scope: CoroutineScope,
    private val onWrite: (() -> Unit)? = null
) {
    private val personDao = db.personDao()
    private val shiftDao = db.shiftTypeDao()
    private val locationDao = db.locationDao()
    private val scheduleDao = db.scheduleDao()

    /** 数据版本号:任何写入后 +1,UI 以此驱动刷新 */
    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    // ---- 一次性读取 ----
    suspend fun getPersons(): List<PersonEntity> = personDao.getAllOnce()
    suspend fun getShifts(): List<ShiftTypeEntity> = shiftDao.getAllOnce()
    suspend fun getLocations(): List<LocationEntity> = locationDao.getAllOnce()

    /** 某月排班(日期 -> 记录) */
    suspend fun getMonthEntries(personId: Long, yearMonth: YearMonth): Map<LocalDate, ScheduleEntryEntity> =
        scheduleDao.getBetween(personId, yearMonth.atDay(1), yearMonth.atEndOfMonth())
            .associateBy { it.date }

    /** 从某天起的所有排班(日历同步用) */
    suspend fun getEntriesFrom(personId: Long, start: LocalDate): List<ScheduleEntryEntity> =
        scheduleDao.getBetween(personId, start, LocalDate.of(9999, 12, 31))

    /** 某天的完整详情(今日卡片/当日编辑) */
    suspend fun dayDetail(personId: Long, date: LocalDate): DayDetail {
        val entry = scheduleDao.getEntry(personId, date)
        return DayDetail(
            entry = entry,
            shift = entry?.shiftTypeId?.let { shiftDao.getById(it) },
            originalShift = entry?.originalShiftTypeId?.let { shiftDao.getById(it) },
            location = entry?.locationId?.let { locationDao.getById(it) }
        )
    }

    // ---- 首次启动初始化 ----
    suspend fun ensureDefaults() {
        var changed = false
        if (personDao.count() == 0) {
            personDao.insert(PersonEntity(name = Defaults.PERSON_NAME))
            changed = true
        }
        if (shiftDao.count() == 0) {
            shiftDao.insertAll(Defaults.defaultShifts())
            changed = true
        }
        if (changed) bumpVersion()
    }

    // ---- 班次 CRUD ----
    suspend fun addShift(
        name: String,
        shortName: String,
        startMinute: Int,
        endMinute: Int,
        color: Int,
        sortOrder: Int
    ): Long = shiftDao.insert(
        ShiftTypeEntity(
            name = name, shortName = shortName,
            startMinute = startMinute, endMinute = endMinute,
            color = color, sortOrder = sortOrder
        )
    ).also { afterWrite() }

    suspend fun updateShift(shift: ShiftTypeEntity) {
        shiftDao.update(shift)
        afterWrite()
    }

    suspend fun deleteShift(shift: ShiftTypeEntity) {
        shiftDao.delete(shift)
        afterWrite()
    }

    suspend fun shiftById(id: Long): ShiftTypeEntity? = shiftDao.getById(id)

    // ---- 地点 CRUD ----
    suspend fun addLocation(name: String, sortOrder: Int = 0): Long =
        locationDao.insert(LocationEntity(name = name, sortOrder = sortOrder)).also { afterWrite() }

    suspend fun updateLocation(location: LocationEntity) {
        locationDao.update(location)
        afterWrite()
    }

    suspend fun deleteLocation(location: LocationEntity) {
        locationDao.delete(location)
        afterWrite()
    }

    // ---- 排班读写(含换班痕迹) ----
    suspend fun setDay(
        personId: Long,
        date: LocalDate,
        shiftTypeId: Long?,
        locationId: Long?,
        note: String?
    ) {
        val existing = scheduleDao.getEntry(personId, date)
        val newOriginal = computeOriginalTrace(existing?.shiftTypeId, existing?.originalShiftTypeId, shiftTypeId)
        val entry = ScheduleEntryEntity(
            id = existing?.id ?: 0,
            personId = personId,
            date = date,
            shiftTypeId = shiftTypeId,
            locationId = locationId,
            originalShiftTypeId = newOriginal,
            note = note?.takeIf { it.isNotBlank() },
            updatedAt = System.currentTimeMillis()
        )
        scheduleDao.upsert(entry)
        afterWrite()
    }

    // ---- 小组件数据 ----
    suspend fun widgetDays(): List<WidgetDay> {
        val n = settings.widgetDaysOnce()
        val today = LocalDate.now()
        val start = today
        val end = today.plusDays((n - 1).toLong())
        val entries = scheduleDao.getBetween(Defaults.PERSON_ID, start, end).associateBy { it.date }
        val shifts = shiftDao.getAllOnce().associateBy { it.id }
        return (0 until n).map { i ->
            val date = today.plusDays(i.toLong())
            val entry = entries[date]
            val shift = entry?.shiftTypeId?.let { shifts[it] }
            WidgetDay(
                date = date,
                isToday = i == 0,
                shiftShortName = shift?.shortName,
                shiftColor = shift?.color,
                hasTrace = entry?.originalShiftTypeId != null
            )
        }
    }

    // ---- 备份 ----
    suspend fun exportSnapshot(): BackupData {
        val now = java.time.OffsetDateTime.now().toString()
        return BackupData(
            version = 1,
            exportedAt = now,
            persons = personDao.getAllOnce().map { BackupPerson(id = it.id, name = it.name) },
            shiftTypes = shiftDao.getAllOnce().map { it.toBackup() },
            locations = locationDao.getAllOnce().map { it.toBackup() },
            entries = scheduleDao.getAll().map { it.toBackup() }
        )
    }

    suspend fun restoreSnapshot(data: BackupData) {
        require(data.version == 1) { "不支持的备份版本:${data.version}" }
        db.withTransaction {
            scheduleDao.clearAll()
            locationDao.clearAll()
            shiftDao.clearAll()
            personDao.clearAll()

            personDao.insertAll(data.persons.map { PersonEntity(id = it.id, name = it.name) })
            shiftDao.insertAll(data.shiftTypes.map { it.toEntity() })
            locationDao.insertAll(data.locations.map { LocationEntity(id = it.id, name = it.name, sortOrder = it.sortOrder) })
            scheduleDao.upsertAll(data.entries.map { it.toEntity() })
        }
        afterWrite()
    }

    // ---- 内部 ----
    private fun bumpVersion() {
        _dataVersion.value = _dataVersion.value + 1
    }

    /** 任何写入后:刷新 UI 版本号 + 刷新小组件 + 触发日历同步 */
    private fun afterWrite() {
        bumpVersion()
        scope.launch { widgetUpdater.refreshAll() }
        onWrite?.invoke()
    }
}

// ---- 备份 <-> 实体转换 ----
private fun ShiftTypeEntity.toBackup() = BackupShiftType(
    id = id, name = name, shortName = shortName,
    startMinute = startMinute, endMinute = endMinute,
    color = color, sortOrder = sortOrder, isBuiltin = isBuiltin
)

private fun BackupShiftType.toEntity() = ShiftTypeEntity(
    id = id, name = name, shortName = shortName,
    startMinute = startMinute, endMinute = endMinute,
    color = color, sortOrder = sortOrder, isBuiltin = isBuiltin
)

private fun LocationEntity.toBackup() = BackupLocation(id = id, name = name, sortOrder = sortOrder)

private fun ScheduleEntryEntity.toBackup() = BackupEntry(
    date = date.toString(),
    personId = personId,
    shiftTypeId = shiftTypeId,
    locationId = locationId,
    originalShiftTypeId = originalShiftTypeId,
    note = note
)

private fun BackupEntry.toEntity() = ScheduleEntryEntity(
    personId = personId,
    date = LocalDate.parse(date),
    shiftTypeId = shiftTypeId,
    locationId = locationId,
    originalShiftTypeId = originalShiftTypeId,
    note = note
)
