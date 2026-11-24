package app.recitation.core

import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlin.math.abs

/*
 * Checking a learner's recitation against what the verse asks for.
 *
 * The tajweed model writes what it heard in the Quran phonetic script (see HeardScript).
 * What a correct recitation sounds like, in the same script, is worked out beforehand for
 * every word of the Quran (tools/make_tajweed_reference.py) and read here. The two are
 * lined up sound by sound and every difference is named the way a teacher would name it.
 *
 * The way of comparing is that of the rule engine published with the model ("imam.py",
 * HisnLabs Tajweed v2, Apache-2.0), written again here in Kotlin: the breaths, the units a
 * teacher listens for, the allowance of one count either way, and the measuring of long
 * madds by the clock are all its own.
 */

/** The kinds of madd, by the letter the reference file writes for each, with the counts a teacher accepts in Hafs. */
enum class Madd(val letter: Char, val allowed: Set<Int>) {
    NATURAL('n', setOf(2)),
    MUNFASIL('f', setOf(2, 4, 5)),
    MUTTASIL('t', setOf(4, 5, 6)),
    MUTTASIL_AT_STOP('T', setOf(4, 5, 6)),
    LAZIM('z', setOf(6)),
    ARID('r', setOf(2, 4, 6)),
    LIN('l', setOf(2, 4, 6)),
}

/** A long vowel of a word that falls under a rule of madd: where it is in the word's script. */
class MaddAt(val from: Int, val until: Int, val rule: Madd)

/**
 * One way a word sounds: its [script]; whether it ends in a nun or tanween that merges
 * into the next word ([merges]); and its madds.
 */
class Form(val script: String, val merges: Boolean, val madds: List<MaddAt>)

/**
 * A word of a verse as a correct recitation sounds, which depends on where the reciter
 * breathes: [inside] a breath, [first] in one, [last] in one (the form it takes at a
 * stop), or a breath of its own ([alone]).
 */
class WordForms(val inside: Form, val first: Form, val last: Form, val alone: Form)

/** Reads the reference file: the forms of every word, verse by verse. */
object TajweedReference {
    /** The verses of [surah], each as its words' forms, from the gzipped file at [gz]. Empty if the surah isn't there. */
    fun surah(gz: InputStream, surah: Int): List<List<WordForms>> {
        val mine = "$surah:"
        val out = ArrayList<List<WordForms>>()
        GZIPInputStream(gz).bufferedReader().useLines { lines ->
            var seen = false
            for (line in lines) {
                if (line.startsWith(mine)) {
                    seen = true
                    out += verse(line)
                } else if (seen) break
            }
        }
        return out
    }

    /** The verses of each of [surahs], by its number, read in one go through the file. */
    fun surahs(gz: InputStream, surahs: Set<Int>): Map<Int, List<List<WordForms>>> {
        val out = LinkedHashMap<Int, ArrayList<List<WordForms>>>()
        GZIPInputStream(gz).bufferedReader().useLines { lines ->
            for (line in lines) {
                val surah = line.substringBefore(':').toIntOrNull() ?: continue
                if (surah in surahs) out.getOrPut(surah) { ArrayList() } += verse(line)
            }
        }
        return out
    }

    /** One line of the file: "surah:verse", then each word after a tab. */
    fun verse(line: String): List<WordForms> = line.split('\t').drop(1).map(::word)

    private fun word(cell: String): WordForms {
        val parts = cell.split('|')
        val inside = form(parts[0])
        val first = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let(::form) ?: inside
        val last = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let(::form) ?: inside
        val alone = parts.getOrNull(3)?.takeIf { it.isNotEmpty() }?.let(::form) ?: last
        return WordForms(inside, first, last, alone)
    }

    private fun form(text: String): Form {
        val at = text.indexOf('@')
        val head = if (at < 0) text else text.substring(0, at)
        val merges = head.endsWith('~')
        val madds = if (at < 0) emptyList() else text.substring(at + 1).split(',').map { m ->
            val dash = m.indexOf('-')
            val rule = Madd.entries.first { it.letter == m.last() }
            MaddAt(m.substring(0, dash).toInt(), m.substring(dash + 1, m.length - 1).toInt(), rule)
        }
        return Form(if (merges) head.dropLast(1) else head, merges, madds)
    }
}

/** What a teacher listens for at one place of the expected script. */
enum class Listened { MADD, QALQALAH, IKHFA, IQLAB, GHUNNAH, IDGHAM_GHUNNAH, IDGHAM, SHADDA }

/** One thing listened for: [from] until [until] of the expected script, in [word] (counted within the breaths as recited). */
class Awaited(val from: Int, val until: Int, val kind: Listened, val word: Int, val counts: Int = 0, val madd: Madd? = null, val symbol: Char = ' ')

/**
 * What a correct recitation of some words sounds like, as they were actually delivered:
 * in one breath or several, each breath's last word in the form it takes at a stop.
 * A word gone back to after a stop is in it twice.
 *
 * [script] is the whole of it; word k of it is [spans]`[2k]` until [spans]`[2k + 1]`.
 * [index] says which word of the verse each is, and [ends] which of them end a breath.
 */
class Expected(
    val script: String,
    val spans: IntArray,
    val awaited: List<Awaited>,
    val index: IntArray,
    val ends: IntArray,
) {
    val words: Int get() = index.size

    /**
     * Which word a reciter has reached who has so far been heard to say [heard]: for
     * lighting the word up while they recite. -1 before the first sound.
     */
    fun reached(heard: String): Int {
        val at = Edits.reach(script, heard)
        if (at == 0) return -1
        for (k in 0 until words) if (at <= spans[2 * k + 1]) return index[k]
        return index[words - 1]
    }

    companion object {
        private const val LONG = "اۥۦ"
        private const val VOWELS = "َُِ"

        /** How far from a silence, in sounds, the end of a breath may be lined up and still be taken to end there. */
        private const val NEAR = 2

        /** The words [from] until [until] of [verse] said in one breath. */
        fun breath(verse: List<WordForms>, from: Int, until: Int): Expected {
            val count = until - from
            val forms = List(count) { k ->
                val word = verse[from + k]
                when {
                    count == 1 -> word.alone
                    k == 0 -> word.first
                    k == count - 1 -> word.last
                    else -> word.inside
                }
            }
            val script = StringBuilder()
            val spans = IntArray(2 * count)
            for ((k, form) in forms.withIndex()) {
                spans[2 * k] = script.length
                script.append(form.script)
                spans[2 * k + 1] = script.length
            }
            val text = script.toString()
            fun wordAt(i: Int): Int {
                for (k in 0 until count) if (spans[2 * k] <= i && i < spans[2 * k + 1]) return k
                return count - 1
            }
            val awaited = ArrayList<Awaited>()
            val ruled = HashSet<Int>()
            // The madds the phonetizer tagged, and every echo of a qalqalah.
            for ((k, form) in forms.withIndex()) {
                for (madd in form.madds) {
                    val s = spans[2 * k] + madd.from
                    val e = spans[2 * k] + madd.until
                    awaited += Awaited(s, e, Listened.MADD, k, counts = e - s + if (madd.rule == Madd.LIN) 1 else 0, madd = madd.rule, symbol = text[s])
                    for (x in s until e) ruled += x
                }
            }
            for (q in text.indices) if (text[q] == 'ڇ') { awaited += Awaited(q, q + 1, Listened.QALQALAH, wordAt(q), symbol = 'ڇ'); ruled += q }
            // The rest is read off the script itself: runs of one symbol.
            var i = 0
            while (i < text.length) {
                var j = i
                while (j < text.length && text[j] == text[i]) j++
                val c = text[i]
                val n = j - i
                val w = wordAt(i)
                if (i !in ruled) {
                    val before = if (i > 0) wordAt(i - 1) else w
                    when {
                        c == 'ں' -> awaited += Awaited(i, j, Listened.IKHFA, w, n, symbol = c)
                        c == '۾' -> awaited += Awaited(i, j, Listened.IQLAB, w, n, symbol = c)
                        // ننننَ / ممممِ: a nun or mim with a shadda, or a nun merged into one.
                        (c == 'ن' || c == 'م') && n >= 3 -> {
                            val merged = before != w && forms[before].merges
                            awaited += Awaited(i, j, if (merged) Listened.IDGHAM_GHUNNAH else Listened.GHUNNAH, if (merged) before else w, n, symbol = c)
                        }
                        // «مَن يَقُولُ» → مَيييَقُ: a nun merged into ي or و, with its ghunnah.
                        (c == 'ي' || c == 'و') && n >= 3 -> awaited += Awaited(i, j, Listened.IDGHAM_GHUNNAH, w, n, symbol = c)
                        // A doubled letter: a shadda, or a nun merged away without ghunnah.
                        c !in LONG && c !in VOWELS && n == 2 ->
                            if (i == spans[2 * w] && before != w && forms[before].merges) awaited += Awaited(i, j, Listened.IDGHAM, before, 2, symbol = c)
                            else awaited += Awaited(i, j, Listened.SHADDA, w, 2, symbol = c)
                    }
                }
                i = j
            }
            awaited.sortBy { it.from }
            return Expected(text, spans, awaited, IntArray(count) { from + it }, intArrayOf(count - 1))
        }

        /** Several breaths, in the order they were recited, as one. */
        fun joined(parts: List<Expected>): Expected {
            val script = StringBuilder()
            val spans = ArrayList<Int>()
            val awaited = ArrayList<Awaited>()
            val index = ArrayList<Int>()
            val ends = ArrayList<Int>()
            for (part in parts) {
                val o = script.length
                val w0 = index.size
                for (x in part.spans) spans += x + o
                for (u in part.awaited) awaited += Awaited(u.from + o, u.until + o, u.kind, u.word + w0, u.counts, u.madd, u.symbol)
                for (x in part.index) index += x
                script.append(part.script)
                ends += index.size - 1
            }
            return Expected(script.toString(), spans.toIntArray(), awaited, index.toIntArray(), ends.toIntArray())
        }

        /**
         * The verse as the reciter delivered it, going by what was [heard]: where they
         * stopped, so the word takes the form it has at a stop, and where they began again,
         * often a few words back. A breath is split wherever that brings the expected
         * script clearly closer to what was heard: by at least [margin] sounds.
         *
         * Many stops change less than that: a tanween read as an alif, the echo of a
         * qalqalah, a last vowel left off. So where the reciter was heard to fall silent
         * ([silences]: how many sounds had been heard at each, see [Silences.among]) one
         * sound is enough. Without a silence it is not: a nun not merged into the next
         * word is a mistake, and would pass for a stop.
         */
        fun recited(verse: List<WordForms>, heard: String, back: Int = 4, margin: Int = 3, silences: IntArray = IntArray(0)): Expected {
            val cache = HashMap<Long, Expected>()
            fun breathOf(a: Int, b: Int) = cache.getOrPut(a.toLong() shl 32 or b.toLong()) { breath(verse, a, b) }
            fun script(breaths: List<IntArray>): String = breaths.joinToString("") { breathOf(it[0], it[1]).script }
            // Whether breath [i] of [breaths] ends where a silence was heard, give or take a sound or two.
            fun endsInSilence(breaths: List<IntArray>, i: Int): Boolean {
                val end = script(breaths.subList(0, i + 1)).length
                for (c in Edits.between(script(breaths), heard)) {
                    if (end < c.from || end > c.until) continue
                    val at = when (c.kind) {
                        Edits.Kind.SAME, Edits.Kind.REPLACED -> c.toFrom + minOf(end - c.from, c.toUntil - c.toFrom)
                        Edits.Kind.ADDED -> c.toUntil
                        Edits.Kind.LEFT_OUT -> c.toFrom
                    }
                    return silences.any { abs(it - at) <= NEAR }
                }
                return false
            }
            class Tried(val cost: Int, val breaths: List<IntArray>, val split: Int)
            var breaths = listOf(intArrayOf(0, verse.size))
            var best = Edits.distance(script(breaths), heard)
            while (true) {
                val tried = ArrayList<Tried>()
                for ((i, breath) in breaths.withIndex()) {
                    val a = breath[0]
                    val b = breath[1]
                    for (k in a + 1 until b) {              // stop after word k - 1 ...
                        for (j in maxOf(a + 1, k - back)..k) { // ... and begin again from word j (before k: words said again)
                            val split = breaths.subList(0, i) + listOf(intArrayOf(a, k), intArrayOf(j, b)) + breaths.subList(i + 1, breaths.size)
                            val c = Edits.distance(script(split), heard)
                            if (c < best) tried += Tried(c, split, i)
                        }
                    }
                }
                // The closest first; of two as close, the one tried first.
                val chosen = tried.sortedBy { it.cost }.firstOrNull {
                    it.cost <= best - margin || (silences.isNotEmpty() && endsInSilence(it.breaths, it.split))
                } ?: break
                best = chosen.cost
                breaths = chosen.breaths
            }
            return joined(breaths.map { breathOf(it[0], it[1]) })
        }
    }
}
