package app.recitation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recitation.core.Timings
import app.recitation.core.WordTiming
import app.recitation.data.Playlist
import app.recitation.data.Track
import app.recitation.data.Verse
import app.recitation.data.Word
import app.recitation.prepare.ModelProblem
import app.recitation.prepare.Progress
import app.recitation.prepare.Step
import app.recitation.speech.ModelStore
import app.recitation.ui.HomeContent
import app.recitation.ui.NoRecitationBar
import app.recitation.ui.NowPlayingBar
import app.recitation.ui.ReaderContent
import app.recitation.ui.RecitationTheme
import app.recitation.ui.RecordingsContent
import app.recitation.ui.Status
import app.recitation.ui.StatusActions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the real screens on the JVM (no phone needed) with made-up recordings, in English
 * and Arabic and in the day and night themes, checks what they show, and saves a picture
 * of each to app/build/screens.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun verse(translation: String, vararg words: Pair<String, String>) = Verse(words.map { Word(it.first, it.second) }, translation)

    private val fatihah = listOf(
        verse(
            "In the name of Allah, the Entirely Merciful, the Especially Merciful.",
            "بِسْمِ" to "In (the) name", "ٱللَّهِ" to "(of) Allah", "ٱلرَّحْمَـٰنِ" to "the Most Gracious", "ٱلرَّحِيمِ" to "the Most Merciful",
        ),
        verse(
            "[All] praise is [due] to Allah, Lord of the worlds -",
            "ٱلْحَمْدُ" to "All praises and thanks", "لِلَّهِ" to "(be) to Allah", "رَبِّ" to "the Lord", "ٱلْعَـٰلَمِينَ" to "of the universe",
        ),
        verse("The Entirely Merciful, the Especially Merciful,", "ٱلرَّحْمَـٰنِ" to "The Most Gracious", "ٱلرَّحِيمِ" to "the Most Merciful"),
        verse("Sovereign of the Day of Recompense.", "مَـٰلِكِ" to "(The) Master", "يَوْمِ" to "(of the) Day", "ٱلدِّينِ" to "(of) [the] Judgment"),
        verse(
            "It is You we worship and You we ask for help.",
            "إِيَّاكَ" to "You Alone", "نَعْبُدُ" to "we worship", "وَإِيَّاكَ" to "and You Alone", "نَسْتَعِينُ" to "we ask for help",
        ),
    )

    private val track = Track("a", "سورة الفاتحة | Surah Al-Fatiha", 74, surah = 1, ready = true, clarity = 0.9f)

    private val playlist = Playlist(
        "https://www.youtube.com/playlist?list=x", "القرآن الكريم كاملاً", "Alafasy",
        listOf(
            track,
            Track("b", "سورة البقرة | Surah Al-Baqarah", 8927, surah = 2, wanted = true),
            Track("c", "سورة آل عمران | Surah Al-imran", 5253, surah = 3, wanted = true),
            Track("d", "سورة النساء | Surah An-Nisa'", 5389, surah = 4),
            Track("e", "تلاوة خاشعة", 600, surah = 0),
            Track("f", "سورة الأنعام | Surah Al-An'am", 4301, surah = 6, error = "Couldn't download it. Check the connection and try again."),
            Track("g", "سورة الأعراف | Surah Al-A'raf", 5021, surah = 7, ready = true, clarity = 0.4f),
        ),
    )

    private fun show(picture: String, content: @Composable () -> Unit) {
        compose.setContent {
            RecitationTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/$picture.png")
    }

    /** Each word a second long, its letters reached evenly through it. */
    private fun timings(verses: List<Verse>, clarity: Float = 0.9f): Timings {
        var ms = 0
        return Timings(
            verses.map { verse ->
                verse.words.map { word ->
                    val n = word.arabic.length
                    WordTiming(ms, intArrayOf(n / 3, ms, 2 * n / 3, ms + 330, n, ms + 660)).also { ms += 1000 }
                }
            },
            clarity,
        )
    }

    @Composable
    private fun Reader(positionMs: Int, meanings: Boolean = true, clarity: Float = 0.9f, verses: List<Verse> = fatihah) = ReaderContent(
        reading = Reading(1, verses, track, timings(verses, clarity)), showMeanings = meanings, playing = true,
        position = { positionMs }, duration = 74_000, onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {},
    )

    @Composable
    private fun Recordings(status: Status?, actions: StatusActions = StatusActions(), onCancel: (Track) -> Unit = {}) =
        RecordingsContent(playlist, status, actions, {}, {}, {}, onCancel, { _, _ -> }, {}, {})

    @Composable
    private fun Home(tab: Int, playlists: List<Playlist> = emptyList(), status: Status? = null) =
        HomeContent(tab, {}, playlists, status, StatusActions(), recited = setOf(1), onSurah = {}, onOpen = {}, onAdd = {})

    private fun startsFromTheRight(text: String): Boolean {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val node = compose.onAllNodesWithText(text).onFirst().fetchSemanticsNode().boundsInRoot
        return node.center.x > root.center.x
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun readingInEnglishByDay() {
        // 7.5 s in: half way through the last word of the second verse.
        show("en-day-reader") { Reader(7_500) }
        compose.onNodeWithText("سورة الفاتحة").assertIsDisplayed()
        compose.onNodeWithText("Al-Fatihah · 7 verses").assertIsDisplayed()
        compose.onNodeWithContentDescription("Verse 2").assertIsDisplayed()
        compose.onNodeWithText("of the universe").assertIsDisplayed()
        compose.onNodeWithText("[All] praise is [due] to Allah, Lord of the worlds -").assertIsDisplayed()
        assertTrue("a verse's first word sits on the right", startsFromTheRight("ٱلْحَمْدُ"))
        compose.onNodeWithText("Previous verse").assertIsDisplayed()
        compose.onNodeWithText("Next verse").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aVerseLongerThanTheScreenScrollsWithTheWord() {
        // One verse of 120 words, several screens tall, followed by a short one.
        val words = fatihah.flatMap { it.words }
        val long = Verse(List(120) { Word(words[it % words.size].arabic, "word ${it + 1}") }, "A very long verse.")
        // 90.5 s in: the 91st word, far below the first screen.
        show("en-day-reader-long-verse") { Reader(90_500, verses = listOf(long, fatihah[2])) }
        compose.onNodeWithText("word 91").assertIsDisplayed()
        compose.onNodeWithText("word 1").assertIsNotDisplayed()
        compose.onNodeWithText("word 120").assertIsNotDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-night-420dpi")
    fun readingInEnglishByNight() {
        show("en-night-reader") { Reader(7_500) }
        compose.onNodeWithText("of the universe").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-night-420dpi")
    fun readingInArabicByNight() {
        // Meanings are English, so they're off when the phone is in Arabic.
        show("ar-night-reader") { Reader(12_400, meanings = false) }
        compose.onNodeWithText("سورة الفاتحة").assertIsDisplayed()
        // Numbers follow the phone's language too.
        compose.onNodeWithText("٧ آيات").assertIsDisplayed()
        compose.onNodeWithContentDescription("الآية ٤").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun readingARecordingOfAnotherSurah() {
        show("en-day-reader-mismatch") { Reader(0, clarity = 0.4f) }
        compose.onNodeWithText("doesn't sound like Surah Al-Fatihah", substring = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun readingASurahWithNoRecitation() {
        var asked = false
        show("en-day-reader-no-recitation") {
            ReaderContent(
                reading = Reading(1, fatihah), showMeanings = true, playing = false, position = { 0 }, duration = 0,
                onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {},
                withoutRecitation = { NoRecitationBar("A recitation of this surah isn't downloaded yet.", "Get ready") { asked = true } },
            )
        }
        // The words and meanings are all there to read; there is just no player.
        compose.onNodeWithContentDescription("Verse 1").assertIsDisplayed()
        compose.onNodeWithText("In (the) name").assertIsDisplayed()
        compose.onNodeWithText("Next verse").assertDoesNotExist()
        compose.onNodeWithText("Get ready").performClick()
        assertTrue(asked)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theQuranTabListsEverySurah() {
        show("en-day-quran") { Home(tab = 0, playlists = listOf(playlist), status = Status.Working(Progress("b", Step.DOWNLOAD, 0.2f), "Surah Al-Baqarah")) }
        compose.onNodeWithText("Quran").assertIsDisplayed()
        compose.onNodeWithText("Al-Fatihah").assertIsDisplayed()
        compose.onNodeWithText("The Opener · 7 verses").assertIsDisplayed()
        compose.onNodeWithText("Al-Baqarah").assertIsDisplayed()
        // The surahs can be read while a download is under way.
        compose.onNodeWithText("Downloading the sound… 20%").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aRecitationCarriesOnWhileOtherScreensAreOpen() {
        var opened = false
        var toggled = false
        show("en-day-now-reciting") {
            HomeContent(
                0, {}, listOf(playlist), null, StatusActions(), recited = setOf(1), onSurah = {}, onOpen = {}, onAdd = {},
                nowPlaying = { NowPlayingBar("Al-Fatihah", playing = true, onPlayPause = { toggled = true }, onOpen = { opened = true }, onStop = {}) },
            )
        }
        compose.onNodeWithText("Reciting now").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause").performClick()
        assertTrue(toggled)
        // Tapping the bar goes back to the surah being recited.
        compose.onNodeWithText("Reciting now").performClick()
        assertTrue(opened)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun recordingsInEnglishByDay() {
        var cancelled: Track? = null
        show("en-day-recordings") { Recordings(Status.Working(Progress("b", Step.LISTEN, 0.43f), "Surah Al-Baqarah"), onCancel = { cancelled = it }) }
        compose.onNodeWithText("2 of 7 ready").assertIsDisplayed()
        compose.onAllNodesWithText("Listening to the recitation… 43%").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Waiting its turn").assertIsDisplayed()
        compose.onNodeWithText("2 · Al-Baqarah").assertIsDisplayed()
        compose.onNodeWithText("Choose surah").assertIsDisplayed()
        compose.onNodeWithText("Pause").assertIsDisplayed()
        // The cross on a queued recording takes just that one out.
        compose.onAllNodesWithContentDescription("Cancel").onFirst().performClick()
        assertTrue(cancelled?.id == "b")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun pausedDownloads() {
        var resumed = false
        show("en-day-paused") { Recordings(Status.Paused, StatusActions(resume = { resumed = true })) }
        compose.onNodeWithText("Downloads are paused. What was fetched so far is kept.").assertIsDisplayed()
        compose.onAllNodesWithText("Paused").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Resume").performClick()
        assertTrue(resumed)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun queuedRecordingsSayWhatTheyAreWaitingFor() {
        // While the speech model is fetched, a queued recording shows that, not a bare "waiting".
        show("en-day-model-download") { Recordings(Status.Working(Progress(null, Step.MODEL_DOWNLOAD, 0.37f), null)) }
        compose.onAllNodesWithText("Downloading the speech model (318 MB)… 37%").assertCountEquals(3)
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-night-420dpi")
    fun recordingsInArabicByNight() {
        show("ar-night-recordings") { Recordings(null) }
        compose.onNodeWithText("٢ من ٧ جاهزة").assertIsDisplayed()
        compose.onNodeWithText("٢ · البقرة").assertIsDisplayed()
        assertTrue("titles start from the right", startsFromTheRight("القرآن الكريم كاملاً"))
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun firstOpenInEnglishByDay() {
        show("en-day-empty") { Home(tab = 1) }
        compose.onNodeWithText("Add a playlist to begin").assertIsDisplayed()
        compose.onNodeWithText("Add playlist").assertIsDisplayed()
        compose.onNodeWithText("Check for updates").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-night-420dpi")
    fun playlistsInArabicByNight() {
        show("ar-night-playlists") { Home(tab = 1, playlists = listOf(playlist), status = Status.Working(Progress(null, Step.MODEL_COPY, 0.6f), null)) }
        compose.onNodeWithText("التلاوة").assertIsDisplayed()
        compose.onNodeWithText("جارٍ نسخ نموذج الصوت", substring = true).assertIsDisplayed()
        compose.onNodeWithText("البحث عن تحديثات").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-night-420dpi")
    fun theQuranTabInArabicByNight() {
        show("ar-night-quran") { Home(tab = 0) }
        compose.onNodeWithText("القرآن").assertIsDisplayed()
        compose.onNodeWithText("البقرة").assertIsDisplayed()
        compose.onNodeWithText("٢٨٦ آية").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun accentCoachIsTooOldToLendItsModel() {
        var download: Boolean? = null
        show("en-day-model-problem") {
            Recordings(Status.ModelBlocked(ModelProblem(ModelStore.Lender.TOO_OLD, null)), StatusActions(retryModel = { download = it }))
        }
        compose.onNodeWithText("Couldn't copy the speech model from Accent Coach").assertIsDisplayed()
        compose.onNodeWithText("accept its update", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Waiting for the speech model").onFirst().assertIsDisplayed()
        // Nothing is downloaded unless asked for.
        compose.onNodeWithText("Download it instead (318 MB)").performClick()
        assertTrue(download == true)
        compose.onNodeWithText("Try again").performClick()
        assertTrue(download == false)
    }
}
