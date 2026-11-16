package com.aashwin.smartplanner.data

/** One step of a focus session (a checklist item, or the whole task when it has no steps). */
data class FocusStep(val subtaskId: Long?, val title: String, val minutes: Int)

/**
 * A running focus session. Saved on the phone, so it survives the app being closed;
 * the only thing that "runs" is one exact alarm for the end of the current timer.
 */
data class FocusRun(
    val taskId: Long?,
    val taskTitle: String,
    val steps: List<FocusStep>,
    val index: Int,
    val phase: Int,
    /** When the current timer ends (0 while paused or after time is up). */
    val phaseEnd: Long,
    /** Time left when paused (0 = not paused). */
    val pausedLeft: Long,
    /** When focus time last started counting (-1 = not counting). */
    val runningSince: Long,
    /** Focus time already banked for the current step. */
    val stepFocusedMs: Long,
    val stepStartedAt: Long,
) {
    val step: FocusStep get() = steps[index]
    val isPaused: Boolean get() = pausedLeft > 0
    val isLast: Boolean get() = index >= steps.lastIndex
    val next: FocusStep? get() = steps.getOrNull(index + 1)
    fun focusedMs(now: Long): Long = stepFocusedMs + if (runningSince >= 0) (now - runningSince).coerceAtLeast(0) else 0
    fun msLeft(now: Long): Long = when {
        isPaused -> pausedLeft
        phaseEnd > 0 -> (phaseEnd - now).coerceAtLeast(0)
        else -> 0
    }
}

/** All focus rules as pure functions (no Android), so they can be tested. */
object FocusMachine {
    const val STEP = 0
    const val TIME_UP = 1
    const val BREAK = 2
    const val BREAK_MS = 5 * 60_000L
    private fun ms(min: Int) = min.coerceAtLeast(1) * 60_000L

    fun start(taskId: Long?, title: String, steps: List<FocusStep>, now: Long): FocusRun {
        require(steps.isNotEmpty())
        return FocusRun(taskId, title, steps, 0, STEP, now + ms(steps[0].minutes), 0, now, 0, now)
    }

    /** The exact alarm fired: a step's time is up (wait for the user), or a break ended (next step starts). */
    fun timerEnded(r: FocusRun, now: Long): FocusRun = when {
        r.phase == STEP && !r.isPaused -> r.copy(phase = TIME_UP, phaseEnd = 0) // keeps counting focus time
        r.phase == BREAK -> nextStep(r, now)
        else -> r
    }

    fun pause(r: FocusRun, now: Long): FocusRun =
        if (r.phase != STEP || r.isPaused) r
        else r.copy(pausedLeft = (r.phaseEnd - now).coerceAtLeast(1_000), phaseEnd = 0, stepFocusedMs = r.focusedMs(now), runningSince = -1)

    fun resume(r: FocusRun, now: Long): FocusRun =
        if (!r.isPaused) r else r.copy(phaseEnd = now + r.pausedLeft, pausedLeft = 0, runningSince = now)

    /** "+5 / +10 min": more time for the current step. */
    fun extend(r: FocusRun, minutes: Int, now: Long): FocusRun = when {
        r.phase == TIME_UP -> r.copy(phase = STEP, phaseEnd = now + ms(minutes))
        r.phase == STEP && r.isPaused -> r.copy(pausedLeft = r.pausedLeft + ms(minutes))
        r.phase == STEP -> r.copy(phaseEnd = r.phaseEnd + ms(minutes))
        else -> r
    }

    /**
     * Current step finished. Returns the next state (a 5-minute break, or null when that was the
     * last step) and the focus time spent on the finished step.
     */
    fun stepDone(r: FocusRun, now: Long): Pair<FocusRun?, Long> {
        val spent = r.focusedMs(now)
        if (r.phase == BREAK) return r to 0
        if (r.isLast) return null to spent
        return r.copy(phase = BREAK, phaseEnd = now + BREAK_MS, pausedLeft = 0, runningSince = -1, stepFocusedMs = 0) to spent
    }

    fun skipBreak(r: FocusRun, now: Long): FocusRun = if (r.phase == BREAK) nextStep(r, now) else r

    private fun nextStep(r: FocusRun, now: Long): FocusRun {
        val i = (r.index + 1).coerceAtMost(r.steps.lastIndex)
        return r.copy(
            index = i, phase = STEP, phaseEnd = now + ms(r.steps[i].minutes), pausedLeft = 0,
            runningSince = now, stepFocusedMs = 0, stepStartedAt = now,
        )
    }

    /** Moment the next exact alarm is needed, or null (paused / waiting after time is up). */
    fun nextAlarm(r: FocusRun): Long? = if (r.phaseEnd > 0) r.phaseEnd else null
}
