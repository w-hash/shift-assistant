package com.lh.shiftassistant.data

/** 首次安装预置数据 */
object Defaults {
    const val PERSON_ID = 1L
    const val PERSON_NAME = "我"

    /** 预置班次:早/中/晚,均 8 小时 */
    fun defaultShifts(): List<ShiftTypeEntity> = listOf(
        ShiftTypeEntity(
            name = "早班", shortName = "早",
            startMinute = 0, endMinute = 480,
            color = 0xFF2196F3.toInt(), sortOrder = 1, isBuiltin = true
        ),
        ShiftTypeEntity(
            name = "中班", shortName = "中",
            startMinute = 480, endMinute = 960,
            color = 0xFF4CAF50.toInt(), sortOrder = 2, isBuiltin = true
        ),
        ShiftTypeEntity(
            name = "晚班", shortName = "晚",
            startMinute = 960, endMinute = 1440,
            color = 0xFFFF9800.toInt(), sortOrder = 3, isBuiltin = true
        )
    )
}
