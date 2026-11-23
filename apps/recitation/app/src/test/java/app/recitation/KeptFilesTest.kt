package app.recitation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recitation.data.Library
import app.recitation.data.Marks
import app.recitation.data.Playlist
import app.recitation.data.Stats
import app.recitation.data.Track
import app.recitation.data.VerseRef
import app.recitation.data.writeWhole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/** What the app keeps on the phone is written whole or not at all, and never written over when it can't be read. */
@RunWith(AndroidJUnit4::class)
class KeptFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun aFileIsReplacedInOneStepAndNothingIsLeftBesideIt() {
        val file = File(folder.root, "deep/in/kept.txt")
        file.writeWhole("first")
        file.writeWhole("second")
        assertEquals("second", file.readText())
        assertEquals(listOf("kept.txt"), file.parentFile!!.list()!!.toList())
    }

    @Test
    fun pinsThatCannotBeReadArePutAsideRatherThanWrittenOver() {
        val file = File(folder.root, "marks.json")
        file.writeText("""{"pins":[{"id":"a","name":"Morning","surah":2""") // cut off half way
        val marks = Marks(file)
        assertTrue("it opens, with nothing in it", marks.pins.value.isEmpty())
        marks.addPin("Evening", VerseRef(36, 1))
        assertEquals("the new pin is kept", 1, Marks(file).pins.value.size)
        assertEquals("""{"pins":[{"id":"a","name":"Morning","surah":2""", File(folder.root, "marks.json.unreadable").readText())
    }

    @Test
    fun listeningThatCannotBeReadIsPutAsideRatherThanWrittenOver() {
        val file = File(folder.root, "stats.json")
        file.writeText("")
        val stats = Stats(file)
        stats.listened(1, 0..0, 4_000)
        assertEquals(setOf(1), Stats(file).listening.value.heard[1])
        assertTrue(File(folder.root, "stats.json.unreadable").exists())
    }

    @Test
    fun aFirstOpeningPutsNothingAside() {
        Marks(File(folder.root, "marks.json"))
        Stats(File(folder.root, "stats.json"))
        assertEquals(emptyList<String>(), folder.root.list()!!.toList())
    }

    @Test
    fun aWholePlaylistIsChangedAtOnceAndAForgottenRecordingIsCounted() {
        val library = Library.get(ApplicationProvider.getApplicationContext())
        library.add(Playlist("https://example.org/kept", "Kept", "Test", listOf(Track("kept-a", "A", 60, 1), Track("kept-b", "B", 60, 2))))
        library.updateAll { if (it.id.startsWith("kept-")) it.copy(wanted = true) else it }
        assertTrue(library.track("kept-a")!!.wanted && library.track("kept-b")!!.wanted)

        // The start of a download, how big the sound it is of is, and one in another form from an earlier try.
        val part = library.audioPart("kept-a", "m4a").apply { writeText("start") }
        val size = library.sizeNote(part).apply { writeText("5000") }
        val other = library.audioPart("kept-a", "webm").apply { writeText("start") }
        assertNull("none of them is taken for the finished sound", library.audio("kept-a"))
        library.dropPartial("kept-a", keep = part)
        assertTrue(part.exists() && size.exists())
        assertFalse(other.exists())

        val before = library.timesForgotten("kept-a")
        library.forget("kept-a")
        assertEquals(before + 1, library.timesForgotten("kept-a"))
        assertFalse(part.exists() || size.exists())
        assertFalse(library.track("kept-a")!!.wanted)
        assertEquals(0, library.timesForgotten("kept-b"))
    }
}
