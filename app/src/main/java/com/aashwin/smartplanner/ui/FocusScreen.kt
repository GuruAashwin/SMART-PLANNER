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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Circle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aashwin.smartplanner.alarm.FocusMode
import com.aashwin.smartplanner.data.FocusMachine
import com.aashwin.smartplanner.data.Status
import com.aashwin.smartplanner.util.Fmt
import com.aashwin.smartplanner.util.Quotes
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Focus session: the task's steps one at a time, each with its own timer, a 5-minute break
 * between steps, and a motivation line. Idle: pick a task (or none) and start.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(vm: MainViewModel, initialTaskId: Long?, onClose: () -> Unit, onSetupBlocker: () -> Unit) {
    val run by vm.prefs.focus.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val sessions by vm.focusSessions.collectAsState()
    val ctx = LocalContext.current
    val pending = remember(tasks) { tasks.filter { it.task.status == Status.PENDING } }

    var taskId by remember { mutableStateOf(initialTaskId?.takeIf { it > 0 }) }
    var minutes by remember { mutableIntStateOf(vm.prefs.focusMinutes) }
    var pickTask by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // A fresh line for each step / phase.
    val quote = remember(run?.index, run?.phase, run == null) {
        Quotes.next(vm.prefs, if (run?.phase == FocusMachine.TIME_UP) Quotes.TIME_UP else Quotes.START)
    }

    // Tick every second; if the end alarm is late (phone asleep), catch up.
    LaunchedEffect(run) {
        while (run != null) {
            now = System.currentTimeMillis()
            val r = run!!
            if (r.phaseEnd in 1..(now - 1_500)) { vm.focusTick(); break }
            delay(1000)
        }
    }

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val todays = sessions.filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == today }
    val weekMinutes = sessions.filter {
        !Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate().isBefore(today.minusDays(6))
    }.sumOf { it.minutes }
    val blockerMissing = vm.prefs.blockedApps.isNotEmpty() && !FocusMode.blockerOn(ctx)

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
            val r = run
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Header: which step / break
                    Text(
                        when {
                            r == null -> "Ready"
                            r.phase == FocusMachine.BREAK -> "Break · next: ${r.next?.title ?: ""}"
                            r.steps.size > 1 -> "Step ${r.index + 1} of ${r.steps.size}"
                            else -> r.taskTitle
                        },
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    if (r != null && r.phase != FocusMachine.BREAK) Text(
                        r.step.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    // Ring timer
                    val total = when {
                        r == null -> minutes * 60_000L
                        r.phase == FocusMachine.BREAK -> FocusMachine.BREAK_MS
                        else -> r.step.minutes.coerceAtLeast(1) * 60_000L
                    }
                    val left = r?.msLeft(now) ?: total
                    Box(Modifier.padding(vertical = 20.dp).size(230.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { if (r == null) 1f else (left.toFloat() / total).coerceIn(0f, 1f) },
                            modifier = Modifier.size(230.dp),
                            strokeWidth = 10.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (r?.phase == FocusMachine.TIME_UP) {
                                Text("Time's up", fontSize = 34.sp, fontWeight = FontWeight.Light)
                                Text("+${(r.focusedMs(now) / 60_000) - r.step.minutes} min over", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                val secs = left / 1000
                                Text("%d:%02d".format(secs / 60, secs % 60), fontSize = 50.sp, fontWeight = FontWeight.Light)
                                Text(
                                    when {
                                        r == null -> "$minutes min"
                                        r.isPaused -> "Paused"
                                        r.phase == FocusMachine.BREAK -> "Breathe. Stand up."
                                        else -> "Focus"
                                    },
                                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    if (quote != null && r != null) Text(
                        "“$quote”", textAlign = TextAlign.Center, fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }

            // Controls for the moment
            item {
                Column(Modifier.fillMaxWidth()) {
                    when {
                        r == null -> {
                            Box(Modifier.align(Alignment.CenterHorizontally)) {
                                TextButton(onClick = { pickTask = true }) {
                                    Text(pending.firstOrNull { it.task.id == taskId }?.task?.title ?: "Choose a task (optional)")
                                }
                                DropdownMenu(expanded = pickTask, onDismissRequest = { pickTask = false }) {
                                    DropdownMenuItem(text = { Text("No task, just focus") }, onClick = { taskId = null; pickTask = false })
                                    pending.take(40).forEach { f ->
                                        DropdownMenuItem(text = { Text(f.task.title) }, onClick = { taskId = f.task.id; pickTask = false })
                                    }
                                }
                            }
                            val chosen = pending.firstOrNull { it.task.id == taskId }
                            val openSteps = chosen?.sortedSubtasks?.filter { !it.done }.orEmpty()
                            if (openSteps.isEmpty()) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                                    listOf(15, 25, 45, 60).forEach { m ->
                                        FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m min") })
                                    }
                                }
                            } else {
                                Text(
                                    "${openSteps.size} steps · ${openSteps.sumOf { if (it.minutes > 0) it.minutes else 25 }} min · 5-min breaks between",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp),
                                )
                            }
                            Button(
                                onClick = { vm.prefs.focusMinutes = minutes; vm.startFocus(taskId, if (openSteps.isEmpty()) minutes else null) },
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                            ) {
                                Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp))
                                Text("Start focus", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        r.phase == FocusMachine.TIME_UP -> {
                            Button(onClick = { vm.focusStepDone() }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                                Text(if (r.isLast) "Done · finish task" else "Done · take a break", fontWeight = FontWeight.SemiBold)
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { vm.focusExtend(5) }, modifier = Modifier.weight(1f)) { Text("+5 min") }
                                OutlinedButton(onClick = { vm.focusExtend(10) }, modifier = Modifier.weight(1f)) { Text("+10 min") }
                            }
                        }
                        r.phase == FocusMachine.BREAK -> {
                            Button(onClick = { vm.focusSkipBreak() }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                                Text("Skip break · start next step", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        else -> {
                            Button(onClick = { vm.focusStepDone() }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                                Text(if (r.isLast) "Done · finish task" else "Step done", fontWeight = FontWeight.SemiBold)
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { if (r.isPaused) vm.focusResume() else vm.focusPause() },
                                    modifier = Modifier.weight(1f),
                                ) { Text(if (r.isPaused) "Resume" else "Pause") }
                                OutlinedButton(onClick = { vm.focusExtend(5) }, modifier = Modifier.weight(1f)) { Text("+5 min") }
                            }
                        }
                    }
                    if (r != null) TextButton(onClick = { vm.stopFocus() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Stop session")
                    }
                    if (blockerMissing) TextButton(onClick = onSetupBlocker, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("App blocking is off. Set it up", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Steps of this session
            if (r != null && r.steps.size > 1) {
                item { Column(Modifier.fillMaxWidth()) { SectionTitle("Steps") } }
                items(r.steps.size) { i ->
                    val s = r.steps[i]
                    val done = i < r.index || (i == r.index && r.phase == FocusMachine.BREAK)
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (done) Icon(Icons.Filled.CheckCircle, "Done", tint = MaterialTheme.colorScheme.primary)
                        else Icon(
                            Icons.Outlined.Circle, null,
                            tint = if (i == r.index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            s.title, Modifier.weight(1f),
                            fontWeight = if (i == r.index && !done) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        Text("${s.minutes} min", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Column(Modifier.fillMaxWidth()) {
                    SectionTitle("History")
                    Text(
                        "Today: ${todays.sumOf { it.minutes }} min in ${todays.size} block${if (todays.size == 1) "" else "s"}" +
                            "   ·   Last 7 days: $weekMinutes min",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (sessions.isEmpty()) EmptyHint("Finished steps and sessions appear here.")
                    Spacer(Modifier.height(8.dp))
                }
            }
            items(sessions.take(12), key = { it.id }) { s ->
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
