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
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllOnce(): List<LocationEntity>

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getById(id: Long): LocationEntity?

    @Insert
    suspend fun insert(location: LocationEntity): Long

    @Update
    suspend fun update(location: LocationEntity)

    @Delete
    suspend fun delete(location: LocationEntity)

    @Query("DELETE FROM locations")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(locations: List<LocationEntity>)
}
