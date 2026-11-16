package com.aashwin.smartplanner.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aashwin.smartplanner.PlannerApp
import com.aashwin.smartplanner.data.Status
import com.aashwin.smartplanner.data.TaskEntity
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
                Scheduler.KIND_FOCUS -> { repo.finishFocus(); return@runAsync }
            }
            val full = repo.getTask(taskId) ?: return@runAsync
            val task = full.task
            // Completed / deleted tasks never alert (protects against stale alarms).
            if (task.status != Status.PENDING) return@runAsync
            when (kind) {
                Scheduler.KIND_REMINDER -> {
                    val rid = intent.getLongExtra(Scheduler.EXTRA_REMINDER, 0)
                    val r = full.reminders.firstOrNull { it.id == rid }
                    // An "at due time" reminder is redundant when the alarm rings at that moment.
                    if (r != null && !(r.offsetMinutes == 0 && task.alarmEnabled)) {
                        Notifier.showReminder(context, task, repo.prefs)
                    }
                }
                Scheduler.KIND_ALARM -> ring(context, task, repo.prefs)
                Scheduler.KIND_SNOOZE -> {
                    repo.clearSnooze(task.id)
                    if (task.alarmEnabled) ring(context, task, repo.prefs)
                    else Notifier.showReminder(context, task, repo.prefs)
                }
            }
        }
    }

    private fun ring(ctx: Context, task: TaskEntity, prefs: com.aashwin.smartplanner.data.Prefs) {
        if (!AlarmService.start(ctx, task)) Notifier.showAlarmFallback(ctx, task, prefs)
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
                ACTION_SNOOZE -> repo.snooze(taskId, minutes)
                ACTION_DISMISS -> repo.dismissAlarm(taskId)
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
        }
    }
}
