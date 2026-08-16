package com.lh.shiftassistant.util

import java.time.LocalDate
import java.time.YearMonth

/**
 * 月历网格纯计算(便于单元测试)。
 * 固定 6 行 x 7 列;周一为一周起点(用户习惯)。
 */
object MonthGrid {

    /** 某月首日之前的偏移天数(周一起点) */
    fun leadingOffset(yearMonth: YearMonth): Int =
        (yearMonth.atDay(1).dayOfWeek.value - 1) % 7 // Mon=1..Sun=7

    /** 生成 42 个格子对应的日期(可能含上月/下月的补位日期) */
    fun cells(yearMonth: YearMonth): List<LocalDate> {
        val first = yearMonth.atDay(1)
        val start = first.minusDays(leadingOffset(yearMonth).toLong())
        return (0 until 42).map { start.plusDays(it.toLong()) }
    }

    /** 6 行分组(每周一行) */
    fun weeks(yearMonth: YearMonth): List<List<LocalDate>> =
        cells(yearMonth).chunked(7)
}
