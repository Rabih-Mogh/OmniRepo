package app.spark.ui

import android.os.SystemClock
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import app.spark.core.Feed
import app.spark.core.Kind
import app.spark.core.Lesson
import app.spark.core.Reel
import app.spark.core.Watch
import app.spark.data.Library
import app.spark.data.Store
import kotlin.random.Random

/**
 * The feed as it has been shown since the app was opened. Pages already passed stay as
 * they were; the page after the one on screen is chosen when the user arrives, so what
 * they did with the last reel already counts.
 */
class FeedState(private val random: Random = Random(seed())) {

    sealed interface Page {
        data class Show(val reel: Reel) : Page

        /** The gentle stop: today's goal, and how far along it is. */
        data object Done : Page

        /** Every reel has been seen. */
        data object End : Page
    }

    val pages = mutableStateListOf<Page>()

    /** Reels that have run to their end, so coming back to one doesn't play it again. */
    val played = mutableStateMapOf<String, Boolean>()

    private var current = -1
    private var since = 0L // when the reel on screen came up, or came back
    private var spent = 0L // how long it had been up before the feed was last left
    private var paused = false
    private var settled = true // whether how the current reel went has been written down
    private var lesson = false
    private var stopped = Long.MIN_VALUE // the day the Done page went in

    /** The user has come to rest on page [index]. */
    fun arrive(index: Int) {
        if (pages.isEmpty()) extend()
        if (index == current || index !in pages.indices) return
        leave()
        current = index
        since = clock()
        spent = 0
        paused = false
        settled = false
        lesson = false
        extend()
    }

    /** Makes sure a page follows the one on screen, when there is anything to follow it with. */
    fun extend() {
        if (pages.lastIndex > current) return
        val kept = Store.kept.value
        val today = Store.today()
        val last = pages.lastOrNull()
        val reelsToday = kept.days.on(today).reels
        // The reel on screen will most likely count, which is what puts the stop right after the goal's last reel.
        val reached = if (last == null) reelsToday >= kept.goal else last is Page.Show && reelsToday + 1 >= kept.goal
        if (stopped != today && reached) {
            stopped = today
            pages += Page.Done
            return
        }
        val before = pages.mapNotNull { (it as? Page.Show)?.reel }
        val reel = Feed.next(Library.shelf.value.reels.values, kept.taste, kept.seen, before, kept.wishes, today, random)
        when {
            reel != null -> pages += Page.Show(reel)
            last != Page.End -> pages += Page.End
        }
    }

    /**
     * The reel on screen is being left for good: by swiping on, or for its lesson. How it went
     * is written down once; [less] is the user saying not for me.
     */
    fun leave(less: Boolean = false) {
        val reel = (pages.getOrNull(current) as? Page.Show)?.reel ?: return
        if (settled) return
        settled = true
        Store.watched(reel, Watch(share(reel), liked = reel.id in Store.kept.value.liked, lesson = lesson, less = less), lessonsLeft())
    }

    // How much of its running time the reel on screen has been looked at. Time away from the feed isn't counted.
    private fun share(reel: Reel): Double = (spent + if (paused) 0 else clock() - since) / runtime(reel).toDouble()

    /**
     * The feed is being left for now: for another screen, or because the app is. A reel that
     * has had most of its time is written down as watched. One barely begun is not, so it
     * isn't taken for skipped, and waits to be seen properly.
     */
    fun pause() {
        val reel = (pages.getOrNull(current) as? Page.Show)?.reel ?: return
        if (paused) return
        spent += clock() - since
        paused = true
        if (share(reel) >= 0.5) leave()
    }

    /** The feed is back on screen. */
    fun resume() {
        if (!paused) return
        paused = false
        since = clock()
    }

    /** The user is going from the reel on screen into its lesson. */
    fun toLesson() {
        lesson = true
        leave()
    }

    companion object {
        /** The running clock, in milliseconds, and what the feed's element of chance starts from. Tests set their own. */
        internal var clock: () -> Long = SystemClock::elapsedRealtime
        internal var seed: () -> Long = System::nanoTime

        const val HOOK_MS = 2200
        const val BEAT_MS = 2600
        const val TAKEAWAY_MS = 1800

        /** How long [reel] takes to show all it has, in milliseconds. */
        fun runtime(reel: Reel): Int = HOOK_MS + BEAT_MS * reel.beats.size + if (reel.kind == Kind.PUZZLE) 0 else TAKEAWAY_MS

        /** Whether there is a lesson the user hasn't finished. */
        fun lessonsLeft(): Boolean = Library.shelf.value.lessons.keys.any { it !in Store.kept.value.lessons }

        /** The lesson to offer when the day needs one: an unfinished one, on the subject that holds the user most. */
        fun suggested(): Lesson? {
            val kept = Store.kept.value
            return Library.shelf.value.lessons.values
                .filter { it.id !in kept.lessons }
                .maxByOrNull { kept.taste.topics[it.topic]?.value ?: 0.0 }
        }
    }
}
