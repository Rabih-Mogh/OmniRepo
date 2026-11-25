package app.spark

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.spark.core.Kind
import app.spark.core.Lesson
import app.spark.core.Reel
import app.spark.core.Watch
import app.spark.data.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** What the app remembers about its user, and that it is still there after the app is closed. */
@RunWith(AndroidJUnit4::class)
class StoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val today = 20_500L

    private fun reel(id: String, topic: String = "maths") = Reel(id, today, topic, listOf("probability"), Kind.IDEA, id, listOf("beat"), "takeaway", null, null, null)
    private fun lesson(id: String) = Lesson(id, today, "maths", listOf("probability"), id, "summary", emptyList())

    @Before
    fun setUp() {
        Store.today = { today }
        Store.load(context)
    }

    @Test
    fun `a new install starts with nothing and a goal of ten`() {
        assertEquals(Store.Kept(), Store.kept.value)
        assertEquals(10, Store.kept.value.goal)
    }

    @Test
    fun `everything kept is still there when the app is opened again`() {
        Store.welcomed()
        Store.goal(5)
        Store.wish("  game   theory ")
        Store.wish("how transformers work")
        Store.like(reel("a"), true)
        Store.watched(reel("a"), Watch(1.0, liked = true), lessonsLeft = true)
        Store.watched(reel("b", "ideas"), Watch(0.05), lessonsLeft = true)
        Store.finished(lesson("l"), Store.Score(3, 4), lessonsLeft = false)
        val before = Store.kept.value

        Store.load(context)
        assertEquals(before, Store.kept.value)
        assertEquals(listOf("game theory", "how transformers work"), before.wishes)
        assertEquals(setOf("a", "b"), before.seen)
        assertEquals(Store.Score(3, 4), before.lessons["l"])
    }

    @Test
    fun `a reel watched counts towards the day and one flicked past doesn't`() {
        Store.watched(reel("a"), Watch(0.8), lessonsLeft = true)
        Store.watched(reel("b"), Watch(0.05), lessonsLeft = true)
        // The same reel left twice is one reel.
        Store.watched(reel("a"), Watch(0.8), lessonsLeft = true)
        val kept = Store.kept.value
        assertEquals(1, kept.days.reels)
        assertEquals(setOf("a", "b"), kept.seen)
        assertTrue(kept.taste.tags.getValue("probability").count == 3)
    }

    @Test
    fun `the reels and a lesson meet the day and start the streak`() {
        Store.goal(5)
        repeat(5) { Store.watched(reel("r$it"), Watch(1.0), lessonsLeft = true) }
        assertFalse(Store.kept.value.days.metOn(today))
        Store.finished(lesson("l"), Store.Score(1, 1), lessonsLeft = true)
        assertTrue(Store.kept.value.days.metOn(today))
        assertEquals(1, Store.kept.value.days.streakOn(today))
    }

    @Test
    fun `a wish is kept once, tidied, and can be taken back`() {
        Store.wish("Game theory")
        Store.wish("game theory")
        Store.wish("   ")
        assertEquals(listOf("Game theory"), Store.kept.value.wishes)
        Store.unwish("Game theory")
        assertTrue(Store.kept.value.wishes.isEmpty())
    }

    @Test
    fun `the first go at a lesson is the score that is kept`() {
        Store.finished(lesson("l"), Store.Score(1, 4), lessonsLeft = true)
        Store.finished(lesson("l"), Store.Score(4, 4), lessonsLeft = true)
        assertEquals(Store.Score(1, 4), Store.kept.value.lessons["l"])
    }

    @Test
    fun `what the library no longer has is forgotten`() {
        Store.watched(reel("a"), Watch(1.0), lessonsLeft = true)
        Store.watched(reel("b"), Watch(1.0), lessonsLeft = true)
        Store.like(reel("b"), true)
        Store.finished(lesson("l"), Store.Score(1, 1), lessonsLeft = true)
        Store.keepOnly(reels = setOf("a"), lessons = emptySet())
        val kept = Store.kept.value
        assertEquals(setOf("a"), kept.seen)
        assertTrue(kept.liked.isEmpty())
        assertTrue(kept.lessons.isEmpty())
    }
}
