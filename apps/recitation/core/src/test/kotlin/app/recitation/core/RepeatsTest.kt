package app.recitation.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A reciter who goes back and says a passage again: the words must be passed over twice, not stand still. */
class RepeatsTest {
    // 1:2 to 1:6. Words 0..3, 4..5, 6..8, 9..12, 13..15.
    private val verses = listOf(
        "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَـٰلَمِينَ", "ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ", "مَـٰلِكِ يَوْمِ ٱلدِّينِ",
        "إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ", "ٱهْدِنَا ٱلصِّرَٰطَ ٱلْمُسْتَقِيمَ",
    ).map { it.split(' ') }
    private val recital = Recital(2, verses)
    private val width = SoundColumns.WIDTH

    /** The sounds of each word of the verses, as places in the recital's run of sounds. */
    private val soundsOfWord: List<List<Int>> = run {
        val sizes = verses.flatMap { Phonemizer.verse(it).map { word -> word.size } }
        var at = recital.phones.size - sizes.sum()
        sizes.map { size -> (at until at + size).toList().also { at += size } }
    }

    /** What the model would hear of a reciter saying [words] in that order: a clear row for each sound, 200 ms apart. */
    private fun heard(words: List<Int>, noiseAfter: Int = -1): Pair<FloatArray, IntArray> {
        val random = Random(7)
        val columns = ArrayList<Int>()
        for ((n, word) in words.withIndex()) {
            for (sound in soundsOfWord[word]) columns += recital.phones[sound].sound.ordinal + 1
            // Other speech: sounds in no order at all.
            if (n == noiseAfter) repeat(14) { columns += 1 + random.nextInt(width - 1) }
        }
        val rows = FloatArray(columns.size * width) { -9f }
        for ((row, column) in columns.withIndex()) rows[row * width + column] = -0.05f
        return rows to IntArray(columns.size) { it * 10 }
    }

    /** The words in the order the light passes over them: every word once, and the passages said again where they fall. */
    private fun order(timings: Timings): List<Int> {
        val entries = ArrayList<Pair<Int, Int>>()
        timings.verses.flatten().forEachIndexed { word, timing -> entries += timing.startMs to word }
        for (repeat in timings.repeats) repeat.words.forEachIndexed { i, timing -> entries += timing.startMs to repeat.first + i }
        // A word carried on with after a break in it is still the one word.
        return entries.sortedBy { it.first }.map { it.second }.fold(ArrayList()) { all, word -> if (all.lastOrNull() != word) all += word; all }
    }

    private fun placed(words: List<Int>, noiseAfter: Int = -1): Timings = heard(words, noiseAfter).let { (rows, frames) -> recital.place(rows, frames)!! }

    @Test
    fun `a recitation said straight through has nothing said again`() {
        val timings = placed((0..15).toList())
        assertTrue(timings.repeats.isEmpty())
        assertEquals((0..15).toList(), order(timings))
    }

    @Test
    fun `a verse said again is passed over twice`() {
        val said = (0..12) + (9..12) + (13..15)
        val timings = placed(said)
        assertEquals(said, order(timings))
        assertTrue(timings.clarity > 0.9f)
    }

    @Test
    fun `going back a few words in the middle of a verse`() {
        val said = (0..10) + (9..10) + (11..15)
        assertEquals(said, order(placed(said)))
        val further = (0..7) + (4..7) + (8..15)
        assertEquals(further, order(placed(further)))
    }

    @Test
    fun `a passage said three times is passed over three times`() {
        val said = (0..8) + (6..8) + (6..8) + (9..15)
        assertEquals(said, order(placed(said)))
    }

    @Test
    fun `other speech in the middle is not taken for a passage said again`() {
        val timings = placed((0..15).toList(), noiseAfter = 8)
        assertTrue(timings.repeats.isEmpty())
        assertEquals((0..15).toList(), order(timings))
    }

    @Test
    fun `passages said again survive being saved and read back`() {
        val timings = placed((0..12) + (9..12) + (13..15))
        val back = Timings.decode(timings.encode())
        assertEquals(order(timings), order(back))
        assertEquals(timings.repeats.map { it.first }, back.repeats.map { it.first })
        assertEquals(timings.repeats.map { it.words.map { w -> w.letters.toList() } }, back.repeats.map { it.words.map { w -> w.letters.toList() } })
    }

    @Test
    fun `a stretch heard out of turn is found in the surah by its sounds`() {
        // The fourth and fifth verses alone, as heard by a listener that went straight to them.
        val (rows, frames) = heard((9..15).toList())
        val found = recital.locate(rows, frames, around = 0.55f)!!
        assertEquals(9, found.first)
        assertEquals(7, found.words.size)
        // Sounds that aren't the surah's are not taken for it.
        val random = Random(3)
        val noise = FloatArray(60 * width) { -9f }
        for (row in 0 until 60) noise[row * width + 1 + random.nextInt(width - 1)] = -0.05f
        assertEquals(null, recital.locate(noise, IntArray(60) { it * 10 }, around = 0.5f))
    }

    @Test
    fun `a passage said again is found wherever in a long recording it falls`() {
        // The five verses forty times over: long enough to be gone through in several stretches.
        val long = List(40) { verses }.flatten()
        val recital = Recital(2, long)
        val sizes = long.flatMap { Phonemizer.verse(it).map { word -> word.size } }
        var at = recital.phones.size - sizes.sum()
        val soundsOfWord = sizes.map { size -> (at until at + size).toList().also { at += size } }
        fun placed(words: List<Int>): Timings {
            val columns = words.flatMap { word -> soundsOfWord[word].map { recital.phones[it].sound.ordinal + 1 } }
            val rows = FloatArray(columns.size * width) { -9f }
            for ((row, column) in columns.withIndex()) rows[row * width + column] = -0.05f
            return recital.place(rows, IntArray(columns.size) { it * 10 })!!
        }
        // Four words said again, begun at each word in turn across where one stretch ends and
        // the next begins (some 1500 sounds in): the end of a stretch mustn't hide it.
        var sounds = 0
        val middle = sizes.indexOfFirst { sounds += it; sounds >= 1500 }
        for (k in middle - 24..middle + 24) {
            val said = (0..k + 3) + (k..k + 3) + (k + 4 until sizes.size)
            assertEquals(said.toList(), order(placed(said.toList())), "going back to word $k")
        }
    }

    @Test
    fun `a passage said again is found while the recording is still being listened to`() {
        val long = List(40) { verses }.flatten()
        val recital = Recital(2, long)
        val sizes = long.flatMap { Phonemizer.verse(it).map { word -> word.size } }
        var at = recital.phones.size - sizes.sum()
        val soundsOfWord = sizes.map { size -> (at until at + size).toList().also { at += size } }
        // Four words said again a third of the way in, and three more two thirds of the way in.
        val k = sizes.size / 3
        val m = 2 * sizes.size / 3
        val said = ((0..k + 3) + (k..m + 2) + (m until sizes.size)).toList()
        val columns = said.flatMap { word -> soundsOfWord[word].map { recital.phones[it].sound.ordinal + 1 } }
        val rows = FloatArray(columns.size * width) { -9f }
        for ((row, column) in columns.withIndex()) rows[row * width + column] = -0.05f
        val frames = IntArray(columns.size) { it * 10 }
        val whole = recital.place(rows, frames)!!
        assertEquals(listOf(k to 4, m to 3), whole.repeats.map { it.first to it.words.size })

        // Heard in pieces of some ten seconds, as the listening hands them on.
        val live = LiveAlignment(recital)
        val seenAt = HashMap<Int, Int>() // for each passage, how many rows had been heard when it first showed
        val shown = ArrayList<List<Pair<Int, Int>>>()
        var soFar = Timings(emptyList(), 1f, heardMs = 0)
        for (from in columns.indices step 140) {
            val until = minOf(columns.size, from + 140)
            live.add(frames.copyOfRange(from, until), rows.copyOfRange(from * width, until * width))
            soFar = live.advance()
            for (repeat in soFar.repeats) seenAt.putIfAbsent(repeat.first, until)
            shown += soFar.repeats.map { it.first to it.words.size }
        }
        // Each is found within a minute of being said, long before the end, and the same as the whole recording gives.
        val rowOf = { word: Int -> said.take(said.indexOf(word) + 1).sumOf { soundsOfWord[it].size } }
        assertTrue(seenAt.getValue(k) - rowOf(k + 3) < 900, "the first showed ${seenAt[k]} rows in, said by row ${rowOf(k + 3)}")
        assertTrue(seenAt.getValue(m) < columns.size - 1_000, "the second showed ${seenAt[m]} rows in, of ${columns.size}")
        assertEquals(whole.repeats.map { it.first to it.words.size }, soFar.repeats.map { it.first to it.words.size })
        assertEquals(whole.repeats.map { it.words.map(WordTiming::startMs) }, soFar.repeats.map { it.words.map(WordTiming::startMs) })
        // Once shown, a passage stays: nothing found is taken back or changed.
        for ((earlier, later) in shown.zipWithNext()) assertEquals(earlier, later.take(earlier.size))
        // The words said once are where the whole recording puts them too, as far as they have been settled.
        val placed = soFar.verses.flatten()
        assertEquals(whole.verses.flatten().take(placed.size).map { it.startMs }, placed.map { it.startMs })
    }

    @Test
    fun `nothing is found said again in a recitation heard straight through as it is listened to`() {
        val long = List(40) { verses }.flatten()
        val recital = Recital(2, long)
        val columns = recital.phones.map { it.sound.ordinal + 1 }
        val rows = FloatArray(columns.size * width) { -9f }
        for ((row, column) in columns.withIndex()) rows[row * width + column] = -0.05f
        val live = LiveAlignment(recital)
        for (from in columns.indices step 140) {
            val until = minOf(columns.size, from + 140)
            live.add(IntArray(until - from) { (from + it) * 10 }, rows.copyOfRange(from * width, until * width))
            assertEquals(emptyList(), live.advance().repeats.map { it.first })
        }
    }

    @Test
    fun `a long stretch heard out of turn is found a chunk at a time`() {
        // All five verses, heard in two ten-second pieces; a chunk is one piece here.
        val (rows, sounds) = heard((0..15).toList())
        val frames = IntArray(sounds.size) { it * (1000 / sounds.size) }
        val pieces = frames.indices.groupBy { frames[it] / 500 }.mapValues { (_, at) ->
            Heard(at.map { frames[it] }.toIntArray(), rows.copyOfRange(at.first() * width, (at.last() + 1) * width))
        }
        assertEquals(setOf(0, 1), pieces.keys)
        val stretch = Stretch(recital, lengthMs = frames.last() * 20L, chunk = 1)

        // With the first piece alone, its words; then both pieces' words, every word once and in order.
        val early = stretch.words(pieces.filterKeys { it == 0 }, 0)
        assertEquals(listOf(0 until 1), early.pieces)
        val all = stretch.words(pieces, 0)
        assertEquals(listOf(0 until 2), all.pieces)
        assertEquals(early.words.single().words.map { it.startMs }, all.words.first().words.map { it.startMs })
        assertEquals((0..15).toList(), all.words.flatMap { chunk -> chunk.words.indices.map { chunk.first + it } })
        // A chunk whose words can't be told is left out: nothing is claimed for it.
        val noise = FloatArray(60 * width) { -9f }
        val lost = mapOf(0 to pieces.getValue(0), 1 to Heard(IntArray(60) { 500 + it * 8 }, noise))
        assertEquals(listOf(0 until 1), Stretch(recital, frames.last() * 20L, chunk = 1).words(lost, 0).pieces)
    }
}
