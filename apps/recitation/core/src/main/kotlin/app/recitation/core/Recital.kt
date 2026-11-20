package app.recitation.core

/**
 * Everything a reciter says for one surah, as a run of sounds: the two opening formulas
 * (which a recording may or may not include), then every verse.
 */
class Recital(surah: Int, verses: List<List<String>>) {

    val phones: List<Phone>

    /** Which word each sound belongs to. Words of the opening formulas come first and have negative numbers. */
    private val wordOf: IntArray

    /** How many words each verse has. */
    private val verseSizes: IntArray = IntArray(verses.size) { verses[it].size }

    init {
        val all = ArrayList<Phone>()
        val words = ArrayList<Int>()
        var opening = -1
        fun formula(text: String) {
            for (word in Phonemizer.verse(text.split(' '))) {
                // Optional throughout: the recording may start straight at the first verse.
                for (phone in word) {
                    all += phone.copy(optional = true)
                    words += opening
                }
                opening--
            }
        }
        formula(ISTIADHA)
        // Al-Fatihah's first verse is the basmala itself; At-Tawbah has none.
        if (surah != 1 && surah != 9) formula(BASMALA)
        var word = 0
        for (verse in verses) {
            for (phones in Phonemizer.verse(verse)) {
                for (phone in phones) {
                    all += phone
                    words += word
                }
                word++
            }
        }
        phones = all
        wordOf = words.toIntArray()
    }

    /**
     * When each word starts, from an alignment: [starts] is [Aligner.align]'s answer and
     * [frameOfRow] the model frame each kept row came from.
     */
    fun timings(starts: IntArray, frameOfRow: IntArray, aligner: Aligner): Timings =
        build(starts, frameOfRow, aligner, phones.size, Timings.COMPLETE)

    /**
     * The same while the recording is still being listened to: only the first [sounds]
     * sounds have been placed, and the model has heard as far as [heardMs]. The answer
     * holds the words placed so far and nothing after them.
     */
    fun timingsSoFar(starts: IntArray, frameOfRow: IntArray, sounds: Int, heardMs: Int): Timings =
        build(starts, frameOfRow, null, sounds, heardMs)

    private fun build(starts: IntArray, frameOfRow: IntArray, aligner: Aligner?, sounds: Int, heardMs: Int): Timings {
        val total = verseSizes.sum()
        val wordStart = IntArray(total) { -1 }
        val letters = Array(total) { ArrayList<Int>() }
        var clear = 0
        var required = 0
        var placed = 0 // words of the surah whose every sound has been placed
        var i = 0
        while (i < phones.size) {
            val word = wordOf[i]
            var end = i
            while (end < phones.size && wordOf[end] == word) end++
            if (end > sounds) break // this word is still being listened to
            if (word >= 0) {
                var first = -1
                var second = -1
                var firstOptional = false
                for (p in i until end) {
                    val row = starts[p]
                    if (!phones[p].optional) {
                        required++
                        if (row >= 0 && aligner != null && aligner.score(p, row) > CLEAR) clear++
                    }
                    if (row < 0) continue
                    val ms = maxOf(0, frameOfRow[row] * SoundColumns.FRAME_MS - LEAD_MS)
                    if (first < 0) {
                        first = ms
                        firstOptional = phones[p].optional
                    } else if (second < 0) {
                        second = ms
                    }
                    val reached = letters[word]
                    if (reached.isEmpty() || phones[p].upTo > reached[reached.size - 2]) {
                        reached += phones[p].upTo
                        reached += ms
                    }
                }
                // A sound that may be left out can be "heard" in the tail of the word before;
                // don't let it pull the start far ahead of the word's next sound.
                if (firstOptional && second >= 0) first = maxOf(first, second - MAX_LEAD_IN_MS)
                wordStart[word] = first
                placed = word + 1
            }
            i = end
        }
        // A word with nothing placed (it shouldn't happen) starts with the one after it.
        var next = -1
        for (w in placed - 1 downTo 0) {
            if (wordStart[w] < 0) wordStart[w] = next else next = wordStart[w]
        }
        var before = 0
        for (w in 0 until placed) {
            if (wordStart[w] < before) wordStart[w] = before
            before = wordStart[w]
        }
        var w = 0
        val verses = ArrayList<List<WordTiming>>()
        for (size in verseSizes) {
            if (w >= placed) break
            verses += List(minOf(size, placed - w)) {
                val start = wordStart[w]
                val reached = letters[w]
                // Letters fill in from the word's start, never before it.
                for (k in 1 until reached.size step 2) if (reached[k] < start) reached[k] = start
                WordTiming(start, reached.toIntArray()).also { w++ }
            }
        }
        // How clearly the surah was heard is only known once all of it has been.
        val clarity = if (aligner == null) 1f else if (required == 0) 0f else clear.toFloat() / required
        return Timings(verses, clarity, heardMs)
    }

    companion object {
        const val ISTIADHA = "أَعُوذُ بِٱللَّهِ مِنَ ٱلشَّيْطَـٰنِ ٱلرَّجِيمِ"
        const val BASMALA = "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ"

        /**
         * The model marks a sound a little after it begins, about 100 ms on recitations
         * with published timings. Lighting a word up slightly early also reads better than late.
         */
        const val LEAD_MS = 100

        private const val MAX_LEAD_IN_MS = 400

        /** A sound scoring above this (about one chance in ten) counts as clearly heard. */
        private const val CLEAR = -2.3f
    }
}

/**
 * When a word starts in the recording, and when each of its letters is reached: pairs of
 * (letters so far, ms). The sound that starts at a pair's time is written by the letters
 * between the pair before and this one.
 */
class WordTiming(val startMs: Int, val letters: IntArray) {

    /**
     * Where in the word the reciter is at [ms]: uttering the characters from [Uttered.from]
     * up to [Uttered.to], [Uttered.fraction] of the way through them. It also says where
     * the stretch uttered just before began and how long ago this one took over, so a
     * marker can glide from one to the next instead of jumping. [endMs] is when the next
     * word starts. Null if the word has no letter timings.
     */
    fun utteredAt(ms: Int, endMs: Int): Uttered? {
        if (letters.isEmpty()) return null
        val count = letters.size / 2
        var k = 0
        while (k + 1 < count && letters[2 * k + 3] <= ms) k++
        val from = if (k == 0) 0 else letters[2 * k - 2]
        val to = letters[2 * k]
        val start = letters[2 * k + 1]
        // The last sound lasts until the next word, but a breath after it isn't part of it.
        val end = if (k + 1 < count) letters[2 * k + 3] else minOf(maxOf(endMs, start + 1), start + LONGEST_LAST_SOUND_MS)
        val fraction = if (end <= start) 1f else ((ms - start).toFloat() / (end - start)).coerceIn(0f, 1f)
        // The stretch before this one ran from where the one before it ended, up to [from].
        val before = if (k <= 1) 0 else letters[2 * k - 4]
        return Uttered(from, to, fraction, if (k == 0) from else before, maxOf(0, ms - start))
    }

    private companion object {
        const val LONGEST_LAST_SOUND_MS = 1200
    }
}

/**
 * See [WordTiming.utteredAt]. [before] is where the stretch uttered just before this one
 * began (it ended at [from]); it equals [from] for a word's first stretch. [sinceMs] is how
 * long ago this stretch began.
 */
data class Uttered(val from: Int, val to: Int, val fraction: Float, val before: Int = from, val sinceMs: Int = 0)

/**
 * The timing of every word of a surah in one recording, or, while the recording is still
 * being listened to, of the words placed so far ([heardMs] says how far that is).
 * [clarity]: the share of sounds the model heard clearly where the alignment put them. A
 * good match scores about 0.9 and a muffled old recording about 0.7; the wrong surah, or a
 * recording that stops half way, scores under 0.6.
 */
class Timings(val verses: List<List<WordTiming>>, val clarity: Float, val heardMs: Int = COMPLETE) {

    /** False while the recording is still being listened to: [verses] then stops at the last word placed. */
    val complete: Boolean get() = heardMs == COMPLETE

    /** Whether the recording is this surah. Not judged until all of it has been heard. */
    val matches: Boolean get() = !complete || clarity >= GOOD_ENOUGH

    /** The verse (counting from 0) being recited at [ms]: the last one whose first word has started. */
    fun verseAt(ms: Int): Int {
        var verse = 0
        for ((v, words) in verses.withIndex()) {
            val start = words.firstOrNull()?.startMs ?: continue
            if (start <= ms) verse = v else break
        }
        return verse
    }

    /**
     * One line for the clarity and how far the listening has got (-1 once it is done), then
     * one per verse: words apart by spaces, numbers by commas.
     */
    fun encode(): String = buildString {
        append(clarity).append(' ').append(heardMs).append('\n')
        for (verse in verses) {
            verse.joinTo(this, " ") { w -> (listOf(w.startMs) + w.letters.toList()).joinToString(",") }
            append('\n')
        }
    }

    companion object {
        const val GOOD_ENOUGH = 0.65f

        /** [heardMs] when the whole recording has been listened to. */
        const val COMPLETE = -1

        fun decode(text: String): Timings {
            val lines = text.trimEnd('\n').split('\n')
            val verses = lines.drop(1).map { line ->
                if (line.isEmpty()) emptyList() else line.split(' ').map { word ->
                    val numbers = word.split(',').map(String::toInt)
                    WordTiming(numbers[0], numbers.drop(1).toIntArray())
                }
            }
            val head = lines[0].split(' ')
            return Timings(verses, head[0].toFloat(), head.getOrNull(1)?.toInt() ?: COMPLETE)
        }
    }
}
