package com.aashwin.smartplanner.alarm

import android.app.KeyguardManager
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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.aashwin.smartplanner.PlannerApp
import com.aashwin.smartplanner.data.TaskEntity
import com.aashwin.smartplanner.ui.PlannerTheme
import com.aashwin.smartplanner.util.Fmt
import kotlinx.coroutines.launch

/** Full-screen alarm shown over the lock screen: Snooze / Complete / Dismiss. */
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

        setContent {
            val theme by repo.prefs.theme.collectAsState()
            var task by remember { mutableStateOf<TaskEntity?>(null) }
            LaunchedEffect(taskId) {
                task = repo.getTask(taskId)?.task
                if (task == null) finish()
            }
            PlannerTheme(theme) {
                AlarmScreen(
                    task = task,
                    defaultSnooze = repo.prefs.defaultSnooze,
                    onSnooze = { m -> lifecycleScope.launch { repo.snooze(taskId, m); finish() } },
                    onComplete = { lifecycleScope.launch { repo.completeTask(taskId, true); finish() } },
                    onDismiss = { lifecycleScope.launch { repo.dismissAlarm(taskId); finish() } },
                )
            }
        }
    }
}

@Composable
private fun AlarmScreen(
    task: TaskEntity?,
    defaultSnooze: Int,
    onSnooze: (Int) -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Alarm, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(
                task?.title ?: "",
                fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                task?.time?.let { Fmt.time(it) } ?: "",
                fontSize = 22.sp, color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(40.dp))
            Button(onClick = { onSnooze(defaultSnooze) }, Modifier.fillMaxWidth().height(56.dp)) {
                Text("Snooze $defaultSnooze min", fontSize = 18.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 15, 30, 60).filter { it != defaultSnooze }.take(3).forEach { m ->
                    OutlinedButton(onClick = { onSnooze(m) }, Modifier.weight(1f)) {
                        Text(if (m == 60) "1 hr" else "$m min")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onComplete, Modifier.fillMaxWidth().height(56.dp)) {
                Text("Complete", fontSize = 18.sp)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onDismiss, Modifier.fillMaxWidth().height(56.dp)) {
                Text("Dismiss", fontSize = 18.sp)
            }
        }
    }
}
