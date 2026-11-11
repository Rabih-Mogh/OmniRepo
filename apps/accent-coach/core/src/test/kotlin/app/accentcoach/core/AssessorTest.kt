package app.accentcoach.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssessorTest {

    // "I think there" in espeak-ng General American.
    private val sentence = Sentence(
        id = "t1", focus = "TH", text = "I think there",
        words = listOf(
            Word("I", listOf("aɪ")),
            Word("think", listOf("θ", "ɪ", "ŋ", "k")),
            Word("there", listOf("ð", "ɛ", "ɹ")),
        ),
    )

    private fun heard(vararg p: String) = p.toList()

    @Test
    fun nativeSpeechScoresFull() {
        val a = Assessor.assess(sentence, heard("aɪ", "θ", "ɪ", "ŋ", "k", "ð", "ɛɹ"))
        assertEquals(100, a.score)
        assertTrue(a.words.none { it.needsWork || it.slightlyOff })
        assertTrue(a.tips.isEmpty())
    }

    @Test
    fun thSaidAsTAndDIsFlaggedWithATip() {
        val a = Assessor.assess(sentence, heard("aɪ", "t", "ɪ", "ŋ", "k", "d", "ɛ", "ɹ"))
        assertTrue(a.words[1].needsWork)
        assertTrue(a.words[2].needsWork)
        assertEquals(Verdict.WRONG, a.words[1].phonemes.first().verdict)
        assertEquals("t", a.words[1].phonemes.first().heard)
        assertEquals(listOf("/θ/", "/ð/"), a.tips.map { it.sound })
        assertTrue(a.score in 60..85, "score ${a.score}")
    }

    @Test
    fun rolledRIsWrongButAmericanRIsFine() {
        val rolled = Assessor.assess(sentence, heard("aɪ", "θ", "ɪ", "ŋ", "k", "ð", "ɛ", "r"))
        assertEquals(Verdict.WRONG, rolled.words[2].phonemes.last().verdict)
        assertFalse(rolled.words[1].needsWork)
        assertTrue(rolled.words[2].needsWork)
        assertEquals("/ɹ/", rolled.tips.single().sound)
    }

    @Test
    fun aDroppedSoundIsMissing() {
        val a = Assessor.assess(sentence, heard("aɪ", "θ", "ɪ", "ŋ", "ð", "ɛ", "ɹ"))
        val k = a.words[1].phonemes.first { it.expected == "k" }
        assertEquals(Verdict.MISSING, k.verdict)
        assertEquals("/k/ dropped", a.tips.single().sound)
    }

    @Test
    fun aVowelAddedAfterAConsonantIsFlagged() {
        val desk = Sentence("d", "final", "desk", listOf(Word("desk", listOf("d", "ɛ", "s", "k"))))
        val a = Assessor.assess(desk, heard("d", "ɛ", "s", "k", "u"))
        assertTrue(a.words.single().phonemes.any { it.expected == null && it.verdict == Verdict.WRONG })
        assertEquals("Extra vowel", a.tips.single().sound)
    }

    @Test
    fun monophthongForGlideIsOnlySlightlyOff() {
        val day = Sentence("g", "glide", "day", listOf(Word("day", listOf("d", "eɪ"))))
        val a = Assessor.assess(day, heard("d", "e"))
        assertTrue(a.words.single().slightlyOff)
        assertFalse(a.words.single().needsWork)
    }

    @Test
    fun americanFlapAndReducedVowelsCountAsNative() {
        val water = Sentence("w", "flap", "water", listOf(Word("water", listOf("w", "ɔː", "t", "ɚ"))))
        val a = Assessor.assess(water, heard("w", "ɑː", "ɾ", "ɚ"))
        assertEquals(100, a.score)
    }

    @Test
    fun aDoubledSoundAcrossWordsCanBeSaidOnce() {
        val s = Sentence("l", "L", "will leave", listOf(Word("will", listOf("w", "ɪ", "l")), Word("leave", listOf("l", "iː", "v"))))
        assertEquals(100, Assessor.assess(s, heard("w", "ɪ", "l", "iː", "v")).score)
    }

        @Test
    fun hearingNothingScoresZero() {
        val a = Assessor.assess(sentence, emptyList())
        assertEquals(0, a.score)
        assertTrue(a.words.all { it.needsWork })
    }
}
