package com.aashwin.smartplanner.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
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
 * The app blocker. Android tells it only which app came to the front (it never reads the screen).
 * Outside a focus session, and during breaks, it does nothing.
 */
class FocusBlockerService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val prefs = (application as PlannerApp).repository.prefs
        val run = prefs.focus.value ?: return
        if (run.phase == FocusMachine.BREAK) return
        if (pkg !in prefs.blockedApps) return
        if (System.currentTimeMillis() < prefs.allowedUntil(pkg)) return
        startActivity(
            Intent(this, BlockActivity::class.java)
                .putExtra(BlockActivity.EXTRA_PKG, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        )
    }

    override fun onInterrupt() {}
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
