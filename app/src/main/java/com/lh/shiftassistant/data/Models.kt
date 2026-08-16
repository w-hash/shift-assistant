package com.lh.shiftassistant.data

import java.time.LocalDate

/** 某天的完整详情(当日编辑弹层使用) */
data class DayDetail(
    val entry: ScheduleEntryEntity?,
    val shift: ShiftTypeEntity?,
    val originalShift: ShiftTypeEntity?,
    val location: LocationEntity?
)

/** 小组件格子数据 */
data class WidgetDay(
    val date: LocalDate,
    val isToday: Boolean,
    val shiftShortName: String?,
    val shiftColor: Int?,
    val hasTrace: Boolean
)
