package app.accentcoach.core

/** A word of a practice sentence with its native General American phonemes. */
data class Word(val text: String, val phonemes: List<String>)

data class Sentence(val id: String, val focus: String, val text: String, val words: List<Word>)

enum class Verdict {
    /** Native, or a variant native speakers also use. */
    OK,
    /** Understandable but sounds a little foreign. */
    CLOSE,
    /** A different sound: this is where the accent shows. */
    WRONG,
    /** Expected but not heard. */
    MISSING,
    /** Heard but not part of the word. */
    EXTRA,
}

/** One expected phoneme lined up with what was heard (either side can be absent). */
data class PhonemeResult(val expected: String?, val heard: String?, val verdict: Verdict)

data class WordResult(val word: Word, val phonemes: List<PhonemeResult>) {
    val needsWork: Boolean get() = phonemes.any { it.verdict == Verdict.WRONG || it.verdict == Verdict.MISSING }
    val slightlyOff: Boolean get() = !needsWork && phonemes.any { it.verdict == Verdict.CLOSE || it.verdict == Verdict.EXTRA }
}

data class Tip(val sound: String, val advice: String, val words: List<String>)

data class Assessment(
    val words: List<WordResult>,
    /** 0..100: how native the sentence sounded. */
    val score: Int,
    val tips: List<Tip>,
    /** Everything the recogniser heard, in order. */
    val heard: List<String>,
)

/**
 * Compares the phonemes heard against a sentence's native ones: lines the two up
 * (weighted edit distance, where similar sounds cost less to swap), grades each pair,
 * and picks tips for the sounds that were off.
 */
object Assessor {

    fun assess(sentence: Sentence, heardRaw: List<String>): Assessment {
        val heard = Phonemes.normalize(heardRaw)
        val expected = sentence.words.flatMapIndexed { w, word -> Phonemes.normalize(word.phonemes).map { it to w } }
        val pairs = align(expected.map { it.first }, heard)

        // Hand each aligned pair to a word: expected phonemes carry their word; extras join the word before them.
        val perWord = List(sentence.words.size) { mutableListOf<PhonemeResult>() }
        var e = 0
        var currentWord = 0
        for ((exp, got) in pairs) {
            if (exp != null) {
                currentWord = expected[e].second
                e++
            }
            perWord[currentWord] += PhonemeResult(exp, got, grade(exp, got, heard.isEmpty()))
        }
        markEpenthesis(perWord)
        forgiveMergedDoubles(perWord)
        val words = sentence.words.mapIndexed { i, w -> WordResult(w, perWord[i]) }
        return Assessment(words, score(words), tips(words), heard)
    }

    /** Pairs of (expected, heard) along the cheapest alignment; null marks a gap. */
    fun align(expected: List<String>, heard: List<String>): List<Pair<String?, String?>> {
        val n = expected.size
        val m = heard.size
        val cost = Array(n + 1) { FloatArray(m + 1) }
        for (i in 1..n) cost[i][0] = cost[i - 1][0] + gapCost(expected[i - 1])
        for (j in 1..m) cost[0][j] = cost[0][j - 1] + GAP
        for (i in 1..n) for (j in 1..m) {
            cost[i][j] = minOf(
                cost[i - 1][j - 1] + swapCost(expected[i - 1], heard[j - 1]),
                cost[i - 1][j] + gapCost(expected[i - 1]),
                cost[i][j - 1] + GAP,
            )
        }
        val out = ArrayDeque<Pair<String?, String?>>()
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && cost[i][j] == cost[i - 1][j - 1] + swapCost(expected[i - 1], heard[j - 1]) -> {
                    out.addFirst(expected[i - 1] to heard[j - 1]); i--; j--
                }
                i > 0 && cost[i][j] == cost[i - 1][j] + gapCost(expected[i - 1]) -> {
                    out.addFirst(expected[i - 1] to null); i--
                }
                else -> {
                    out.addFirst(null to heard[j - 1]); j--
                }
            }
        }
        return out
    }

    private fun grade(expected: String?, heard: String?, nothingHeard: Boolean): Verdict = when {
        expected == null -> Verdict.EXTRA
        heard == null -> if (!nothingHeard && expected in REDUCED) Verdict.OK else Verdict.MISSING
        else -> judge(expected, heard)
    }

    /** How a heard phoneme compares with the native one it stands for. */
    fun judge(expected: String, heard: String): Verdict {
        if (expected == heard || Phonemes.base(expected) == Phonemes.base(heard)) return Verdict.OK
        if (heard in (NATIVE_VARIANTS[expected] ?: emptySet())) return Verdict.OK
        if (heard in (NEAR[expected] ?: emptySet())) return Verdict.CLOSE
        return Verdict.WRONG
    }

    /**
     * Two identical sounds across a word boundary ("will leave", "big game") are said
     * as one by native speakers, so the "missing" second one isn't a mistake.
     */
    private fun forgiveMergedDoubles(words: List<MutableList<PhonemeResult>>) {
        val flat = words.flatMapIndexed { w, list -> list.indices.map { w to it } }
            .filter { (w, k) -> words[w][k].expected != null }
        for ((idx, at) in flat.withIndex()) {
            val (w, k) = at
            val p = words[w][k]
            if (p.verdict != Verdict.MISSING) continue
            val neighbours = listOfNotNull(flat.getOrNull(idx - 1), flat.getOrNull(idx + 1)).map { (nw, nk) -> words[nw][nk] }
            if (neighbours.any { it.expected == p.expected && it.verdict == Verdict.OK }) words[w][k] = p.copy(verdict = Verdict.OK)
        }
    }

    /** A vowel slipped in after a consonant (e.g. "desk-u"): a common non-native habit, worth flagging. */
    private fun markEpenthesis(words: List<MutableList<PhonemeResult>>) {
        for (list in words) for (k in list.indices) {
            val p = list[k]
            val prev = list.getOrNull(k - 1)
            if (p.verdict == Verdict.EXTRA && p.heard != null && Phonemes.isVowel(p.heard) &&
                prev?.expected != null && !Phonemes.isVowel(prev.expected)
            ) {
                list[k] = p.copy(verdict = Verdict.WRONG)
            }
        }
    }

    private fun score(words: List<WordResult>): Int {
        val all = words.flatMap { it.phonemes }
        val expectedCount = all.count { it.expected != null }
        if (expectedCount == 0) return 0
        val points = all.sumOf {
            when (it.verdict) {
                Verdict.OK -> if (it.expected != null) 1.0 else 0.0
                Verdict.CLOSE -> 0.6
                Verdict.EXTRA -> -0.25
                Verdict.WRONG -> if (it.expected == null) -0.5 else 0.0
                Verdict.MISSING -> 0.0
            }
        }
        return (100 * points / expectedCount).coerceIn(0.0, 100.0).toInt()
    }

    private fun tips(words: List<WordResult>): List<Tip> {
        val byAdvice = linkedMapOf<Pair<String, String>, MutableSet<String>>()
        for (w in words) for (p in w.phonemes) {
            if (p.verdict == Verdict.OK || p.verdict == Verdict.EXTRA) continue
            val advice = adviceFor(p) ?: continue
            byAdvice.getOrPut(advice) { linkedSetOf() } += w.word.text
        }
        return byAdvice.map { (k, v) -> Tip(k.first, k.second, v.toList()) }
    }

    private fun adviceFor(p: PhonemeResult): Pair<String, String>? {
        val exp = p.expected
        if (exp == null) {
            return "Extra vowel" to "Don't add a vowel after a consonant: end the word on the consonant itself."
        }
        if (p.verdict == Verdict.MISSING) {
            return "/$exp/ dropped" to "Say every sound, especially the last consonant of a word. " +
                "Native speakers soften it but don't drop it."
        }
        val tip = ADVICE[exp] ?: ADVICE[Phonemes.base(exp)]
            ?: return "/$exp/" to "You said /${p.heard}/. Listen to the native version and copy that sound."
        return "/$exp/" to tip
    }

    private const val GAP = 0.7f

    /** Leaving out a reduced vowel is what native speakers do, so it costs little. */
    private fun gapCost(expected: String) = if (expected in REDUCED) 0.35f else GAP

    private fun swapCost(expected: String, heard: String): Float = when (judge(expected, heard)) {
        Verdict.OK -> if (expected == heard) 0f else 0.1f
        Verdict.CLOSE -> 0.4f
        else -> if (Phonemes.isVowel(expected) == Phonemes.isVowel(heard)) 0.9f else 1.3f
    }

    /** Unstressed vowels that native speakers reduce or swallow. */
    private val REDUCED = setOf("ə", "ᵻ")

    /** Sounds General American speakers really use for the expected one. */
    private val NATIVE_VARIANTS: Map<String, Set<String>> = mapOf(
        "ə" to setOf("ɪ", "ᵻ", "ʌ", "ɐ"),
        "ᵻ" to setOf("ɪ", "ə", "i"),
        "ʌ" to setOf("ə", "ɐ"),
        "ɑː" to setOf("ɑ", "ɔː", "ɔ", "a"),
        "ɔː" to setOf("ɑː", "ɑ", "ɔ"),
        "ɜː" to setOf("ə", "ɚ"),
        "t" to setOf("ɾ", "ʔ"),
        "d" to setOf("ɾ"),
        "n" to setOf("n̩"),
        "l" to setOf("ɫ", "l̩"),
        "i" to setOf("iː", "ɪ"),
    )

    /** Close but foreign-sounding stand-ins, shown as "slightly off". */
    private val NEAR: Map<String, Set<String>> = mapOf(
        "ɾ" to setOf("t", "d", "r"),
        "eɪ" to setOf("e", "eː", "ɛ", "ɛɪ", "ei"),
        "oʊ" to setOf("o", "oː", "əʊ", "ɔ", "ou"),
        "ɪ" to setOf("i", "iː", "e"),
        "iː" to setOf("ɪ"),
        "ʊ" to setOf("u", "uː"),
        "uː" to setOf("ʊ"),
        "æ" to setOf("ɛ", "a", "aː"),
        "ɛ" to setOf("e", "æ"),
        "ɹ" to setOf("ɻ"),
        "ŋ" to setOf("n"),
        "z" to setOf("s"),
        "ʌ" to setOf("a", "ɑ", "ɔ"),
        "ɑː" to setOf("ʌ"),
        "aɪ" to setOf("ai", "a"),
        "aʊ" to setOf("au"),
    )

    private val ADVICE = mapOf(
        "θ" to "TH as in think: put the tip of your tongue lightly between your teeth and blow. Not t, s or f.",
        "ð" to "TH as in this: tongue between the teeth as for think, but with your voice on. Not d or z.",
        "ɹ" to "American R: pull your tongue back without touching the roof of your mouth and round your lips a little. " +
            "Don't tap it, roll it or make it in the throat.",
        "w" to "W: round your lips as for \"oo\" and open them; your teeth don't touch your lip.",
        "v" to "V: rest your top teeth on your bottom lip and buzz.",
        "ɪ" to "Short i as in sit: short and relaxed, between \"ee\" and \"eh\". Don't smile.",
        "iː" to "Long ee as in seat: spread your lips and hold it a little longer than the short i.",
        "i" to "The ee at the end of words like happy: a light, short \"ee\".",
        "æ" to "The vowel in cat: drop your jaw and spread your lips, between \"eh\" and \"ah\".",
        "ɛ" to "The vowel in bed: mouth half open, short \"eh\".",
        "ʌ" to "The vowel in cup: a short, relaxed \"uh\" with the mouth half open.",
        "ɑː" to "The vowel in hot: open wide, tongue low and back, \"ah\".",
        "ɔː" to "The vowel in thought: open \"ah\" with the lips slightly rounded.",
        "eɪ" to "Say \"ay\" as a glide: start at \"eh\" and slide up to \"ee\".",
        "oʊ" to "Say \"oh\" as a glide: start at \"oh\" and round into \"oo\".",
        "aɪ" to "Say \"eye\" as a glide: from \"ah\" up to \"ee\".",
        "aʊ" to "Say \"ow\" as a glide: from \"ah\" rounding into \"oo\".",
        "ʊ" to "Short oo as in book: short and relaxed, not the long oo of food.",
        "uː" to "Long oo as in food: round your lips and hold it.",
        "ɾ" to "American T between vowels sounds like a quick, soft d: water → \"wadder\", better → \"bedder\".",
        "h" to "H: just a breath of air, soft, not scraped in the throat. Don't drop it.",
        "ŋ" to "NG: lift the back of your tongue and hum through your nose, with no g after it.",
        "l" to "L: the tip of your tongue on the ridge behind your top teeth; at the end of a word it's darker.",
        "z" to "Z: buzz it with your voice on, as in is and was.",
        "s" to "S: a clean hiss with your tongue behind your top teeth.",
        "ʃ" to "SH: lips pushed forward, a soft rushing sound.",
        "dʒ" to "J as in judge: start with d and release into a buzzing sh.",
        "tʃ" to "CH as in church: start with t and release into sh.",
        "j" to "Y as in yes: tongue close to the roof of the mouth, then glide.",
        "ə" to "The weak \"uh\" (schwa) in unstressed syllables: short and lazy, don't give it full value.",
        "ɜː" to "The vowel in bird: tongue bunched back with an R colour, \"er\".",
    )
}
