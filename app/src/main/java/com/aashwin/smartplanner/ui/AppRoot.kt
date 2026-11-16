package com.aashwin.smartplanner.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.time.LocalDate
import kotlinx.coroutines.flow.collectLatest

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("tasks", "Tasks", Icons.Filled.Checklist),
    Tab("habits", "Habits", Icons.Filled.Loop),
    Tab("calendar", "Calendar", Icons.Filled.CalendarMonth),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

@Composable
fun AppRoot(vm: MainViewModel, openTaskId: Long?, onOpenHandled: () -> Unit, openSpecial: String? = null, onSpecialHandled: () -> Unit = {}) {
    val onboarded by vm.prefs.onboarded.collectAsState()
    if (!onboarded) {
        Onboarding(vm)
        return
    }
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"
    var quickAdd by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<Draft?>(null) }
    var calendarDay by remember { mutableStateOf(LocalDate.now()) }
    val snackbar = remember { SnackbarHostState() }

    // Completed / deleted / moved messages, with Undo where possible. A new one replaces the old.
    LaunchedEffect(Unit) {
        vm.messages.collectLatest { m ->
            val result = snackbar.showSnackbar(
                message = m.text,
                actionLabel = if (m.undo != null) "Undo" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) m.undo?.let { vm.undo(it) }
        }
    }

    LaunchedEffect(openTaskId) {
        if (openTaskId != null && openTaskId > 0) {
            nav.navigate("edit/$openTaskId")
            onOpenHandled()
        }
    }

    fun openEditor(id: Long) { draft = null; nav.navigate("edit/$id") }
    fun openFocus(taskId: Long) = nav.navigate("focus?taskId=$taskId") { launchSingleTop = true }

    // Opened from the widget (+ button) or the focus notification.
    LaunchedEffect(openSpecial) {
        when (openSpecial) {
            "quickadd" -> { quickAdd = true; onSpecialHandled() }
            "focus" -> { openFocus(-1L); onSpecialHandled() }
        }
    }
    fun openTab(r: String) = nav.navigate(r) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!route.startsWith("edit") && !route.startsWith("focus") && route != "sounds") {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { openTab(tab.route) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (route in listOf("home", "tasks", "calendar", "habits")) {
                FloatingActionButton(
                    onClick = { quickAdd = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.Add, if (route == "habits") "Add habit" else "Add task")
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { HomeScreen(vm, ::openEditor, onOpenSettings = { openTab("settings") }, onFocus = ::openFocus) }
            composable("tasks") { TasksScreen(vm, ::openEditor, onFocus = ::openFocus) }
            composable("habits") { HabitsScreen(vm) }
            composable("calendar") {
                CalendarScreen(vm, calendarDay, onSelect = { calendarDay = it }, onOpen = ::openEditor, onFocus = ::openFocus)
            }
            composable("settings") {
                SettingsScreen(vm, onSounds = { nav.navigate("sounds") }, onFocusMode = { nav.navigate("focusmode") })
            }
            composable("sounds") { SoundsScreen(vm, onBack = { nav.popBackStack() }) }
            composable("focusmode") { FocusModeScreen(vm, onBack = { nav.popBackStack() }) }
            composable(
                "focus?taskId={taskId}",
                arguments = listOf(navArgument("taskId") { type = NavType.LongType; defaultValue = -1L }),
            ) { entry ->
                FocusScreen(
                    vm, entry.arguments?.getLong("taskId"), onClose = { nav.popBackStack() },
                    onSetupBlocker = { nav.navigate("focusmode") },
                )
            }
            composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                EditorScreen(vm, id, draft, onClose = { nav.popBackStack() })
            }
        }
    }

    if (quickAdd) {
        if (route == "habits") {
            HabitDialog(
                initialName = "", initialReminder = null, isNew = true,
                onDismiss = { quickAdd = false }, onDelete = {},
                onSave = { name, rem -> vm.addHabit(name, rem); quickAdd = false },
            )
        } else {
            QuickAddDialog(
                vm,
                defaultDate = if (route == "calendar") calendarDay else null,
                onDismiss = { quickAdd = false },
                onMoreOptions = { d -> quickAdd = false; draft = d; nav.navigate("edit/0") },
            )
        }
    }
}

@Composable
private fun Onboarding(vm: MainViewModel) {
    var name by remember { mutableStateOf("") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.prefs.setOnboarded()
    }
    fun start() {
        vm.prefs.setName(name.trim())
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else vm.prefs.setOnboarded()
    }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Smart Planner", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Plan it once. Get reminded on time. Never lose an unfinished task.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Your name") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { start() }),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Next, allow notifications so reminders and alarms can reach you.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { start() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Get started", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
