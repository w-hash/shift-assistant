package com.lh.shiftassistant.util

/**
 * 分钟数与显示文本的转换。
 * 1440 表示次日 00:00,统一显示为 "24:00"(不显示 "00:00")。
 */
object TimeFormat {

    /**
     * 日历事件标题(会话9 改版):优先「地点 + 时间」,便于直接看出上班地点。
     * 未选地点时回退「班次名 + 时间」,避免出现只有时间、看不出班次的标题。
     *
     * 例:("晚班", "进口", 960, 1440) -> "进口 16:00-24:00"
     *     ("晚班", null, 960, 1440)   -> "晚班 16:00-24:00"
     *     ("晚班", "  ", 960, 1440)   -> "晚班 16:00-24:00"
     */
    fun eventTitle(shiftName: String, locationName: String?, startMinute: Int, endMinute: Int): String {
        val head = locationName?.takeIf { it.isNotBlank() } ?: shiftName
        return "$head ${formatMinute(startMinute)}-${formatMinute(endMinute)}"
    }

    /** 分钟 -> "HH:mm"(1440 -> "24:00") */
    fun formatMinute(minute: Int): String {
        val m = minute.coerceIn(0, 1440)
        if (m == 1440) return "24:00"
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /** 时长文本,如 480 -> "8小时",510 -> "8小时30分" */
    fun durationText(startMinute: Int, endMinute: Int): String {
        val minutes = (endMinute - startMinute).coerceAtLeast(0)
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}小时${mins}分"
            hours > 0 -> "${hours}小时"
            mins > 0 -> "${mins}分"
            else -> "0分钟"
        }
    }
}
