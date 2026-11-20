package app.recitation.core

/**
 * Turns the timings an alignment gives into what the light needs to follow a recitation
 * letter by letter:
 *
 * - **Where the reciter stopped.** Several rules of recitation depend on it (see [Tajweed]).
 *   A stop shows as a long wait between a word's last sound and the next word.
 * - **A time for every letter and vowel sign.** The alignment times sounds. A letter that
 *   is merged into the next one has no sound of its own, so the light would jump over it;
 *   it is given the opening part of the sound it is merged into. And where only a letter
 *   and its vowel were timed together, the time is shared between the letter and the sign.
 */
object Pointer {

    /** [timings] made finer, and after which of the surah's words the reciter stopped. */
    class Refined(val timings: Timings, val stops: BooleanArray)

    /** [verses]: every word of the surah. [timings]: as aligned; it may cover only the first words. */
    fun refine(verses: List<List<String>>, timings: Timings): Refined {
        val words = verses.flatten()
        val timed = timings.verses.flatten()
        val stops = Tajweed.stopsAtVerseEnds(verses.map { it.size })
        val units = words.take(timed.size).map(Phonemizer::units)

        // ---- where the reciter stopped
        for (i in 0 until timed.size - 1) {
            val lastSound = timed[i].letters.let { if (it.size >= 2) it[it.size - 1] else timed[i].startMs }
            val wait = timed[i + 1].startMs - lastSound
            val endsLong = Phonemizer.word(words[i]).lastOrNull()?.sound.let { it == Sound.AA || it == Sound.II || it == Sound.UU }
            // At a verse's end a stop is the usual thing, so less of a wait is taken for one.
            val needed = (if (endsLong) LONG_WAIT_MS else WAIT_MS) * (if (stops[i]) 3 else 4) / 4
            stops[i] = wait >= needed
        }

        // ---- a letter merged into the next word shares that word's first sound
        val starts = IntArray(timed.size) { timed[it].startMs }
        val reached = Array(timed.size) { i -> timed[i].letters.copyOf().toMutableList() }
        for (i in 0 until timed.size - 1) {
            val covered = reached[i].let { if (it.size >= 2) it[it.size - 2] else 0 }
            // Letters after the last one that was timed: an n, m or other bare letter run into the next word.
            val merged = units[i].filter { it.start >= covered && Phonemizer.isConsonant(it.base) && it.marks.none { m -> m in Phonemizer.VOWEL_SIGNS || m == 'ْ' || m == 'ّ' } }
            if (stops[i] || merged.isEmpty() || covered == 0) continue
            val next = timed[i + 1]
            val firstSoundEnds = if (next.letters.size >= 4) next.letters[3] else timed.getOrNull(i + 2)?.startMs ?: (next.startMs + 2 * MERGED_MS)
            val share = minOf((firstSoundEnds - next.startMs) / 2, MERGED_MS).coerceAtLeast(0)
            reached[i] += merged.last().end
            reached[i] += next.startMs
            starts[i + 1] = next.startMs + share
            for (k in 1 until reached[i + 1].size step 2) if (reached[i + 1][k] < starts[i + 1]) reached[i + 1][k] = starts[i + 1]
        }

        // ---- inside each word: silent letters, then the letter, then its vowel sign
        val refined = List(timed.size) { i ->
            val wordEnds = if (i + 1 < timed.size) starts[i + 1] else Int.MAX_VALUE
            val pairs = reached[i]
            val out = ArrayList<Int>(pairs.size * 2)
            var from = 0
            var k = 0
            while (k < pairs.size) {
                val to = pairs[k]
                val begins = pairs[k + 1]
                val ends = if (k + 2 < pairs.size) pairs[k + 3] else minOf(wordEnds, begins + LAST_SOUND_MS)
                // The pieces of this stretch, in order: each ends at a character, and has the most time it may take.
                val pieces = ArrayList<IntArray>()
                val inside = units[i].filter { it.start >= from && it.start < to }
                for ((n, unit) in inside.withIndex()) {
                    if (n < inside.lastIndex) {
                        // Letters before the one that is sounded: silent, or merged into it.
                        pieces += intArrayOf(minOf(unit.end, to), SILENT_MS)
                    } else if (unit.body in (unit.start + 1) until to) {
                        // A letter timed together with its vowel: the letter first, then the sign.
                        pieces += intArrayOf(unit.body, if ('ّ' in unit.marks) DOUBLED_MS else LETTER_MS)
                    }
                }
                pieces += intArrayOf(to, Int.MAX_VALUE)
                var at = begins
                var left = (ends - begins).coerceAtLeast(0)
                for ((n, piece) in pieces.withIndex()) {
                    out += piece[0]
                    out += at
                    if (n < pieces.lastIndex) {
                        // Never more than its share, so the last piece keeps the larger part.
                        val take = minOf(piece[1], left * 2 / 5)
                        at += take
                        left -= take
                    }
                }
                from = to
                k += 2
            }
            WordTiming(starts[i], out.toIntArray())
        }

        var w = 0
        val grouped = timings.verses.map { verse -> List(verse.size) { refined[w++] } }
        return Refined(Timings(grouped, timings.clarity, timings.heardMs), stops)
    }

    /** A wait this long after a word's last sound is a stop, when that sound is a short one. */
    private const val WAIT_MS = 700

    /** The same when the word ends in a long vowel, which lasts longer by itself. */
    private const val LONG_WAIT_MS = 1800

    /** The most a merged letter takes from the sound it is merged into. */
    private const val MERGED_MS = 350

    /** The most the light spends on a letter that isn't sounded, on a letter before its vowel, and on a doubled one. */
    private const val SILENT_MS = 100
    private const val LETTER_MS = 110
    private const val DOUBLED_MS = 280

    /** A word's last sound is taken to last no longer than this when sharing it out. */
    private const val LAST_SOUND_MS = 1200
}
