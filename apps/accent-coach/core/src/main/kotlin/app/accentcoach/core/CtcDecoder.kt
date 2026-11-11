package app.accentcoach.core

/** A phoneme the recogniser heard, and the audio time it was heard at. */
data class HeardPhoneme(val symbol: String, val timeMs: Int)

/**
 * Turns the recogniser's per-frame scores into phonemes (greedy CTC): take the best
 * token in every frame, merge repeats, and drop blanks and special tokens.
 */
class CtcDecoder(private val vocab: List<String>, private val msPerFrame: Int = 20) {

    private val blank = vocab.indexOf("<pad>").coerceAtLeast(0)
    private val skip = vocab.withIndex().filter { (_, t) -> t.startsWith("<") || t.isBlank() }.map { it.index }.toSet()

    /** [logits] is frames × vocabulary, row after row. */
    fun decode(logits: FloatArray, frames: Int): List<HeardPhoneme> {
        val size = vocab.size
        require(logits.size >= frames * size) { "expected $frames×$size scores, got ${logits.size}" }
        val out = mutableListOf<HeardPhoneme>()
        var previous = blank
        for (f in 0 until frames) {
            var best = 0
            var bestScore = Float.NEGATIVE_INFINITY
            val row = f * size
            for (v in 0 until size) {
                val score = logits[row + v]
                if (score > bestScore) {
                    bestScore = score
                    best = v
                }
            }
            if (best != previous && best != blank && best !in skip) {
                out += HeardPhoneme(vocab[best], f * msPerFrame)
            }
            previous = best
        }
        return out
    }
}
