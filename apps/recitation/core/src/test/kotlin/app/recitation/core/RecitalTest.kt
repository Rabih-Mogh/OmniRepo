package app.recitation.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecitalTest {

    @Test
    fun `the opening formulas may be left out of a recording, the verses may not`() {
        val recital = Recital(112, listOf(listOf("قُلْ", "هُوَ", "ٱللَّهُ", "أَحَدٌ")))
        val formulas = Phonemizer.verse(Recital.ISTIADHA.split(' ')).sumOf { it.size } +
            Phonemizer.verse(Recital.BASMALA.split(' ')).sumOf { it.size }
        assertTrue(recital.phones.take(formulas).all { it.optional })
        assertTrue(recital.phones.drop(formulas).any { !it.optional })
    }

    @Test
    fun `Al-Fatihah and At-Tawbah have no basmala before them`() {
        val verse = listOf(listOf("قُلْ"))
        assertEquals(Recital(1, verse).phones.size, Recital(9, verse).phones.size)
        assertTrue(Recital(2, verse).phones.size > Recital(1, verse).phones.size)
    }

    @Test
    fun `timings survive being saved and read back`() {
        val timings = Timings(
            listOf(
                listOf(WordTiming(0, intArrayOf(2, 0, 5, 240)), WordTiming(580, intArrayOf(7, 580))),
                listOf(WordTiming(6020, intArrayOf())),
            ),
            0.91f,
        )
        val back = Timings.decode(timings.encode())
        assertTrue(back.complete)
        assertEquals(0.91f, back.clarity)
        assertEquals(listOf(2, 1), back.verses.map { it.size })
        assertEquals(listOf(2, 0, 5, 240), back.verses[0][0].letters.toList())
        assertEquals(6020, back.verses[1][0].startMs)
    }

    @Test
    fun `timings of a recording still being listened to say how far they go`() {
        val partial = Timings(listOf(listOf(WordTiming(0, intArrayOf(2, 0)))), 1f, heardMs = 12_340)
        val back = Timings.decode(partial.encode())
        assertEquals(12_340, back.heardMs)
        assertTrue(!back.complete && back.matches)
        // Files written before this was added have only the clarity on their first line.
        assertTrue(Timings.decode("0.9\n0,2,0\n").complete)
    }

    @Test
    fun `knows which verse is being recited at a moment`() {
        val timings = Timings(
            listOf(listOf(WordTiming(500, intArrayOf())), listOf(WordTiming(4000, intArrayOf()), WordTiming(5000, intArrayOf())), listOf(WordTiming(9000, intArrayOf()))),
            0.9f,
        )
        assertEquals(0, timings.verseAt(0))
        assertEquals(0, timings.verseAt(3999))
        assertEquals(1, timings.verseAt(5500))
        assertEquals(2, timings.verseAt(60_000))
    }

    @Test
    fun `the place in a word glides from letter to letter`() {
        // Letters 0-2 from 1000 ms, 2-4 from 1200 ms, 4-9 from 1500 ms; the next word starts at 2000 ms.
        val word = WordTiming(1000, intArrayOf(2, 1000, 4, 1200, 9, 1500))
        assertEquals(Uttered(0, 2, 0f), word.utteredAt(900, 2000))
        assertEquals(Uttered(0, 2, 0.5f, before = 0, sinceMs = 100), word.utteredAt(1100, 2000))
        // Each stretch also says where the one before it began, and how long ago it took over.
        assertEquals(Uttered(2, 4, 0f, before = 0, sinceMs = 0), word.utteredAt(1200, 2000))
        assertEquals(Uttered(4, 9, 0.5f, before = 2, sinceMs = 250), word.utteredAt(1750, 2000))
        assertEquals(Uttered(4, 9, 1f, before = 2, sinceMs = 3500), word.utteredAt(5000, 2000))
    }

    @Test
    fun `a long breath after a word doesn't slow its last letter to a crawl`() {
        val word = WordTiming(1000, intArrayOf(3, 1000))
        // The next word starts ten seconds later; the letter is crossed in 1.2 s all the same.
        assertEquals(Uttered(0, 3, 0.5f, before = 0, sinceMs = 600), word.utteredAt(1600, 11_000))
        assertEquals(null, WordTiming(1000, intArrayOf()).utteredAt(1000, 2000))
    }
}
