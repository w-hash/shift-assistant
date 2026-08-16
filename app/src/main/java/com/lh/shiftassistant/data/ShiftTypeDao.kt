package com.lh.shiftassistant.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/**
 * 注意:DAO 不使用 Flow 返回值,原因见 PersonDao 注释(本机 Room KSP 限制)。
 */
@Dao
interface ShiftTypeDao {
    @Query("SELECT * FROM shift_types ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllOnce(): List<ShiftTypeEntity>

    @Query("SELECT * FROM shift_types WHERE id = :id")
    suspend fun getById(id: Long): ShiftTypeEntity?

    @Query("SELECT COUNT(*) FROM shift_types")
    suspend fun count(): Int

    @Insert
    suspend fun insert(shift: ShiftTypeEntity): Long

    @Update
    suspend fun update(shift: ShiftTypeEntity)

    @Delete
    suspend fun delete(shift: ShiftTypeEntity)

    @Query("DELETE FROM shift_types")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(shifts: List<ShiftTypeEntity>)
}
