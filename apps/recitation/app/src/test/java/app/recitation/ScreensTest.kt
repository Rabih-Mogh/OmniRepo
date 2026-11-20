package app.recitation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recitation.core.Timings
import app.recitation.core.WordTiming
import app.recitation.data.Hit
import app.recitation.data.Marks
import app.recitation.data.Pin
import app.recitation.data.Playlist
import app.recitation.data.Script
import app.recitation.data.VerseCollection
import app.recitation.data.VerseRef
import app.recitation.data.Track
import app.recitation.data.Verse
import app.recitation.data.Word
import app.recitation.player.Artwork
import app.recitation.prepare.ModelProblem
import app.recitation.prepare.Progress
import app.recitation.prepare.Step
import app.recitation.speech.ModelStore
import app.recitation.ui.CollectionContent
import app.recitation.ui.HomeContent
import app.recitation.ui.LastRead
import app.recitation.ui.LastReadBar
import app.recitation.ui.LocalSurahNames
import app.recitation.ui.MarkActions
import app.recitation.ui.MenuContent
import app.recitation.ui.NoRecitationBar
import app.recitation.ui.NowPlayingBar
import app.recitation.ui.RangeChooser
import app.recitation.ui.ReaderContent
import app.recitation.ui.RecitationTheme
import app.recitation.ui.RecordingsContent
import app.recitation.ui.SavedTab
import app.recitation.ui.ScriptChooser
import app.recitation.ui.Status
import app.recitation.ui.StatusActions
import app.recitation.ui.TextSizeControls
import app.recitation.ui.VerseMarks
import app.recitation.ui.VerseMarksContent
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.math.abs
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

    /**
     * The surah-name calligraphy the app fetches from Quran.com isn't in the repo. Point
     * SURA_NAMES_FONT at a copy to see it in the pictures; without it the names are drawn in
     * the bundled hand, as on a phone that hasn't fetched it yet.
     */
    private val surahNames = System.getenv("SURA_NAMES_FONT")?.let(::File)?.takeIf { it.exists() }?.let { FontFamily(Font(it)) }

    private fun show(picture: String, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalSurahNames provides surahNames) {
                RecitationTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
            }
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

    /** The picture Android's own media player shows, and takes its colours from. */
    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-night-420dpi")
    fun thePictureForAndroidsMediaPlayerByNight() {
        val font = System.getenv("SURA_NAMES_FONT")?.let(::File)?.takeIf { it.exists() }
        val png = Artwork.of(ApplicationProvider.getApplicationContext(), app.recitation.core.SURAHS[17], font)
        File("build/screens").mkdirs()
        File("build/screens/artwork-night.png").writeBytes(png)
        assertTrue(png.size > 2_000)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun thePictureForAndroidsMediaPlayerByDay() {
        val font = System.getenv("SURA_NAMES_FONT")?.let(::File)?.takeIf { it.exists() }
        val png = Artwork.of(ApplicationProvider.getApplicationContext(), app.recitation.core.SURAHS[17], font)
        File("build/screens/artwork-day.png").writeBytes(png)
        assertTrue(png.size > 2_000)
    }

    /** The launcher icon as phones show it: cut to a rounded square, and to a circle. */
    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theAppIcon() {
        show("icon") {
            Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                for (shape in listOf(RoundedCornerShape(22.dp), CircleShape)) {
                    // Launchers show the middle 72 of the icon's 108 units.
                    Box(Modifier.size(144.dp).clip(shape), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize(216.dp))
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(216.dp))
                    }
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun readingInEnglishByDay() {
        // 7.5 s in: half way through the last word of the second verse.
        show("en-day-reader") { Reader(7_500) }
        compose.onNodeWithTag("surah-title").assertIsDisplayed().assertContentDescriptionEquals("سورة الفاتحة")
        compose.onNodeWithText("Al-Fatihah · 7 verses").assertIsDisplayed()
        compose.onNodeWithContentDescription("Verse 2").assertIsDisplayed()
        compose.onNodeWithText("of the universe").assertIsDisplayed()
        // A verse's translation stays out of the way until the verse is tapped.
        compose.onNodeWithText("[All] praise is [due] to Allah, Lord of the worlds -").assertDoesNotExist()
        assertTrue("a verse's first word sits on the right", startsFromTheRight("ٱلْحَمْدُ"))
        // The player's buttons carry no words: they are named for screen readers only.
        compose.onNodeWithContentDescription("Previous verse").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next verse").assertIsDisplayed()
        compose.onNodeWithText("Previous verse").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun tappingAVerseShowsItsTranslationForAWhile() {
        val translation = "[All] praise is [due] to Allah, Lord of the worlds -"
        compose.setContent {
            RecitationTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Reader(7_500) } }
        }
        compose.onNodeWithText("رَبِّ", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(translation).assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/screens/en-day-reader-translation.png")
        // Left alone, it goes away by itself.
        compose.mainClock.advanceTimeBy(30_000)
        compose.waitForIdle()
        compose.onNodeWithText(translation).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun wordMeaningsCanBeHidden() {
        show("en-day-reader-no-meanings") { Reader(7_500, meanings = false) }
        compose.onNodeWithText("of the universe").assertDoesNotExist()
        compose.onNodeWithContentDescription("Word meanings").assertIsDisplayed()
        // The verse's translation is still a tap away.
        compose.onNodeWithText("رَبِّ", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("[All] praise is [due] to Allah, Lord of the worlds -").assertIsDisplayed()
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
        assertTrue("the word being recited is at the middle of the page, off by ${offCentre("word 91")} px", abs(offCentre("word 91")) < 60f)
    }

    /** How far the middle of the tile showing [meaning] is below the middle of the page of verses, in px. */
    private fun offCentre(meaning: String): Float {
        val page = compose.onNodeWithTag("verses").fetchSemanticsNode().boundsInRoot
        val word = compose.onNodeWithText(meaning).fetchSemanticsNode().boundsInRoot
        return word.center.y - page.center.y
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theFirstAndLastWordsAreCentredToo() {
        // There is nothing above the first word or below the last, yet both sit in the middle.
        show("en-day-reader-first-word") { Reader(200) }
        // The title page fills the room above it: the surah's details, and the basmala even for Al-Fatihah.
        compose.onNodeWithText("Al-Fatihah · The Opener").assertIsDisplayed()
        compose.onNodeWithText("7 verses · Meccan · No. 5 in order of revelation").assertIsDisplayed()
        compose.onNodeWithContentDescription("بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ").assertIsDisplayed()
        assertTrue("first word off by ${offCentre("In (the) name")} px", abs(offCentre("In (the) name")) < 60f)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theLastWordIsCentred() {
        show("en-day-reader-last-word") { Reader(17_500) }
        assertTrue("last word off by ${offCentre("we ask for help")} px", abs(offCentre("we ask for help")) < 60f)
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
        compose.onNodeWithTag("surah-title").assertIsDisplayed().assertContentDescriptionEquals("سورة الفاتحة")
        // Numbers follow the phone's language too.
        compose.onNodeWithText("٧ آيات").assertIsDisplayed()
        compose.onNodeWithContentDescription("الآية ٤").assertIsDisplayed()
    }

    private val ikhlas = listOf(
        verse("Say, \"He is Allah, [who is] One,", "قُلْ" to "Say", "هُوَ" to "He", "ٱللَّهُ" to "(is) Allah", "أَحَدٌ" to "the One"),
        verse("Allah, the Eternal Refuge.", "ٱللَّهُ" to "Allah", "ٱلصَّمَدُ" to "the Eternal, the Absolute"),
        verse("He neither begets nor is born,", "لَمْ" to "Not", "يَلِدْ" to "He begets", "وَلَمْ" to "and not", "يُولَدْ" to "He is begotten"),
        verse(
            "Nor is there to Him any equivalent.\"",
            "وَلَمْ" to "And not", "يَكُن" to "is", "لَّهُۥ" to "for Him", "كُفُوًا" to "equivalent", "أَحَدٌۢ" to "any [one]",
        ),
    )

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-night-420dpi")
    fun theBasmalaIsOneCalligraphicPiece() {
        // Any surah but Al-Fatihah (whose first verse it is) and At-Tawbah opens with it.
        show("en-night-reader-basmala") {
            ReaderContent(
                reading = Reading(112, ikhlas, track.copy(surah = 112), timings(ikhlas)), showMeanings = true, playing = true,
                position = { 200 }, duration = 33_000, onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {},
            )
        }
        compose.onNodeWithContentDescription("بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ").assertIsDisplayed()
        // The verse's number is above the verse, at the left.
        val page = compose.onNodeWithTag("verses").fetchSemanticsNode().boundsInRoot
        val number = compose.onNodeWithContentDescription("Verse 1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val firstWord = compose.onNodeWithText("Say").fetchSemanticsNode().boundsInRoot
        assertTrue("the number is at the left", number.center.x < page.left + page.width / 6)
        assertTrue("the number is above the verse", number.bottom <= firstWord.top)
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
        compose.onNodeWithContentDescription("Next verse").assertDoesNotExist()
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
    @Config(qualifiers = "en-rUS-w411dp-h891dp-night-420dpi")
    fun offersToMoveToTheLastPlaceRead() {
        var moved = false
        show("en-night-last-read") {
            HomeContent(
                0, {}, listOf(playlist), null, StatusActions(), recited = setOf(1, 2), onSurah = {}, onOpen = {}, onAdd = {},
                lastRead = LastRead(app.recitation.core.SURAHS[1], 45), onLastRead = { moved = true },
            )
        }
        compose.onNodeWithText("Last read: Al-Baqarah, verse 45").assertIsDisplayed()
        compose.onNodeWithText("Move to last read").performClick()
        assertTrue(moved)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun offersTheLastPlaceInsideASurahToo() {
        var moved = false
        show("en-day-reader-last-read") {
            ReaderContent(
                reading = Reading(1, fatihah, track, timings(fatihah)), showMeanings = true, playing = true,
                position = { 200 }, duration = 74_000, onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {},
                suggestion = { LastReadBar(verse = 5, onGo = { moved = true }, onDismiss = {}) },
            )
        }
        compose.onNodeWithText("You stopped at verse 5.").assertIsDisplayed()
        compose.onNodeWithText("Move to last read").performClick()
        assertTrue(moved)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theRecitationWaitsAndSaysWhichVersesItWillRecite() {
        show("en-day-reader-range") {
            ReaderContent(
                reading = Reading(1, fatihah, track, timings(fatihah)), showMeanings = true, playing = false,
                position = { 0 }, duration = 74_000, onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {},
                range = 1..3,
            )
        }
        // Not playing: the recitation waits for the reader. The bar says which verses it will recite.
        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
        compose.onNodeWithContentDescription("Verses 2 – 4").assertIsDisplayed()
        compose.onNodeWithText("2 – 4").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun choosingTheFirstAndLastVerse() {
        var range = 1..3
        compose.setContent {
            var shown by remember { mutableStateOf(range) }
            RecitationTheme { Surface(color = MaterialTheme.colorScheme.background) { RangeChooser(7, shown) { shown = it; range = it } } }
        }
        compose.onNodeWithText("Start at verse").assertIsDisplayed()
        compose.onNodeWithText("End after verse").assertIsDisplayed()
        // One verse earlier at the start, one later at the end.
        compose.onAllNodesWithContentDescription("Smaller").onFirst().performClick()
        compose.onAllNodesWithContentDescription("Larger")[1].performClick()
        compose.waitForIdle()
        assertTrue("got $range", range == 0..4)
        compose.onRoot().captureRoboImage("build/screens/en-day-range-chooser.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theTextCanBeMadeLarger() {
        fun wordHeight() = compose.onNodeWithText("ٱلْعَـٰلَمِينَ", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            RecitationTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ReaderContent(
                        reading = Reading(1, fatihah, track, timings(fatihah)), showMeanings = true, playing = true,
                        position = { 7_500 }, duration = 74_000, onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {},
                        textScale = scale,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Text size").assertIsDisplayed()
        val before = wordHeight()
        scale = 1.5f
        compose.waitForIdle()
        assertTrue("the words are drawn larger", wordHeight() > before * 1.3f)
        compose.onRoot().captureRoboImage("build/screens/en-day-reader-large-text.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theTextSizeControlsStepByTenths() {
        var scale by mutableFloatStateOf(1f)
        compose.setContent { RecitationTheme { Surface(color = MaterialTheme.colorScheme.background) { TextSizeControls(scale) { scale = it } } } }
        compose.onNodeWithText("100%").assertIsDisplayed()
        repeat(5) { compose.onNodeWithContentDescription("Larger").performClick() }
        compose.onNodeWithText("150%").assertIsDisplayed()
        repeat(20) { compose.onNodeWithContentDescription("Smaller").performClick() }
        // It stops at the smallest size rather than shrinking to nothing.
        compose.onNodeWithText("70%").assertIsDisplayed()
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

    /** The first [words] words placed, each a second long, with the listening [heardMs] into the recording. */
    private fun timingsSoFar(words: Int, heardMs: Int): Timings {
        var left = words
        val placed = timings(fatihah).verses.map { verse -> verse.take(left).also { left -= it.size } }.filter { it.isNotEmpty() }
        return Timings(placed, 1f, heardMs)
    }

    @Composable
    private fun HalfHeardReader(positionMs: Int, listening: Boolean = true, onSeek: (Int) -> Unit = {}) = ReaderContent(
        reading = Reading(1, fatihah, track.copy(ready = false, playable = true, wanted = listening), timingsSoFar(words = 8, heardMs = 8_000)),
        showMeanings = true, playing = true, position = { positionMs }, duration = 74_000,
        onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = onSeek, listening = listening,
    )

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aRecordingPlaysWhileItIsStillListenedTo() {
        // The first two verses have been placed; the recitation is in the second.
        var sought: Int? = null
        show("en-day-reader-listening") { HalfHeardReader(6_500, onSeek = { sought = it }) }
        compose.onNodeWithText("Still listening to this recording: words light up as far as 0:08.").assertIsDisplayed()
        // A word that has a place can be jumped to; one the listening hasn't reached can't yet.
        compose.onNodeWithText("ٱلْحَمْدُ", useUnmergedTree = true).performClick()
        assertTrue(sought == 4_000)
        sought = null
        compose.onNodeWithText("مَـٰلِكِ", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Next verse").performClick()
        assertTrue(sought == null)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-night-420dpi")
    fun pastWhatHasBeenHeardNoWordIsLit() {
        // The recitation has run ahead of the listening: the words are there to read, none framed.
        show("en-night-reader-ahead-of-listening") { HalfHeardReader(20_000) }
        compose.onNodeWithText("Still listening to this recording: words light up as far as 0:08.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aRecordingWhoseListeningWasStoppedSaysSo() {
        show("en-day-reader-listening-stopped") { HalfHeardReader(6_500, listening = false) }
        compose.onNodeWithText("Not listened to the end yet: words light up as far as 0:08.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aRecordingCanBePlayedWhileItIsListenedTo() {
        val tracks = listOf(
            track,
            Track("b", "سورة البقرة | Surah Al-Baqarah", 8927, surah = 2, wanted = true, playable = true),
            Track("c", "سورة آل عمران | Surah Al-imran", 5253, surah = 3, playable = true),
        )
        var opened: Track? = null
        var asked: List<Track>? = null
        show("en-day-recordings-listening") {
            RecordingsContent(
                playlist.copy(tracks = tracks), Status.Working(Progress("b", Step.LISTEN, 0.43f), "Surah Al-Baqarah"), StatusActions(),
                {}, { opened = it }, { asked = it }, {}, { _, _ -> }, {}, {},
            )
        }
        // Its sound is here, so it has a play button while the listening goes on.
        compose.onAllNodesWithText("Listening to the recitation… 43%").onFirst().assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Play")[1].performClick()
        assertTrue(opened?.id == "b")
        // One whose listening was stopped says so, and its button takes the listening up again.
        compose.onNodeWithText("Plays, but not listened to the end yet").assertIsDisplayed()
        compose.onNodeWithContentDescription("Get ready").performClick()
        assertTrue(asked?.single()?.id == "c")
    }

    /** Al-Fatihah as each script writes it: the Madinah mushaf's, the IndoPak one, and everyday spelling. */
    private class S(val madinah: String, val indopak: String, val everyday: String)

    private val fatihahInEveryScript = listOf(
        listOf(S("بِسۡمِ", "بِسۡمِ", "بِسْمِ"), S("ٱللَّهِ", "اللهِ", "اللَّهِ"), S("ٱلرَّحۡمَٰنِ", "الرَّحۡمٰنِ", "الرَّحْمَٰنِ"), S("ٱلرَّحِيمِ", "الرَّحِيۡمِ", "الرَّحِيمِ")),
        listOf(S("ٱلۡحَمۡدُ", "اَلۡحَمۡدُ", "الْحَمْدُ"), S("لِلَّهِ", "لِلّٰهِ", "لِلَّهِ"), S("رَبِّ", "رَبِّ", "رَبِّ"), S("ٱلۡعَٰلَمِينَ", "الۡعٰلَمِيۡنَۙ‏", "الْعَالَمِينَ")),
        listOf(S("ٱلرَّحۡمَٰنِ", "الرَّحۡمٰنِ", "الرَّحْمَٰنِ"), S("ٱلرَّحِيمِ", "الرَّحِيۡمِۙ‏", "الرَّحِيمِ")),
        listOf(S("مَٰلِكِ", "مٰلِكِ", "مَالِكِ"), S("يَوۡمِ", "يَوۡمِ", "يَوْمِ"), S("ٱلدِّينِ", "الدِّيۡنِؕ‏", "الدِّينِ")),
        listOf(S("إِيَّاكَ", "اِيَّاكَ", "إِيَّاكَ"), S("نَعۡبُدُ", "نَعۡبُدُ", "نَعْبُدُ"), S("وَإِيَّاكَ", "وَاِيَّاكَ", "وَإِيَّاكَ"), S("نَسۡتَعِينُ", "نَسۡتَعِيۡنُؕ‏", "نَسْتَعِينُ")),
        listOf(S("ٱهۡدِنَا", "اِهۡدِنَا", "اهْدِنَا"), S("ٱلصِّرَٰطَ", "الصِّرَاطَ", "الصِّرَاطَ"), S("ٱلۡمُسۡتَقِيمَ", "الۡمُسۡتَقِيۡمَۙ‏", "الْمُسْتَقِيمَ")),
        listOf(
            S("صِرَٰطَ", "صِرَاطَ", "صِرَاطَ"), S("ٱلَّذِينَ", "الَّذِيۡنَ", "الَّذِينَ"), S("أَنۡعَمۡتَ", "اَنۡعَمۡتَ", "أَنْعَمْتَ"), S("عَلَيۡهِمۡ", "عَلَيۡهِمۡ ۙ‏", "عَلَيْهِمْ"),
            S("غَيۡرِ", "غَيۡرِ", "غَيْرِ"), S("ٱلۡمَغۡضُوبِ", "الۡمَغۡضُوۡبِ", "الْمَغْضُوبِ"), S("عَلَيۡهِمۡ", "عَلَيۡهِمۡ", "عَلَيْهِمْ"), S("وَلَا", "وَلَا", "وَلَا"), S("ٱلضَّآلِّينَ", "الضَّآلِّيۡنَ‏", "الضَّالِّينَ"),
        ),
    ).let { scripts ->
        fatihah.mapIndexed { v, verse -> Verse(verse.words.mapIndexed { w, word -> scripts[v][w].let { Word(word.arabic, word.meaning, it.madinah, it.indopak, it.everyday) } }, verse.translation) }
    }

    /** The IndoPak font is fetched by the app, not kept in the repo: point INDOPAK_FONT at a copy to draw that script. */
    private val indopak = System.getenv("INDOPAK_FONT")?.let(::File)?.takeIf { it.exists() }?.let { FontFamily(Font(it)) }

    @Composable
    private fun ReaderIn(script: Script, meanings: Boolean = false) = ReaderContent(
        reading = Reading(1, fatihahInEveryScript, track, timings(fatihah)), showMeanings = meanings, playing = true,
        position = { 7_500 }, duration = 74_000, onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {},
        script = script, indopak = indopak,
    )

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theVersesInTheMadinahMushafsScript() {
        show("en-day-script-madinah") { ReaderIn(Script.MADINAH) }
        // The Madinah mushaf writes the alif that isn't pronounced with a wasla, and its sukun as a small open head.
        compose.onNodeWithText("ٱلۡحَمۡدُ", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theVersesInTheAmiriHand() {
        show("en-day-script-amiri") { ReaderIn(Script.AMIRI) }
        compose.onNodeWithText("ٱلْحَمْدُ", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theVersesInEverydaySpelling() {
        show("en-day-script-everyday") { ReaderIn(Script.EVERYDAY) }
        // "العالمين" with its alif written out, as at school.
        compose.onNodeWithText("الْعَالَمِينَ", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theVersesInTheIndoPakScript() {
        show("en-day-script-indopak") { ReaderIn(Script.INDOPAK) }
        // Without its font on the phone the script isn't used: the Madinah mushaf's stands in.
        compose.onNodeWithText(if (indopak != null) "اَلۡحَمۡدُ" else "ٱلۡحَمۡدُ", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theScriptCanBeChosen() {
        var chosen: Script? = null
        show("en-day-script-chooser") { Column { ScriptChooser(Script.MADINAH, withIndoPak = true) { chosen = it } } }
        compose.onNodeWithText("Uthmani · Madinah mushaf").assertIsDisplayed()
        compose.onNodeWithText("IndoPak").assertIsDisplayed()
        compose.onNodeWithText("Everyday spelling").performClick()
        assertTrue(chosen == Script.EVERYDAY)
    }

    @Test
    @Config(qualifiers = "ar-w411dp-h891dp-night-420dpi")
    fun theIndoPakScriptIsOfferedOnlyOnceItsFontIsHere() {
        show("ar-night-script-chooser") { Column { ScriptChooser(Script.MADINAH, withIndoPak = false) {} } }
        compose.onNodeWithText("عثماني · مصحف المدينة").assertIsDisplayed()
        compose.onNodeWithText("الرسم الإملائي").assertIsDisplayed()
        compose.onNodeWithText("إندوباك (شبه القارة الهندية)").assertDoesNotExist()
    }

    private val pinned = listOf(Pin("p1", "Reading", VerseRef(1, 2)), Pin("p2", "Memorising", VerseRef(2, 255)))
    private val kept = listOf(
        VerseCollection(Marks.LATER, "", listOf(VerseRef(1, 5), VerseRef(2, 255))),
        VerseCollection("c1", "Verses of mercy", listOf(VerseRef(1, 3))),
    )

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-night-420dpi")
    fun pinsAndKeptVersesAreMarkedOnThePage() {
        var marking: Int? = null
        show("en-night-reader-marks") {
            ReaderContent(
                reading = Reading(1, fatihah, track, timings(fatihah)), showMeanings = true, playing = false,
                position = { 7_500 }, duration = 74_000, onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {},
                marks = VerseMarks(mapOf(1 to listOf("Reading")), setOf(2, 4)), onMark = { marking = it },
            )
        }
        compose.onNodeWithText("Reading").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Kept in a collection").onFirst().assertIsDisplayed()
        // A verse's number opens its pins and collections.
        compose.onNodeWithContentDescription("Verse 3").performClick()
        assertTrue(marking == 2)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h420dp-420dpi")
    fun aSurahOpensAtThePinnedVerse() {
        compose.setContent {
            RecitationTheme {
                ReaderContent(
                    reading = Reading(1, fatihah), showMeanings = true, playing = false, position = { 0 }, duration = 0,
                    onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {}, startVerse = 4,
                )
            }
        }
        compose.waitForIdle()
        // Verse 5 is at the top of the page; the title page and the first verses are above it, out of sight.
        compose.onNodeWithContentDescription("Verse 5").assertIsDisplayed()
        compose.onNodeWithContentDescription("Verse 1").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/screens/en-day-reader-opened-at-verse.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aVersesPinsAndCollections() {
        var moved: Pin? = null
        var added: String? = null
        var keeping: Pair<VerseCollection, Boolean>? = null
        show("en-day-verse-marks") {
            Column(Modifier.padding(24.dp)) {
                VerseMarksContent(
                    VerseRef(1, 2), pinned, kept,
                    MarkActions(newPin = { added = it }, movePin = { moved = it }, keep = { collection, on -> keeping = collection to on }),
                )
            }
        }
        // The pin on this verse can be taken off; the one elsewhere can be moved here.
        compose.onNodeWithText("On this verse").assertIsDisplayed()
        compose.onNodeWithText("Al-Baqarah · verse 255").assertIsDisplayed()
        compose.onNodeWithText("Move here").performClick()
        assertTrue(moved?.id == "p2")
        // A pin left without a name gets one.
        compose.onNodeWithContentDescription("Pin this verse").performClick()
        assertTrue(added == "Pin 3")
        // The collection that is always there, for questions to study later.
        compose.onNodeWithText("To look into").assertIsDisplayed().performClick()
        assertTrue(keeping?.first?.id == Marks.LATER && keeping?.second == true)
    }

    @Test
    @Config(qualifiers = "ar-w411dp-h891dp-night-420dpi")
    fun aVersesPinsAndCollectionsInArabic() {
        show("ar-night-verse-marks") { Column(Modifier.padding(24.dp)) { VerseMarksContent(VerseRef(1, 2), pinned, kept, MarkActions()) } }
        compose.onNodeWithText("للبحث والسؤال").assertIsDisplayed()
        compose.onNodeWithText("البقرة · الآية ٢٥٥").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-night-420dpi")
    fun theSavedTabListsPinsAndCollections() {
        var opened: VerseRef? = null
        var collection: VerseCollection? = null
        show("en-night-saved") {
            HomeContent(2, {}, emptyList(), null, StatusActions(), recited = setOf(1), onSurah = {}, onOpen = {}, onAdd = {}, saved = { modifier ->
                SavedTab(pinned, kept, onVerse = { opened = it }, onRemovePin = {}, onCollection = { collection = it }, modifier = modifier)
            })
        }
        compose.onNodeWithText("Saved").assertIsDisplayed()
        compose.onNodeWithText("Al-Fatihah · verse 2").assertIsDisplayed()
        compose.onNodeWithText("Memorising").performClick()
        assertTrue(opened == VerseRef(2, 255))
        compose.onNodeWithText("2 verses").assertIsDisplayed()
        compose.onNodeWithText("Verses of mercy").performClick()
        assertTrue(collection?.id == "c1")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aCollectionShowsItsVerses() {
        var opened: VerseRef? = null
        var removed: VerseRef? = null
        show("en-day-collection") {
            CollectionContent(
                VerseCollection(Marks.LATER, "", listOf(VerseRef(1, 2), VerseRef(1, 5))),
                text = { fatihahInEveryScript[it.verse - 1] }, onBack = {}, onVerse = { opened = it }, onRemove = { removed = it }, onDelete = {},
            )
        }
        compose.onNodeWithText("To look into").assertIsDisplayed()
        compose.onNodeWithText("It is You we worship and You we ask for help.").assertIsDisplayed()
        compose.onNodeWithText("Al-Fatihah · verse 5").performClick()
        assertTrue(opened == VerseRef(1, 5))
        compose.onAllNodesWithContentDescription("Take out").onFirst().performClick()
        assertTrue(removed == VerseRef(1, 2))
        // The collection that comes with the app can't be deleted.
        compose.onNodeWithContentDescription("Delete collection").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theTitleBandHasTheNameAMenuButtonAndASearchButton() {
        var searching: String? = null
        show("en-day-title-band") {
            HomeContent(0, {}, emptyList(), null, StatusActions(), recited = setOf(1), onSurah = {}, onOpen = {}, onAdd = {}, onQuery = { searching = it })
        }
        compose.onNodeWithText("Recitation").assertIsDisplayed()
        compose.onNodeWithContentDescription("Menu").assertIsDisplayed()
        // The name is at the left, right beside the menu button.
        val name = compose.onNodeWithText("Recitation").fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithContentDescription("Menu").fetchSemanticsNode().boundsInRoot
        assertTrue(name.left >= menu.right && name.left - menu.right < 60f)
        // No emblem and no second line: the band is the name between its two buttons.
        compose.onNodeWithText("Read the Quran word by word, and follow a recitation as you listen.").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search").performClick()
        assertTrue(searching == "")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-night-420dpi")
    fun theMenuButtonOpensTheMenu() {
        var script: Script? = null
        var meanings: Boolean? = null
        compose.setContent {
            RecitationTheme {
                HomeContent(0, {}, emptyList(), null, StatusActions(), recited = setOf(1), onSurah = {}, onOpen = {}, onAdd = {}, menu = {
                    MenuContent(Script.MADINAH, { script = it }, withIndoPak = true, textScale = 1f, onTextScale = {}, meanings = true, onMeanings = { meanings = it }) {
                        Text("Version 1.41")
                    }
                })
            }
        }
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/en-night-menu.png")
        compose.onNodeWithText("Read the Quran word by word, and follow a recitation as you listen.").assertIsDisplayed()
        compose.onNodeWithText("Everyday spelling").performClick()
        assertTrue(script == Script.EVERYDAY)
        compose.onNodeWithText("Word meanings").performClick()
        assertTrue(meanings == false)
        compose.onNodeWithText("100%").assertIsDisplayed()
        compose.onNodeWithText("Version 1.41").assertIsDisplayed()
    }

    @Composable
    private fun Searching(query: String, hits: List<Hit> = emptyList(), onSurah: (Int) -> Unit = {}, onVerse: (VerseRef) -> Unit = {}) =
        HomeContent(0, {}, emptyList(), null, StatusActions(), recited = setOf(1), onSurah = onSurah, onOpen = {}, onAdd = {}, query = query, hits = hits, onVerse = onVerse)

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun searchingFindsSurahsAndAVerseByItsNumbers() {
        var surah: Int? = null
        var verse: VerseRef? = null
        show("en-day-search") { Searching("kahf 10", onSurah = { surah = it }, onVerse = { verse = it }) }
        // The tabs step aside while searching.
        compose.onNodeWithText("Playlists").assertDoesNotExist()
        compose.onNodeWithText("Al-Kahf · verse 10").performClick()
        assertTrue(verse == VerseRef(18, 10))
        compose.onNodeWithText("Al-Kahf").performClick()
        assertTrue(surah == 18)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-night-420dpi")
    fun searchingFindsWordsInTheVerses() {
        var verse: VerseRef? = null
        val hits = listOf(
            Hit(VerseRef(1, 2), "ٱلۡحَمۡدُ لِلَّهِ رَبِّ ٱلۡعَٰلَمِينَ", "[All] praise is [due] to Allah, Lord of the worlds -"),
            Hit(VerseRef(1, 5), "إِيَّاكَ نَعۡبُدُ وَإِيَّاكَ نَسۡتَعِينُ", "It is You we worship and You we ask for help."),
        )
        show("en-night-search-words") { Searching("worship", hits, onVerse = { verse = it }) }
        compose.onNodeWithText("In the verses").assertIsDisplayed()
        compose.onNodeWithText("Al-Fatihah · verse 5").performClick()
        assertTrue(verse == VerseRef(1, 5))
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-night-420dpi")
    fun searchingInArabic() {
        show("ar-night-search") { Searching("البقرة ٢٥٥") }
        compose.onNodeWithText("البقرة · الآية ٢٥٥").assertIsDisplayed()
        compose.onNodeWithContentDescription("البقرة").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun searchSaysWhatCanBeTypedAndWhenNothingIsFound() {
        var query by mutableStateOf("")
        compose.setContent { RecitationTheme { Searching(query) } }
        compose.onNodeWithText("Type a surah's name or number, a verse such as 2:255, or words from a verse or its translation.").assertIsDisplayed()
        query = "zzzz"
        compose.waitForIdle()
        compose.onNodeWithText("Nothing found.").assertIsDisplayed()
        compose.onNodeWithText("Words are looked for in the surahs already on the phone.").assertIsDisplayed()
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
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-night-420dpi")
    fun playlistsInArabicByNight() {
        show("ar-night-playlists") { Home(tab = 1, playlists = listOf(playlist), status = Status.Working(Progress(null, Step.MODEL_COPY, 0.6f), null)) }
        compose.onNodeWithContentDescription("التلاوة").assertIsDisplayed()
        compose.onNodeWithText("جارٍ نسخ نموذج الصوت", substring = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-night-420dpi")
    fun theQuranTabInArabicByNight() {
        show("ar-night-quran") { Home(tab = 0) }
        compose.onNodeWithText("القرآن").assertIsDisplayed()
        compose.onNodeWithContentDescription("البقرة").assertIsDisplayed()
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
