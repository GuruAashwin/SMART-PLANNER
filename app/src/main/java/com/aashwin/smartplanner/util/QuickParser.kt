package com.aashwin.smartplanner.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Small natural-language parser for quick add, e.g. "Study Python today at 7 PM".
 * It never guesses AM/PM: "at 7" is reported as [ambiguousHour] so the UI can ask.
 */
object QuickParser {
    data class Result(
        val title: String,
        val date: LocalDate?,
        val minutes: Int?,
        /** Hour (1..12) given without AM/PM; the UI must ask the user. */
        val ambiguousHour: Int? = null,
        val ambiguousMinute: Int = 0,
    )

    private val days = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
    )

    fun parse(input: String, today: LocalDate = LocalDate.now()): Result {
        var text = " " + input.trim() + " "
        var date: LocalDate? = null
        var minutes: Int? = null
        var ambHour: Int? = null
        var ambMin = 0

        fun cut(r: Regex): MatchResult? {
            val m = r.find(text) ?: return null
            text = text.removeRange(m.range).let { " $it " }
            return m
        }

        // ---- date ----
        when {
            cut(Regex("""\s(?:on\s)?today\s""", RegexOption.IGNORE_CASE)) != null -> date = today
            cut(Regex("""\s(?:on\s)?tonight\s""", RegexOption.IGNORE_CASE)) != null -> date = today
            cut(Regex("""\s(?:on\s)?(?:tomorrow|tmrw|tmr)\s""", RegexOption.IGNORE_CASE)) != null -> date = today.plusDays(1)
            else -> {
                cut(Regex("""\sin\s(\d{1,3})\sdays?\s""", RegexOption.IGNORE_CASE))?.let {
                    date = today.plusDays(it.groupValues[1].toLong())
                } ?: cut(Regex("""\s(?:on\s|next\s)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)\s""", RegexOption.IGNORE_CASE))?.let {
                    val dow = days.getValue(it.groupValues[1].lowercase())
                    date = today.with(TemporalAdjusters.next(dow))
                }
            }
        }

        // ---- time ----
        val withMeridiem = cut(Regex("""\s(?:at\s)?(\d{1,2})(?::(\d{2}))?\s?(am|pm|a\.m\.|p\.m\.)\s""", RegexOption.IGNORE_CASE))
        if (withMeridiem != null) {
            var h = withMeridiem.groupValues[1].toInt()
            val m = withMeridiem.groupValues[2].ifEmpty { "0" }.toInt()
            val pm = withMeridiem.groupValues[3].lowercase().startsWith("p")
            if (h in 1..12 && m in 0..59) {
                if (h == 12) h = 0
                if (pm) h += 12
                minutes = h * 60 + m
            }
        } else {
            val hhmm = cut(Regex("""\s(?:at\s)?(\d{1,2}):(\d{2})\s"""))
            val atH = hhmm ?: cut(Regex("""\sat\s(\d{1,2})\s"""))
            if (atH != null) {
                val h = atH.groupValues[1].toInt()
                val m = if (atH.groupValues.size > 2 && atH.groupValues[2].isNotEmpty()) atH.groupValues[2].toInt() else 0
                when {
                    h in 13..23 && m in 0..59 -> minutes = h * 60 + m
                    h == 0 && m in 0..59 -> minutes = m
                    h in 1..12 && m in 0..59 -> {
                        // "tonight at 7" is clearly PM
                        if (input.contains("tonight", ignoreCase = true) && h < 12) minutes = (h + 12) * 60 + m
                        else { ambHour = h; ambMin = m }
                    }
                }
            } else {
                when {
                    cut(Regex("""\s(?:in\sthe\s)?morning\s""", RegexOption.IGNORE_CASE)) != null -> minutes = 9 * 60
                    cut(Regex("""\s(?:in\sthe\s)?evening\s""", RegexOption.IGNORE_CASE)) != null -> minutes = 18 * 60
                    cut(Regex("""\snoon\s""", RegexOption.IGNORE_CASE)) != null -> minutes = 12 * 60
                }
            }
        }
        if ((minutes != null || ambHour != null) && date == null) date = today

        val title = text.trim().replace(Regex("""\s+"""), " ")
            .replace(Regex("""\s(at|on|by)$""", RegexOption.IGNORE_CASE), "")
            .replaceFirstChar { it.uppercase() }
        return Result(title.ifBlank { input.trim() }, date, minutes, ambHour, ambMin)
    }
}
