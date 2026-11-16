package com.aashwin.smartplanner.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aashwin.smartplanner.data.Priority
import com.aashwin.smartplanner.data.Status
import java.time.LocalDate

private val FILTERS = listOf("Active", "Today", "Upcoming", "Overdue", "Someday", "Done")

@Composable
fun TasksScreen(vm: MainViewModel, onOpen: (Long) -> Unit, onFocus: (Long) -> Unit = {}) {
    val tasks by vm.tasks.collectAsState()
    val categories by vm.categories.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Active") }
    var priority by rememberSaveable { mutableStateOf(-1) }
    var categoryId by rememberSaveable { mutableStateOf(-1L) }
    var menu by remember { mutableStateOf(false) }
    val actions = rememberTaskActions(vm, onOpen, onFocus)

    val now = System.currentTimeMillis()
    val today = LocalDate.now().toEpochDay()
    val catNames = remember(categories) { categories.associate { it.id to it.name } }
    val extraFilters = (if (priority != -1) 1 else 0) + (if (categoryId != -1L) 1 else 0)

    val shown = remember(tasks, query, filter, priority, categoryId) { tasks.filter { f ->
        val t = f.task
        val q = query.trim()
        (q.isEmpty() || t.title.contains(q, true) || t.description.contains(q, true) ||
            f.subtasks.any { it.title.contains(q, true) }) &&
            (priority == -1 || t.priority == priority) &&
            (categoryId == -1L || t.categoryId == categoryId) &&
            when (filter) {
                "Today" -> t.dueDate == today && t.status == Status.PENDING
                "Upcoming" -> t.status == Status.PENDING && (t.dueDate ?: -1) > today
                "Overdue" -> t.isOverdue(now)
                "Someday" -> t.status == Status.PENDING && t.dueDate == null
                "Done" -> t.status != Status.PENDING
                else -> t.status == Status.PENDING
            }
    }.let { list ->
        if (filter == "Done") list.sortedByDescending { it.task.completedAt ?: it.task.createdAt } else list
    } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            placeholder = { Text("Search") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Clear search") }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(
                                Icons.Filled.FilterList, "Priority and category filter",
                                tint = if (extraFilters > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            Text("Priority", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            listOf(-1, Priority.HIGH, Priority.MEDIUM, Priority.LOW).forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(if (p == -1) "Any priority" else Priority.label(p)) },
                                    trailingIcon = { if (priority == p) Icon(Icons.Filled.Check, null) },
                                    onClick = { priority = p },
                                )
                            }
                            HorizontalDivider()
                            Text("Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            DropdownMenuItem(
                                text = { Text("Any category") },
                                trailingIcon = { if (categoryId == -1L) Icon(Icons.Filled.Check, null) },
                                onClick = { categoryId = -1L },
                            )
                            categories.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.name) },
                                    trailingIcon = { if (categoryId == c.id) Icon(Icons.Filled.Check, null) },
                                    onClick = { categoryId = c.id },
                                )
                            }
                        }
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FILTERS.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) }) }
        }
        if (extraFilters > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(
                        if (priority != -1) Priority.label(priority) + " priority" else null,
                        if (categoryId != -1L) catNames[categoryId] else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { priority = -1; categoryId = -1L }) { Text("Clear") }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            if (shown.isEmpty()) item { EmptyHint(if (query.isNotBlank()) "No tasks match \"$query\"." else "Nothing here.") }
            taskItems(shown, catNames, actions, filter)
        }
    }
}
