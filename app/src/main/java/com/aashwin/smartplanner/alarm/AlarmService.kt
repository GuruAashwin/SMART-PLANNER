package com.aashwin.smartplanner.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.aashwin.smartplanner.PlannerApp
import com.aashwin.smartplanner.data.TaskEntity

/**
 * Plays the alarm sound and vibration itself (like a clock app), instead of relying on a
 * notification sound that phones may mute or play only once. Stops on its own after
 * [TIMEOUT_MS] and leaves a "Missed alarm" notification, so it never rings forever.
 */
class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ringing: TaskEntity? = null
    private val timeout = Runnable {
        val t = ringing
        stopRinging()
        if (t != null) (application as PlannerApp).repository.alarmTimedOut(t)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRinging()
            return START_NOT_STICKY
        }
        val taskId = intent?.getLongExtra(EXTRA_TASK, 0L) ?: 0L
        if (taskId == 0L) { stopRinging(); return START_NOT_STICKY }
        val minutes = intent?.getIntExtra(EXTRA_MINUTES, -1) ?: -1
        val task = TaskEntity(
            id = taskId, title = intent?.getStringExtra(EXTRA_TITLE) ?: "Alarm",
            dueMinutes = if (minutes >= 0) minutes else null,
        )
        val prefs = (application as PlannerApp).repository.prefs

        // A second alarm while one is ringing: the first becomes a "missed alarm" notification.
        ringing?.let { prev -> if (prev.id != taskId) Notifier.showMissedAlarm(this, prev, prefs) }

        ServiceCompat.startForeground(
            this, Notifier.alarmNotificationId(taskId), Notifier.buildAlarm(this, task, prefs),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        ringing = task
        current = taskId
        startSound(prefs.alarmToneUri)
        startVibration()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, TIMEOUT_MS)
        return START_NOT_STICKY
    }

    private fun startSound(custom: String?) {
        player?.release()
        player = null
        val candidates = listOfNotNull(
            custom?.let { Uri.parse(it) },
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (uri in candidates) {
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@AlarmService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
                return
            } catch (_: Exception) {
                player?.release()
                player = null
            }
        }
    }

    private fun startVibration() {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator = v
        try { v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 700), 0)) } catch (_: Exception) { }
    }

    private fun stopRinging() {
        handler.removeCallbacks(timeout)
        try { player?.stop() } catch (_: Exception) { }
        player?.release()
        player = null
        vibrator?.cancel()
        vibrator = null
        ringing = null
        current = 0L
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        player?.release()
        vibrator?.cancel()
        current = 0L
        super.onDestroy()
    }

    companion object {
        const val TIMEOUT_MS = 10 * 60 * 1000L
        private const val ACTION_STOP = "com.aashwin.smartplanner.STOP_ALARM"
        private const val EXTRA_TASK = "taskId"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MINUTES = "minutes"

        /** Task currently ringing (0 = none). */
        @Volatile var current: Long = 0L
            private set

        /** Returns false if the phone refused to start it (caller then shows a fallback notification). */
        fun start(ctx: Context, task: TaskEntity): Boolean = try {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, AlarmService::class.java)
                    .putExtra(EXTRA_TASK, task.id)
                    .putExtra(EXTRA_TITLE, task.title)
                    .putExtra(EXTRA_MINUTES, task.dueMinutes ?: -1),
            )
            true
        } catch (_: Exception) {
            false
        }

        /** Stops the sound only if [taskId] is the alarm that is ringing. */
        fun stop(ctx: Context, taskId: Long) {
            if (taskId == 0L || current != taskId) return
            try {
                ctx.startService(Intent(ctx, AlarmService::class.java).setAction(ACTION_STOP))
            } catch (_: Exception) { }
        }
    }
}
