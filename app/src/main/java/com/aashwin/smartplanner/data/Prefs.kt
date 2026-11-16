package com.aashwin.smartplanner.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Local profile + preferences (no account needed). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("profile", Context.MODE_PRIVATE)

    private val _name = MutableStateFlow(sp.getString("name", "") ?: "")
    val name: StateFlow<String> = _name
    private val _theme = MutableStateFlow(sp.getInt("theme", THEME_SYSTEM))
    val theme: StateFlow<Int> = _theme
    private val _onboarded = MutableStateFlow(sp.getBoolean("onboarded", false))
    val onboarded: StateFlow<Boolean> = _onboarded

    /** Minutes before due for the reminder added to new timed tasks; -1 = none. */
    var defaultReminder: Int
        get() = sp.getInt("defaultReminder", 10)
        set(v) = sp.edit().putInt("defaultReminder", v).apply()

    var defaultSnooze: Int
        get() = sp.getInt("defaultSnooze", 10)
        set(v) = sp.edit().putInt("defaultSnooze", v).apply()

    /**
     * Hour your day really starts (default 4 AM). Until then, yesterday's repeating
     * tasks can still be completed, e.g. finishing "Study" at 12:30 AM.
     */
    var dayStartHour: Int
        get() = sp.getInt("dayStartHour", 4)
        set(v) = sp.edit().putInt("dayStartHour", v).apply()

    /** Morning summary of today's tasks (covers tasks that have a date but no time). */
    var summaryEnabled: Boolean
        get() = sp.getBoolean("summaryEnabled", true)
        set(v) = sp.edit().putBoolean("summaryEnabled", v).apply()

    var summaryMinutes: Int
        get() = sp.getInt("summaryMinutes", 8 * 60)
        set(v) = sp.edit().putInt("summaryMinutes", v).apply()

    var reliabilityDone: Boolean
        get() = sp.getBoolean("reliabilityDone", false)
        set(v) = sp.edit().putBoolean("reliabilityDone", v).apply()

    // ---- running focus session (saved, so it survives the app being closed) ----
    private val _focus = MutableStateFlow(readRun())
    val focus: StateFlow<FocusRun?> = _focus

    private fun readRun(): FocusRun? = try {
        sp.getString("focusRun", null)?.let { json ->
            val o = JSONObject(json)
            val arr = o.getJSONArray("steps")
            FocusRun(
                taskId = if (o.isNull("taskId")) null else o.getLong("taskId"),
                taskTitle = o.getString("taskTitle"),
                steps = (0 until arr.length()).map { i ->
                    val x = arr.getJSONObject(i)
                    FocusStep(if (x.isNull("id")) null else x.getLong("id"), x.getString("title"), x.getInt("minutes"))
                },
                index = o.getInt("index"), phase = o.getInt("phase"), phaseEnd = o.getLong("phaseEnd"),
                pausedLeft = o.getLong("pausedLeft"), runningSince = o.getLong("runningSince"),
                stepFocusedMs = o.getLong("stepFocusedMs"), stepStartedAt = o.getLong("stepStartedAt"),
            )
        }
    } catch (_: Exception) { null }

    fun setFocus(r: FocusRun?) {
        if (r == null) sp.edit().remove("focusRun").apply()
        else {
            val o = JSONObject()
            o.put("taskId", r.taskId ?: JSONObject.NULL); o.put("taskTitle", r.taskTitle)
            o.put("steps", JSONArray().apply {
                r.steps.forEach { put(JSONObject().put("id", it.subtaskId ?: JSONObject.NULL).put("title", it.title).put("minutes", it.minutes)) }
            })
            o.put("index", r.index); o.put("phase", r.phase); o.put("phaseEnd", r.phaseEnd)
            o.put("pausedLeft", r.pausedLeft); o.put("runningSince", r.runningSince)
            o.put("stepFocusedMs", r.stepFocusedMs); o.put("stepStartedAt", r.stepStartedAt)
            sp.edit().putString("focusRun", o.toString()).apply()
        }
        _focus.value = r
    }

    // ---- start alert sound ----
    /** "file:/path" (your own file, copied into the app) or a phone sound URI; null = phone's default alarm. */
    var startSound: String?
        get() = sp.getString("startSound", null) ?: sp.getString("alarmTone", null)
        set(v) = sp.edit().putString("startSound", v).apply()

    var startSoundName: String?
        get() = sp.getString("startSoundName", null)
        set(v) = sp.edit().putString("startSoundName", v).apply()

    /** How long the start sound plays without an answer (minutes). */
    var ringMinutes: Int
        get() = sp.getInt("ringMinutes", 5)
        set(v) = sp.edit().putInt("ringMinutes", v).apply()

    var fadeIn: Boolean
        get() = sp.getBoolean("fadeIn", true)
        set(v) = sp.edit().putBoolean("fadeIn", v).apply()

    // ---- focus mode: blocked apps, Do Not Disturb, motivation lines ----
    var blockedApps: Set<String>
        get() = sp.getStringSet("blockedApps", null)?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("blockedApps", v).apply()

    fun allowAppUntil(pkg: String, until: Long) = sp.edit().putLong("allow_$pkg", until).apply()
    fun allowedUntil(pkg: String): Long = sp.getLong("allow_$pkg", 0L)

    var focusDnd: Boolean
        get() = sp.getBoolean("focusDnd", true)
        set(v) = sp.edit().putBoolean("focusDnd", v).apply()

    /** Do Not Disturb setting before focus turned it on (-1 = we didn't change it). */
    var dndBefore: Int
        get() = sp.getInt("dndBefore", -1)
        set(v) = sp.edit().putInt("dndBefore", v).apply()

    var quotesOn: Boolean
        get() = sp.getBoolean("quotesOn", true)
        set(v) = sp.edit().putBoolean("quotesOn", v).apply()

    var userQuotes: List<String>
        get() = sp.getString("userQuotes", "")!!.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("userQuotes", v.joinToString("\n")).apply()

    fun nextQuoteIndex(kind: String): Int {
        val i = sp.getInt("q_$kind", 0)
        sp.edit().putInt("q_$kind", i + 1).apply()
        return i
    }

    var focusMinutes: Int
        get() = sp.getInt("focusMinutes", 25)
        set(v) = sp.edit().putInt("focusMinutes", v).apply()

    fun setName(v: String) { sp.edit().putString("name", v).apply(); _name.value = v }
    fun setTheme(v: Int) { sp.edit().putInt("theme", v).apply(); _theme.value = v }
    fun setOnboarded() { sp.edit().putBoolean("onboarded", true).apply(); _onboarded.value = true }

    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
    }
}
