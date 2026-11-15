package com.aashwin.smartplanner.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val timeF = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val dayF = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val longDayF = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)
    private val monthF = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    fun time(t: LocalTime): String = t.format(timeF)
    fun time(minutes: Int): String = time(LocalTime.of(minutes / 60, minutes % 60))
    fun longDay(d: LocalDate): String = d.format(longDayF)
    fun month(d: LocalDate): String = d.format(monthF)

    fun day(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(dayF)
    }

    fun due(d: LocalDate?, minutes: Int?): String {
        if (d == null) return "No date"
        val day = day(d)
        return if (minutes != null) "$day · ${time(minutes)}" else day
    }

    fun offset(min: Int): String = when {
        min == 0 -> "At due time"
        min % 1440 == 0 -> "${min / 1440} day${if (min / 1440 > 1) "s" else ""} before"
        min % 60 == 0 -> "${min / 60} hour${if (min / 60 > 1) "s" else ""} before"
        else -> "$min min before"
    }

    fun greeting(now: LocalTime = LocalTime.now()): String = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
}
