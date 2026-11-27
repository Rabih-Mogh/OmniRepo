package app.recitation.core

import kotlin.random.Random

/** What a tajweed exercise is about: one thing a teacher listens for, so that only it is judged. */
enum class Topic {
    GHUNNAH, IKHFA, IQLAB, IDGHAM, QALQALAH,
    MADD_NATURAL, MADD_MUNFASIL, MADD_MUTTASIL, MADD_LAZIM, MADD_ARID,

    /** Stopping on a word in the middle of a verse, with the form the word takes at a stop. */
    STOP,

    /** Telling two letters apart that are made close to each other. */
    LETTERS,
}

/**
 * One go of an exercise: [verse] of [surah] (both counted from 1) recited for [topic].
 * For [Topic.LETTERS], [letters] are the letters to tell apart; for [Topic.STOP],
 * [stopAfter] is the word, counted from 0, to stop on.
 */
data class Drill(val surah: Int, val verse: Int, val topic: Topic, val letters: String = "", val stopAfter: Int = -1)

/** How a go of an exercise went, for the thing it was about. */
enum class Went {
    /** Nothing to correct about it. */
    SOUND,

    /** Something to go over. */
    MISSED,

    /** The words that carry it weren't said: there was nothing to judge. */
    UNHEARD,
}

/**
 * Chooses verses for the tajweed exercises and judges a recitation for one topic. The
 * verses are short ones, so each is a breath or two: a rule is practised by reciting
 * verses that have it, one after another, and being told about that rule alone.
 */
object Drills {
    /** A verse is taken for an exercise if it has this many words: enough to carry a rule, few enough for a breath or two. */
    private val LENGTHS = 3..10

    /** A verse to stop in the middle of is longer: it is recited in two breaths. */
    private val LENGTHS_TO_STOP_IN = 6..14

    /** The pairs of letters that are made close to each other and are the ones most often said for each other. */
    val PAIRS = listOf("صس", "ضد", "طت", "ظذ", "قك", "حه", "عء")

    /** How many verses an exercise on a topic is made of. */
    const val GOES = 8

    /**
     * How many goes of an exercise of [of] have to be sound for its lesson to count as
     * learned: three in four, so six of eight.
     */
    fun needed(of: Int): Int = (of * 3 + 3) / 4

    /** The topic a correction falls under: what a reader who was given it should practise. Null for the rest. */
    fun topicOf(correction: Correction): Topic? = topicOf(correction.fault, correction.madd)

    private fun topicOf(fault: Fault, madd: Madd?): Topic? = when (fault) {
        Fault.MADD_SHORT, Fault.MADD_LONG, Fault.MADD_MISSING, Fault.MADD_UNEVEN -> topicOf(madd)
        Fault.MADD_ADDED -> Topic.MADD_NATURAL
        Fault.GHUNNAH_SHORT -> Topic.GHUNNAH
        Fault.IKHFA_CLEAR, Fault.IKHFA_SHORT -> Topic.IKHFA
        Fault.IQLAB_MISSED -> Topic.IQLAB
        Fault.IDGHAM_MISSED, Fault.IDGHAM_GHUNNAH_SHORT -> Topic.IDGHAM
        Fault.QALQALAH_MISSING -> Topic.QALQALAH
        Fault.LETTER -> Topic.LETTERS
        Fault.SHADDA_MISSING, Fault.VOWEL, Fault.DROPPED, Fault.ADDED, Fault.WORD_MISSING -> null
    }

    private fun topicOf(madd: Madd?): Topic = when (madd) {
        Madd.MUNFASIL -> Topic.MADD_MUNFASIL
        Madd.MUTTASIL, Madd.MUTTASIL_AT_STOP -> Topic.MADD_MUTTASIL
        Madd.LAZIM -> Topic.MADD_LAZIM
        Madd.ARID, Madd.LIN -> Topic.MADD_ARID
        Madd.NATURAL, null -> Topic.MADD_NATURAL
    }

    /** The pair among [PAIRS] a letter said for another belongs to, if both are of one pair; empty otherwise. */
    fun pairOf(correction: Correction): String = PAIRS.firstOrNull { pair -> correction.wanted.any { it in pair } && correction.heard.any { it in pair } }.orEmpty()

    /** Whether [verse], recited in one breath, has a place where [topic] is listened for (for letters: one of [letters]). */
    fun has(verse: List<WordForms>, topic: Topic, letters: String = ""): Boolean = when (topic) {
        Topic.STOP -> stopIn(verse) >= 0
        Topic.LETTERS -> verse.any { word -> word.inside.script.any { it in letters } }
        else -> Expected.breath(verse, 0, verse.size).awaited.any { topicOf(it) == topic }
    }

    /**
     * The words of [verse], counted from 0, where [topic] shows when the verse is recited in
     * one breath (for letters: one of [letters]; for a stop: its last word): the words to
     * point at, or to play, to show what the topic sounds like.
     */
    fun places(verse: List<WordForms>, topic: Topic, letters: String = ""): List<Int> = when (topic) {
        Topic.STOP -> listOf(verse.size - 1)
        Topic.LETTERS -> verse.indices.filter { w -> verse[w].inside.script.any { it in letters } }
        else -> Expected.breath(verse, 0, verse.size).awaited.filter { topicOf(it) == topic }.map { it.word }.distinct()
    }

    /** The topic a thing listened for falls under; null for a doubled letter, which no exercise is about. */
    fun topicOf(awaited: Awaited): Topic? = when (awaited.kind) {
        Listened.MADD -> topicOf(awaited.madd)
        Listened.QALQALAH -> Topic.QALQALAH
        Listened.IKHFA -> Topic.IKHFA
        Listened.IQLAB -> Topic.IQLAB
        Listened.GHUNNAH -> Topic.GHUNNAH
        Listened.IDGHAM_GHUNNAH, Listened.IDGHAM -> Topic.IDGHAM
        Listened.SHADDA -> null
    }

    /** The rules of recitation, as the page of a surah marks them on a word's letters, that make up [topic]. */
    fun rulesOf(topic: Topic): Set<Rule> = when (topic) {
        Topic.GHUNNAH -> setOf(Rule.GHUNNAH)
        Topic.IKHFA -> setOf(Rule.IKHFA, Rule.IKHFA_SHAFAWI)
        Topic.IQLAB -> setOf(Rule.IQLAB)
        Topic.IDGHAM -> setOf(Rule.IDGHAM_GHUNNAH, Rule.IDGHAM_BILA_GHUNNAH, Rule.IDGHAM_SHAFAWI)
        Topic.QALQALAH -> setOf(Rule.QALQALAH)
        Topic.MADD_NATURAL -> setOf(Rule.MADD_TABII, Rule.MADD_SILAH_SUGHRA, Rule.MADD_IWAD)
        Topic.MADD_MUNFASIL -> setOf(Rule.MADD_MUNFASIL, Rule.MADD_SILAH_KUBRA)
        Topic.MADD_MUTTASIL -> setOf(Rule.MADD_MUTTASIL)
        Topic.MADD_LAZIM -> setOf(Rule.MADD_LAZIM)
        Topic.MADD_ARID -> setOf(Rule.MADD_ARID, Rule.MADD_LIN)
        Topic.STOP, Topic.LETTERS -> emptySet()
    }

    /**
     * The letters of [words] (a verse, or a few words, as the mushaf writes them, recited in
     * one breath) that [topic] is about: for each word, the stretches of it to point at. For
     * letters, every one of [letters]; for a stop, the whole of the word [stopAfter].
     * [stopped] says the reciter stops on the last of the words, as at the end of a verse;
     * otherwise they are taken as recited on into whatever follows.
     */
    fun letters(words: List<String>, topic: Topic, letters: String = "", stopAfter: Int = -1, stopped: Boolean = true): List<List<IntRange>> = when (topic) {
        Topic.STOP -> words.indices.map { w -> if (w == stopAfter) listOf(words[w].indices) else emptyList() }
        Topic.LETTERS -> words.map { word ->
            word.indices.filter { word[it] in letters }.map { at ->
                // The letter with the signs written on it.
                var end = at + 1
                while (end < word.length && Character.getType(word[end]) == Character.NON_SPACING_MARK.toInt()) end++
                at until end
            }
        }
        else -> {
            // Recited on, the last word is followed by another: any word that no rule joins it to.
            val all = if (stopped) words else words + "وَ"
            val stops = BooleanArray(all.size).also { if (it.isNotEmpty()) it[it.lastIndex] = true }
            val marks = Tajweed.marks(all, stops)
            val rules = rulesOf(topic)
            val plain = if (topic == Topic.MADD_NATURAL) Tajweed.longVowels(all, stops) else null
            words.indices.map { w ->
                val ruled = marks[w].filter { it.rule in rules }.map { it.from until it.to }
                // An ordinary long vowel is under no sign: it is told by its letters, where no other rule has it.
                val more = plain?.get(w).orEmpty().filter { long -> marks[w].none { it.from <= long.last && long.first < it.to && it.rule.family != Family.HEAVY } }
                (ruled + more).sortedBy { it.first }
            }
        }
    }

    /**
     * [words] as an exercise on [drill]'s topic sees them: only what the exercise is about is
     * left to correct in them, and only what it listens for. A word that wasn't said isn't
     * among them: it is neither sound nor to go over. For a stop, the word to stop on keeps
     * all that was corrected in it, since it has to sound as it does at a stop.
     */
    fun only(drill: Drill, words: List<WordRecited>): List<WordRecited> = words.filter { !it.unsaid }.map { word ->
        val kept = when (drill.topic) {
            Topic.STOP -> if (word.index == drill.stopAfter) word.corrections else emptyList()
            Topic.LETTERS -> word.corrections.filter { c -> c.fault == Fault.LETTER && (c.wanted.any { it in drill.letters } || c.heard.any { it in drill.letters }) }
            else -> word.corrections.filter { topicOf(it) == drill.topic }
        }
        val measures = word.measures.filter { topicOf(it.awaited) == drill.topic }
            // Something else wrong at the same place (a doubled letter said once) is not this exercise's to tell.
            .map { m -> if (m.fault == null || topicOf(m.fault, m.awaited.madd) == drill.topic) m else Measure(m.awaited, m.wanted, m.wanted, null) }
        WordRecited(word.index, word.stopped, kept, if (drill.topic == Topic.STOP) word.madds else emptyList(), word.wanted, word.listened.filter { topicOf(it) == drill.topic }, measures)
    }

    /**
     * The word of [verse] to ask a reader to stop on, counted from 0: one that sounds
     * different at a stop than when recited on, as near the verse's middle as there is.
     * -1 if the verse is too short or has none.
     */
    fun stopIn(verse: List<WordForms>): Int {
        if (verse.size !in LENGTHS_TO_STOP_IN) return -1
        val middle = (verse.size - 1) / 2f
        return (2..verse.size - 3).filter { verse[it].last.script != verse[it].inside.script }.minByOrNull { kotlin.math.abs(it - middle) } ?: -1
    }

    /**
     * [count] verses for [topic] out of [surahs] (each surah's verses by its number), no two
     * the same, in an order of [random]'s choosing. Fewer if there aren't that many.
     */
    fun pick(surahs: Map<Int, List<List<WordForms>>>, topic: Topic, count: Int, random: Random, letters: String = ""): List<Drill> {
        val all = ArrayList<Drill>()
        for ((surah, verses) in surahs) for ((v, verse) in verses.withIndex()) {
            if ((topic == Topic.STOP || verse.size in LENGTHS) && has(verse, topic, letters)) all += Drill(surah, v + 1, topic, letters, if (topic == Topic.STOP) stopIn(verse) else -1)
        }
        return all.shuffled(random).take(count)
    }

    /**
     * How [words], what was heard of the drill's verse, went as far as the drill's topic
     * goes. It is judged only in the words that were said: a rule in a word passed over
     * was neither kept nor broken, and if none of the words that carry it were said, the
     * go is [Went.UNHEARD]. For a stop, the reader has to have stopped on the word asked,
     * and said it as it sounds at a stop.
     */
    fun went(drill: Drill, words: List<WordRecited>): Went {
        val said = words.filter { !it.unsaid }
        return when (drill.topic) {
            Topic.STOP -> said.filter { it.index == drill.stopAfter }.let { asked ->
                if (asked.isEmpty()) Went.UNHEARD else if (asked.any { it.stopped && it.sound }) Went.SOUND else Went.MISSED
            }
            Topic.LETTERS -> when {
                said.any { word -> word.corrections.any { c -> c.fault == Fault.LETTER && (c.wanted.any { it in drill.letters } || c.heard.any { it in drill.letters }) } } -> Went.MISSED
                said.none { word -> word.wanted.any { it in drill.letters } } -> Went.UNHEARD
                else -> Went.SOUND
            }
            else -> when {
                said.any { word -> word.corrections.any { topicOf(it) == drill.topic } } -> Went.MISSED
                said.none { word -> word.listened.any { topicOf(it) == drill.topic } } -> Went.UNHEARD
                else -> Went.SOUND
            }
        }
    }
}
