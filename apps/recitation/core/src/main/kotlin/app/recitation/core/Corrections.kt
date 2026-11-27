package app.recitation.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What can be wrong with a word as it was recited. */
enum class Fault {
    /** A madd held for fewer counts than it is due. */
    MADD_SHORT,

    /** A madd held for more counts than it is due. */
    MADD_LONG,

    /** A madd not lengthened at all. */
    MADD_MISSING,

    /** A vowel lengthened where there is no madd. */
    MADD_ADDED,

    /** The same kind of madd held for different lengths through the recitation. */
    MADD_UNEVEN,

    /** The nasal sound of a doubled nun or mim cut short. */
    GHUNNAH_SHORT,

    /** A nun that should be hidden (ikhfa) said clearly. */
    IKHFA_CLEAR,

    /** A hidden nun whose nasal sound was cut short. */
    IKHFA_SHORT,

    /** A nun before ب not turned into a hidden mim. */
    IQLAB_MISSED,

    /** A nun that should merge into the next letter said clearly. */
    IDGHAM_MISSED,

    /** A nun merged with ghunnah, but the nasal sound left out. */
    IDGHAM_GHUNNAH_SHORT,

    /** A doubled letter said once. */
    SHADDA_MISSING,

    /** No echo on a letter of qalqalah. */
    QALQALAH_MISSING,

    /** Another letter said. */
    LETTER,

    /** Another vowel said. */
    VOWEL,

    /** A sound left out. */
    DROPPED,

    /** A sound added. */
    ADDED,

    /** The whole word not heard. */
    WORD_MISSING,
}

/**
 * One correction. [clear] is a clear mistake (لحن جلي: the wording itself is changed);
 * otherwise it is a fine point of performance (لحن خفي). [wanted] and [heard] are in the
 * phonetic script, or a letter; for a madd, [madd] names it and [wantedCounts] and
 * [heardCounts] give its length; for an uneven madd [lengths] are the lengths heard.
 */
class Correction(
    val fault: Fault,
    val clear: Boolean,
    val wanted: String = "",
    val heard: String = "",
    val madd: Madd? = null,
    val wantedCounts: Int = 0,
    val heardCounts: Int = 0,
    val lengths: List<Int> = emptyList(),
) {
    /**
     * The rules of recitation the correction is about, as the page of a surah marks them
     * on a word's letters: the letters to point at. Empty for a mistake in the wording
     * itself (another letter or vowel, a sound left out or added), which no rule covers.
     */
    val rules: Set<Rule>
        get() = when (fault) {
            Fault.MADD_SHORT, Fault.MADD_LONG, Fault.MADD_MISSING, Fault.MADD_UNEVEN -> when (madd) {
                Madd.MUNFASIL -> setOf(Rule.MADD_MUNFASIL, Rule.MADD_SILAH_KUBRA)
                Madd.MUTTASIL, Madd.MUTTASIL_AT_STOP -> setOf(Rule.MADD_MUTTASIL)
                Madd.LAZIM -> setOf(Rule.MADD_LAZIM)
                Madd.ARID -> setOf(Rule.MADD_ARID)
                Madd.LIN -> setOf(Rule.MADD_LIN)
                Madd.NATURAL, null -> setOf(Rule.MADD_TABII, Rule.MADD_SILAH_SUGHRA, Rule.MADD_IWAD)
            }
            Fault.MADD_ADDED -> setOf(Rule.MADD_TABII)
            Fault.GHUNNAH_SHORT -> setOf(Rule.GHUNNAH)
            Fault.IKHFA_CLEAR, Fault.IKHFA_SHORT -> setOf(Rule.IKHFA, Rule.IKHFA_SHAFAWI)
            Fault.IQLAB_MISSED -> setOf(Rule.IQLAB)
            Fault.IDGHAM_GHUNNAH_SHORT -> setOf(Rule.IDGHAM_GHUNNAH, Rule.IDGHAM_SHAFAWI)
            // Merged through the nose into ي ن م و, whole into ل ر.
            Fault.IDGHAM_MISSED -> if (wanted.any { it in "لر" }) setOf(Rule.IDGHAM_BILA_GHUNNAH) else setOf(Rule.IDGHAM_GHUNNAH, Rule.IDGHAM_SHAFAWI)
            Fault.QALQALAH_MISSING -> setOf(Rule.QALQALAH)
            Fault.SHADDA_MISSING, Fault.LETTER, Fault.VOWEL, Fault.DROPPED, Fault.ADDED, Fault.WORD_MISSING -> emptySet()
        }

    /** The family those rules belong to, which gives the correction its colour: the one the rule has on the page and in its legend. */
    val family: Family? get() = rules.firstOrNull()?.family
}

/** A madd other than the natural one, as it was heard: for showing its length beside the word. */
class MaddHeard(val rule: Madd, val counts: Int)

/**
 * One thing listened for, as it was heard. For a madd, [heard] is the counts it was held
 * for and [wanted] the counts it is due. For a nasal sound both are lengths as the
 * phonetic script writes them, which are not counts: only how [heard] stands to [wanted]
 * means anything. For what is there or isn't (an echo, a nun turned or merged), [heard] is
 * 0 when it wasn't. [fault] is what there is to correct about it, null if nothing: then
 * [heard] is [wanted], since a length the model wasn't sure of is not held against the
 * reciter.
 */
class Measure(val awaited: Awaited, val heard: Int, val wanted: Int, val fault: Fault?)

/**
 * A word as it was recited: which word of the verse ([index]), whether the reciter stopped
 * on it mid-verse ([stopped]), what to correct in it, and how long its madds were held.
 * [wanted] is how it should have sounded, [listened] what was listened for in it, and
 * [measures] how each of those was heard. A word that wasn't said has nothing listened
 * for: it is told as not said, and nothing else.
 */
class WordRecited(
    val index: Int,
    val stopped: Boolean,
    val corrections: List<Correction>,
    val madds: List<MaddHeard>,
    val wanted: String = "",
    val listened: List<Awaited> = emptyList(),
    val measures: List<Measure> = emptyList(),
) {
    val sound: Boolean get() = corrections.isEmpty()

    /** Whether the word wasn't said at all. */
    val unsaid: Boolean get() = corrections.any { it.fault == Fault.WORD_MISSING }
}

/**
 * Lines a recitation as heard up with the recitation expected and names every difference.
 *
 * A correction about sounds the model was not sure of is left out: better to say nothing
 * than to correct a reciter who was right. A clear mistake asks for more sureness
 * ([sureOfClear]) than a fine point ([sure]).
 *
 * A word that wasn't said is told as that and nothing more. No rule is judged in it: a
 * madd not held, a nasal sound cut short, an echo left off are things said wrongly, and it
 * wasn't said. The words that were said are lined up with what was heard without it, so
 * that none of them is corrected for sounds that were the unsaid word's.
 */
object Corrections {
    private const val LONG = "اۥۦ"
    private const val VOWELS = "َُِ"
    private const val LENGTH_SIGNS = "ںڇ۾"

    /** Sounds at the start of a breath where another letter or a dropped sound isn't judged: often clipped by the recording, not the reciter. */
    private const val ONSET = 3

    // A long madd is written with four symbols at most, however long it was held. When four or
    // five are written its length is measured by the clock against the reciter's own natural
    // madd (two counts): 8.3 counts or more is a six, 7 or more a five.
    private const val SIX = 8.3f
    private const val FIVE = 7.0f

    /** How many words either side of a word are looked at with it, to tell whether it was said. */
    private const val AROUND = 3

    /** The most counts a sound is shown as having been held for: a madd is written with no more. */
    private const val MOST_SHOWN = 8

    /** How many sounds either side of where a word wasn't said aren't judged sound by sound: how a word ends and begins goes by its neighbour. */
    private const val GAP = 2

    /**
     * [tempo] is kept by the caller through a session: the natural madds heard so far, in
     * seconds, for a verse with too few of its own to set the pace by.
     */
    fun of(expected: Expected, heard: HeardScript, sure: Float = 0.3f, sureOfClear: Float = 0.7f, tempo: MutableList<Float>? = null): List<WordRecited> {
        val ref = expected.script
        val got = heard.symbols
        val at = boundaries(ref, got, Edits.between(ref, got))
        val unsaid = unsaid(expected, got, at)
        if (unsaid.none { it }) return judged(expected, heard, sure, sureOfClear, tempo)
        // The words that were said, gone over without the others; and the others in their places.
        val left = expected.without(unsaid, got)
        val said = if (left.words == 0) emptyList() else judged(left, heard, sure, sureOfClear, tempo)
        val ends = left.ends.toSet()
        var j = 0
        return List(expected.words) { k ->
            val a = expected.spans[2 * k]
            val z = expected.spans[2 * k + 1]
            if (unsaid[k]) {
                WordRecited(expected.index[k], false, listOf(Correction(Fault.WORD_MISSING, clear = true, wanted = ref.substring(a, z), heard = got.substring(at[a], at[z]))), emptyList(), ref.substring(a, z))
            } else {
                // Stopped on, if a breath ended on it and it isn't the last word there is.
                val word = said[j]
                WordRecited(word.index, j++ in ends && k != expected.words - 1, word.corrections, word.madds, word.wanted, word.listened, word.measures)
            }
        }
    }

    /**
     * Which words of [expected] weren't said, going by what was heard ([got], lined up with
     * the expected script by [at]). One whose sounds are all but all missing is one. So is
     * one that what was heard fits better without: nearer, by two thirds of the word's
     * sounds or more, to the recitation with the word left out than with it in. Lined up
     * sound by sound, a word passed over can take sounds of its neighbours for its own (a
     * vowel here, a ل there), and would then look said, and said badly. The clearest is
     * taken out first and the rest looked at again without it: beside a word not said, one
     * that was said would else look as if it could be done without too.
     */
    private fun unsaid(expected: Expected, got: String, at: IntArray): BooleanArray {
        val ref = expected.script
        val words = expected.words
        fun from(k: Int) = expected.spans[2 * k]
        fun until(k: Int) = expected.spans[2 * k + 1]
        val out = BooleanArray(words) { until(it) > from(it) && at[until(it)] - at[from(it)] <= max(0, (until(it) - from(it)) / 4) }
        var whole = -1
        while (true) {
            val said = (0 until words).filter { !out[it] }
            var best = -1
            var gained = 0
            for ((p, k) in said.withIndex()) {
                val length = until(k) - from(k)
                if (length == 0) continue
                // First with only the few words either side of it that count as said: most words are plainly there.
                val first = said[max(0, p - AROUND)]
                val last = said[min(said.size - 1, p + AROUND)]
                val there = got.substring(at[from(first)], at[until(last)])
                fun about(with: Boolean) = (first..last).filter { !out[it] && (with || it != k) }.joinToString("") { ref.substring(from(it), until(it)) }
                val with = Edits.distance(about(true), there)
                if (with * 4 < length || (with - Edits.distance(about(false), there)) * 4 < length) continue
                // Then the whole recitation, with the word and without it.
                if (whole < 0) whole = Edits.distance(expected.without(out, got).script, got)
                val gain = whole - Edits.distance(expected.without(BooleanArray(words) { out[it] || it == k }, got).script, got)
                if (gain * 3 < length * 2) continue
                // Of two, the one more of whose sounds are accounted for by its not having been said.
                val before = if (best < 0) 1 else until(best) - from(best)
                if (best < 0 || gain * before > gained * length) { best = k; gained = gain }
            }
            if (best < 0) return out
            out[best] = true
            whole -= gained
        }
    }

    /** The corrections of [expected] as it stands: every word of it taken to have been said, or passed over whole. */
    private fun judged(expected: Expected, heard: HeardScript, sure: Float, sureOfClear: Float, tempo: MutableList<Float>?): List<WordRecited> {
        val ref = expected.script
        val got = heard.symbols
        val changes = Edits.between(ref, got)
        val at = boundaries(ref, got, changes)
        val words = expected.words
        val notes = List(words) { ArrayList<Correction>() }
        val madds = List(words) { ArrayList<MaddHeard>() }
        val listened = List(words) { ArrayList<Awaited>() }
        val measures = List(words) { ArrayList<Measure>() }
        val covered = BooleanArray(ref.length)
        // A word whose sounds are all but all missing was passed over: nothing is listened for in it.
        val passed = BooleanArray(words) { k ->
            val a = expected.spans[2 * k]
            val z = expected.spans[2 * k + 1]
            z > a && at[z] - at[a] <= max(0, (z - a) / 4)
        }

        fun unsure(from: Int, until: Int, threshold: Float = sure): Boolean {
            val a = max(0, from - 1)
            val b = min(heard.sureness.size, until + 1)
            if (b <= a) return false
            var least = Float.MAX_VALUE
            for (i in a until b) least = min(least, heard.sureness[i])
            return least < threshold
        }
        fun slice(from: Int, until: Int): String = got.substring(max(0, from).coerceAtMost(got.length), until.coerceIn(max(0, from).coerceAtMost(got.length), got.length))
        // The heard script has a nun the expected one hasn't: the nun was not merged.
        fun moreNun(u: Awaited, hs: Int, he: Int): Boolean =
            slice(hs - 2, he).count { it == 'ن' } > ref.substring(max(0, u.from - 2), u.until).count { it == 'ن' }

        val unit = pace(expected, heard, at, tempo)
        val seen = LinkedHashMap<Madd, ArrayList<Pair<Int, Int>>>() // by kind of madd: the word and the symbols written
        for (u in expected.awaited) {
            for (x in u.from until u.until) covered[x] = true
            if (passed[u.word]) continue
            listened[u.word] += u
            val hs = at[u.from]
            val he = at[u.until]
            fun add(correction: Correction) { if (!unsure(hs, he)) notes[u.word] += correction }
            // How it was heard: the counts it was held for and is due, or whether it was there.
            val before = notes[u.word].size
            var held = 0
            var due = max(1, u.counts)
            when (u.kind) {
                Listened.MADD -> {
                    val rule = u.madd!!
                    val run = run(got, max(0, hs - 1), min(got.length, he + 1), u.symbol)
                    var n = run[0]
                    if (rule == Madd.LIN && n > 0) n++
                    val symbols = n
                    var shown = if (n == 0) 0 else if (n <= 3) 2 else if (n <= 5) 4 else 6
                    if (unit != null && (n == 4 || n == 5)) {
                        val counts = seconds(got, heard.times, run[1], run[2]) / unit
                        n = if (counts >= SIX) 6 else if (counts >= FIVE) 5 else 4
                        shown = if (counts >= SIX) 6 else 4
                    }
                    val near = rule.allowed.sorted().minBy { abs(it - n) }
                    held = n
                    due = near
                    seen.getOrPut(rule) { ArrayList() } += u.word to symbols
                    if (shown != 0 && rule != Madd.NATURAL) madds[u.word] += MaddHeard(rule, shown)
                    if (n == 0 && rule != Madd.LIN) {
                        due = u.counts
                        add(Correction(Fault.MADD_MISSING, clear = true, madd = rule, wantedCounts = u.counts))
                    } else if (abs(n - near) > 1 && !unsure(hs, he)) {
                        due = if (u.counts in rule.allowed) u.counts else near
                        notes[u.word] += Correction(if (n > near) Fault.MADD_LONG else Fault.MADD_SHORT, clear = false, madd = rule, wantedCounts = due, heardCounts = n)
                    }
                }
                Listened.QALQALAH -> if ('ڇ' !in slice(hs - 1, he + 1)) {
                    add(Correction(Fault.QALQALAH_MISSING, clear = false, wanted = if (u.from > 0) ref[u.from - 1].toString() else ""))
                }
                Listened.IKHFA -> {
                    val n = run(got, hs, max(he, hs + 1), 'ں')[0]
                    held = n
                    if (n == 0 && 'ن' in slice(hs, he + 1)) add(Correction(Fault.IKHFA_CLEAR, clear = false))
                    else if (n != 0 && n < u.counts - 1) add(Correction(Fault.IKHFA_SHORT, clear = false))
                }
                Listened.IQLAB -> if ('۾' !in slice(hs - 1, he + 1)) add(Correction(Fault.IQLAB_MISSED, clear = false))
                Listened.GHUNNAH, Listened.IDGHAM_GHUNNAH -> {
                    val n = run(got, max(0, hs - 1), min(got.length, he + 1), u.symbol)[0]
                    held = n
                    if (u.kind == Listened.IDGHAM_GHUNNAH && u.symbol != 'ن' && moreNun(u, hs, he)) {
                        add(Correction(Fault.IDGHAM_MISSED, clear = false, wanted = u.symbol.toString()))
                    } else if (n < u.counts) {
                        add(Correction(if (u.kind == Listened.GHUNNAH) Fault.GHUNNAH_SHORT else Fault.IDGHAM_GHUNNAH_SHORT, clear = false, wanted = u.symbol.toString()))
                    }
                }
                Listened.SHADDA, Listened.IDGHAM -> {
                    val n = run(got, max(0, hs - 1), min(got.length, he + 1), u.symbol)[0]
                    if (u.kind == Listened.IDGHAM && moreNun(u, hs, he)) add(Correction(Fault.IDGHAM_MISSED, clear = false, wanted = u.symbol.toString()))
                    else if (n == 1) add(Correction(Fault.SHADDA_MISSING, clear = true, wanted = u.symbol.toString()))
                }
            }
            val fault = notes[u.word].getOrNull(before)?.fault
            // A nun said clearly where it should have gone into the next letter: nothing of the rule was heard.
            if (fault == Fault.IDGHAM_MISSED || fault == Fault.IKHFA_CLEAR) held = 0
            measures[u.word] += Measure(u, if (fault == null) due else min(held, MOST_SHOWN), due, fault)
        }

        // Whatever the rules didn't explain: other letters, other vowels, sounds dropped or added. Clear mistakes.
        for (c in changes) {
            if (c.kind == Edits.Kind.SAME) continue
            val exp = ref.substring(c.from, c.until)
            val was = got.substring(c.toFrom, c.toUntil)
            if (c.until > c.from && (c.from until c.until).all { covered[it] } && was.all { it in exp || it in LONG || it in "ڇں۾نم" }) continue // a length or a ghunnah: judged above
            if (c.kind == Edits.Kind.ADDED && expected.awaited.any { it.kind == Listened.MADD && it.from <= c.from && c.from <= it.until && was.all { ch -> ch == it.symbol } }) continue
            var w = words - 1
            for (k in 0 until words) if (expected.spans[2 * k] <= c.from && c.from < expected.spans[2 * k + 1]) { w = k; break }
            if (c.from < ONSET) continue
            if (expected.gaps.any { c.from <= it + GAP && c.until >= it - GAP }) continue
            val wantedCore = withoutLength(exp)
            val heardCore = withoutLength(was)
            if (wantedCore == heardCore) continue // only lengths differ
            if (unsure(c.toFrom, c.toUntil, sureOfClear)) continue
            when (c.kind) {
                Edits.Kind.REPLACED ->
                    if (wantedCore.length == 1 && heardCore.length == 1 && wantedCore[0] in VOWELS && heardCore[0] in VOWELS) {
                        notes[w] += Correction(Fault.VOWEL, clear = true, wanted = wantedCore, heard = heardCore)
                    } else if (!unsure(c.toFrom, c.toUntil)) {
                        notes[w] += Correction(Fault.LETTER, clear = true, wanted = exp, heard = was)
                    }
                Edits.Kind.LEFT_OUT -> {
                    if (exp.length == 1 && (exp[0] in LONG || exp[0] in VOWELS)) continue // one vowel sign: within the model's noise
                    if (!unsure(c.toFrom, c.toUntil)) notes[w] += Correction(Fault.DROPPED, clear = true, wanted = exp)
                }
                Edits.Kind.ADDED -> {
                    if (c.from == 0 || c.from == ref.length) continue // before the first word or after the last: not this passage
                    if (was.all { it in LONG }) {
                        if (was.length >= 2 && !unsure(c.toFrom, c.toUntil)) notes[w] += Correction(Fault.MADD_ADDED, clear = false, heard = was)
                        continue
                    }
                    if (was.length == 1 && was[0] in VOWELS) continue
                    if (!unsure(c.toFrom, c.toUntil)) notes[w] += Correction(Fault.ADDED, clear = true, heard = was)
                }
                Edits.Kind.SAME -> {}
            }
        }

        for (k in 0 until words) {
            if (!passed[k]) continue
            val a = expected.spans[2 * k]
            val z = expected.spans[2 * k + 1]
            notes[k].clear()
            measures[k].clear()
            notes[k] += Correction(Fault.WORD_MISSING, clear = true, wanted = ref.substring(a, z), heard = got.substring(at[a], at[z]))
        }

        // The same kind of madd keeps the same length through the recitation.
        for ((rule, places) in seen) {
            if (rule != Madd.MUNFASIL && rule != Madd.MUTTASIL && rule != Madd.ARID) continue
            if (places.size < 2) continue
            val lengths = places.map { it.second }.filter { it != 0 }
            if (lengths.isEmpty() || lengths.max() - lengths.min() < 2) continue
            val usual = Math.rint(lengths.sum().toDouble() / lengths.size).toInt()
            val word = places.maxBy { abs(it.second - usual) }.first
            notes[word] += Correction(Fault.MADD_UNEVEN, clear = false, madd = rule, lengths = lengths)
        }

        val stops = expected.ends.dropLast(1).toSet()
        return List(words) { k -> WordRecited(expected.index[k], k in stops, notes[k], madds[k], ref.substring(expected.spans[2 * k], expected.spans[2 * k + 1]), listened[k], measures[k]) }
    }

    /** For each place in the expected script, the place in the heard one that answers to it. */
    private fun boundaries(ref: String, got: String, changes: List<Edits.Change>): IntArray {
        val at = IntArray(ref.length + 1)
        for (c in changes) {
            for (k in c.from..c.until) {
                at[k] = c.toFrom + if (c.until > c.from) Math.rint((k - c.from).toDouble() * (c.toUntil - c.toFrom) / (c.until - c.from)).toInt() else 0
            }
        }
        at[ref.length] = got.length
        return at
    }

    /** The run of [symbol] found in [got] between [from] and [until], grown over its neighbours: how long it is, where it starts and ends. */
    private fun run(got: String, from: Int, until: Int, symbol: Char): IntArray {
        var k = -1
        for (x in from until min(until, got.length)) if (got[x] == symbol) { k = x; break }
        if (k < 0) return intArrayOf(0, from, from)
        var s = k
        var e = k
        while (s > 0 && got[s - 1] == symbol) s--
        while (e < got.length && got[e] == symbol) e++
        return intArrayOf(e - s, s, e)
    }

    /** How long, in seconds, the long vowel heard from [from] until [until] was held, its short vowel included, up to the next sound. */
    private fun seconds(got: String, times: FloatArray, from: Int, until: Int): Float {
        val a = if (from > 0 && got[from - 1] in VOWELS) times[from - 1] else times[from]
        return max(0f, times[min(until, times.size - 1)] - a)
    }

    /**
     * Seconds to a count, at this reciter's pace: half the middle natural madd of the
     * recitation, leaving out those a breath ends on (which may be held longer); or of the
     * session's, when this one has fewer than two.
     */
    private fun pace(expected: Expected, heard: HeardScript, at: IntArray, tempo: MutableList<Float>?): Float? {
        val got = heard.symbols
        if (heard.times.size != got.length + 1) return null
        val ends = expected.ends.toSet()
        val natural = ArrayList<Float>()
        for (u in expected.awaited) {
            if (u.kind != Listened.MADD || u.madd != Madd.NATURAL || u.word in ends) continue
            val run = run(got, max(0, at[u.from] - 1), min(got.length, at[u.until] + 1), u.symbol)
            if (run[0] in 1..3) {
                val d = seconds(got, heard.times, run[1], run[2])
                if (d > 0.08f && d < 2f) natural += d
            }
        }
        if (tempo != null) {
            tempo += natural
            while (tempo.size > 40) tempo.removeAt(0)
        }
        val from = if (natural.size >= 2) natural else if (tempo != null && tempo.size >= 3) tempo.toList() else return null
        return from.sorted()[from.size / 2] / 2
    }

    /** A stretch of script without its lengths: runs of a symbol counted once, the signs of length taken out. */
    private fun withoutLength(s: String): String {
        val out = StringBuilder()
        for (c in s) if (out.isEmpty() || out.last() != c) out.append(c)
        return out.filter { it !in LONG && it !in LENGTH_SIGNS }.toString()
    }

    /** How sound a recitation was, 0..100: the share of its words with nothing to correct. */
    fun score(words: List<WordRecited>): Int = if (words.isEmpty()) 0 else (100f * words.count { it.sound } / words.size).roundToInt()
}
