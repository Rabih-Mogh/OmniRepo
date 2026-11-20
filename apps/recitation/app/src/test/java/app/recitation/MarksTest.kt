package app.recitation

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recitation.data.Marks
import app.recitation.data.VerseRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/** The pins and collections: what they hold, and that it is still there after the app is closed. */
@RunWith(AndroidJUnit4::class)
class MarksTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun file() = File(folder.root, "marks.json")

    @Test
    fun aPinIsOnOneVerseAndMovesWhenPutOnAnother() {
        val marks = Marks(file())
        val pin = marks.addPin("Reading", VerseRef(2, 142))
        marks.movePin(pin.id, VerseRef(2, 255))
        assertEquals(listOf(VerseRef(2, 255)), marks.pins.value.map { it.at })
        // A second pin is a second place; the first stays where it is.
        marks.addPin("Memorising", VerseRef(67, 1))
        assertEquals(listOf(VerseRef(2, 255), VerseRef(67, 1)), marks.pins.value.map { it.at })
        marks.removePin(pin.id)
        assertEquals(listOf("Memorising"), marks.pins.value.map { it.name })
    }

    @Test
    fun theCollectionForQuestionsIsThereFromTheStartAndStays() {
        val marks = Marks(file())
        val later = marks.collections.value.single()
        assertTrue(later.builtIn)
        marks.removeCollection(later.id)
        assertEquals(1, marks.collections.value.size)
    }

    @Test
    fun aCollectionKeepsItsVersesInTheQuransOrderAndOnce() {
        val marks = Marks(file())
        marks.setSaved(Marks.LATER, VerseRef(18, 10), true)
        marks.setSaved(Marks.LATER, VerseRef(2, 255), true)
        marks.setSaved(Marks.LATER, VerseRef(2, 3), true)
        marks.setSaved(Marks.LATER, VerseRef(2, 255), true)
        assertEquals(listOf(VerseRef(2, 3), VerseRef(2, 255), VerseRef(18, 10)), marks.collections.value.single().verses)
        marks.setSaved(Marks.LATER, VerseRef(2, 3), false)
        assertEquals(listOf(VerseRef(2, 255), VerseRef(18, 10)), marks.collections.value.single().verses)
    }

    @Test
    fun everythingIsStillThereNextTime() {
        val first = Marks(file())
        first.addPin("Reading", VerseRef(36, 12))
        val mercy = first.addCollection("Verses of mercy")
        first.setSaved(mercy.id, VerseRef(39, 53), true)
        first.setSaved(Marks.LATER, VerseRef(4, 82), true)

        val again = Marks(file())
        assertEquals(listOf("Reading" to VerseRef(36, 12)), again.pins.value.map { it.name to it.at })
        assertEquals(listOf("", "Verses of mercy"), again.collections.value.map { it.name })
        assertEquals(listOf(VerseRef(4, 82)), again.collections.value[0].verses)
        assertEquals(listOf(VerseRef(39, 53)), again.collections.value[1].verses)
        // A collection the user made can be deleted.
        again.removeCollection(mercy.id)
        assertEquals(1, Marks(file()).collections.value.size)
    }
}
