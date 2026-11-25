package app.spark

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.spark.core.Reel
import app.spark.data.Library
import app.spark.ui.ReelPage
import app.spark.ui.SparkTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every reel in the starter pack, laid out on a small phone: each has to fit its page
 * without scrolling, because a page that scrolls is a page harder to swipe.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w360dp-h740dp-420dpi")
class FitTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Library.base = null
        Library.load(context)
    }

    @Test
    fun `every starter reel fits a small phone's page`() {
        val reels = Library.shelf.value.reels.values.toList()
        var reel by mutableStateOf(reels.first())
        compose.setContent {
            SparkTheme {
                // The room the feed leaves a reel: the strip above it and the gap below.
                Box(Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 52.dp, bottom = 8.dp)) {
                    key(reel.id) { ReelPage(reel, active = true, played = true, liked = false, lessonDone = reel.lesson?.let { false }, {}, {}, {}, {}, {}) }
                }
            }
        }
        // A reel is set smaller before it is ever made to scroll, so one that scrolls is too long even small.
        val scrolls = SemanticsMatcher("scrolls") { (it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.maxValue?.invoke() ?: 0f) > 0f }
        val tooLong = ArrayList<String>()
        for (next: Reel in reels) {
            reel = next
            compose.waitForIdle()
            if (compose.onAllNodes(scrolls).fetchSemanticsNodes().isNotEmpty()) {
                tooLong += next.id
                compose.onRoot().captureRoboImage("build/screens/too-long-${next.id}.png")
            }
        }
        compose.onRoot().captureRoboImage("build/screens/small-phone.png")
        assertEquals("reels too long for a small phone", emptyList<String>(), tooLong)
    }
}
