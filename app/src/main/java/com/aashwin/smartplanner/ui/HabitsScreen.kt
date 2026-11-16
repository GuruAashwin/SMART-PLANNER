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
import com.aashwin.smartplanner.data.HabitCalc
import com.aashwin.smartplanner.data.HabitWithLogs
import com.aashwin.smartplanner.util.Fmt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

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
    // Habits use your day: until the day-start hour (default 4 AM) it is still "yesterday",
    // so ticking a late-night session at 12:30 AM keeps the streak.
    val today = vm.repo.logicalToday()
    val zone = java.time.ZoneId.systemDefault()

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
            val logged = h.logs.map { it.day }.toSet()
            val created = java.time.Instant.ofEpochMilli(h.habit.createdAt).atZone(zone).toLocalDate().toEpochDay()
            val s = HabitCalc.stats(logged, created, today.toEpochDay(), today.dayOfWeek.value)
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
                                Text(
                                    " ${s.current} day${if (s.current == 1) "" else "s"} streak · best ${s.best}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            if (s.atRisk) Text(
                                "Do it today to keep the streak",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                listOfNotNull(
                                    if (s.weekDays > 0) "This week ${s.week}/${s.weekDays}" else null,
                                    s.monthPercent?.let { "Last 30 days $it%" } ?: "New habit",
                                    h.habit.reminderMinutes?.let { "Reminder ${Fmt.time(it)}" },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val doneToday = s.doneToday
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
