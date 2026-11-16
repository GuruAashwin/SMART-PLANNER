package com.aashwin.smartplanner.data

/**
 * Habit numbers. All days are epoch days in the user's *logical* day
 * (before the day-start hour, default 4 AM, it is still yesterday).
 *
 * Today is "in progress": it only counts for or against you once it is done
 * (or once the day is over), so a perfect record always shows 100%.
 */
data class HabitStats(
    val current: Int,
    val best: Int,
    val doneToday: Boolean,
    /** Streak is alive but today isn't done yet. */
    val atRisk: Boolean,
    val week: Int,
    val weekDays: Int,
    /** null = too new to have a percentage yet. */
    val monthPercent: Int?,
)

object HabitCalc {
    /**
     * @param logDays days the habit was done
     * @param createdDay day the habit was created
     * @param today the logical today
     * @param todayDayOfWeek 1 = Monday .. 7 = Sunday
     */
    fun stats(logDays: Collection<Long>, createdDay: Long, today: Long, todayDayOfWeek: Int): HabitStats {
        val days = logDays.filter { it <= today }.toSortedSet() // ignore future days (e.g. clock changes)
        val doneToday = today in days

        // Current streak: up to today if done, otherwise up to yesterday (still alive until the day ends).
        var d = if (doneToday) today else today - 1
        var current = 0
        while (d in days) { current++; d-- }

        var best = 0; var run = 0; var prev = Long.MIN_VALUE
        for (x in days) { run = if (x == prev + 1) run + 1 else 1; best = maxOf(best, run); prev = x }

        // The habit "exists" from its creation, or from the earliest day logged (backfilled days count).
        val start = minOf(createdDay, days.firstOrNull() ?: createdDay)
        val lastCounted = if (doneToday) today else today - 1

        val monday = today - (todayDayOfWeek - 1)
        val weekStart = maxOf(monday, start)
        val weekDays = (lastCounted - weekStart + 1).coerceAtLeast(0).toInt()
        val week = days.count { it in weekStart..today }

        val winStart = maxOf(today - 29, start)
        val winLen = lastCounted - winStart + 1
        val inWindow = days.count { it in winStart..today }
        val percent = if (winLen <= 0) null else (inWindow * 100 / winLen).toInt().coerceAtMost(100)

        return HabitStats(
            current = current, best = maxOf(best, current), doneToday = doneToday,
            atRisk = !doneToday && current > 0,
            week = week, weekDays = weekDays, monthPercent = percent,
        )
    }
}
