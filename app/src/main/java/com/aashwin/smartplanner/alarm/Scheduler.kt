package com.aashwin.smartplanner.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aashwin.smartplanner.MainActivity
import com.aashwin.smartplanner.data.ReminderEntity
import com.aashwin.smartplanner.data.Status
import com.aashwin.smartplanner.data.TaskEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules alerts with AlarmManager. Nothing runs in the background between alerts:
 * the system wakes [AlertReceiver] at the right moment, even if the app is closed.
 */
object Scheduler {
    const val KIND_REMINDER = 0
    const val KIND_ALARM = 1
    const val KIND_SNOOZE = 2
    const val KIND_ROLLOVER = 3

    const val EXTRA_KIND = "kind"
    const val EXTRA_TASK = "taskId"
    const val EXTRA_REMINDER = "reminderId"

    private fun requestCode(kind: Int, id: Long): Int = (id * 4 + kind).toInt()

    private fun intent(ctx: Context, kind: Int, taskId: Long, reminderId: Long = 0): Intent =
        Intent(ctx, AlertReceiver::class.java)
            .setAction("com.aashwin.smartplanner.ALERT_$kind")
            .putExtra(EXTRA_KIND, kind)
            .putExtra(EXTRA_TASK, taskId)
            .putExtra(EXTRA_REMINDER, reminderId)

    private fun pending(ctx: Context, kind: Int, id: Long, taskId: Long, reminderId: Long = 0): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, requestCode(kind, id), intent(ctx, kind, taskId, reminderId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun cancelPending(ctx: Context, kind: Int, id: Long, taskId: Long) {
        val pi = PendingIntent.getBroadcast(
            ctx, requestCode(kind, id), intent(ctx, kind, taskId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        am(ctx).cancel(pi)
        pi.cancel()
    }

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am(ctx).canScheduleExactAlarms()

    private fun setExact(ctx: Context, at: Long, pi: PendingIntent) {
        val am = am(ctx)
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun setAlarmClock(ctx: Context, at: Long, pi: PendingIntent) {
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (canExact(ctx)) am(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
            else setExact(ctx, at, pi)
        } catch (e: SecurityException) {
            setExact(ctx, at, pi)
        }
    }

    /** Schedule every future alert of a pending task. Past alerts are skipped (no duplicates after reboot). */
    fun schedule(ctx: Context, task: TaskEntity, reminders: List<ReminderEntity>) {
        if (task.status != Status.PENDING) return
        val now = System.currentTimeMillis()
        val due = task.dueMillis()
        if (due != null && task.dueMinutes != null) {
            for (r in reminders) {
                val at = due - r.offsetMinutes * 60_000L
                if (at > now) setExact(ctx, at, pending(ctx, KIND_REMINDER, r.id, task.id, r.id))
            }
            if (task.alarmEnabled && due > now) setAlarmClock(ctx, due, pending(ctx, KIND_ALARM, task.id, task.id))
        }
        val snooze = task.snoozedUntil
        if (snooze != null && snooze > now) {
            val pi = pending(ctx, KIND_SNOOZE, task.id, task.id)
            if (task.alarmEnabled) setAlarmClock(ctx, snooze, pi) else setExact(ctx, snooze, pi)
        }
    }

    fun cancel(ctx: Context, task: TaskEntity, reminders: List<ReminderEntity>) {
        reminders.forEach { cancelPending(ctx, KIND_REMINDER, it.id, task.id) }
        cancelPending(ctx, KIND_ALARM, task.id, task.id)
        cancelPending(ctx, KIND_SNOOZE, task.id, task.id)
    }

    /** Daily roll-over of recurring tasks at the start of the user's day. */
    fun scheduleRollover(ctx: Context, startHour: Int) {
        val zone = ZoneId.systemDefault()
        var at = LocalDateTime.of(LocalDate.now(), LocalTime.of(startHour, 0, 30))
        if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
        val millis = at.atZone(zone).toInstant().toEpochMilli()
        setExact(ctx, millis, pending(ctx, KIND_ROLLOVER, 0, 0))
    }
}
