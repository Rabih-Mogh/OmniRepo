package app.fajralarm

import android.app.Activity
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fajralarm.core.AlarmSettings
import app.fajralarm.core.DismissMission
import app.fajralarm.data.SettingsStore
import app.fajralarm.data.StopReason
import app.fajralarm.ui.AlarmActivity
import app.fajralarm.ui.MainActivity
import app.fajralarm.ui.SinkSetupActivity
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Opens the real screens on the JVM with the phone's language set to Arabic and to
 * English, checks each shows that language and lays out from the matching side, and
 * saves a picture of each screen to app/build/screens.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LanguageTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** A set-up alarm that has rung once, so every part of the main screen has something to show. */
    @Before
    fun setUp() {
        val store = SettingsStore(context)
        store.save(
            AlarmSettings(
                offsetMinutes = -15,
                fajrAdjustMinutes = 2,
                latitude = 33.8938,
                longitude = 35.5018,
                locationLabel = "Current location",
                dismissMission = DismissMission.MATH,
            ),
        )
        val rang = System.currentTimeMillis() - 3 * 60 * 60 * 1000
        store.recordRinging(rang)
        store.recordStop(StopReason.SUM, rang + 4 * 60 * 1000)
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h4400dp-420dpi")
    fun mainScreenInArabic() {
        open<MainActivity>("ar-main") {
            // The first page: the day's prayers, with the settings folded up to a line each.
            compose.onNodeWithText("مواقيت الصلاة").assertIsDisplayed()
            compose.onNodeWithText("أوقات اليوم").assertIsDisplayed()
            compose.onNodeWithText("الإمساك").assertIsDisplayed()
            compose.onNodeWithText("قيام الليل (الثلث الأخير)").assertIsDisplayed()
            compose.onNodeWithText("القمر").assertIsDisplayed()
            compose.onNodeWithText("الموقع الحالي", substring = true).assertIsDisplayed()
            compose.onNodeWithText("شاشة القفل والأدوات").performClick()
            compose.onNodeWithText("الصلاة الحالية على شاشة القفل").assertIsDisplayed()
            assertTrue("the title starts from the right", startsFromTheRight("مواقيت الصلاة"))
        }
        open<MainActivity>("ar-fajr") {
            // The second page: the alarm for Fajr.
            compose.onNodeWithText("منبّه الفجر").performClick()
            compose.onNodeWithText("المنبّه القادم").assertIsDisplayed()
            compose.onNodeWithText("قبل الفجر بـ", substring = true).assertIsDisplayed()
            compose.onNodeWithText("وأُوقف بحلّ المسألة الحسابية", substring = true).assertIsDisplayed()
            compose.onNodeWithText("جرّب المنبّه الآن").assertIsDisplayed()
            compose.onNodeWithText("أوقات اليوم").assertDoesNotExist()
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h4400dp-420dpi")
    fun mainScreenInEnglish() {
        open<MainActivity>("en-main") {
            // The first page: the day's prayers, with the settings folded up to a line each.
            compose.onNodeWithText("Prayer Times").assertIsDisplayed()
            compose.onNodeWithText("Imsak").assertIsDisplayed()
            compose.onNodeWithText("Shuruq").assertIsDisplayed()
            compose.onNodeWithText("Qiyam (last third)").assertIsDisplayed()
            compose.onNodeWithText("MOON").assertIsDisplayed()
            compose.onNodeWithText("Current location: 33.8938, 35.5018").assertIsDisplayed()
            compose.onNodeWithText("Use my current location").assertDoesNotExist()
            compose.onNodeWithText("Fajr +2").assertDoesNotExist()
            // A folded section opens when tapped, and the adjustments inside it open in turn.
            compose.onNodeWithText("CALCULATION").performClick()
            compose.onNodeWithText("Fajr +2").assertIsDisplayed()
            compose.onNodeWithText("Minute adjustments").performClick()
            compose.onNodeWithText("+2 min").assertIsDisplayed()
            compose.onNodeWithText("Imsak before Fajr").assertIsDisplayed()
            compose.onNodeWithText("LOCK SCREEN & WIDGETS").performClick()
            compose.onNodeWithText("Current prayer on the lock screen").assertIsDisplayed()
            compose.onNodeWithText("Add the “All times” widget").assertIsDisplayed()
            compose.onNodeWithText("See-through, for the wallpaper to show").assertIsDisplayed()
            compose.onNodeWithText("NEXT ALARM").assertDoesNotExist()
            assertTrue("the title starts from the left", !startsFromTheRight("Prayer Times"))
        }
        open<MainActivity>("en-fajr") {
            // The second page: the alarm for Fajr.
            compose.onNodeWithText("Fajr alarm").performClick()
            compose.onNodeWithText("NEXT ALARM").assertIsDisplayed()
            compose.onNodeWithText("15 min before Fajr").assertIsDisplayed()
            compose.onNodeWithText("and turned off by solving the sum at", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Solve a sum").assertIsDisplayed()
            compose.onNodeWithText("Test the alarm now").assertIsDisplayed()
            compose.onNodeWithText("Qiyam (last third)").assertDoesNotExist()
            compose.onNodeWithText("ALARM").performClick()
            compose.onNodeWithText("Ring relative to").assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-420dpi")
    fun methodListInArabic() {
        open<MainActivity>("ar-methods") {
            compose.onNodeWithText("الحساب").performScrollTo().performClick()
            compose.onNodeWithText("الطريقة").performScrollTo().performClick()
            compose.onNodeWithText("دار الفتوى – لبنان", substring = true).assertIsDisplayed()
            compose.onNodeWithText("أم القرى، مكة المكرمة", substring = true).assertIsDisplayed()
            compose.onNodeWithText("إغلاق").assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-420dpi")
    fun alarmScreenInArabic() {
        // Nothing is ringing here, and the alarm screen closes itself when it notices.
        compose.mainClock.autoAdvance = false
        open<AlarmActivity>("ar-alarm") {
            compose.onNodeWithText("حان وقت الفجر").assertIsDisplayed()
            compose.onNodeWithText("الصلاة خير من النوم").assertIsDisplayed()
            compose.onNodeWithText("حلّ المسألة لإيقاف المنبّه").assertIsDisplayed()
            // Five takes the plural used for three to ten; the digit is the phone's own.
            compose.onNodeWithText("دقائق", substring = true).assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-420dpi")
    fun sinkTestScreenInArabic() {
        open<SinkSetupActivity>("ar-sink-test") {
            compose.onNodeWithText("اختبار كشف المغسلة").assertIsDisplayed()
            compose.onAllNodesWithText("تم").onFirst().assertIsDisplayed()
        }
    }

    private inline fun <reified A : Activity> open(picture: String, check: () -> Unit) {
        ActivityScenario.launch(A::class.java).use {
            compose.waitForIdle()
            check()
            captureScreenRoboImage("build/screens/$picture.png")
        }
    }

    private fun startsFromTheRight(text: String): Boolean {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val node = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        return node.center.x > root.center.x
    }
}
