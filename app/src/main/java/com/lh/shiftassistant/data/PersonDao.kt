package com.lh.shiftassistant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * 注意:本机环境下 Room KSP 无法解析 DAO 返回值中的 kotlinx.coroutines Flow 类型(JDK 21 + KSP 组合 bug),
 * 因此 DAO 一律使用 suspend + 一次性查询,响应式刷新由 Repository 的 dataVersion 机制驱动。
 */
@Dao
interface PersonDao {
    @Query("SELECT * FROM persons ORDER BY id ASC")
    suspend fun getAllOnce(): List<PersonEntity>

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun getById(id: Long): PersonEntity?

    @Query("SELECT COUNT(*) FROM persons")
    suspend fun count(): Int

    @Insert
    suspend fun insert(person: PersonEntity): Long

    @Insert
    suspend fun insertAll(persons: List<PersonEntity>)

    @Query("DELETE FROM persons")
    suspend fun clearAll()
}
