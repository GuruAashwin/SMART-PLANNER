package com.aashwin.smartplanner.data

import java.time.LocalDate

object RecurrenceCalc {
    /** One step forward from [from] according to the rule. [anchor] = day of month for monthly (0 = from's day). */
    fun advance(from: LocalDate, type: Int, interval: Int, days: Int, anchor: Int = 0): LocalDate = when (type) {
        Recurrence.DAILY -> from.plusDays(1)
        Recurrence.EVERY_N_DAYS -> from.plusDays(interval.coerceAtLeast(1).toLong())
        Recurrence.WEEKLY -> from.plusWeeks(1)
        Recurrence.MONTHLY -> {
            // Jan 31 -> Feb 28 -> Mar 31: the anchor day is kept, clamped to short months.
            val m = from.plusMonths(1)
            val day = if (anchor in 1..31) anchor else from.dayOfMonth
            m.withDayOfMonth(minOf(day, m.lengthOfMonth()))
        }
        Recurrence.WEEKDAYS -> {
            if (days == 0) from.plusWeeks(1) else {
                var d = from.plusDays(1)
                // dayOfWeek.value: Monday = 1 .. Sunday = 7
                while (days and (1 shl (d.dayOfWeek.value - 1)) == 0) d = d.plusDays(1)
                d
            }
        }
        else -> from
    }

    fun advance(from: LocalDate, t: TaskEntity): LocalDate = advance(from, t.recType, t.recInterval, t.recDays, t.recAnchor)

    /** Next occurrence after the task's date that is on or after [today]. */
    fun nextOccurrence(task: TaskEntity, today: LocalDate): LocalDate? {
        if (!task.isRecurring) return null
        val base = task.date ?: today.minusDays(1)
        var next = advance(base, task)
        var guard = 0
        while (next.isBefore(today) && guard++ < 5000) next = advance(next, task)
        return next
    }

    /** Dates of a repeating task from its date up to [until] (for the calendar). */
    fun occurrences(task: TaskEntity, until: LocalDate, max: Int = 400): List<LocalDate> {
        val start = task.date ?: return emptyList()
        if (!task.isRecurring) return emptyList()
        val out = ArrayList<LocalDate>()
        var d = advance(start, task)
        while (!d.isAfter(until) && out.size < max) { out.add(d); d = advance(d, task) }
        return out
    }

    /** First valid date for a new recurring task starting at [start]. */
    fun firstOnOrAfter(start: LocalDate, type: Int, days: Int): LocalDate {
        if (type != Recurrence.WEEKDAYS || days == 0) return start
        var d = start
        while (days and (1 shl (d.dayOfWeek.value - 1)) == 0) d = d.plusDays(1)
        return d
    }
}
