package app.recitation.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhonemizerTest {

    /** Sounds by name, with "?" after the ones that may be left out. */
    private fun say(word: String, next: String? = null, stop: Boolean = false): String =
        Phonemizer.word(word, next, stop).joinToString(" ") { it.sound.name + if (it.optional) "?" else "" }

    @Test
    fun `plain vowels and consonants`() {
        assertEquals("B I S M I", say("بِسْمِ"))
        assertEquals("Y A W M I", say("يَوْمِ"))
    }

    @Test
    fun `a letter with no mark is silent, and long vowels are read from the letter after`() {
        // The l of "al-" is swallowed by the doubled r; the bare alif after the m makes its a long.
        assertEquals("LINK? R A HHA M AA N I", say("ٱلرَّحْمَـٰنِ"))
        assertEquals("LINK? L AYN AA L A M II N A", say("ٱلْعَـٰلَمِينَ"))
        assertEquals("K A F A R UU", say("كَفَرُوا۟"))
        assertEquals("AYN A L AA", say("عَلَىٰ"))
        assertEquals("F II", say("فِى"))
    }

    @Test
    fun `the long a of Allah is read though it isn't written`() {
        assertEquals("LINK? L AA H I", say("ٱللَّهِ"))
        assertEquals("L I L AA H I", say("لِلَّهِ"))
    }

    @Test
    fun `hamza is optional, because the model rarely hears it`() {
        assertEquals("HAMZA? I Y AA K A", say("إِيَّاكَ"))
        assertEquals("HAMZA? AA B AA HAMZA? U H U M", say("ءَابَآؤُهُمْ"))
    }

    @Test
    fun `a bare n or m merges into a doubled letter in the next word, and n before b becomes m`() {
        assertEquals("F A H U", say("فَهُم", next = "مُّقْمَحُونَ"))
        assertEquals("M I N", say("مِن", next = "تَفَـٰوُتٍۢ"))
        assertEquals("M I", say("مِن", next = "رَّبِّهِمْ"))
        assertEquals("M I M", say("مِنۢ", next = "بَعْدِ"))
    }

    @Test
    fun `tanween ends in an n that can be dropped`() {
        assertEquals("AYN A M A L A N?", say("عَمَلًۭا"))
        assertEquals("SHIN A Y HAMZA? I N?", say("شَىْءٍۢ"))
    }

    @Test
    fun `at a stop the last short vowel may go, and -un and -in with it`() {
        assertEquals("LINK? R A HHA II M I?", say("ٱلرَّحِيمِ", stop = true))
        assertEquals("Q A D II R U? N?", say("قَدِيرٌ", stop = true))
        assertEquals("AYN A M A L A N?", say("عَمَلًۭا", stop = true))
        assertEquals("L A H UU?", say("لَهُۥ", stop = true))
    }

    @Test
    fun `a stop sign inside a verse allows the same`() {
        val verse = Phonemizer.verse(listOf("عَذَابُ", "جَهَنَّمَ ۖ", "وَبِئْسَ", "ٱلْمَصِيرُ"))
        assertEquals(listOf(false, true, false, true), verse.map { it.last().optional })
    }

    @Test
    fun `opening letters are read by name`() {
        assertEquals("Y AA S II N", say("يسٓ"))
        assertEquals("HAMZA A L I F L AA M M II M", say("الٓمٓ"))
    }

    @Test
    fun `every sound says how far into the word it reaches`() {
        val word = "ٱلْحَمْدُ"
        val reach = Phonemizer.word(word).map { it.upTo }
        assertEquals(reach.sorted(), reach)
        assertEquals(word.length, reach.last())
        assertTrue(reach.first() >= 1)
    }
}
