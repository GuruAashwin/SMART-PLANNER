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
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.aashwin.smartplanner.PlannerApp
import com.aashwin.smartplanner.data.TaskEntity
import java.io.File

/**
 * Plays the "Time to start" sound (your own file or a phone sound) like a clock app does:
 * it loops until you answer, starts soft and gets louder, and stops by itself after the
 * ring limit, leaving a "Missed start" notification. Only runs while it is ringing.
 */
class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ringing: TaskEntity? = null
    private var fadeStep = 0

    private val timeout = Runnable {
        val t = ringing
        stopRinging()
        if (t != null) (application as PlannerApp).repository.startAlertTimedOut(t)
    }

    /** Volume rises from 15% to 100% over about 15 seconds. */
    private val fade = object : Runnable {
        override fun run() {
            fadeStep++
            val v = (0.15f + 0.85f * fadeStep / 15f).coerceAtMost(1f)
            try { player?.setVolume(v, v) } catch (_: Exception) { }
            if (v < 1f) handler.postDelayed(this, 1_000)
        }
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
            id = taskId, title = intent?.getStringExtra(EXTRA_TITLE) ?: "Task",
            dueMinutes = if (minutes >= 0) minutes else null,
        )
        val subtitle = intent?.getStringExtra(EXTRA_SUBTITLE) ?: ""
        val quote = intent?.getStringExtra(EXTRA_QUOTE)
        val fullScreen = intent?.getBooleanExtra(EXTRA_FULL, false) ?: false
        val prefs = (application as PlannerApp).repository.prefs

        // A second alert while one is ringing: the first becomes a "missed start" notification.
        ringing?.let { prev -> if (prev.id != taskId) Notifier.showMissedStart(this, prev, prefs) }

        ServiceCompat.startForeground(
            this, Notifier.alarmNotificationId(taskId),
            Notifier.buildStartAlert(this, task, subtitle, quote, fullScreen, prefs),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        ringing = task
        current = taskId
        startSound(prefs.startSound, prefs.fadeIn)
        startVibration()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, prefs.ringMinutes.coerceIn(1, 30) * 60_000L)
        return START_NOT_STICKY
    }

    private fun startSound(choice: String?, fadeIn: Boolean) {
        handler.removeCallbacks(fade)
        player?.release()
        player = null
        val candidates = listOfNotNull(
            choice?.let { soundUri(it) },
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
                    isLooping = true // a short clip repeats; a long song plays through and repeats
                    prepare()
                    if (fadeIn) setVolume(0.15f, 0.15f)
                    start()
                }
                if (fadeIn) { fadeStep = 0; handler.postDelayed(fade, 1_000) }
                return
            } catch (_: Exception) {
                player?.release()
                player = null
            }
        }
    }

    private fun startVibration() {
        val v = Chime.vibrator(this)
        vibrator = v
        try { v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 700), 0)) } catch (_: Exception) { }
    }

    private fun stopRinging() {
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(fade)
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
        handler.removeCallbacks(fade)
        player?.release()
        vibrator?.cancel()
        current = 0L
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "com.aashwin.smartplanner.STOP_ALARM"
        private const val EXTRA_TASK = "taskId"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MINUTES = "minutes"
        private const val EXTRA_SUBTITLE = "subtitle"
        private const val EXTRA_QUOTE = "quote"
        private const val EXTRA_FULL = "fullScreen"

        /** Task whose start alert is ringing (0 = none). */
        @Volatile var current: Long = 0L
            private set

        /** "file:/path" = your own sound copied into the app; anything else = a phone sound URI. */
        fun soundUri(choice: String): Uri =
            if (choice.startsWith("file:")) Uri.fromFile(File(choice.removePrefix("file:"))) else Uri.parse(choice)

        /** Returns false if the phone refused to start it (caller then shows a fallback notification). */
        fun start(ctx: Context, task: TaskEntity, subtitle: String, quote: String?, fullScreen: Boolean): Boolean = try {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, AlarmService::class.java)
                    .putExtra(EXTRA_TASK, task.id)
                    .putExtra(EXTRA_TITLE, task.title)
                    .putExtra(EXTRA_MINUTES, task.dueMinutes ?: -1)
                    .putExtra(EXTRA_SUBTITLE, subtitle)
                    .putExtra(EXTRA_QUOTE, quote)
                    .putExtra(EXTRA_FULL, fullScreen),
            )
            true
        } catch (_: Exception) {
            false
        }

        /** Stops the sound only if [taskId] is the one ringing. */
        fun stop(ctx: Context, taskId: Long) {
            if (taskId == 0L || current != taskId) return
            try {
                ctx.startService(Intent(ctx, AlarmService::class.java).setAction(ACTION_STOP))
            } catch (_: Exception) { }
        }
    }
}
