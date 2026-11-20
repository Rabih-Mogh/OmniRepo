package app.recitation.core

/** The rules grouped as they are shown: one colour to a family, as in the printed mushafs that colour them. */
enum class Family {
    /** Sound held through the nose (ghunnah), two counts. */
    NASAL,

    /** A long vowel that must be drawn out to six counts (madd lazim). */
    MADD_LAZIM,

    /** A long vowel that must be drawn out to four or five counts (madd wajib). */
    MADD_WAJIB,

    /** A long vowel that may be drawn out: two, four or six counts (madd ja'iz). */
    MADD_JAIZ,

    /** A long vowel of two counts written with a small letter, or one that comes of stopping. */
    MADD_TABII,

    /** A stopped letter let go with a slight echo (qalqalah). */
    ECHO,

    /** A letter said full-mouthed (tafkhim). */
    HEAVY,

    /** A letter that is written but not sounded, or is merged whole into the next. */
    QUIET,
}

/** A rule of recitation (tajweed) at work on a stretch of a word. */
enum class Rule(val family: Family) {
    /** Ghunnah: a doubled n or m, held through the nose for two counts. */
    GHUNNAH(Family.NASAL),

    /** Ikhfa haqiqi: an n, or the n of a tanween, hidden into one of fifteen letters after it. */
    IKHFA(Family.NASAL),

    /** Ikhfa shafawi: an m hidden before b. */
    IKHFA_SHAFAWI(Family.NASAL),

    /** Iqlab: an n turned into an m before b. */
    IQLAB(Family.NASAL),

    /** Idgham bi-ghunnah: an n merged into y, n, m or w in the next word, through the nose. */
    IDGHAM_GHUNNAH(Family.NASAL),

    /** Idgham shafawi: an m merged into the m after it. */
    IDGHAM_SHAFAWI(Family.NASAL),

    /** Madd lazim: a long vowel before a doubled or stopped letter, and the letters some surahs open with; six counts. */
    MADD_LAZIM(Family.MADD_LAZIM),

    /** Madd wajib muttasil: a long vowel before a hamza in the same word; four or five counts. */
    MADD_MUTTASIL(Family.MADD_WAJIB),

    /** Madd ja'iz munfasil: a long vowel before a hamza that opens the next word. */
    MADD_MUNFASIL(Family.MADD_JAIZ),

    /** Madd as-silah al-kubra: the long vowel of the pronoun "hu" or "hi" before a hamza. */
    MADD_SILAH_KUBRA(Family.MADD_JAIZ),

    /** Madd 'arid lil-sukun: a long vowel before the last letter of a word the reciter stops on. */
    MADD_ARID(Family.MADD_JAIZ),

    /** Madd al-lin: a soft w or y (after a fatha) before the last letter of a word the reciter stops on. */
    MADD_LIN(Family.MADD_JAIZ),

    /** Madd tabi'i: an ordinary long vowel, two counts; marked where it is written with a small letter. */
    MADD_TABII(Family.MADD_TABII),

    /** Madd as-silah as-sughra: the long vowel of the pronoun "hu" or "hi" when run on, two counts. */
    MADD_SILAH_SUGHRA(Family.MADD_TABII),

    /** Madd al-'iwad: stopping on "-an", which is said as a long a. */
    MADD_IWAD(Family.MADD_TABII),

    /** Qalqalah: q, t, b, j or d without a vowel, released with an echo. */
    QALQALAH(Family.ECHO),

    /** Tafkhim ar-ra': an r said full-mouthed. */
    TAFKHIM_RA(Family.HEAVY),

    /** The l of the name of Allah said full-mouthed, after a fatha or a damma. */
    TAFKHIM_LAM(Family.HEAVY),

    /** Idgham bi-ghayr ghunnah: an n merged whole into l or r. */
    IDGHAM_BILA_GHUNNAH(Family.QUIET),

    /** Idgham al-mutamathilayn: a letter merged into the same letter after it. */
    IDGHAM_MUTAMATHILAYN(Family.QUIET),

    /** Idgham al-mutajanisayn: a letter merged into one said from the same place (t, d and the heavy t; dh, th and the heavy dh; b and m). */
    IDGHAM_MUTAJANISAYN(Family.QUIET),

    /** Idgham al-mutaqaribayn: a letter merged into one said from close by (l and r, q and k). */
    IDGHAM_MUTAQARIBAYN(Family.QUIET),

    /** Al-lam ash-shamsiyyah: the l of "al-" merged into a sun letter. */
    LAM_SHAMSIYYAH(Family.QUIET),

    /** Hamzat al-wasl: a joining alif, silent when the reciter runs into it. */
    HAMZAT_WASL(Family.QUIET),

    /** Written but never sounded. */
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
 * This follows the reading of Hafs from 'Asim as the Madinah mushaf writes it. Of the
 * heavy and light letters it gives the two that change from place to place: the r, and
 * the l of the name of Allah.
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

    /** A small y written above the line: a long i the script leaves out ("an-nabiyyin"). */
    private const val SMALL_HIGH_YA = '\u06E7'
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

    /** The letters said with the tongue raised, which keep an r before them heavy. */
    private val RAISED = "خصضغطقظ".toSet()

    /** Under an r that leans towards an i ("majraha"). */
    private const val LEANING = '\u06EA'

    /** Letters said from the same place, which merge into one another. */
    private val SAME_PLACE = listOf("تدط".toSet(), "ثذظ".toSet(), "بم".toSet())

    /** Letters said from close by, which merge into one another. */
    private val CLOSE_BY = listOf("لر".toSet(), "قك".toSet())

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

        /** Where a long vowel written small on the letter begins, or -1. */
        val smallAt: Int = (start + 1 until end).firstOrNull { text[it] == DAGGER_ALIF || text[it] == SMALL_WAW || text[it] == SMALL_YA || text[it] == SMALL_HIGH_YA } ?: -1

        /** Up to where the letter itself and its own signs go: a long vowel written small after them is another sound. */
        val own: Int get() = if (smallAt >= 0) smallAt else end
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

    /** Where the doubled l of the name of Allah is in [word] ("Allah", "lillah", "Allahumma"), or -1. */
    private fun nameOfAllah(word: List<Unit>): Int {
        for (j in 1 until word.size - 1) {
            val l = word[j]
            if (l.base != 'ل' || !l.shadda || l.vowel != FATHA || word[j - 1].base != 'ل' || word[j + 1].base != 'ه') continue
            // "al-lahab" and "al-lahw" begin the same way and go on.
            val rest = word.drop(j + 2).filter { !it.unsounded }
            if (rest.isEmpty() || (rest.size == 1 && rest[0].base == 'م' && rest[0].shadda)) return j
        }
        return -1
    }

    /**
     * Whether the r at [k] of [word] is said heavy. With a vowel of its own it follows the
     * vowel: heavy with a or u, light with i. Without one, or [stoppedOn], it follows what
     * comes before it.
     */
    private fun heavyR(word: List<Unit>, k: Int, stoppedOn: Boolean): Boolean {
        val r = word[k]
        if (r.has(LEANING)) return false
        val tanween = if (r.tanweenAt >= 0) r.text[r.tanweenAt] else null
        if (!stoppedOn) {
            if (r.vowel == KASRA || tanween == KASRATAN) return false
            if (r.vowel != null || tanween != null) return true
        }
        val before = word.take(k).lastOrNull { !it.unsounded } ?: return true
        return when {
            // The i a joining alif begins with is not the word's own ("irji'u").
            before.base == WASLA -> true
            before.vowel == FATHA || before.vowel == DAMMA -> true
            // After an i it is light, unless a raised letter follows it in the word ("qirtas", "mirsad").
            before.vowel == KASRA -> !stoppedOn && word.getOrNull(k + 1).let { it != null && it.base in RAISED && it.vowel != KASRA }
            before.has(DAGGER_ALIF) || before.base == ALIF || before.base == TATWEEL || before.base == 'و' -> true
            before.base == 'ي' || before.base == 'ى' -> false
            // After a letter without a vowel, as the vowel before that one. With a raised letter
            // between ("Misr", "al-qitr") it is said as it would be run on.
            word.take(k - 1).lastOrNull { !it.unsounded }?.vowel == KASRA -> before.base in RAISED && r.vowel == FATHA
            else -> true
        }
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

        /** The letter alone, leaving a long vowel written small on it to its own rule. */
        fun putLetter(w: Int, u: Unit, rule: Rule) = put(w, u.start, u.own, rule)

        /** The letter the one at [k] of word [w] runs into, and whether it is in the same word; null at a stop. */
        fun after(w: Int, k: Int, afterTanween: Boolean = false): Pair<Unit, Boolean>? {
            var i = k + 1
            // The alif or ya a tanween is written on top of is only its seat.
            if (afterTanween) while (i < units[w].size && units[w][i].bare && (units[w][i].base == ALIF || units[w][i].base == 'ى')) i++
            while (i < units[w].size && units[w][i].unsounded) i++
            if (i < units[w].size) return units[w][i] to true
            if (stopsAfter(w)) return null
            // Before a joining alif a tanween is said "-ni", clear, and is under no rule.
            if (afterTanween && units[w + 1].firstOrNull()?.base == WASLA) return null
            // A joining alif at the start of the next word is silent when run into.
            val next = units[w + 1].firstOrNull { it.base != WASLA && !it.unsounded } ?: return null
            return next to false
        }

        /** What becomes of an n, or the n of a tanween, before [next]. */
        fun ofN(next: Unit, sameWord: Boolean): Rule? = when {
            next.base == 'ب' -> Rule.IQLAB
            // Inside one word an n before y or w stays clear (dunya, qinwan).
            next.base == 'ي' || next.base == 'و' || next.base == 'ن' || next.base == 'م' -> if (sameWord) null else Rule.IDGHAM_GHUNNAH
            next.base == 'ل' || next.base == 'ر' -> if (sameWord) null else Rule.IDGHAM_BILA_GHUNNAH
            next.hamza || next.base in THROAT -> null
            next.base in HIDING -> Rule.IKHFA
            else -> null
        }

        /** The vowel a word leaves in the ear as the next is begun: of its last letter that has one. */
        fun endsOnAnI(w: Int): Boolean {
            val last = units[w].lastOrNull { it.vowel != null || it.tanweenAt >= 0 } ?: return false
            // A tanween before a joining alif is said "-ni".
            return last.tanweenAt >= 0 || last.vowel == KASRA
        }

        for ((w, word) in units.withIndex()) {
            if (word.isEmpty()) continue
            val stop = stopsAfter(w)
            val opens = w == 0 || stopsAfter(w - 1)
            val named = word.none { it.vowel != null || it.tanweenAt >= 0 || it.sukun || it.shadda || it.has(DAGGER_ALIF) } && word.all { it.base in NAMED }
            // The last letter that is sounded: not a seat, nor a letter marked as silent.
            val last = word.indexOfLast { !it.unsounded && !(it.bare && (it.base == ALIF || it.base == 'ى') && word.any { t -> t.tanweenAt >= 0 }) }
            val name = nameOfAllah(word)

            for ((k, u) in word.withIndex()) {
                // ---- written, not sounded
                when {
                    u.unsounded -> put(w, u, Rule.SILENT)
                    u.has(SOUNDED_AT_A_STOP) && !(stop && k == word.lastIndex) -> put(w, u, Rule.SILENT)
                    u.base == WASLA && !(k == 0 && opens) -> put(w, u, Rule.HAMZAT_WASL)
                }

                // ---- a long vowel under the wavy line
                if (u.drawnOut) {
                    val small = u.base !in LONG_VOWELS && u.base != 'آ'
                    val from = if (!small && u.base != TATWEEL || u.smallAt < 0) u.start else if (u.base == TATWEEL) u.start else u.smallAt
                    val next = word.drop(k + 1).firstOrNull { !it.unsounded }
                    // "ya" and "ha" joined to the word they call to are still words of their own.
                    val calling = k == 1 && u.base == TATWEEL && (word[0].base == 'ي' || word[0].base == 'ه') && word[0].vowel == FATHA
                    when {
                        named -> put(w, from, u.end, Rule.MADD_LAZIM)
                        // The pronoun's long "hu" or "hi" is written as a small w or y on its h.
                        next == null -> if (!stop) put(w, from, u.end, if (small && u.base == 'ه') Rule.MADD_SILAH_KUBRA else Rule.MADD_MUNFASIL)
                        next.hamza -> put(w, from, u.end, if (calling) Rule.MADD_MUNFASIL else Rule.MADD_MUTTASIL)
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
                        if (rule == Rule.IDGHAM_GHUNNAH && !next.second && (next.first.base == 'ي' || next.first.base == 'و')) putLetter(w + 1, next.first, rule)
                    }
                } else if (u.bare && u.base in CONSONANTS) {
                    val next = after(w, k)
                    if (next != null) {
                        val (into, same) = next
                        when (u.base) {
                            'ن' -> ofN(into, same)?.let { rule ->
                                put(w, u, rule)
                                if (rule == Rule.IDGHAM_GHUNNAH && !same && (into.base == 'ي' || into.base == 'و')) putLetter(w + 1, into, rule)
                            }
                            'م' -> when (into.base) {
                                'ب' -> put(w, u, Rule.IKHFA_SHAFAWI)
                                'م' -> put(w, u, Rule.IDGHAM_SHAFAWI)
                            }
                            // Any other bare letter is merged whole into the doubled letter after it: the l of
                            // "al-" before a sun letter, "qad tabayyana". Not in the name of Allah, whose two
                            // l's are written and read as the name's own.
                            else -> if (into.shadda && !(same && name == k + 1)) {
                                val pair = setOf(u.base, into.base)
                                put(
                                    w, u,
                                    when {
                                        u.base == 'ل' && same -> Rule.LAM_SHAMSIYYAH
                                        u.base == into.base -> Rule.IDGHAM_MUTAMATHILAYN
                                        SAME_PLACE.any { it.containsAll(pair) } -> Rule.IDGHAM_MUTAJANISAYN
                                        CLOSE_BY.any { it.containsAll(pair) } -> Rule.IDGHAM_MUTAQARIBAYN
                                        else -> Rule.SILENT
                                    },
                                )
                            }
                        }
                    }
                }

                // ---- a doubled n or m. At the start of a word it is doubled only by the word before running into it.
                if ((u.base == 'ن' || u.base == 'م') && u.shadda) {
                    val first = word.take(k).all { it.base == WASLA }
                    if (!(first && opens)) putLetter(w, u, Rule.GHUNNAH)
                }

                // ---- the echo: on a stopped q, t, b, j or d, and on one the reciter stops on
                val stoppedOn = stop && k == last && u.tanweenAt.let { it < 0 || u.text[it] != FATHATAN }
                if (u.base in ECHOING && (u.sukun || stoppedOn)) put(w, u, Rule.QALQALAH)

                // ---- the heavy r, and the heavy l of the name of Allah
                if (u.base == 'ر' && (named || heavyR(word, k, stoppedOn))) putLetter(w, u, Rule.TAFKHIM_RA)
                if (k == name) {
                    val own = word[k - 1].vowel ?: word.take(k - 1).lastOrNull { it.vowel != null }?.vowel
                    val heavy = when {
                        own != null -> own != KASRA
                        opens -> true
                        else -> !endsOnAnI(w - 1)
                    }
                    if (heavy) putLetter(w, u, Rule.TAFKHIM_LAM)
                }
            }

            // ---- stopping on the word: a long vowel, or a soft w or y, before its last letter may be drawn out
            if (stop && last >= 1) {
                val end = word[last]
                val before = word[last - 1]
                val vowelBefore = word.getOrNull(last - 2)?.vowel
                val endsOnALetter = (end.base in CONSONANTS || end.hamza) && !(end.tanweenAt >= 0 && end.text[end.tanweenAt] == FATHATAN)
                val soft = before.sukun && vowelBefore == FATHA && (before.base == 'و' || before.base == 'ي' || before.base == 'ى')
                val long = when {
                    before.base == ALIF -> before.bare && vowelBefore == FATHA
                    before.base == 'و' -> before.bare && vowelBefore == DAMMA
                    before.base == 'ي' || before.base == 'ى' -> before.bare && vowelBefore == KASRA
                    else -> false
                }
                when {
                    !endsOnALetter -> {}
                    soft -> put(w, before, Rule.MADD_LIN)
                    long || (before.base == TATWEEL && before.smallAt >= 0) -> put(w, before, Rule.MADD_ARID)
                    // A long a written as a small alif on the letter itself.
                    before.smallAt >= 0 && before.text[before.smallAt] == DAGGER_ALIF -> put(w, before.smallAt, before.end, Rule.MADD_ARID)
                }
            }

            // ---- stopping on "-an": it is said as a long a, on the alif it is written with
            if (stop && last >= 0) {
                val end = word[last]
                if (end.tanweenAt >= 0 && end.text[end.tanweenAt] == FATHATAN && end.base != 'ة') {
                    val seat = word.drop(last + 1).lastOrNull { it.bare && (it.base == ALIF || it.base == 'ى') }
                    put(w, end.tanweenAt, seat?.end ?: end.end, Rule.MADD_IWAD)
                }
            }

            // ---- ordinary long vowels written with a small letter, where nothing above has claimed them
            for ((k, u) in word.withIndex()) {
                if (u.drawnOut || named) continue
                if (u.smallAt < 0) continue
                if (u.text[u.smallAt] != DAGGER_ALIF && u.base != TATWEEL) {
                    // The pronoun's "hu" and "hi" are long only when run on.
                    val pronoun = u.base == 'ه' && k == last
                    if (!(pronoun && stop)) put(w, u.smallAt, u.end, if (pronoun) Rule.MADD_SILAH_SUGHRA else Rule.MADD_TABII)
                } else {
                    // The small letter with the stroke or the silent letter it is written on, or alone on a sounded letter.
                    val seat = u.base == TATWEEL || (u.vowel == null && (u.base == 'و' || u.base == 'ى' || u.base == 'ي'))
                    put(w, if (seat) u.start else u.smallAt, if (seat) u.end else u.smallAt + 1, Rule.MADD_TABII)
                }
            }
        }
        return out.map { marks -> marks.sortedBy { it.from } }
    }
}
