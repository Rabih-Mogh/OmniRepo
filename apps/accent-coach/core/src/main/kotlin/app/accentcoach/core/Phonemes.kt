package app.accentcoach.core

/**
 * Phoneme spelling shared by the native reference (espeak-ng, General American) and
 * the recogniser (wav2vec2 trained on espeak labels), so both sides are compared in
 * the same symbols.
 */
object Phonemes {

    /**
     * R-coloured and syllabic tokens the recogniser emits as one symbol but espeak may
     * write as two (or the other way round). Both sides are split into the parts, so
     * "ɚ" and "ə ɹ" compare equal.
     */
    private val SPLIT = mapOf(
        "ɚ" to listOf("ə", "ɹ"),
        "ɑːɹ" to listOf("ɑː", "ɹ"),
        "ɔːɹ" to listOf("ɔː", "ɹ"),
        "oːɹ" to listOf("oː", "ɹ"),
        "ɪɹ" to listOf("ɪ", "ɹ"),
        "ɛɹ" to listOf("ɛ", "ɹ"),
        "ʊɹ" to listOf("ʊ", "ɹ"),
        "aɪɚ" to listOf("aɪ", "ə", "ɹ"),
        "aɪə" to listOf("aɪ", "ə"),
        "əl" to listOf("ə", "l"),
        "ju" to listOf("j", "u"),
    )

    /** Stress, syllable and boundary marks that espeak writes and the recogniser doesn't. */
    private val MARKS = setOf('ˈ', 'ˌ', '.', '-', '_', ' ', '\'')

    private const val VOWEL_CHARS = "aeiouyæɑɒɐəɘɛɜɞɪɨʉʊʌɔøœɤɯᵻɵɚ"

    /** Aspirated stops written the Mandarin way; English stops are aspirated anyway. */
    private val ASPIRATED = mapOf("th" to "t", "kh" to "k", "ph" to "p", "tsh" to "ts", "tɕh" to "tɕ", "ts.h" to "ts.")

    /**
     * Splits compound tokens into their parts (see [SPLIT]) and strips Mandarin tone
     * marks ("iɛ5", "uoɜ"), which the recogniser falls back on when it's unsure.
     */
    fun normalize(tokens: List<String>): List<String> = tokens.flatMap { raw ->
        val t = untoned(ASPIRATED[raw] ?: raw)
        if (t.isEmpty()) emptyList() else SPLIT[t] ?: listOf(t)
    }

    private fun untoned(t: String): String {
        val noDigits = t.trimEnd { it.isDigit() }
        // A trailing ɜ is a tone mark only when it follows another vowel ("əɜ", "iɛɜ"); "ɜ"/"ɜː" are real vowels.
        return if (noDigits.length > 1 && noDigits.endsWith("ɜ") && isVowel(noDigits.dropLast(1).takeLast(1))) {
            noDigits.dropLast(1)
        } else {
            noDigits
        }
    }

    /**
     * Cuts an IPA string into tokens of [vocab], longest match first, dropping stress
     * and boundary marks. Characters that match nothing are skipped.
     */
    fun tokenize(ipa: String, vocab: Set<String>): List<String> {
        val maxLen = vocab.maxOfOrNull { it.length } ?: 1
        val s = ipa.filterNot { it in MARKS }
        val out = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            // Don't take a match that would leave a length mark on its own ("ju" + "ː").
            val len = (minOf(maxLen, s.length - i) downTo 1).firstOrNull {
                s.substring(i, i + it) in vocab && s.getOrNull(i + it) != 'ː'
            }
            if (len == null) {
                i++
            } else {
                out += s.substring(i, i + len)
                i += len
            }
        }
        return normalize(out)
    }

    fun isVowel(p: String): Boolean = p.firstOrNull()?.let { it in VOWEL_CHARS } ?: false

    /** The phoneme without its length mark, so "iː" and "i" can be told apart from a real change. */
    fun base(p: String): String = p.replace("ː", "").replace(":", "")
}
