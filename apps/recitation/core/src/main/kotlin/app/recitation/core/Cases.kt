package app.recitation.core

/**
 * One place where something an exercise is about is really at work, as a teacher would
 * point at it: in [word], on its [letter] (empty where the whole word is the point), which
 * is the n of a tanween if [tanween]; [into] is the letter after it that brings the rule
 * about, [heavy] whether that letter is a heavy one, and [stopped] whether the reciter
 * stops on the word. [rule] is the rule, as the page marks it; null for a stop, for two
 * letters to tell apart, and for an ordinary long vowel the page gives no mark.
 */
class Case(val rule: Rule?, val word: String, val letter: String = "", val into: String = "", val tanween: Boolean = false, val heavy: Boolean = false, val stopped: Boolean = false)

/** A [case] at word [word] of verse [verse] of a surah, both counted from 0. */
class CaseAt(val verse: Int, val word: Int, val case: Case)

/**
 * Finds where in a surah a topic is really at work for a given reciter, which depends on
 * where that reciter stops. A nun at the end of a word is hidden, turned or merged only if
 * the reciter runs on into the next word; a long vowel before a word's last letter is
 * drawn out only if the reciter stops there. So words that would show a rule on paper are
 * left out wherever this reciter's stops undo it, and words that show one only because
 * this reciter stops on them are taken in.
 */
object Cases {
    private const val TANWEENS = "ًٌٍ"
    private const val HEAVY = "صضطظق"
    private const val WASLA = 'ٱ'
    private const val NEVER_SOUNDED = '۟'
    private const val HAMZAS = "ءأإؤئ"

    /**
     * Every place in a surah where [topic] is at work (for letters, where one of [letters]
     * is), in the surah's order. [verses] are its verses' words as the mushaf writes them
     * and [stops] says, for each word of the surah in order, whether the reciter stops
     * after it (see [Pointer.refine]).
     */
    fun places(verses: List<List<String>>, stops: BooleanArray, topic: Topic, letters: String = ""): List<CaseAt> {
        val words = verses.flatten()
        val out = ArrayList<CaseAt>()
        fun stopped(i: Int) = i == words.lastIndex || stops.getOrElse(i) { false }
        val marks = if (topic == Topic.STOP || topic == Topic.LETTERS) null else Tajweed.marks(words, stops)
        val plain = if (topic == Topic.MADD_NATURAL) Tajweed.longVowels(words, stops) else null
        val rules = Drills.rulesOf(topic)
        var i = 0
        for ((v, verse) in verses.withIndex()) {
            for ((w, word) in verse.withIndex()) {
                when (topic) {
                    Topic.STOP -> if (stopped(i)) out += CaseAt(v, w, Case(null, word, stopped = true))
                    Topic.LETTERS -> word.firstOrNull { it in letters }?.let { out += CaseAt(v, w, Case(null, word, it.toString(), stopped = stopped(i))) }
                    else -> {
                        val mark = marks!![i].firstOrNull { it.rule in rules }
                        val long = plain?.get(i)?.firstOrNull { range -> marks[i].none { it.from <= range.last && range.first < it.to } }
                        if (mark != null) out += CaseAt(v, w, of(words, i, mark.from until mark.to, mark.rule, stopped(i)))
                        else if (long != null) out += CaseAt(v, w, of(words, i, long, null, stopped(i)))
                    }
                }
                i++
            }
        }
        return out
    }

    /** The case of [rule] on the stretch [range] of word [i] of [words]. */
    private fun of(words: List<String>, i: Int, range: IntRange, rule: Rule?, stopped: Boolean): Case {
        val word = words[i]
        val units = Phonemizer.units(word)
        val first = word[range.first]
        val tanween = first in TANWEENS
        val at = units.indexOfLast { it.start <= range.first }.coerceAtLeast(0)
        // The letter the rule falls on: the one written, or for a long vowel written small, the letter it stands for.
        val letter = when {
            tanween -> ""
            first == 'ٰ' -> "ا"
            first == 'ۥ' -> "و"
            first == 'ۦ' || first == 'ۧ' -> "ي"
            else -> units.getOrNull(at)?.base?.let { if (it == 'ـ' || it == 'آ') "ا" else it.toString() }.orEmpty()
        }
        // The letter after it that is sounded: in the same word, or the first of the next if the reciter runs on.
        fun sounded(of: List<Phonemizer.Letter>, from: Int) = of.drop(from).firstOrNull { u ->
            u.base != WASLA && NEVER_SOUNDED !in u.marks && !(tanween && (u.base == 'ا' || u.base == 'ى') && u.marks.isEmpty())
        }
        val after = sounded(units, at + 1) ?: if (stopped) null else words.getOrNull(i + 1)?.let { sounded(Phonemizer.units(it), 0) }
        val into = after?.base?.let { if (it in HAMZAS) "ء" else it.toString() }.orEmpty()
        return Case(rule, word, letter, into, tanween, into.isNotEmpty() && into[0] in HEAVY, stopped)
    }
}
