package app.accentcoach.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PhonemesTest {

    private val vocab = setOf("θ", "ɪ", "ŋ", "k", "ð", "ɛ", "ɹ", "ɚ", "aɪ", "a", "ɪ", "t", "ə", "w", "ɔː", "ɔ", "j", "ju", "uː", "u")

    @Test
    fun tokenizesLongestMatchAndDropsStress() {
        assertEquals(listOf("aɪ", "θ", "ɪ", "ŋ", "k"), Phonemes.tokenize("aɪ θˈɪŋk", vocab))
    }

    @Test
    fun splitsRColouredVowels() {
        assertEquals(listOf("w", "ɔː", "t", "ə", "ɹ"), Phonemes.tokenize("wˈɔːtɚ", vocab))
    }

    @Test
    fun neverLeavesALengthMarkAlone() {
        assertEquals(listOf("j", "uː"), Phonemes.tokenize("juː", vocab))
    }

    @Test
    fun stripsMandarinToneMarksAndAspiration() {
        assertEquals(listOf("i", "ɛ", "ə", "t", "ɜː"), Phonemes.normalize(listOf("i5", "ɛɜ", "əɜ", "th", "ɜː")))
    }

    @Test
    fun ctcMergesRepeatsAndDropsBlanks() {
        val vocab = listOf("<pad>", "<s>", "a", "b")
        // Frames: a a <pad> a b b
        val best = intArrayOf(2, 2, 0, 2, 3, 3)
        val logits = FloatArray(best.size * vocab.size).also { l -> best.forEachIndexed { f, v -> l[f * vocab.size + v] = 1f } }
        val heard = CtcDecoder(vocab).decode(logits, best.size)
        assertEquals(listOf("a", "a", "b"), heard.map { it.symbol })
        assertEquals(listOf(0, 60, 80), heard.map { it.timeMs })
    }
}
