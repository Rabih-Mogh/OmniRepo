package app.spark.core

/**
 * The day's goal and the streak. A day is met once [goal] reels have been watched and one
 * lesson finished; the lesson isn't asked for when there is none left to take. Days are
 * counted from 1970, so the next day is the next number.
 */
data class Days(
    val day: Long = 0,
    val reels: Int = 0,
    val lesson: Boolean = false,
    val streak: Int = 0,
    /** The last day the goal was met. */
    val met: Long = NEVER,
) {
    /** The same record on [today]: a new day starts from nothing. */
    fun on(today: Long): Days = if (day == today) this else copy(day = today, reels = 0, lesson = false)

    /** The streak as it stands on [today]: a day missed ends it. */
    fun streakOn(today: Long): Int = if (met >= today - 1) streak else 0

    fun metOn(today: Long): Boolean = met == today

    fun watched(today: Long, goal: Int, lessonsLeft: Boolean): Days =
        on(today).let { it.copy(reels = it.reels + 1) }.settle(today, goal, lessonsLeft)

    fun learned(today: Long, goal: Int, lessonsLeft: Boolean): Days =
        on(today).copy(lesson = true).settle(today, goal, lessonsLeft)

    private fun settle(today: Long, goal: Int, lessonsLeft: Boolean): Days =
        if (met != today && reels >= goal && (lesson || !lessonsLeft)) copy(streak = streakOn(today) + 1, met = today) else this

    companion object {
        const val NEVER = Long.MIN_VALUE / 2
    }
}
