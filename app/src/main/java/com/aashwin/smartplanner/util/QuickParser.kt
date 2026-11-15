package com.aashwin.smartplanner.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Small natural-language parser for quick add, e.g. "Study Python today at 7 PM",
 * "Submit record 28 Nov", "Lab exam on 2/12 at 9:30 am", "Pay fees by friday".
 * It never guesses AM/PM: "at 7" is reported as [Result.ambiguousHour] so the UI can ask.
 * Numeric dates are day/month (Indian format).
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

    private const val MONTHS =
        "january|jan|february|feb|march|mar|april|apr|may|june|jun|july|jul|august|aug|september|sept|sep|october|oct|november|nov|december|dec"

    private fun monthNumber(word: String): Int = when (word.lowercase().take(3)) {
        "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4; "may" -> 5; "jun" -> 6
        "jul" -> 7; "aug" -> 8; "sep" -> 9; "oct" -> 10; "nov" -> 11; else -> 12
    }

    /** A date without a year that has already passed means next year. */
    private fun dateOf(day: Int, month: Int, year: Int?, today: LocalDate): LocalDate? = try {
        if (year != null) LocalDate.of(if (year < 100) 2000 + year else year, month, day)
        else LocalDate.of(today.year, month, day).let { if (it.isBefore(today)) it.plusYears(1) else it }
    } catch (_: Exception) { null }

    fun parse(input: String, today: LocalDate = LocalDate.now()): Result {
        var text = " " + input.trim() + " "
        var date: LocalDate? = null
        var minutes: Int? = null
        var ambHour: Int? = null
        var ambMin = 0

        fun cut(r: Regex): MatchResult? {
            val m = r.find(text) ?: return null
            text = text.replaceRange(m.range, " ") // keep a space so neighbouring words stay apart
            return m
        }
        /** Like [cut], but only removes the words if they really make a date. */
        fun takeDate(r: Regex, f: (MatchResult) -> LocalDate?): LocalDate? {
            val m = r.find(text) ?: return null
            val d = f(m) ?: return null
            text = text.replaceRange(m.range, " ")
            return d
        }
        val ic = RegexOption.IGNORE_CASE

        // ---- date ----
        when {
            cut(Regex("""\s(?:on\s|by\s)?today\s""", ic)) != null -> date = today
            cut(Regex("""\s(?:on\s|by\s)?tonight\s""", ic)) != null -> date = today
            cut(Regex("""\s(?:on\s|by\s)?(?:tomorrow|tmrw|tmr)\s""", ic)) != null -> date = today.plusDays(1)
            cut(Regex("""\s(?:on\s|by\s)?day after tomorrow\s""", ic)) != null -> date = today.plusDays(2)
            cut(Regex("""\snext week\s""", ic)) != null -> date = today.plusWeeks(1)
            else -> {
                // 28 Nov / 28th November 2026
                date = takeDate(Regex("""\s(?:on\s|by\s)?(\d{1,2})(?:st|nd|rd|th)?\s(?:of\s)?($MONTHS)(?:,?\s(\d{4}))?\s""", ic)) {
                    dateOf(it.groupValues[1].toInt(), monthNumber(it.groupValues[2]), it.groupValues[3].toIntOrNull(), today)
                }
                // Nov 28 / November 28th, 2026
                if (date == null) date = takeDate(Regex("""\s(?:on\s|by\s)?($MONTHS)\s(\d{1,2})(?:st|nd|rd|th)?(?:,?\s(\d{4}))?\s""", ic)) {
                    dateOf(it.groupValues[2].toInt(), monthNumber(it.groupValues[1]), it.groupValues[3].toIntOrNull(), today)
                }
                // 28/11, 28-11-2026 (day/month)
                if (date == null) date = takeDate(Regex("""\s(?:on\s|by\s)?(\d{1,2})[/-](\d{1,2})(?:[/-](\d{2,4}))?\s""")) {
                    dateOf(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toIntOrNull(), today)
                }
                // on the 28th: this month, or next month if already past
                if (date == null) date = takeDate(Regex("""\s(?:on\s|by\s)(?:the\s)?(\d{1,2})(?:st|nd|rd|th)\s""", ic)) {
                    val d = it.groupValues[1].toInt()
                    dateOf(d, today.monthValue, today.year, today)?.let { x -> if (x.isBefore(today)) dateOf(d, today.plusMonths(1).monthValue, today.plusMonths(1).year, today) else x }
                }
                if (date == null) cut(Regex("""\sin\s(\d{1,3})\sdays?\s""", ic))?.let {
                    date = today.plusDays(it.groupValues[1].toLong())
                }
                if (date == null) cut(Regex("""\s(?:on\s|by\s|next\s|this\s)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)\s""", ic))?.let {
                    val dow = days.getValue(it.groupValues[1].lowercase())
                    date = today.with(TemporalAdjusters.next(dow))
                }
            }
        }

        // ---- time ----
        val withMeridiem = cut(Regex("""\s(?:at\s|by\s)?(\d{1,2})(?:[:.](\d{2}))?\s?(am|pm|a\.m\.|p\.m\.)\s""", ic))
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
            val hhmm = cut(Regex("""\s(?:at\s|by\s)?(\d{1,2}):(\d{2})\s"""))
            val atH = hhmm ?: cut(Regex("""\s(?:at|by)\s(\d{1,2})\s""", ic))
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
                    cut(Regex("""\s(?:in\sthe\s)?morning\s""", ic)) != null -> minutes = 9 * 60
                    cut(Regex("""\s(?:in\sthe\s)?evening\s""", ic)) != null -> minutes = 18 * 60
                    cut(Regex("""\s(?:at\s)?noon\s""", ic)) != null -> minutes = 12 * 60
                }
            }
        }
        if ((minutes != null || ambHour != null) && date == null) date = today

        val title = text.trim().replace(Regex("""\s+"""), " ")
            .replace(Regex("""\s(at|on|by)$""", ic), "")
            .replaceFirstChar { it.uppercase() }
        return Result(title.ifBlank { input.trim() }, date, minutes, ambHour, ambMin)
    }
}
