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

    /** The topic a correction falls under: what a reader who was given it should practise. Null for the rest. */
    fun topicOf(correction: Correction): Topic? = when (correction.fault) {
        Fault.MADD_SHORT, Fault.MADD_LONG, Fault.MADD_MISSING, Fault.MADD_UNEVEN -> topicOf(correction.madd)
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

    private fun topicOf(awaited: Awaited): Topic? = when (awaited.kind) {
        Listened.MADD -> topicOf(awaited.madd)
        Listened.QALQALAH -> Topic.QALQALAH
        Listened.IKHFA -> Topic.IKHFA
        Listened.IQLAB -> Topic.IQLAB
        Listened.GHUNNAH -> Topic.GHUNNAH
        Listened.IDGHAM_GHUNNAH, Listened.IDGHAM -> Topic.IDGHAM
        Listened.SHADDA -> null
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
     * Whether [words], what was heard of the drill's verse, were sound as far as the drill's
     * topic goes: nothing to correct about it. For a stop, the reader has to have stopped on
     * the word asked, and said it as it sounds at a stop.
     */
    fun sound(drill: Drill, words: List<WordRecited>): Boolean = when (drill.topic) {
        Topic.STOP -> words.any { it.index == drill.stopAfter && it.stopped && it.sound }
        Topic.LETTERS -> words.none { word -> word.corrections.any { c -> c.fault == Fault.LETTER && (c.wanted.any { it in drill.letters } || c.heard.any { it in drill.letters }) } }
        else -> words.none { word -> word.corrections.any { topicOf(it) == drill.topic } }
    }
}
