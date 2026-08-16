package com.lh.shiftassistant.backup

import kotlinx.serialization.Serializable

/**
 * 备份文件格式(与《开发方案.md》第6节一致)。
 * version = 1;导入时校验,整体覆盖数据库。
 */
@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: String = "",
    val persons: List<BackupPerson> = emptyList(),
    val shiftTypes: List<BackupShiftType> = emptyList(),
    val locations: List<BackupLocation> = emptyList(),
    val entries: List<BackupEntry> = emptyList()
)

@Serializable
data class BackupPerson(
    val id: Long,
    val name: String
)

@Serializable
data class BackupShiftType(
    val id: Long,
    val name: String,
    val shortName: String,
    val startMinute: Int,
    val endMinute: Int,
    val color: Int,
    val sortOrder: Int = 0,
    val isBuiltin: Boolean = false
)

@Serializable
data class BackupLocation(
    val id: Long,
    val name: String,
    val sortOrder: Int = 0
)

@Serializable
data class BackupEntry(
    val date: String,
    val personId: Long = 1,
    val shiftTypeId: Long? = null,
    val locationId: Long? = null,
    val originalShiftTypeId: Long? = null,
    val note: String? = null
)
