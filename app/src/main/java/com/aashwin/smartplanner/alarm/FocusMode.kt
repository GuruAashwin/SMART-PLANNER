package com.aashwin.smartplanner.alarm

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.accessibility.AccessibilityManager
import com.aashwin.smartplanner.data.Prefs

/** Do Not Disturb during focus, and checks for the app blocker. */
object FocusMode {
    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    fun dndAllowed(ctx: Context): Boolean = nm(ctx).isNotificationPolicyAccessGranted

    /** Turns on Do Not Disturb (priority only) and remembers what it was. */
    fun enter(ctx: Context, prefs: Prefs) {
        if (!prefs.focusDnd || !dndAllowed(ctx) || prefs.dndBefore != -1) return
        try {
            prefs.dndBefore = nm(ctx).currentInterruptionFilter
            nm(ctx).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        } catch (_: Exception) { prefs.dndBefore = -1 }
    }

    /** Puts Do Not Disturb back the way it was before focus. */
    fun exit(ctx: Context, prefs: Prefs) {
        val before = prefs.dndBefore
        if (before == -1) return
        prefs.dndBefore = -1
        if (!dndAllowed(ctx)) return
        try {
            nm(ctx).setInterruptionFilter(
                if (before == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) NotificationManager.INTERRUPTION_FILTER_ALL else before
            )
        } catch (_: Exception) { }
    }

    /** Is the Smart Planner accessibility service (the app blocker) switched on? */
    fun blockerOn(ctx: Context): Boolean {
        val am = ctx.getSystemService(AccessibilityManager::class.java) ?: return false
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.packageName == ctx.packageName }
    }
}

/** Short built-in sound + vibration for focus moments. Uses the alarm stream so Do Not Disturb doesn't mute it. */
object Chime {
    @Suppress("DEPRECATION")
    fun vibrator(ctx: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else ctx.getSystemService(Vibrator::class.java)

    fun play(ctx: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
            val r = RingtoneManager.getRingtone(ctx, uri) ?: return
            r.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            r.play()
        } catch (_: Exception) { }
        try {
            val v: Vibrator? = vibrator(ctx)
            @Suppress("DEPRECATION")
            v?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        } catch (_: Exception) { }
    }
}
