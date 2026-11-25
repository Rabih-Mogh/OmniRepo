package app.accentcoach.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrillsTest {

    private val think = Word("think", listOf("θ", "ɪ", "ŋ", "k"))
    private val sink = Word("sink", listOf("s", "ɪ", "ŋ", "k"))
    private val th = Lesson(
        id = "th", title = "TH as in think", sound = "θ", made = "", slip = "",
        words = listOf(think, Word("bath", listOf("b", "æ", "θ"))),
        pairs = listOf(think to sink),
    )
    private val word = th.items[0]
    private val pair = th.items[2]

    private fun heard(vararg p: String) = p.toList()

    @Test
    fun theDrillIsTheWordsThenThePairs() {
        assertEquals(listOf("think", "bath", "think, sink"), th.items.map { it.text })
        assertFalse(word.isPair)
        assertTrue(pair.isPair)
    }

    @Test
    fun onlyTheLessonsSoundIsJudgedInAWord() {
        // The TH is right; the vowel and the last consonant aren't, and don't count.
        val result = Drills.judge(th, word, heard("θ", "iː", "ŋ"))
        assertEquals(Said.NATIVE, result.said)
        assertEquals(listOf("θ"), result.judged.map { it.expected })
        assertTrue(result.assessment.words.single().needsWork)
    }

    @Test
    fun anotherSoundInItsPlaceIsOff() {
        val result = Drills.judge(th, word, heard("t", "ɪ", "ŋ", "k"))
        assertEquals(Said.OFF, result.said)
        assertEquals("t", result.judged.single().heard)
    }

    @Test
    fun leavingTheSoundOutIsOff() {
        val result = Drills.judge(th, th.items[1], heard("b", "æ"))
        assertEquals(Said.OFF, result.said)
        assertEquals(Verdict.MISSING, result.judged.single().verdict)
    }

    @Test
    fun aNearSoundIsClose() {
        val day = Lesson("ay", "The glide in day", "eɪ", "", "", listOf(Word("day", listOf("d", "eɪ"))), emptyList())
        assertEquals(Said.CLOSE, Drills.judge(day, day.items.single(), heard("d", "e")).said)
    }

    @Test
    fun aPairIsJudgedOnTheSoundItsWordsDifferIn() {
        assertEquals(
            listOf(listOf(true, false, false, false), listOf(true, false, false, false)),
            Drills.sounds(th, pair).map { word -> word.map { it.second } },
        )
        val told = Drills.judge(th, pair, heard("θ", "ɪ", "ŋ", "k", "s", "ɪ", "ŋ", "k"))
        assertEquals(Said.NATIVE, told.said)
        assertEquals(listOf("θ", "s"), told.judged.map { it.expected })
    }

    @Test
    fun aPairSaidAsTheSameWordTwiceIsOff() {
        assertEquals(Said.OFF, Drills.judge(th, pair, heard("s", "ɪ", "ŋ", "k", "s", "ɪ", "ŋ", "k")).said)
        assertEquals(Said.OFF, Drills.judge(th, pair, heard("θ", "ɪ", "ŋ", "k", "θ", "ɪ", "ŋ", "k")).said)
    }

    @Test
    fun aPairWithOnlyOneWordSaidIsOff() {
        assertEquals(Said.OFF, Drills.judge(th, pair, heard("θ", "ɪ", "ŋ", "k")).said)
    }

    @Test
    fun soundsWrittenAsOneSymbolAreJudgedInTheirParts() {
        val r = Lesson("r", "American R", "ɹ", "", "", listOf(Word("water", listOf("w", "ɔː", "ɾ", "ɚ"))), emptyList())
        val item = r.items.single()
        assertEquals(listOf("w", "ɔː", "ɾ", "ə", "ɹ"), Drills.sounds(r, item).single().map { it.first })
        assertEquals(Said.NATIVE, Drills.judge(r, item, heard("w", "ɑː", "ɾ", "ɚ")).said)
        assertEquals(Said.OFF, Drills.judge(r, item, heard("w", "ɑː", "ɾ", "ə", "r")).said)
    }

    @Test
    fun hearingNothingIsUnheard() {
        val result = Drills.judge(th, word, emptyList())
        assertEquals(Said.UNHEARD, result.said)
        assertTrue(result.judged.isEmpty())
    }

    @Test
    fun threeInFourMakesALessonLearned() {
        assertTrue(Drills.learned(3, 4))
        assertTrue(Drills.learned(7, 9))
        assertFalse(Drills.learned(6, 9))
        assertFalse(Drills.learned(0, 0))
    }
}
