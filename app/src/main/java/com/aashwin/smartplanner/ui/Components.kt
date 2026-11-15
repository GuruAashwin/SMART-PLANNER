package com.aashwin.smartplanner.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
private fun Meta(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, bold: Boolean = false) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = if (bold) FontWeight.SemiBold else null)
}

@Composable
private fun MetaIcon(icon: ImageVector, description: String) {
    Icon(icon, description, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Priority uses an icon + a word; Medium is the default and shows nothing (less noise). */
@Composable
fun PriorityTag(priority: Int) {
    when (priority) {
        Priority.HIGH -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Flag, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(2.dp))
            Meta("High", MaterialTheme.colorScheme.primary, bold = true)
        }
        Priority.LOW -> Meta("Low")
        else -> {}
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TaskRow(
    full: TaskFull,
    categoryName: String?,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onPostpone: () -> Unit,
    onMoveToToday: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val t = full.task
    val done = t.status == Status.DONE
    val missed = t.status == Status.MISSED
    val overdue = t.isOverdue()
    var menu by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Box {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggle, enabled = !missed) {
                if (done) Icon(Icons.Filled.CheckCircle, "Mark not done", tint = MaterialTheme.colorScheme.primary)
                else Icon(
                    Icons.Outlined.Circle, "Mark done",
                    tint = if (t.priority == Priority.HIGH) MaterialTheme.colorScheme.primary else muted,
                )
            }
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    t.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (done || missed) muted else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    when {
                        missed -> Meta("Missed · " + Fmt.due(t.date, t.dueMinutes), MaterialTheme.colorScheme.error)
                        overdue -> Meta("Overdue · " + Fmt.due(t.date, t.dueMinutes), MaterialTheme.colorScheme.error, bold = true)
                        t.dueDate != null -> Meta(Fmt.due(t.date, t.dueMinutes))
                    }
                    if (!done) PriorityTag(t.priority)
                    if (categoryName != null) Meta(categoryName)
                    if (full.subtasks.isNotEmpty()) Meta("${full.subtasks.count { it.done }}/${full.subtasks.size}")
                    if (t.alarmEnabled && !done) MetaIcon(Icons.Filled.Alarm, "Alarm")
                    if (t.isRecurring) MetaIcon(Icons.Filled.Repeat, "Repeats")
                    if (!done && t.postponeCount >= 3) Meta("postponed ${t.postponeCount}×", MaterialTheme.colorScheme.primary)
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (!done && !missed) {
                if (overdue) DropdownMenuItem(text = { Text("Move to today") }, onClick = { menu = false; onMoveToToday() })
                DropdownMenuItem(text = { Text("Postpone 1 day") }, onClick = { menu = false; onPostpone() })
            }
            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
        }
    }
}

/**
 * Handles ticking a task: if it has unfinished checklist items, asks whether to complete them too.
 */
@Composable
fun rememberCompleter(vm: MainViewModel): (TaskFull) -> Unit {
    var asking by remember { mutableStateOf<TaskFull?>(null) }
    asking?.let { f ->
        val left = f.subtasks.count { !it.done }
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text("Complete task?") },
            text = { Text("$left checklist item${if (left > 1) "s are" else " is"} still open.") },
            confirmButton = { TextButton(onClick = { vm.complete(f.task.id, true); asking = null }) { Text("Complete all") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { asking = null }) { Text("Cancel") }
                    TextButton(onClick = { vm.complete(f.task.id, false); asking = null }) { Text("Task only") }
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

/** Task list used by every screen. Long-press a task for more actions. */
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
        key(f.task.id) {
            TaskRow(
                full = f,
                categoryName = f.task.categoryId?.let { categoryNames[it] },
                onToggle = { complete(f) },
                onOpen = { onOpen(f.task.id) },
                onPostpone = { vm.postpone(f.task.id) },
                onMoveToToday = { vm.moveToToday(listOf(f.task.id)) },
                onDuplicate = { vm.duplicate(f.task.id) },
                onDelete = { deleting = f },
            )
        }
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
