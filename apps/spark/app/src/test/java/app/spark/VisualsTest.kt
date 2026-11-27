package app.spark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.spark.core.Expr
import app.spark.core.Mark
import app.spark.core.Visual
import app.spark.data.Library
import app.spark.ui.SparkTheme
import app.spark.ui.VisualView
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The pictures a lesson can show, drawn on the JVM: two curves on one scale with a point
 * marked, things along a line, a share of a whole and a grid. Saves a picture of them,
 * whole and half drawn, to app/build/screens.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
class VisualsTest {
    @get:Rule
    val compose = createComposeRule()

    private val visuals = listOf(
        Visual.Curve(Expr.parse("100*1.07^x"), 0.0, 40.0, Expr.parse("100+7*x"), listOf("Compound", "Simple"), Mark(10.0, "Year 10: 197")),
        Visual.Line(0.0, 100.0, listOf(Mark(9.0, "Real: 9%"), Mark(99.0, "Most guess: 99%")), 0),
        Visual.Share(listOf("True alarms", "False alarms"), listOf(99.0, 999.0), 0),
        Visual.Grid(3, 3, 1),
    )

    private fun show(progress: Float, compact: Boolean) = compose.setContent {
        SparkTheme {
            Surface {
                Column(Modifier.padding(16.dp), Arrangement.spacedBy(20.dp)) {
                    for (visual in visuals) {
                        VisualView(visual, progress, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primary, compact = compact)
                    }
                }
            }
        }
    }

    @Test
    fun `each picture is drawn with what it says`() {
        show(1f, compact = false)
        // The two curves are named under the picture.
        compose.onNodeWithText("Compound").assertIsDisplayed()
        compose.onNodeWithText("Simple").assertIsDisplayed()
        // A share says how big each part is: once in the ring for the part picked out, once beside each name.
        assertEquals(2, compose.onAllNodesWithText("9%").fetchSemanticsNodes().size)
        compose.onNodeWithText("91%").assertIsDisplayed()
        compose.onNodeWithText("False alarms").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/screens/visuals.png")
    }

    /** Every picture the starter lessons show, a lesson to a page, each over its caption, as it is set beside a question. */
    @Test
    @Config(qualifiers = "en-rUS-w360dp-h2400dp-420dpi")
    fun `the starter lessons' pictures are drawn on a small phone`() {
        Library.base = null
        Library.load(ApplicationProvider.getApplicationContext())
        var lesson by mutableStateOf(Library.shelf.value.lessons.values.first())
        compose.setContent {
            SparkTheme {
                Surface {
                    Column(Modifier.padding(16.dp), Arrangement.spacedBy(14.dp)) {
                        for (picture in lesson.steps.flatMap { listOfNotNull(it.picture, it.reveal) }) {
                            key(picture) {
                                VisualView(picture.visual, 1f, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primary, compact = true)
                                Text(picture.caption.orEmpty(), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
        for (next in Library.shelf.value.lessons.values) {
            lesson = next
            compose.waitForIdle()
            val pictures = next.steps.flatMap { listOfNotNull(it.picture, it.reveal) }
            val captions = pictures.mapNotNull { it.caption }
            // A picture in a lesson says what it shows.
            assertEquals(next.id, pictures.size, captions.size)
            for (caption in captions.distinct()) assertTrue(caption, compose.onAllNodesWithText(caption).fetchSemanticsNodes().isNotEmpty())
            compose.onRoot().captureRoboImage("build/screens/pictures-${next.id.substringAfter("-").substringAfter("-").substringAfter("-")}.png")
        }
    }

    @Test
    fun `a picture half drawn, and set smaller`() {
        show(0.5f, compact = true)
        compose.onNodeWithText("Compound").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/screens/visuals-half.png")
    }
}
