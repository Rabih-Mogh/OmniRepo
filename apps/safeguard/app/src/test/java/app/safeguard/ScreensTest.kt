package app.safeguard

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.safeguard.core.Change
import app.safeguard.core.Listed
import app.safeguard.core.Why
import app.safeguard.data.Store
import app.safeguard.ui.BlockActivity
import app.safeguard.ui.Words
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Opens the real screens on the JVM in English and in Arabic, checks each shows that
 * language and lays out from the matching side, walks from the first page into each of
 * the others, and saves a picture of each to app/build/screens.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The three limits a new install has, something on each list, and a few minutes of YouTube used. */
    @Before
    fun setUp() {
        Store.load(context)
        Store.ask(context, Change.Add(Listed.APP, "TikTok"))
        Store.ask(context, Change.Add(Listed.SITE, "reddit.com"))
        Store.ask(context, Change.Add(Listed.KEYWORD, "casino"))
        Store.spend(context, "youtube", 12 * 60_000L)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h1000dp-420dpi")
    fun firstPageInEnglish() {
        open<MainActivity>("en-main") {
            compose.onNodeWithText("Safeguard").assertIsDisplayed()
            // Nothing is switched on in a test, and the first page says so before anything else.
            compose.onNodeWithText("Setup").assertIsDisplayed()
            compose.onNodeWithText("Still to switch on: Accessibility, Device admin, Updates").assertIsDisplayed()
            compose.onNodeWithText("Time limits").assertIsDisplayed()
            compose.onNodeWithText("YouTube, Facebook, Instagram").assertIsDisplayed()
            compose.onNodeWithText("Blocked apps").assertIsDisplayed()
            compose.onNodeWithText("TikTok").assertIsDisplayed()
            compose.onNodeWithText("Blocked websites").assertIsDisplayed()
            compose.onNodeWithText("reddit.com").assertIsDisplayed()
            compose.onNodeWithText("Blocked words").assertIsDisplayed()
            compose.onNodeWithText("casino").assertIsDisplayed()
            compose.onNodeWithText("Locking").assertIsDisplayed()
            compose.onNodeWithText("Unlocked").assertIsDisplayed()
            compose.onNodeWithText("Waiting period: 1 day").assertIsDisplayed()
            compose.onNodeWithText("Check for updates").assertIsDisplayed()
            // What belongs to the other pages isn't here.
            compose.onNodeWithText("Add a limit").assertDoesNotExist()
            compose.onNodeWithText("Choose from this phone").assertDoesNotExist()
            assertTrue("the title starts from the left", !startsFromTheRight("Safeguard"))
        }
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h1000dp-420dpi")
    fun firstPageInArabic() {
        open<MainActivity>("ar-main") {
            compose.onNodeWithText("الحارس").assertIsDisplayed()
            compose.onNodeWithText("الإعداد").assertIsDisplayed()
            compose.onNodeWithText("حدود الوقت").assertIsDisplayed()
            compose.onNodeWithText("التطبيقات المحظورة").assertIsDisplayed()
            compose.onNodeWithText("المواقع المحظورة").assertIsDisplayed()
            compose.onNodeWithText("الكلمات المحظورة").assertIsDisplayed()
            compose.onNodeWithText("القفل").assertIsDisplayed()
            compose.onNodeWithText("غير مقفل").assertIsDisplayed()
            compose.onNodeWithText("البحث عن تحديثات").assertIsDisplayed()
            assertTrue("the title starts from the right", startsFromTheRight("الحارس"))

            page("حدود الوقت", "ar-limits", back = "رجوع") {
                compose.onNodeWithText("YouTube").assertIsDisplayed()
                compose.onNodeWithText("إضافة حدّ").assertIsDisplayed()
                assertTrue("the page's name starts from the right", startsFromTheRight("حدود الوقت"))
            }
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h1000dp-420dpi")
    fun eachKindOfRuleHasItsOwnPage() {
        open<MainActivity>("en-main-again") {
            page("Time limits", "en-limits") {
                compose.onNodeWithText("YouTube").assertIsDisplayed()
                compose.onNodeWithText("12 of 30 min used today").assertIsDisplayed()
                compose.onAllNodesWithText("Closed Mon–Fri", substring = true).onFirst().assertIsDisplayed()
                compose.onNodeWithText("Add a limit").assertIsDisplayed()
                compose.onNodeWithText("TikTok").assertDoesNotExist()
            }
            page("Blocked apps", "en-apps") {
                compose.onNodeWithText("TikTok").assertIsDisplayed()
                compose.onNodeWithText("Choose from this phone").assertIsDisplayed()
                compose.onNodeWithText("reddit.com").assertDoesNotExist()
            }
            page("Blocked websites", "en-sites") {
                compose.onNodeWithText("reddit.com").assertIsDisplayed()
                compose.onNodeWithText("example.com").assertIsDisplayed()
                compose.onNodeWithText("casino").assertDoesNotExist()
            }
            page("Blocked words", "en-words") {
                compose.onNodeWithText("casino").assertIsDisplayed()
                compose.onNodeWithText("Word or phrase").assertIsDisplayed()
                compose.onNodeWithText("reddit.com").assertDoesNotExist()
            }
            page("Locking", "en-lock") {
                compose.onNodeWithText("Unlocked").assertIsDisplayed()
                compose.onNodeWithText("Lock").assertIsDisplayed()
                compose.onNodeWithText("Waiting period").assertIsDisplayed()
                compose.onNodeWithText("7 days").assertIsDisplayed()
            }
            page("Setup", "en-setup") {
                compose.onNodeWithText("Safeguard isn't watching").assertIsDisplayed()
                compose.onNodeWithText("Make it a device admin app").assertIsDisplayed()
                compose.onNodeWithText("Allow updates").assertIsDisplayed()
            }
            // And back on the first page.
            compose.onNodeWithText("Blocked apps").assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h1000dp-420dpi")
    fun lockedWithChangesWaiting() {
        Store.ask(context, Change.Lock)
        Store.ask(context, Change.Unlock)
        Store.ask(context, Change.Drop(Listed.SITE, "reddit.com"))
        open<MainActivity>("en-main-locked") {
            compose.onNodeWithText("Locked").assertIsDisplayed()
            compose.onNodeWithText("Waiting: Unlock Safeguard, Stop blocking reddit.com").assertIsDisplayed()
            // Still blocked while it waits.
            compose.onNodeWithText("reddit.com").assertIsDisplayed()

            page("Locking", "en-locked") {
                compose.onNodeWithText("Locked").assertIsDisplayed()
                compose.onNodeWithText("Ask to unlock").assertIsDisplayed()
                compose.onNodeWithText("Waiting").assertIsDisplayed()
                compose.onNodeWithText("Unlock Safeguard").assertIsDisplayed()
                compose.onNodeWithText("Stop blocking reddit.com").assertIsDisplayed()
                compose.onAllNodesWithText("in 24 h 0 min").onFirst().assertIsDisplayed()
            }
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h1600dp-420dpi")
    fun aLimitOpensToBeChanged() {
        open<MainActivity>("en-limit") {
            compose.onNodeWithText("Time limits").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("YouTube").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Daily allowance").assertIsDisplayed()
            compose.onNodeWithText("30").assertIsDisplayed()
            compose.onNodeWithText("Closed hours").assertIsDisplayed()
            compose.onNodeWithText("youtube.com, youtu.be").assertIsDisplayed()
            compose.onNodeWithText("Save").assertIsDisplayed()
            compose.onNodeWithText("Remove").assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-420dpi")
    fun blockScreenInArabic() {
        val reason = Words.why(context, Why.Spent("YouTube", 30))
        open("ar-block", BlockActivity.intent(context, reason, leave = true)) {
            compose.onNodeWithText("أغلقه «الحارس»").assertIsDisplayed()
            compose.onNodeWithText(reason).assertIsDisplayed()
            compose.onNodeWithText("حسناً").assertIsDisplayed()
        }
    }

    private inline fun <reified A : Activity> open(picture: String, check: () -> Unit) =
        open(picture, Intent(context, A::class.java), check)

    private inline fun open(picture: String, intent: Intent, check: () -> Unit) {
        ActivityScenario.launch<Activity>(intent).use {
            compose.waitForIdle()
            check()
            compose.waitForIdle()
            captureScreenRoboImage("build/screens/$picture.png")
        }
    }

    /** Opens the page behind [row] of the first page, checks it, saves its picture, and goes back. */
    private inline fun page(row: String, picture: String, back: String = "Back", check: () -> Unit) {
        compose.onNodeWithText(row).performClick()
        compose.waitForIdle()
        check()
        captureScreenRoboImage("build/screens/$picture.png")
        compose.onNodeWithContentDescription(back).performClick()
        compose.waitForIdle()
    }

    private fun startsFromTheRight(text: String): Boolean {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val node = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        return node.center.x > root.center.x
    }
}
