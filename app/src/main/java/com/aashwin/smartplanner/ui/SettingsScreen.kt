package com.aashwin.smartplanner.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aashwin.smartplanner.alarm.Reliability
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Switch
import com.aashwin.smartplanner.alarm.Scheduler
import com.aashwin.smartplanner.data.Prefs
import com.aashwin.smartplanner.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
private fun <T> PickerRow(label: String, value: T, options: List<T>, show: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { open = true }) { Text(show(value)) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { o -> DropdownMenuItem(text = { Text(show(o)) }, onClick = { onPick(o); open = false }) }
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, why: String, ok: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error, if (ok) "Allowed" else "Not allowed",
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!ok) TextButton(onClick = onFix) { Text("Fix") }
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val prefs = vm.prefs
    val scope = rememberCoroutineScope()
    val name by prefs.name.collectAsState()
    val theme by prefs.theme.collectAsState()
    val categories by vm.categories.collectAsState()
    var defaultReminder by remember { mutableIntStateOf(prefs.defaultReminder) }
    var defaultSnooze by remember { mutableIntStateOf(prefs.defaultSnooze) }
    var dayStart by remember { mutableIntStateOf(prefs.dayStartHour) }
    var summaryOn by remember { mutableStateOf(prefs.summaryEnabled) }
    var summaryAt by remember { mutableIntStateOf(prefs.summaryMinutes) }
    var pickSummary by remember { mutableStateOf(false) }
    var toneName by remember { mutableStateOf(toneTitle(ctx, prefs.alarmToneUri)) }
    var addCat by remember { mutableStateOf(false) }
    var deleteCat by remember { mutableStateOf<Long?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val toneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
        if (uri != null) {
            prefs.alarmToneUri = uri.toString()
            toneName = toneTitle(ctx, uri.toString())
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val json = vm.repo.exportJson()
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }
                Toast.makeText(ctx, "Backup saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                } ?: return@launch
                vm.repo.importJson(json)
                Toast.makeText(ctx, "Backup restored", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "That file isn't a valid backup", Toast.LENGTH_LONG).show()
            }
        }
    }

    // permission states (refresh on resume)
    val notifOk = remember(tick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val exactOk = remember(tick) { Scheduler.canExact(ctx) }
    val fullScreenOk = remember(tick) {
        Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
    val batteryOk = remember(tick) {
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    }
    val pkgUri = Uri.parse("package:" + ctx.packageName)
    fun open(intent: Intent) {
        try { ctx.startActivity(intent) } catch (_: Exception) {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionTitle("Profile")
        OutlinedTextField(
            value = name, onValueChange = { prefs.setName(it) }, label = { Text("Your name") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text("Theme")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val opts = listOf(Prefs.THEME_SYSTEM to "System", Prefs.THEME_LIGHT to "Light", Prefs.THEME_DARK to "Dark")
            opts.forEachIndexed { i, (v, label) ->
                SegmentedButton(
                    selected = theme == v, onClick = { prefs.setTheme(v) },
                    shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                ) { Text(label) }
            }
        }

        SectionTitle("Reminders & alarms")
        PickerRow("Default reminder", defaultReminder, listOf(-1, 0, 5, 10, 15, 30, 60),
            { if (it < 0) "None" else Fmt.offset(it) }) { defaultReminder = it; prefs.defaultReminder = it }
        PickerRow("Snooze length", defaultSnooze, listOf(5, 10, 15, 30, 60),
            { if (it == 60) "1 hour" else "$it min" }) { defaultSnooze = it; prefs.defaultSnooze = it }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Morning summary")
                Text(
                    "Today's tasks in one notification, including ones with no time",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (summaryOn) TextButton(onClick = { pickSummary = true }) { Text(Fmt.time(summaryAt)) }
            Switch(checked = summaryOn, onCheckedChange = { summaryOn = it; prefs.summaryEnabled = it; vm.scheduleSummary() })
        }
        PickerRow("New day starts at", dayStart, (0..6).toList(), { Fmt.time(it * 60) }) {
            dayStart = it; prefs.dayStartHour = it; vm.rescheduleAll()
        }
        Text(
            "Until then, yesterday's repeating tasks can still be ticked off (e.g. finishing at 12:30 AM).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Alarm sound", Modifier.weight(1f))
            OutlinedButton(onClick = {
                toneLauncher.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, prefs.alarmToneUri?.let { Uri.parse(it) })
                )
            }) { Text(toneName) }
        }

        SectionTitle("Make alarms reliable")
        Text(
            "These let reminders and alarms work when the app is closed and after a restart.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PermissionRow("Notifications", "Required to remind you about your scheduled tasks.", notifOk) {
            if (Build.VERSION.SDK_INT >= 33 && !notifOk) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }
        PermissionRow("Exact alarms", "Lets alerts fire at the exact minute instead of a few minutes late.", exactOk) {
            if (Build.VERSION.SDK_INT >= 31) open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri))
        }
        PermissionRow("Full-screen alarms", "Shows the alarm screen over the lock screen.", fullScreenOk) {
            if (Build.VERSION.SDK_INT >= 34) open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri))
        }
        PermissionRow("Unrestricted battery", "Stops the phone from delaying alerts to save battery.", batteryOk) {
            @Suppress("BatteryLife")
            open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri))
        }
        Reliability.brand?.let { b ->
            // The phone gives no way to check this switch, so it is shown as a step to do once.
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("${b.autostartLabel} (${b.name})", fontWeight = FontWeight.Medium)
                    Text(b.steps, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { prefs.reliabilityDone = true; Reliability.openAutostart(ctx) }) { Text("Open") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Alarm, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Test it", fontWeight = FontWeight.Medium)
                Text(
                    "Sets a real alarm 2 minutes from now. Lock the phone and wait. For a full check, restart the phone first.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = {
                prefs.reliabilityDone = true
                vm.testAlarm { at -> Toast.makeText(ctx, "Test alarm set for ${Fmt.time(at.hour * 60 + at.minute)}", Toast.LENGTH_LONG).show() }
            }) { Text("Test") }
        }

        SectionTitle("Categories")
        categories.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, Modifier.weight(1f))
                IconButton(onClick = { deleteCat = c.id }) { Icon(Icons.Filled.Close, "Delete ${c.name}") }
            }
        }
        TextButton(onClick = { addCat = true }) { Text("+ Add category") }

        SectionTitle("Backup")
        Text(
            "Save all tasks, habits and settings to a file, and restore them later (for example on a new phone).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            TextButton(onClick = { exportLauncher.launch("smart-planner-backup-${LocalDate.now()}.json") }) { Text("Export backup") }
            TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) { Text("Restore backup") }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(
            "Smart Planner 1.2.1 · All data stays on this phone. No account, no internet needed.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(96.dp))
    }

    if (pickSummary) TimeDialog(initial = summaryAt, title = "Morning summary at", onDismiss = { pickSummary = false }) {
        summaryAt = it; prefs.summaryMinutes = it; vm.scheduleSummary(); pickSummary = false
    }
    if (addCat) TextInputDialog("New category", "Name", onDismiss = { addCat = false }) { vm.addCategory(it); addCat = false }
    deleteCat?.let { id ->
        val c = categories.firstOrNull { it.id == id }
        if (c != null) ConfirmDelete(c.name + " (its tasks are kept)", { vm.deleteCategory(id) }, { deleteCat = null })
    }
}

private fun toneTitle(ctx: android.content.Context, uri: String?): String {
    if (uri == null) return "Default alarm"
    return try {
        RingtoneManager.getRingtone(ctx, Uri.parse(uri))?.getTitle(ctx) ?: "Custom"
    } catch (_: Exception) { "Custom" }
}
