package app.recitation.core

/**
 * One sound of a word.
 * [optional]: the reciter may leave it out (a hamza the model rarely hears, a vowel dropped at a stop).
 * [upTo]: how far into the word's letters this sound reaches, for filling the word in as it is recited.
 */
data class Phone(val sound: Sound, val optional: Boolean, val upTo: Int)

/**
 * Turns a word of the Quran, written in the Uthmani script with all its marks, into the
 * sounds a reciter makes. That script spells recitation out closely: a letter with no
 * mark is silent or merged into the next one, long vowels and stops are marked, and so on.
 * This follows those marks. It isn't a full tajweed engine, and doesn't need to be: the
 * sounds only have to be close enough to line the text up with the audio.
 */
object Phonemizer {

    private const val FATHA = 'َ'
    private const val DAMMA = 'ُ'
    private const val KASRA = 'ِ'
    private const val FATHATAN = 'ً'
    private const val DAMMATAN = 'ٌ'
    private const val KASRATAN = 'ٍ'
    private const val SHADDA = 'ّ'
    private const val SUKUN = 'ْ'
    private const val HAMZA_ABOVE = 'ٔ'
    private const val HAMZA_BELOW = 'ٕ'
    private const val DAGGER_ALIF = 'ٰ'
    private const val SMALL_WAW = 'ۥ'
    private const val SMALL_YA = 'ۦ'
    private const val TATWEEL = 'ـ'
    private const val WASLA = 'ٱ'
    private const val ALIF = 'ا'
    private const val ALIF_MADDA = 'آ'

    /** Marks saying "this letter isn't pronounced". */
    private val SILENT = setOf('۟', '۠')

    /** Signs where a reciter may stop inside a verse. */
    internal val STOP_SIGNS = setOf('ۖ', 'ۗ', 'ۘ', 'ۙ', 'ۚ', 'ۛ')

    private val VOWELLING = setOf(FATHA, DAMMA, KASRA, FATHATAN, DAMMATAN, KASRATAN, SUKUN, SHADDA)

    private val CONSONANTS = mapOf(
        'ب' to Sound.B, 'ت' to Sound.T, 'ث' to Sound.THA, 'ج' to Sound.JIM, 'ح' to Sound.HHA, 'خ' to Sound.KHA,
        'د' to Sound.D, 'ذ' to Sound.DHAL, 'ر' to Sound.R, 'ز' to Sound.Z, 'س' to Sound.S, 'ش' to Sound.SHIN,
        'ص' to Sound.SAD, 'ض' to Sound.DAD, 'ط' to Sound.TAH, 'ظ' to Sound.ZAH, 'ع' to Sound.AYN, 'غ' to Sound.GHAYN,
        'ف' to Sound.F, 'ق' to Sound.Q, 'ك' to Sound.K, 'ل' to Sound.L, 'م' to Sound.M, 'ن' to Sound.N,
        'ه' to Sound.H, 'ة' to Sound.TA_MARBUTA,
    )
    private val HAMZAS = setOf('ء', 'أ', 'إ', 'ؤ', 'ئ')
    private val LETTERS = CONSONANTS.keys + HAMZAS + setOf(ALIF, ALIF_MADDA, 'و', 'ى', 'ي', TATWEEL, WASLA)

    /** The letters some surahs open with (الٓمٓ, يسٓ…) are read out by name. */
    private val LETTER_NAMES = mapOf(
        'ا' to listOf(Sound.HAMZA, Sound.A, Sound.L, Sound.I, Sound.F),
        'ل' to listOf(Sound.L, Sound.AA, Sound.M),
        'م' to listOf(Sound.M, Sound.II, Sound.M),
        'ص' to listOf(Sound.SAD, Sound.AA, Sound.D),
        'ر' to listOf(Sound.R, Sound.AA),
        'ك' to listOf(Sound.K, Sound.AA, Sound.F),
        'ه' to listOf(Sound.H, Sound.AA),
        'ي' to listOf(Sound.Y, Sound.AA),
        'ع' to listOf(Sound.AYN, Sound.A, Sound.Y, Sound.N),
        'ط' to listOf(Sound.TAH, Sound.AA),
        'س' to listOf(Sound.S, Sound.II, Sound.N),
        'ح' to listOf(Sound.HHA, Sound.AA),
        'ق' to listOf(Sound.Q, Sound.AA, Sound.F),
        'ن' to listOf(Sound.N, Sound.UU, Sound.N),
    )

    /**
     * A letter with the marks written on it, and where it sits in the word: from [start]
     * up to [end]. [body] is where its vowel sign begins (the letter itself and a shadda or
     * sukun come before it), or [end] if it has none.
     */
    internal class Letter(val base: Char, val start: Int) {
        val marks = HashSet<Char>(4)
        var end = start + 1
        var body = -1
    }

    /** The signs that are a vowel's own: a short vowel, a doubled one, or a small long one written above. */
    internal val VOWEL_SIGNS = setOf(FATHA, DAMMA, KASRA, FATHATAN, DAMMATAN, KASRATAN, DAGGER_ALIF, SMALL_WAW, SMALL_YA)

    private class Draft(var sound: Sound, var optional: Boolean, val upTo: Int, val tanween: Boolean = false)

    internal fun units(word: String): List<Letter> {
        val out = ArrayList<Letter>()
        for ((i, c) in word.withIndex()) {
            if (c in LETTERS) {
                out += Letter(c, i)
            } else if (out.isNotEmpty() && c != ' ') {
                val letter = out.last()
                letter.marks += c
                if (c in VOWEL_SIGNS && letter.body < 0) letter.body = i
                if (c !in STOP_SIGNS) letter.end = i + 1
            }
        }
        for (letter in out) if (letter.body < 0 || letter.body > letter.end) letter.body = letter.end
        return out
    }

    /** Whether [letter] is a consonant with a sound of its own (not a long vowel's letter, nor a hamza's seat). */
    internal fun isConsonant(letter: Char): Boolean = letter in CONSONANTS

    /** Whether the word carries a sign that allows a stop after it. */
    fun allowsStop(word: String): Boolean = word.any { it in STOP_SIGNS }

    /** The sounds of each word of a verse, in order. The reciter is taken to stop at its end. */
    fun verse(words: List<String>): List<List<Phone>> =
        words.mapIndexed { i, word ->
            word(word, next = words.getOrNull(i + 1), stop = i == words.lastIndex || allowsStop(word))
        }

    /**
     * The sounds of [word]. [next] is the word after it: it decides whether a final n or m
     * merges into it. [stop]: the reciter may pause after this word, dropping its last vowel.
     */
    fun word(word: String, next: String? = null, stop: Boolean = false): List<Phone> {
        val us = units(word)
        if (us.isEmpty()) return emptyList()
        if (us.none { u -> u.marks.any { it in VOWELLING || it == DAGGER_ALIF } } && us.all { it.base in LETTER_NAMES }) {
            return us.flatMap { u -> LETTER_NAMES.getValue(u.base).map { Phone(it, false, u.end) } }
        }
        // What the word runs into, for merging; a joining alif at the start of the next word is silent.
        val after = next?.let(::units)?.dropWhile { it.base == WASLA }?.firstOrNull()

        val out = ArrayList<Draft>()
        fun last() = out.lastOrNull()?.sound
        // A consonant is written by the letter's body; its vowel by the sign on it. Keeping the
        // two apart lets the light fall on the letter and then on its vowel sign.
        fun add(sound: Sound, u: Letter, optional: Boolean = false, body: Boolean = false) {
            out += Draft(sound, optional, if (body) u.body else u.end)
        }
        fun lengthen(short: Sound, long: Sound, u: Letter) {
            when (last()) {
                short -> out[out.lastIndex] = Draft(long, false, u.end)
                long -> {}
                else -> add(long, u)
            }
        }
        fun vowel(u: Letter) {
            val m = u.marks
            when {
                FATHA in m -> add(if (DAGGER_ALIF in m) Sound.AA else Sound.A, u)
                KASRA in m -> add(if (SMALL_YA in m) Sound.II else Sound.I, u)
                DAMMA in m -> add(if (SMALL_WAW in m) Sound.UU else Sound.U, u)
                DAGGER_ALIF in m -> add(Sound.AA, u)
            }
            val tanween = when {
                FATHATAN in m -> Sound.A
                DAMMATAN in m -> Sound.U
                KASRATAN in m -> Sound.I
                else -> null
            }
            if (tanween != null) {
                add(tanween, u)
                // The n is often swallowed by the next word, and is never said at a stop.
                out += Draft(Sound.N, true, u.end, tanween = true)
            }
        }

        for ((k, u) in us.withIndex()) {
            val m = u.marks
            if (m.any { it in SILENT }) continue
            val marked = m.any { it in VOWELLING }
            val following = us.getOrNull(k + 1) ?: after
            val b = u.base
            when {
                b == TATWEEL -> when {
                    HAMZA_ABOVE in m -> { add(Sound.HAMZA, u, optional = true, body = true); vowel(u) }
                    DAGGER_ALIF in m -> lengthen(Sound.A, Sound.AA, u)
                    SMALL_YA in m -> lengthen(Sound.I, Sound.II, u)
                    SMALL_WAW in m -> lengthen(Sound.U, Sound.UU, u)
                }
                // Silent when the reciter runs on from the word before; a short vowel after a pause.
                b == WASLA -> if (k == 0) add(Sound.LINK, u, optional = true)
                // After an a it only draws the vowel out; otherwise it is a hamza with a long a.
                b == ALIF_MADDA -> if (last() == Sound.A) lengthen(Sound.A, Sound.AA, u) else { add(Sound.HAMZA, u, optional = true); add(Sound.AA, u) }
                b in HAMZAS || HAMZA_ABOVE in m || HAMZA_BELOW in m -> { add(Sound.HAMZA, u, optional = true, body = true); vowel(u) }
                b == ALIF -> when {
                    marked -> { add(Sound.HAMZA, u, optional = true, body = true); vowel(u) }
                    last() == Sound.A -> lengthen(Sound.A, Sound.AA, u)
                }
                b == 'و' -> when {
                    marked -> { add(Sound.W, u, body = true); vowel(u) }
                    DAGGER_ALIF in m -> lengthen(Sound.A, Sound.AA, u)
                    last() == Sound.U -> lengthen(Sound.U, Sound.UU, u)
                }
                b == 'ي' || b == 'ى' -> when {
                    marked -> { add(Sound.Y, u, body = true); vowel(u) }
                    DAGGER_ALIF in m -> lengthen(Sound.A, Sound.AA, u)
                    last() == Sound.I -> lengthen(Sound.I, Sound.II, u)
                    last() == Sound.A && b == 'ى' -> lengthen(Sound.A, Sound.AA, u)
                }
                else -> {
                    val sound = CONSONANTS.getValue(b)
                    if (!marked) {
                        // A bare letter is merged into a doubled one after it. A bare n or m
                        // before anything else is still said, through the nose.
                        val merged = following != null && SHADDA in following.marks
                        if ((b == 'ن' || b == 'م') && !merged) {
                            add(if (b == 'ن' && following?.base == 'ب') Sound.M else sound, u)
                        }
                    } else {
                        add(sound, u, body = true)
                        vowel(u)
                        // "Allah": the a after its doubled l is long, though no alif is written.
                        if (b == 'ل' && SHADDA in m && FATHA in m && k > 0 && us[k - 1].base == 'ل') {
                            val ha = us.getOrNull(k + 1)
                            if (ha != null && ha.base == 'ه' && (FATHA in ha.marks || DAMMA in ha.marks || KASRA in ha.marks)) {
                                out[out.lastIndex].sound = Sound.AA
                            }
                        }
                    }
                }
            }
        }

        if (stop && out.isNotEmpty()) {
            val end = out.last()
            when {
                // At a stop -un and -in vanish, and -an becomes a long a.
                end.tanween -> if (out.size > 1 && out[out.lastIndex - 1].sound != Sound.A) out[out.lastIndex - 1].optional = true
                end.sound == Sound.A || end.sound == Sound.I || end.sound == Sound.U -> end.optional = true
                // "lahu" with its small waw is "lah" at a stop.
                (end.sound == Sound.UU || end.sound == Sound.II) &&
                    (SMALL_WAW in us.last().marks || SMALL_YA in us.last().marks) -> end.optional = true
            }
        }
        return out.map { Phone(it.sound, it.optional, it.upTo) }
    }
}
