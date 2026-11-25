package app.accentcoach.core

/**
 * A lesson on one sound: how it is [made], the [slip] most often made with it, and the
 * words of its drill. Each of [pairs] is two words that differ in that sound only, the
 * first of them having it ("think", "sink").
 */
data class Lesson(
    val id: String,
    val title: String,
    val sound: String,
    val made: String,
    val slip: String,
    val words: List<Word>,
    val pairs: List<Pair<Word, Word>>,
) {
    /** What the drill asks for, in order: each word on its own, then each pair. */
    val items: List<DrillItem> = words.map { DrillItem(listOf(it)) } + pairs.map { DrillItem(listOf(it.first, it.second)) }
}

/** One thing to say in a drill: a word, or the two words of a pair. */
data class DrillItem(val words: List<Word>) {
    val text: String get() = words.joinToString(", ") { it.text }
    val isPair: Boolean get() = words.size > 1
}

/** How the lesson's sound came out in one item of its drill. */
enum class Said {
    /** As a native speaker says it. */
    NATIVE,
    /** Close, but a little foreign. */
    CLOSE,
    /** Another sound, or none. */
    OFF,
    /** Nothing was heard at all. */
    UNHEARD,
}

/** [judged] are the sounds the drill is about; the rest of the word is in [assessment]. */
data class DrillResult(val said: Said, val judged: List<PhonemeResult>, val assessment: Assessment)

/**
 * Judges the items of a lesson's drill. Only the lesson's sound counts: a word is said
 * well when that sound was, whatever happened to the others. In a pair it is the sound
 * the two words differ in that counts, in both words, so "think, sink" is only right
 * when the two were told apart.
 */
object Drills {

    fun sentence(lesson: Lesson, item: DrillItem): Sentence = Sentence(lesson.id, lesson.title, item.text, item.words)

    /** The item's native sounds word by word, each with whether it is one the drill judges. */
    fun sounds(lesson: Lesson, item: DrillItem): List<List<Pair<String, Boolean>>> {
        val words = item.words.map { Phonemes.normalize(it.phonemes) }
        return words.map { word ->
            word.mapIndexed { i, p ->
                p to if (item.isPair) words.any { it.getOrNull(i) != p } else p == lesson.sound
            }
        }
    }

    fun judge(lesson: Lesson, item: DrillItem, heard: List<String>): DrillResult {
        val assessment = Assessor.assess(sentence(lesson, item), heard)
        if (assessment.heard.isEmpty()) return DrillResult(Said.UNHEARD, emptyList(), assessment)
        val judged = sounds(lesson, item).flatMapIndexed { w, word ->
            val expected = assessment.words[w].phonemes.filter { it.expected != null }
            word.indices.filter { word[it].second }.map { expected[it] }
        }
        val said = when {
            judged.any { it.verdict == Verdict.WRONG || it.verdict == Verdict.MISSING } -> Said.OFF
            judged.any { it.verdict == Verdict.CLOSE } -> Said.CLOSE
            else -> Said.NATIVE
        }
        return DrillResult(said, judged, assessment)
    }

    /** A drill has gone well, and its lesson counts as learned, with three items in four native. */
    fun learned(native: Int, of: Int): Boolean = of > 0 && native * 4 >= of * 3
}
