package com.aashwin.smartplanner.util

import com.aashwin.smartplanner.data.Prefs

/**
 * Original motivation lines, one set per moment of a focus session.
 * They push against excuses, pride and wasted time; never against the person.
 * They rotate without repeating until the set is used up. Your own lines are mixed in.
 */
object Quotes {
    const val START = "start"
    const val BLOCKED = "blocked"
    const val TIME_UP = "timeup"
    const val DONE = "done"

    private val start = listOf(
        "They expect you to quit by step two. Prove them wrong.",
        "You said you'd do this. Was that a lie?",
        "Nobody remembers the guy who almost did it.",
        "Start now. The version of you that waits never wins.",
        "Somewhere a rival opened his books an hour ago.",
        "You don't need to feel ready. You need to begin.",
        "Talk is cheap. This hour is not.",
        "Every great result started with someone forcing the first step.",
        "Prove it to the one person you can't lie to.",
        "Comfort has kept you average long enough.",
    )
    private val blocked = listOf(
        "Your phone gets your best hours. Your future gets the leftovers.",
        "Every scroll is a vote for the version of you that loses.",
        "Instagram will still be there. Will your chance?",
        "They post highlights. You're building yours. Get back.",
        "Nobody ever scrolled their way to the top.",
        "The feed is designed to steal you. Don't let it.",
        "You came here to escape. Escape is how you stay where you are.",
        "Five more minutes of this is how a whole year disappears.",
        "Winners are offline right now. Join them.",
        "This app doesn't care about your rank. You should.",
    )
    private val timeUp = listOf(
        "You're not tired. You're bored. That's a different problem. Get back in.",
        "The exam won't care how tired you were.",
        "Finish it, or admit the excuse is stronger than you.",
        "Half done is the same as not done. Finish.",
        "This is the part where most people stop. You're not most people.",
        "Pain of discipline now, or pain of regret later. Choose.",
        "The last stretch decides who you are.",
        "Don't let a timer be braver than you.",
        "Quitting here would make every minute before it a waste.",
        "Push through. The weak ones already left.",
    )
    private val done = listOf(
        "That's one less thing they can say about you.",
        "Your future self just saw that. He's not embarrassed anymore.",
        "Somewhere someone is resting. You just pulled ahead.",
        "Done. That's what separates you from the talkers.",
        "Proof beats promises. You just made proof.",
        "Another brick. Keep building.",
        "You kept your word to yourself. That's rare.",
        "One step closer to the person they'll have to respect.",
        "Momentum is yours now. Don't hand it back.",
        "Good. Now do it again tomorrow.",
    )

    private fun set(kind: String) = when (kind) {
        BLOCKED -> blocked
        TIME_UP -> timeUp
        DONE -> done
        else -> start
    }

    /** Next line for this moment, or null if lines are turned off. */
    fun next(prefs: Prefs, kind: String): String? {
        if (!prefs.quotesOn) return null
        val all = set(kind) + prefs.userQuotes
        if (all.isEmpty()) return null
        // Walk the list with a step coprime to its size: every line appears once before any repeats.
        val i = prefs.nextQuoteIndex(kind)
        fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
        val stride = listOf(7, 5, 3, 11, 13, 1).first { gcd(it, all.size) == 1 }
        return all[(i * stride) % all.size]
    }
}
