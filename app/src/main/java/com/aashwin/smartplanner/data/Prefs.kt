package com.aashwin.smartplanner.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class FocusState(val taskId: Long?, val title: String, val startAt: Long, val endAt: Long, val isBreak: Boolean) {
    val totalMinutes: Int get() = ((endAt - startAt) / 60_000L).toInt()
}

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

    var alarmToneUri: String?
        get() = sp.getString("alarmTone", null)
        set(v) = sp.edit().putString("alarmTone", v).apply()

    // ---- running focus session (kept here so it survives the app being closed) ----
    private val _focus = MutableStateFlow(readFocus())
    val focus: StateFlow<FocusState?> = _focus

    private fun readFocus(): FocusState? {
        val end = sp.getLong("focusEnd", 0L)
        if (end == 0L) return null
        return FocusState(
            taskId = sp.getLong("focusTask", 0L).takeIf { it != 0L },
            title = sp.getString("focusTitle", "") ?: "",
            startAt = sp.getLong("focusStart", 0L),
            endAt = end,
            isBreak = sp.getBoolean("focusBreak", false),
        )
    }

    fun setFocus(f: FocusState?) {
        val e = sp.edit()
        if (f == null) e.putLong("focusEnd", 0L) else e
            .putLong("focusTask", f.taskId ?: 0L).putString("focusTitle", f.title)
            .putLong("focusStart", f.startAt).putLong("focusEnd", f.endAt).putBoolean("focusBreak", f.isBreak)
        e.apply()
        _focus.value = f
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
