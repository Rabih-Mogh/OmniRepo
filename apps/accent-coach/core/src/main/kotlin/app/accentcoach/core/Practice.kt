package app.accentcoach.core

import java.time.LocalDate

/**
 * What has been practised: how many recordings were scored on each day, and for each
 * lesson (by its id) the most items of its drill that were native in one go.
 */
data class Practice(val days: Map<LocalDate, Int> = emptyMap(), val drills: Map<String, Int> = emptyMap()) {

    /** One more recording scored on [day]. */
    fun recorded(day: LocalDate): Practice = copy(days = days + (day to (days[day] ?: 0) + 1))

    /** A drill of [lesson] finished with [native] of its items native; the best go is the one kept. */
    fun drilled(lesson: String, native: Int): Practice = copy(drills = drills + (lesson to maxOf(native, drills[lesson] ?: 0)))

    fun learned(lesson: Lesson): Boolean = Drills.learned(drills[lesson.id] ?: 0, lesson.items.size)

    /** The streak as it stands on [today]. */
    fun streak(today: LocalDate): Streak {
        fun counts(day: LocalDate) = (days[day] ?: 0) >= Streak.GOAL
        // Days in a row with the goal met, up to today; a day not yet finished doesn't break the run.
        var run = 0
        var day = if (counts(today)) today else today.minusDays(1)
        while (counts(day)) {
            run++
            day = day.minusDays(1)
        }
        // The longest such run there has been.
        var best = 0
        var length = 0
        var before: LocalDate? = null
        for (met in days.keys.filter(::counts).sorted()) {
            length = if (before != null && met == before.plusDays(1)) length + 1 else 1
            best = maxOf(best, length)
            before = met
        }
        return Streak(
            today = days[today] ?: 0,
            days = run,
            best = best,
            week = List(7) { today.minusDays((6 - it).toLong()) }.map { it to (days[it] ?: 0) },
        )
    }
}

/**
 * [today] is how many recordings have been scored today. [days] is how many days in a
 * row the goal was met, up to today or yesterday, and [best] the longest such run there
 * has been. [week] is the last seven days, oldest first, each with its recordings.
 */
data class Streak(val today: Int, val days: Int, val best: Int, val week: List<Pair<LocalDate, Int>>) {

    /** Whether today's goal is met: until it is, the run stands but hasn't grown. */
    val todayDone: Boolean get() = today >= GOAL

    companion object {
        /** How many scored recordings make a day count. */
        const val GOAL = 5
    }
}
