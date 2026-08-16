package com.lh.shiftassistant.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 每日排班记录。date = 班次归属日(开始日),跨天班按开始日期归属。
 * shiftTypeId 为空 = 未排班(显示"休")。
 * originalShiftTypeId 记录换班痕迹;为空表示无换班。
 *
 * 删除班次/地点时,相关引用置空(该天变"休"),不产生脏引用。
 */
@Entity(
    tableName = "schedule_entries",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ShiftTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["shiftTypeId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ShiftTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["originalShiftTypeId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["personId", "date"], unique = true),
        Index(value = ["shiftTypeId"]),
        Index(value = ["originalShiftTypeId"]),
        Index(value = ["locationId"])
    ]
)
data class ScheduleEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long = 1,
    val date: LocalDate,
    val shiftTypeId: Long? = null,
    val locationId: Long? = null,
    val originalShiftTypeId: Long? = null,
    val note: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
