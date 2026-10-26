package com.suitanglu.app.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 课程表匹配：按星期几（1=周一 … 7=周日）配置课程名与时间段。
 * 时间段可在设置页修改（每天独立，默认五个标准时段）。
 */
object CourseSchedule {
    const val UNNAMED = "未命名"

    fun dayNames(): List<String> = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun localDateTime(millis: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

    /** 分钟数（从 00:00 起） */
    private fun minutesOf(millis: Long): Int {
        val t = localDateTime(millis)
        return t.hour * 60 + t.minute
    }

    fun dayOfWeek(millis: Long): Int = localDateTime(millis).dayOfWeek.value // 1=Mon..7=Sun

    /** 返回命中的时间段（1–5），未命中返回 null */
    fun matchSlot(millis: Long, slots: List<DaySlotTime>): DaySlotTime? {
        val m = minutesOf(millis)
        return slots.firstOrNull { m >= it.startMinute && m < it.endMinute }
    }

    /**
     * 判定课程名：按开始时间命中星期几 + 时间段。
     * 结束时间落入另一个时间段视为跨段，课程名视为空。
     */
    suspend fun resolveCourseName(
        startTime: Long,
        endTime: Long?,
        slotsOf: suspend (day: Int) -> List<DaySlotTime>,
        nameOf: suspend (day: Int, slotIndex: Int) -> String?,
    ): Pair<String?, Int?> {
        val day = dayOfWeek(startTime)
        val slot = matchSlot(startTime, slotsOf(day)) ?: return null to null
        // 结束时间不参与命名。
        //
        // 原实现在"结束时刻落在另一节"时直接返回 null，结果拖堂五分钟、或者连着录了
        // 两节课（09:59 → 11:34 跨过 11:30），整条录音就变成"未命名"——
        // 而它 95% 的内容明明属于开始那一节。以开始时刻为准才是符合直觉的。
        val name = nameOf(day, slot.index)
        val cleaned = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null to slot.index
        return cleaned to slot.index
    }

    fun displayName(courseName: String?, userTitle: String?): String =
        userTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?: courseName?.trim()?.takeIf { it.isNotEmpty() }
            ?: UNNAMED

    fun baseFileName(title: String, startTime: Long): String {
        val fmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        return "${sanitize(title)}_${fmt.format(localDateTime(startTime))}"
    }

    fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60)
}

object Formatters {
    fun duration(ms: Long): String {
        val total = ms / 1000
        return String.format("%02d:%02d:%02d", total / 3600, total % 3600 / 60, total % 60)
    }

    fun clockTime(ms: Long): String {
        val total = ms / 1000
        return String.format("%02d:%02d", total / 60, total % 60)
    }

    /** 时间轴里的时长："98 分钟" —— 比 01:38:12 好读，也不会被误认成时刻 */
    fun durationShort(ms: Long): String {
        val sec = ms / 1000
        return if (sec < 60) "$sec 秒" else "${(sec + 30) / 60} 分钟"
    }

    /** 星期几，用于日期分组头 */
    fun weekdayShort(millis: Long): String {
        val dow = CourseSchedule.localDateTime(millis).dayOfWeek.value
        return "周" + "一二三四五六日"[dow - 1]
    }

    fun dateTime(millis: Long): String {
        val t = CourseSchedule.localDateTime(millis)
        return "%04d-%02d-%02d %02d:%02d".format(t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute)
    }

    fun timeOnly(millis: Long): String {
        val t = CourseSchedule.localDateTime(millis)
        return "%02d:%02d".format(t.hour, t.minute)
    }

    fun dateLabel(millis: Long): String {
        val t = CourseSchedule.localDateTime(millis).toLocalDate()
        val today = java.time.LocalDate.now()
        return when (t) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> "%d年%d月%d日".format(t.year, t.monthValue, t.dayOfMonth)
        }
    }

    fun bytes(n: Long): String = when {
        n >= 1L shl 30 -> "%.2f GB".format(n.toDouble() / (1L shl 30))
        n >= 1L shl 20 -> "%.1f MB".format(n.toDouble() / (1L shl 20))
        n >= 1L shl 10 -> "%.1f KB".format(n.toDouble() / (1L shl 10))
        else -> "$n B"
    }
}
