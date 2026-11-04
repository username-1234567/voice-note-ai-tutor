package com.suitanglu.app.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.suitanglu.app.AppGraph
import com.suitanglu.app.R
import com.suitanglu.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * 上课提醒。
 *
 * 只排**一次**闹钟：到点弹通知，然后在接收器里重新排下一次。
 * 这样不用一次性塞 7 天 × 5 节的十几个 PendingIntent，
 * 课表改了下次触发自然就按新的来。
 */
object ReminderScheduler {

    const val CHANNEL_ID = "course_reminder"
    const val EXTRA_COURSE = "course"
    const val EXTRA_MINUTES = "minutes"
    /** 上课时间点（毫秒）。通知里的倒计时以它为准，不以"收到闹钟的那一刻"为准 */
    const val EXTRA_START_AT = "start_at"

    private const val REQUEST_CODE = 4201
    private val BEIJING: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun pending(context: Context, course: String, minutes: Int, startAt: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_COURSE, course)
            putExtra(EXTRA_MINUTES, minutes)
            putExtra(EXTRA_START_AT, startAt)
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 取消所有提醒（用户关掉开关时调用） */
    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(context, "", 0, 0L))
    }

    /** 重新计算并排下一次。App 启动、录完一条、改完设置都调它 */
    suspend fun reschedule(context: Context, settings: SettingsRepository) {
        val (enabled, lead) = settings.reminderConfigNow()
        cancel(context)
        if (!enabled) return
        val next = nextTrigger(settings, System.currentTimeMillis(), lead) ?: return
        schedule(context, next.first, next.second, lead)
    }

    private fun schedule(context: Context, triggerAt: Long, course: String, minutes: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // 上课时间点 = 闹钟时刻 + 提前量。通知里的倒计时按它算，
        // 这样即使闹钟被省电策略拖晚了几分钟，显示的仍是真实剩余时间
        val startAt = triggerAt + minutes * 60_000L
        val pi = pending(context, course, minutes, startAt)
        runCatching {
            // Android 12+ 精确闹钟要单独授权；没授权就退回非精确的
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }.onFailure {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    /**
     * 未来 8 天内最近的一次「课程开始 - 提前量」。
     * 往后看 8 天是为了跨过周末：周五配完，下周一那节课也得排上。
     */
    private suspend fun nextTrigger(
        settings: SettingsRepository,
        now: Long,
        leadMinutes: Int,
    ): Pair<Long, String>? {
        val today = Instant.ofEpochMilli(now).atZone(BEIJING)
        for (offset in 0..7) {
            val day = today.plusDays(offset.toLong())
            val weekday = day.dayOfWeek.value // 1=周一 … 7=周日
            val slots = runCatching { settings.slotsNow(weekday) }.getOrDefault(emptyList())
            for (slot in slots.sortedBy { it.startMinute }) {
                val startMs = day.toLocalDate()
                    .atStartOfDay(BEIJING)
                    .plusMinutes(slot.startMinute.toLong())
                    .toInstant()
                    .toEpochMilli()
                val trigger = startMs - leadMinutes * 60_000L
                // 这一格没配课程名就不提醒：课表是空的（或这节课没排）时，
                // 不该按默认时段冒出"下一节课"的通知
                val name = runCatching { settings.courseNameNow(weekday, slot.index) }
                    .getOrDefault("").trim()
                if (name.isEmpty()) continue
                // 至少留 30 秒余量，否则刚排上就已经过期，会立刻弹
                if (trigger > now + 30_000) {
                    return trigger to name
                }
            }
        }
        return null
    }

    internal fun notify(context: Context, course: String, startAt: Long) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            nm.getNotificationChannel(CHANNEL_ID) == null
        ) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "上课提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "按课程表在上课前提醒"
                },
            )
        }
        val tap = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val content = tap?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val now = System.currentTimeMillis()
        // 闹钟被省电策略拖晚时，"提前 N 分钟"已经不是真实剩余时间了，
        // 所以标题按上课时间点现算，倒计时也挂在同一个时间点上
        val counting = startAt > now
        val remainMin = if (counting) {
            ((startAt - now + 59_999) / 60_000).toInt().coerceAtLeast(1)
        } else {
            0
        }
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle(if (counting) "$remainMin 分钟后上课" else "该上课了")
            .setContentText(course)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(if (counting) startAt else now)
            // 时间区显示倒计时（API 24+）。自己走，不用每分钟重发通知
            .setUsesChronometer(counting)
            .setChronometerCountDown(counting)
            .setAutoCancel(true)
            .apply {
                // 归零后倒计时会翻成负数（"-00:05" 看着像坏了），上课一分钟后自动收走
                if (counting) setTimeoutAfter(startAt - now + 60_000)
            }
            .also { b -> content?.let { b.setContentIntent(it) } }
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }

    const val NOTIFICATION_ID = 2001
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val course = intent.getStringExtra(ReminderScheduler.EXTRA_COURSE).orEmpty()
        val minutes = intent.getIntExtra(ReminderScheduler.EXTRA_MINUTES, 10)
        val startAt = intent.getLongExtra(ReminderScheduler.EXTRA_START_AT, 0L)
        ReminderScheduler.notify(
            context,
            course.ifBlank { "下一节课" },
            startAt.takeIf { it > 0 } ?: (System.currentTimeMillis() + minutes * 60_000L),
        )

        // 通知发完立刻排下一次；用 goAsync 让进程多活一会儿
        val pending = goAsync()
        val container = AppGraph.get(context.applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { ReminderScheduler.reschedule(context, container.settings) }
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val container = AppGraph.get(context.applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { ReminderScheduler.reschedule(context, container.settings) }
            } finally {
                pending.finish()
            }
        }
    }
}
