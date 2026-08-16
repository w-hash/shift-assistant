package com.lh.shiftassistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 人员(二期预留,一期默认只有"我") */
@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
