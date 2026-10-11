package com.lh.shiftassistant.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** 日历事件标题规则(会话9):优先「地点 + 时间」,无地点回退「班次名 + 时间」 */
class TimeFormatEventTitleTest {

    @Test
    fun withLocation_usesLocationAsHead() {
        assertEquals(
            "进口 16:00-24:00",
            TimeFormat.eventTitle(shiftName = "晚班", locationName = "进口", startMinute = 960, endMinute = 1440)
        )
        assertEquals(
            "A车间 08:00-16:00",
            TimeFormat.eventTitle(shiftName = "中班", locationName = "A车间", startMinute = 480, endMinute = 960)
        )
    }

    @Test
    fun withoutLocation_fallsBackToShiftName() {
        assertEquals(
            "晚班 16:00-24:00",
            TimeFormat.eventTitle(shiftName = "晚班", locationName = null, startMinute = 960, endMinute = 1440)
        )
    }

    @Test
    fun blankLocation_fallsBackToShiftName() {
        assertEquals(
            "晚班 16:00-24:00",
            TimeFormat.eventTitle(shiftName = "晚班", locationName = "", startMinute = 960, endMinute = 1440)
        )
        assertEquals(
            "早班 00:00-08:00",
            TimeFormat.eventTitle(shiftName = "早班", locationName = "   ", startMinute = 0, endMinute = 480)
        )
    }

    @Test
    fun endMinute1440_showsAs24h() {
        assertEquals(
            "夜班 20:00-24:00",
            TimeFormat.eventTitle(shiftName = "夜班", locationName = null, startMinute = 1200, endMinute = 1440)
        )
    }
}
