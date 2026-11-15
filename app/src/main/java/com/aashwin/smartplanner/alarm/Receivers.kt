package com.aashwin.smartplanner.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aashwin.smartplanner.PlannerApp
import com.aashwin.smartplanner.data.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try { block() } finally { pending.finish() }
    }
}

/** Fired by AlarmManager when a reminder, alarm, snooze or the daily roll-over is due. */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as PlannerApp
        val repo = app.repository
        val kind = intent.getIntExtra(Scheduler.EXTRA_KIND, -1)
        val taskId = intent.getLongExtra(Scheduler.EXTRA_TASK, 0)
        runAsync {
            if (kind == Scheduler.KIND_ROLLOVER) {
                repo.rollover()
                return@runAsync
            }
            val full = repo.getTask(taskId) ?: return@runAsync
            val task = full.task
            // Completed / deleted tasks never alert (protects against stale alarms).
            if (task.status != Status.PENDING) return@runAsync
            when (kind) {
                Scheduler.KIND_REMINDER -> {
                    val rid = intent.getLongExtra(Scheduler.EXTRA_REMINDER, 0)
                    if (full.reminders.any { it.id == rid }) Notifier.showReminder(context, task, repo.prefs)
                }
                Scheduler.KIND_ALARM -> Notifier.showAlarm(context, task, repo.prefs)
                Scheduler.KIND_SNOOZE -> {
                    repo.clearSnooze(task.id)
                    if (task.alarmEnabled) Notifier.showAlarm(context, task, repo.prefs)
                    else Notifier.showReminder(context, task, repo.prefs)
                }
            }
        }
    }
}

/** Buttons on notifications: Complete / Snooze / Dismiss. */
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
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.aashwin.smartplanner.COMPLETE"
        const val ACTION_SNOOZE = "com.aashwin.smartplanner.SNOOZE"
        const val ACTION_DISMISS = "com.aashwin.smartplanner.DISMISS"
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
