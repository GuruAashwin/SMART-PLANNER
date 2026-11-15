package com.aashwin.smartplanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aashwin.smartplanner.data.Recurrence
import com.aashwin.smartplanner.data.TaskEntity
import com.aashwin.smartplanner.util.Fmt
import com.aashwin.smartplanner.util.QuickParser
import java.time.LocalDate

/**
 * Quick add: type naturally ("Study Python today at 7 PM"), check what was understood, save.
 * If the time is unclear (AM or PM?), the user is asked instead of guessing.
 */
@Composable
fun QuickAddDialog(
    vm: MainViewModel,
    defaultDate: LocalDate?,
    onDismiss: () -> Unit,
    onMoreOptions: (Draft) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var meridiem by remember { mutableStateOf<Boolean?>(null) } // true = PM
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val parsed = remember(text) { QuickParser.parse(text) }
    LaunchedEffect(parsed.ambiguousHour) { meridiem = null }
    val minutes: Int? = parsed.minutes ?: parsed.ambiguousHour?.let { h ->
        meridiem?.let { pm -> ((h % 12) + if (pm) 12 else 0) * 60 + parsed.ambiguousMinute }
    }
    val date = parsed.date ?: defaultDate
    val needsAnswer = parsed.ambiguousHour != null && meridiem == null
    val canSave = text.isNotBlank() && !needsAnswer

    fun save() {
        if (!canSave) return
        val reminders = if (minutes != null && date != null && vm.prefs.defaultReminder >= 0) listOf(vm.prefs.defaultReminder) else emptyList()
        vm.save(
            TaskEntity(title = parsed.title, dueDate = date?.toEpochDay(), dueMinutes = if (date != null) minutes else null, recType = Recurrence.NONE),
            emptyList(), reminders,
        )
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick add") },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text("e.g. Study Python today at 7 PM") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                )
                if (text.isNotBlank()) {
                    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("I understood:", style = MaterialTheme.typography.labelMedium)
                            Text("Task: ${parsed.title}", fontWeight = FontWeight.Medium)
                            Text("Date: " + (date?.let { Fmt.day(it) + " (" + Fmt.longDay(it) + ")" } ?: "No date"))
                            Text("Time: " + (minutes?.let { Fmt.time(it) } ?: if (needsAnswer) "?" else "No time"))
                        }
                    }
                    if (parsed.ambiguousHour != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Is ${parsed.ambiguousHour}${if (parsed.ambiguousMinute > 0) ":%02d".format(parsed.ambiguousMinute) else ""} in the morning or evening?")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = meridiem == false, onClick = { meridiem = false }, label = { Text("AM") })
                            FilterChip(selected = meridiem == true, onClick = { meridiem = true }, label = { Text("PM") })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { save() }, enabled = canSave) { Text("Save") } },
        dismissButton = {
            TextButton(
                onClick = { onMoreOptions(Draft(parsed.title.takeIf { text.isNotBlank() } ?: "", date, if (date != null) minutes else null)) },
                enabled = !needsAnswer,
            ) { Text("More options") }
        },
    )
}
