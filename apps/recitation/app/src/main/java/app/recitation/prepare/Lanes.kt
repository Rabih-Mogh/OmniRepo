package app.recitation.prepare

/**
 * The two lanes of the background work, and what each takes up next. Fetching a
 * recording's sound takes the connection and hardly any of the phone's strength;
 * listening to it takes all of that strength and no connection. So the two go on side by
 * side: the sounds are fetched one after another, and meanwhile those already on the phone
 * are listened to, one at a time. In both lanes the recording the reader has open goes
 * first, and after it the rest in the order they stand in.
 */
object Lanes {
    /**
     * A recording that still has something to be done to it. [hasSound] is whether its sound
     * is on the phone, all of it; [needsFetched] whether something else it can't be
     * listened to without is still to be fetched: its surah's words, the speech model or
     * the engine.
     */
    class Waiting(val id: String, val hasSound: Boolean, val needsFetched: Boolean = false)

    /**
     * The recording whose sound is fetched next; null when there is none to fetch, or none
     * that can be: there is no connection, or YouTube has [refused] this phone for now.
     */
    fun toFetch(waiting: List<Waiting>, open: String?, online: Boolean, refused: Boolean): String? {
        if (!online || refused) return null
        val without = waiting.filter { !it.hasSound }
        return (without.firstOrNull { it.id == open } ?: without.firstOrNull())?.id
    }

    /**
     * The recording listened to next; null when none can be yet. Not the one [fetching],
     * whose sound has only just arrived and is still being looked over; and with no
     * connection, only those that need nothing more fetched.
     */
    fun toHear(waiting: List<Waiting>, open: String?, online: Boolean, fetching: String?): String? {
        val here = waiting.filter { it.hasSound && it.id != fetching && (online || !it.needsFetched) }
        return (here.firstOrNull { it.id == open } ?: here.firstOrNull())?.id
    }
}
