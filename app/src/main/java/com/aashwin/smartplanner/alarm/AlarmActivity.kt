package com.aashwin.smartplanner.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.aashwin.smartplanner.MainActivity
import com.aashwin.smartplanner.PlannerApp
import com.aashwin.smartplanner.data.TaskFull
import com.aashwin.smartplanner.ui.PlannerTheme
import com.aashwin.smartplanner.util.Fmt
import com.aashwin.smartplanner.util.Quotes
import kotlinx.coroutines.launch

/** Full-screen "Time to start" over the lock screen: Start / Later / Skip. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val repo = (application as PlannerApp).repository
        val taskId = intent.getLongExtra(Scheduler.EXTRA_TASK, 0)
        val quote = Quotes.next(repo.prefs, Quotes.START)

        setContent {
            val theme by repo.prefs.theme.collectAsState()
            var full by remember { mutableStateOf<TaskFull?>(null) }
            LaunchedEffect(taskId) {
                full = repo.getTask(taskId)
                if (full == null) finish()
            }
            PlannerTheme(theme) {
                StartScreen(
                    full = full,
                    summary = full?.let { repo.startSummary(it) } ?: "",
                    quote = quote,
                    snooze = repo.prefs.defaultSnooze,
                    onStart = {
                        lifecycleScope.launch {
                            repo.startFocus(taskId, null)
                            startActivity(
                                Intent(this@AlarmActivity, MainActivity::class.java)
                                    .putExtra(MainActivity.EXTRA_OPEN_FOCUS, true)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            )
                            finish()
                        }
                    },
                    onLater = { m -> lifecycleScope.launch { repo.snooze(taskId, m, startAlert = true); finish() } },
                    onSkip = { lifecycleScope.launch { repo.skip(taskId); finish() } },
                )
            }
        }
    }
}

@Composable
private fun StartScreen(
    full: TaskFull?,
    summary: String,
    quote: String?,
    snooze: Int,
    onStart: () -> Unit,
    onLater: (Int) -> Unit,
    onSkip: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("TIME TO START", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text(full?.task?.title ?: "", fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(full?.task?.time?.let { Fmt.time(it) }, summary.takeIf { it.isNotBlank() }).joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            full?.sortedSubtasks?.filter { !it.done }?.take(5)?.let { steps ->
                if (steps.isNotEmpty()) Column(Modifier.padding(top = 16.dp)) {
                    steps.forEachIndexed { i, s ->
                        Text(
                            "${i + 1}. ${s.title}" + if (s.minutes > 0) " · ${s.minutes} min" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (quote != null) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "“$quote”", textAlign = TextAlign.Center, fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(36.dp))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(58.dp)) {
                Text("Start", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, snooze, 30).distinct().forEach { m ->
                    OutlinedButton(onClick = { onLater(m) }, modifier = Modifier.weight(1f)) { Text("Later $m min") }
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onSkip) { Text("Skip (move to next time)") }
        }
    }
}
