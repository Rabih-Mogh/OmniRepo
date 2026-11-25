package app.spark

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.spark.core.Packs
import app.spark.data.Store
import app.spark.ui.LessonScreen
import app.spark.ui.SparkTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A lesson taken on the JVM from its first step to its last, one of each kind of step,
 * answered right and answered wrong. Saves a picture of each step to app/build/screens.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
class LessonTest {
    @get:Rule
    val compose = createComposeRule()

    private val lesson = Packs.read(javaClass.getResource("/pack.json")!!.readText()).pack!!.lessons.single()
    private var score: Store.Score? = null
    private var closed = false

    private fun open() = compose.setContent {
        SparkTheme {
            androidx.compose.material3.Surface {
                LessonScreen(lesson) {
                    score = it
                    closed = true
                }
            }
        }
    }

    private fun tap(text: String) = compose.onNodeWithText(text).performClick()
    private fun picture(name: String) = compose.onRoot().captureRoboImage("build/screens/lesson-$name.png")
    private fun slide(to: Float) =
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performSemanticsAction(SemanticsActions.SetProgress) { it(to) }

    @Test
    fun `every step answered right`() {
        open()
        compose.onNodeWithText("Pairs grow fast").assertIsDisplayed()
        compose.onNodeWithText("253 pairs").assertIsDisplayed()
        picture("read")
        tap("Continue")

        compose.onNodeWithText("Check").assertIsNotEnabled()
        tap("6")
        tap("Check")
        compose.onNodeWithText("Right").assertIsDisplayed()
        compose.onNodeWithText("3 + 2 + 1 = 6.").assertIsDisplayed()
        picture("choice")
        tap("Continue")

        tap("5 people")
        tap("10 people")
        compose.onNodeWithText("Check").assertIsNotEnabled()
        tap("23 people")
        tap("Check")
        compose.onNodeWithText("Right").assertIsDisplayed()
        picture("order")
        tap("Continue")

        compose.onNodeWithText("2 people").assertIsDisplayed()
        slide(24.4f)
        compose.onNodeWithText("24 people").assertIsDisplayed()
        tap("Check")
        compose.onNodeWithText("Right").assertIsDisplayed()
        picture("estimate")
        tap("Continue")

        compose.onNodeWithText("a = 0.2").assertIsDisplayed()
        compose.onNodeWithText("Finish").assertIsNotEnabled()
        slide(1.02f)
        compose.onNodeWithText("a = 1").assertIsDisplayed()
        compose.onNodeWithText("That's it").assertIsDisplayed()
        compose.onNodeWithText("45").assertIsDisplayed()
        picture("explore")
        compose.onNodeWithText("Finish").assertIsEnabled()
        tap("Finish")

        compose.onNodeWithText("Lesson finished").assertIsDisplayed()
        compose.onNodeWithText("4 of 4 right first time").assertIsDisplayed()
        picture("finished")
        tap("Back to the feed")
        assertEquals(Store.Score(4, 4), score)
    }

    @Test
    fun `every step answered wrong`() {
        open()
        tap("Continue")

        tap("8")
        tap("Check")
        compose.onNodeWithText("Not quite").assertIsDisplayed()
        compose.onNodeWithText("3 + 2 + 1 = 6.").assertIsDisplayed()
        tap("Continue")

        // Picked, taken back, and picked again in the wrong order.
        tap("23 people")
        tap("23 people")
        tap("10 people")
        tap("5 people")
        tap("23 people")
        tap("Check")
        compose.onNodeWithText("Not quite").assertIsDisplayed()
        compose.onNodeWithText("The right order: 5 people → 10 people → 23 people").assertIsDisplayed()
        picture("order-wrong")
        tap("Continue")

        slide(60f)
        tap("Check")
        compose.onNodeWithText("Not quite").assertIsDisplayed()
        compose.onNodeWithText("The answer is 23 people.").assertIsDisplayed()
        picture("estimate-wrong")
        tap("Continue")

        slide(1.6f)
        tap("Skip")
        compose.onNodeWithText("Not quite").assertIsDisplayed()
        tap("Finish")
        compose.onNodeWithText("0 of 4 right first time").assertIsDisplayed()
        tap("Back to the feed")
        assertEquals(Store.Score(0, 4), score)
    }

    @Test
    fun `closing a lesson early gives no score`() {
        open()
        tap("Continue")
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Close the lesson")).performClick()
        assertEquals(true, closed)
        assertEquals(null, score)
    }
}
