package com.aashwin.smartplanner.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aashwin.smartplanner.data.Priority
import com.aashwin.smartplanner.data.Recurrence
import com.aashwin.smartplanner.data.RecurrenceCalc
import com.aashwin.smartplanner.data.Status
import com.aashwin.smartplanner.data.SubtaskEntity
import com.aashwin.smartplanner.data.TaskEntity
import com.aashwin.smartplanner.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Pre-filled values for a new task (from quick add or the calendar). */
data class Draft(val title: String = "", val date: LocalDate? = null, val minutes: Int? = null)

private val REMINDER_PRESETS = listOf(0, 5, 10, 15, 30, 60, 120, 1440)
private val RECURRENCES = listOf(
    Recurrence.NONE, Recurrence.DAILY, Recurrence.WEEKDAYS, Recurrence.WEEKLY, Recurrence.MONTHLY, Recurrence.EVERY_N_DAYS,
)
private fun recName(t: Int) = when (t) {
    Recurrence.DAILY -> "Daily"; Recurrence.WEEKDAYS -> "Selected weekdays"; Recurrence.WEEKLY -> "Weekly"
    Recurrence.MONTHLY -> "Monthly"; Recurrence.EVERY_N_DAYS -> "Every N days"; else -> "Does not repeat"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(vm: MainViewModel, taskId: Long, draft: Draft?, onClose: () -> Unit) {
    val categories by vm.categories.collectAsState()
    val isNew = taskId == 0L

    var loaded by remember { mutableStateOf(isNew) }
    var original by remember { mutableStateOf<TaskEntity?>(null) }
    var title by remember { mutableStateOf(draft?.title ?: "") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(draft?.date) }
    var minutes by remember { mutableStateOf(draft?.minutes) }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var alarm by remember { mutableStateOf(false) }
    var recType by remember { mutableStateOf(Recurrence.NONE) }
    var recInterval by remember { mutableStateOf("2") }
    var recDays by remember { mutableStateOf(0) }
    val reminders = remember { mutableStateListOf<Int>() }
    val subtasks = remember { mutableStateListOf<SubtaskEntity>() }
    var newSub by remember { mutableStateOf("") }

    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showReminderTime by remember { mutableStateOf(false) }
    var reminderMenu by remember { mutableStateOf(false) }
    var catMenu by remember { mutableStateOf(false) }
    var recMenu by remember { mutableStateOf(false) }
    var newCatDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }

    LaunchedEffect(taskId) {
        if (isNew) {
            if (draft?.minutes != null && vm.prefs.defaultReminder >= 0) reminders.add(vm.prefs.defaultReminder)
            return@LaunchedEffect
        }
        val full = vm.repo.getTask(taskId) ?: run { onClose(); return@LaunchedEffect }
        val t = full.task
        original = t
        title = t.title; description = t.description; date = t.date; minutes = t.dueMinutes
        priority = t.priority; categoryId = t.categoryId; alarm = t.alarmEnabled
        recType = t.recType; recInterval = t.recInterval.toString(); recDays = t.recDays
        reminders.clear(); reminders.addAll(full.reminders.map { it.offsetMinutes }.sorted())
        subtasks.clear(); subtasks.addAll(full.sortedSubtasks)
        loaded = true
    }

    fun save() {
        if (title.isBlank()) { titleError = true; return }
        var d = date
        if (recType != Recurrence.NONE) {
            d = RecurrenceCalc.firstOnOrAfter(d ?: LocalDate.now(), recType, recDays)
        }
        val base = original ?: TaskEntity(title = "")
        val task = base.copy(
            title = title.trim(), description = description.trim(), categoryId = categoryId, priority = priority,
            dueDate = d?.toEpochDay(), dueMinutes = if (d == null) null else minutes,
            alarmEnabled = alarm, recType = recType,
            recInterval = recInterval.toIntOrNull()?.coerceIn(1, 365) ?: 1,
            recDays = if (recType == Recurrence.WEEKDAYS && recDays == 0) (1 shl (d ?: LocalDate.now()).dayOfWeek.value - 1) else recDays,
            snoozedUntil = null,
        )
        // Re-opening a completed task by editing keeps it completed unless every subtask is undone.
        vm.save(task, subtasks.toList(), reminders.toList()) { }
        onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New task" else "Edit task") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (!isNew) {
                        if (original?.status == Status.PENDING) {
                            IconButton(onClick = { vm.postpone(taskId); onClose() }) { Icon(Icons.Filled.Snooze, "Postpone 1 day") }
                        }
                        IconButton(onClick = { vm.duplicate(taskId); onClose() }) { Icon(Icons.Filled.ContentCopy, "Duplicate") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                    }
                    TextButton(onClick = { save() }) { Text("Save") }
                },
            )
        },
    ) { pad ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = title, onValueChange = { title = it; titleError = false },
                label = { Text("Title") }, isError = titleError,
                supportingText = if (titleError) { { Text("Enter a title") } } else null,
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )

            // ---- date & time ----
            SectionTitle("When")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { showDate = true },
                    label = { Text(date?.let { Fmt.day(it) } ?: "Add date") },
                    leadingIcon = { Icon(Icons.Filled.CalendarMonth, null) },
                )
                if (date != null) {
                    AssistChip(
                        onClick = { showTime = true },
                        label = { Text(minutes?.let { Fmt.time(it) } ?: "Add time") },
                        leadingIcon = { Icon(Icons.Filled.Schedule, null) },
                    )
                    if (minutes != null) TextButton(onClick = { minutes = null }) { Text("No time") }
                }
            }
            if (date != null) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { date = null; minutes = null }) { Text("Remove date") }
                    TextButton(onClick = { date = LocalDate.now() }) { Text("Today") }
                    TextButton(onClick = { date = LocalDate.now().plusDays(1) }) { Text("Tomorrow") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { date = LocalDate.now() }) { Text("Today") }
                    TextButton(onClick = { date = LocalDate.now().plusDays(1) }) { Text("Tomorrow") }
                }
            }

            // ---- priority ----
            SectionTitle("Priority")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val opts = listOf(Priority.LOW, Priority.MEDIUM, Priority.HIGH)
                opts.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = priority == p, onClick = { priority = p },
                        shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                        icon = { },
                    ) { PriorityMeta(p) }
                }
            }

            // ---- category ----
            SectionTitle("Category")
            Box {
                OutlinedButton(onClick = { catMenu = true }) {
                    Text(categories.firstOrNull { it.id == categoryId }?.name ?: "No category")
                }
                DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                    DropdownMenuItem(text = { Text("No category") }, onClick = { categoryId = null; catMenu = false })
                    categories.forEach { c ->
                        DropdownMenuItem(text = { Text(c.name) }, onClick = { categoryId = c.id; catMenu = false })
                    }
                    DropdownMenuItem(text = { Text("+ New category") }, onClick = { catMenu = false; newCatDialog = true })
                }
            }

            // ---- reminders & alarm ----
            SectionTitle("Reminders")
            if (minutes == null) {
                Text(
                    "Set a date and time to use reminders and alarms.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    reminders.sorted().forEach { off ->
                        val at = LocalTime.of(minutes!! / 60, minutes!! % 60).minusMinutes(off.toLong())
                        InputChip(
                            selected = false, onClick = { reminders.remove(off) },
                            label = { Text("${Fmt.offset(off)} (${Fmt.time(at)})") },
                            leadingIcon = { Icon(Icons.Filled.NotificationsActive, null) },
                            trailingIcon = { Icon(Icons.Filled.Close, "Remove") },
                        )
                    }
                    Box {
                        AssistChip(
                            onClick = { reminderMenu = true },
                            label = { Text("Add reminder") },
                            leadingIcon = { Icon(Icons.Filled.Add, null) },
                        )
                        DropdownMenu(expanded = reminderMenu, onDismissRequest = { reminderMenu = false }) {
                            REMINDER_PRESETS.filter { it !in reminders }.forEach { off ->
                                DropdownMenuItem(text = { Text(Fmt.offset(off)) }, onClick = { reminders.add(off); reminderMenu = false })
                            }
                            DropdownMenuItem(text = { Text("Custom time…") }, onClick = { reminderMenu = false; showReminderTime = true })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Alarm, null)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Alarm at due time")
                        Text(
                            "Rings with sound and vibration, with Snooze / Complete / Dismiss",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = alarm, onCheckedChange = { alarm = it })
                }
            }

            // ---- repeat ----
            SectionTitle("Repeat")
            Box {
                OutlinedButton(onClick = { recMenu = true }) { Text(recName(recType)) }
                DropdownMenu(expanded = recMenu, onDismissRequest = { recMenu = false }) {
                    RECURRENCES.forEach { r ->
                        DropdownMenuItem(text = { Text(recName(r)) }, onClick = { recType = r; recMenu = false })
                    }
                }
            }
            if (recType == Recurrence.WEEKDAYS) {
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Recurrence.DAY_SHORT.forEachIndexed { i, name ->
                        val on = recDays and (1 shl i) != 0
                        FilterChip(selected = on, onClick = { recDays = recDays xor (1 shl i) }, label = { Text(name) })
                    }
                }
            }
            if (recType == Recurrence.EVERY_N_DAYS) {
                OutlinedTextField(
                    value = recInterval, onValueChange = { v -> recInterval = v.filter { it.isDigit() }.take(3) },
                    label = { Text("Repeat every … days") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.padding(top = 6.dp).width(200.dp), singleLine = true,
                )
            }

            // ---- checklist ----
            SectionTitle("Checklist" + if (subtasks.isNotEmpty()) " (${subtasks.count { it.done }}/${subtasks.size})" else "")
            subtasks.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = s.done, onCheckedChange = { subtasks[i] = s.copy(done = it) })
                    Text(s.title, Modifier.weight(1f))
                    IconButton(onClick = { subtasks.removeAt(i) }) { Icon(Icons.Filled.Close, "Remove item") }
                }
            }
            fun addSub() {
                if (newSub.isNotBlank()) { subtasks.add(SubtaskEntity(taskId = 0, title = newSub.trim())); newSub = "" }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newSub, onValueChange = { newSub = it },
                    placeholder = { Text("Add checklist item") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addSub() }),
                )
                IconButton(onClick = { addSub() }) { Icon(Icons.Filled.Add, "Add item") }
            }

            // ---- notes ----
            SectionTitle("Notes")
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                placeholder = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(), minLines = 3,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { save() }, Modifier.fillMaxWidth().height(52.dp)) { Text("Save task") }
            Spacer(Modifier.height(32.dp))
        }
    }

    // ---- dialogs ----
    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (showTime) {
        TimeDialog(initial = minutes ?: (LocalTime.now().hour + 1).coerceAtMost(23) * 60, onDismiss = { showTime = false }) { m ->
            val hadNoTime = minutes == null
            minutes = m
            if (hadNoTime && reminders.isEmpty() && vm.prefs.defaultReminder >= 0) reminders.add(vm.prefs.defaultReminder)
            showTime = false
        }
    }
    if (showReminderTime && minutes != null && date != null) {
        TimeDialog(initial = (minutes!! - 60).coerceAtLeast(0), title = "Remind me at", onDismiss = { showReminderTime = false }) { m ->
            val due = LocalDateTime.of(date!!, LocalTime.of(minutes!! / 60, minutes!! % 60))
            var at = LocalDateTime.of(date!!, LocalTime.of(m / 60, m % 60))
            if (at.isAfter(due)) at = at.minusDays(1) // e.g. 11 PM the night before
            val off = ChronoUnit.MINUTES.between(at, due).toInt()
            if (off >= 0 && off !in reminders) reminders.add(off)
            showReminderTime = false
        }
    }
    if (newCatDialog) {
        TextInputDialog("New category", "Name", onDismiss = { newCatDialog = false }) { name ->
            vm.addCategory(name) { id -> categoryId = id }
            newCatDialog = false
        }
    }
    if (confirmDelete) {
        ConfirmDelete(title, { vm.delete(taskId); onClose() }, { confirmDelete = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: Int, title: String = "Select time", onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TextInputDialog(title: String, label: String, initial: String = "", onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onDone(text.trim()) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
