package com.aashwin.smartplanner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aashwin.smartplanner.data.Priority
import com.aashwin.smartplanner.data.Status
import com.aashwin.smartplanner.data.TaskFull
import com.aashwin.smartplanner.util.Fmt

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun Meta(icon: ImageVector, text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun PriorityMeta(priority: Int) {
    val (icon, color) = when (priority) {
        Priority.HIGH -> Icons.Filled.ArrowUpward to PriorityStyle.high
        Priority.LOW -> Icons.Filled.ArrowDownward to PriorityStyle.low
        else -> Icons.Filled.Remove to PriorityStyle.medium
    }
    Meta(icon, Priority.label(priority), color)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskRow(
    full: TaskFull,
    categoryName: String?,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onPostpone: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val t = full.task
    val done = t.status == Status.DONE
    val missed = t.status == Status.MISSED
    val overdue = t.isOverdue()
    var menu by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpen() },
        colors = CardDefaults.cardColors(
            containerColor = if (overdue) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 0.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = done, onCheckedChange = { onToggle() }, enabled = !missed)
            Column(Modifier.weight(1f)) {
                Text(
                    t.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (t.priority == Priority.HIGH && !done) FontWeight.SemiBold else FontWeight.Normal,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (t.dueDate != null) {
                        if (overdue) Meta(Icons.Filled.Warning, "Overdue · " + Fmt.due(t.date, t.dueMinutes), MaterialTheme.colorScheme.error)
                        else Meta(Icons.Filled.Schedule, Fmt.due(t.date, t.dueMinutes))
                    }
                    if (missed) Meta(Icons.Filled.Warning, "Missed", MaterialTheme.colorScheme.error)
                    PriorityMeta(t.priority)
                    if (categoryName != null) Meta(Icons.Filled.Folder, categoryName)
                    if (full.subtasks.isNotEmpty()) Meta(Icons.Filled.Checklist, "${full.subtasks.count { it.done }}/${full.subtasks.size}")
                    if (full.reminders.isNotEmpty()) Meta(Icons.Filled.NotificationsActive, full.reminders.size.toString())
                    if (t.alarmEnabled) Meta(Icons.Filled.Alarm, "Alarm")
                    if (t.isRecurring) Meta(Icons.Filled.Repeat, "Repeats")
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!done) DropdownMenuItem(text = { Text("Postpone 1 day") }, onClick = { menu = false; onPostpone() })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

/**
 * Handles ticking a task: if it has unfinished checklist items, asks whether to complete them too.
 * Returns the function to call when a checkbox is tapped.
 */
@Composable
fun rememberCompleter(vm: MainViewModel): (TaskFull) -> Unit {
    var asking by remember { mutableStateOf<TaskFull?>(null) }
    asking?.let { f ->
        val left = f.subtasks.count { !it.done }
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text("Complete task?") },
            text = { Text("\"${f.task.title}\" still has $left unfinished checklist item${if (left > 1) "s" else ""}. Mark them completed too?") },
            confirmButton = { TextButton(onClick = { vm.complete(f.task.id, true); asking = null }) { Text("Complete all") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { asking = null }) { Text("Cancel") }
                    TextButton(onClick = { vm.complete(f.task.id, false); asking = null }) { Text("Only the task") }
                }
            },
        )
    }
    return { f ->
        when {
            f.task.status == Status.DONE -> vm.uncomplete(f.task.id)
            f.subtasks.any { !it.done } -> asking = f
            else -> vm.complete(f.task.id, false)
        }
    }
}

@Composable
fun ConfirmDelete(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete?") },
        text = { Text("\"$title\" will be deleted permanently.") },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Task list with delete confirmation, used by several screens. */
@Composable
fun TaskList(
    items: List<TaskFull>,
    vm: MainViewModel,
    categoryNames: Map<Long, String>,
    onOpen: (Long) -> Unit,
) {
    val complete = rememberCompleter(vm)
    var deleting by remember { mutableStateOf<TaskFull?>(null) }
    items.forEach { f ->
        TaskRow(
            full = f,
            categoryName = f.task.categoryId?.let { categoryNames[it] },
            onToggle = { complete(f) },
            onOpen = { onOpen(f.task.id) },
            onPostpone = { vm.postpone(f.task.id) },
            onDuplicate = { vm.duplicate(f.task.id) },
            onDelete = { deleting = f },
        )
    }
    deleting?.let { f -> ConfirmDelete(f.task.title, { vm.delete(f.task.id) }, { deleting = null }) }
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}
