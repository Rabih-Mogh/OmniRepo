package app.spark.core

/**
 * The day's goal and the streak. The goal is met once [goal] reels have been watched and
 * one lesson finished; the lesson isn't asked for when there is none left to take. The
 * streak asks for less: a day counts once [KEEPS] reels are watched or a lesson is
 * finished, and one missed day in a week is let go. Days are counted from 1970, so the
 * next day is the next number.
 */
data class Days(
    val day: Long = 0,
    val reels: Int = 0,
    val lesson: Boolean = false,
    val streak: Int = 0,
    /** The last day the goal was met. */
    val met: Long = NEVER,
    /** The last day that counted towards the streak. */
    val kept: Long = NEVER,
    /** The last missed day that was let go. */
    val rested: Long = NEVER,
) {
    /** The same record on [today]: a new day starts from nothing. */
    fun on(today: Long): Days = if (day == today) this else copy(day = today, reels = 0, lesson = false)

    /** The streak as it stands on [today]. One missed day leaves it standing, unless another was let go in the week before. */
    fun streakOn(today: Long): Int = when {
        kept >= today - 1 -> streak
        kept == today - 2 && today - 1 - rested >= REST_EVERY -> streak
        else -> 0
    }

    fun metOn(today: Long): Boolean = met == today

    fun watched(today: Long, goal: Int, lessonsLeft: Boolean): Days =
        on(today).let { it.copy(reels = it.reels + 1) }.settle(today, goal, lessonsLeft)

    fun learned(today: Long, goal: Int, lessonsLeft: Boolean): Days =
        on(today).copy(lesson = true).settle(today, goal, lessonsLeft)

    private fun settle(today: Long, goal: Int, lessonsLeft: Boolean): Days {
        var now = this
        if (kept != today && (lesson || reels >= minOf(KEEPS, goal))) {
            // Yesterday was missed and is let go: that is the week's one.
            val rest = if (kept == today - 2 && streakOn(today) > 0) today - 1 else rested
            now = now.copy(streak = streakOn(today) + 1, kept = today, rested = rest)
        }
        if (met != today && reels >= goal && (lesson || !lessonsLeft)) now = now.copy(met = today)
        return now
    }

    companion object {
        const val NEVER = Long.MIN_VALUE / 2

        /** The reels that make a day count towards the streak. */
        const val KEEPS = 3

        /** A missed day is let go once in this many days. */
        const val REST_EVERY = 7
    }
}
