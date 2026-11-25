package app.accentcoach.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MyWordsTest {

    @Test
    fun aWordIsKeptWithoutThePunctuationRoundIt() {
        assertEquals("undisablable", MyWords.clean("  undisablable? "))
        assertEquals("Worcestershire", MyWords.clean("\"Worcestershire\""))
        assertEquals("don't", MyWords.clean("don't!"))
        assertEquals("well-known", MyWords.clean("well-known."))
    }

    @Test
    fun aShortPhraseIsKeptWithSingleSpaces() {
        assertEquals("rural juror", MyWords.clean("rural   juror"))
        assertEquals("a b c d e f", MyWords.clean("a b c d e f"))
    }

    @Test
    fun whatCannotBeSaidIsTurnedAway() {
        assertNull(MyWords.clean(""))
        assertNull(MyWords.clean("  ?! "))
        assertNull(MyWords.clean("route 66"))
        assertNull(MyWords.clean("naïve"))
        assertNull(MyWords.clean("-"))
        assertNull(MyWords.clean("a b c d e f g"))
        assertNull(MyWords.clean("a".repeat(MyWords.MAX_LENGTH + 1)))
    }

    @Test
    fun theDictionaryIsLookedUpInSmallLetters() {
        assertEquals("february", MyWords.key("February"))
    }

    @Test
    fun aWordIsPractisedAsASentenceOfItsOwn() {
        val word = MyWord("rural juror", listOf(Word("rural", listOf("ɹ", "ʊ", "ɹ", "ə", "l")), Word("juror", listOf("dʒ", "ʊ", "ɹ", "ɚ"))), heard = false)
        assertEquals("rural juror", word.sentence.text)
        assertEquals(2, word.sentence.words.size)
    }
}
