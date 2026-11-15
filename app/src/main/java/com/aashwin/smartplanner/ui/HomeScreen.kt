package com.aashwin.smartplanner.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.aashwin.smartplanner.data.Status
import com.aashwin.smartplanner.data.TaskFull
import com.aashwin.smartplanner.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HomeScreen(vm: MainViewModel, onOpen: (Long) -> Unit) {
    val tasks by vm.tasks.collectAsState()
    val categories by vm.categories.collectAsState()
    val habits by vm.habits.collectAsState()
    val name by vm.prefs.name.collectAsState()
    val ctx = LocalContext.current
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { resumeTick++; onPauseOrDispose { } }
    var showDone by rememberSaveable { mutableStateOf(false) }

    val today = LocalDate.now()
    val todayDay = today.toEpochDay()
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val catNames = categories.associate { it.id to it.name }
    fun completedToday(f: TaskFull) =
        f.task.completedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today } == true

    // Today = due today, plus anything finished today (so finishing an old task counts as progress).
    val todays = tasks.filter {
        it.task.status != Status.MISSED &&
            (it.task.dueDate == todayDay || (it.task.status == Status.DONE && completedToday(it)))
    }
    val doneToday = todays.filter { it.task.status == Status.DONE }
    val overdue = tasks.filter { it.task.isOverdue(now) }
        .sortedWith(compareByDescending<TaskFull> { it.task.priority }.thenBy { it.task.dueMillis() })
    val todayPending = todays.filter { it.task.status == Status.PENDING && !it.task.isOverdue(now) }
    val next = tasks.filter { it.task.status == Status.PENDING && it.task.dueMinutes != null && (it.task.dueMillis() ?: 0) >= now }
        .minByOrNull { it.task.dueMillis() ?: Long.MAX_VALUE }
    val notificationsOn = remember(resumeTick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Text(
            Fmt.greeting() + if (name.isNotBlank()) ", $name" else "",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(Fmt.longDay(today), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (!notificationsOn) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Notifications are off. Reminders and alarms can't alert you.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    )
                }) { Text("Turn on") }
            }
        }

        Surface(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (todays.isEmpty()) "Nothing due today" else "${doneToday.size} of ${todays.size} done",
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                    )
                    if (overdue.isNotEmpty()) Text(
                        "${overdue.size} overdue", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (todays.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { doneToday.size.toFloat() / todays.size },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                    )
                }
                next?.let {
                    Spacer(Modifier.height(12.dp))
                    Text("Next", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "${it.task.title} · ${Fmt.due(it.task.date, it.task.dueMinutes)}",
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        if (habits.isNotEmpty()) {
            SectionTitle("Habits")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(habits.size) { i ->
                    val h = habits[i]
                    val doneHabit = h.logs.any { it.day == todayDay }
                    FilterChip(
                        selected = doneHabit,
                        onClick = { vm.setHabit(h.habit.id, today, !doneHabit) },
                        label = { Text(h.habit.name) },
                        leadingIcon = if (doneHabit) {
                            { Icon(Icons.Filled.Check, null, Modifier.height(FilterChipDefaults.IconSize)) }
                        } else null,
                    )
                }
            }
        }

        if (overdue.isNotEmpty()) {
            SectionTitle("Overdue") {
                TextButton(onClick = { vm.moveToToday(overdue.map { it.task.id }) }) { Text("Move all to today") }
            }
            TaskList(overdue, vm, catNames, onOpen)
        }

        SectionTitle("Today")
        if (todayPending.isEmpty()) EmptyHint(if (todays.isEmpty()) "Tap + to plan something." else "All clear for today.")
        TaskList(todayPending, vm, catNames, onOpen)

        if (doneToday.isNotEmpty()) {
            SectionTitle("Done · ${doneToday.size}") {
                TextButton(onClick = { showDone = !showDone }) { Text(if (showDone) "Hide" else "Show") }
            }
            if (showDone) TaskList(doneToday, vm, catNames, onOpen)
        }
        Spacer(Modifier.height(96.dp))
    }
}
