package app.spark

import android.content.Context
import android.media.AudioManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.spark.core.Chime
import app.spark.core.Packs
import app.spark.data.Store
import app.spark.ui.LessonScreen
import app.spark.ui.Sounds
import app.spark.ui.SparkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The app's sounds: that each is made and loaded, that the moments of a lesson play theirs,
 * and that nothing plays with sounds off or the phone on silent. What they sound like is
 * for a phone to say.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
class SoundsTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val audio get() = context.getSystemService(AudioManager::class.java)
    private val pool get() = shadowOf(Sounds.pool)

    private fun path(chime: Chime) = Sounds.file(context, chime).path
    private fun played(chime: Chime) = pool.getPathPlaybacks(path(chime)).size

    @Before
    fun setUp() {
        Store.load(context)
        Sounds.load(context)
        for (chime in Chime.entries) pool.notifyPathLoaded(path(chime), true)
    }

    @Test
    fun `every sound is written to the cache as a WAV`() {
        for (chime in Chime.entries) {
            val bytes = Sounds.file(context, chime).readBytes()
            assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertTrue(bytes.size > 1000)
        }
    }

    @Test
    fun `a sound plays once it has loaded, and not before`() {
        Sounds.play(Chime.RIGHT)
        assertEquals(1, played(Chime.RIGHT))

        Sounds.load(context) // Loading again: nothing has finished loading yet.
        Sounds.play(Chime.RIGHT)
        assertEquals(0, played(Chime.RIGHT))
    }

    @Test
    fun `nothing plays with sounds turned off`() {
        Store.sounds(false)
        Sounds.play(Chime.RIGHT)
        assertEquals(0, played(Chime.RIGHT))
        Store.sounds(true)
        Sounds.play(Chime.RIGHT)
        assertEquals(1, played(Chime.RIGHT))
    }

    @Test
    fun `nothing plays while the phone is on silent or vibrate`() {
        audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        Sounds.play(Chime.FINISH)
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        Sounds.play(Chime.FINISH)
        assertEquals(0, played(Chime.FINISH))
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        Sounds.play(Chime.FINISH)
        assertEquals(1, played(Chime.FINISH))
    }

    @Test
    fun `a lesson plays a sound for a pick, a right answer, a wrong one and the end`() {
        val lesson = Packs.read(javaClass.getResource("/pack.json")!!.readText()).pack!!.lessons.single()
        compose.setContent { SparkTheme { LessonScreen(lesson) {} } }
        fun tap(text: String) = compose.onNodeWithText(text).performClick()

        assertEquals(0, Chime.entries.sumOf(::played))
        tap("6")
        assertEquals(1, played(Chime.TAP))
        tap("Check")
        compose.waitForIdle()
        assertEquals(1, played(Chime.RIGHT))
        tap("Continue")
        tap("Continue")

        tap("23 people")
        tap("10 people")
        tap("5 people")
        tap("Check")
        compose.waitForIdle()
        assertEquals(1, played(Chime.WRONG))
        assertEquals(1, played(Chime.RIGHT))
        tap("Continue")

        tap("Check")
        tap("Continue")
        tap("Skip")
        tap("Finish")
        compose.waitForIdle()
        assertEquals(1, played(Chime.FINISH))
        assertEquals(0, played(Chime.GOAL))
    }
}
