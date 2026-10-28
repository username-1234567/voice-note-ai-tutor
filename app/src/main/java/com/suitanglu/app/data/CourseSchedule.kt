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
     * 判定课程名：看录音区间覆盖了哪一节的时长最多，就归哪一节。
     *
     * 早先的两版各有一个极端：第一版"结束时刻落在另一节就判空"，拖堂五分钟整条变未命名；
     * 第二版"只看开始时刻、结束完全不看"，于是 09:59 开始（落在第 1、2 节的缝隙里）
     * 也判空。正确的是按 [startTime, endTime] 与每个时段的**重叠时长**取最大者——
     * 开始早了一分钟、拖堂、连录两节，都归到"这堂课主要在哪节"。
     */
    suspend fun resolveCourseName(
        startTime: Long,
        endTime: Long?,
        slotsOf: suspend (day: Int) -> List<DaySlotTime>,
        nameOf: suspend (day: Int, slotIndex: Int) -> String?,
    ): Pair<String?, Int?> {
        val day = dayOfWeek(startTime)
        val slots = slotsOf(day)
        val startMin = minutesOf(startTime)

        // 没有结束时间（开始录音那一刻）时，只能用开始时刻就近匹配：
        // 落在某节内直接取；落在缝隙里取"最近的一节"（离哪个更近归哪个）
        val effectiveEnd = endTime?.let { minutesOf(it) } ?: startMin

        val best = slots
            .filter { slot ->
                // 只要与 [startMin, effectiveEnd] 有重叠就算候选
                slot.startMinute < effectiveEnd && slot.endMinute > startMin
            }
            .maxByOrNull { slot ->
                val overlapStart = maxOf(slot.startMinute, startMin)
                val overlapEnd = minOf(slot.endMinute, effectiveEnd)
                overlapEnd - overlapStart
            }

        if (best == null) {
            // 完全没重叠（比如午休录一段、或结束时间也落在缝隙里）：
            // 退而求其次，按开始时刻就近归到最近的一节，尽量给个名字而不是"未命名"
            val nearest = slots.minByOrNull { kotlin.math.abs(it.startMinute - startMin) } ?: return null to null
            val name = nameOf(day, nearest.index)?.trim()?.takeIf { it.isNotEmpty() }
            return name to nearest.index
        }

        val name = nameOf(day, best.index)?.trim()?.takeIf { it.isNotEmpty() }
        return name to best.index
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
