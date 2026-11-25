package app.accentcoach.core

/**
 * A word or short phrase the learner added to practise, with its native sounds. [heard]
 * is true when some of those sounds weren't in the dictionary and were written down by
 * the recogniser from the phone's voice instead. [best] is the best score so far.
 */
data class MyWord(val text: String, val words: List<Word>, val heard: Boolean, val best: Int? = null) {
    val sentence: Sentence get() = Sentence("mine", "My words", text, words)
}

object MyWords {
    const val MAX_WORDS = 6
    const val MAX_LENGTH = 60

    /**
     * What was typed, as it is kept: its words with single spaces between and without
     * the punctuation round them. Null if that isn't a word or a short phrase: nothing
     * to say, too long, or something other than letters, apostrophes and hyphens.
     */
    fun clean(typed: String): String? {
        val words = typed.trim().split(Regex("\\s+")).map { it.trim { c -> c in ROUND } }.filter { it.isNotEmpty() }
        val text = words.joinToString(" ")
        val sayable = words.all { word -> word.any(::isLetter) && word.all { isLetter(it) || it in INSIDE } }
        return text.takeIf { words.isNotEmpty() && words.size <= MAX_WORDS && text.length <= MAX_LENGTH && sayable }
    }

    /** The word as the dictionary lists it: in small letters. */
    fun key(word: String): String = word.lowercase()

    private fun isLetter(c: Char) = c in 'a'..'z' || c in 'A'..'Z'

    /** Punctuation taken off the ends of a word. */
    private const val ROUND = ".,;:!?\"()"

    /** What a word may have between its letters: don't, well-known. */
    private const val INSIDE = "'’-"
}
