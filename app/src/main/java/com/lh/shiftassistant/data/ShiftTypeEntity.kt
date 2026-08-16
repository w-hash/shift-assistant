package com.lh.shiftassistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 班次类型。时间统一用"分钟数"表示:
 * startMinute 范围 0..1439,endMinute 范围 1..1440(1440 表示次日 00:00,显示为 24:00)。
 */
@Entity(tableName = "shift_types")
data class ShiftTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val shortName: String,
    val startMinute: Int,
    val endMinute: Int,
    /** ARGB 色值 */
    val color: Int,
    val sortOrder: Int,
    /** 预置班次标记(可编辑) */
    val isBuiltin: Boolean = false
) {
    /** 时长(分钟) */
    val durationMinutes: Int get() = endMinute - startMinute
}
