package com.lh.shiftassistant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.LocalDate

/**
 * 注意:DAO 不使用 Flow 返回值,原因见 PersonDao 注释(本机 Room KSP 限制)。
 * 排班数据的一次性查询 + Repository.dataVersion 刷新机制驱动 UI。
 */
@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_entries WHERE personId = :personId AND date = :date LIMIT 1")
    suspend fun getEntry(personId: Long, date: LocalDate): ScheduleEntryEntity?

    @Query("SELECT * FROM schedule_entries WHERE personId = :personId AND date BETWEEN :start AND :end ORDER BY date ASC")
    suspend fun getBetween(personId: Long, start: LocalDate, end: LocalDate): List<ScheduleEntryEntity>

    @Query("SELECT * FROM schedule_entries ORDER BY date ASC")
    suspend fun getAll(): List<ScheduleEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ScheduleEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<ScheduleEntryEntity>)

    @Query("DELETE FROM schedule_entries")
    suspend fun clearAll()
}
