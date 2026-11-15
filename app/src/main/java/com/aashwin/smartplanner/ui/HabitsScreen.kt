package com.aashwin.smartplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitsScreen(vm: MainViewModel) {
    val habits by vm.habits.collectAsState()
    var deleting by remember { mutableStateOf<HabitWithLogs?>(null) }
    val today = LocalDate.now()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Habits", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Tap a day to log it. Long-press a habit to delete it.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )
        if (habits.isEmpty()) EmptyHint("No habits yet. Tap + to add one, like Exercise, Reading or Practicing coding.")
        habits.forEach { h ->
            val s = habitStats(h, today)
            val logged = h.logs.map { it.day }.toSet()
            Surface(
                Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    .combinedClickable(onClick = {}, onLongClick = { deleting = h }),
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
                                    " ${s.current} day streak · best ${s.best}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Text(
                                "This week ${s.week}/${s.weekDays} · Last 30 days ${s.monthPercent}%",
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
                                Text(day.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall)
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
        Spacer(Modifier.height(96.dp))
    }
    deleting?.let { h ->
        ConfirmDelete(h.habit.name, { vm.deleteHabit(h.habit.id) }, { deleting = null })
    }
}

