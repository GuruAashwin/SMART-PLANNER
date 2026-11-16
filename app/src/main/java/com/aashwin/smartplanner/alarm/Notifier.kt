package com.aashwin.smartplanner.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aashwin.smartplanner.MainActivity
import com.aashwin.smartplanner.R
import com.aashwin.smartplanner.data.FocusMachine
import com.aashwin.smartplanner.data.FocusRun
import com.aashwin.smartplanner.data.HabitEntity
import com.aashwin.smartplanner.data.Prefs
import com.aashwin.smartplanner.data.TaskEntity
import com.aashwin.smartplanner.util.Fmt

object Notifier {
    const val CH_REMINDERS = "reminders"
    /** Alarm notifications are silent: [AlarmService] plays the sound itself, which is far more reliable. */
    const val CH_ALARMS = "alarms_live"
    const val CH_DAILY = "daily"
    const val CH_FOCUS = "focus"
    const val CH_FOCUS_ALERT = "focus_alerts"
    private const val FOCUS_RUNNING_ID = 999_995
    private const val FOCUS_DONE_ID = 999_997
    private const val OLD_ALARM_PREFIX = "alarms_v"
    private val GOLD = 0xFFD4AF37.toInt()

    private const val SUMMARY_ID = 999_999
    private fun habitNotificationId(habitId: Long) = 1_000_000 + habitId.toInt()

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.notificationChannels.filter { it.id.startsWith(OLD_ALARM_PREFIX) }.forEach { nm.deleteNotificationChannel(it.id) }
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Task reminders and habit reminders"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARMS, "Start alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Time to start alerts. The sound is played by the app, so this channel is silent."
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_FOCUS_ALERT, "Focus moments", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Time's up, break over, step done. The app plays a short chime itself."
                setSound(null, null)
                enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_FOCUS, "Focus timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Countdown while a focus session runs"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DAILY, "Daily summary", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Morning list of today's tasks"
            }
        )
    }

    fun reminderNotificationId(taskId: Long) = (taskId * 2).toInt()
    fun alarmNotificationId(taskId: Long) = (taskId * 2 + 1).toInt()

    private fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun post(ctx: Context, id: Int, n: Notification) {
        if (!canPost(ctx)) return
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) { }
    }

    private fun openApp(ctx: Context, taskId: Long): PendingIntent = PendingIntent.getActivity(
        ctx, (taskId * 8 + 5).toInt(),
        Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TASK, taskId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun openHome(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 7, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun action(ctx: Context, action: String, taskId: Long, minutes: Int = 0, slot: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, (taskId * 8 + slot).toInt(),
            Intent(ctx, ActionReceiver::class.java)
                .setAction(action)
                .putExtra(Scheduler.EXTRA_TASK, taskId)
                .putExtra(ActionReceiver.EXTRA_MINUTES, minutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun dueText(task: TaskEntity): String {
        val t = task.time
        return if (t != null) "Due ${Fmt.time(t)}" else "Due today"
    }

    // ---------------- reminders and start alerts ----------------

    /** Opens the app and starts focus on the task. */
    private fun startFocusIntent(ctx: Context, taskId: Long): PendingIntent = PendingIntent.getActivity(
        ctx, (taskId * 8 + 7).toInt(),
        Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_START_FOCUS, taskId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun later(ctx: Context, taskId: Long, prefs: Prefs, startAlert: Boolean, slot: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, (taskId * 8 + slot).toInt(),
            Intent(ctx, ActionReceiver::class.java)
                .setAction(ActionReceiver.ACTION_SNOOZE)
                .putExtra(Scheduler.EXTRA_TASK, taskId)
                .putExtra(ActionReceiver.EXTRA_MINUTES, prefs.defaultSnooze)
                .putExtra(ActionReceiver.EXTRA_START_ALERT, startAlert),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Reminder before the start or the deadline: Start focus, or Later. */
    fun showReminder(ctx: Context, task: TaskEntity, prefs: Prefs, text: String = reminderText(task)) {
        val n = NotificationCompat.Builder(ctx, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(GOLD)
            .setContentTitle(task.title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, task.id))
            .addAction(0, "Start", startFocusIntent(ctx, task.id))
            .addAction(0, "Later (${prefs.defaultSnooze} min)", later(ctx, task.id, prefs, startAlert = false, slot = 1))
            .build()
        post(ctx, reminderNotificationId(task.id), n)
    }

    private fun reminderText(task: TaskEntity): String {
        val start = task.time?.let { "Starts ${Fmt.time(it)}" }
        val due = if (task.deadlineDate != null) "Due " + Fmt.due(task.deadline, task.deadlineMinutes) else null
        return listOfNotNull(start, due).joinToString(" · ").ifEmpty { "Today" }
    }

    /** Start alert nobody answered: stays in the shade so the task isn't forgotten. */
    fun showMissedStart(ctx: Context, task: TaskEntity, prefs: Prefs) =
        showReminder(ctx, task, prefs, "Missed start · " + reminderText(task))

    /**
     * "Time to start" alert. Posted by [AlarmService] as its foreground notification while the sound plays.
     * Full screen over the lock screen for High priority, a heads-up notification otherwise.
     */
    fun buildStartAlert(ctx: Context, task: TaskEntity, subtitle: String, quote: String?, fullScreen: Boolean, prefs: Prefs): Notification {
        val screen = PendingIntent.getActivity(
            ctx, (task.id * 8 + 6).toInt(),
            Intent(ctx, AlarmActivity::class.java)
                .putExtra(Scheduler.EXTRA_TASK, task.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(ctx, CH_ALARMS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(GOLD)
            .setContentTitle("Time to start: ${task.title}")
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle + (quote?.let { "\n\n$it" } ?: "")))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(if (fullScreen) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(screen)
            .addAction(0, "Start", startFocusIntent(ctx, task.id))
            .addAction(0, "Later (${prefs.defaultSnooze} min)", later(ctx, task.id, prefs, startAlert = true, slot = 2))
            .addAction(0, "Skip", action(ctx, ActionReceiver.ACTION_SKIP, task.id, slot = 4))
        if (fullScreen) b.setFullScreenIntent(screen, true)
        return b.build()
    }

    /** Used only if the phone refuses to start the sound service: a loud, repeating notification instead. */
    fun showStartFallback(ctx: Context, task: TaskEntity, prefs: Prefs) {
        val n = NotificationCompat.Builder(ctx, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(GOLD)
            .setContentTitle("Time to start: ${task.title}")
            .setContentText(reminderText(task))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(openApp(ctx, task.id))
            .addAction(0, "Start", startFocusIntent(ctx, task.id))
            .addAction(0, "Later (${prefs.defaultSnooze} min)", later(ctx, task.id, prefs, startAlert = true, slot = 2))
            .addAction(0, "Skip", action(ctx, ActionReceiver.ACTION_SKIP, task.id, slot = 4))
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        post(ctx, alarmNotificationId(task.id), n)
    }

    // ---------------- focus session ----------------

    private fun focusAction(ctx: Context, action: String, code: Int, minutes: Int = 0): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 20 + code,
            Intent(ctx, ActionReceiver::class.java).setAction(action).putExtra(ActionReceiver.EXTRA_MINUTES, minutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun clock(ms: Long) = Fmt.time(java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalTime())

    /** Quiet, always-visible notification while focusing: step, time left, and the right buttons for the moment. */
    fun showFocusRunning(ctx: Context, r: FocusRun) {
        val now = System.currentTimeMillis()
        val stepLabel = if (r.steps.size > 1) "Step ${r.index + 1}/${r.steps.size} · ${r.step.title}" else r.step.title
        val b = NotificationCompat.Builder(ctx, CH_FOCUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(GOLD)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openFocus(ctx))
        when {
            r.phase == FocusMachine.BREAK -> {
                b.setContentTitle("Break · next: ${r.next?.title ?: r.step.title}")
                    .setContentText("Back at ${clock(r.phaseEnd)}")
                    .setUsesChronometer(true).setChronometerCountDown(true).setWhen(r.phaseEnd).setShowWhen(true)
                    .addAction(0, "Skip break", focusAction(ctx, ActionReceiver.ACTION_FOCUS_SKIP_BREAK, 1))
                    .addAction(0, "Stop", focusAction(ctx, ActionReceiver.ACTION_FOCUS_STOP, 2))
            }
            r.phase == FocusMachine.TIME_UP -> {
                b.setContentTitle("Time's up · $stepLabel")
                    .setContentText("Done, or need more time?")
                    .addAction(0, "Done", focusAction(ctx, ActionReceiver.ACTION_FOCUS_DONE, 3))
                    .addAction(0, "+5 min", focusAction(ctx, ActionReceiver.ACTION_FOCUS_EXTEND, 4, 5))
                    .addAction(0, "+10 min", focusAction(ctx, ActionReceiver.ACTION_FOCUS_EXTEND, 5, 10))
            }
            r.isPaused -> {
                b.setContentTitle("Paused · $stepLabel")
                    .setContentText("${(r.msLeft(now) + 59_999) / 60_000} min left")
                    .addAction(0, "Resume", focusAction(ctx, ActionReceiver.ACTION_FOCUS_RESUME, 6))
                    .addAction(0, "Stop", focusAction(ctx, ActionReceiver.ACTION_FOCUS_STOP, 2))
            }
            else -> {
                b.setContentTitle(stepLabel)
                    .setContentText("Focus · ends ${clock(r.phaseEnd)}")
                    .setUsesChronometer(true).setChronometerCountDown(true).setWhen(r.phaseEnd).setShowWhen(true)
                    .addAction(0, "Pause", focusAction(ctx, ActionReceiver.ACTION_FOCUS_PAUSE, 7))
                    .addAction(0, "Step done", focusAction(ctx, ActionReceiver.ACTION_FOCUS_DONE, 3))
                    .addAction(0, "Stop", focusAction(ctx, ActionReceiver.ACTION_FOCUS_STOP, 2))
            }
        }
        post(ctx, FOCUS_RUNNING_ID, b.build())
    }

    /** A focus moment (time up, break over, step or task done): chime + vibration, with a motivation line. */
    fun focusAlert(ctx: Context, title: String, text: String, quote: String?, timeUpActions: Boolean, chime: Boolean = true) {
        val b = NotificationCompat.Builder(ctx, CH_FOCUS_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(GOLD)
            .setContentTitle(title)
            .setContentText(quote ?: text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text + (quote?.let { "\n\n$it" } ?: "")))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openFocus(ctx))
        if (timeUpActions) {
            b.addAction(0, "Done", focusAction(ctx, ActionReceiver.ACTION_FOCUS_DONE, 3))
                .addAction(0, "+5 min", focusAction(ctx, ActionReceiver.ACTION_FOCUS_EXTEND, 4, 5))
                .addAction(0, "+10 min", focusAction(ctx, ActionReceiver.ACTION_FOCUS_EXTEND, 5, 10))
        }
        post(ctx, FOCUS_DONE_ID, b.build())
        if (chime) Chime.play(ctx)
    }

    fun cancelFocusAlert(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(FOCUS_DONE_ID)

    fun showSummary(ctx: Context, today: List<TaskEntity>, overdue: Int) {
        val title = when {
            today.isEmpty() -> "$overdue overdue task${if (overdue > 1) "s" else ""}"
            else -> "Today: ${today.size} task${if (today.size > 1) "s" else ""}" + if (overdue > 0) " · $overdue overdue" else ""
        }
        val lines = today.map { t -> (t.dueMinutes?.let { Fmt.time(it) + "  " } ?: "") + t.title }
        val style = NotificationCompat.InboxStyle()
        lines.take(6).forEach { style.addLine(it) }
        if (lines.size > 6) style.setSummaryText("+${lines.size - 6} more")
        val n = NotificationCompat.Builder(ctx, CH_DAILY)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(GOLD)
            .setContentTitle(title)
            .setContentText(lines.take(3).joinToString(", ").ifEmpty { "Open the app to catch up" })
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(openHome(ctx))
            .build()
        post(ctx, SUMMARY_ID, n)
    }

    fun showHabit(ctx: Context, habit: HabitEntity) {
        val done = PendingIntent.getBroadcast(
            ctx, habitNotificationId(habit.id),
            Intent(ctx, ActionReceiver::class.java)
                .setAction(ActionReceiver.ACTION_HABIT_DONE)
                .putExtra(Scheduler.EXTRA_HABIT, habit.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(GOLD)
            .setContentTitle(habit.name)
            .setContentText("Habit · not done yet today")
            .setAutoCancel(true)
            .setContentIntent(openHome(ctx))
            .addAction(0, "Done", done)
            .build()
        post(ctx, habitNotificationId(habit.id), n)
    }

    private fun openFocus(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 9,
        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_FOCUS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun cancelFocusRunning(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(FOCUS_RUNNING_ID)

    fun cancelHabit(ctx: Context, habitId: Long) = NotificationManagerCompat.from(ctx).cancel(habitNotificationId(habitId))

    /** Removes a task's notifications and stops its alarm sound if it is the one ringing. */
    fun cancel(ctx: Context, taskId: Long) {
        val nm = NotificationManagerCompat.from(ctx)
        nm.cancel(reminderNotificationId(taskId))
        nm.cancel(alarmNotificationId(taskId))
        AlarmService.stop(ctx, taskId)
    }
}
