package com.aashwin.smartplanner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    Recurrence.DAILY -> "Every day"; Recurrence.WEEKDAYS -> "On chosen days"; Recurrence.WEEKLY -> "Every week"
    Recurrence.MONTHLY -> "Every month"; Recurrence.EVERY_N_DAYS -> "Every few days"; else -> "Doesn't repeat"
}

/** One line of the editor: icon on the left, content on the right. */
@Composable
private fun EditorRow(icon: ImageVector, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun Muted(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
    var moreMenu by remember { mutableStateOf(false) }
    var newCatDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var initialState by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(taskId) {
        if (isNew) {
            if (draft?.minutes != null && vm.prefs.defaultReminder >= 0) reminders.add(vm.prefs.defaultReminder)
            initialState = "new"
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

    /** Fingerprint of everything editable, to notice unsaved changes. */
    fun currentState(): String = listOf(
        title, description, date, minutes, priority, categoryId, alarm, recType, recInterval, recDays,
        reminders.sorted(), subtasks.map { it.title to it.done }, newSub,
    ).toString()
    LaunchedEffect(loaded, original) { if (loaded && initialState != "new" && original != null) initialState = currentState() }
    fun isDirty(): Boolean = when (initialState) {
        null -> false
        "new" -> title.isNotBlank() || subtasks.isNotEmpty() || description.isNotBlank()
        else -> currentState() != initialState
    }
    fun leave() { if (isDirty()) confirmDiscard = true else onClose() }
    BackHandler(enabled = true) { leave() }

    fun setTime(m: Int?) {
        val hadNoTime = minutes == null
        minutes = m
        if (m != null && hadNoTime && reminders.isEmpty() && vm.prefs.defaultReminder >= 0) reminders.add(vm.prefs.defaultReminder)
        if (m == null) { reminders.clear(); alarm = false }
    }

    fun save() {
        if (title.isBlank()) { titleError = true; return }
        var d = date
        if (recType != Recurrence.NONE) d = RecurrenceCalc.firstOnOrAfter(d ?: LocalDate.now(), recType, recDays)
        val base = original ?: TaskEntity(title = "")
        val dateChanged = base.dueDate != d?.toEpochDay() || base.dueMinutes != (if (d == null) null else minutes)
        val task = base.copy(
            title = title.trim(), description = description.trim(), categoryId = categoryId, priority = priority,
            dueDate = d?.toEpochDay(), dueMinutes = if (d == null) null else minutes,
            alarmEnabled = alarm && d != null && minutes != null, recType = recType,
            recInterval = recInterval.toIntOrNull()?.coerceIn(1, 365) ?: 1,
            recDays = if (recType == Recurrence.WEEKDAYS && recDays == 0) (1 shl (d ?: LocalDate.now()).dayOfWeek.value - 1) else recDays,
            snoozedUntil = if (dateChanged) null else base.snoozedUntil,
            // A new date counts as a fresh plan: the "postponed" hint resets.
            postponeCount = if (dateChanged && original != null) 0 else base.postponeCount,
            // New date or new rule = the monthly day follows the date picked now.
            recAnchor = if (recType == Recurrence.MONTHLY && (dateChanged || base.recType != Recurrence.MONTHLY)) (d?.dayOfMonth ?: 0)
                else if (recType == Recurrence.MONTHLY) base.recAnchor else 0,
        )
        vm.save(task, subtasks.toList(), if (task.dueMinutes == null) emptyList() else reminders.toList())
        onClose()
    }

    val plainField = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = { IconButton(onClick = { leave() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (!isNew) Box {
                        IconButton(onClick = { moreMenu = true }) { Icon(Icons.Filled.MoreVert, "More actions") }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            if (original?.status == Status.PENDING) DropdownMenuItem(
                                text = { Text("Postpone 1 day") }, onClick = { moreMenu = false; vm.postpone(taskId); onClose() },
                            )
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { moreMenu = false; vm.delete(taskId); onClose() })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0), // the outer screen already handles system bars
            )
        },
        bottomBar = {
            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth().imePadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp).height(52.dp),
            ) { Text(if (isNew) "Add task" else "Save", fontWeight = FontWeight.SemiBold) }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { pad ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            TextField(
                value = title, onValueChange = { title = it; titleError = false },
                placeholder = { Text("What needs doing?", style = MaterialTheme.typography.headlineSmall) },
                textStyle = MaterialTheme.typography.headlineSmall,
                isError = titleError,
                supportingText = if (titleError) { { Text("Enter a title") } } else null,
                modifier = Modifier.fillMaxWidth(), colors = plainField,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            TextField(
                value = description, onValueChange = { description = it },
                placeholder = { Text("Add notes") },
                modifier = Modifier.fillMaxWidth(), colors = plainField,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            val postponed = original?.let { it.status == Status.PENDING && it.postponeCount >= 3 } == true
            if (postponed) {
                Text(
                    "Postponed ${original?.postponeCount} times. Maybe pick a date that really works?",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // ---- date ----
            EditorRow(Icons.Filled.CalendarMonth, onClick = if (date != null) { { showDate = true } } else null) {
                if (date == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { date = LocalDate.now() }, label = { Text("Today") })
                        AssistChip(onClick = { date = LocalDate.now().plusDays(1) }, label = { Text("Tomorrow") })
                        AssistChip(onClick = { showDate = true }, label = { Text("Pick date") })
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Fmt.day(date!!) + if (Fmt.day(date!!).let { it == "Today" || it == "Tomorrow" }) " · " + Fmt.longDay(date!!) else "",
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = { date = null; setTime(null) }) { Icon(Icons.Filled.Close, "Remove date") }
                    }
                }
            }
            // ---- time ----
            if (date != null) {
                EditorRow(Icons.Filled.Schedule, onClick = { showTime = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (minutes == null) Box(Modifier.weight(1f)) { Muted("Add time") }
                        else Text(Fmt.time(minutes!!), Modifier.weight(1f))
                        if (minutes != null) IconButton(onClick = { setTime(null) }) { Icon(Icons.Filled.Close, "Remove time") }
                    }
                }
            }
            if (date != null && minutes == null) {
                Text(
                    "No time set: it will be in your morning summary. Add a time for reminders or an alarm.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 42.dp, bottom = 4.dp),
                )
            }
            // ---- reminders + alarm (need a time) ----
            if (date != null && minutes != null) {
                EditorRow(Icons.Filled.Notifications) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        reminders.sorted().forEach { off ->
                            val at = LocalTime.of(minutes!! / 60, minutes!! % 60).minusMinutes(off.toLong())
                            InputChip(
                                selected = false, onClick = { reminders.remove(off) },
                                label = { Text(if (off == 0) "At ${Fmt.time(at)}" else Fmt.time(at)) },
                                trailingIcon = { Icon(Icons.Filled.Close, "Remove reminder", Modifier.size(16.dp)) },
                            )
                        }
                        Box {
                            AssistChip(
                                onClick = { reminderMenu = true },
                                label = { Text(if (reminders.isEmpty()) "Add reminder" else "Add") },
                                leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) },
                            )
                            DropdownMenu(expanded = reminderMenu, onDismissRequest = { reminderMenu = false }) {
                                REMINDER_PRESETS.filter { it !in reminders }.forEach { off ->
                                    DropdownMenuItem(text = { Text(Fmt.offset(off)) }, onClick = { reminders.add(off); reminderMenu = false })
                                }
                                DropdownMenuItem(text = { Text("Pick a time…") }, onClick = { reminderMenu = false; showReminderTime = true })
                            }
                        }
                    }
                }
                EditorRow(Icons.Filled.Alarm, onClick = { alarm = !alarm }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Alarm")
                            Text(
                                "Rings full screen at ${Fmt.time(minutes!!)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = alarm, onCheckedChange = { alarm = it })
                    }
                }
            }
            // ---- repeat ----
            EditorRow(Icons.Filled.Repeat, onClick = { recMenu = true }) {
                Box {
                    if (recType == Recurrence.NONE) Muted(recName(recType)) else Text(recName(recType))
                    DropdownMenu(expanded = recMenu, onDismissRequest = { recMenu = false }) {
                        RECURRENCES.forEach { r ->
                            DropdownMenuItem(text = { Text(recName(r)) }, onClick = { recType = r; recMenu = false })
                        }
                    }
                }
            }
            if (recType == Recurrence.WEEKDAYS) {
                FlowRow(Modifier.padding(start = 42.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Recurrence.DAY_SHORT.forEachIndexed { i, name ->
                        FilterChip(
                            selected = recDays and (1 shl i) != 0,
                            onClick = { recDays = recDays xor (1 shl i) },
                            label = { Text(name) },
                        )
                    }
                }
            }
            if (recType == Recurrence.EVERY_N_DAYS) {
                OutlinedTextField(
                    value = recInterval, onValueChange = { v -> recInterval = v.filter { it.isDigit() }.take(3) },
                    label = { Text("Every … days") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.padding(start = 42.dp).width(160.dp), singleLine = true,
                )
            }
            // ---- priority ----
            EditorRow(Icons.Filled.Flag) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Priority.LOW, Priority.MEDIUM, Priority.HIGH).forEach { p ->
                        FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(Priority.label(p)) })
                    }
                }
            }
            // ---- category ----
            EditorRow(Icons.Filled.Folder, onClick = { catMenu = true }) {
                Box {
                    val name = categories.firstOrNull { it.id == categoryId }?.name
                    if (name == null) Muted("No category") else Text(name)
                    DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                        DropdownMenuItem(text = { Text("No category") }, onClick = { categoryId = null; catMenu = false })
                        categories.forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, onClick = { categoryId = c.id; catMenu = false })
                        }
                        DropdownMenuItem(text = { Text("New category…") }, onClick = { catMenu = false; newCatDialog = true })
                    }
                }
            }

            // ---- checklist ----
            SectionTitle("Checklist" + if (subtasks.isNotEmpty()) " · ${subtasks.count { it.done }}/${subtasks.size}" else "")
            subtasks.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { subtasks[i] = s.copy(done = !s.done) }) {
                        if (s.done) Icon(Icons.Filled.CheckCircle, "Undo", tint = MaterialTheme.colorScheme.primary)
                        else Icon(Icons.Outlined.Circle, "Done", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        s.title, Modifier.weight(1f),
                        color = if (s.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = { subtasks.removeAt(i) }) {
                        Icon(Icons.Filled.Close, "Remove item", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            fun addSub() {
                if (newSub.isNotBlank()) { subtasks.add(SubtaskEntity(taskId = 0, title = newSub.trim())); newSub = "" }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { addSub() }) { Icon(Icons.Filled.Add, "Add item", tint = MaterialTheme.colorScheme.primary) }
                TextField(
                    value = newSub, onValueChange = { newSub = it },
                    placeholder = { Text("Add item") }, singleLine = true,
                    modifier = Modifier.weight(1f), colors = plainField,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                    keyboardActions = KeyboardActions(onDone = { addSub() }),
                )
            }
            Spacer(Modifier.height(24.dp))
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
        TimeDialog(initial = minutes ?: ((LocalTime.now().hour + 1).coerceAtMost(23) * 60), onDismiss = { showTime = false }) { m ->
            setTime(m); showTime = false
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
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Save changes?") },
            text = { Text("You have changes that aren't saved yet.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; save() }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Discard") } },
        )
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
