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
        assertEquals(0.91f, back.clarity)
        assertEquals(listOf(2, 1), back.verses.map { it.size })
        assertEquals(listOf(2, 0, 5, 240), back.verses[0][0].letters.toList())
        assertEquals(6020, back.verses[1][0].startMs)
    }

    @Test
    fun `a word fills in letter by letter`() {
        val word = WordTiming(1000, intArrayOf(2, 1000, 4, 1200, 9, 1500))
        assertEquals(0, word.reachedAt(900))
        assertEquals(2, word.reachedAt(1000))
        assertEquals(4, word.reachedAt(1499))
        assertEquals(9, word.reachedAt(5000))
    }
}
