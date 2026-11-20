package app.recitation

import app.recitation.core.Repeat
import app.recitation.core.Timings
import app.recitation.core.WordTiming
import app.recitation.ui.WordIndex
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which word is being recited at a moment, when the reciter has gone back over a passage. */
class WordIndexTest {
    // Two verses of three words, a second a word; after the fifth word (at 4.0 s) he goes back
    // to the second word of the second verse (words 4 and 5 again, at 5.2 s and 5.8 s), and the sixth comes at 7 s.
    private val once = listOf(
        listOf(WordTiming(0, intArrayOf(3, 0)), WordTiming(1000, intArrayOf(3, 1000)), WordTiming(2000, intArrayOf(3, 2000))),
        listOf(WordTiming(3000, intArrayOf(3, 3000)), WordTiming(4000, intArrayOf(3, 4000)), WordTiming(7000, intArrayOf(3, 7000))),
    )
    private val again = Repeat(3, listOf(WordTiming(5200, intArrayOf(3, 5200)), WordTiming(5800, intArrayOf(2, 5800, 3, 6100))))
    private val index = WordIndex(Timings(once, 0.9f, repeats = listOf(again)), listOf(3, 3))

    @Test
    fun theWordsOfAPassageSaidAgainAreRecitedASecondTime() {
        assertEquals(listOf(0, 1, 2, 3, 4, 3, 4, 5), listOf(500, 1500, 2500, 3500, 4500, 5300, 6000, 7200).map(index::at))
        // Not standing still on the fifth word until the sixth comes.
        assertEquals(3, index.at(5700))
        assertEquals(1, index.verse(index.at(5300)))
    }

    @Test
    fun theSecondSayingHasItsOwnTimings() {
        val second = index.said(6000)
        assertEquals(listOf(2, 5800, 3, 6100), index.timing(second)!!.letters.toList())
        // It lasts until the next word is said, and the first saying until the going back.
        assertEquals(7000, index.ends(second))
        assertEquals(5200, index.ends(index.said(4500)))
    }

    @Test
    fun tappingAWordStillGoesToWhereItWasFirstSaid() {
        assertEquals(4000, index.starts[4])
        assertEquals(3000, index.startOfVerse(1))
    }

    @Test
    fun aRecitationSaidStraightThroughIsAsBefore() {
        val plain = WordIndex(Timings(once, 0.9f), listOf(3, 3))
        assertEquals(listOf(-1, 0, 4, 4, 5), listOf(-5, 10, 4500, 6000, 9000).map(plain::at))
    }
}
