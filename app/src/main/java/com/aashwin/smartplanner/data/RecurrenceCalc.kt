package com.aashwin.smartplanner.data

import java.time.LocalDate

object RecurrenceCalc {
    /** One step forward from [from] according to the rule. */
    fun advance(from: LocalDate, type: Int, interval: Int, days: Int): LocalDate = when (type) {
        Recurrence.DAILY -> from.plusDays(1)
        Recurrence.EVERY_N_DAYS -> from.plusDays(interval.coerceAtLeast(1).toLong())
        Recurrence.WEEKLY -> from.plusWeeks(1)
        Recurrence.MONTHLY -> from.plusMonths(1)
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

    /** Next occurrence after [current] that is on or after [today]. */
    fun nextOccurrence(task: TaskEntity, today: LocalDate): LocalDate? {
        if (!task.isRecurring) return null
        val base = task.date ?: today.minusDays(1)
        var next = advance(base, task.recType, task.recInterval, task.recDays)
        var guard = 0
        while (next.isBefore(today) && guard++ < 5000) {
            next = advance(next, task.recType, task.recInterval, task.recDays)
        }
        return next
    }

    /** First valid date for a new recurring task starting at [start]. */
    fun firstOnOrAfter(start: LocalDate, type: Int, days: Int): LocalDate {
        if (type != Recurrence.WEEKDAYS || days == 0) return start
        var d = start
        while (days and (1 shl (d.dayOfWeek.value - 1)) == 0) d = d.plusDays(1)
        return d
    }
}
