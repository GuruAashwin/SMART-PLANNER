package com.aashwin.smartplanner.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aashwin.smartplanner.alarm.AlarmService
import com.aashwin.smartplanner.alarm.FocusMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(title: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            content = content,
        )
    }
}

@Composable
private fun Hint(text: String) = Text(
    text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(bottom = 6.dp),
)

// ---------------------------------------------------------------- sounds

private data class PhoneSound(val title: String, val uri: String)

private fun loadPhoneSounds(ctx: Context): List<PhoneSound> {
    val out = ArrayList<PhoneSound>()
    for (type in listOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE)) {
        try {
            val rm = RingtoneManager(ctx).apply { setType(type) }
            val c = rm.cursor
            while (c.moveToNext()) {
                val title = c.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: continue
                out += PhoneSound(title, rm.getRingtoneUri(c.position).toString())
            }
        } catch (_: Exception) { }
    }
    return out.distinctBy { it.uri }
}

/** Copies a picked audio file into the app (max 10 MB), so it keeps working if the original moves. */
private fun copySound(ctx: Context, uri: Uri): Pair<String, String>? {
    var name = "Your sound"
    var size = -1L
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            name = c.getString(0) ?: name
            size = if (c.isNull(1)) -1 else c.getLong(1)
        }
    }
    if (size > 10L * 1024 * 1024) return null
    val ext = name.substringAfterLast('.', "mp3").lowercase().take(4)
    val dest = File(ctx.filesDir, "start_sound.$ext")
    ctx.filesDir.listFiles()?.filter { it.name.startsWith("start_sound.") }?.forEach { it.delete() }
    ctx.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } } ?: return null
    if (dest.length() > 10L * 1024 * 1024) { dest.delete(); return null }
    return ("file:" + dest.absolutePath) to name
}

@Composable
fun SoundsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = vm.prefs
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf(prefs.startSound) }
    var choiceName by remember { mutableStateOf(prefs.startSoundName) }
    var ringMinutes by remember { mutableIntStateOf(prefs.ringMinutes) }
    var fadeIn by remember { mutableStateOf(prefs.fadeIn) }
    var sounds by remember { mutableStateOf<List<PhoneSound>>(emptyList()) }
    var playing by remember { mutableStateOf<String?>(null) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }

    fun stop() { player[0]?.let { try { it.stop() } catch (_: Exception) { }; it.release() }; player[0] = null; playing = null }
    fun preview(sound: String) {
        stop()
        try {
            player[0] = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(ctx, AlarmService.soundUri(sound))
                setOnCompletionListener { stop() }
                prepare(); start()
            }
            playing = sound
        } catch (_: Exception) { Toast.makeText(ctx, "Can't play that sound", Toast.LENGTH_SHORT).show() }
    }
    fun pick(sound: String?, name: String?) { choice = sound; choiceName = name; prefs.startSound = sound; prefs.startSoundName = name }

    DisposableEffect(Unit) { onDispose { stop() } }
    LaunchedEffect(Unit) { sounds = withContext(Dispatchers.IO) { loadPhoneSounds(ctx) } }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = withContext(Dispatchers.IO) { try { copySound(ctx, uri) } catch (_: Exception) { null } }
            if (r == null) Toast.makeText(ctx, "Choose an audio file up to 10 MB", Toast.LENGTH_LONG).show()
            else { pick(r.first, r.second); preview(r.first) }
        }
    }

    SubScreen("Start alert sound", onBack) {
        item {
            Column {
                Hint("Plays when it's time to start a task. Uses your alarm volume, so silent mode won't mute it.")
                SectionTitle("Your own sound")
                Hint("MP3, M4A, OGG or WAV, up to 10 MB. A short clip repeats; a song plays from the start.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { filePicker.launch(arrayOf("audio/*")) }) {
                        Icon(Icons.Filled.MusicNote, null); Spacer(Modifier.width(6.dp)); Text("Choose a file")
                    }
                    if (choice?.startsWith("file:") == true) {
                        Text(choiceName ?: "Your sound", Modifier.weight(1f).padding(start = 12.dp), fontWeight = FontWeight.Medium)
                        IconButton(onClick = { if (playing == choice) stop() else preview(choice!!) }) {
                            Icon(if (playing == choice) Icons.Filled.Stop else Icons.Filled.PlayArrow, "Preview")
                        }
                    }
                }
                SectionTitle("Ring for")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 10).forEach { m ->
                        FilterChip(selected = ringMinutes == m, onClick = { ringMinutes = m; prefs.ringMinutes = m }, label = { Text("$m min") })
                    }
                }
                Hint("If you don't answer, it stops and leaves a \"Missed start\" notification.")
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Start soft, get louder")
                        Hint("Rises to full volume over about 15 seconds")
                    }
                    Switch(checked = fadeIn, onCheckedChange = { fadeIn = it; prefs.fadeIn = it })
                }
                SectionTitle("Phone sounds")
                Row(Modifier.fillMaxWidth().clickable { pick(null, null) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = choice == null, onClick = { pick(null, null) })
                    Text("Phone's default alarm", Modifier.weight(1f))
                }
                if (sounds.isEmpty()) Hint("Loading your phone's sounds…")
            }
        }
        items(sounds, key = { it.uri }) { s ->
            Row(
                Modifier.fillMaxWidth().clickable { pick(s.uri, s.title); preview(s.uri) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = choice == s.uri, onClick = { pick(s.uri, s.title); preview(s.uri) })
                Text(s.title, Modifier.weight(1f))
                IconButton(onClick = { if (playing == s.uri) stop() else preview(s.uri) }) {
                    Icon(if (playing == s.uri) Icons.Filled.Stop else Icons.Filled.PlayArrow, "Preview ${s.title}")
                }
            }
        }
    }
}

// ---------------------------------------------------------------- focus mode

private class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap?)

private val COMMON_DISTRACTIONS = setOf(
    "com.instagram.android", "com.google.android.youtube", "com.facebook.katana", "com.snapchat.android",
    "com.twitter.android", "com.zhiliaoapp.musically", "com.reddit.frontpage", "in.mohalla.sharechat",
    "com.instagram.barcelona", "com.pinterest", "com.netflix.mediaclient", "in.startv.hotstar",
)

private fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { it.activityInfo.packageName to it }
        .distinctBy { it.first }
        .filter { it.first != ctx.packageName }
        .map { (pkg, ri) ->
            val icon: Drawable? = try { ri.loadIcon(pm) } catch (_: Exception) { null }
            AppEntry(pkg, ri.loadLabel(pm).toString(), try { icon?.toBitmap(96, 96)?.asImageBitmap() } catch (_: Exception) { null })
        }
        .sortedBy { it.label.lowercase() }
}

@Composable
private fun SetupRow(title: String, why: String, ok: Boolean, button: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error, if (ok) "Done" else "Needed",
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!ok) TextButton(onClick = onClick) { Text(button) }
    }
}

@Composable
fun FocusModeScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = vm.prefs
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val usageOk = remember(tick) { com.aashwin.smartplanner.blocker.BlockerService.hasUsageAccess(ctx) }
    val overlayOk = remember(tick) { Settings.canDrawOverlays(ctx) }
    val dndOk = remember(tick) { FocusMode.dndAllowed(ctx) }
    var blocked by remember { mutableStateOf(prefs.blockedApps) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var dnd by remember { mutableStateOf(prefs.focusDnd) }
    var quotesOn by remember { mutableStateOf(prefs.quotesOn) }
    var own by remember { mutableStateOf(prefs.userQuotes) }
    var newLine by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(ctx) } }
    fun setBlocked(v: Set<String>) { blocked = v; prefs.blockedApps = v }
    fun open(i: Intent) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    SubScreen("Focus mode", onBack) {
        item {
            Column {
                Hint("While you focus on any task, the apps you pick below are blocked and Do Not Disturb turns on. Breaks are free time.")
                SectionTitle("Set up the app blocker (once)")
                SetupRow(
                    "1. Usage access",
                    "Lets the app see which app you open during focus. Find Smart Planner and allow it.",
                    usageOk, "Open",
                ) { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                SetupRow(
                    "2. Display over other apps",
                    "Lets it show the Back to work screen on top of a blocked app.",
                    overlayOk, "Open",
                ) { open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))) }
                if (usageOk && overlayOk) Hint("Blocker is ready. It only runs during focus sessions and never reads your screen.")

                SectionTitle("Do Not Disturb")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Silence notifications while focusing")
                        Hint("Turned back to your normal setting when the session ends")
                    }
                    Switch(checked = dnd, onCheckedChange = { dnd = it; prefs.focusDnd = it })
                }
                if (dnd) SetupRow("Do Not Disturb access", "Needed so the app can switch it on and off for you.", dndOk, "Allow") {
                    open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }

                SectionTitle("Motivation lines")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Show lines during focus", Modifier.weight(1f))
                    Switch(checked = quotesOn, onCheckedChange = { quotesOn = it; prefs.quotesOn = it })
                }
                own.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("“$line”", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { own = own - line; prefs.userQuotes = own }) { Icon(Icons.Filled.Close, "Remove line") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newLine, onValueChange = { newLine = it.take(140) },
                        placeholder = { Text("Add your own line") }, modifier = Modifier.weight(1f), singleLine = true,
                    )
                    TextButton(onClick = {
                        if (newLine.isNotBlank()) { own = own + newLine.trim(); prefs.userQuotes = own; newLine = "" }
                    }) { Text("Add") }
                }

                SectionTitle("Blocked apps · ${blocked.size}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        setBlocked(blocked + apps.map { it.pkg }.filter { it in COMMON_DISTRACTIONS })
                    }) { Text("Select social & video apps") }
                    if (blocked.isNotEmpty()) TextButton(onClick = { setBlocked(emptySet()) }) { Text("Clear") }
                }
                if (apps.isEmpty()) Hint("Loading your apps…")
            }
        }
        items(apps.sortedByDescending { it.pkg in blocked }, key = { it.pkg }) { a ->
            val on = a.pkg in blocked
            Row(
                Modifier.fillMaxWidth().clickable { setBlocked(if (on) blocked - a.pkg else blocked + a.pkg) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (a.icon != null) Image(a.icon, null, Modifier.size(32.dp)) else Spacer(Modifier.size(32.dp))
                Text(a.label, Modifier.weight(1f).padding(horizontal = 12.dp))
                Checkbox(checked = on, onCheckedChange = { setBlocked(if (it) blocked + a.pkg else blocked - a.pkg) })
            }
        }
    }
}
