package com.aashwin.smartplanner.blocker

import android.app.AppOpsManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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
 * notification showing). Every 0.7 s it asks Android which app is in front (Usage access) and,
 * if it is one you chose to block, draws "Back to work" on top of it (Display over other apps).
 * Drawing an overlay works even on phones that block apps from opening screens in the
 * background (Xiaomi, Realme, Vivo, Oppo). It never reads what is on the screen. Breaks are free.
 */
class BlockerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    /** App currently in front, as last reported by Android. */
    private var foreground: String? = null
    private var overlay: View? = null
    private var overlayPkg: String? = null
    private var overlayStatus: TextView? = null

    private val tick = object : Runnable {
        override fun run() {
            val prefs = (application as PlannerApp).repository.prefs
            val run = prefs.focus.value
            if (run == null) { hideOverlay(); stopSelf(); return }
            check(prefs, run)
            handler.postDelayed(this, 700)
        }
    }

    private fun check(prefs: com.aashwin.smartplanner.data.Prefs, run: com.aashwin.smartplanner.data.FocusRun) {
        val now = System.currentTimeMillis()
        val usm = getSystemService(UsageStatsManager::class.java)
        if (usm != null) try {
            val events = usm.queryEvents(now - 10_000, now)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) foreground = e.packageName
            }
        } catch (_: Exception) { }
        val pkg = foreground
        val block = run.phase != FocusMachine.BREAK && pkg != null && pkg != packageName &&
            pkg in prefs.blockedApps && now >= prefs.allowedUntil(pkg)
        if (block) showOverlay(pkg!!, prefs, run, now) else hideOverlay()
    }

    // ---- the "Back to work" screen, drawn on top of the blocked app ----

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun showOverlay(pkg: String, prefs: com.aashwin.smartplanner.data.Prefs, run: com.aashwin.smartplanner.data.FocusRun, now: Long) {
        val left = run.msLeft(now) / 1000
        val status = "Focusing on ${run.step.title}" + if (left > 0) " · %d:%02d left".format(left / 60, left % 60) else ""
        if (overlay != null && overlayPkg == pkg) { overlayStatus?.text = status; return }
        hideOverlay()
        if (!Settings.canDrawOverlays(this)) return
        val gold = 0xFFD4AF37.toInt()
        val appName = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { "This app" }
        val quote = Quotes.next(prefs, Quotes.BLOCKED)
        fun text(t: String, size: Float, color: Int, bold: Boolean = false, italic: Boolean = false) = TextView(this).apply {
            text = t; textSize = size; setTextColor(color); gravity = Gravity.CENTER
            setTypeface(typeface, when { bold -> Typeface.BOLD; italic -> Typeface.ITALIC; else -> Typeface.NORMAL })
            setPadding(0, dp(6), 0, dp(6))
        }
        val statusView = text(status, 15f, 0xFFB0B0B0.toInt())
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF000000.toInt())
            setPadding(dp(28), dp(28), dp(28), dp(28))
            isClickable = true // swallow touches so the blocked app can't be used underneath
            addView(text("FOCUS MODE", 13f, gold, bold = true))
            addView(text("$appName is blocked", 26f, 0xFFF2F2F2.toInt(), bold = true))
            addView(statusView)
            if (quote != null) addView(text("\u201C$quote\u201D", 19f, gold, italic = true).apply { setPadding(0, dp(28), 0, dp(28)) })
            addView(Button(this@BlockerService).apply {
                text = "Back to work"; textSize = 17f; setTextColor(0xFF000000.toInt()); isAllCaps = false
                background = GradientDrawable().apply { setColor(gold); cornerRadius = dp(28).toFloat() }
                setOnClickListener {
                    hideOverlay()
                    try {
                        startActivity(
                            Intent(this@BlockerService, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_OPEN_FOCUS, true)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                    } catch (_: Exception) { }
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))
            addView(text("Emergency: allow 1 minute", 14f, 0xFF9E9E9E.toInt()).apply {
                setPadding(0, dp(18), 0, dp(6))
                setOnClickListener { prefs.allowAppUntil(pkg, System.currentTimeMillis() + 60_000L); hideOverlay() }
            })
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        try {
            getSystemService(WindowManager::class.java).addView(root, params)
            overlay = root; overlayPkg = pkg; overlayStatus = statusView
        } catch (_: Exception) { }
    }

    private fun hideOverlay() {
        overlay?.let { try { getSystemService(WindowManager::class.java).removeView(it) } catch (_: Exception) { } }
        overlay = null; overlayPkg = null; overlayStatus = null
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
        hideOverlay()
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
