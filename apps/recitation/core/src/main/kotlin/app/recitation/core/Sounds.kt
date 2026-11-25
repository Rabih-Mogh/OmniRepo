package app.recitation.core

import kotlin.math.exp
import kotlin.math.ln

/**
 * The sounds of recited Arabic that words are broken into, for saying when each word of a
 * recording is said. The speech model writes a recitation in the Quran phonetic script
 * (see [HeardScript]): [written] is how it writes the sound. A long vowel is written once
 * for every count it is held, after the short vowel it grows out of, so a long vowel takes
 * its short one's sign too. [also] are signs the sound may be heard under, counted for
 * half: a short vowel said long, and the vowel a joining alif takes after a pause, which
 * must not be taken from the end of the word before when the reciter runs on.
 */
enum class Sound(val written: String, val also: String = "") {
    A("َ۪", "ا"),
    AA("اَـ"),
    I("ِ۪", "ۦ"),
    II("ۦِ"),
    U("ُؙ", "ۥ"),
    UU("ۥُ"),

    /** The vowel a word written with a joining alif (ٱ) starts with after a pause: a hamza and its vowel. */
    LINK("ء", "َُِ"),
    B("ب"),
    T("ت"),
    THA("ث"),
    JIM("ج"),
    HHA("ح"),
    KHA("خ"),
    D("د"),
    DHAL("ذ"),
    R("ر"),
    Z("ز"),
    S("س"),
    SHIN("ش"),
    SAD("ص"),
    DAD("ض"),
    TAH("ط"),
    ZAH("ظ"),
    AYN("ع"),
    GHAYN("غ"),
    F("ف"),
    Q("ق"),
    K("ك"),
    L("ل"),

    /** A mim, and the mim a nun turns into before ب, hidden. */
    M("م۾"),

    /** A nun, said or hidden; a tanween's before ب is hidden as a mim. */
    N("نں۾"),
    H("ه"),
    W("و"),
    Y("ي"),
    HAMZA("ءٲ"),

    /** The closed ta (ة): "t" when the reciter carries on, "h" at a stop. */
    TA_MARBUTA("ته"),
}

/**
 * Boils one frame of the model's output (a score for "nothing here" and for each symbol
 * of the phonetic script) down to a row the aligner reads: the chance of a pause, then
 * the chance of each [Sound]. The echo of a qalqalah and the sign of a sakt are no sound
 * of a word's own: they count with the pause.
 */
class SoundColumns {

    private val full: Array<IntArray> = Sound.entries.map { sound -> sound.written.map { HeardScript.ALPHABET.indexOf(it) + 1 }.toIntArray() }.toTypedArray()
    private val half: Array<IntArray> = Sound.entries.map { sound -> sound.also.map { HeardScript.ALPHABET.indexOf(it) + 1 }.toIntArray() }.toTypedArray()
    private val silent: IntArray = intArrayOf(0) + SILENT.map { HeardScript.ALPHABET.indexOf(it) + 1 }

    init {
        require(full.all { ids -> ids.all { it > 0 } } && half.all { ids -> ids.all { it > 0 } }) { "a sound is written with a sign the model doesn't have" }
    }

    /**
     * Reads the frame at [offset] in [scores] and writes its row at [outOffset] in [out].
     * Returns the chance (0..1) that the frame is a pause.
     */
    fun reduce(scores: FloatArray, offset: Int, out: FloatArray, outOffset: Int): Float {
        var max = Float.NEGATIVE_INFINITY
        for (v in 0 until SYMBOLS) if (scores[offset + v] > max) max = scores[offset + v]
        var sum = 0.0
        for (v in 0 until SYMBOLS) sum += exp((scores[offset + v] - max).toDouble())
        val all = max + ln(sum)
        var quiet = 0.0
        for (id in silent) quiet += exp(scores[offset + id] - all)
        out[outOffset] = if (quiet > 0.0) ln(quiet).toFloat() else UNHEARD
        for (s in full.indices) {
            var p = 0.0
            for (id in full[s]) p += exp(scores[offset + id] - all)
            for (id in half[s]) p += ALSO * exp(scores[offset + id] - all)
            out[outOffset + 1 + s] = if (p > 1e-17) ln(p).toFloat() else UNHEARD
        }
        return quiet.toFloat()
    }

    companion object {
        /** Numbers per row: the pause, then one per sound. */
        val WIDTH = Sound.entries.size + 1

        /** Scores per frame of the model's output: "nothing here", then one per symbol of the script. */
        const val SYMBOLS = HeardScript.ALPHABET.length + 1

        /** The model's frames are 20 ms apart. */
        const val FRAME_MS = 20

        /** A frame is worth keeping when the model is less sure than this that it's a pause. */
        const val PAUSE = 0.95f

        /** What a sign a sound is only also heard under counts for. */
        private const val ALSO = 0.5

        /** The echo of a qalqalah, and the sign of a sakt. */
        private const val SILENT = "ڇۜ"

        private const val UNHEARD = -40f
    }
}
