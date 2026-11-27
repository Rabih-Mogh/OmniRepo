package app.spark.core

/** Whether the feed needs new reels written for it: it is running out, or what it has is old. */
object Supply {
    /** How many days after its newest pack the feed counts as old: news has faded from it by then. */
    const val STALE_DAYS = 7L

    /**
     * True when fewer reels are left [unseen] than the day's [goal] asks for, or the newest
     * pack is from more than [STALE_DAYS] days before [today]. [newest] is that pack's day, counted from 1970.
     */
    fun low(unseen: Int, goal: Int, newest: Long, today: Long): Boolean = unseen < goal || today - newest > STALE_DAYS
}
