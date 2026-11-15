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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = (app as PlannerApp).repository
    val prefs = repo.prefs

    val tasks: StateFlow<List<TaskFull>> =
        repo.tasks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories: StateFlow<List<CategoryEntity>> =
        repo.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val habits: StateFlow<List<HabitWithLogs>> =
        repo.habits.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun save(task: TaskEntity, subtasks: List<SubtaskEntity>, reminders: List<Int>, done: () -> Unit = {}) =
        viewModelScope.launch { repo.saveTask(task, subtasks, reminders); done() }

    fun complete(id: Long, withSubtasks: Boolean) = viewModelScope.launch { repo.completeTask(id, withSubtasks) }
    fun uncomplete(id: Long) = viewModelScope.launch { repo.uncompleteTask(id) }
    fun toggleSubtask(id: Long) = viewModelScope.launch { repo.toggleSubtask(id) }
    fun delete(id: Long) = viewModelScope.launch { repo.deleteTask(id) }
    fun duplicate(id: Long) = viewModelScope.launch { repo.duplicate(id) }
    fun postpone(id: Long) = viewModelScope.launch { repo.postponeOneDay(id) }
    fun reschedule(id: Long, date: LocalDate?, minutes: Int?) =
        viewModelScope.launch { repo.reschedule(id, date, minutes) }

    fun addCategory(name: String, then: (Long) -> Unit = {}) =
        viewModelScope.launch { then(repo.addCategory(name)) }
    fun deleteCategory(id: Long) = viewModelScope.launch { repo.deleteCategory(id) }

    fun addHabit(name: String) = viewModelScope.launch { repo.addHabit(name) }
    fun deleteHabit(id: Long) = viewModelScope.launch { repo.deleteHabit(id) }
    fun setHabit(id: Long, day: LocalDate, done: Boolean) = viewModelScope.launch { repo.setHabitDone(id, day, done) }

    fun rescheduleAll() = viewModelScope.launch { repo.rescheduleAll() }
}
