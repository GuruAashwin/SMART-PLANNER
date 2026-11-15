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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aashwin.smartplanner.data.Priority
import com.aashwin.smartplanner.data.Status
import com.aashwin.smartplanner.util.Fmt
import java.time.LocalDate

@Composable
fun HomeScreen(vm: MainViewModel, onOpen: (Long) -> Unit) {
    val tasks by vm.tasks.collectAsState()
    val categories by vm.categories.collectAsState()
    val habits by vm.habits.collectAsState()
    val name by vm.prefs.name.collectAsState()
    val ctx = LocalContext.current
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { resumeTick++; onPauseOrDispose { } }

    val today = LocalDate.now()
    val todayDay = today.toEpochDay()
    val now = System.currentTimeMillis()
    val catNames = categories.associate { it.id to it.name }

    val todays = tasks.filter { it.task.dueDate == todayDay && it.task.status != Status.MISSED }
    val doneCount = todays.count { it.task.status == Status.DONE }
    val overdue = tasks.filter { it.task.isOverdue(now) }
        .sortedWith(compareByDescending<com.aashwin.smartplanner.data.TaskFull> { it.task.priority }.thenBy { it.task.dueMillis() })
    val next = tasks.filter { it.task.status == Status.PENDING && it.task.dueMinutes != null && (it.task.dueMillis() ?: 0) >= now }
        .minByOrNull { it.task.dueMillis() ?: Long.MAX_VALUE }
    val todayPending = todays.filter { it.task.status == Status.PENDING && !it.task.isOverdue(now) }
    val todayDone = todays.filter { it.task.status == Status.DONE }
    val notificationsOn = remember(resumeTick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            Fmt.greeting() + if (name.isNotBlank()) ", $name" else "",
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
        )
        Text(Fmt.longDay(today), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (!notificationsOn) {
            Card(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.NotificationsOff, null)
                    Text(
                        "Notifications are off, so reminders and alarms can't alert you.",
                        Modifier.weight(1f).padding(horizontal = 10.dp),
                    )
                    TextButton(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        )
                    }) { Text("Enable") }
                }
            }
        }

        Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    if (todays.isEmpty()) "No tasks scheduled today" else "$doneCount of ${todays.size} tasks completed",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (todays.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { doneCount.toFloat() / todays.size },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                    )
                }
                next?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Next: ${it.task.title} – ${Fmt.due(it.task.date, it.task.dueMinutes)}",
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    )
                }
                if (overdue.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    val top = overdue.first().task
                    Text(
                        "Overdue: ${top.title}" + if (overdue.size > 1) " (+${overdue.size - 1} more)" else "",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = if (top.priority == Priority.HIGH) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        if (habits.isNotEmpty()) {
            SectionTitle("Habits today")
            run {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(habits.size) { i ->
                        val h = habits[i]
                        val doneToday = h.logs.any { it.day == todayDay }
                        FilterChip(
                            selected = doneToday,
                            onClick = { vm.setHabit(h.habit.id, today, !doneToday) },
                            label = { Text(h.habit.name) },
                            leadingIcon = if (doneToday) {
                                { Icon(Icons.Filled.Check, null, Modifier.height(FilterChipDefaults.IconSize)) }
                            } else null,
                        )
                    }
                }
            }
        }

        if (overdue.isNotEmpty()) {
            SectionTitle("Overdue")
            TaskList(overdue, vm, catNames, onOpen)
        }
        SectionTitle("Today")
        if (todayPending.isEmpty()) EmptyHint("Nothing left for today. Tap + to add a task.")
        TaskList(todayPending, vm, catNames, onOpen)
        if (todayDone.isNotEmpty()) {
            SectionTitle("Done today")
            TaskList(todayDone, vm, catNames, onOpen)
        }
        Spacer(Modifier.height(96.dp))
    }
}
