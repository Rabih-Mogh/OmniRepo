package app.recitation

import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.time.LocalDate
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recitation.core.Phonemizer
import app.recitation.core.Timings
import app.recitation.core.SURAHS
import app.recitation.core.WordTiming
import app.recitation.data.Hear
import app.recitation.ui.LessonContent
import app.recitation.ui.lessonOf
import app.recitation.core.Drill
import app.recitation.core.Drills
import app.recitation.core.Family
import app.recitation.core.Topic
import app.recitation.data.Spot
import app.recitation.data.Tally
import app.recitation.ui.DrillOver
import app.recitation.ui.DrillVerdict
import app.recitation.ui.MaddContent
import app.recitation.ui.MaddsBeside
import app.recitation.ui.SpotContent
import app.recitation.data.Appearance
import app.recitation.data.Hit
import app.recitation.data.Listening
import app.recitation.data.Marks
import app.recitation.data.Pin
import app.recitation.data.Playlist
import app.recitation.data.Script
import app.recitation.data.Space
import app.recitation.data.VerseCollection
import app.recitation.data.VerseRef
import app.recitation.data.Track
import app.recitation.data.Verse
import app.recitation.data.Word
import app.recitation.player.Artwork
import app.recitation.prepare.Progress
import app.recitation.prepare.Step
import app.recitation.core.Correction
import app.recitation.core.Listened
import app.recitation.core.Awaited
import app.recitation.core.Went
import app.recitation.core.Fault
import app.recitation.core.Madd
import app.recitation.core.MaddHeard
import app.recitation.core.VoiceTake
import app.recitation.core.WordRecited
import app.recitation.ui.Exercise
import app.recitation.ui.ExerciseContent
import app.recitation.ui.PracticeTab
import app.recitation.ui.PageRecital
import app.recitation.ui.ReciteContent
import app.recitation.ui.Reciting
import app.recitation.ui.Choices
import app.recitation.ui.Doing
import app.recitation.ui.LocalDoing
import app.recitation.ui.ClearOfTheSides
import app.recitation.ui.CollectionContent
import app.recitation.ui.FollowButton
import app.recitation.ui.HomeContent
import app.recitation.ui.LastRead
import app.recitation.ui.LastReadBar
import app.recitation.ui.LocalSurahNames
import app.recitation.ui.MarkActions
import app.recitation.ui.MenuContent
import app.recitation.ui.NoRecitationBar
import app.recitation.ui.NowPlayingBar
import app.recitation.ui.RangeChooser
import app.recitation.ui.ReadingChoices
import app.recitation.ui.named
import app.recitation.ui.TajweedRulesPage
import app.recitation.ui.LocalTajweedRules
import app.recitation.ui.ReaderContent
import app.recitation.ui.RecitationTheme
import app.recitation.ui.RecordingsContent
import app.recitation.ui.RecordingStats
import app.recitation.ui.SavedStats
import app.recitation.ui.SavedTab
import app.recitation.ui.Plant
import app.recitation.ui.StatsTab
import app.recitation.ui.stageOf
import app.recitation.ui.ScriptChooser
import app.recitation.ui.Status
import app.recitation.ui.StatusActions
import app.recitation.ui.TextSizeControls
import app.recitation.ui.AppIcons
import app.recitation.ui.says
import app.recitation.ui.picture
import app.recitation.ui.WorkIcon
import app.recitation.ui.VerseMarks
import app.recitation.ui.VerseMarksContent
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    private fun Reader(positionMs: Int, meanings: Boolean = true, clarity: Float = 0.9f, verses: List<Verse> = fatihah, playing: Boolean = true) = ReaderContent(
        reading = Reading(1, verses, track, timings(verses, clarity)), showMeanings = meanings, playing = playing,
        position = { positionMs }, duration = 74_000, onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {},
    )

    @Composable
    private fun Recordings(status: Status?, actions: StatusActions = StatusActions(), onCancel: (Track) -> Unit = {}) =
        RecordingsContent(playlist, status, actions, {}, {}, {}, onCancel, { _, _ -> }, {}, {})

    @Composable
    private fun Home(tab: Int, playlists: List<Playlist> = emptyList(), status: Status? = null) =
        HomeContent(tab, {}, playlists, status, StatusActions(), recited = setOf(1, 3, 4, 8), onSurah = {}, onOpen = {}, onAdd = {}, unheard = setOf(4), queued = setOf(5))

    /** What the app is at is shown in pictures: every step and every state of waiting has one of its own. */
    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun everyKindOfWorkHasItsOwnPicture() {
        val steps = Step.entries.map { Status.Working(Progress("b", it, 0.6f), null, surah = 2) }
        val states = listOf(Status.Starting, Status.Paused, Status.WaitingForNetwork, Status.Refused)
        val all: List<Status> = steps + states
        // No two look alike: the icon and the small one at its corner together tell them apart.
        assertEquals(all.size, all.map { it.picture().let { p -> p.icon.name to p.badge?.name } }.toSet().size)
        compose.setContent {
            RecitationTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (status in all) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                WorkIcon(status.picture(), null, size = 40.dp)
                                Text(status.says(), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        // The marks under a surah's number, and beside a recording.
                        for ((icon, says) in listOf(AppIcons.Sound to "Listened to the end", AppIcons.SoundPart to "Listening left off part-way", AppIcons.Hourglass to "Waiting its turn")) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) }
                                Text(says, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/doing-legend.png")
    }

    /** Every icon the app draws, each with a number and its name, on one sheet to look them over. */
    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun everyIconInTheApp() {
        val own = AppIcons::class.java.declaredFields.filter { it.type == androidx.compose.ui.graphics.vector.ImageVector::class.java }
            .map { it.isAccessible = true; it.name to it.get(AppIcons) as androidx.compose.ui.graphics.vector.ImageVector }
        val stock = listOf("Back" to Icons.AutoMirrored.Filled.ArrowBack, "More" to Icons.Filled.MoreVert)
        val all = own + stock
        assertTrue("found ${all.size}", all.size > 30)
        compose.setContent {
            RecitationTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (row in all.withIndex().chunked(5)) {
                            Row(Modifier.fillMaxWidth()) {
                                for ((i, icon) in row) {
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(icon.second, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text("${i + 1}", style = MaterialTheme.typography.labelLarge)
                                        Text(icon.first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/every-icon.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun thePractiseTabOffersRecitingAndTheVoiceExercises() {
        var recite = false
        var chosen: Exercise? = null
        show("en-day-practise") {
            HomeContent(
                4, {}, emptyList(), null, StatusActions(), recited = setOf(1), onSurah = {}, onOpen = {}, onAdd = {},
                practice = { modifier ->
                    PracticeTab(mapOf(Exercise.BREATH to 14.2f, Exercise.STEADY to 18f), onRecite = { recite = true }, onExercise = { chosen = it }, modifier = modifier)
                },
            )
        }
        compose.onNodeWithText("Practise").assertIsDisplayed()
        compose.onNodeWithText("Recite and be corrected").assertIsDisplayed()
        // The voice is trained under its condition, and without any melody being taught.
        compose.onNodeWithText("Choose a verse").performClick()
        assertTrue(recite)
        // The voice exercises come after the tajweed ones, further down the tab.
        compose.onNodeWithText("Tajweed comes first", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("14.2 s").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("± 18 cents").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("A steady note").performScrollTo().performClick()
        assertTrue(chosen == Exercise.STEADY)
    }

    /** A voice holding a note about 196 Hz, wavering a little, for some seconds, as the looks at it would read. */
    private fun heldNote(looks: Int = 320) = FloatArray(looks) { 196f * Math.pow(2.0, 0.012 * kotlin.math.sin(it / 9.0)).toFloat() }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aVoiceExerciseDrawsTheVoiceAndSaysHowSteadyItWas() {
        val pitches = heldNote()
        var toggled = false
        show("en-day-voice-steady") {
            ExerciseContent(
                Exercise.STEADY, listening = false, pitches = pitches.toList(), take = VoiceTake(pitches, FloatArray(pitches.size) { 0.1f }),
                best = 12f, problem = null, onToggle = { toggled = true }, onBack = {},
            )
        }
        compose.onNodeWithText("A steady note").assertIsDisplayed()
        // The note strayed about a seventh of a semitone: steady, and said so.
        compose.onNodeWithText("± 10 cents").assertIsDisplayed()
        compose.onNodeWithText("Very steady").assertIsDisplayed()
        compose.onNodeWithText("Best so far: ± 12 cents").assertIsDisplayed()
        compose.onNodeWithContentDescription("Start").performClick()
        assertTrue(toggled)
    }

    @Test
    @Config(qualifiers = "ar-rSA-w411dp-h891dp-night-420dpi")
    fun theRangeExerciseInArabicByNight() {
        val slide = FloatArray(300) { 110f * Math.pow(2.0, 17.0 / 12 * it / 300).toFloat() }
        show("ar-night-voice-range") {
            ExerciseContent(
                Exercise.RANGE, listening = true, pitches = slide.toList(), take = VoiceTake(slide, FloatArray(slide.size) { 0.1f }),
                best = 0f, problem = null, onToggle = {}, onBack = {},
            )
        }
        compose.onNodeWithText("مدى الصوت").assertIsDisplayed()
        compose.onNodeWithText("القرار").assertIsDisplayed()
        compose.onNodeWithText("الجواب").assertIsDisplayed()
        // While it listens the button is the one that stops it.
        compose.onNodeWithContentDescription("توقّف").assertIsDisplayed()
    }

    /** 1:2 as recited with two things to go over: ح said as ه in its first word, and the madd of its last word not held. */
    private val recitedWithMistakes = listOf(
        WordRecited(0, false, listOf(Correction(Fault.LETTER, clear = true, wanted = "ح", heard = "ه")), emptyList()),
        WordRecited(1, false, emptyList(), emptyList()),
        WordRecited(2, false, emptyList(), emptyList()),
        WordRecited(3, false, listOf(Correction(Fault.MADD_SHORT, clear = false, madd = Madd.MUTTASIL, wantedCounts = 4, heardCounts = 2)), listOf(MaddHeard(Madd.ARID, 4))),
    )

    @Composable
    private fun Recite(state: Reciting, needsModel: Boolean = false, verse: Int = 2, reached: Int = -1, onMic: () -> Unit = {}, onFetch: () -> Unit = {}) = ReciteContent(
        app.recitation.core.SURAHS[0], verse, fatihah[verse - 1], state, needsModel, onMic, onFetch, onPrevious = {}, onNext = {}, onBack = {}, reached = reached,
    )

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h1500dp-420dpi")
    fun thePractiseTabOffersTheTajweedExercises() {
        var drilled: Pair<Topic, String>? = null
        var started = ""
        val tally = Tally(
            mapOf(Topic.IKHFA to 5, Topic.MADD_MUTTASIL to 3, Topic.LETTERS to 1), listOf(Drill(78, 2, Topic.IKHFA), Drill(1, 6, Topic.LETTERS, "صس")),
            learned = setOf("GHUNNAH", "QALQALAH", "LETTERS:قك"),
        )
        show("en-day-practise-tajweed") {
            HomeContent(
                4, {}, emptyList(), null, StatusActions(), recited = setOf(1), onSurah = {}, onOpen = {}, onAdd = {},
                practice = { modifier ->
                    PracticeTab(
                        emptyMap(), onRecite = {}, onExercise = {}, modifier = modifier, tally = tally, canEcho = true,
                        onLesson = { topic, letters -> drilled = topic to letters }, onWeak = { started = "weak" }, onMadd = { started = "madd" },
                        onSpot = { started = "spot" }, onEcho = { started = "echo" },
                    )
                },
            )
        }
        // A lesson for each rule, by the rule's own name with the dot of its colour, ticked once it is learned; and one for each pair of letters.
        compose.onNodeWithTag("lesson-IKHFA").assertIsDisplayed()
        compose.onNodeWithText("2 of 10 learned").assertIsDisplayed()
        compose.onNodeWithTag("lesson-QALQALAH").performClick()
        assertEquals(Topic.QALQALAH to "", drilled)
        compose.onNodeWithTag("pair-قك").performScrollTo().performClick()
        assertEquals(Topic.LETTERS to "قك", drilled)
        compose.onNodeWithTag("stops-go").performScrollTo().performClick()
        assertEquals(Topic.STOP to "", drilled)
        // What the reader has been corrected on most, and the way to go over it.
        compose.onNodeWithTag("weak-go").performScrollTo().performClick()
        assertEquals("weak", started)
        for (go in listOf("madd", "spot", "echo")) {
            compose.onNodeWithTag("$go-go").performScrollTo().performClick()
            assertEquals(go, started)
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h1300dp-420dpi")
    fun aRuleIsTaughtBeforeItIsPractised() {
        var heard: Hear? = null
        var practise = false
        val hears = listOf(Hear("مِن قَبْلِ", File("a.m4a"), 12_000, 14_000), Hear("أَنتُمْ", File("a.m4a"), 40_000, 41_500))
        show("en-day-lesson") { LessonContent(Topic.IKHFA, "", hears, learned = true, onHear = { heard = it }, onPractise = { practise = true }, onBack = {}) }
        // The reciter's voice comes first, before anything is said about the rule.
        val hearAt = compose.onAllNodesWithTag("lesson-hear").onFirst().fetchSemanticsNode().boundsInRoot.top
        assertTrue(hearAt < compose.onNodeWithText("When").fetchSemanticsNode().boundsInRoot.top)
        // Then when it comes, how it is said, and the slip most often made.
        compose.onNodeWithText("fifteen letters", substring = true).assertIsDisplayed()
        compose.onNodeWithText("When").assertIsDisplayed()
        compose.onNodeWithText("shape the mouth for the letter that follows", substring = true).assertIsDisplayed()
        compose.onNodeWithText("The common slip").assertIsDisplayed()
        compose.onNodeWithContentDescription("Learned").assertIsDisplayed()
        // Words from the reader's own recordings that show it, each played at a tap.
        compose.onAllNodesWithTag("lesson-hear").onFirst().performScrollTo().performClick()
        assertEquals(12_000, heard?.fromMs)
        compose.onNodeWithTag("lesson-practise").performClick()
        assertTrue(practise)
    }

    @Test
    @Config(qualifiers = "ar-w411dp-h1300dp-night-420dpi")
    fun aLessonByNightInArabic() {
        show("ar-night-lesson") { LessonContent(Topic.MADD_LAZIM, "", emptyList(), learned = false, onHear = {}, onPractise = {}, onBack = {}) }
        compose.onNodeWithText("ستّ حركات دائماً", substring = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h1300dp-420dpi")
    fun twoLettersMadeCloseTogetherAreTaughtSideBySide() {
        show("en-day-lesson-pair") { LessonContent(Topic.LETTERS, "قك", emptyList(), learned = false, onHear = {}, onPractise = {}, onBack = {}) }
        // Where each is made, and how they differ.
        compose.onNodeWithText("ق is the deepest part of the tongue", substring = true).assertIsDisplayed()
        compose.onNodeWithText("ك is the back of the tongue", substring = true).assertIsDisplayed()
        compose.onNodeWithText("How they differ").assertIsDisplayed()
    }

    @Test
    fun everyTopicHasALesson() {
        for (topic in Topic.entries) {
            if (topic == Topic.LETTERS) for (pair in Drills.PAIRS) assertTrue(lessonOf(topic, pair).examples.isNotEmpty()) else assertTrue(lessonOf(topic).examples.isNotEmpty())
        }
        // The seven pairs have seven lessons, no two the same.
        assertEquals(7, Drills.PAIRS.map { lessonOf(Topic.LETTERS, it).how }.toSet().size)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aVerseCanBeHeardFromTheReciterBeforeItIsRecited() {
        var heard = 0
        show("en-day-drill-hear") {
            ReciteContent(
                app.recitation.core.SURAHS[0], 2, fatihah[1], Reciting.Idle, needsModel = false, onMic = {}, onFetch = {}, onPrevious = {}, onNext = {}, onBack = {},
                title = "Ghunnah of a doubled n or m", how = "Recite this verse. Only Ghunnah of a doubled n or m is judged.", onHear = { heard++ },
            )
        }
        compose.onNodeWithText("Hear the reciter").performClick()
        assertEquals(1, heard)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun whereNoRecordingHasTheVerseThereIsNothingToHear() {
        compose.setContent {
            RecitationTheme {
                ReciteContent(app.recitation.core.SURAHS[0], 2, fatihah[1], Reciting.Idle, needsModel = false, onMic = {}, onFetch = {}, onPrevious = {}, onNext = {}, onBack = {})
            }
        }
        compose.onNodeWithTag("hear-reciter").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theRecitersMaddsCanBeHeardFromTheTableOfTheirLengths() {
        var asked: Topic? = null
        show("en-day-madd-hear") {
            MaddContent(4, 0.5f, listening = false, seconds = 0f, holds = emptyList(), problem = null, onTarget = {}, onPace = {}, onToggle = {}, onBack = {}, onHearMadd = { asked = it })
        }
        compose.onNodeWithTag("madd-row-MADD_LAZIM").performClick()
        assertEquals(Topic.MADD_LAZIM, asked)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aGoThatWasNotSoundIsTaughtAgainThereAndThen() {
        val drill = Drill(1, 2, Topic.QALQALAH)
        var heard = 0
        show("en-day-drill-taught") { Column(Modifier.padding(16.dp)) { DrillVerdict(drill, Went.MISSED, onHear = { heard++ }) } }
        compose.onNodeWithText("To go over: Qalqalah").assertIsDisplayed()
        compose.onNodeWithText("It is not a vowel", substring = true).assertIsDisplayed()
        // And in the reciter's voice: the words it was about, as he says them.
        compose.onNodeWithText("Hear how the reciter says it").performClick()
        assertEquals(1, heard)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aDrillJudgesAVerseForItsRuleAlone() {
        // 1:2 recited for its ghunnah: a madd in it was held short, which is marked as ever, but the ghunnah was sound.
        val ghunnah = listOf(Awaited(0, 4, Listened.GHUNNAH, 1, 4, symbol = 'ل'))
        val heard = List(4) { w ->
            WordRecited(w, false, if (w == 3) listOf(Correction(Fault.MADD_SHORT, clear = false, madd = Madd.ARID, wantedCounts = 4, heardCounts = 1)) else emptyList(), emptyList(), listened = if (w == 1) ghunnah else emptyList())
        }
        val drill = Drill(1, 2, Topic.GHUNNAH)
        show("en-day-drill") {
            ReciteContent(
                app.recitation.core.SURAHS[0], 2, fatihah[1], Reciting.Heard(heard), needsModel = false, onMic = {}, onFetch = {}, onPrevious = {}, onNext = {}, onBack = {},
                title = "Ghunnah of a doubled n or m", subtitle = "3 of 8 · Al-Fatihah · verse 2",
                verdict = { DrillVerdict(drill, Drills.went(drill, heard)) }, canPrevious = true, canNext = true,
            )
        }
        compose.onNodeWithText("Sound: Ghunnah of a doubled n or m").assertIsDisplayed()
        compose.onNodeWithText("3 of 4 words sound").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aRuleInAWordThatWasNotSaidIsNotJudged() {
        // 1:2 recited for its ghunnah, but only its first word was said: the word with the ghunnah wasn't, so
        // there is nothing to say of the ghunnah. The words not said are told as that, and as nothing else.
        val unsaid = listOf(Correction(Fault.WORD_MISSING, clear = true))
        val heard = List(4) { w -> WordRecited(w, false, if (w == 0) emptyList() else unsaid, emptyList()) }
        val drill = Drill(1, 2, Topic.GHUNNAH)
        assertEquals(Went.UNHEARD, Drills.went(drill, heard))
        show("en-day-drill-unheard") {
            ReciteContent(
                app.recitation.core.SURAHS[0], 2, fatihah[1], Reciting.Heard(heard), needsModel = false, onMic = {}, onFetch = {}, onPrevious = {}, onNext = {}, onBack = {},
                title = "Ghunnah of a doubled n or m", subtitle = "3 of 8 · Al-Fatihah · verse 2",
                verdict = { DrillVerdict(drill, Drills.went(drill, heard), onHear = {}) }, canPrevious = true, canNext = true,
            )
        }
        compose.onNodeWithText("Not heard: Ghunnah of a doubled n or m.", substring = true).assertIsDisplayed()
        // Nothing is taught over it: there was no mistake.
        compose.onNodeWithTag("drill-teach").assertDoesNotExist()
        compose.onNodeWithTag("hear-it").assertDoesNotExist()
        compose.onNodeWithTag("rule-family").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aStopIsMarkedOnTheWordToStopOn() {
        // 2:8, to be stopped in after its fifth word.
        val drill = Drill(2, 8, Topic.STOP, stopAfter = 4)
        show("en-day-drill-stop") {
            ReciteContent(
                app.recitation.core.SURAHS[1], 8, ruled[1], Reciting.Idle, needsModel = false, onMic = {}, onFetch = {}, onPrevious = {}, onNext = {}, onBack = {},
                title = "Stopping on a word", how = "Recite this verse and stop on the marked word; then carry on, from a word or two back if you like.", stopAfter = drill.stopAfter,
            )
        }
        compose.onNodeWithTag("stop-here", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("stop on the marked word", substring = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun anExerciseEndsWithHowManyOfItsVersesWereSound() {
        var again = false
        val drills = listOf(Drill(78, 2, Topic.IKHFA), Drill(78, 5, Topic.IKHFA), Drill(79, 3, Topic.IKHFA))
        show("en-day-drill-over") { DrillOver("Ikhfa’ haqiqi", drills, mapOf(0 to Went.SOUND, 1 to Went.MISSED, 2 to Went.UNHEARD), onAgain = { again = true }, onBack = {}) }
        compose.onNodeWithText("1 of 3 sound").assertIsDisplayed()
        compose.onNodeWithText("Once more").performClick()
        assertTrue(again)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aMaddIsCountedAsItIsHeld() {
        var target = 4
        // Aiming for four counts at half a second a count; two holds so far, and the third 1.6 s in.
        show("en-day-madd-holding") {
            MaddContent(4, 0.5f, listening = true, seconds = 1.6f, holds = listOf(2.1f, 1.4f), problem = null, onTarget = { target = it }, onPace = {}, onToggle = {}, onBack = {})
        }
        compose.onNodeWithText("3.2 counts").assertIsDisplayed()
        compose.onNodeWithTag("madd-6").performClick()
        assertEquals(6, target)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aHoldThatEndsIsJudgedAgainstTheCountsAimedFor() {
        show("en-day-madd-held") {
            MaddContent(4, 0.5f, listening = true, seconds = 0f, holds = listOf(2.1f, 1.4f, 2.0f, 2.9f), problem = null, onTarget = {}, onPace = {}, onToggle = {}, onBack = {})
        }
        // The last hold was 2.9 s: nearly six counts, past the four aimed for.
        compose.onNodeWithText("5.8 counts").assertIsDisplayed()
        compose.onNodeWithText("Past 4").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theRuleInAWordIsSpottedAmongFour() {
        val spots = listOf(
            Spot(2, 8, ruled[1].words[4].arabic, Family.MADD_TABII, listOf(Family.NASAL, Family.MADD_TABII, Family.ECHO, Family.HEAVY), rule = app.recitation.core.Rule.MADD_TABII),
            Spot(1, 2, fatihah[1].words[2].arabic, Family.HEAVY, listOf(Family.HEAVY, Family.NASAL, Family.ECHO, Family.MADD_WAJIB)),
        )
        show("en-day-spot") { SpotContent(spots, onPlay = {}, onBack = {}) }
        compose.onNodeWithText("Which rule is in this word?").assertIsDisplayed()
        // A wrong answer shows the right one, and what that family of rules is.
        compose.onNodeWithTag("spot-NASAL").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/en-day-spot-answered.png")
        compose.onNodeWithTag("spot-note", useUnmergedTree = true).assertIsDisplayed()
        // It is taught: the rule by its name, and what the rule is.
        compose.onAllNodesWithText("Madd tabi‘i", useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithTag("spot-next").performClick()
        compose.onNodeWithTag("spot-HEAVY").performClick()
        compose.onNodeWithTag("spot-next").performClick()
        compose.onNodeWithText("1 of 2 sound").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theRecitersMaddsStandBesideTheReaders() {
        // 2:8, stopped on: the reciter held the madd of its last word for four counts, the reader for two.
        fun words(counts: Int) = List(11) { w -> WordRecited(w, false, emptyList(), if (w == 10) listOf(MaddHeard(Madd.ARID, counts)) else emptyList()) }
        show("en-day-echo-madds") {
            Column(Modifier.padding(16.dp)) { MaddsBeside(ruled[1], words(4), words(2)) }
        }
        compose.onNodeWithText("Reciter 4").assertIsDisplayed()
        compose.onNodeWithText("You 2").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theWordBeingRecitedIsLitWhileTheMicrophoneIsOpen() {
        // The reader has got to the third word of the verse.
        show("en-day-recite-listening") { Recite(Reciting.Listening, reached = 2) }
        val lit = compose.onNodeWithTag("reciting-word", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val word = compose.onNodeWithText(fatihah[1].words[2].arabic).fetchSemanticsNode().boundsInRoot
        assertTrue("lit $lit, the third word $word", lit.overlaps(word))
        // While it listens the button is the one that ends the recitation.
        compose.onNodeWithContentDescription("Done reciting").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun atASurahsFirstVerseThereIsNoGoingBack() {
        show("en-day-recite-first-verse") { Recite(Reciting.Idle, verse = 1) }
        compose.onNodeWithContentDescription("Previous verse").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next verse").assertIsEnabled()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun whileTheModelIsFetchedInTheBackgroundRecitingShowsHowFar() {
        show("en-day-recite-fetching") { Recite(Reciting.Fetching(R.string.recite_getting_model, 0.4f), needsModel = true) }
        // A picture with its ring, the words behind it; and no button to press, as it is on its way.
        compose.onNodeWithContentDescription("Fetching the tajweed model… 40%").assertIsDisplayed()
        compose.onNodeWithText("Download it").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun recitingAVerseMarksWhatToGoOverAndTeachesIt() {
        show("en-day-recite-heard") { Recite(Reciting.Heard(recitedWithMistakes)) }
        compose.onNodeWithText("2 of 4 words sound").assertIsDisplayed()
        // The first marked word is opened: what was said in place of what, and how the letter is made.
        compose.onNodeWithText("Clear mistake").assertIsDisplayed()
        compose.onNodeWithText("ه said in place of ح").assertIsDisplayed()
        compose.onNodeWithText("middle of the throat", substring = true).assertIsDisplayed()
        // Tapping the other marked word shows its own: a fine point, with the lengths.
        compose.onNodeWithText("ٱلْعَـٰلَمِينَ").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/en-day-recite-madd.png")
        compose.onNodeWithText("Fine point").assertIsDisplayed()
        compose.onNodeWithText("Joined madd (muttasil) held too short").assertIsDisplayed()
        compose.onNodeWithText("It was held for 2 counts and is due 4.", substring = true).assertIsDisplayed()
        // It never claims to be a teacher.
        compose.onNodeWithText("This can be wrong.", substring = true).assertExists()
        compose.onNodeWithContentDescription("Recite it again").assertIsDisplayed()
    }

    /**
     * 2:8 as recited to the phone on its own page, with something to go over in four of its
     * words: the ghunnah of «ٱلنَّاسِ» cut short, the nun of «مَن» not merged into the ي after
     * it, ق said as ك in «يَقُولُ», and the madd of the last word, stopped on, held too short.
     */
    private val recitedOnThePage = List(11) { w ->
        WordRecited(
            w, false,
            when (w) {
                1 -> listOf(Correction(Fault.GHUNNAH_SHORT, clear = false, wanted = "ن"))
                2 -> listOf(Correction(Fault.IDGHAM_MISSED, clear = false, wanted = "ي"))
                3 -> listOf(Correction(Fault.LETTER, clear = true, wanted = "ق", heard = "ك"))
                10 -> listOf(Correction(Fault.MADD_SHORT, clear = false, madd = Madd.ARID, wantedCounts = 4, heardCounts = 1))
                else -> emptyList()
            },
            emptyList(),
        )
    }

    @Composable
    private fun PageRecited(
        recital: PageRecital?, playing: Boolean = false, tajweed: Boolean = true, onPlayPause: () -> Unit = {},
    ) = ReaderContent(
        reading = Reading(2, ruled), showMeanings = false, playing = playing, position = { 0 }, duration = 0,
        onBack = {}, onMeanings = {}, onPlayPause = onPlayPause, onSeek = {}, tajweed = tajweed, recital = recital,
    )

    private fun recital(verse: Int = 0, state: Reciting = Reciting.Idle, reached: Int = -1, heard: Map<Int, List<WordRecited>> = emptyMap(), onToggle: () -> Unit = {}, onForget: (Int) -> Unit = {}) =
        PageRecital(verse, state, reached, heard, onToggle, onForget)

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun recitingToThePhoneIsAskedForFromTheSurahsMenu() {
        var asked = 0
        var paused = false
        compose.setContent { RecitationTheme { PageRecited(recital(onToggle = { asked++ }), playing = true, onPlayPause = { paused = true }) } }
        // No microphone beside the verses: the phone finds the verse by itself.
        compose.onAllNodesWithTag("verse-mic-open").assertCountEquals(0)
        compose.onNodeWithTag("recite-bar").assertDoesNotExist()
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/en-day-reader-menu-recite.png")
        compose.onNodeWithText("Recite to the phone").assertIsDisplayed()
        compose.onNodeWithTag("recite-to-phone").performClick()
        assertEquals(1, asked)
        // It is the reader's voice that is listened to: the recording stops.
        assertTrue(paused)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun whereRecitingIsNotOfferedTheMenuHasNoneOfIt() {
        compose.setContent { RecitationTheme { PageRecited(null) } }
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithTag("recite-to-phone").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theVerseAndTheWordBeingRecitedToThePhoneAreFollowed() {
        var asked = 0
        // The reader has recited 2:7, which was gone over as they went on, and is on the fourth word of 2:8.
        val first = List(ruled[0].words.size) { WordRecited(it, false, if (it == 2) listOf(Correction(Fault.GHUNNAH_SHORT, clear = false, wanted = "ن")) else emptyList(), emptyList()) }
        show("en-day-page-reciting") { PageRecited(recital(verse = 2, state = Reciting.Listening, reached = 3, heard = mapOf(1 to first), onToggle = { asked++ })) }
        // In the player's place: the microphone, the verse they are at, how the words so far went, and the button that ends it.
        compose.onNodeWithTag("recite-bar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Listening to you recite").assertIsDisplayed()
        compose.onNodeWithTag("recite-verse").assertIsDisplayed()
        compose.onNodeWithTag("recite-tally").assertIsDisplayed()
        compose.onNodeWithTag("recite-stop").performClick()
        assertEquals(1, asked)
        // The verse gone over has its mark on the page already.
        assertEquals(1, compose.onAllNodesWithTag("marked-word").fetchSemanticsNodes().size + compose.onAllNodesWithTag("marked-word-picked").fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = "ar-w411dp-h891dp-night-420dpi")
    fun recitingToThePhoneByNightInArabic() {
        show("ar-night-page-reciting") { PageRecited(recital(verse = 2, state = Reciting.Listening, reached = 3, heard = mapOf(1 to recitedOnThePage.take(ruled[0].words.size)))) }
        compose.onNodeWithTag("recite-bar").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun whenTheReaderIsDoneTheLastVerseIsGoneOver() {
        show("en-day-page-reciting-done") { PageRecited(recital(verse = 2, state = Reciting.Checking)) }
        compose.onNodeWithContentDescription("Listening to what you recited").assertIsDisplayed()
        compose.onNodeWithTag("recite-stop").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun whatToGoOverIsMarkedOnThePageInTheColoursOfTheRules() {
        var rules = false
        var forgot = 0
        compose.setContent {
            CompositionLocalProvider(LocalSurahNames provides surahNames, LocalTajweedRules provides { rules = true }) {
                RecitationTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        PageRecited(recital(verse = 2, state = Reciting.Heard(recitedOnThePage), heard = mapOf(2 to recitedOnThePage), onForget = { forgot = it }))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/en-day-page-recited.png")
        // Beside the verse's number: how many of its words were sound.
        compose.onNodeWithContentDescription("7 of 11 words sound").assertIsDisplayed()
        // Four words are marked, and the first of them is open under the verse: a point of
        // tajweed, headed by its family of rules as the legend of the colours names it.
        assertEquals(4, compose.onAllNodesWithTag("marked-word").fetchSemanticsNodes().size + compose.onAllNodesWithTag("marked-word-picked").fetchSemanticsNodes().size)
        compose.onNodeWithText("Ghunnah cut short").assertIsDisplayed()
        compose.onNodeWithText("Ghunnah").assertIsDisplayed()
        // The family's name leads to the page that sets the rules out.
        compose.onNodeWithTag("rule-family").performClick()
        assertTrue(rules)
        // Another marked word shows its own: a mistake in the wording, with no rule to name.
        compose.onNodeWithText(ruled[1].words[3].madinah, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/en-day-page-recited-letter.png")
        compose.onNodeWithText("ك said in place of ق").assertIsDisplayed()
        compose.onNodeWithTag("rule-family").assertDoesNotExist()
        // A tap on the verse's result takes the corrections off the page.
        compose.onNodeWithTag("recited").performClick()
        assertEquals(2, forgot)
    }

    @Test
    @Config(qualifiers = "ar-w411dp-h891dp-night-420dpi")
    fun whatToGoOverIsMarkedOnThePageByNightInArabic() {
        show("ar-night-page-recited") { PageRecited(recital(verse = 2, state = Reciting.Heard(recitedOnThePage), heard = mapOf(2 to recitedOnThePage))) }
        compose.onNodeWithText("الغُنَّة").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theMarksKeepTheRulesColoursWithTheColoursOfThePageOff() {
        show("en-day-page-recited-plain") { PageRecited(recital(verse = 2, state = Reciting.Heard(recitedOnThePage), heard = mapOf(2 to recitedOnThePage)), tajweed = false) }
        compose.onNodeWithText("Ghunnah cut short").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun recitingAskedForBeforeTheModelIsHereShowsItBeingFetched() {
        show("en-day-page-fetching") { PageRecited(recital(state = Reciting.Fetching(R.string.recite_getting_model, 0.4f))) }
        compose.onNodeWithContentDescription("Fetching the tajweed model… 40%").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aRecitationThatCouldNotBeHeardSaysSoInItsBar() {
        var put = 0
        show("en-day-page-not-heard") { PageRecited(recital(state = Reciting.Failed("Nothing was heard. Recite a little louder, closer to the phone."), onToggle = { put++ })) }
        compose.onNodeWithText("Nothing was heard.", substring = true).assertIsDisplayed()
        // Its cross puts it away.
        compose.onNodeWithContentDescription("Cancel").performClick()
        assertEquals(1, put)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun beforeItsModelIsFetchedRecitingAsksForIt() {
        var fetched = false
        var mic = false
        show("en-day-recite-model") { Recite(Reciting.Idle, needsModel = true, onMic = { mic = true }, onFetch = { fetched = true }) }
        compose.onNodeWithText("Checking a recitation needs a small tajweed model (23 MB), fetched once and kept on the phone.").assertIsDisplayed()
        // The microphone does nothing until the model is there.
        compose.onNodeWithContentDescription("Start").performClick()
        assertTrue(!mic)
        compose.onNodeWithText("Download it").performClick()
        assertTrue(fetched)
    }

    @Test
    @Config(qualifiers = "ar-rSA-w411dp-h891dp-night-420dpi")
    fun recitingInArabicByNight() {
        show("ar-night-recite-heard") { Recite(Reciting.Heard(recitedWithMistakes)) }
        compose.onNodeWithText("لحن جليّ").assertIsDisplayed()
        compose.onNodeWithText("نُطق ه بدل ح").assertIsDisplayed()
        compose.onNodeWithText("الحاء من وسط الحلق", substring = true).assertIsDisplayed()
    }

    /** What every word should sound like is in the app's package, for all 114 surahs, and reads as the checker needs it. */
    @Test
    fun theReferenceForEveryVerseIsInThePackage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var verses = 0
        var words = 0
        for (surah in app.recitation.core.SURAHS) {
            val forms = context.assets.open("tajweed_reference.bin").use { app.recitation.core.TajweedReference.surah(it, surah.number) }
            assertEquals("surah ${surah.number}", surah.verses, forms.size)
            verses += forms.size
            words += forms.sumOf { it.size }
            assertTrue(forms.all { verse -> verse.isNotEmpty() && verse.all { it.inside.script.isNotEmpty() || it.first.script.isNotEmpty() } })
        }
        assertEquals(6236, verses)
        assertEquals(77433, words)
        // Al-Fatihah's second verse, said in one breath: its last word in the form it takes at a stop, its madd four counts long.
        val verse = context.assets.open("tajweed_reference.bin").use { app.recitation.core.TajweedReference.surah(it, 1) }[1]
        assertEquals(fatihah[1].words.size, verse.size)
        assertEquals("ءَلحَمدُلِللَااهِرَببِلعَاالَمِۦۦۦۦن", app.recitation.core.Expected.breath(verse, 0, verse.size).script)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun whileTheModelListensItIsShownAsAPicture() {
        show("en-day-recite-checking") { Recite(Reciting.Checking) }
        compose.onNodeWithContentDescription("Listening to what you recited").assertIsDisplayed()
        compose.onNodeWithText("Listening to what you recited").assertDoesNotExist()
    }

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

    /**
     * The launcher icon as phones show it: cut to a rounded square, and to a circle. Under
     * them, in one colour as Android's themed icons draw it, and the small one for the
     * status bar, enlarged.
     */
    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theAppIcon() {
        show("icon") {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    for (shape in listOf(RoundedCornerShape(22.dp), CircleShape)) {
                        // Launchers show the middle 72 of the icon's 108 units.
                        Box(Modifier.size(144.dp).clip(shape), contentAlignment = Alignment.Center) {
                            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize(216.dp))
                            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(216.dp))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(Modifier.size(144.dp).clip(CircleShape).background(Color(0xFF2B3033)), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.ic_launcher_monochrome), null, Modifier.requiredSize(216.dp))
                    }
                    Box(Modifier.size(144.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF2B3033)), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.ic_notification), null, Modifier.size(96.dp))
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
        // It is reciting, and the page has gone on with it: the top bar has slid out of the way.
        compose.onNodeWithTag("surah-title").assertIsNotDisplayed().assertContentDescriptionEquals("سورة الفاتحة")
        compose.onNodeWithText("1 · Al-Fatihah · 7 verses").assertIsNotDisplayed()
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
        show("en-day-reader-no-meanings") { Reader(7_500, meanings = false, playing = false) }
        compose.onNodeWithText("of the universe").assertDoesNotExist()
        // Showing them again is in the surah's menu, with the text size and the script.
        compose.onNodeWithContentDescription("Menu").assertIsDisplayed()
        compose.onNodeWithContentDescription("Word meanings").assertDoesNotExist()
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
        // The title page fills the room above it with the surah's details. The basmala isn't
        // drawn on it for Al-Fatihah: it is the surah's first verse, and would be there twice.
        compose.onNodeWithText("1 · Al-Fatihah · The Opener").assertIsDisplayed()
        compose.onNodeWithText("7 verses · Meccan · No. 5 in order of revelation").assertIsDisplayed()
        compose.onNodeWithContentDescription("بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ").assertDoesNotExist()
        assertTrue("first word off by ${offCentre("In (the) name")} px", abs(offCentre("In (the) name")) < 60f)
        // At the surah's start the top bar is in its place.
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theTopBarIsOutOfTheWayWhileItRecitesAndBackWhenPaused() {
        var playing by mutableStateOf(true)
        show("en-day-reader-reciting") {
            ReaderContent(
                reading = Reading(1, fatihah, track, timings(fatihah)), showMeanings = true, playing = playing,
                position = { 17_500 }, duration = 74_000, onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {},
            )
        }
        compose.onNodeWithContentDescription("Back").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("Menu").assertIsNotDisplayed()
        playing = false
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/en-day-reader-paused.png")
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithContentDescription("Menu").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theTopBarSlidesAwayAsThePageIsScrolledOnAndIsBackWhenItIsPulledBack() {
        // A surah read by hand, with nothing reciting: forty verses, several screens of them.
        val many = List(40) { fatihah[it % fatihah.size] }
        show("en-day-reader-top") {
            ReaderContent(
                reading = Reading(2, many), showMeanings = true, playing = false, position = { 0 }, duration = 0,
                onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {},
            )
        }
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithTag("verses").performTouchInput { swipeUp(startY = bottom - 200f, endY = top + 200f) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/en-day-reader-scrolled-on.png")
        compose.onNodeWithContentDescription("Back").assertIsNotDisplayed()
        compose.onNodeWithTag("surah-title").assertIsNotDisplayed()
        // A short pull back is enough, wherever in the surah the page is.
        compose.onNodeWithTag("verses").performTouchInput { swipeDown(startY = centerY, endY = centerY + 300f) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/en-day-reader-pulled-back.png")
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithTag("surah-title").assertIsDisplayed()
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
        show("ar-night-reader") { Reader(12_400, meanings = false, playing = false) }
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
        compose.onNodeWithText("doesn't sound like Surah الفاتحة · Al-Fatihah", substring = true).assertIsDisplayed()
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
        val status = Status.Working(Progress("b", Step.DOWNLOAD, 0.2f), "Surah Al-Baqarah", surah = 2)
        show("en-day-quran") { CompositionLocalProvider(LocalDoing provides Doing(status, StatusActions())) { Home(tab = 0, playlists = listOf(playlist), status = status) } }
        compose.onNodeWithText("Quran").assertIsDisplayed()
        compose.onNodeWithText("Al-Fatihah").assertIsDisplayed()
        compose.onNodeWithText("The Opener · 7 verses").assertIsDisplayed()
        compose.onNodeWithText("Al-Baqarah").assertIsDisplayed()
        // The surahs can be read while a download is under way. What is being done is shown as a
        // picture in two places: in the top bar, and under the number of the surah it is for.
        // The list itself is the surahs': the card with the buttons opens from the top bar.
        compose.onAllNodesWithContentDescription("Downloading the sound… 20%", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithText("Downloading the sound… 20%").assertCountEquals(0)
        // A surah waiting its turn has an hourglass under its number.
        compose.onNodeWithContentDescription("Waiting its turn", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aRecitationCarriesOnWhileOtherScreensAreOpen() {
        var opened = false
        var toggled = false
        var stepped = 0
        show("en-day-now-reciting") {
            HomeContent(
                0, {}, listOf(playlist), null, StatusActions(), recited = setOf(1), onSurah = {}, onOpen = {}, onAdd = {},
                nowPlaying = {
                    NowPlayingBar(
                        SURAHS[1], playing = true, position = 3_725_000, duration = 8_927_000, verse = 120,
                        onPlayPause = { toggled = true }, onPrevious = { stepped-- }, onNext = { stepped++ }, onOpen = { opened = true }, onStop = {},
                    )
                },
            )
        }
        // It does what the surah's player does: the verse being recited, the time, and from verse to verse.
        compose.onNodeWithText("Verse 120", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("1:02:05 / 2:28:47", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Next verse").performClick()
        assertEquals(1, stepped)
        // That it is reciting is a picture, the bars of sound; the words are for a screen reader.
        compose.onNodeWithContentDescription("Reciting now").assertIsDisplayed()
        compose.onNodeWithText("Reciting now").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pause").performClick()
        assertTrue(toggled)
        // Tapping the bar goes back to the surah being recited.
        compose.onNodeWithContentDescription("Reciting now").performClick()
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
        compose.onNodeWithText("Last read: البقرة · Al-Baqarah, verse 45").assertIsDisplayed()
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
        // Choosing them is in the surah's menu, which says which are chosen; from there the chooser opens.
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/en-day-reader-menu-range.png")
        compose.onNodeWithText("2 – 4").assertIsDisplayed()
        // (The chooser itself isn't opened here: its slider, inside a dialog, never comes to rest under these tests.)
        compose.onNodeWithText("Which verses to recite").assertHasClickAction()
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
                        reading = Reading(1, fatihah, track, timings(fatihah)), showMeanings = true, playing = false,
                        position = { 7_500 }, duration = 74_000, onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {},
                        textScale = scale,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Menu").assertIsDisplayed()
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

    /** A recording being listened to and another whose sound is being downloaded meanwhile. */
    private val twoLanes = Status.Working(
        Progress("b", Step.LISTEN, 0.43f, speed = 1.1f), "Surah Al-Baqarah", surah = 2,
        fetching = Status.Working(Progress("c", Step.DOWNLOAD, 0.2f), "Surah Al-imran", surah = 3),
    )

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aSoundBeingDownloadedShowsBesideTheListening() {
        show("en-day-recordings-two-lanes") { Recordings(twoLanes) }
        // On the card: the listening, and under it the download with the surah it is for. On the list: each recording's own ring.
        compose.onNodeWithTag("fetching-beside").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Listening to the recitation… 43%").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Downloading the sound… 20%").assertCountEquals(2)
        // The one being downloaded isn't shown as waiting its turn.
        compose.onAllNodesWithContentDescription("Waiting its turn").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun inTheListOfSurahsBothTheOneListenedToAndTheOneDownloadedShowTheirRings() {
        show("en-day-quran-two-lanes") { Home(tab = 0, playlists = listOf(playlist), status = twoLanes) }
        compose.onNodeWithContentDescription("Listening to the recitation… 43%").assertIsDisplayed()
        compose.onNodeWithContentDescription("Downloading the sound… 20%").assertIsDisplayed()
    }

    @Test
    fun theWorkOnARecordingIsFoundWhicheverLaneItIsIn() {
        assertEquals(Step.LISTEN, twoLanes.on("b")?.progress?.step)
        assertEquals(Step.DOWNLOAD, twoLanes.on("c")?.progress?.step)
        assertNull(twoLanes.on("d"))
        assertNull(twoLanes.on(null))
        assertEquals(Step.DOWNLOAD, twoLanes.onSurah(3)?.progress?.step)
        assertNull(twoLanes.onSurah(0))
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun recordingsInEnglishByDay() {
        var cancelled: Track? = null
        show("en-day-recordings") { Recordings(Status.Working(Progress("b", Step.LISTEN, 0.43f, speed = 1.1f), "Surah Al-Baqarah", surah = 2), onCancel = { cancelled = it }) }
        // What is being done is shown in pictures: the words are there for a screen reader, not on the page.
        compose.onNodeWithContentDescription("2 of 7 ready").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Listening to the recitation… 43%").assertCountEquals(2)
        compose.onAllNodesWithText("Listening to the recitation… 43%").assertCountEquals(0)
        compose.onAllNodesWithText("1.1×").assertCountEquals(2)
        compose.onNodeWithContentDescription("Waiting its turn").assertIsDisplayed()
        // Each recording is laid out like a surah in the Quran tab: its number with its mark, and under its name the recording's own title.
        compose.onNodeWithText("سورة البقرة | Surah Al-Baqarah").assertIsDisplayed()
        compose.onNodeWithText("Choose surah").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
        // Tapping the picture says what it means.
        compose.onAllNodesWithContentDescription("Listening to the recitation… 43%").onFirst().performClick()
        compose.onNodeWithText("Listening to the recitation… 43%").assertIsDisplayed()
        // The cross on a queued recording takes just that one out. (The first cross is the card's, which calls everything off.)
        compose.onAllNodesWithContentDescription("Cancel")[1].performClick()
        assertTrue(cancelled?.id == "b")
    }

    /** The first [words] words placed, each a second long, with the listening [heardMs] into the recording. */
    private fun timingsSoFar(words: Int, heardMs: Int): Timings {
        var left = words
        val placed = timings(fatihah).verses.map { verse -> verse.take(left).also { left -= it.size } }.filter { it.isNotEmpty() }
        return Timings(placed, 1f, heardMs)
    }

    @Composable
    private fun HalfHeardReader(positionMs: Int, listening: Boolean = true, speed: Float = 0f, sketch: IntArray? = null, listeningAt: Int = -1, playing: Boolean = true, onSeek: (Int) -> Unit = {}) = ReaderContent(
        reading = Reading(1, fatihah, track.copy(ready = false, playable = true, wanted = listening), timingsSoFar(words = 8, heardMs = 8_000).sketched(sketch)),
        showMeanings = true, playing = playing, position = { positionMs }, duration = 74_000,
        onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = onSeek, listening = listening, speed = speed, listeningAt = listeningAt,
    )

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theReaderShowsWhatIsBeingDoneToItsRecordingAndWhere() {
        // It is being listened to, at 0:20 of it: the ear in its ring and the speed are over the line, and a mark stands on the line there.
        val here = Status.Working(Progress("a", Step.LISTEN, 0.27f, speed = 1.2f, atMs = 20_000), "Al-Fatihah", surah = 1)
        show("en-day-reader-doing-here") {
            CompositionLocalProvider(LocalDoing provides Doing(here, StatusActions())) { HalfHeardReader(6_500, speed = 1.2f, listeningAt = 20_000) }
        }
        compose.onNodeWithContentDescription("Listening to the recitation… 27%").assertIsDisplayed()
        compose.onNodeWithText("1.2×").assertIsDisplayed()
        compose.onNodeWithText("Listening to the recitation… 27%").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun whileAnotherSurahIsWorkedOnTheReaderShowsWhich() {
        // This recording waits its turn: an hourglass over the line. What is being done meanwhile, and for which surah,
        // isn't in the player: it is at the end of the top bar, which is there whenever the recitation is paused.
        val there = Status.Working(Progress("b", Step.DOWNLOAD, 0.4f), "Al-Baqarah", surah = 2)
        var paused = false
        show("en-day-reader-doing-elsewhere") {
            CompositionLocalProvider(LocalDoing provides Doing(there, StatusActions(pause = { paused = true }))) { HalfHeardReader(6_500, playing = false) }
        }
        compose.onNodeWithContentDescription("Waiting its turn").assertIsDisplayed()
        compose.onNodeWithContentDescription("Downloading the sound… 40%").assertIsDisplayed()
        // Tapping it opens the whole of it: the words for the picture, and the buttons.
        compose.onNodeWithContentDescription("Downloading the sound… 40%").performClick()
        compose.onNodeWithText("Downloading the sound… 40%").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Pause").onLast().performClick()
        assertTrue(paused)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aRecordingPlaysWhileItIsStillListenedTo() {
        // The first two verses have been placed; the recitation is in the second.
        var sought: Int? = null
        show("en-day-reader-listening") { HalfHeardReader(6_500, speed = 1.34f, onSeek = { sought = it }) }
        // How fast the listening is going; how far it has got is the line's colour, not a sentence.
        compose.onNodeWithContentDescription("Listening · 1.3× as fast as it plays").assertIsDisplayed()
        compose.onNodeWithText("1.3×").assertIsDisplayed()
        compose.onNodeWithText("Still listening to this recording: words light up as far as 0:08.").assertDoesNotExist()
        // A word that has a place can be jumped to; one the listening hasn't reached can't yet.
        compose.onNodeWithText("ٱلْحَمْدُ", useUnmergedTree = true).performClick()
        assertTrue(sought == 4_000)
        sought = null
        compose.onNodeWithText("مَـٰلِكِ", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Next verse").performClick()
        assertTrue(sought == null)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun thePlayLineIsCutIntoVersesAndShowsEveryStretchHeard() {
        // Heard in order as far as 0:08, and out of turn from 0:40 to 0:55; the verses begin where the quick look put them.
        val there = app.recitation.core.Repeat(20, listOf(WordTiming(41_000, intArrayOf(3, 41_000))))
        val soFar = timingsSoFar(words = 8, heardMs = 8_000)
        val timings = Timings(soFar.verses, soFar.clarity, soFar.heardMs, listOf(there), listOf(40_000 until 55_000), intArrayOf(0, 4_000, 14_000, 24_000, 34_000, 47_000, 60_000))
        show("en-day-reader-bar-verses") {
            ReaderContent(
                reading = Reading(1, fatihah, track.copy(ready = false, playable = true, wanted = true), timings),
                showMeanings = true, playing = true, position = { 6_500 }, duration = 74_000,
                onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {}, speed = 1.1f,
            )
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-night-420dpi")
    fun pastWhatHasBeenHeardNoWordIsLit() {
        // The recitation has run ahead of the listening: the words are there to read, none framed.
        show("en-night-reader-ahead-of-listening") { HalfHeardReader(20_000) }
        compose.onNodeWithText("Still listening to this recording: words light up as far as 0:08.").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-night-420dpi")
    fun aVerseNotHeardYetCanBeGoneToByTheQuickLookOverTheRecording() {
        // Two verses placed, as far as 8 s; a quick look over the recording says roughly where all five begin.
        var sought: Int? = null
        val sketch = intArrayOf(0, 4_000, 9_000, 14_000, 21_000)
        show("en-night-reader-unheard-verse") { HalfHeardReader(10_000, sketch = sketch, onSeek = { sought = it }) }
        // None of its words is placed, so all of it is lit rather than its number alone, and all of it is on the page.
        compose.onNodeWithTag("verse-lit", useUnmergedTree = true).assertIsDisplayed()
        // The recitation is in the third verse, which hasn't been heard: the verses either side are still within reach.
        compose.onNodeWithContentDescription("Next verse").performClick()
        assertTrue("went to $sought", sought == 14_000)
        compose.onNodeWithContentDescription("Previous verse").performClick()
        assertTrue("went to $sought", sought == 4_000)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aRecordingWhoseListeningWasStoppedSaysSo() {
        show("en-day-reader-listening-stopped") { HalfHeardReader(6_500, listening = false) }
        compose.onNodeWithContentDescription("Not listened to the end yet: words light up as far as 0:08.").assertIsDisplayed()
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
                playlist.copy(tracks = tracks), Status.Working(Progress("b", Step.LISTEN, 0.43f), "Surah Al-Baqarah", surah = 2), StatusActions(),
                {}, { opened = it }, { asked = it }, {}, { _, _ -> }, {}, {},
            )
        }
        // Its sound is here, so it has a play button while the listening goes on.
        compose.onAllNodesWithContentDescription("Listening to the recitation… 43%").onFirst().assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Play")[1].performClick()
        assertTrue(opened?.id == "b")
        // One whose listening was stopped says so, and its button takes the listening up again.
        compose.onNodeWithContentDescription("Has a recitation on the phone, not listened to the end yet").assertIsDisplayed()
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
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun eachVerseHasItsButtonsBesideItsNumber() {
        var sought: Int? = null
        var started = 0
        var marking: Int? = null
        var chosen: IntRange? = null
        show("en-day-reader-verse-buttons") {
            ReaderContent(
                reading = Reading(1, fatihah, track, timings(fatihah)), showMeanings = true, playing = false,
                position = { 500 }, duration = 74_000, onBack = {}, onMeanings = {}, onPlayPause = { started++ }, onSeek = { sought = it },
                marks = VerseMarks(mapOf(1 to listOf("Reading")), setOf(2)), onMark = { marking = it },
                range = 0..6, onRange = { chosen = it },
            )
        }
        // Recite from the third verse: the recitation goes to its first word (the ninth, a second a word) and starts.
        compose.onAllNodesWithContentDescription("Recite from this verse")[2].performClick()
        assertTrue("sought $sought, started $started", sought == 8_000 && started == 1)
        // The bookmark opens the verse's pins and collections; it says so when the verse is already kept.
        compose.onAllNodesWithContentDescription("Pins and collections")[0].performClick()
        assertTrue(marking == 0)
        compose.onNodeWithContentDescription("Kept in a collection").assertIsDisplayed()
        // The rest is under the third button.
        compose.onNodeWithContentDescription("More for verse 2").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Copy verse").assertIsDisplayed()
        compose.onNodeWithText("Share verse").assertIsDisplayed()
        compose.onNodeWithText("Show translation").assertIsDisplayed()
        compose.onNodeWithText("Recite this verse only").performClick()
        assertTrue("chosen $chosen, sought $sought", chosen == 1..1 && sought == 4_000 && started == 2)
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-night-420dpi")
    fun theVerseButtonsInArabic() {
        show("ar-night-reader-verse-buttons") {
            ReaderContent(
                reading = Reading(1, fatihah, track, timings(fatihah)), showMeanings = false, playing = false,
                position = { 500 }, duration = 74_000, onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {}, range = 0..6,
            )
        }
        compose.onNodeWithContentDescription("المزيد للآية ٢").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("نسخ الآية").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun withNoRecordingAVerseHasNoReciteButton() {
        compose.setContent {
            RecitationTheme {
                ReaderContent(
                    reading = Reading(1, fatihah), showMeanings = true, playing = false, position = { 0 }, duration = 0,
                    onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {},
                )
            }
        }
        compose.onAllNodesWithContentDescription("Recite from this verse").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Pins and collections")[0].assertIsDisplayed()
    }

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
        compose.onNodeWithText("البقرة · Al-Baqarah · verse 255").assertIsDisplayed()
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
        compose.onNodeWithText("الفاتحة · Al-Fatihah · verse 2").assertIsDisplayed()
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
        compose.onNodeWithText("الفاتحة · Al-Fatihah · verse 5").performClick()
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
        compose.onNodeWithText("The Recitation").assertIsDisplayed()
        compose.onNodeWithContentDescription("Menu").assertIsDisplayed()
        // The name is at the left, right beside the menu button.
        val name = compose.onNodeWithText("The Recitation").fetchSemanticsNode().boundsInRoot
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
        var appearance: Appearance? = null
        var script: Script? = null
        compose.setContent {
            RecitationTheme {
                HomeContent(0, {}, emptyList(), null, StatusActions(), recited = setOf(1), onSurah = {}, onOpen = {}, onAdd = {}, menu = {
                    MenuContent(Appearance.AUTO, { appearance = it }, Script.MADINAH, { script = it }, withIndoPak = true) { Text("Version 1.41") }
                })
            }
        }
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/en-night-menu.png")
        // What belongs to the whole app is here: how it looks, and its version.
        compose.onNodeWithText("Read the Quran word by word, and follow a recitation as you listen.").assertIsDisplayed()
        compose.onNodeWithText("Dark").performClick()
        assertTrue(appearance == Appearance.DARK)
        compose.onNodeWithText("Version 1.41").assertIsDisplayed()
        // The script every surah is written in is chosen here, once for the whole app.
        compose.onNodeWithText("Script").assertIsDisplayed()
        compose.onNodeWithText("Everyday spelling").performClick()
        assertTrue(script == Script.EVERYDAY)
        // What is turned to while reading is in a surah's own menu, not here as well.
        compose.onNodeWithText("Word meanings").assertDoesNotExist()
        compose.onNodeWithText("Text size").assertDoesNotExist()
        // The panel stands beside the page, which has gone aside by the panel's width: nothing of it lies under the panel.
        val panel = compose.onNodeWithTag("menu").fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithContentDescription("Menu").fetchSemanticsNode().boundsInRoot
        assertTrue("panel $panel, the page's menu button $button", panel.left <= 1f && button.left >= panel.right)
        // Its cross closes it, and the page comes back.
        compose.onNodeWithContentDescription("Close").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("menu").assertDoesNotExist()
        assertTrue(compose.onNodeWithContentDescription("Menu").fetchSemanticsNode().boundsInRoot.left < 40f)
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
        compose.onNodeWithText("الكهف · Al-Kahf · verse 10").performClick()
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
        compose.onNodeWithText("الفاتحة · Al-Fatihah · verse 5").performClick()
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
    fun theScreenStaysOnWhileTheRecitationPlays() {
        var playing by mutableStateOf(true)
        var open by mutableStateOf(true)
        lateinit var screen: View
        compose.setContent {
            screen = LocalView.current
            RecitationTheme {
                if (open) {
                    ReaderContent(
                        reading = Reading(1, fatihah, track, timings(fatihah)), showMeanings = true, playing = playing,
                        position = { 7_500 }, duration = 74_000, onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        assertTrue(screen.keepScreenOn)
        // Paused, the phone may dim and sleep as usual.
        playing = false
        compose.waitForIdle()
        assertTrue(!screen.keepScreenOn)
        // Playing again, then the surah is left while it plays: the lists don't hold the screen on.
        playing = true
        compose.waitForIdle()
        assertTrue(screen.keepScreenOn)
        open = false
        compose.waitForIdle()
        assertTrue(!screen.keepScreenOn)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-night-420dpi")
    fun theFollowButtonPointsTheWayToTheRecitedWord() {
        var followed = 0
        show("en-night-follow-button") {
            Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                FollowButton(up = true) { followed++ }
                FollowButton(up = false) {}
            }
        }
        compose.onAllNodesWithContentDescription("Follow the recitation").onFirst().performClick()
        assertTrue(followed == 1)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theWordsBlocksShareAllOfTheVersesWidth() {
        compose.setContent { RecitationTheme { Reader(7_500, meanings = false) } }
        compose.waitForIdle()
        // The second verse's four words sit on one row; their blocks touch, and reach the page's edges but for a small margin.
        val blocks = listOf("ٱلْحَمْدُ", "لِلَّهِ", "رَبِّ", "ٱلْعَـٰلَمِينَ").map { word ->
            compose.onNodeWithText(word, useUnmergedTree = true).fetchSemanticsNode().parent!!.boundsInRoot
        }
        val page = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val margin = 6 * 420 / 160f
        assertTrue(abs(page.right - blocks.first().right - margin) < 1.5f && abs(blocks.last().left - page.left - margin) < 1.5f)
        for ((right, left) in blocks.zipWithNext()) assertTrue(abs(right.left - left.right) < 1f)
        assertTrue(blocks.all { abs(it.height - blocks.first().height) < 1f })
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theSurahsTitleIsInTheMiddleOfItsBar() {
        show("en-day-reader-bar") { Reader(7_500, playing = false) }
        val page = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("surah-title").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(title.center.x - page.center.x) < 2f)
        // Back at one end, the menu at the other, and nothing else on the bar.
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithContentDescription("Menu").assertIsDisplayed()
        compose.onNodeWithContentDescription("Text size").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-night-420dpi")
    fun aSurahsMenuHoldsTheMeaningsTheSizeAndLightOrDark() {
        var meanings: Boolean? = null
        var scale: Float? = null
        var appearance: Appearance? = null
        show("en-night-reader-choices") {
            Column(Modifier.padding(24.dp).width(280.dp)) {
                ReadingChoices(true, { meanings = it }, 1f, { scale = it }, Appearance.AUTO, { appearance = it })
            }
        }
        compose.onNodeWithText("Word meanings").performClick()
        assertTrue(meanings == false)
        compose.onNodeWithContentDescription("Larger").performClick()
        assertTrue(scale == 1.1f)
        // Light or dark is right under the size of the text, above the colours of tajweed.
        val size = compose.onNodeWithText("Text size").fetchSemanticsNode().boundsInRoot
        val look = compose.onNodeWithText("Appearance").fetchSemanticsNode().boundsInRoot
        val colours = compose.onNodeWithText("Tajweed colours").fetchSemanticsNode().boundsInRoot
        assertTrue("size $size, appearance $look, colours $colours", size.bottom <= look.top && look.bottom < colours.top)
        compose.onNodeWithText("Dark").performClick()
        assertTrue(appearance == Appearance.DARK)
        // The script is the whole app's: it is in the first screen's menu, not here.
        compose.onNodeWithText("Everyday spelling").assertDoesNotExist()
        compose.onNodeWithText("Script").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theChoiceOfLightOrDarkIsKept() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // The phone decides until the reader does.
        assertTrue(Choices(context, arabic = false).appearance == Appearance.AUTO)
        Choices(context, arabic = false).look(Appearance.DARK)
        assertTrue(Choices(context, arabic = false).appearance == Appearance.DARK)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun darkCanBeChosenWhileThePhoneIsLight() {
        // The phone is in its light mode; the app draws dark because that is what was chosen.
        compose.setContent { RecitationTheme(dark = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Reader(7_500) } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/en-dark-chosen-on-a-light-phone.png")
        compose.onNodeWithText("of the universe").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w891dp-h411dp-land-420dpi")
    fun onItsSideThePageKeepsTheLineJustReadInViewToo() {
        // One verse of 40 words, several lines long even on a phone held on its side.
        val all = fatihah.flatMap { it.words }
        val long = Verse(List(40) { Word(all[it % all.size].arabic, "word ${it + 1}") }, "A long verse.")
        val words = long.words
        val firstOfVerse = 0
        var position by mutableIntStateOf(500)
        compose.setContent {
            CompositionLocalProvider(LocalSurahNames provides surahNames) {
                RecitationTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        ReaderContent(
                            reading = Reading(1, listOf(long), track, timings(listOf(long))), showMeanings = true, playing = true,
                            position = { position }, duration = 74_000, onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        // Where the block of the verse's [w]th word is.
        fun block(w: Int) = compose.onNodeWithText("word ${w + 1}").fetchSemanticsNode().boundsInRoot
        var linesTurned = 0
        for (w in 1 until words.size) {
            position = (firstOfVerse + w) * 1000 + 500
            compose.waitForIdle()
            val page = compose.onNodeWithTag("verses").fetchSemanticsNode().boundsInRoot
            val now = block(w)
            val before = block(w - 1)
            if (now.top > before.top + 1f) {
                linesTurned++
                if (linesTurned == 1) compose.onRoot().captureRoboImage("build/screens/en-day-reader-on-its-side.png")
                // The recitation has gone on to the next line: the line it left is still wholly on the page...
                assertTrue("the line just read starts at ${before.top}, above the page's top at ${page.top}", before.top >= page.top - 1f)
                // ...and so is the line being recited.
                assertTrue(now.bottom <= page.bottom + 1f)
            }
        }
        assertTrue("the verse was meant to run over more than one line", linesTurned >= 1)
    }

    @Test
    @Config(qualifiers = "en-rUS-w891dp-h411dp-land-420dpi")
    fun nothingLiesUnderACameraCutOutAtTheSide() {
        lateinit var screen: View
        compose.setContent {
            screen = LocalView.current
            RecitationTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { ClearOfTheSides { Reader(7_500, meanings = false, playing = false) } }
            }
        }
        compose.waitForIdle()
        fun leftmost() = compose.onAllNodesWithText("ٱلْعَـٰلَمِينَ", useUnmergedTree = true).onFirst().fetchSemanticsNode().parent!!.boundsInRoot.left
        val without = leftmost()
        // The phone reports a camera hole 120 px deep at its left edge.
        val cutOut = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(120, 0, 0, 0)).build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(screen, cutOut) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/en-day-reader-cut-out.png")
        assertTrue("the leftmost word starts at ${leftmost()}, was at $without", leftmost() >= 120f && leftmost() >= without + 119f)
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        assertTrue(compose.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot.left >= 120f)
    }

    /** A word as the Uthmani text and the Madinah mushaf's text write it, with its meaning. */
    private class T(val uthmani: String, val madinah: String, val meaning: String)

    /** Five verses rich in rules of recitation (2:5, 2:8, 2:10, 112:3, 113:1), as Quran.com gives them. */
    private val ruled = listOf(
        listOf(T("\u0623\u064F\u0648\u06DF\u0644\u064E\u0640\u0670\u0653\u0626\u0650\u0643\u064E", "\u0623\u064F\u0648\u0652\u0644\u064E\u0670\u0653\u0626\u0650\u0643\u064E", "Those"), T("\u0639\u064E\u0644\u064E\u0649\u0670", "\u0639\u064E\u0644\u064E\u0649\u0670", "(are) on"), T("\u0647\u064F\u062F\u064B\u06ED\u0649", "\u0647\u064F\u062F\u0657\u0649", "Guidance"), T("\u0645\u0651\u0650\u0646", "\u0645\u0651\u0650\u0646", "from"), T("\u0631\u0651\u064E\u0628\u0651\u0650\u0647\u0650\u0645\u0652 \u06D6", "\u0631\u0651\u064E\u0628\u0651\u0650\u0647\u0650\u0645\u06E1\u06D6", "their Lord"), T("\u0648\u064E\u0623\u064F\u0648\u06DF\u0644\u064E\u0640\u0670\u0653\u0626\u0650\u0643\u064E", "\u0648\u064E\u0623\u064F\u0648\u0652\u0644\u064E\u0670\u0653\u0626\u0650\u0643\u064E", "and those "), T("\u0647\u064F\u0645\u064F", "\u0647\u064F\u0645\u064F", "they"), T("\u0671\u0644\u0652\u0645\u064F\u0641\u0652\u0644\u0650\u062D\u064F\u0648\u0646\u064E", "\u0671\u0644\u06E1\u0645\u064F\u0641\u06E1\u0644\u0650\u062D\u064F\u0648\u0646\u064E", "(are) the successful ones")), // 2:5
        listOf(T("\u0648\u064E\u0645\u0650\u0646\u064E", "\u0648\u064E\u0645\u0650\u0646\u064E", "And of"), T("\u0671\u0644\u0646\u0651\u064E\u0627\u0633\u0650", "\u0671\u0644\u0646\u0651\u064E\u0627\u0633\u0650", "the people"), T("\u0645\u064E\u0646", "\u0645\u064E\u0646", "(are some) who"), T("\u064A\u064E\u0642\u064F\u0648\u0644\u064F", "\u064A\u064E\u0642\u064F\u0648\u0644\u064F", "say"), T("\u0621\u064E\u0627\u0645\u064E\u0646\u0651\u064E\u0627", "\u0621\u064E\u0627\u0645\u064E\u0646\u0651\u064E\u0627", "We believed"), T("\u0628\u0650\u0671\u0644\u0644\u0651\u064E\u0647\u0650", "\u0628\u0650\u0671\u0644\u0644\u0651\u064E\u0647\u0650", "in Allah"), T("\u0648\u064E\u0628\u0650\u0671\u0644\u0652\u064A\u064E\u0648\u0652\u0645\u0650", "\u0648\u064E\u0628\u0650\u0671\u0644\u06E1\u064A\u064E\u0648\u06E1\u0645\u0650", "and in the Day"), T("\u0671\u0644\u0652\u0640\u0654\u064E\u0627\u062E\u0650\u0631\u0650", "\u0671\u0644\u06E1\u0623\u0653\u062E\u0650\u0631\u0650", "[the] Last"), T("\u0648\u064E\u0645\u064E\u0627", "\u0648\u064E\u0645\u064E\u0627", "but not"), T("\u0647\u064F\u0645", "\u0647\u064F\u0645", "they"), T("\u0628\u0650\u0645\u064F\u0624\u0652\u0645\u0650\u0646\u0650\u064A\u0646\u064E", "\u0628\u0650\u0645\u064F\u0624\u06E1\u0645\u0650\u0646\u0650\u064A\u0646\u064E", "(are) believers (at all)")), // 2:8
        listOf(T("\u0641\u0650\u0649", "\u0641\u0650\u064A", "In"), T("\u0642\u064F\u0644\u064F\u0648\u0628\u0650\u0647\u0650\u0645", "\u0642\u064F\u0644\u064F\u0648\u0628\u0650\u0647\u0650\u0645", "their hearts"), T("\u0645\u0651\u064E\u0631\u064E\u0636\u064C\u06ED", "\u0645\u0651\u064E\u0631\u064E\u0636\u065E", "(is) a disease"), T("\u0641\u064E\u0632\u064E\u0627\u062F\u064E\u0647\u064F\u0645\u064F", "\u0641\u064E\u0632\u064E\u0627\u062F\u064E\u0647\u064F\u0645\u064F", "so has increased them"), T("\u0671\u0644\u0644\u0651\u064E\u0647\u064F", "\u0671\u0644\u0644\u0651\u064E\u0647\u064F", "Allah"), T("\u0645\u064E\u0631\u064E\u0636\u064B\u06ED\u0627 \u06D6", "\u0645\u064E\u0631\u064E\u0636\u0657\u0627\u06D6", "(in) disease"), T("\u0648\u064E\u0644\u064E\u0647\u064F\u0645\u0652", "\u0648\u064E\u0644\u064E\u0647\u064F\u0645\u06E1", "and for them"), T("\u0639\u064E\u0630\u064E\u0627\u0628\u064C", "\u0639\u064E\u0630\u064E\u0627\u0628\u064C", "(is) a punishment"), T("\u0623\u064E\u0644\u0650\u064A\u0645\u064C\u06E2", "\u0623\u064E\u0644\u0650\u064A\u0645\u064F\u06E2", "painful"), T("\u0628\u0650\u0645\u064E\u0627", "\u0628\u0650\u0645\u064E\u0627", "because"), T("\u0643\u064E\u0627\u0646\u064F\u0648\u0627\u06DF", "\u0643\u064E\u0627\u0646\u064F\u0648\u0627\u0652", "they used to"), T("\u064A\u064E\u0643\u0652\u0630\u0650\u0628\u064F\u0648\u0646\u064E", "\u064A\u064E\u0643\u06E1\u0630\u0650\u0628\u064F\u0648\u0646\u064E", "[they] lie")), // 2:10
        listOf(T("\u0644\u064E\u0645\u0652", "\u0644\u064E\u0645\u06E1", "Not"), T("\u064A\u064E\u0644\u0650\u062F\u0652", "\u064A\u064E\u0644\u0650\u062F\u06E1", "He begets"), T("\u0648\u064E\u0644\u064E\u0645\u0652", "\u0648\u064E\u0644\u064E\u0645\u06E1", "and not"), T("\u064A\u064F\u0648\u0644\u064E\u062F\u0652", "\u064A\u064F\u0648\u0644\u064E\u062F\u06E1", "He is begotten")), // 112:3
        listOf(T("\u0642\u064F\u0644\u0652", "\u0642\u064F\u0644\u06E1", "Say"), T("\u0623\u064E\u0639\u064F\u0648\u0630\u064F", "\u0623\u064E\u0639\u064F\u0648\u0630\u064F", "I seek refuge"), T("\u0628\u0650\u0631\u064E\u0628\u0651\u0650", "\u0628\u0650\u0631\u064E\u0628\u0651\u0650", "in (the) Lord"), T("\u0671\u0644\u0652\u0641\u064E\u0644\u064E\u0642\u0650", "\u0671\u0644\u06E1\u0641\u064E\u0644\u064E\u0642\u0650", "(of) the dawn")), // 113:1
    ).map { verse -> Verse(verse.map { Word(it.uthmani, it.meaning, madinah = it.madinah) }, "") }

    /** Every sound of every word 150 ms after the one before, as an alignment would time them. */
    private fun sounded(verses: List<Verse>): Timings {
        var ms = 1000
        return Timings(
            verses.map { verse ->
                verse.words.mapIndexed { i, word ->
                    val start = ms
                    val reached = ArrayList<Int>()
                    for (phone in Phonemizer.word(word.arabic, verse.words.getOrNull(i + 1)?.arabic)) {
                        if (phone.optional) continue
                        if (reached.isEmpty() || phone.upTo > reached[reached.size - 2]) {
                            reached += phone.upTo
                            reached += ms
                        }
                        ms += 150
                    }
                    WordTiming(start, reached.toIntArray())
                }
            },
            0.9f,
        )
    }

    /** A moment inside the stretch of word [w] of verse [v] that falls under [rule], in [reading]'s own timings. */
    private fun whenUttered(reading: Reading, v: Int, w: Int, rule: app.recitation.core.Rule): Int {
        val mark = reading.rules[v][w].first { it.rule == rule }
        val letters = reading.timings!!.verses[v][w].letters
        var from = 0
        for (k in letters.indices step 2) {
            if (from < mark.to && mark.from < letters[k]) return letters[k + 1] + 60
            from = letters[k]
        }
        error("no stretch of the word falls under $rule")
    }

    @Composable
    private fun Ruled(
        position: Int, script: Script = Script.MADINAH, tajweed: Boolean = true, reading: Reading = Reading(2, ruled, track, sounded(ruled)), moving: (() -> Int)? = null,
    ) = ReaderContent(
        // [moving] is for a recitation that goes on while the page is on show: the page reads it afresh each time.
        reading = reading, showMeanings = false, playing = true, position = moving ?: { position }, duration = 74_000,
        onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {}, script = script, tajweed = tajweed,
    )

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun lettersAreColouredByTheirRuleOfRecitation() {
        // No recitation: the reciter is taken to stop at the end of each verse.
        show("en-day-tajweed") {
            ReaderContent(
                reading = Reading(2, ruled), showMeanings = false, playing = false, position = { 0 }, duration = 0,
                onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {},
            )
        }
        compose.onNodeWithText(ruled[0].words[0].madinah, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-night-420dpi")
    fun lettersAreColouredByTheirRuleOfRecitationByNight() {
        show("en-night-tajweed") {
            ReaderContent(
                reading = Reading(2, ruled), showMeanings = false, playing = false, position = { 0 }, duration = 0,
                onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {},
            )
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theColoursCanBeTurnedOff() {
        show("en-day-tajweed-off") {
            ReaderContent(
                reading = Reading(2, ruled), showMeanings = false, playing = false, position = { 0 }, duration = 0,
                onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {}, tajweed = false,
            )
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theLightTakesTheColourOfTheRuleBeingRecited() {
        val reading = Reading(2, ruled, track, sounded(ruled))
        // 2:8 "man yaqulu": the n merged into the y, through the nose.
        show("en-day-tajweed-light-nasal") { Ruled(whenUttered(reading, 1, 2, app.recitation.core.Rule.IDGHAM_GHUNNAH), reading = reading) }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-night-420dpi")
    fun aLongVowelsLightSpreadsAsItIsHeld() {
        val reading = Reading(2, ruled, track, sounded(ruled))
        // 2:5 "ula'ika": the long a before the hamza.
        show("en-night-tajweed-light-madd") { Ruled(whenUttered(reading, 0, 0, app.recitation.core.Rule.MADD_MUTTASIL) + 60, reading = reading) }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-night-420dpi")
    fun theLightFallsOnAVowelSignToo() {
        val reading = Reading(2, ruled, track, sounded(ruled))
        // 112:3 "lam yalid": the fatha of the first l, the moment after the letter itself.
        val lam = reading.timings!!.verses[3][0].letters
        show("en-night-light-on-a-sign") { Ruled(lam[3] + 40, reading = reading) }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theMenuNamesTheColoursAndLeadsToTheRules() {
        var shown: Boolean? = null
        var rules = false
        show("en-day-tajweed-legend") {
            CompositionLocalProvider(LocalTajweedRules provides { rules = true }) {
                Column(Modifier.padding(24.dp).width(300.dp)) {
                    ReadingChoices(true, {}, 1f, {}, tajweed = true) { shown = it }
                }
            }
        }
        // Each colour by the family's Arabic name, with the same in Latin letters.
        compose.onNodeWithText("القلقلة").assertIsDisplayed()
        compose.onNodeWithText("Qalqalah").assertIsDisplayed()
        compose.onNodeWithText("المدّ اللازم").assertIsDisplayed()
        compose.onNodeWithText("All the rules").performClick()
        assertTrue(rules)
        compose.onNodeWithText("Tajweed colours").performClick()
        assertTrue(shown == false)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h1600dp-420dpi")
    fun thePageOfRulesSetsOutEveryRuleByItsArabicName() {
        var back = false
        show("en-day-tajweed-rules") { TajweedRulesPage(onBack = { back = true }) }
        compose.onNodeWithText("The rules of tajweed").assertIsDisplayed()
        compose.onNodeWithText("الإخفاء الحقيقي").assertIsDisplayed()
        compose.onNodeWithText("Ikhfa’ haqiqi").assertIsDisplayed()
        // Every rule the verses can be coloured by has a name, a meaning and an example.
        for (rule in app.recitation.core.Rule.entries) assertTrue(rule.named().arabic.isNotBlank() && rule.named().example.isNotBlank())
        compose.onNodeWithContentDescription("Back").performClick()
        assertTrue(back)
    }

    @Test
    @Config(qualifiers = "ar-w411dp-h1600dp-night-420dpi")
    fun thePageOfRulesInArabic() {
        show("ar-night-tajweed-rules") { TajweedRulesPage(onBack = {}) }
        compose.onNodeWithText("أحكام التجويد").assertIsDisplayed()
        compose.onNodeWithText("الإخفاء الشفوي").assertIsDisplayed()
        // No Latin names where the app is in Arabic.
        compose.onNodeWithText("Ikhfa’ haqiqi").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theSurahsMenuSlidesInFromTheSideItsButtonIsOn() {
        var meanings: Boolean? = null
        compose.setContent {
            RecitationTheme {
                ReaderContent(
                    reading = Reading(2, ruled), showMeanings = false, playing = false, position = { 0 }, duration = 0,
                    onBack = {}, onMeanings = { meanings = it }, onPlayPause = {}, onSeek = {},
                )
            }
        }
        val button = compose.onNodeWithContentDescription("Menu").fetchSemanticsNode().boundsInRoot
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(button.right > screen.right - 40f)
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/en-day-reader-menu.png")
        // A panel the height of the screen at the right edge, not a small menu under the button.
        val panel = compose.onNodeWithTag("reader-menu").fetchSemanticsNode().boundsInRoot
        assertTrue("panel at $panel on $screen", panel.right >= screen.right - 1f && panel.left > screen.width * 0.1f && panel.height > screen.height * 0.8f)
        compose.onNodeWithText("Word meanings").performClick()
        assertTrue(meanings == true)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theSurahsMenuSwitchesToDarkAndBack() {
        var chosen: Appearance? = null
        compose.setContent {
            RecitationTheme {
                ReaderContent(
                    reading = Reading(2, ruled), showMeanings = false, playing = false, position = { 0 }, duration = 0,
                    onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {}, appearance = Appearance.LIGHT, onAppearance = { chosen = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Appearance").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        assertEquals(Appearance.DARK, chosen)
        compose.onNodeWithText("Auto").performClick()
        assertEquals(Appearance.AUTO, chosen)
    }

    @Test
    @Config(qualifiers = "ar-w411dp-h891dp-night-420dpi")
    fun inArabicTheSurahsMenuSlidesInFromTheLeft() {
        compose.setContent {
            RecitationTheme {
                ReaderContent(
                    reading = Reading(2, ruled), showMeanings = false, playing = false, position = { 0 }, duration = 0,
                    onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {},
                )
            }
        }
        compose.onNodeWithContentDescription("القائمة").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/ar-night-reader-menu.png")
        val panel = compose.onNodeWithTag("reader-menu").fetchSemanticsNode().boundsInRoot
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("panel at $panel on $screen", panel.left <= 1f && panel.right < screen.width * 0.9f)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aPassageSaidAgainIsFollowedASecondTime() {
        // Each word a second long; after the fourth word of 2:5 the reciter goes back to its second and third.
        val once = timings(ruled)
        val again = app.recitation.core.Repeat(1, listOf(WordTiming(3300, intArrayOf(ruled[0].words[1].arabic.length, 3300)), WordTiming(3600, intArrayOf(ruled[0].words[2].arabic.length, 3600))))
        val reading = Reading(2, ruled, track, Timings(once.verses, once.clarity, repeats = listOf(again)))
        show("en-day-reader-said-again") { Ruled(3450, reading = reading) }
        compose.onNodeWithText(ruled[0].words[1].madinah, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun aWaveRunsOverTheWordsGoneBackOver() {
        theWave("en-day", dark = false)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-night-420dpi")
    fun theWaveByNight() {
        theWave("en-night", dark = true)
    }

    /** The recitation has just gone back from the sixth word of 2:5 to its second, which begins the verse's second row. */
    private fun theWave(look: String, dark: Boolean) {
        val once = timings(ruled)
        val again = app.recitation.core.Repeat(1, (1..5).map { w -> (5050 + 150 * w).let { WordTiming(it, intArrayOf(ruled[0].words[w].arabic.length, it)) } })
        val reading = Reading(2, ruled, track, Timings(once.verses, once.clarity, repeats = listOf(again)))
        var at by mutableIntStateOf(5_210)
        compose.setContent {
            CompositionLocalProvider(LocalSurahNames provides surahNames) {
                RecitationTheme(dark) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Ruled(0, reading = reading, moving = { at }) } }
            }
        }
        // All the words gone back over are marked, with a stream flowing back under them, until he has said
        // them again (at 6 s). It goes by where the recitation is, not by the phone's clock: 150 ms after he went back (at 5.2 s), then 350, 550 and 750.
        for (ms in listOf(150, 350, 550, 750)) {
            at = 5_200 + ms
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/screens/$look-reader-said-again-wave-$ms.png")
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h560dp-420dpi")
    fun whereAVersesWordsRunOutThePageDoesNotGoBackUpToItsStart() {
        // 2:5 placed, and the first eight words of 2:8, a second a word; the listening is 16 s in. A quick look says roughly where every verse begins.
        val all = timings(ruled)
        val placed = Timings(listOf(all.verses[0], all.verses[1].take(8)), 1f, heardMs = 16_000).sketched(intArrayOf(0, 8_000, 19_000, 31_000, 35_000))
        var at by mutableIntStateOf(15_500)
        compose.setContent {
            RecitationTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ReaderContent(
                        reading = Reading(2, ruled, track.copy(ready = false, playable = true, wanted = true), placed), showMeanings = true, playing = true,
                        position = { at }, duration = 74_000, onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {}, textScale = 1.6f,
                    )
                }
            }
        }
        compose.waitForIdle()
        // The eighth word of 2:8 is being recited, some lines down the verse, and the page has followed it there.
        fun top() = compose.onNodeWithText(ruled[1].words[0].madinah, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        val before = top()
        // The recitation goes on past the last word placed. The verse is lit whole, and stays where it is or moves on: never back down the page.
        at = 17_000
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/en-day-reader-words-run-out.png")
        compose.onNodeWithTag("verse-lit", useUnmergedTree = true).assertExists()
        assertTrue("the verse's start went from $before to ${top()}", top() <= before + 1f)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theSurahsNameInTheTopBarLeadsToAnotherSurah() {
        var picked = 0
        compose.setContent {
            RecitationTheme {
                ReaderContent(
                    reading = Reading(2, ruled), showMeanings = false, playing = false, position = { 0 }, duration = 0,
                    onBack = {}, onMeanings = {}, onPlayPause = {}, onSeek = {}, onSurah = { picked = it },
                )
            }
        }
        compose.onNodeWithTag("surah-switch").performClick()
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onLast().captureRoboImage("build/screens/en-day-reader-switch-surah.png")
        // The list opens at the surah that is open, with the ones round it within reach.
        compose.onNodeWithText("Choose surah").assertIsDisplayed()
        compose.onNodeWithText("Ali 'Imran").performClick()
        assertEquals(3, picked)
    }

    private val week = LocalDate.of(2026, 11, 20).let { friday ->
        Listening(
            days = mapOf(
                friday to 24 * 60_000L, friday.minusDays(1) to 41 * 60_000L, friday.minusDays(2) to 8 * 60_000L,
                friday.minusDays(4) to 65 * 60_000L, friday.minusDays(5) to 15 * 60_000L, friday.minusDays(30) to 600 * 60_000L,
            ),
            heard = mapOf(1 to (1..7).toSet(), 2 to (1..141).toSet(), 112 to (1..4).toSet()),
        ).summary(friday)
    }

    @Composable
    private fun StatsOnHome(space: Space?) = HomeContent(
        3, {}, emptyList(), null, StatusActions(), recited = setOf(1), onSurah = {}, onOpen = {}, onAdd = {},
        stats = { modifier -> StatsTab(week, RecordingStats(12, 114, 9 * 3_600_000L + 20 * 60_000L), SavedStats(2, 3, 17), space, modifier) },
    )

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theDaysListenedOnInARowAreAPlantInTheTopBar() {
        show("en-day-streak-chip") {
            HomeContent(0, {}, listOf(playlist), null, StatusActions(), recited = setOf(1), onSurah = {}, onOpen = {}, onAdd = {}, streak = week)
        }
        compose.onNodeWithContentDescription("3 days in a row").assertIsDisplayed()
        // A tap opens the whole of it: what today asks for, and the last seven days.
        compose.onNodeWithTag("streak").performClick()
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onLast().captureRoboImage("build/screens/en-day-streak.png")
        compose.onNodeWithText("3 days in a row").assertIsDisplayed()
        compose.onNodeWithText("Today's minute is in. It grows again tomorrow.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun thePlantGrowsWithTheRunAndHangsUntilTodayIsListenedOn() {
        // A seed, a sprout, a pair of leaves at a time, in flower at a fortnight, in fruit at a month.
        assertEquals(listOf(0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5), listOf(0, 1, 2, 3, 6, 7, 13, 14, 29, 30, 365).map(::stageOf))
        compose.setContent {
            RecitationTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (watered in listOf(true, false)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (days in listOf(0, 1, 3, 7, 14, 30)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Plant(days, watered, Modifier.size(58.dp))
                                        Text("$days", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/en-day-plants.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theStatsTab() {
        show("en-day-stats") { StatsOnHome(Space(1_240_000_000, 34_331_256, 0, 21_400_000)) }
        compose.onNodeWithText("Stats").assertIsDisplayed()
        compose.onNodeWithText("24 min").assertIsDisplayed()
        compose.onNodeWithText("2 h 33 min").assertIsDisplayed()
        compose.onNodeWithText("3 days in a row").assertIsDisplayed()
        compose.onNodeWithText("152 of 6236 verses").assertIsDisplayed()
        compose.onNodeWithText("2 of 114 surahs heard in full").assertIsDisplayed()
        compose.onNodeWithText("12 of 114").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-night-420dpi")
    fun theStatsTabInArabicByNight() {
        show("ar-night-stats") { StatsOnHome(null) }
        compose.onNodeWithText("إحصاءات").assertIsDisplayed()
        compose.onNodeWithText("٣ أيام على التوالي").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun pausedDownloads() {
        var resumed = false
        show("en-day-paused") { Recordings(Status.Paused, StatusActions(resume = { resumed = true })) }
        compose.onNodeWithContentDescription("Downloads are paused. What was fetched so far is kept.").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Paused").onFirst().assertIsDisplayed()
        compose.onNodeWithContentDescription("Resume").performClick()
        assertTrue(resumed)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theMenuOffersAReportToCopy() {
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        compose.setContent {
            RecitationTheme {
                Surface {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        MenuContent {
                            Text("Version 1.55")
                            app.recitation.ui.CopyReport()
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("For when something isn't working: the phone, the queue and what the app did lately. It goes nowhere unless you paste it somewhere.").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/screens/en-day-menu-report.png")
        compose.onNodeWithText("Copy a report").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Copied. Paste it into a message.").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun theFirstListeningSaysItIsFindingTheFastestWay() {
        show("en-day-tuning") { Recordings(Status.Working(Progress(null, Step.TUNE, 0.5f), null)) }
        compose.onAllNodesWithContentDescription("Finding the fastest way to listen on this phone… 50%").onFirst().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun whenYouTubeRefusesTheQueueSaysSoAndWaits() {
        show("en-day-refused") { Recordings(Status.Refused) }
        compose.onNodeWithContentDescription("YouTube isn't letting recitations be downloaded right now. They will be tried again in a while; what is already on the phone carries on.").assertIsDisplayed()
        // Each recording still in the queue says the same in short, and none is marked as failed.
        compose.onAllNodesWithContentDescription("YouTube is refusing for now. Trying again in a while…").onFirst().assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-420dpi")
    fun whenYouTubeRefusesInArabic() {
        show("ar-day-refused") { Recordings(Status.Refused) }
        compose.onNodeWithContentDescription("يوتيوب لا يسمح بتنزيل التلاوات حالياً. ستُعاد المحاولة بعد قليل، ويستمر العمل على ما هو موجود في الهاتف.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun queuedRecordingsSayWhatTheyAreWaitingFor() {
        // While the speech model is fetched, a queued recording shows that, not a bare "waiting".
        show("en-day-model-download") { Recordings(Status.Working(Progress(null, Step.MODEL_DOWNLOAD, 0.37f), null)) }
        compose.onAllNodesWithContentDescription("Downloading the speech model (23 MB)… 37%").assertCountEquals(3)
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-night-420dpi")
    fun recordingsInArabicByNight() {
        show("ar-night-recordings") { Recordings(null) }
        compose.onNodeWithContentDescription("٢ من ٧ جاهزة").assertIsDisplayed()
        compose.onNodeWithText("سورة البقرة | Surah Al-Baqarah").assertIsDisplayed()
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
        show("ar-night-playlists") { Home(tab = 1, playlists = listOf(playlist), status = Status.Working(Progress(null, Step.MODEL_DOWNLOAD, 0.6f), null)) }
        compose.onNodeWithContentDescription("التلاوة").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("جارٍ تنزيل نموذج الصوت", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-night-420dpi")
    fun theQuranTabInArabicByNight() {
        show("ar-night-quran") { Home(tab = 0) }
        compose.onNodeWithText("القرآن").assertIsDisplayed()
        compose.onNodeWithContentDescription("البقرة").assertIsDisplayed()
        compose.onNodeWithText("٢٨٦ آية").assertIsDisplayed()
    }

    // The app is for readers whose first language is Arabic and whose second is English: the
    // Arabic name comes before the Latin one in both languages, each the way its page runs.
    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun inEnglishThePageRunsFromTheLeftAndTheArabicNameComesFirst() {
        compose.setContent { CompositionLocalProvider(LocalSurahNames provides surahNames) { RecitationTheme { Home(tab = 0) } } }
        compose.waitForIdle()
        val arabic = compose.onNodeWithContentDescription("البقرة", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val latin = compose.onNodeWithText("Al-Baqarah", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val number = compose.onNodeWithText("2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("number $number, Arabic $arabic, Latin $latin", number.right <= arabic.left && arabic.left < latin.left)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun surahsWithARecitationDownloadedAreToldApart() {
        // Four of the first eight have one; the bars of sound are under their numbers, but for
        // the fourth, whose recitation hasn't been listened to the end: an hourglass is under its.
        compose.setContent { CompositionLocalProvider(LocalSurahNames provides surahNames) { RecitationTheme { Home(tab = 0) } } }
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Has a recitation on the phone", useUnmergedTree = true).assertCountEquals(3)
        compose.onAllNodesWithContentDescription("Has a recitation on the phone, not listened to the end yet", useUnmergedTree = true).assertCountEquals(1)
        // The number is still there to read, and the others keep theirs plain.
        compose.onNodeWithText("3", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("2", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-night-420dpi")
    fun inArabicThePageRunsFromTheRightAndTheArabicNameComesFirst() {
        compose.setContent { CompositionLocalProvider(LocalSurahNames provides surahNames) { RecitationTheme { Home(tab = 0) } } }
        compose.waitForIdle()
        val arabic = compose.onNodeWithContentDescription("البقرة", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val latin = compose.onNodeWithText("Al-Baqarah", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val number = compose.onNodeWithText("٢", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("number $number, Arabic $arabic, Latin $latin", number.left >= arabic.right && arabic.right > latin.right)
    }

    @Test
    @Config(qualifiers = "ar-rLB-w891dp-h411dp-land-night-420dpi")
    fun inArabicOnItsSideTheReaderStillRunsFromTheRight() {
        show("ar-night-reader-on-its-side") { Reader(7_500, meanings = false, playing = false) }
        val back = compose.onNodeWithContentDescription("رجوع").fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithContentDescription("القائمة").fetchSemanticsNode().boundsInRoot
        assertTrue("back $back, menu $menu", back.left > menu.right)
    }
}
