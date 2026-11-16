package com.aashwin.smartplanner.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aashwin.smartplanner.alarm.Reliability
import com.aashwin.smartplanner.data.Status
import com.aashwin.smartplanner.data.TaskFull
import com.aashwin.smartplanner.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private class HomeData(
    val todays: List<TaskFull>,
    val doneToday: List<TaskFull>,
    val overdue: List<TaskFull>,
    val todayPending: List<TaskFull>,
    val next: TaskFull?,
)

private fun buildHome(tasks: List<TaskFull>, today: LocalDate, now: Long): HomeData {
    val zone = ZoneId.systemDefault()
    val todayDay = today.toEpochDay()
    fun completedToday(f: TaskFull) =
        f.task.completedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today } == true
    // Today = due today, plus anything finished today (so finishing an old task counts as progress).
    val todays = tasks.filter {
        (it.task.status == Status.PENDING && it.task.dueDate == todayDay) ||
            (it.task.status == Status.DONE && (it.task.dueDate == todayDay || completedToday(it)))
    }
    return HomeData(
        todays = todays,
        doneToday = todays.filter { it.task.status == Status.DONE },
        overdue = tasks.filter { it.task.isOverdue(now) }
            .sortedWith(compareByDescending<TaskFull> { it.task.priority }.thenBy { it.task.dueMillis() }),
        todayPending = todays.filter { it.task.status == Status.PENDING && !it.task.isOverdue(now) },
        next = tasks.filter { it.task.status == Status.PENDING && it.task.dueMinutes != null && (it.task.dueMillis() ?: 0) >= now }
            .minByOrNull { it.task.dueMillis() ?: Long.MAX_VALUE },
    )
}

@Composable
fun HomeScreen(vm: MainViewModel, onOpen: (Long) -> Unit, onOpenSettings: () -> Unit, onFocus: (Long) -> Unit = {}) {
    val tasks by vm.tasks.collectAsState()
    val categories by vm.categories.collectAsState()
    val habits by vm.habits.collectAsState()
    val name by vm.prefs.name.collectAsState()
    val ctx = LocalContext.current
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { resumeTick++; onPauseOrDispose { } }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var reliabilityDone by remember { mutableStateOf(vm.prefs.reliabilityDone) }
    val actions = rememberTaskActions(vm, onOpen, onFocus)
    val focus by vm.prefs.focus.collectAsState()

    val today = LocalDate.now()
    val todayDay = today.toEpochDay()
    // Recomputed only when the data changes or the screen comes back, not on every frame.
    val d = remember(tasks, resumeTick) { buildHome(tasks, today, System.currentTimeMillis()) }
    val catNames = remember(categories) { categories.associate { it.id to it.name } }
    val notificationsOn = remember(resumeTick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp)) {
        item {
            Column {
                Text(Fmt.greeting() + if (name.isNotBlank()) ", $name" else "", style = MaterialTheme.typography.headlineSmall)
                Text(Fmt.longDay(today), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (!notificationsOn) item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Notifications are off. Reminders and alarms can't alert you.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                }) { Text("Turn on") }
            }
        }

        // One-time setup card: many phones block alarms until the app is allowed to run in the background.
        if (!reliabilityDone) item {
            Surface(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Make alarms reliable", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        (Reliability.brand?.let { "${it.name} phones" } ?: "Many phones") +
                            " can block alarms to save battery. A 1-minute setup fixes it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { reliabilityDone = true; vm.prefs.reliabilityDone = true }) { Text("Later") }
                        TextButton(onClick = onOpenSettings) { Text("Set up") }
                    }
                }
            }
        }

        item {
            Surface(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            if (d.todays.isEmpty()) "Nothing due today" else "${d.doneToday.size} of ${d.todays.size} done",
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                        )
                        if (d.overdue.isNotEmpty()) Text(
                            "${d.overdue.size} overdue", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (d.todays.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { d.doneToday.size.toFloat() / d.todays.size },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            drawStopIndicator = {},
                            gapSize = 0.dp,
                        )
                    }
                    d.next?.let {
                        Spacer(Modifier.height(12.dp))
                        Text("Next", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "${it.task.title} · ${Fmt.due(it.task.date, it.task.dueMinutes)}",
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        val f = focus
                        TextButton(onClick = { onFocus(if (f != null) -1L else d.next?.task?.id ?: -1L) }) {
                            Text(
                                when {
                                    f == null -> if (d.next != null) "Start next now" else "Start focus"
                                    f.phase == com.aashwin.smartplanner.data.FocusMachine.BREAK -> "On a break · open"
                                    else -> "Focusing on ${f.step.title} · open"
                                }
                            )
                        }
                    }
                }
            }
        }

        if (habits.isNotEmpty()) item { Column {
            val habitDay = vm.repo.logicalToday() // same 4 AM rule as the Habits screen
            SectionTitle("Habits")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(habits, key = { it.habit.id }) { h ->
                    val doneHabit = h.logs.any { it.day == habitDay.toEpochDay() }
                    FilterChip(
                        selected = doneHabit,
                        onClick = { vm.setHabit(h.habit.id, habitDay, !doneHabit) },
                        label = { Text(h.habit.name) },
                        leadingIcon = if (doneHabit) {
                            { Icon(Icons.Filled.Check, null, Modifier.height(FilterChipDefaults.IconSize)) }
                        } else null,
                    )
                }
            }
        } }

        if (d.overdue.isNotEmpty()) {
            item(key = "h-overdue") {
                SectionTitle("Overdue") {
                    TextButton(onClick = { vm.moveToToday(d.overdue.map { it.task.id }) }) { Text("Move all to today") }
                }
            }
            taskItems(d.overdue, catNames, actions, "o")
        }

        item(key = "h-today") {
            Column {
                SectionTitle("Today")
                if (d.todayPending.isEmpty()) EmptyHint(if (d.todays.isEmpty()) "Tap + to plan something." else "All clear for today.")
            }
        }
        taskItems(d.todayPending, catNames, actions, "t")

        if (d.doneToday.isNotEmpty()) {
            item(key = "h-done") {
                SectionTitle("Done · ${d.doneToday.size}") {
                    TextButton(onClick = { showDone = !showDone }) { Text(if (showDone) "Hide" else "Show") }
                }
            }
            if (showDone) taskItems(d.doneToday, catNames, actions, "d")
        }
    }
}
