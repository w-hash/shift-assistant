package com.lh.shiftassistant.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShiftTraceTest {

    // 场景:原班 A(id=1)、新班 B(id=2)

    @Test
    fun first_change_records_original() {
        // 已有 A,改选 B -> 记录原班 A
        assertEquals(1L, computeOriginalTrace(currentShiftId = 1L, originalShiftId = null, newShiftId = 2L))
    }

    @Test
    fun keep_original_when_already_traced() {
        // 已有 B(痕迹 A),再改成 C -> 保留痕迹 A(不覆盖为 B)
        assertEquals(1L, computeOriginalTrace(currentShiftId = 2L, originalShiftId = 1L, newShiftId = 3L))
    }

    @Test
    fun revert_to_original_clears_trace() {
        // 已有 B(痕迹 A),改回 A -> 清空痕迹
        assertNull(computeOriginalTrace(currentShiftId = 2L, originalShiftId = 1L, newShiftId = 1L))
    }

    @Test
    fun same_shift_clears_trace() {
        // 已有 A,再选 A -> 无痕迹
        assertNull(computeOriginalTrace(currentShiftId = 1L, originalShiftId = null, newShiftId = 1L))
        // 已有 A(痕迹 B),再选 A -> 清空
        assertNull(computeOriginalTrace(currentShiftId = 1L, originalShiftId = 2L, newShiftId = 1L))
    }

    @Test
    fun set_to_off_keeps_trace() {
        // 已有 A(无痕迹),设为休 -> 无痕迹
        assertNull(computeOriginalTrace(currentShiftId = 1L, originalShiftId = null, newShiftId = null))
        // 已有 B(痕迹 A),设为休 -> 保留痕迹 A(仍可查看原班)
        assertEquals(1L, computeOriginalTrace(currentShiftId = 2L, originalShiftId = 1L, newShiftId = null))
    }

    @Test
    fun no_previous_no_trace() {
        // 原来休/无记录,直接选班次 -> 无痕迹
        assertNull(computeOriginalTrace(currentShiftId = null, originalShiftId = null, newShiftId = 2L))
    }
}
