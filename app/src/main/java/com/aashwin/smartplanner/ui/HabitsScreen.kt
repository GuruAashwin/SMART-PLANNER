package com.aashwin.smartplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aashwin.smartplanner.data.HabitWithLogs
import com.aashwin.smartplanner.util.Fmt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class HabitStats(val current: Int, val best: Int, val week: Int, val weekDays: Int, val monthPercent: Int)

fun habitStats(h: HabitWithLogs, today: LocalDate = LocalDate.now()): HabitStats {
    val days = h.logs.map { it.day }.toSortedSet()
    val t = today.toEpochDay()
    var d = if (t in days) t else t - 1
    var current = 0
    while (d in days) { current++; d-- }
    var best = 0; var streak = 0; var prev = Long.MIN_VALUE
    for (x in days) { streak = if (x == prev + 1) streak + 1 else 1; best = maxOf(best, streak); prev = x }
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
    val week = days.count { it in monday..t }
    val created = java.time.Instant.ofEpochMilli(h.habit.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
    val windowStart = maxOf(t - 29, minOf(created, days.firstOrNull() ?: created))
    val windowLen = (t - windowStart + 1).coerceAtLeast(1)
    val month = days.count { it in windowStart..t }
    return HabitStats(current, best, week, (t - monday + 1).toInt(), (month * 100 / windowLen).toInt())
}

/** Add or edit a habit: name + optional daily reminder (shown only if it isn't done yet that day). */
@Composable
fun HabitDialog(
    initialName: String,
    initialReminder: Int?,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (String, Int?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var reminder by remember { mutableStateOf(initialReminder) }
    var pickTime by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New habit" else "Edit habit") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("e.g. Exercise, Reading, Drink water") }, singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = { pickTime = true },
                        label = { Text(reminder?.let { "Remind at ${Fmt.time(it)}" } ?: "Add daily reminder") },
                        leadingIcon = { Icon(Icons.Filled.Notifications, null, Modifier.size(18.dp)) },
                    )
                    if (reminder != null) TextButton(onClick = { reminder = null }) { Text("Remove") }
                }
                Text(
                    "Habits track streaks. For work with a time and deadline, use a repeating task instead.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), reminder) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
    if (pickTime) {
        TimeDialog(initial = reminder ?: (20 * 60), title = "Remind me daily at", onDismiss = { pickTime = false }) {
            reminder = it; pickTime = false
        }
    }
}

@Composable
fun HabitsScreen(vm: MainViewModel) {
    val habits by vm.habits.collectAsState()
    var editing by remember { mutableStateOf<HabitWithLogs?>(null) }
    var deleting by remember { mutableStateOf<HabitWithLogs?>(null) }
    val today = LocalDate.now()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp)) {
        item {
            Column {
                Text("Habits", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Tap a circle to log that day. Tap a habit to edit it or set a reminder.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
                if (habits.isEmpty()) EmptyHint("No habits yet. Tap + to add one, like Exercise or Practicing coding.")
            }
        }
        items(habits, key = { it.habit.id }) { h ->
            val s = habitStats(h, today)
            val logged = h.logs.map { it.day }.toSet()
            Surface(
                Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).clickable { editing = h },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(h.habit.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.LocalFireDepartment, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(" ${s.current} day streak · best ${s.best}", style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                "This week ${s.week}/${s.weekDays} · Last 30 days ${s.monthPercent}%" +
                                    (h.habit.reminderMinutes?.let { " · ${Fmt.time(it)}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val doneToday = today.toEpochDay() in logged
                        FilledIconToggleButton(
                            checked = doneToday,
                            onCheckedChange = { vm.setHabit(h.habit.id, today, it) },
                            modifier = Modifier.size(52.dp),
                        ) { Icon(Icons.Filled.Check, if (doneToday) "Done today" else "Mark done today") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        (6 downTo 0).forEach { back ->
                            val day = today.minusDays(back.toLong())
                            val done = day.toEpochDay() in logged
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(day.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Box(
                                    Modifier.padding(top = 2.dp).size(34.dp).clip(CircleShape)
                                        .then(
                                            if (done) Modifier.background(MaterialTheme.colorScheme.primary)
                                            else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                        )
                                        .clickable { vm.setHabit(h.habit.id, day, !done) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        day.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium,
                                        color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { h ->
        HabitDialog(
            initialName = h.habit.name, initialReminder = h.habit.reminderMinutes, isNew = false,
            onDismiss = { editing = null },
            onDelete = { editing = null; deleting = h },
            onSave = { name, rem -> vm.updateHabit(h.habit.id, name, rem); editing = null },
        )
    }
    deleting?.let { h ->
        ConfirmDelete(h.habit.name, { vm.deleteHabit(h.habit.id) }, { deleting = null })
    }
}
