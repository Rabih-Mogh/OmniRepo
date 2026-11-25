package app.accentcoach

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accentcoach.core.MyWord
import app.accentcoach.core.Word
import app.accentcoach.data.Dictionary
import app.accentcoach.data.References
import app.accentcoach.data.Wordbook
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/** The learner's own words: where their native sounds come from, and how they are kept. */
@RunWith(AndroidJUnit4::class)
class WordsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val bundled get() = Dictionary { context.assets.open("dictionary.txt") }
    private val small = Dictionary { "apple\tæ p ə l\nwater\tw ɔː ɾ ə ɹ\nzebra\tz iː b ɹ ə\n".byteInputStream() }

    @Test
    fun theBundledDictionaryHasTheWordsPeopleFindHard() {
        val found = bundled.lookup(setOf("thoroughly", "squirrel", "water", "zymurgy", "undisablable"))
        assertEquals(listOf("θ", "ʌ", "ɹ", "oʊ", "l", "i"), found["thoroughly"])
        assertEquals(listOf("w", "ɔː", "ɾ", "ə", "ɹ"), found["water"])
        assertTrue("squirrel" in found && "zymurgy" in found)
        assertFalse("undisablable" in found)
    }

    @Test
    fun aWordTheDictionaryLacksIsSimplyNotFound() {
        assertEquals(mapOf("water" to listOf("w", "ɔː", "ɾ", "ə", "ɹ")), small.lookup(setOf("water", "banana", "zzz")))
        assertTrue(small.lookup(emptySet()).isEmpty())
    }

    @Test
    fun aKnownWordGetsItsSoundsFromTheDictionaryWithoutTheVoice() = runBlocking {
        val word = References(small) { error("the voice isn't needed") }.of("Water")
        assertEquals(MyWord("Water", listOf(Word("Water", listOf("w", "ɔː", "ɾ", "ə", "ɹ"))), heard = false), word)
    }

    @Test
    fun anUnknownWordIsHeardFromTheVoiceAndMarkedSo() = runBlocking {
        val asked = ArrayList<String>()
        val references = References(small) { word -> asked += word; listOf("ʌ", "n", "d", "uː") }
        val word = references.of("water undo")
        assertEquals(listOf("undo"), asked)
        assertTrue(word.heard)
        assertEquals(listOf(listOf("w", "ɔː", "ɾ", "ə", "ɹ"), listOf("ʌ", "n", "d", "uː")), word.words.map { it.phonemes })
    }

    @Test
    fun aWordThatCannotBeWorkedOutIsNotAdded() {
        val references = References(small) { error("no voice") }
        assertThrows(IllegalStateException::class.java) { runBlocking { references.of("undo") } }
    }

    @Test
    fun wordsAreKeptLatestFirstWithTheirBestScore() {
        val file = File(folder.root, "words.json")
        val book = Wordbook(file)
        book.add(MyWord("water", listOf(Word("water", listOf("w", "ɔː", "ɾ", "ə", "ɹ"))), heard = false))
        book.add(MyWord("undo", listOf(Word("undo", listOf("ʌ", "n", "d", "uː"))), heard = true))
        book.scored("water", 70)
        book.scored("water", 55)

        val reopened = Wordbook(file)
        assertEquals(listOf("undo", "water"), reopened.words.map { it.text })
        assertEquals(70, reopened.words[1].best)
        assertNull(reopened.words[0].best)
        assertTrue(reopened.words[0].heard)
        assertEquals(listOf("ʌ", "n", "d", "uː"), reopened.words[0].words.single().phonemes)
    }

    @Test
    fun addingAWordAgainKeepsItsScoreAndRemovingForgetsIt() {
        val file = File(folder.root, "words.json")
        val book = Wordbook(file)
        val water = MyWord("water", listOf(Word("water", listOf("w", "ɔː", "ɾ", "ə", "ɹ"))), heard = false)
        book.add(water)
        book.scored("water", 80)
        book.add(water)
        assertEquals(listOf(80), book.words.map { it.best })

        book.remove("water")
        assertTrue(Wordbook(file).words.isEmpty())
    }
}
