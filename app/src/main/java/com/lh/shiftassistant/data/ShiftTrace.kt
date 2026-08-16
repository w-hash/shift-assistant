package com.lh.shiftassistant.data

/**
 * 换班痕迹计算规则(纯函数,便于单元测试):
 *
 * 某天已有班次 A,新选班次 B:
 * - B 为空(设为休):保留原有痕迹(痕迹仍可查看)
 * - 原来无班(或无记录)选了班次:无痕迹
 * - B == A(班次未变/改回当前班):清空痕迹
 * - B == 原班(改回最初的原班):清空痕迹
 * - B != A(换班):首次记录原班 A,若已有痕迹则保留原值
 */
fun computeOriginalTrace(
    currentShiftId: Long?,
    originalShiftId: Long?,
    newShiftId: Long?
): Long? = when {
    newShiftId == null -> originalShiftId
    currentShiftId == null -> null
    currentShiftId == newShiftId -> null
    newShiftId == originalShiftId -> null
    else -> originalShiftId ?: currentShiftId
}
