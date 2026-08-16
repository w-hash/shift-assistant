package com.lh.shiftassistant.util

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthGridTest {

    @Test
    fun leadingOffset_monday_start() {
        // 2026-08-01 是周六(dayOfWeek=6),周一起点偏移应为 5
        assertEquals(5, MonthGrid.leadingOffset(YearMonth.of(2026, 8)))
        // 2026-09-01 是周二(dayOfWeek=2),偏移应为 1
        assertEquals(1, MonthGrid.leadingOffset(YearMonth.of(2026, 9)))
    }

    @Test
    fun cells_42_and_first_weekday() {
        val cells = MonthGrid.cells(YearMonth.of(2026, 8))
        assertEquals(42, cells.size)
        // 第一格是周一
        assertEquals(1, cells.first().dayOfWeek.value)
        // 每周 7 天
        assertEquals(6, MonthGrid.weeks(YearMonth.of(2026, 8)).size)
        MonthGrid.weeks(YearMonth.of(2026, 8)).forEach { week ->
            assertEquals(7, week.size)
        }
    }

    @Test
    fun cross_year_transition() {
        // 2025-12-01 是周一,所以 12 月第一格就是 12-01
        val dec = MonthGrid.cells(YearMonth.of(2025, 12))
        assertEquals(LocalDate.of(2025, 12, 1), dec.first())
        // 2026-01-01 是周四,1 月第一格应是 2025-12-29(跨年补位)
        val jan = MonthGrid.cells(YearMonth.of(2026, 1))
        assertEquals(LocalDate.of(2025, 12, 29), jan.first())
        assertEquals(42, jan.size)
        // 1 月最后一行包含 2026-02-08(跨月补位)
        assertTrue(jan.any { it == LocalDate.of(2026, 2, 8) })
    }

    @Test
    fun month_contains_its_days() {
        val ym = YearMonth.of(2026, 2) // 28 天
        val cells = MonthGrid.cells(ym)
        (1..28).forEach { d ->
            assertTrue("缺少 $d 日", cells.contains(LocalDate.of(2026, 2, d)))
        }
    }
}
