package app.recitation.core

import kotlin.math.exp
import kotlin.math.ln

/**
 * The sounds of recited Arabic that words are broken into. The speech model writes sounds
 * in IPA and was trained on ordinary speech in many languages, so one Arabic sound can
 * come out as any of a few symbols: [heardAs] lists the ones to accept for it.
 */
enum class Sound(val heardAs: String) {
    A("a æ ɐ ɑ ʌ ə ɛ aː ɑː"),
    AA("aː ɑː a æ ɑ ɐ æː"),
    I("i ɪ e ᵻ iː"),
    II("iː i ɪ eː e"),
    U("u ʊ o uː"),
    UU("uː u oː ʊ o"),

    /** The vowel a word written with a joining alif (ٱ) starts with after a pause. */
    LINK("a i u ɪ ʊ æ ɐ ə e"),
    B("b p"),
    T("t t̪"),
    THA("θ s"),
    JIM("dʒ ʒ ɡ"),
    HHA("ħ h x"),
    KHA("x χ ħ"),
    D("d dˤ"),
    DHAL("ð z d"),
    R("r ɾ ɹ"),
    Z("z"),
    S("s s̪"),
    SHIN("ʃ"),
    SAD("s s̪"),
    DAD("dˤ d"),
    TAH("t t̪"),
    ZAH("ð z dˤ"),
    AYN("ʕ ʔ"),
    GHAYN("ɣ ʁ ɡ"),
    F("f"),
    Q("q k"),
    K("k"),
    L("l ɫ"),
    M("m"),
    N("n ŋ"),
    H("h ħ"),
    W("w"),
    Y("j"),
    HAMZA("ʔ"),

    /** The closed ta (ة): "t" when the reciter carries on, "h" at a stop. */
    TA_MARBUTA("t h"),
}

/**
 * Boils one frame of the model's output (a score for each of its few hundred symbols) down
 * to a row the aligner reads: the chance of a pause, then the chance of each [Sound].
 */
class SoundColumns(vocab: List<String>) {

    private val blank = vocab.indexOf("<pad>").coerceAtLeast(0)
    private val ids: Array<IntArray> = Sound.entries.map { sound ->
        sound.heardAs.split(' ').map { vocab.indexOf(it) }.filter { it >= 0 }.toIntArray()
    }.toTypedArray()
    private val size = vocab.size

    /**
     * Reads the frame at [offset] in [logits] and writes its row at [outOffset] in [out].
     * Returns the chance (0..1) that the frame is a pause.
     */
    fun reduce(logits: FloatArray, offset: Int, out: FloatArray, outOffset: Int): Float {
        var max = Float.NEGATIVE_INFINITY
        for (v in 0 until size) if (logits[offset + v] > max) max = logits[offset + v]
        var sum = 0.0
        for (v in 0 until size) sum += exp((logits[offset + v] - max).toDouble())
        val all = max + ln(sum)
        val pause = (logits[offset + blank] - all).toFloat()
        out[outOffset] = pause
        for (s in ids.indices) {
            var p = 0.0
            for (id in ids[s]) p += exp(logits[offset + id] - all)
            out[outOffset + 1 + s] = if (p > 0.0) ln(p).toFloat() else UNHEARD
        }
        return exp(pause.toDouble()).toFloat()
    }

    companion object {
        /** Numbers per row: the pause, then one per sound. */
        val WIDTH = Sound.entries.size + 1

        /** The model's frames are 20 ms apart. */
        const val FRAME_MS = 20

        /** A frame is worth keeping when the model is less sure than this that it's a pause. */
        const val PAUSE = 0.95f

        private const val UNHEARD = -40f
    }
}
