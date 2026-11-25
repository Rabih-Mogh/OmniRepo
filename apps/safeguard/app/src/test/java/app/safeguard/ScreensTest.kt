package app.safeguard

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
 * language and lays out from the matching side, and saves a picture of each to
 * app/build/screens.
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
    @Config(qualifiers = "en-rUS-w411dp-h3200dp-420dpi")
    fun mainScreenInEnglish() {
        open<MainActivity>("en-main") {
            compose.onNodeWithText("Safeguard").assertIsDisplayed()
            compose.onNodeWithText("Safeguard isn't watching").assertIsDisplayed()
            compose.onNodeWithText("Make it a device admin app").assertIsDisplayed()
            compose.onNodeWithText("Unlocked").assertIsDisplayed()
            compose.onNodeWithText("Waiting period: 1 day").assertIsDisplayed()
            compose.onNodeWithText("YouTube").assertIsDisplayed()
            compose.onNodeWithText("12 of 30 min used today").assertIsDisplayed()
            compose.onAllNodesWithText("Closed Mon–Fri", substring = true).onFirst().assertIsDisplayed()
            compose.onNodeWithText("Blocked apps").assertIsDisplayed()
            compose.onNodeWithText("Choose from this phone").assertIsDisplayed()
            compose.onNodeWithText("TikTok").assertIsDisplayed()
            compose.onNodeWithText("reddit.com").assertIsDisplayed()
            compose.onNodeWithText("casino").assertIsDisplayed()
            compose.onNodeWithText("Check for updates").assertIsDisplayed()
            assertTrue("the title starts from the left", !startsFromTheRight("Safeguard"))
        }
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h3200dp-420dpi")
    fun mainScreenInArabic() {
        open<MainActivity>("ar-main") {
            compose.onNodeWithText("الحارس").assertIsDisplayed()
            compose.onNodeWithText("«الحارس» لا يراقب").assertIsDisplayed()
            compose.onNodeWithText("غير مقفل").assertIsDisplayed()
            compose.onNodeWithText("حدود الوقت").assertIsDisplayed()
            compose.onNodeWithText("YouTube").assertIsDisplayed()
            compose.onNodeWithText("التطبيقات المحظورة").assertIsDisplayed()
            compose.onNodeWithText("المواقع المحظورة").assertIsDisplayed()
            compose.onNodeWithText("الكلمات المحظورة").assertIsDisplayed()
            compose.onNodeWithText("البحث عن تحديثات").assertIsDisplayed()
            assertTrue("the title starts from the right", startsFromTheRight("الحارس"))
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h3200dp-420dpi")
    fun lockedWithChangesWaiting() {
        Store.ask(context, Change.Lock)
        Store.ask(context, Change.Unlock)
        Store.ask(context, Change.Drop(Listed.SITE, "reddit.com"))
        open<MainActivity>("en-locked") {
            compose.onNodeWithText("Locked").assertIsDisplayed()
            compose.onNodeWithText("Waiting").assertIsDisplayed()
            compose.onNodeWithText("Unlock Safeguard").assertIsDisplayed()
            compose.onNodeWithText("Stop blocking reddit.com").assertIsDisplayed()
            compose.onAllNodesWithText("in 24 h 0 min").onFirst().assertIsDisplayed()
            // Still blocked while it waits.
            compose.onNodeWithText("reddit.com").assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h1600dp-420dpi")
    fun aLimitOpensToBeChanged() {
        open<MainActivity>("en-limit") {
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

    private fun startsFromTheRight(text: String): Boolean {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val node = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        return node.center.x > root.center.x
    }
}
