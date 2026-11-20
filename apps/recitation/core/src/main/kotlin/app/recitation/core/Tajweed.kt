package app.recitation.core

/** The rules grouped as they are shown: one colour to a family. */
enum class Family {
    /** Sound held through the nose. */
    NASAL,

    /** A vowel that must be drawn out: four to six counts. */
    MADD_MUST,

    /** A vowel that may be drawn out: two, four or six counts. */
    MADD_MAY,

    /** A stopped letter let go with a slight echo. */
    ECHO,

    /** A letter that is written but not sounded, or is merged into the next. */
    QUIET,
}

/** A rule of recitation (tajweed) at work on a stretch of a word. */
enum class Rule(val family: Family) {
    /** Ghunnah: a doubled n or m, held through the nose for two counts. */
    GHUNNAH(Family.NASAL),

    /** Ikhfa: an n (or an m before b) hidden into the letter after it, through the nose. */
    IKHFA(Family.NASAL),

    /** Iqlab: an n turned into an m before b, through the nose. */
    IQLAB(Family.NASAL),

    /** Idgham with ghunnah: an n merged into y, n, m or w after it (or an m into an m), through the nose. */
    IDGHAM_GHUNNAH(Family.NASAL),

    /** Madd muttasil: a long vowel before a hamza in the same word, four or five counts. */
    MADD_REQUIRED(Family.MADD_MUST),

    /** Madd lazim: a long vowel before a doubled or stopped letter, six counts. */
    MADD_LAZIM(Family.MADD_MUST),

    /**
     * A long vowel that may be drawn out: before a hamza that opens the next word (munfasil),
     * or before the last letter of a word the reciter stops on ('arid lil-sukun, lin).
     */
    MADD_ALLOWED(Family.MADD_MAY),

    /** Qalqalah: q, t, b, j or d without a vowel, released with an echo. */
    QALQALAH(Family.ECHO),

    /** Written but not sounded: merged whole into the next letter, or simply silent. */
    SILENT(Family.QUIET),
}

/** [rule] applies to the characters from [from] up to [to] of a word. */
data class Mark(val from: Int, val to: Int, val rule: Rule)

/**
 * Works out which rules of recitation apply where, from the Uthmani script: it writes
 * them into its signs (an n with no sukun is merged or hidden, a wavy line over a long
 * vowel means it is drawn out, and so on).
 *
 * Several rules depend on whether the reciter stops after a word or runs on into the
 * next: an n at the end of a word merges into the next word only if he runs on; a long
 * vowel before a word's last letter is drawn out only if he stops there. So the answer
 * is given for one way of reciting: [marks] is told after which words the reciter stops.
 *
 * This follows the reading of Hafs from 'Asim as the Madinah mushaf writes it. It leaves
 * out the heavy and light letters (tafkhim and tarqiq), which have no sign of their own.
 */
object Tajweed {

    private const val FATHA = 'َ'
    private const val DAMMA = 'ُ'
    private const val KASRA = 'ِ'
    private const val FATHATAN = 'ً'
    private const val DAMMATAN = 'ٌ'
    private const val KASRATAN = 'ٍ'
    private const val SHADDA = 'ّ'
    private const val SUKUN = 'ْ'
    private const val MADDAH = 'ٓ'
    private const val HAMZA_ABOVE = 'ٔ'
    private const val HAMZA_BELOW = 'ٕ'
    private const val DAGGER_ALIF = 'ٰ'
    private const val SMALL_WAW = 'ۥ'
    private const val SMALL_YA = 'ۦ'
    private const val TATWEEL = 'ـ'
    private const val WASLA = 'ٱ'
    private const val ALIF = 'ا'

    /** Over a letter that is never sounded. */
    private const val NEVER_SOUNDED = '۟'

    /** Over a letter that is sounded only if the reciter stops on it. */
    private const val SOUNDED_AT_A_STOP = '۠'

    /** The small m written by a tanween or an n that is merged, hidden or turned. */
    private val SMALL_MEEMS = setOf('ۢ', 'ۭ')

    private val HAMZAS = setOf('ء', 'أ', 'إ', 'ؤ', 'ئ')
    private val THROAT = setOf('ه', 'ع', 'ح', 'غ', 'خ')
    private val HIDING = "تثجدذزسشصضطظفقك".toSet()
    private val ECHOING = "قطبجد".toSet()
    private val LONG_VOWELS = setOf(ALIF, 'و', 'ي', 'ى', TATWEEL)
    private val CONSONANTS = "بتثجحخدذرزسشصضطظعغفقكلمنهة".toSet()

    /** The letters some surahs open with, read out by name. */
    private val NAMED = "المصركهيعطسحقن".toSet()

    /** A letter and its signs, with what the rules ask about it. */
    private class Unit(val text: String, val letter: Phonemizer.Letter) {
        val base: Char get() = letter.base
        val start: Int get() = letter.start
        val end: Int get() = letter.end

        fun has(sign: Char) = sign in letter.marks
        fun at(sign: Char): Int = (start + 1 until end).firstOrNull { text[it] == sign } ?: -1

        val vowel: Char? = listOf(FATHA, DAMMA, KASRA).firstOrNull(::has)
        val tanweenAt: Int = (start + 1 until end).firstOrNull { text[it] == FATHATAN || text[it] == DAMMATAN || text[it] == KASRATAN } ?: -1
        val shadda = has(SHADDA)
        val sukun = has(SUKUN)

        /** The wavy line over a long vowel; some texts write an alif with it as one character. */
        val drawnOut = has(MADDAH) || base == 'آ'

        /** Neither a vowel nor a sukun nor a shadda: in this script, merged, hidden or silent. */
        val bare = vowel == null && tanweenAt < 0 && !sukun && !shadda
        val hamza = base in HAMZAS || has(HAMZA_ABOVE) || has(HAMZA_BELOW) || (base == ALIF && (vowel != null || tanweenAt >= 0))
        val unsounded = has(NEVER_SOUNDED)
    }

    /** Where a reciter stops when nothing says otherwise: at the end of every verse. */
    fun stopsAtVerseEnds(verseSizes: List<Int>): BooleanArray {
        val stops = BooleanArray(verseSizes.sum())
        var word = 0
        for (size in verseSizes) {
            word += size
            if (word > 0) stops[word - 1] = true
        }
        return stops
    }

    /**
     * The rules at work in each of [words], a surah's words in order, for a reciter who
     * stops after the words [stops] says and runs on everywhere else.
     */
    fun marks(words: List<String>, stops: BooleanArray): List<List<Mark>> {
        val units = words.map { word -> Phonemizer.units(word).map { Unit(word, it) } }
        val out = List(words.size) { ArrayList<Mark>() }
        fun stopsAfter(w: Int) = w == words.lastIndex || stops.getOrElse(w) { false }

        fun put(w: Int, from: Int, to: Int, rule: Rule) {
            if (from < to && out[w].none { it.from < to && from < it.to }) out[w] += Mark(from, to, rule)
        }
        fun put(w: Int, u: Unit, rule: Rule) = put(w, u.start, u.end, rule)

        /** The letter the one at [k] of word [w] runs into, and whether it is in the same word; null at a stop. */
        fun after(w: Int, k: Int, afterTanween: Boolean = false): Pair<Unit, Boolean>? {
            var i = k + 1
            // The alif or ya a tanween is written on top of is only its seat.
            if (afterTanween) while (i < units[w].size && units[w][i].bare && (units[w][i].base == ALIF || units[w][i].base == 'ى')) i++
            while (i < units[w].size && units[w][i].unsounded) i++
            if (i < units[w].size) return units[w][i] to true
            if (stopsAfter(w)) return null
            // A joining alif at the start of the next word is silent when run into.
            val next = units[w + 1].firstOrNull { it.base != WASLA && !it.unsounded } ?: return null
            return next to false
        }

        /** What becomes of an n, or the n of a tanween, before [next]. */
        fun ofN(next: Unit, sameWord: Boolean): Rule? = when {
            next.base == 'ب' -> Rule.IQLAB
            // Inside one word an n before y or w stays clear (dunya, qinwan).
            next.base == 'ي' || next.base == 'و' || next.base == 'ن' || next.base == 'م' -> if (sameWord) null else Rule.IDGHAM_GHUNNAH
            next.base == 'ل' || next.base == 'ر' -> if (sameWord) null else Rule.SILENT
            next.hamza || next.base in THROAT -> null
            next.base in HIDING -> Rule.IKHFA
            else -> null
        }

        for ((w, word) in units.withIndex()) {
            if (word.isEmpty()) continue
            val stop = stopsAfter(w)
            val opens = w == 0 || stopsAfter(w - 1)
            val named = word.none { it.vowel != null || it.tanweenAt >= 0 || it.sukun || it.shadda || it.has(DAGGER_ALIF) } && word.all { it.base in NAMED }
            // The last letter that is sounded: not a seat, nor a letter marked as silent.
            val last = word.indexOfLast { !it.unsounded && !(it.bare && (it.base == ALIF || it.base == 'ى') && word.any { t -> t.tanweenAt >= 0 }) }

            for ((k, u) in word.withIndex()) {
                // ---- written, not sounded
                when {
                    u.unsounded -> put(w, u, Rule.SILENT)
                    u.has(SOUNDED_AT_A_STOP) && !(stop && k == word.lastIndex) -> put(w, u, Rule.SILENT)
                    u.base == WASLA && !(k == 0 && opens) -> put(w, u, Rule.SILENT)
                }

                // ---- a long vowel under the wavy line
                if (u.drawnOut) {
                    val from = if (u.base in LONG_VOWELS || u.base == 'آ') u.start else listOf(u.at(DAGGER_ALIF), u.at(SMALL_WAW), u.at(SMALL_YA)).filter { it >= 0 }.minOrNull() ?: u.start
                    val next = word.drop(k + 1).firstOrNull { !it.unsounded }
                    // "ya" and "ha" joined to the word they call to are still words of their own.
                    val calling = k == 1 && u.base == TATWEEL && (word[0].base == 'ي' || word[0].base == 'ه') && word[0].vowel == FATHA
                    when {
                        named -> put(w, from, u.end, Rule.MADD_LAZIM)
                        next == null -> if (!stop) put(w, from, u.end, Rule.MADD_ALLOWED)
                        next.hamza -> put(w, from, u.end, if (calling) Rule.MADD_ALLOWED else Rule.MADD_REQUIRED)
                        else -> put(w, from, u.end, Rule.MADD_LAZIM)
                    }
                }

                // ---- n and m without a vowel, and the n of a tanween
                if (u.tanweenAt >= 0) {
                    val next = after(w, k, afterTanween = true)
                    val rule = next?.let { ofN(it.first, sameWord = false) }
                    if (next != null && rule != null) {
                        val to = if (u.tanweenAt + 1 < u.end && u.text[u.tanweenAt + 1] in SMALL_MEEMS) u.tanweenAt + 2 else u.tanweenAt + 1
                        put(w, u.tanweenAt, to, rule)
                        if (rule == Rule.IDGHAM_GHUNNAH && !next.second && (next.first.base == 'ي' || next.first.base == 'و')) put(w + 1, next.first, rule)
                    }
                } else if (u.bare && u.base in CONSONANTS) {
                    val next = after(w, k)
                    if (next != null) {
                        val (into, same) = next
                        when (u.base) {
                            'ن' -> ofN(into, same)?.let { rule ->
                                put(w, u, rule)
                                if (rule == Rule.IDGHAM_GHUNNAH && !same && (into.base == 'ي' || into.base == 'و')) put(w + 1, into, rule)
                            }
                            'م' -> when (into.base) {
                                'ب' -> put(w, u, Rule.IKHFA)
                                'م' -> put(w, u, Rule.IDGHAM_GHUNNAH)
                            }
                            // Any other bare letter is merged whole into the doubled letter after it: the l of
                            // "al-" before a sun letter, "qad tabayyana". Not in the name of Allah, whose two
                            // l's are written and read as the name's own.
                            else -> {
                                val name = u.base == 'ل' && into.base == 'ل' && same && word.getOrNull(k + 2)?.base == 'ه'
                                if (into.shadda && !name) put(w, u, Rule.SILENT)
                            }
                        }
                    }
                }

                // ---- a doubled n or m. At the start of a word it is doubled only by the word before running into it.
                if ((u.base == 'ن' || u.base == 'م') && u.shadda) {
                    val first = word.take(k).all { it.base == WASLA }
                    if (!(first && opens)) put(w, u, Rule.GHUNNAH)
                }

                // ---- the echo: on a stopped q, t, b, j or d, and on one the reciter stops on
                if (u.base in ECHOING) {
                    val stoppedOn = stop && k == last && u.tanweenAt.let { it < 0 || u.text[it] != FATHATAN }
                    if (u.sukun || stoppedOn) put(w, u, Rule.QALQALAH)
                }
            }

            // ---- stopping on the word: a long vowel, or a soft w or y, before its last letter may be drawn out
            if (stop && last >= 1) {
                val end = word[last]
                val before = word[last - 1]
                val vowelBefore = word.getOrNull(last - 2)?.vowel
                val endsOnALetter = (end.base in CONSONANTS || end.hamza) && !(end.tanweenAt >= 0 && end.text[end.tanweenAt] == FATHATAN)
                val long = when {
                    before.base == ALIF -> before.bare && vowelBefore == FATHA
                    before.base == 'و' -> (before.bare && vowelBefore == DAMMA) || (before.sukun && vowelBefore == FATHA)
                    before.base == 'ي' || before.base == 'ى' -> (before.bare && vowelBefore == KASRA) || (before.sukun && vowelBefore == FATHA)
                    else -> false
                }
                when {
                    !endsOnALetter -> {}
                    long || (before.base == TATWEEL && before.has(DAGGER_ALIF)) -> put(w, before, Rule.MADD_ALLOWED)
                    // A long a written as a small alif on the letter itself.
                    before.has(DAGGER_ALIF) -> put(w, before.at(DAGGER_ALIF), before.end, Rule.MADD_ALLOWED)
                }
            }
        }
        return out.map { marks -> marks.sortedBy { it.from } }
    }
}
