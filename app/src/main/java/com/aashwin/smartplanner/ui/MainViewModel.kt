package com.aashwin.smartplanner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aashwin.smartplanner.PlannerApp
import com.aashwin.smartplanner.data.CategoryEntity
import com.aashwin.smartplanner.data.HabitWithLogs
import com.aashwin.smartplanner.data.SubtaskEntity
import com.aashwin.smartplanner.data.TaskEntity
import com.aashwin.smartplanner.data.TaskFull
import com.aashwin.smartplanner.data.Undo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** A message for the snackbar, optionally with an Undo. */
class UiMessage(val text: String, val undo: Undo? = null)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = (app as PlannerApp).repository
    val prefs = repo.prefs

    val tasks: StateFlow<List<TaskFull>> =
        repo.tasks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories: StateFlow<List<CategoryEntity>> =
        repo.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val habits: StateFlow<List<HabitWithLogs>> =
        repo.habits.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    fun save(task: TaskEntity, subtasks: List<SubtaskEntity>, reminders: List<Int>) =
        viewModelScope.launch { repo.saveTask(task, subtasks, reminders) }

    fun complete(id: Long, withSubtasks: Boolean) = viewModelScope.launch {
        repo.completeTask(id, withSubtasks)?.let { u ->
            val next = if (u.createdId != null) " · next one scheduled" else ""
            _messages.emit(UiMessage("Completed$next", u))
        }
    }

    fun delete(id: Long) = viewModelScope.launch {
        repo.deleteTask(id)?.let { _messages.emit(UiMessage("Task deleted", it)) }
    }

    fun undo(u: Undo) = viewModelScope.launch { repo.undo(u) }

    fun uncomplete(id: Long) = viewModelScope.launch { repo.uncompleteTask(id) }
    fun postpone(id: Long) = viewModelScope.launch { repo.postponeOneDay(id) }
    fun moveToToday(ids: List<Long>) = viewModelScope.launch {
        repo.moveToToday(ids)
        _messages.emit(UiMessage(if (ids.size == 1) "Moved to today" else "${ids.size} tasks moved to today"))
    }

    fun addCategory(name: String, then: (Long) -> Unit = {}) =
        viewModelScope.launch { then(repo.addCategory(name)) }
    fun deleteCategory(id: Long) = viewModelScope.launch { repo.deleteCategory(id) }

    fun addHabit(name: String, reminderMinutes: Int?) = viewModelScope.launch { repo.addHabit(name, reminderMinutes) }
    fun updateHabit(id: Long, name: String, reminderMinutes: Int?) =
        viewModelScope.launch { repo.updateHabit(id, name, reminderMinutes) }
    fun deleteHabit(id: Long) = viewModelScope.launch { repo.deleteHabit(id) }
    fun setHabit(id: Long, day: LocalDate, done: Boolean) = viewModelScope.launch { repo.setHabitDone(id, day, done) }

    fun rescheduleAll() = viewModelScope.launch { repo.rescheduleAll() }
    fun scheduleSummary() = repo.scheduleSummary()
    fun testAlarm(then: (LocalDateTime) -> Unit) = viewModelScope.launch { then(repo.createTestAlarm()) }
}
