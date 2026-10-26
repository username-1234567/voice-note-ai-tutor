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

    private const val REQUEST_CODE = 4201
    private val BEIJING: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun pending(context: Context, course: String, minutes: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_COURSE, course)
            putExtra(EXTRA_MINUTES, minutes)
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 取消所有提醒（用户关掉开关时调用） */
    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(context, "", 0))
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
        val pi = pending(context, course, minutes)
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
                // 至少留 30 秒余量，否则刚排上就已经过期，会立刻弹
                if (trigger > now + 30_000) {
                    val name = runCatching { settings.courseNameNow(weekday, slot.index) }
                        .getOrDefault("").ifBlank { "下一节课" }
                    return trigger to name
                }
            }
        }
        return null
    }

    internal fun notify(context: Context, course: String, minutes: Int) {
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
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle("$minutes 分钟后上课")
            .setContentText(course)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
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
        ReminderScheduler.notify(context, course.ifBlank { "下一节课" }, minutes)

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
