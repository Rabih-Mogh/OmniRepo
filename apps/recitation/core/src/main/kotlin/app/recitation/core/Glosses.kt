package app.recitation.core

/**
 * Word meanings that come a verse at a time, not a word at a time: under each verse, a
 * line for every word or phrase of it that wants explaining, written "the words: what they
 * mean". That is how the Arabic books of the Quran's unfamiliar words are set out, and
 * most words have no line, since a reader of Arabic knows them. This finds, for each line,
 * the word of the verse it is about, so the meaning can stand under that word.
 */
object Glosses {
    /** Under a word that a meaning given under the word before it also covers. */
    const val CARRIES_ON = "…"

    /** The lines of [text], a verse's meanings, each as the words explained and what they mean. A line with no colon carries on the one before. */
    fun entries(text: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for (line in text.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
            val colon = line.indexOf(':')
            if (colon > 0) out += line.substring(0, colon).trim() to line.substring(colon + 1).trim()
            else if (out.isNotEmpty()) out[out.lastIndex] = out.last().first to out.last().second + " " + line
            else out += "" to line
        }
        return out
    }

    /**
     * For each verse of a surah, for each of its words, the meaning to stand under it: empty
     * for a word that has none. [verses] are the verses' words in everyday spelling and
     * [told] each verse's meanings as they come (see [entries]). A meaning of several words
     * stands under the first of them, and the rest have [CARRIES_ON].
     *
     * The words a line explains are written in everyday spelling with every vowel, and now
     * and then a little differently from the verse's own, so they are matched by their
     * letters alone and may differ in one letter in four. A line whose words aren't in its
     * verse is looked for in the verse before and the one after: some lines stand a verse
     * off in the source. One found nowhere is kept whole, words and meaning, under the last
     * word of its verse, so that nothing of the source is left out.
     */
    fun place(verses: List<List<String>>, told: List<String>): List<List<String>> {
        val bare = verses.map { verse -> verse.map(::bare) }
        val out = verses.map { MutableList(it.size) { "" } }
        fun put(v: Int, word: Int, meaning: String) {
            val there = out[v][word]
            out[v][word] = if (there.isEmpty() || there == CARRIES_ON) meaning else "$there\n$meaning"
        }
        for (v in verses.indices) {
            var after = 0
            for ((words, meaning) in entries(told.getOrElse(v) { "" })) {
                val wanted = bare(words)
                val count = words.split(' ').count { it.isNotEmpty() }
                val found = sequenceOf(v, v - 1, v + 1).filter { it in verses.indices && wanted.isNotEmpty() }
                    .firstNotNullOfOrNull { u -> find(bare[u], wanted, count, if (u == v) after else 0)?.let { u to it } }
                if (found == null) {
                    if (verses[v].isNotEmpty()) put(v, verses[v].lastIndex, if (words.isEmpty()) meaning else "$words: $meaning")
                    continue
                }
                val (u, at) = found
                put(u, at.first, meaning)
                for (k in at.first + 1..at.last) if (out[u][k].isEmpty()) out[u][k] = CARRIES_ON
                if (u == v) after = at.first
            }
        }
        return out
    }

    /**
     * The words of a verse ([bare], each by its letters alone) that [wanted] is: the run of
     * words nearest to it, of [count] words or a couple more (the verse may write as two
     * words what the line writes as one). Of two alike, the one at or after [after], where
     * the line before was found, and then the earlier. Null if none is near enough.
     */
    private fun find(bare: List<String>, wanted: String, count: Int, after: Int): IntRange? {
        var best: IntRange? = null
        var least = Int.MAX_VALUE
        var late = true
        for (from in bare.indices) {
            val run = StringBuilder()
            for (to in from until minOf(bare.size, from + count + 2)) {
                run.append(bare[to])
                if (run.length > wanted.length + 3) break
                val off = Edits.distance(run.toString(), wanted)
                if (off * 4 > wanted.length) continue
                val before = from < after
                if (off < least || (off == least && late && !before)) {
                    best = from..to
                    least = off
                    late = before
                }
            }
        }
        return best
    }

    /** A word by its letters alone: no signs, no stroke, and the ways of writing an alif, a hamza's seat, a ya and a closed ta each as one. */
    internal fun bare(text: String): String = buildString {
        for (c in text) {
            when {
                c == 'أ' || c == 'إ' || c == 'آ' || c == 'ٱ' -> append('ا')
                c == 'ى' -> append('ي')
                c == 'ؤ' -> append('و')
                c == 'ئ' -> append('ي')
                c == 'ة' -> append('ه')
                c in 'ء'..'ي' && c != 'ـ' -> append(c)
            }
        }
    }
}
