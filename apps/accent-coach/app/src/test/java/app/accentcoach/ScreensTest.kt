package app.accentcoach

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import app.accentcoach.core.Drills
import app.accentcoach.core.Phonemes
import app.accentcoach.core.Said
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Opens the real screens on the JVM, checks what each shows, and saves a picture of each
 * to app/build/screens. The speech model can't run here, so nothing is recorded: where a
 * screen needs a result, the one the model would have led to is put in by hand.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
class ScreensTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val progress: File get() = File(context.filesDir, "progress.json")

    /** Three full days before today and two recordings today; one lesson learned and one tried. */
    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val today = LocalDate.now()
        progress.writeText(
            """{"days":{"${today.minusDays(3)}":5,"${today.minusDays(2)}":7,"${today.minusDays(1)}":5,"$today":2},"drills":{"th":8,"r":4}}""",
        )
    }

    /**
     * View models are made with the first application the process saw, which in a real
     * process is the only one. Each test here has its own, with its own files, so the
     * one that was kept is let go of.
     */
    @After
    fun tearDown() {
        ViewModelProvider.AndroidViewModelFactory::class.java.getDeclaredField("_instance").apply { isAccessible = true }.set(null, null)
    }

    @Test
    fun aNewInstallOpensOnTheDownload() {
        open("setup") {
            compose.onNodeWithText("Download (318 MB)").assertIsDisplayed()
        }
    }

    @Test
    fun theSentencesPageShowsTheStreak() {
        open("sentences") { vm ->
            vm.ready()
            compose.onNodeWithText("3 days in a row").assertIsDisplayed()
            compose.onNodeWithText("3 more today to keep it going.").assertIsDisplayed()
            compose.onNodeWithContentDescription("2 of 5 recordings today").assertIsDisplayed()
            compose.onNodeWithText("Sentences").assertIsDisplayed()
            compose.onNodeWithText("I think there are three of them.").assertIsDisplayed()
            compose.onNodeWithText("Tap and read the sentence aloud").assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h2000dp-420dpi")
    fun theSoundsPageListsEveryLesson() {
        open("sounds") { vm ->
            vm.ready()
            compose.onNodeWithText("Sounds").performClick()
            assertEquals(15, vm.lessons.size)
            for (lesson in vm.lessons) compose.onNodeWithText(lesson.title).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("1 OF 15 LEARNED").assertIsDisplayed()
            compose.onNodeWithText("Learned. Best: 8 of 9").assertIsDisplayed()
            compose.onNodeWithText("Best so far: 4 of 9").assertIsDisplayed()
            compose.onAllNodesWithContentDescription("Learned").assertCountEquals(1)
            compose.onAllNodesWithText("Not tried yet").assertCountEquals(13)
        }
    }

    @Test
    fun aLessonTeachesTheSoundThenStartsItsDrill() {
        open("sounds-after-lesson") { vm ->
            vm.ready()
            compose.onNodeWithText("Sounds").performClick()
            compose.onNodeWithText("American R").performScrollTo().performClick()
            compose.onNodeWithText("HOW IT'S MADE").assertIsDisplayed()
            compose.onNodeWithText("THE USUAL SLIP").assertIsDisplayed()
            compose.onNodeWithText("right · light").assertIsDisplayed()
            // The tabs give way to the lesson's own way back.
            compose.onAllNodesWithText("Sentences").assertCountEquals(0)
            compose.onNodeWithText("Best so far: 4 of 9.").performScrollTo().assertIsDisplayed()
            captureScreenRoboImage("build/screens/lesson.png")

            compose.onNodeWithText("Start the drill (9 to say)").performScrollTo().performClick()
            compose.onNodeWithText("SAY THE WORD").assertIsDisplayed()
            compose.onNodeWithText("red").assertIsDisplayed()
            compose.onNodeWithText("/ɹɛd/").assertIsDisplayed()
            compose.onNodeWithText("1 of 9").assertIsDisplayed()
            compose.onNodeWithText("Tap and say it aloud").assertIsDisplayed()
            captureScreenRoboImage("build/screens/drill-word.png")

            // Back goes to the lesson, then to the list.
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("HOW IT'S MADE").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Sentences").assertIsDisplayed()
        }
    }

    @Test
    fun aWordWithAnotherSoundInItsPlaceIsNotYet() {
        open("drill-next-word") { vm ->
            vm.ready()
            vm.show(Page.SOUNDS)
            val th = vm.lessons.first { it.id == "th" }
            val result = Drills.judge(th, th.items[0], listOf("t", "ɪ", "ŋ", "k"))
            vm.put("lesson", th)
            vm.put("drill", DrillRun(th, 0, mapOf(0 to result)))
            vm.put("stage", Stage.Done(result.assessment))
            compose.waitForIdle()
            compose.onNodeWithText("Not yet").assertIsDisplayed()
            compose.onNodeWithText("I heard /t/ where /θ/ belongs.").assertIsDisplayed()
            compose.onNodeWithText("Only the /θ/ counts here. Everything I heard: /tɪŋk/").assertIsDisplayed()
            compose.onNodeWithText("Try again").performScrollTo().assertIsDisplayed()
            captureScreenRoboImage("build/screens/drill-not-yet.png")

            compose.onNodeWithText("Next").performScrollTo().performClick()
            compose.onNodeWithText("2 of 9").assertIsDisplayed()
            compose.onNodeWithText("thank").assertIsDisplayed()
        }
    }

    @Test
    fun aPairToldApartIsNative() {
        open("drill-pair") { vm ->
            vm.ready()
            vm.show(Page.SOUNDS)
            val th = vm.lessons.first { it.id == "th" }
            val at = th.items.indexOfFirst { it.isPair }
            val result = Drills.judge(th, th.items[at], th.items[at].words.flatMap { it.phonemes })
            vm.put("lesson", th)
            vm.put("drill", DrillRun(th, at, mapOf(at to result)))
            vm.put("stage", Stage.Done(result.assessment))
            compose.waitForIdle()
            compose.onNodeWithText("SAY BOTH, ONE AFTER THE OTHER").assertIsDisplayed()
            compose.onNodeWithText("think, sink").assertIsDisplayed()
            // The verdict, and the button that plays the native voice.
            compose.onAllNodesWithText("Native").assertCountEquals(2)
            compose.onNodeWithText("The two words came out different, each with its own sound.").assertIsDisplayed()
        }
    }

    @Test
    fun aDrillThatWentWellMakesItsLessonLearned() {
        open("lesson-learned") { vm ->
            vm.ready()
            vm.show(Page.SOUNDS)
            val r = vm.lessons.first { it.id == "r" }
            // Every item as a native speaker says it, but the fourth with a rolled R.
            val results = r.items.indices.associateWith { i ->
                val native = Phonemes.normalize(r.items[i].words.flatMap { it.phonemes })
                Drills.judge(r, r.items[i], if (i == 3) native.map { if (it == "ɹ") "r" else it } else native)
            }
            assertEquals(Said.OFF, results.getValue(3).said)
            val last = r.items.lastIndex
            vm.put("lesson", r)
            vm.put("drill", DrillRun(r, last, results))
            vm.put("stage", Stage.Done(results.getValue(last).assessment))
            compose.waitForIdle()

            compose.onNodeWithText("Finish").performScrollTo().performClick()
            compose.onNodeWithText("8 of 9 native").assertIsDisplayed()
            compose.onNodeWithText("That is three in four or better, so this lesson is learned.").assertIsDisplayed()
            compose.onNodeWithText("Not yet").assertIsDisplayed()
            assertTrue("the drill's best go is kept", progress.readText().contains("\"r\":8"))
            captureScreenRoboImage("build/screens/drill-end.png")

            compose.onNodeWithText("Back to the lesson").performScrollTo().performClick()
            compose.onNodeWithText("Learned. Best: 8 of 9.").performScrollTo().assertIsDisplayed()
        }
    }

    private fun open(picture: String, check: (PracticeViewModel) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: PracticeViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[PracticeViewModel::class.java] }
            compose.waitForIdle()
            check(vm)
            captureScreenRoboImage("build/screens/$picture.png")
        }
    }

    /** As if the speech model had been downloaded and had started. */
    private fun PracticeViewModel.ready() {
        put("model", ModelState.Ready)
        compose.waitForIdle()
    }

    /** Sets a piece of the view model's state that only it may set, by the name of its property. */
    private fun PracticeViewModel.put(name: String, value: Any?) {
        val field = PracticeViewModel::class.java.getDeclaredField("$name\$delegate").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (field.get(this) as MutableState<Any?>).value = value
    }
}
