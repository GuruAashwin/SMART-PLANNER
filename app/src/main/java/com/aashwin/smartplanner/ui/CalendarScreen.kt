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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aashwin.smartplanner.data.RecurrenceCalc
import com.aashwin.smartplanner.data.Status
import com.aashwin.smartplanner.data.TaskFull
import com.aashwin.smartplanner.util.Fmt
import java.time.LocalDate

@Composable
fun CalendarScreen(
    vm: MainViewModel,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onOpen: (Long) -> Unit,
    onFocus: (Long) -> Unit = {},
) {
    val tasks by vm.tasks.collectAsState()
    val categories by vm.categories.collectAsState()
    val habits by vm.habits.collectAsState()
    val actions = rememberTaskActions(vm, onOpen, onFocus)
    val catNames = remember(categories) { categories.associate { it.id to it.name } }
    val today = LocalDate.now()
    val month = selected.withDayOfMonth(1)
    val monthEnd = month.plusMonths(1).minusDays(1)

    val byDay = remember(tasks) {
        tasks.filter { it.task.dueDate != null && it.task.status != Status.MISSED }.groupBy { it.task.dueDate!! }
    }
    // Future dates of repeating tasks, so next week's calendar isn't empty.
    val repeats = remember(tasks, month) {
        val map = HashMap<Long, MutableList<TaskFull>>()
        tasks.filter { it.task.status == Status.PENDING && it.task.isRecurring }.forEach { f ->
            RecurrenceCalc.occurrences(f.task, monthEnd).filter { !it.isBefore(month) }.forEach { d ->
                map.getOrPut(d.toEpochDay()) { mutableListOf() }.add(f)
            }
        }
        map
    }
    val dayTasks = byDay[selected.toEpochDay()].orEmpty()
    val dayRepeats = repeats[selected.toEpochDay()].orEmpty()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp)) {
        item(key = "grid") {
            Column {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSelect(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                    Text(
                        Fmt.month(month), Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(onClick = { onSelect(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
                }
                if (selected != today) TextButton(onClick = { onSelect(today) }, modifier = Modifier.align(Alignment.End)) { Text("Today") }

                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                        Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val lead = month.dayOfWeek.value - 1
                val len = month.lengthOfMonth()
                val rows = (lead + len + 6) / 7
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val idx = r * 7 + c - lead
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                                if (idx in 0 until len) {
                                    val day = month.plusDays(idx.toLong())
                                    val key = day.toEpochDay()
                                    val items = byDay[key].orEmpty()
                                    val hasRepeat = repeats.containsKey(key)
                                    val isSel = day == selected
                                    val isToday = day == today
                                    Column(
                                        Modifier.fillMaxSize().clip(CircleShape)
                                            .then(if (isSel) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
                                            .then(if (isToday && !isSel) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                            .clickable { onSelect(day) },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            day.dayOfMonth.toString(),
                                            color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        )
                                        if (items.any { it.task.status == Status.PENDING } || hasRepeat) {
                                            val dot = when {
                                                isSel -> MaterialTheme.colorScheme.onPrimary
                                                items.any { it.task.isOverdue() } -> MaterialTheme.colorScheme.error
                                                else -> MaterialTheme.colorScheme.primary
                                            }
                                            Box(Modifier.padding(top = 2.dp).size(4.dp).clip(CircleShape).background(dot))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                SectionTitle(Fmt.day(selected) + if (Fmt.day(selected) == "Today" || Fmt.day(selected) == "Tomorrow") " · " + Fmt.longDay(selected) else "")
                if (dayTasks.isEmpty() && dayRepeats.isEmpty()) EmptyHint("Nothing planned. Tap + to add a task for this day.")
            }
        }

        taskItems(dayTasks, catNames, actions, "c")

        // Upcoming dates of repeating tasks: shown muted; tap opens the real (current) task.
        items(dayRepeats, key = { "r-${it.task.id}" }) { f ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(f.task.id) }.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Repeat, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(f.task.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Repeats" + (f.task.dueMinutes?.let { " · " + Fmt.time(it) } ?: ""),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (habits.isNotEmpty() && !selected.isAfter(today)) {
            item(key = "habits-title") { SectionTitle("Habits") }
            items(habits, key = { "hb-${it.habit.id}" }) { h ->
                val done = h.logs.any { it.day == selected.toEpochDay() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = done, onCheckedChange = { vm.setHabit(h.habit.id, selected, it) })
                    Text(h.habit.name)
                }
            }
        }
    }
}
