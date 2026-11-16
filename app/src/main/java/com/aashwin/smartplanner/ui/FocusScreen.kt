package com.aashwin.smartplanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aashwin.smartplanner.data.Status
import com.aashwin.smartplanner.util.Fmt
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Focus (Pomodoro) timer: pick a task and a length, start, and the app tells you when time is up. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(vm: MainViewModel, initialTaskId: Long?, onClose: () -> Unit) {
    val running by vm.prefs.focus.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val sessions by vm.focusSessions.collectAsState()
    val pending = remember(tasks) { tasks.filter { it.task.status == Status.PENDING } }

    var taskId by remember { mutableStateOf(initialTaskId?.takeIf { it > 0 }) }
    var minutes by remember { mutableIntStateOf(vm.prefs.focusMinutes) }
    var pickTask by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Tick once a second while a session runs.
    LaunchedEffect(running) {
        while (running != null) {
            now = System.currentTimeMillis()
            if (now >= running!!.endAt) { vm.finishFocus(); break }
            delay(1000)
        }
    }

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val todays = sessions.filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == today }
    val weekMinutes = sessions.filter {
        !Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate().isBefore(today.minusDays(6))
    }.sumOf { it.minutes }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                val r = running
                val total = r?.let { (it.endAt - it.startAt).coerceAtLeast(1) } ?: (minutes * 60_000L)
                val left = r?.let { (it.endAt - now).coerceAtLeast(0) } ?: total
                Box(Modifier.padding(vertical = 24.dp).size(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { if (r == null) 1f else left.toFloat() / total },
                        modifier = Modifier.size(240.dp),
                        strokeWidth = 10.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val secs = left / 1000
                        Text("%d:%02d".format(secs / 60, secs % 60), fontSize = 52.sp, fontWeight = FontWeight.Light)
                        Text(
                            when {
                                r == null -> "Ready"
                                r.isBreak -> "Break"
                                else -> "Focusing"
                            },
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (running == null) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            TextButton(onClick = { pickTask = true }) {
                                Text(pending.firstOrNull { it.task.id == taskId }?.task?.title ?: "Choose a task (optional)", textAlign = TextAlign.Center)
                            }
                            DropdownMenu(expanded = pickTask, onDismissRequest = { pickTask = false }) {
                                DropdownMenuItem(text = { Text("No task") }, onClick = { taskId = null; pickTask = false })
                                pending.take(30).forEach { f ->
                                    DropdownMenuItem(text = { Text(f.task.title) }, onClick = { taskId = f.task.id; pickTask = false })
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                            listOf(15, 25, 45, 60).forEach { m ->
                                FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m min") })
                            }
                        }
                        Button(
                            onClick = {
                                val title = pending.firstOrNull { it.task.id == taskId }?.task?.title ?: ""
                                vm.startFocus(taskId, title, minutes, isBreak = false)
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text("Start focus", fontWeight = FontWeight.SemiBold) }
                        OutlinedButton(
                            onClick = { vm.startFocus(null, "", 5, isBreak = true) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
                        ) { Text("Take a 5-min break") }
                    }
                }
            } else {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (running!!.title.isNotBlank()) Text(running!!.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Text(
                            "Ends at " + Fmt.time(Instant.ofEpochMilli(running!!.endAt).atZone(zone).toLocalTime()) +
                                ". You can leave the app; it will alert you.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp),
                        )
                        OutlinedButton(onClick = { vm.stopFocus() }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Stop") }
                    }
                }
            }

            item {
                Column(Modifier.fillMaxWidth()) {
                    SectionTitle("History")
                    Text(
                        "Today: ${todays.size} session${if (todays.size == 1) "" else "s"} · ${todays.sumOf { it.minutes }} min" +
                            "   ·   Last 7 days: $weekMinutes min",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (sessions.isEmpty()) EmptyHint("Finished sessions appear here.")
                    Spacer(Modifier.height(8.dp))
                }
            }
            items(sessions.take(10), key = { it.id }) { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(s.title.ifBlank { "Focus" }, Modifier.weight(1f))
                    Text(
                        "${s.minutes} min · " + Fmt.day(Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate()),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
