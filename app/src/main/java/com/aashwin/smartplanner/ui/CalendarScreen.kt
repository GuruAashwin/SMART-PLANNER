package com.aashwin.smartplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aashwin.smartplanner.data.Status
import com.aashwin.smartplanner.util.Fmt
import java.time.LocalDate

@Composable
fun CalendarScreen(
    vm: MainViewModel,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onOpen: (Long) -> Unit,
) {
    val tasks by vm.tasks.collectAsState()
    val categories by vm.categories.collectAsState()
    val habits by vm.habits.collectAsState()
    val catNames = categories.associate { it.id to it.name }
    val today = LocalDate.now()
    val month = selected.withDayOfMonth(1)

    val byDay = tasks.filter { it.task.dueDate != null && it.task.status != Status.MISSED }.groupBy { it.task.dueDate!! }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSelect(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
            Text(
                Fmt.month(month), Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { onSelect(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
        }
        TextButton(onClick = { onSelect(today) }, modifier = Modifier.align(Alignment.End)) { Text("Today") }

        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
            }
        }
        val lead = month.dayOfWeek.value - 1
        val len = month.lengthOfMonth()
        val cells = lead + len
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - lead
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (idx in 0 until len) {
                            val day = month.plusDays(idx.toLong())
                            val items = byDay[day.toEpochDay()].orEmpty()
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
                                if (items.isNotEmpty()) {
                                    val anyOverdue = items.any { it.task.isOverdue() }
                                    Box(
                                        Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape).background(
                                            when {
                                                isSel -> MaterialTheme.colorScheme.onPrimary
                                                anyOverdue -> MaterialTheme.colorScheme.error
                                                else -> MaterialTheme.colorScheme.primary
                                            }
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SectionTitle(Fmt.day(selected) + if (Fmt.day(selected) == "Today" || Fmt.day(selected) == "Tomorrow") " · " + Fmt.longDay(selected) else "")
        val dayTasks = byDay[selected.toEpochDay()].orEmpty()
        if (dayTasks.isEmpty()) EmptyHint("No tasks on this day. Tap + to add one.")
        TaskList(dayTasks, vm, catNames, onOpen)

        if (habits.isNotEmpty() && !selected.isAfter(today)) {
            SectionTitle("Habits")
            habits.forEach { h ->
                val done = h.logs.any { it.day == selected.toEpochDay() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = done, onCheckedChange = { vm.setHabit(h.habit.id, selected, it) })
                    Text(h.habit.name)
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}
