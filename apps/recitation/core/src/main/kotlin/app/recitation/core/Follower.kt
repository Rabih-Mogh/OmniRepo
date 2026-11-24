package app.recitation.core

import kotlin.math.abs

/**
 * Follows a reader reciting a surah to the phone, verse after verse, without being told
 * which verse they are at. The sound is heard a stretch at a time (the stretches end where
 * the reader falls silent), and what was heard is kept in one run from the start. The last
 * few words heard say where in the surah the reader is: looked for near where they were
 * first, then anywhere, so a reader may begin at any verse, go back, or skip ahead. When
 * the reader is heard to have gone on into the next verse, the one before is gone over the
 * way a verse recited by itself is (see [Corrections]).
 *
 * A verse taken up in its middle is followed but not gone over: its beginning wasn't heard.
 * [opening] is the basmala a reader says before the first verse of a surah it isn't a verse
 * of; it is followed so that it isn't taken for the first verse's words, and isn't gone over.
 */
class Follower(private val verses: List<List<WordForms>>, opening: List<WordForms>? = null, private val tempo: MutableList<Float>? = null) {
    /** A verse the reader recited to its end: which, counted from 0, and how each of its words went. */
    class Finished(val verse: Int, val words: List<WordRecited>)

    /** The verse being recited, counted from 0; -1 before that is known, and during the opening. */
    var verse = -1
        private set

    /** The word of it the reader has reached, counted from 0; -1 with [verse]. */
    var word = -1
        private set

    // What is followed, in order: the opening if there is one, then the verses, each as it
    // sounds said in one breath; and all of it as one run, to look for a place in.
    private val lead = if (opening != null) 1 else 0
    private val parts: List<Expected> = (listOfNotNull(opening) + verses).map { Expected.breath(it, 0, it.size) }
    private val starts = IntArray(parts.size + 1).also { for (i in parts.indices) it[i + 1] = it[i] + parts[i].script.length }
    private val whole = parts.joinToString("") { it.script }

    // What has been heard for good, from the start of the recitation.
    private val symbols = StringBuilder()
    private var sureness = FloatArray(0)
    private var began = FloatArray(0) // when each sound began, in seconds
    private var ended = FloatArray(0) // and when it ended: where the next began, or the stretch's own end
    private var silences = FloatArray(0)

    // Which part the reader was last heard in, how far into it, and where in what was
    // heard it began; -1 where that isn't known.
    private var part = -1
    private var into = 0
    private var begin = -1

    /**
     * Takes in a stretch of sound that has been heard for good: [heard], which began [at]
     * seconds into the recitation, with the [silences] found in it (see [Silences.of]).
     */
    fun add(heard: HeardScript, at: Float, silences: FloatArray) {
        symbols.append(heard.symbols)
        sureness += heard.sureness
        began += FloatArray(heard.symbols.length) { heard.times[it] + at }
        ended += FloatArray(heard.symbols.length) { heard.times[it + 1] + at }
        val moved = FloatArray(silences.size) { silences[it] + at }
        // A stretch ends in the middle of a silence, and the next begins with the rest of it: one silence.
        this.silences = if (moved.isNotEmpty() && this.silences.isNotEmpty() && moved[0] - this.silences.last() < JOINED) {
            this.silences.copyOf(this.silences.size - 1) + moved.copyOfRange(1, moved.size)
        } else {
            this.silences + moved
        }
    }

    /**
     * Looks at where the reader is, and gives the verses they have finished since the last
     * look. [pending] is what the sound after the last stretch taken in sounds like so far:
     * it says where the reader is now, but no verse is gone over on its word, as it may be
     * heard differently once all of it is in. [last] is for when the reader has stopped: the
     * verse they were in is gone over too, if they got to its last word.
     */
    fun look(pending: HeardScript? = null, last: Boolean = false): List<Finished> {
        val finished = ArrayList<Finished>()
        val heard = symbols.toString()
        val now = locate(heard)
        if (now != null) {
            val (to, at) = now
            when {
                // Taken up for the first time, or somewhere else in the surah.
                part < 0 || to < part || to > part + AT_ONCE -> {
                    part = to
                    begin = beginOf(to, at, heard)
                }
                // On into a later verse: the ones before it are finished, once it is clear where it began.
                to > part -> if (at >= BEYOND || last) {
                    val next = beginOf(to, at, heard)
                    if (next >= 0 && begin in 0 until next) finished += goneOver(part, to, begin, next, heard)
                    part = to
                    begin = next
                }
                // Back at the start of the verse they were well into: they are reciting it again.
                at < into - AGAIN && at < AGAIN -> begin = beginOf(to, at, heard)
            }
            into = at
        }
        if (last && part >= 0 && begin >= 0 && into > parts[part].spans[2 * (parts[part].words - 1)]) {
            finished += goneOver(part, part + 1, begin, heard.length, heard)
            part = -1
            begin = -1
        }
        // Where the reader is now: what is still being heard counts for that.
        val place = if (pending == null || pending.symbols.isEmpty()) now else locate(heard + pending.symbols)
        if (place != null) {
            val (to, at) = place
            verse = to - lead
            word = if (verse < 0) -1 else (0 until parts[to].words).firstOrNull { at <= parts[to].spans[2 * it + 1] } ?: (parts[to].words - 1)
            if (verse < 0) verse = -1
        }
        return finished
    }

    /** The part the last sounds of [heard] belong to, and how far into it they end; null if they can't be placed. */
    private fun locate(heard: String): Pair<Int, Int>? {
        val tail = heard.takeLast(TAIL)
        if (tail.length < LEAST) return null
        // Near where the reader was, first: there the words of a refrain are the ones meant.
        val near = if (part < 0) null else {
            val from = starts[part]
            Edits.within(whole, tail, from, starts[minOf(part + AT_ONCE + 1, parts.size)], after = from + into)?.takeIf { it.cost <= NEAR_FIT * tail.length }
        }
        val found = near ?: Edits.within(whole, tail)?.takeIf { tail.length >= LEAST_ANYWHERE && it.cost <= ANYWHERE_FIT * tail.length } ?: return null
        val to = (0 until parts.size).first { found.until <= starts[it + 1] }
        return to to found.until - starts[to]
    }

    /**
     * Where in [heard] the part [to] began, given that [heard] ends [at] sounds into it;
     * -1 if its beginning can't be made out: the reader took it up in its middle.
     */
    private fun beginOf(to: Int, at: Int, heard: String): Int {
        val said = parts[to].script.take(at)
        if (said.isEmpty()) return heard.length
        val lately = heard.takeLast(2 * at + 12)
        val took = Edits.reach(lately.reversed(), said.reversed())
        if (Edits.distance(said, lately.takeLast(took)) > BEGIN_FIT * said.length + 1) return -1
        return atSilence(heard.length - took)
    }

    /** [at], or where the reader fell silent if that was within a sound or two of it: a verse ends on a breath. */
    private fun atSilence(at: Int): Int = Silences.among(times(0, began.size), silences).minByOrNull { abs(it - at) }?.takeIf { abs(it - at) <= NEAR } ?: at

    /** When the sounds [from] until [until] of what was heard began, and when the last of them ended (see [HeardScript.times]). */
    private fun times(from: Int, until: Int): FloatArray = if (until <= from) FloatArray(0) else began.copyOfRange(from, until) + ended[until - 1]

    /** Goes over the parts [from] until [until], which were heard from [begin] until [end] of [heard]. */
    private fun goneOver(from: Int, until: Int, begin: Int, end: Int, heard: String): List<Finished> {
        // Several at once (short verses, said between two looks): each ends where the next one's sounds begin.
        val ends = IntArray(until - from) { end }
        if (until - from > 1) {
            val expected = (from until until).joinToString("") { parts[it].script }
            val changes = Edits.between(expected, heard.substring(begin, end))
            var length = 0
            for (k in 0 until until - from - 1) {
                length += parts[from + k].script.length
                val c = changes.first { length >= it.from && length <= it.until }
                ends[k] = atSilence(begin + when (c.kind) {
                    Edits.Kind.SAME, Edits.Kind.REPLACED -> c.toFrom + minOf(length - c.from, c.toUntil - c.toFrom)
                    Edits.Kind.ADDED -> c.toUntil
                    Edits.Kind.LEFT_OUT -> c.toFrom
                }).coerceIn(begin, end)
            }
        }
        val out = ArrayList<Finished>()
        var a = begin
        for (k in 0 until until - from) {
            val b = maxOf(a, ends[k])
            val v = from + k - lead
            if (v >= 0 && b - a >= 2) {
                val script = HeardScript(heard.substring(a, b), sureness.copyOfRange(a, b), times(a, b))
                val recited = Expected.recited(verses[v], script.symbols, silences = Silences.among(script.times, silences))
                out += Finished(v, Corrections.of(recited, script, tempo = tempo))
            }
            a = b
        }
        return out
    }

    private companion object {
        /** How many of the last sounds heard are gone by to find the reader's place: some four or five words. */
        const val TAIL = 40

        /** The fewest sounds a place is looked for with, near where the reader was and anywhere in the surah. */
        const val LEAST = 8
        const val LEAST_ANYWHERE = 16

        /** How much of what was heard may differ from the surah's words for a place to be taken, near and anywhere. */
        const val NEAR_FIT = 0.34f
        const val ANYWHERE_FIT = 0.25f

        /** How much of a verse's beginning may differ from what was heard for it to count as heard from its beginning. */
        const val BEGIN_FIT = 0.34f

        /** How many verses on from the one the reader was in they may be found in and still be taken to have recited on. */
        const val AT_ONCE = 3

        /** How many sounds into the next verse the reader is heard to be before the one before is gone over. */
        const val BEYOND = 5

        /** How many sounds back, and how near the verse's start, the reader is found for them to be reciting the verse again. */
        const val AGAIN = 24

        /** How far from a silence, in sounds, a verse's end may be made out and still be taken to be there. */
        const val NEAR = 3

        /** How close, in seconds, the end of one silence and the start of the next are for them to be one. */
        const val JOINED = 0.05f
    }
}
