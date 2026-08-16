package com.lh.shiftassistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 地点库(无默认值,用户手动维护) */
@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0
)
