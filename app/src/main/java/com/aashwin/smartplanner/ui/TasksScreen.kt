package com.aashwin.smartplanner.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aashwin.smartplanner.data.Priority
import com.aashwin.smartplanner.data.Status
import java.time.LocalDate

private val FILTERS = listOf("All", "Today", "Upcoming", "Overdue", "No date", "Completed")

@Composable
fun TasksScreen(vm: MainViewModel, onOpen: (Long) -> Unit) {
    val tasks by vm.tasks.collectAsState()
    val categories by vm.categories.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var priority by rememberSaveable { mutableStateOf(-1) }
    var categoryId by rememberSaveable { mutableStateOf(-1L) }
    var catMenu by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val today = LocalDate.now().toEpochDay()
    val catNames = categories.associate { it.id to it.name }

    val shown = tasks.filter { f ->
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
                "No date" -> t.status == Status.PENDING && t.dueDate == null
                "Completed" -> t.status != Status.PENDING
                else -> t.status == Status.PENDING
            }
    }.let { list ->
        if (filter == "Completed") list.sortedByDescending { it.task.completedAt ?: it.task.createdAt } else list
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            placeholder = { Text("Search tasks") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Clear") } },
            singleLine = true,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FILTERS.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Priority.HIGH, Priority.MEDIUM, Priority.LOW).forEach { p ->
                FilterChip(
                    selected = priority == p,
                    onClick = { priority = if (priority == p) -1 else p },
                    label = { Text(Priority.label(p) + " priority") },
                )
            }
            Box {
                FilterChip(
                    selected = categoryId != -1L,
                    onClick = { catMenu = true },
                    label = { Text(catNames[categoryId] ?: "Category") },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                )
                DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                    DropdownMenuItem(text = { Text("All categories") }, onClick = { categoryId = -1L; catMenu = false })
                    categories.forEach { c ->
                        DropdownMenuItem(text = { Text(c.name) }, onClick = { categoryId = c.id; catMenu = false })
                    }
                }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(4.dp))
            if (shown.isEmpty()) EmptyHint(if (query.isNotBlank()) "No tasks match \"$query\"." else "No tasks here.")
            TaskList(shown, vm, catNames, onOpen)
            Spacer(Modifier.height(96.dp))
        }
    }
}
