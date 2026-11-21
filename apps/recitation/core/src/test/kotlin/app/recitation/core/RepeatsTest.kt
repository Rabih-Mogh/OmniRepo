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
}
