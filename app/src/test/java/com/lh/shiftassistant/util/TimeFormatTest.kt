package com.lh.shiftassistant.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    @Test
    fun formatMinute_basic() {
        assertEquals("00:00", TimeFormat.formatMinute(0))
        assertEquals("08:00", TimeFormat.formatMinute(480))
        assertEquals("16:00", TimeFormat.formatMinute(960))
        assertEquals("23:59", TimeFormat.formatMinute(1439))
    }

    @Test
    fun formatMinute_1440_is_24h() {
        assertEquals("24:00", TimeFormat.formatMinute(1440))
    }

    @Test
    fun duration_8h() {
        assertEquals("8小时", TimeFormat.durationText(0, 480))
        assertEquals("8小时", TimeFormat.durationText(480, 960))
        assertEquals("8小时", TimeFormat.durationText(960, 1440))
    }

    @Test
    fun duration_with_minutes() {
        assertEquals("8小时30分", TimeFormat.durationText(0, 510))
        assertEquals("30分", TimeFormat.durationText(480, 510))
        assertEquals("12小时", TimeFormat.durationText(960, 1440 + 240)) // 跨天到次日04:00
    }

    @Test
    fun duration_zero() {
        assertEquals("0分钟", TimeFormat.durationText(480, 480))
    }
}
