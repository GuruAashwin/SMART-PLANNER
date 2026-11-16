package com.aashwin.smartplanner.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aashwin.smartplanner.PlannerApp
import com.aashwin.smartplanner.data.Priority
import com.aashwin.smartplanner.data.Status
import com.aashwin.smartplanner.data.TaskFull
import com.aashwin.smartplanner.util.Quotes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try { block() } finally { pending.finish() }
    }
}

/** Fired by AlarmManager when a reminder, alarm, snooze, summary, habit reminder or the daily roll-over is due. */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = (context.applicationContext as PlannerApp).repository
        val kind = intent.getIntExtra(Scheduler.EXTRA_KIND, -1)
        val taskId = intent.getLongExtra(Scheduler.EXTRA_TASK, 0)
        runAsync {
            when (kind) {
                Scheduler.KIND_ROLLOVER -> { repo.rollover(); return@runAsync }
                Scheduler.KIND_SUMMARY -> { repo.showSummary(); return@runAsync }
                Scheduler.KIND_HABIT -> { repo.habitAlert(intent.getLongExtra(Scheduler.EXTRA_HABIT, 0)); return@runAsync }
                Scheduler.KIND_FOCUS -> { repo.focusTimerEnded(); return@runAsync }
            }
            val full = repo.getTask(taskId) ?: return@runAsync
            val task = full.task
            // Completed / deleted tasks never alert (protects against stale alarms).
            if (task.status != Status.PENDING) return@runAsync
            when (kind) {
                Scheduler.KIND_REMINDER -> {
                    val rid = intent.getLongExtra(Scheduler.EXTRA_REMINDER, 0)
                    val r = full.reminders.firstOrNull { it.id == rid }
                    // A reminder at exactly the start time is redundant: the start alert covers it.
                    val atStart = r != null && r.offsetMinutes == 0 && task.startAlert &&
                        Scheduler.reminderBase(task) == task.dueMillis()
                    if (r != null && !atStart) Notifier.showReminder(context, task, repo.prefs)
                }
                Scheduler.KIND_ALARM -> ring(context, repo, full)
                Scheduler.KIND_SNOOZE -> {
                    repo.clearSnooze(task.id)
                    ring(context, repo, full)
                }
                Scheduler.KIND_REMIND_SNOOZE -> Notifier.showReminder(context, task, repo.prefs)
            }
        }
    }

    /** "Time to start": full screen for High priority, a heads-up notification otherwise; your sound either way. */
    private fun ring(ctx: Context, repo: com.aashwin.smartplanner.data.Repository, full: TaskFull) {
        if (repo.prefs.focus.value?.taskId == full.task.id) return // already focusing on it
        val quote = Quotes.next(repo.prefs, Quotes.START)
        val high = full.task.priority == Priority.HIGH
        if (!AlarmService.start(ctx, full.task, repo.startSummary(full), quote, high)) {
            Notifier.showStartFallback(ctx, full.task, repo.prefs)
        }
    }
}

/** Buttons on notifications: Complete / Snooze / Dismiss / habit Done. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = (context.applicationContext as PlannerApp).repository
        val taskId = intent.getLongExtra(Scheduler.EXTRA_TASK, 0)
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 10)
        runAsync {
            when (intent.action) {
                ACTION_COMPLETE -> repo.completeTask(taskId, completeSubtasks = true)
                ACTION_SNOOZE -> repo.snooze(taskId, minutes, intent.getBooleanExtra(EXTRA_START_ALERT, true))
                ACTION_SKIP -> repo.skip(taskId)
                ACTION_DISMISS -> repo.dismissAlarm(taskId)
                ACTION_FOCUS_PAUSE -> repo.focusPause()
                ACTION_FOCUS_RESUME -> repo.focusResume()
                ACTION_FOCUS_DONE -> repo.focusStepDone()
                ACTION_FOCUS_EXTEND -> repo.focusExtend(minutes)
                ACTION_FOCUS_SKIP_BREAK -> repo.focusSkipBreak()
                ACTION_HABIT_DONE -> repo.setHabitDone(intent.getLongExtra(Scheduler.EXTRA_HABIT, 0), repo.logicalToday(), true)
                ACTION_FOCUS_STOP -> repo.stopFocus()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.aashwin.smartplanner.COMPLETE"
        const val ACTION_SNOOZE = "com.aashwin.smartplanner.SNOOZE"
        const val ACTION_DISMISS = "com.aashwin.smartplanner.DISMISS"
        const val ACTION_HABIT_DONE = "com.aashwin.smartplanner.HABIT_DONE"
        const val ACTION_FOCUS_STOP = "com.aashwin.smartplanner.FOCUS_STOP"
        const val ACTION_SKIP = "com.aashwin.smartplanner.SKIP"
        const val ACTION_FOCUS_PAUSE = "com.aashwin.smartplanner.FOCUS_PAUSE"
        const val ACTION_FOCUS_RESUME = "com.aashwin.smartplanner.FOCUS_RESUME"
        const val ACTION_FOCUS_DONE = "com.aashwin.smartplanner.FOCUS_DONE"
        const val ACTION_FOCUS_EXTEND = "com.aashwin.smartplanner.FOCUS_EXTEND"
        const val ACTION_FOCUS_SKIP_BREAK = "com.aashwin.smartplanner.FOCUS_SKIP_BREAK"
        const val EXTRA_START_ALERT = "startAlert"
        const val EXTRA_MINUTES = "minutes"
    }
}

/** Restores alarms after reboot, app update, time or time-zone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = (context.applicationContext as PlannerApp).repository
        runAsync {
            repo.rollover()
            repo.rescheduleAll()
            repo.restoreFocus()
        }
    }
}
