package app.spark

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.spark.data.Library
import app.spark.data.Store
import app.spark.ui.FeedState
import app.spark.ui.FeedState.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/** The feed as it is walked through: what follows what, where the day's stop goes, and what is written down on the way. */
@RunWith(AndroidJUnit4::class)
class FeedStateTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val today = 20_500L
    private var now = 0L
    private lateinit var feed: FeedState

    @Before
    fun setUp() {
        Library.base = null
        Library.load(context)
        Store.today = { today }
        Store.load(context)
        FeedState.clock = { now }
        feed = FeedState(Random(3))
    }

    /** Arrives on page [index] and stays for [seconds]. */
    private fun watch(index: Int, seconds: Int = 20) {
        feed.arrive(index)
        now += seconds * 1000L
    }

    private fun reel(index: Int) = (feed.pages[index] as Page.Show).reel

    @Test
    fun `the page after the one on screen is always ready, and no reel comes twice`() {
        for (i in 0 until 8) {
            watch(i)
            assertEquals(i + 2, feed.pages.size)
        }
        val reels = feed.pages.filterIsInstance<Page.Show>().map { it.reel.id }
        assertEquals(reels.size, reels.toSet().size)
    }

    @Test
    fun `how a reel went is written down once, when it is left`() {
        watch(0)
        assertTrue(Store.kept.value.seen.isEmpty())
        watch(1)
        assertEquals(setOf(reel(0).id), Store.kept.value.seen)
        assertEquals(1, Store.kept.value.days.reels)
        assertTrue(Store.kept.value.taste.topics.getValue(reel(0).topic).value > 0)

        // Leaving the app and coming back to the same reel doesn't count it again.
        feed.leave()
        feed.leave()
        feed.arrive(1)
        assertEquals(2, Store.kept.value.days.reels)
        assertEquals(2, Store.kept.value.taste.topics.values.sumOf { it.count })
    }

    @Test
    fun `a reel flicked past is seen but doesn't count, and one refused counts against its subject`() {
        watch(0, seconds = 0)
        watch(1)
        assertEquals(0, Store.kept.value.days.reels)
        assertTrue(Store.kept.value.taste.topics.getValue(reel(0).topic).value < 0)

        feed.leave(less = true)
        assertEquals(setOf(reel(0).id, reel(1).id), Store.kept.value.seen)
        assertTrue(Store.kept.value.taste.tags.getValue(reel(1).tags.first()).value < 0)
    }

    @Test
    fun `time away from the feed isn't counted, and a reel barely begun isn't taken for skipped`() {
        watch(0, seconds = 1)
        feed.pause()
        assertTrue(Store.kept.value.seen.isEmpty())
        now += 3_600_000
        feed.resume()
        now += 1000
        feed.leave()
        // Two seconds of looking, not an hour: a flick would have been -0.6, and a full watch 0.5.
        val reward = Store.kept.value.taste.topics.getValue(reel(0).topic).value / 0.2
        assertTrue("$reward", reward > 0 && reward < 0.2)
    }

    @Test
    fun `a reel that had its time is written down when the app is left`() {
        watch(0)
        feed.pause()
        assertEquals(setOf(reel(0).id), Store.kept.value.seen)
        assertEquals(1, Store.kept.value.days.reels)
        feed.resume()
        watch(1)
        assertEquals(1, Store.kept.value.days.reels)
    }

    @Test
    fun `going into the lesson counts for the reel`() {
        watch(0, seconds = 1)
        feed.toLesson()
        assertEquals(0.4, Store.kept.value.taste.topics.getValue(reel(0).topic).value / 0.2, 0.2)
        assertEquals(1, Store.kept.value.days.reels)
    }

    @Test
    fun `the stop comes right after the goal's last reel, once`() {
        Store.goal(5)
        for (i in 0 until 5) watch(i)
        assertEquals(Page.Done, feed.pages[5])
        watch(5)
        assertEquals(5, Store.kept.value.days.reels)
        // Swiping past it carries on with reels, and it doesn't come again today.
        for (i in 6 until 14) watch(i)
        assertEquals(1, feed.pages.count { it == Page.Done })
    }

    @Test
    fun `opened again with the goal reached, the stop comes first`() {
        Store.goal(5)
        for (i in 0..5) watch(i)
        val again = FeedState(Random(4))
        again.arrive(0)
        assertEquals(Page.Done, again.pages[0])
        assertTrue(again.pages[1] is Page.Show)
    }

    @Test
    fun `when every reel has been seen the feed says so, and carries on when more arrive`() {
        var i = 0
        while (feed.pages.lastOrNull() != Page.End) watch(i++)
        watch(i)
        assertEquals(40, Store.kept.value.seen.size)
        assertEquals(40, feed.pages.count { it is Page.Show })
        feed.extend()
        assertEquals(Page.End, feed.pages.last())
        assertFalse(feed.pages.dropLast(1).contains(Page.End))
    }
}
