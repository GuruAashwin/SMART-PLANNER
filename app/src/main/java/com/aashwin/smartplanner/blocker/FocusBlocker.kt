package com.aashwin.smartplanner.blocker

import android.app.AppOpsManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.aashwin.smartplanner.alarm.Notifier
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aashwin.smartplanner.MainActivity
import com.aashwin.smartplanner.PlannerApp
import com.aashwin.smartplanner.data.FocusMachine
import com.aashwin.smartplanner.ui.PlannerTheme
import com.aashwin.smartplanner.util.Quotes
import kotlinx.coroutines.delay

/**
 * The app blocker. Runs only during a focus session the user started (with the focus
 * notification showing). Once a second it asks Android which app came to the front
 * (Usage access) and, if it is one you chose to block, shows "Back to work" over it
 * (Display over other apps). It never reads what is on the screen. Breaks are free time.
 */
class BlockerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastPkg: String? = null
    private var lastBlockAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            val prefs = (application as PlannerApp).repository.prefs
            val run = prefs.focus.value
            if (run == null) { stopSelf(); return }
            if (run.phase != FocusMachine.BREAK) check(prefs)
            handler.postDelayed(this, 1_000)
        }
    }

    private fun check(prefs: com.aashwin.smartplanner.data.Prefs) {
        val now = System.currentTimeMillis()
        val usm = getSystemService(UsageStatsManager::class.java) ?: return
        val events = try { usm.queryEvents(now - 5_000, now) } catch (_: Exception) { return }
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) lastPkg = e.packageName
        }
        val pkg = lastPkg ?: return
        if (pkg == packageName || pkg !in prefs.blockedApps) return
        if (now < prefs.allowedUntil(pkg) || now - lastBlockAt < 1_500) return
        lastBlockAt = now
        lastPkg = null // shown once per visit; reopening the app triggers it again
        try {
            startActivity(
                Intent(this, BlockActivity::class.java)
                    .putExtra(BlockActivity.EXTRA_PKG, pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        } catch (_: Exception) { }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repo = (application as PlannerApp).repository
        val run = repo.prefs.focus.value
        if (run == null) { stopSelf(); return START_NOT_STICKY }
        // Shares the focus notification, so there is still just one notification on screen.
        try {
            ServiceCompat.startForeground(
                this, Notifier.FOCUS_RUNNING_ID, Notifier.buildFocusRunning(this, run),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
            )
        } catch (_: Exception) { stopSelf(); return START_NOT_STICKY }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    companion object {
        /** Both permissions the blocker needs: see which app is open, and show the block screen over it. */
        fun ready(ctx: Context): Boolean = hasUsageAccess(ctx) && Settings.canDrawOverlays(ctx)

        fun hasUsageAccess(ctx: Context): Boolean {
            val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return false
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
            else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
            return mode == AppOpsManager.MODE_ALLOWED
        }

        /** Called when focus starts (app in the foreground). Does nothing if no apps are chosen or permissions are missing. */
        fun start(ctx: Context, prefs: com.aashwin.smartplanner.data.Prefs) {
            if (prefs.blockedApps.isEmpty() || !ready(ctx)) return
            try { ContextCompat.startForegroundService(ctx, Intent(ctx, BlockerService::class.java)) } catch (_: Exception) { }
        }

        fun stop(ctx: Context) {
            try { ctx.stopService(Intent(ctx, BlockerService::class.java)) } catch (_: Exception) { }
        }
    }
}

/** Shown instead of a blocked app while focusing: "Back to work", or a 1-minute emergency pass. */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = (application as PlannerApp).repository.prefs
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: ""
        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { "This app" }
        val quote = Quotes.next(prefs, Quotes.BLOCKED)

        fun backToWork() {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_FOCUS, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }
        // Back never returns to the blocked app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = backToWork()
        })

        setContent {
            val theme by prefs.theme.collectAsState()
            val run by prefs.focus.collectAsState()
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
            LaunchedEffect(run) { if (run == null || run?.phase == FocusMachine.BREAK) finish() }
            PlannerTheme(theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("FOCUS MODE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        Text("$appName is blocked", fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        run?.let { r ->
                            val left = r.msLeft(now) / 1000
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Focusing on ${r.step.title}" + if (left > 0) " · %d:%02d left".format(left / 60, left % 60) else "",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                            )
                        }
                        if (quote != null) {
                            Spacer(Modifier.height(28.dp))
                            Text(
                                "“$quote”", textAlign = TextAlign.Center, fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        Spacer(Modifier.height(40.dp))
                        Button(onClick = { backToWork() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Text("Back to work", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            prefs.allowAppUntil(pkg, System.currentTimeMillis() + 60_000L)
                            finish() // the app underneath shows again, for 1 minute
                        }) { Text("Emergency: allow 1 minute") }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_PKG = "pkg"
    }
}
