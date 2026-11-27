package app.spark

import android.content.Context
import android.os.Looper
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.spark.data.Gemini
import app.spark.data.Library
import app.spark.data.Store
import app.spark.data.Writer
import app.spark.ui.SparkTheme
import app.spark.ui.WriterSection
import com.github.takahirom.roborazzi.captureRoboImage
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The part of the screen about you where a key is given: the steps to get one, the key
 * pasted and tried, and what is shown once it is kept. Saves a picture of each to app/build/screens.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WriterScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
        Gemini.base = server.url("/v1beta/").toString()
        Library.base = null
        Library.load(context)
        Store.today = { LocalDate.parse("2026-11-26").toEpochDay() }
        Store.load(context)
        Writer.today = { LocalDate.parse("2026-11-26") }
        Writer.load(context)
        compose.setContent { SparkTheme { Surface { androidx.compose.foundation.layout.Box(Modifier.padding(16.dp)) { WriterSection() } } } }
    }

    @After
    fun stop() = server.shutdown()

    /** Waits until Google has been asked about the key and the answer has reached the screen. */
    private fun settle() {
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            Writer.state.value != Writer.State.Idle && Writer.state.value != Writer.State.Checking
        }
        compose.waitForIdle()
    }

    private fun picture(name: String) = compose.onRoot().captureRoboImage("build/screens/$name.png")

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun `a key is asked for with the steps to get one, then tried and kept`() {
        compose.onNodeWithText("Reels written on this phone").assertIsDisplayed()
        compose.onNodeWithText("Open Google AI Studio").assertIsDisplayed()
        compose.onNodeWithText("3. Copy the key and paste it here.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        picture("en-writer-key")

        server.enqueue(MockResponse().setBody("""{"models": [{"name": "models/gemini-3.8-flash"}]}"""))
        compose.onNodeWithText("Paste the key").performTextInput("AIza-test-key-9f3d")
        compose.onNodeWithText("Save").performClick()
        settle()
        // The key itself is never shown again, only how it ends.
        compose.onNodeWithText("Key saved, ending in 9f3d").assertIsDisplayed()
        compose.onNodeWithText("The key works.").assertIsDisplayed()
        compose.onNodeWithText("Write new reels now").assertIsEnabled()
        picture("en-writer-ready")

        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("Paste the key").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar-w411dp-h891dp-420dpi")
    fun `in Arabic, a key Google turns down says so`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error": {"code": "authentication", "message": "API key not valid."}}"""))
        compose.onNodeWithText("مقاطع تُكتب على هذا الهاتف").assertIsDisplayed()
        picture("ar-writer-key")
        compose.onNodeWithText("الصق المفتاح").performTextInput("wrong")
        compose.onNodeWithText("حفظ").performClick()
        settle()
        compose.onNodeWithText("لم يقبل Google هذا المفتاح. تأكد من نسخه كاملاً، أو أنشئ مفتاحاً جديداً.").assertIsDisplayed()
        compose.onNodeWithText("API key not valid.").assertIsDisplayed()
        picture("ar-writer-refused")
    }
}
