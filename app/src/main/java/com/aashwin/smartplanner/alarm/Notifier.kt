package com.aashwin.smartplanner.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aashwin.smartplanner.MainActivity
import com.aashwin.smartplanner.R
import com.aashwin.smartplanner.data.Prefs
import com.aashwin.smartplanner.data.TaskEntity
import com.aashwin.smartplanner.util.Fmt

object Notifier {
    const val CH_REMINDERS = "reminders"
    private const val ALARM_PREFIX = "alarms_v"

    fun alarmChannelId(prefs: Prefs) = ALARM_PREFIX + prefs.alarmChannelVersion

    fun createChannels(ctx: Context, prefs: Prefs) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val reminders = NotificationChannel(CH_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Task reminders"
            enableVibration(true)
        }
        nm.createNotificationChannel(reminders)

        val id = alarmChannelId(prefs)
        if (nm.getNotificationChannel(id) == null) {
            // remove older alarm channels (sound can only be changed by making a new channel)
            nm.notificationChannels.filter { it.id.startsWith(ALARM_PREFIX) && it.id != id }
                .forEach { nm.deleteNotificationChannel(it.id) }
            val sound: Uri = prefs.alarmToneUri?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val alarms = NotificationChannel(id, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Strong alerts with sound for tasks that have an alarm"
                setSound(
                    sound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 600, 800, 600, 800)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(alarms)
        }
    }

    /** Call after the user picks a new alarm tone. */
    fun changeAlarmTone(ctx: Context, prefs: Prefs, uri: String?) {
        prefs.alarmToneUri = uri
        prefs.alarmChannelVersion = prefs.alarmChannelVersion + 1
        createChannels(ctx, prefs)
    }

    fun reminderNotificationId(taskId: Long) = (taskId * 2).toInt()
    fun alarmNotificationId(taskId: Long) = (taskId * 2 + 1).toInt()

    private fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context, taskId: Long): PendingIntent = PendingIntent.getActivity(
        ctx, (taskId * 8 + 5).toInt(),
        Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TASK, taskId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
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

    fun showReminder(ctx: Context, task: TaskEntity, prefs: Prefs) {
        if (!canPost(ctx)) return
        val snooze = prefs.defaultSnooze
        val n = NotificationCompat.Builder(ctx, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(dueText(task) + " · " + com.aashwin.smartplanner.data.Priority.label(task.priority) + " priority")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, task.id))
            .addAction(0, "Complete", action(ctx, ActionReceiver.ACTION_COMPLETE, task.id, slot = 0))
            .addAction(0, "Snooze $snooze min", action(ctx, ActionReceiver.ACTION_SNOOZE, task.id, snooze, slot = 1))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(reminderNotificationId(task.id), n)
        } catch (_: SecurityException) { }
    }

    fun showAlarm(ctx: Context, task: TaskEntity, prefs: Prefs) {
        if (!canPost(ctx)) return
        val fullScreen = PendingIntent.getActivity(
            ctx, (task.id * 8 + 6).toInt(),
            Intent(ctx, AlarmActivity::class.java)
                .putExtra(Scheduler.EXTRA_TASK, task.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snooze = prefs.defaultSnooze
        val n = NotificationCompat.Builder(ctx, alarmChannelId(prefs))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(dueText(task))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, "Snooze $snooze min", action(ctx, ActionReceiver.ACTION_SNOOZE, task.id, snooze, slot = 2))
            .addAction(0, "Complete", action(ctx, ActionReceiver.ACTION_COMPLETE, task.id, slot = 3))
            .addAction(0, "Dismiss", action(ctx, ActionReceiver.ACTION_DISMISS, task.id, slot = 4))
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT // keep ringing until handled
        try {
            NotificationManagerCompat.from(ctx).notify(alarmNotificationId(task.id), n)
        } catch (_: SecurityException) { }
    }

    fun cancel(ctx: Context, taskId: Long) {
        val nm = NotificationManagerCompat.from(ctx)
        nm.cancel(reminderNotificationId(taskId))
        nm.cancel(alarmNotificationId(taskId))
    }
}
