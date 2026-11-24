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
    fun goingBackIsKnownByTheWordsGoneBackOver() {
        // From the fifth word back to the fourth: those two blocks. Nowhere else.
        assertEquals(3..4, index.goesBack(index.said(5300)))
        assertEquals(listOf(null, null, null, null), listOf(500, 4500, 6000, 7200).map { index.goesBack(index.said(it)) })
    }

    @Test
    fun theWaveOverAPassageSaidAgainGoesByWhereTheRecitationIs() {
        // Before he goes back, none; from then on, the words gone back over, when he went back, and when he goes on past them.
        assertEquals(null, index.goneBack(4500))
        assertEquals(WordIndex.GoneBack(3..4, 5200, 7000), index.goneBack(5200))
        assertEquals(WordIndex.GoneBack(3..4, 5200, 7000), index.goneBack(5900))
        // Gone back to that place in the recording, it is the same again.
        assertEquals(WordIndex.GoneBack(3..4, 5200, 7000), index.goneBack(5300))
        assertEquals(null, WordIndex(Timings(once, 0.9f), listOf(3, 3)).goneBack(6000))
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

    @Test
    fun wordsHeardOutOfTurnLightUpWhereTheyArePlayed() {
        // Still being listened to: the first verse is placed (as far as 2.5 s), and the reader has
        // jumped to 60 s, where the last two words were heard out of turn.
        val soFar = listOf(once[0])
        val there = Repeat(4, listOf(WordTiming(61_000, intArrayOf(3, 61_000)), WordTiming(63_000, intArrayOf(3, 63_000))))
        val index = WordIndex(Timings(soFar, 1f, heardMs = 2_500, repeats = listOf(there), ahead = listOf(60_000 until 70_000)), listOf(3, 3))
        assertEquals(listOf(0, 2, -1, -1, 4, 5, -1), listOf(500, 2_400, 30_000, 60_500, 61_500, 64_000, 71_000).map(index::at))
        assertEquals(1, index.verse(index.at(64_000)))
    }

    @Test
    fun aVerseNotHeardYetCanStillBeGoneToAndFollowed() {
        // Only the first verse is placed; a quick look over the recording says roughly where the second begins.
        val index = WordIndex(Timings(listOf(once[0]), 1f, heardMs = 2_500, sketch = intArrayOf(0, 40_000)), listOf(3, 3))
        assertEquals(0, index.startOfVerse(0))
        assertEquals(40_000, index.startOfVerse(1))
        // No word lights up there, but the verse being played is known.
        assertEquals(-1, index.at(45_000))
        assertEquals(1, index.verseAt(45_000))
        assertEquals(0, index.verseAt(20_000))
    }
}
