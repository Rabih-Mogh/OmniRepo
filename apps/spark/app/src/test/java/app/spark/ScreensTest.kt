package app.spark

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.spark.core.Reel
import app.spark.core.Watch
import app.spark.data.Library
import app.spark.data.Store
import app.spark.ui.FeedState
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Opens the real screens on the JVM in English and in Arabic, with the starter pack the
 * app ships with, checks each shows that language and lays out from the matching side,
 * and saves a picture of each to app/build/screens.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val today = 20_500L
    private var now = 0L

    @Before
    fun setUp() {
        Library.base = null
        Library.load(context)
        Store.today = { today }
        Store.load(context)
        FeedState.clock = { now }
        FeedState.seed = { 11 }
    }

    /** The reel whose hook is on screen. */
    private fun onScreen(): Reel =
        Library.shelf.value.reels.values.single { compose.onAllNodesWithText(it.hook).fetchSemanticsNodes().isNotEmpty() }

    private fun swipeOn() {
        now += 20_000
        // From inside the reel: the edges of the screen aren't part of the feed.
        compose.onRoot().performTouchInput { swipeUp(startY = height * 0.8f, endY = height * 0.1f) }
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun firstScreenInEnglish() {
        open("en-welcome") {
            compose.onNodeWithText("What do you want to learn?").assertIsDisplayed()
            compose.onNodeWithText("Nothing yet.").assertIsDisplayed()
            compose.onNodeWithText("A subject, a question, a skill").performTextInput("game theory")
            compose.onNodeWithText("Add").performClick()
            compose.onNodeWithText("game theory").assertIsDisplayed()
            assertEquals(listOf("game theory"), Store.kept.value.wishes)
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun startGoesToTheFeed() {
        open("en-first-reel") {
            compose.onNodeWithText("Start").performClick()
            compose.waitForIdle()
            assertTrue(Store.kept.value.welcomed)
            compose.onNodeWithText("0/10").assertIsDisplayed()
            // The reel has run to its end, so its takeaway is up, unless it is a puzzle waiting to be asked.
            val reel = onScreen()
            if (reel.kind == app.spark.core.Kind.PUZZLE) compose.onNodeWithText("Show the answer").assertIsDisplayed()
            compose.onNodeWithText(reel.takeaway).assertExists()
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun feedInEnglish() {
        Store.welcomed()
        open("en-feed") {
            compose.onNodeWithText("Spark").assertIsDisplayed()
            val first = onScreen()
            compose.onNode(hasContentDescription("Like")).performClick()
            compose.onNode(hasContentDescription("Liked")).assertIsDisplayed()
            assertEquals(setOf(first.id), Store.kept.value.liked)

            swipeOn()
            val second = onScreen()
            assertNotEquals(first.id, second.id)
            assertEquals(setOf(first.id), Store.kept.value.seen)
            compose.onNodeWithText("1/10").assertIsDisplayed()
            // A liked reel watched to the end is the strongest sign there is.
            assertEquals(1.0 * 0.2, Store.kept.value.taste.topics.getValue(first.topic).value, 1e-9)

            compose.onNode(hasContentDescription("Not for me")).performClick()
            compose.waitForIdle()
            assertNotEquals(second.id, onScreen().id)
            assertTrue(Store.kept.value.taste.tags.getValue(second.tags.first()).value < 0.21)
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aReelLeadsToItsLesson() {
        Store.welcomed()
        open("en-lesson") {
            // On through the feed to the first reel with a lesson.
            var reel = onScreen()
            repeat(40) {
                if (reel.lesson != null) return@repeat
                swipeOn()
                reel = onScreen()
            }
            val lesson = Library.shelf.value.lessons.getValue(reel.lesson!!)
            compose.onNodeWithText("Take the lesson").performClick()
            compose.waitForIdle()
            compose.onNode(hasContentDescription("Close the lesson")).assertIsDisplayed()
            compose.onNode(hasContentDescription("Step 1 of ${lesson.steps.size}")).assertIsDisplayed()
            assertTrue(reel.id in Store.kept.value.seen)
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theDayStopsAtItsGoal() {
        Store.welcomed()
        Store.goal(5)
        Library.shelf.value.reels.values.take(5).forEach { Store.watched(it, Watch(1.0), lessonsLeft = true) }
        open("en-done-lesson") {
            compose.onNodeWithText("One lesson to go").assertIsDisplayed()
            compose.onNodeWithText("5/5").assertIsDisplayed()
            captureScreenRoboImage("build/screens/en-done.png")
            compose.onNodeWithText("Take a lesson").performClick()
            compose.waitForIdle()
            compose.onNode(hasContentDescription("Close the lesson")).assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h2600dp-420dpi")
    fun youInEnglish() {
        Store.welcomed()
        Store.wish("game theory")
        Library.shelf.value.reels.values.take(6).forEachIndexed { i, reel -> Store.watched(reel, Watch(if (i % 3 == 0) 0.05 else 1.0, liked = i % 2 == 1), lessonsLeft = true) }
        Store.finished(Library.shelf.value.lessons.values.first(), Store.Score(3, 4), lessonsLeft = true)
        open("en-you") {
            compose.onNode(hasContentDescription("Your progress and settings")).performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Streak").assertIsDisplayed()
            compose.onNodeWithText("5 of 10 reels").assertIsDisplayed()
            compose.onNodeWithText("Lesson done").assertIsDisplayed()
            compose.onNodeWithText("Daily goal").assertIsDisplayed()
            compose.onNodeWithText("game theory").assertIsDisplayed()
            compose.onNodeWithText("Send the list").assertIsDisplayed()
            compose.onNodeWithText("What Spark has learned").assertIsDisplayed()
            compose.onNodeWithText("Lessons").assertIsDisplayed()
            compose.onNodeWithText("3/4").assertIsDisplayed()
            compose.onNodeWithText("40 reels and 8 lessons, 34 reels not seen yet.").assertIsDisplayed()
            compose.onNodeWithText("Check for updates").assertIsDisplayed()
            assertTrue("the title starts from the left", !startsFromTheRight("Daily goal"))

            compose.onNodeWithText("5 reels").performClick()
            assertEquals(5, Store.kept.value.goal)
        }
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-420dpi")
    fun firstScreenInArabic() {
        open("ar-welcome") {
            compose.onNodeWithText("ماذا تريد أن تتعلّم؟").assertIsDisplayed()
            compose.onNodeWithText("ابدأ").assertIsDisplayed()
            assertTrue("the title starts from the right", startsFromTheRight("ماذا تريد أن تتعلّم؟"))
        }
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-420dpi")
    fun feedInArabic() {
        Store.welcomed()
        open("ar-feed") {
            compose.onNodeWithText("شرارة").assertIsDisplayed()
            assertTrue("the app's name starts from the right", startsFromTheRight("شرارة"))
            // The reel itself is in English, and reads from the left.
            assertTrue("the hook starts from the left", !startsFromTheRight(onScreen().hook))
            compose.onNode(hasContentDescription("أعجبني")).assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h2600dp-420dpi")
    fun youInArabic() {
        Store.welcomed()
        Store.wish("نظرية الألعاب")
        Library.shelf.value.reels.values.take(3).forEach { Store.watched(it, Watch(1.0), lessonsLeft = true) }
        open("ar-you") {
            compose.onNode(hasContentDescription("تقدّمك والإعدادات")).performClick()
            compose.waitForIdle()
            compose.onNodeWithText("الهدف اليومي").assertIsDisplayed()
            // Whatever digits the phone writes Arabic numbers with.
            compose.onNodeWithText(context.getString(R.string.you_today_reels, 3, 10)).assertIsDisplayed()
            compose.onNodeWithText("نظرية الألعاب").assertIsDisplayed()
            compose.onNodeWithText("أرسل القائمة").assertIsDisplayed()
            compose.onNodeWithText("البحث عن تحديثات").assertIsDisplayed()
            assertTrue("the title starts from the right", startsFromTheRight("الهدف اليومي"))
        }
    }

    private inline fun open(picture: String, check: () -> Unit) {
        ActivityScenario.launch<Activity>(Intent(context, MainActivity::class.java)).use {
            compose.waitForIdle()
            check()
            compose.waitForIdle()
            captureScreenRoboImage("build/screens/$picture.png")
        }
    }

    private fun startsFromTheRight(text: String): Boolean {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val node = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        return node.center.x > root.center.x
    }
}
